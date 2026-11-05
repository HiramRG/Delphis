# Original, calm "console menu" loop for Delphis (composed for this app; not based on any existing song).
import numpy as np, scipy.io.wavfile as wav, sys
SR = 44100
BPM = 96
BEAT = 60.0 / BPM
BARS = 16
LEN = int(SR * BEAT * 4 * BARS)
L = np.zeros(LEN); R = np.zeros(LEN)

def hz(n): return 440.0 * 2 ** ((n - 69) / 12)
def add(sig, start_s, pan=0.0, gain=1.0):
    i = int(start_s * SR); n = len(sig)
    idx = (np.arange(n) + i) % LEN  # wrap around so the loop is seamless
    lg = gain * np.sqrt(0.5 * (1 - pan)); rg = gain * np.sqrt(0.5 * (1 + pan))
    np.add.at(L, idx, sig * lg); np.add.at(R, idx, sig * rg)

def env(n, a, d_tau):
    t = np.arange(n) / SR
    e = np.exp(-t / d_tau)
    att = int(a * SR)
    if att > 0: e[:att] *= np.linspace(0, 1, att)
    return e

def epiano(note, dur):  # soft FM electric piano
    n = int((dur + 1.2) * SR); t = np.arange(n) / SR; f = hz(note)
    mod = np.sin(2 * np.pi * f * 2 * t) * 1.2 * np.exp(-t / 0.4)
    s = np.sin(2 * np.pi * f * t + mod) * env(n, 0.005, 0.9)
    rel = np.ones(n); off = int(dur * SR); rel[off:] = np.exp(-np.arange(n - off) / SR / 0.15)
    return s * rel

def mallet(note, dur=0.6):  # marimba / vibes-like
    n = int((dur + 0.8) * SR); t = np.arange(n) / SR; f = hz(note)
    s = (np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * f * 4 * t) * np.exp(-t / 0.05)) * env(n, 0.002, 0.45)
    s *= 1 + 0.15 * np.sin(2 * np.pi * 5.5 * t)  # gentle vibrato-tremolo
    return s

def bass(note, dur):
    n = int((dur + 0.2) * SR); t = np.arange(n) / SR; f = hz(note)
    s = (np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * 2 * f * t)) * env(n, 0.01, 0.6)
    off = int(dur * SR); s[off:] *= np.exp(-np.arange(n - off) / SR / 0.05)
    return s

def shaker(dur=0.08):
    n = int(dur * SR); rng = np.random.default_rng(7)
    noise = rng.standard_normal(n)
    noise = np.diff(np.concatenate([[0], noise]))  # brighten
    return noise * env(n, 0.003, 0.025)

# Chords per bar (MIDI notes) — Fmaj9, Dm9, Bbmaj7, C6/9 style, two passes with a turn
F, Dm, Bb, C, Am, Gm = [53, 57, 60, 64, 67], [50, 53, 57, 60, 64], [46, 53, 57, 62, 65], [48, 55, 57, 62, 64], [45, 52, 55, 60, 64], [43, 50, 53, 57, 62]
prog = [F, Dm, Bb, C, F, Am, Gm, C, Bb, C, Am, Dm, Gm, C, F, C]
roots = [41, 38, 46, 36, 41, 45, 43, 36, 46, 36, 45, 38, 43, 36, 41, 36]
bar = BEAT * 4
for b, (ch, r) in enumerate(zip(prog, roots)):
    t0 = b * bar
    # comp: chord on beat 1 and the "and" of 2, softly
    for off, g in ((0, 0.11), (1.5 * BEAT, 0.07), (3 * BEAT, 0.05)):
        for k, nt in enumerate(ch):
            add(epiano(nt, BEAT * 1.2), t0 + off + k * 0.008, pan=-0.3 + 0.15 * k, gain=g)
    # bass: root on 1, fifth on 3, little pickup
    add(bass(r, BEAT * 1.6), t0, gain=0.28)
    add(bass(r + 7, BEAT * 0.9), t0 + 2 * BEAT, gain=0.22)
    add(bass(r + 12, BEAT * 0.4), t0 + 3.5 * BEAT, gain=0.16)
    # shaker on eighths, accent off-beats
    for e in range(8):
        add(shaker(), t0 + e * BEAT / 2, pan=0.4, gain=0.05 if e % 2 else 0.025)

# Original melody (beat offsets within the loop, MIDI note, length in beats)
mel = [
    (0, 72, 1), (1, 74, .5), (1.5, 76, 1.5), (3.5, 79, .5),
    (4, 77, 1), (5, 76, .5), (5.5, 74, 1), (6.5, 72, 1.5),
    (8, 70, 1), (9, 72, .5), (9.5, 74, 1), (10.5, 77, 1.5),
    (12, 76, 2), (14, 74, .5), (14.5, 72, .5), (15, 74, 1),
    (16, 72, 1), (17, 76, .5), (17.5, 79, 1), (18.5, 81, 1.5),
    (20, 79, .5), (20.5, 76, .5), (21, 72, 1), (22, 76, 2),
    (24, 74, 1), (25, 72, .5), (25.5, 70, 1), (26.5, 69, 1.5),
    (28, 72, 3), (31, 67, 1),
    (32, 70, 1), (33, 74, .5), (33.5, 77, 1.5), (35.5, 76, .5),
    (36, 72, 1), (37, 74, .5), (37.5, 76, 2.5),
    (40, 76, 1), (41, 74, .5), (41.5, 72, 1), (42.5, 69, 1.5),
    (44, 74, 1), (45, 77, .5), (45.5, 76, 1), (46.5, 74, 1.5),
    (48, 74, 1), (49, 72, .5), (49.5, 70, 1), (50.5, 74, 1.5),
    (52, 72, 1), (53, 74, .5), (53.5, 76, 2.5),
    (56, 77, 1), (57, 76, .5), (57.5, 72, 1), (58.5, 69, 1.5),
    (60, 72, 2.5), (62.5, 74, .5), (63, 76, 1),
]
for beat, nt, ln in mel:
    t = beat * BEAT
    add(mallet(nt, ln * BEAT), t, pan=0.25, gain=0.20)
    add(mallet(nt + 12, ln * BEAT) * 0.3, t + 0.012, pan=0.35, gain=0.06)  # sparkle octave

# Simple stereo "room": a few decaying echoes (wrapped, so the loop stays seamless)
def room(x, delays=(0.031, 0.047, 0.071, 0.113, 0.167), g=0.22):
    y = x.copy()
    for k, d in enumerate(delays):
        y += np.roll(x, int(d * SR)) * g * (0.75 ** k)
    return y
L2, R2 = room(L), room(R, (0.037, 0.053, 0.079, 0.121, 0.181))
mix = np.stack([L2, R2], axis=1)
# gentle low-pass to soften
from scipy.signal import butter, sosfiltfilt
mix = sosfiltfilt(butter(2, 7000, fs=SR, output='sos'), mix, axis=0)
mix /= np.max(np.abs(mix)) * 1.12
wav.write(sys.argv[1], SR, (mix * 32767).astype(np.int16))
print("seconds:", LEN / SR)
