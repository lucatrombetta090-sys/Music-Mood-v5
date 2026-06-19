# 🎵 Music-Mood

App Android che analizza la tua libreria musicale locale e classifica ogni brano in **9 mood** (Energico, Positivo, Aggressivo, Malinconico, Romantico, Rilassato, Nostalgico, Concentrazione, Festivo) usando analisi DSP on‑device.

## ✨ Caratteristiche

- 🎧 **Player nativo** con mini-player persistente
- 🧠 **Motore mood v5**: 13 MFCC, chroma, centroide/rolloff/flatness spettrali, onset detection, key/mode (Krumhansl-Schmuckler), tempo da autocorrelazione
- 📊 **Modello Valenza/Arousal** (Russell) → 9 mood con score di confidenza
- 🫧 **BubbleMap interattiva** nello spazio emotivo bidimensionale
- 📈 **Statistiche** sulla composizione mood della libreria
- 🌗 Material 3 con tema chiaro/scuro

## 🛠️ Stack tecnico

- **Kotlin** (UI, player, ViewModel, coroutines)
- **Python 3.11 + Chaquopy** (analizzatore DSP)
- **NumPy + SciPy** (FFT, filtraggio, autocorrelazione)
- **Mutagen** (lettura tag ID3/Vorbis)
- **Material Components 3**

## 🚀 Build

Requisiti:
- Android Studio Hedgehog o superiore
- JDK 17
- Android SDK 34
- minSdk 24, targetSdk 34

```bash
./gradlew assembleDebug
```

L'APK viene generato in `app/build/outputs/apk/debug/`.

## 🧪 Test del motore Python (standalone)

```bash
cd app/src/main/python
python music_analyzer.py
```

## 📐 Architettura del classificatore

```
PCM audio
  ↓ downsample 22050 Hz, mono
  ↓ 5 finestre da 4s, ordinate per energia
  ↓ FFT + framing (frame 2048, hop 512)
Feature extraction
  • RMS, ZCR, varianza energetica
  • Centroide, rolloff, flatness, bandwidth spettrali
  • 13 MFCC
  • Chroma 12-bin → key/mode (Krumhansl-Schmuckler)
  • Onset detection (spectral flux) → density + BPM
  ↓
Modello Valenza/Arousal (Russell)
  ↓
Distanza euclidea pesata vs 9 centroidi mood
  ↓
{mood, confidence, valence, arousal, ...}
```

## 📝 Licenza

MIT — vedi LICENSE.

## 🙋 Autore

https://github.com/lucatrombetta090-sys
