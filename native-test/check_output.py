#!/usr/bin/env python3
"""Comprueba la salida del motor de Praat (chromatic.wav y pitched_samples/).

Comprueba:
  1. chromatic.wav = nota 1, silencio, nota 2, silencio, ... (mismo orden y exactamente los mismos
     datos que los archivos de pitched_samples/, que ademas debe haber uno por semitono).
  2. Todos los silencios miden lo que corresponde a la separacion pedida (a 48 kHz).
  3. Con "pitched": la frecuencia fundamental de cada nota es la de la escala cromatica pedida
     (C1 = 32.703 Hz; nota i -> 32.703 * 2^((i + nota_inicial + 12 * octava + 12) / 12)).

Uso: check_output.py <carpeta_salida> <nota_inicial 0-11> <octava 0-2> <rango> <gap_s> <pitched 0|1>
"""
import sys
from pathlib import Path

import numpy as np
from scipy.io import wavfile

RATE = 48000


def f0_autocorr(x, rate, fmin=40.0, fmax=700.0):
    x = x - np.mean(x)
    n = len(x)
    size = 1 << (2 * n - 1).bit_length()
    spectrum = np.fft.rfft(x, size)
    ac = np.fft.irfft(spectrum * np.conj(spectrum))[:n]
    ac = ac / ac[0]
    lo, hi = int(rate / fmax), min(int(rate / fmin), n - 1)
    lag = lo + int(np.argmax(ac[lo:hi]))
    # Interpolacion parabolica del maximo
    if 0 < lag < n - 1:
        a, b, c = ac[lag - 1], ac[lag], ac[lag + 1]
        denom = a - 2 * b + c
        if denom != 0:
            lag = lag + 0.5 * (a - c) / denom
    return rate / lag


def read_mono(path):
    rate, data = wavfile.read(path)
    assert rate == RATE, f"{path.name}: frecuencia de muestreo {rate}, se esperaba {RATE}"
    assert data.ndim == 1, f"{path.name}: debe ser mono"
    assert data.dtype == np.int16, f"{path.name}: debe ser PCM de 16 bits ({data.dtype})"
    return data.astype(np.int32)


def main():
    out = Path(sys.argv[1])
    note, octave, n, gap, pitched = int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), float(sys.argv[5]), int(sys.argv[6])
    start_key = note + 12 * octave
    ok = True

    def fail(msg):
        nonlocal ok
        ok = False
        print("FALLO:", msg)

    chromatic = read_mono(out / "chromatic.wav")
    samples_dir = out / "pitched_samples"
    notes = []
    if samples_dir.is_dir():
        for i in range(1, n + 1):
            p = samples_dir / f"pitched_{i}.wav"
            if not p.is_file():
                fail(f"falta {p.name}")
                continue
            notes.append(read_mono(p))
        extra = sorted(samples_dir.glob("pitched_*.wav"))
        if len(extra) != n:
            fail(f"hay {len(extra)} archivos en pitched_samples y se esperaban {n}")
    else:
        print("(sin pitched_samples/: se omiten las comprobaciones 1 y 3)")

    expected_gap = int(round(gap * RATE))
    print(f"chromatic.wav: {len(chromatic)} muestras ({len(chromatic) / RATE:.3f} s)")

    if len(notes) == n:
        total_notes = sum(len(x) for x in notes)
        gap_len = (len(chromatic) - total_notes) / n
        print(f"notas: {total_notes} muestras; silencio medido = {gap_len} muestras (esperado {expected_gap})")
        if gap_len != expected_gap:
            fail(f"el silencio mide {gap_len} muestras y se esperaban {expected_gap}")
        # 1. Mismo orden y mismos datos
        pos = 0
        for i, x in enumerate(notes):
            seg = chromatic[pos:pos + len(x)]
            if len(seg) != len(x) or not np.array_equal(seg, x):
                fail(f"la nota {i + 1} de chromatic.wav no coincide con pitched_{i + 1}.wav")
            pos += len(x)
            gseg = chromatic[pos:pos + expected_gap]
            if len(gseg) != expected_gap or np.any(gseg != 0):
                fail(f"el silencio despues de la nota {i + 1} no es silencio")
            pos += expected_gap
        if pos != len(chromatic):
            fail("sobran muestras al final de chromatic.wav")

        # 3. Tono de cada nota
        if pitched:
            worst = 0.0
            for i, x in enumerate(notes):
                want = 32.703 * 2 ** ((i + start_key + 12) / 12)
                core = x[len(x) // 5: -len(x) // 5].astype(float)  # sin los bordes
                got = f0_autocorr(core, RATE)
                cents = 1200 * np.log2(got / want)
                worst = max(worst, abs(cents))
                flag = "" if abs(cents) < 50 else "   <-- FUERA DE TONO"
                print(f"  nota {i + 1:3d}: esperado {want:8.2f} Hz, medido {got:8.2f} Hz ({cents:+6.1f} cents){flag}")
                if abs(cents) >= 50:
                    fail(f"la nota {i + 1} esta a {cents:+.1f} cents del tono esperado")
            print(f"desviacion maxima de tono: {worst:.1f} cents")
    else:
        # Sin pitched_samples: solo se puede comprobar el largo total frente al silencio
        silence = n * expected_gap
        print(f"(largo total {len(chromatic)}; de ellos {silence} son silencio)")

    print("RESULTADO:", "OK" if ok else "FALLO")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
