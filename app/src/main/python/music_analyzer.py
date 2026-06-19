# music_analyzer.py — v5
# Music-Mood audio feature extractor & mood classifier
# Compatibile con Chaquopy 15+ / Python 3.11
#
# API pubblica (NON cambiare le firme, sono chiamate da Kotlin):
#   - read_tags(path: str) -> dict
#   - analyze_pcm(pcm_bytes: bytes, sample_rate: int, channels: int,
#                 duration_ms: int, title: str = "", artist: str = "") -> dict
#
# Output di analyze_pcm:
# {
#   "mood": "Energico",
#   "confidence": 0.78,
#   "valence": 0.62,        # -1..+1  (negativo/positivo)
#   "arousal": 0.81,        # -1..+1  (calmo/eccitato)
#   "tempo_bpm": 124.0,
#   "key": "A",             # nota tonica
#   "mode": "major",        # major/minor
#   "features": { ... },    # tutte le feature DSP normalizzate
#   "debug":    { ... }     # solo per logging
# }

from __future__ import annotations

import io
import logging
import math
from dataclasses import dataclass, asdict, field
from typing import Optional

import numpy as np
from scipy.signal import get_window, find_peaks
from scipy.fft import rfft, rfftfreq

# Tag reading
try:
    from mutagen import File as MutagenFile  # type: ignore
    from mutagen.id3 import ID3NoHeaderError  # type: ignore
except Exception:  # pragma: no cover
    MutagenFile = None  # type: ignore
    ID3NoHeaderError = Exception  # type: ignore

log = logging.getLogger("music_analyzer")
log.setLevel(logging.INFO)

# ────────────────────────────────────────────────────────────────────────────
# Costanti DSP
# ────────────────────────────────────────────────────────────────────────────
TARGET_SR        = 22_050        # downsample per ridurre il costo computazionale
WINDOW_SEC       = 4.0           # durata di ogni finestra analizzata
N_WINDOWS        = 5             # finestre estratte uniformemente
FRAME_SIZE       = 2048
HOP_SIZE         = 512
N_MFCC           = 13
N_MEL            = 40
FMIN, FMAX       = 30.0, 8_000.0
EPS              = 1e-10

# Note nomenclature (per key detection)
NOTE_NAMES = ["C", "C#", "D", "D#", "E", "F",
              "F#", "G", "G#", "A", "A#", "B"]

# Profili di Krumhansl-Schmuckler per major/minor (usati per stimare la tonalità)
KS_MAJOR = np.array([6.35, 2.23, 3.48, 2.33, 4.38, 4.09,
                     2.52, 5.19, 2.39, 3.66, 2.29, 2.88])
KS_MINOR = np.array([6.33, 2.68, 3.52, 5.38, 2.60, 3.53,
                     2.54, 4.75, 3.98, 2.69, 3.34, 3.17])


# ────────────────────────────────────────────────────────────────────────────
# Dataclasses
# ────────────────────────────────────────────────────────────────────────────
@dataclass
class AudioFeatures:
    rms:                float = 0.0
    rms_var:            float = 0.0
    zcr:                float = 0.0
    spectral_centroid:  float = 0.0
    spectral_rolloff:   float = 0.0
    spectral_flatness:  float = 0.0
    spectral_bandwidth: float = 0.0
    onset_density:      float = 0.0   # onset/sec
    tempo_bpm:          float = 0.0
    mfcc:               list  = field(default_factory=list)   # 13 coeff
    chroma:             list  = field(default_factory=list)   # 12 bin
    key:                str   = "C"
    mode:               str   = "major"
    key_confidence:     float = 0.0


@dataclass
class MoodResult:
    mood:       str
    confidence: float
    valence:    float
    arousal:    float
    features:   AudioFeatures
    debug:      dict = field(default_factory=dict)


# ────────────────────────────────────────────────────────────────────────────
# Pre-processing
# ────────────────────────────────────────────────────────────────────────────
def _pcm_to_float_mono(pcm_bytes: bytes, channels: int) -> np.ndarray:
    """Converte PCM 16-bit signed little-endian in float32 mono [-1, 1]."""
    if not pcm_bytes:
        return np.zeros(0, dtype=np.float32)
    x = np.frombuffer(pcm_bytes, dtype=np.int16).astype(np.float32) / 32768.0
    if channels == 2 and x.size >= 2:
        x = x.reshape(-1, 2).mean(axis=1)
    return x


