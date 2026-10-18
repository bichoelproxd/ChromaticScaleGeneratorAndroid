#!/usr/bin/env python3
"""Crea muestras de prueba (1.wav, 2.wav, 3.wav) con tonos conocidos, con distintas frecuencias de
muestreo y canales, para probar el motor de Praat sin necesitar muestras reales.

Uso: make_samples.py <carpeta_destino>
"""
import sys
from pathlib import Path

import numpy as np
from scipy.io import wavfile


def tone(freq, seconds, rate, harmonics=(1.0,), channels=1):
    t = np.arange(int(round(seconds * rate))) / rate
    signal = sum(a * np.sin(2 * np.pi * freq * (k + 1) * t) for k, a in enumerate(harmonics))
    signal = signal / max(1e-9, np.max(np.abs(signal))) * 0.6
    # Pequeno fundido de entrada/salida para que no haya clics
    fade = int(0.01 * rate)
    env = np.ones_like(signal)
    env[:fade] = np.linspace(0, 1, fade)
    env[-fade:] = np.linspace(1, 0, fade)
    signal = signal * env
    if channels == 2:
        signal = np.stack([signal, signal * 0.8], axis=1)
    return (signal * 32767).astype(np.int16)


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    wavfile.write(out / "1.wav", 44100, tone(220.0, 0.40, 44100, harmonics=(1.0, 0.4, 0.2)))
    wavfile.write(out / "2.wav", 48000, tone(330.0, 0.50, 48000, harmonics=(1.0, 0.3), channels=2))
    wavfile.write(out / "3.wav", 22050, tone(180.0, 0.30, 22050, harmonics=(1.0, 0.5, 0.25)))
    print("Muestras creadas en", out)


if __name__ == "__main__":
    main()
