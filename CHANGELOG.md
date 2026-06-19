# Changelog

## [0.5.0] - 2026-06-19
### Added
- Motore di analisi v5 con MFCC, chroma, key detection
- Modello Valenza/Arousal bidimensionale
- Output strutturato con confidence score
- Self-test integrato nel modulo Python

### Changed
- Migrazione da 1 finestra fissa a 5 finestre con ordinamento per energia
- Downsample a 22050 Hz per dimezzare il costo computazionale

### Dependencies
- Aggiunto: scipy
- Mantenuto: numpy, mutagen