def _resample(x: np.ndarray, sr_in: int, sr_out: int) -> np.ndarray:
    """Resampling lineare (sufficiente per feature extraction, non per playback)."""
    if sr_in == sr_out or x.size == 0:
        return x
    ratio = sr_out / sr_in
    n_out = int(round(x.size * ratio))
    if n_out <= 1:
        return x
    xp = np.linspace(0.0, 1.0, x.size, endpoint=False)
    fp = np.linspace(0.0, 1.0, n_out, endpoint=False)
    return np.interp(fp, xp, x).astype(np.float32)


def _pick_windows(x: np.ndarray, sr: int) -> list[np.ndarray]:
    """
    Estrae N_WINDOWS finestre da WINDOW_SEC e ne ordina le posizioni in base
    all'energia RMS: la prima finestra restituita è la "più carica"
    (proxy del ritornello). Restituisce TUTTE le finestre per consentire
    feature aggregate (media/varianza).
    """
    win_len = int(WINDOW_SEC * sr)
    if x.size <= win_len:
        return [x] if x.size else []

    n = N_WINDOWS
    centers = np.linspace(win_len // 2, x.size - win_len // 2, n).astype(int)
    windows, energies = [], []
    for c in centers:
        seg = x[c - win_len // 2 : c + win_len // 2]
        if seg.size == win_len:
            windows.append(seg)
            energies.append(float(np.sqrt(np.mean(seg * seg) + EPS)))

    # ordina per energia decrescente
    order = np.argsort(energies)[::-1]
    return [windows[i] for i in order]


# ────────────────────────────────────────────────────────────────────────────
# Frame-level features
# ────────────────────────────────────────────────────────────────────────────
def _framewise(x: np.ndarray, frame: int, hop: int) -> np.ndarray:
    """Restituisce una matrice (n_frames, frame) con finestratura Hann."""
    if x.size < frame:
        return np.zeros((0, frame), dtype=np.float32)
    n_frames = 1 + (x.size - frame) // hop
    idx = (np.arange(frame)[None, :] +
           hop * np.arange(n_frames)[:, None])
    frames = x[idx]
    win = get_window("hann", frame, fftbins=True).astype(np.float32)
    return frames * win


def _spectra(frames: np.ndarray, sr: int):
    """FFT magnitude spectrum + array delle frequenze (Hz)."""
    spec = np.abs(rfft(frames, axis=1)).astype(np.float32)
    freqs = rfftfreq(frames.shape[1], 1.0 / sr).astype(np.float32)
    return spec, freqs


def _spectral_centroid(spec: np.ndarray, freqs: np.ndarray) -> float:
    num = np.sum(spec * freqs[None, :], axis=1)
    den = np.sum(spec, axis=1) + EPS
    return float(np.mean(num / den))


def _spectral_rolloff(spec: np.ndarray, freqs: np.ndarray, p: float = 0.85) -> float:
    total = np.sum(spec, axis=1, keepdims=True) + EPS
    cum   = np.cumsum(spec, axis=1) / total
    idx   = np.argmax(cum >= p, axis=1)
    return float(np.mean(freqs[idx]))


def _spectral_flatness(spec: np.ndarray) -> float:
    s = spec + EPS
    geo = np.exp(np.mean(np.log(s), axis=1))
    arith = np.mean(s, axis=1)
    return float(np.mean(geo / arith))


def _spectral_bandwidth(spec: np.ndarray, freqs: np.ndarray) -> float:
    centroid = (np.sum(spec * freqs[None, :], axis=1) /
                (np.sum(spec, axis=1) + EPS))
    var = np.sum(spec * (freqs[None, :] - centroid[:, None]) ** 2, axis=1) / \
          (np.sum(spec, axis=1) + EPS)
    return float(np.mean(np.sqrt(var + EPS)))


def _zcr(frames: np.ndarray) -> float:
    signs = np.sign(frames)
    signs[signs == 0] = 1
    return float(np.mean(np.abs(np.diff(signs, axis=1)).sum(axis=1) /
                         (2.0 * frames.shape[1])))


# ────────────────────────────────────────────────────────────────────────────
# Mel filterbank + MFCC (implementazione minimale, no librosa)
# ────────────────────────────────────────────────────────────────────────────
def _hz_to_mel(f):  return 2595.0 * np.log10(1.0 + f / 700.0)
def _mel_to_hz(m):  return 700.0 * (10 ** (m / 2595.0) - 1.0)


def _mel_filterbank(n_mel: int, n_fft: int, sr: int,
                    fmin: float, fmax: float) -> np.ndarray:
    mel_pts = np.linspace(_hz_to_mel(fmin), _hz_to_mel(fmax), n_mel + 2)
    hz_pts  = _mel_to_hz(mel_pts)
    bin_pts = np.floor((n_fft + 1) * hz_pts / sr).astype(int)
    fb = np.zeros((n_mel, n_fft // 2 + 1), dtype=np.float32)
    for i in range(1, n_mel + 1):
        l, c, r = bin_pts[i - 1], bin_pts[i], bin_pts[i + 1]
        if c == l: c += 1
        if r == c: r += 1
        fb[i - 1, l:c] = (np.arange(l, c) - l) / max(c - l, 1)
        fb[i - 1, c:r] = (r - np.arange(c, r)) / max(r - c, 1)
    return fb


def _mfcc(spec: np.ndarray, sr: int, n_mfcc: int) -> np.ndarray:
    n_fft = (spec.shape[1] - 1) * 2
    fb = _mel_filterbank(N_MEL, n_fft, sr, FMIN, FMAX)
    mel = np.log(spec @ fb.T + EPS)                     # (frames, n_mel)
    # DCT-II ortonormale
    n = mel.shape[1]
    k = np.arange(n_mfcc)[:, None]
    j = np.arange(n)[None, :]
    dct = np.cos(np.pi / n * (j + 0.5) * k) * np.sqrt(2.0 / n)
    dct[0] *= 1.0 / np.sqrt(2.0)
    mfcc = mel @ dct.T                                  # (frames, n_mfcc)
    return mfcc.mean(axis=0)


# ────────────────────────────────────────────────────────────────────────────
# Chroma + key/mode (Krumhansl-Schmuckler)
# ────────────────────────────────────────────────────────────────────────────
def _chroma(spec: np.ndarray, freqs: np.ndarray) -> np.ndarray:
    """Vettore 12-dim normalizzato delle classi di altezza."""
    mask = freqs > 0
    f = freqs[mask]
    s = spec[:, mask].mean(axis=0)
    # MIDI pitch -> pitch class
    midi = 69 + 12 * np.log2(f / 440.0 + EPS)
    pc = np.mod(np.round(midi).astype(int), 12)
    chroma = np.zeros(12, dtype=np.float32)
    np.add.at(chroma, pc, s)
    norm = np.linalg.norm(chroma) + EPS
    return chroma / norm


def _estimate_key(chroma: np.ndarray) -> tuple[str, str, float]:
    """Restituisce (tonica, modo, confidenza 0..1)."""
    best_corr, best_key, best_mode = -1.0, 0, "major"
    for k in range(12):
        for mode, profile in (("major", KS_MAJOR), ("minor", KS_MINOR)):
            rotated = np.roll(profile, k)
            corr = float(np.corrcoef(chroma, rotated)[0, 1])
            if corr > best_corr:
                best_corr, best_key, best_mode = corr, k, mode
    conf = max(0.0, min(1.0, (best_corr + 1.0) / 2.0))
    return NOTE_NAMES[best_key], best_mode, conf


# ────────────────────────────────────────────────────────────────────────────
# Onset detection + tempo
# ────────────────────────────────────────────────────────────────────────────
def _onset_env(spec: np.ndarray) -> np.ndarray:
    """Spectral flux (half-wave rectified)."""
    diff = np.diff(spec, axis=0)
    diff[diff < 0] = 0
    return diff.sum(axis=1)


def _tempo_from_onset(onset: np.ndarray, sr: int, hop: int,
                      bpm_lo: float = 60.0, bpm_hi: float = 200.0) -> float:
    if onset.size < 4:
        return 0.0
    # autocorrelazione dell'onset envelope
    o = onset - onset.mean()
    ac = np.correlate(o, o, mode="full")[onset.size - 1:]
    ac[0] = 0
    # range di lag corrispondenti al BPM ammissibile
    fps = sr / hop
    lag_min = int(fps * 60.0 / bpm_hi)
    lag_max = int(fps * 60.0 / bpm_lo)
    lag_max = min(lag_max, ac.size - 1)
    if lag_min >= lag_max:
        return 0.0
    seg = ac[lag_min:lag_max]
    peaks, _ = find_peaks(seg)
    if peaks.size == 0:
        lag = int(np.argmax(seg)) + lag_min
    else:
        lag = peaks[np.argmax(seg[peaks])] + lag_min
    return float(60.0 * fps / max(lag, 1))


def _onset_density(onset: np.ndarray, sr: int, hop: int) -> float:
    if onset.size == 0:
        return 0.0
    thr = onset.mean() + onset.std()
    peaks, _ = find_peaks(onset, height=thr, distance=int(sr / hop * 0.1))
    duration = onset.size * hop / sr
    return float(peaks.size / max(duration, EPS))


# ────────────────────────────────────────────────────────────────────────────
# Aggregazione feature su tutte le finestre
# ────────────────────────────────────────────────────────────────────────────
def _extract_features(windows: list[np.ndarray], sr: int) -> AudioFeatures:
    feat = AudioFeatures()
    if not windows:
        return feat

    rms_list, zcr_list, sc_list, sr_list, sf_list, sb_list = [], [], [], [], [], []
    mfcc_list, chroma_list, onset_dens, tempi = [], [], [], []

    for w in windows:
        frames = _framewise(w, FRAME_SIZE, HOP_SIZE)
        if frames.shape[0] == 0:
            continue
        spec, freqs = _spectra(frames, sr)

        rms_list.append(float(np.sqrt(np.mean(w * w) + EPS)))
        zcr_list.append(_zcr(frames))
        sc_list.append(_spectral_centroid(spec, freqs))
        sr_list.append(_spectral_rolloff(spec, freqs))
        sf_list.append(_spectral_flatness(spec))
        sb_list.append(_spectral_bandwidth(spec, freqs))
        mfcc_list.append(_mfcc(spec, sr, N_MFCC))
        chroma_list.append(_chroma(spec, freqs))

        onset = _onset_env(spec)
        onset_dens.append(_onset_density(onset, sr, HOP_SIZE))
        tempi.append(_tempo_from_onset(onset, sr, HOP_SIZE))

    feat.rms                = float(np.mean(rms_list)) if rms_list else 0.0
    feat.rms_var            = float(np.var(rms_list))   if rms_list else 0.0
    feat.zcr                = float(np.mean(zcr_list)) if zcr_list else 0.0
    feat.spectral_centroid  = float(np.mean(sc_list))  if sc_list  else 0.0
    feat.spectral_rolloff   = float(np.mean(sr_list))  if sr_list  else 0.0
    feat.spectral_flatness  = float(np.mean(sf_list))  if sf_list  else 0.0
    feat.spectral_bandwidth = float(np.mean(sb_list))  if sb_list  else 0.0
    feat.mfcc               = (np.mean(mfcc_list, axis=0).tolist()
                               if mfcc_list else [0.0] * N_MFCC)
    chroma_mean             = (np.mean(chroma_list, axis=0)
                               if chroma_list else np.zeros(12))
    feat.chroma             = chroma_mean.tolist()
    feat.onset_density      = float(np.mean(onset_dens)) if onset_dens else 0.0

    # mediana del tempo (più robusta della media)
    valid_tempi = [t for t in tempi if t > 0]
    feat.tempo_bpm = float(np.median(valid_tempi)) if valid_tempi else 0.0

    feat.key, feat.mode, feat.key_confidence = _estimate_key(chroma_mean)
    return feat


# ────────────────────────────────────────────────────────────────────────────
# Mood classification: Valenza/Arousal → 9 mood
# ────────────────────────────────────────────────────────────────────────────
def _normalize(value: float, lo: float, hi: float) -> float:
    """Clamp + min-max normalize to [0, 1]."""
    if hi <= lo:
        return 0.0
    return max(0.0, min(1.0, (value - lo) / (hi - lo)))


def _valence_arousal(f: AudioFeatures) -> tuple[float, float]:
    """
    Modello bidimensionale di Russell.
    Range output: [-1, +1].

    Arousal (eccitazione) ← energia, tempo, onset, centroide, ZCR
    Valence (positività)  ← modo (major/minor), brillantezza, flatness inversa,
                            chroma "diatonica" + bonus tempo medio-alto
    """
    # arousal: combinazione lineare di feature normalizzate
    a = (
        0.30 * _normalize(f.rms,                0.02, 0.30) +
        0.25 * _normalize(f.tempo_bpm,          60.0, 180.0) +
        0.15 * _normalize(f.onset_density,      0.5,  6.0) +
        0.15 * _normalize(f.spectral_centroid,  800,  4_500) +
        0.10 * _normalize(f.zcr,                0.02, 0.25) +
        0.05 * _normalize(f.spectral_bandwidth, 500,  3_000)
    )
    arousal = 2.0 * a - 1.0

    # valence: parte da modo + brillantezza, modulata da tempo e ritmica
    mode_bias = 0.35 if f.mode == "major" else -0.25
    brightness = _normalize(f.spectral_centroid, 800, 4_500)
    tonal_purity = 1.0 - _normalize(f.spectral_flatness, 0.05, 0.45)  # poca rumorosità
    tempo_bonus = _normalize(f.tempo_bpm, 70.0, 140.0) * 0.20

    v = (
        mode_bias +
        0.25 * (brightness - 0.5) * 2.0 +     # -1..+1
        0.20 * (tonal_purity - 0.5) * 2.0 +
        tempo_bonus +
        0.10 * (f.key_confidence - 0.5) * 2.0
    )
    valence = max(-1.0, min(1.0, v))
    arousal = max(-1.0, min(1.0, arousal))
    return valence, arousal


# Centroidi dei 9 mood nello spazio (valence, arousal, tempo_norm)
# Calibrati a mano; in futuro sostituibili con k-means su dataset etichettato.
_MOOD_CENTROIDS = {
    # mood            valence  arousal  tempo(0..1)
    "Energico":       ( 0.55,   0.85,   0.80),
    "Festivo":        ( 0.75,   0.70,   0.75),
    "Positivo":       ( 0.70,   0.30,   0.55),
    "Aggressivo":     (-0.35,   0.85,   0.80),
    "Concentrazione": ( 0.10,   0.10,   0.45),
    "Rilassato":      ( 0.40,  -0.40,   0.30),
    "Romantico":      ( 0.45,  -0.20,   0.40),
    "Nostalgico":     (-0.15,  -0.30,   0.35),
    "Malinconico":    (-0.55,  -0.55,   0.25),
}


def _classify_mood(valence: float, arousal: float,
                   tempo_bpm: float) -> tuple[str, float]:
    """
    Trova il centroide più vicino in distanza euclidea pesata.
    Restituisce (mood, confidence ∈ [0,1]).
    """
    tempo_norm = _normalize(tempo_bpm, 50.0, 180.0)
    point = np.array([valence, arousal, tempo_norm])

    dists = {
        name: float(np.linalg.norm(point - np.array(c)))
        for name, c in _MOOD_CENTROIDS.items()
    }
    # softmax inverso → confidenza
    keys = list(dists.keys())
    d = np.array([dists[k] for k in keys])
    inv = np.exp(-d * 2.5)
    probs = inv / inv.sum()
    best_idx = int(np.argmax(probs))
    return keys[best_idx], float(probs[best_idx])


# ────────────────────────────────────────────────────────────────────────────
# API pubblica
# ────────────────────────────────────────────────────────────────────────────
def analyze_pcm(pcm_bytes: bytes,
                sample_rate: int,
                channels: int,
                duration_ms: int = 0,
                title: str = "",
                artist: str = "") -> dict:
    """
    Analizza un buffer PCM 16-bit signed little-endian e restituisce mood + feature.
    Firma compatibile con la chiamata Kotlin esistente.
    """
    try:
        x = _pcm_to_float_mono(pcm_bytes, channels)
        if x.size == 0:
            return _empty_result("buffer PCM vuoto")

        # downsample a 22 050 Hz per dimezzare il costo computazionale
        x = _resample(x, sample_rate, TARGET_SR)
        sr = TARGET_SR

        windows = _pick_windows(x, sr)
        if not windows:
            return _empty_result("nessuna finestra utilizzabile")

        feat = _extract_features(windows, sr)
        valence, arousal = _valence_arousal(feat)
        mood, conf = _classify_mood(valence, arousal, feat.tempo_bpm)

        result = MoodResult(
            mood=mood,
            confidence=round(conf, 3),
            valence=round(valence, 3),
            arousal=round(arousal, 3),
            features=feat,
            debug={
                "title": title,
                "artist": artist,
                "duration_ms": duration_ms,
                "windows_used": len(windows),
                "sr_internal": sr,
            },
        )
        out = asdict(result)
        # appiattisco tempo/key per comodità del client Kotlin
        out["tempo_bpm"] = feat.tempo_bpm
        out["key"] = feat.key
        out["mode"] = feat.mode
        return out

    except Exception as e:  # log e fallback
        log.exception("analyze_pcm failed: %s", e)
        return _empty_result(f"errore: {e}")


def read_tags(path: str) -> dict:
    """
    Legge i tag ID3/Vorbis tramite mutagen. Resta robusto se il file non ha
    tag o se mutagen non è installato.
    """
    base = {"title": "", "artist": "", "album": "",
            "genre": "", "tempo_bpm": 0.0, "duration_ms": 0}
    if MutagenFile is None:
        return base
    try:
        mf = MutagenFile(path, easy=True)
        if mf is None:
            return base
        tags = getattr(mf, "tags", None) or {}
        def first(key: str) -> str:
            v = tags.get(key)
            if isinstance(v, list) and v:
                return str(v[0])
            return str(v) if v else ""

        bpm_raw = first("bpm") or first("BPM")
        try:
            tempo = float(bpm_raw) if bpm_raw else 0.0
        except ValueError:
            tempo = 0.0

        info = getattr(mf, "info", None)
        duration_ms = int(info.length * 1000) if info and info.length else 0

        return {
            "title":       first("title"),
            "artist":      first("artist"),
            "album":       first("album"),
            "genre":       first("genre"),
            "tempo_bpm":   tempo,
            "duration_ms": duration_ms,
        }
    except (ID3NoHeaderError, OSError, ValueError) as e:
        log.warning("read_tags fallback (%s): %s", path, e)
        return base


def _empty_result(reason: str) -> dict:
    return {
        "mood": "Sconosciuto",
        "confidence": 0.0,
        "valence": 0.0,
        "arousal": 0.0,
        "tempo_bpm": 0.0,
        "key": "C",
        "mode": "major",
        "features": asdict(AudioFeatures()),
        "debug": {"reason": reason},
    }


# ────────────────────────────────────────────────────────────────────────────
# Self-test (eseguibile da terminale: `python music_analyzer.py`)
# ────────────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    sr = 44_100
    t = np.linspace(0, 15.0, sr * 15, endpoint=False)

    # tono "energico": 120 BPM, sinusoide brillante + click ritmici
    click = np.zeros_like(t)
    click[::sr // 2] = 1.0  # 120 BPM
    sig = 0.4 * np.sin(2 * np.pi * 880 * t) + 0.5 * click
    pcm = (sig * 32767).astype(np.int16).tobytes()

    res = analyze_pcm(pcm, sr, channels=1, duration_ms=15_000,
                      title="test", artist="synthetic")
    print("Mood:",        res["mood"], "conf:", res["confidence"])
    print("Valence:",     res["valence"], "Arousal:", res["arousal"])
    print("Tempo BPM:",   res["tempo_bpm"])
    print("Key:",         res["key"], res["mode"])
