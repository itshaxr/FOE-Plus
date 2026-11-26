"""FOE+'s own sounds, made from scratch (no recordings, no borrowed melodies): each one is built here from
oscillators, noise and envelopes, then written as a WAV. python3 foe_sounds.py <out_dir>"""
import sys, os
import numpy as np
from scipy.io import wavfile
from scipy.signal import lfilter

SR = 48000
rng = np.random.default_rng(20261126)


def midi(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def tline(d):
    return np.arange(int(d * SR)) / SR


def adsr(n, a=0.005, d=0.05, s=0.7, r=0.05):
    t = np.arange(n) / SR
    L = n / SR
    e = np.full(n, s, dtype=float)
    e[t < a] = t[t < a] / max(a, 1e-6)
    m = (t >= a) & (t < a + d)
    e[m] = 1 - (1 - s) * (t[m] - a) / max(d, 1e-6)
    tail = t > L - r
    e[tail] *= np.clip((L - t[tail]) / max(r, 1e-6), 0, 1)
    return e


def expdecay(n, rate):
    return np.exp(-np.arange(n) / SR * rate)


def phase(freq, n):
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    return np.cumsum(f / SR)


def sine(freq, d):
    n = int(d * SR)
    return np.sin(2 * np.pi * phase(freq, n))


def square(freq, d, duty=0.5):
    n = int(d * SR)
    p = phase(freq, n) % 1
    return np.where(p < duty, 1.0, -1.0)


def saw(freq, d):
    n = int(d * SR)
    return 2 * (phase(freq, n) % 1) - 1


def tri(freq, d):
    n = int(d * SR)
    p = phase(freq, n) % 1
    return 2 * np.abs(2 * p - 1) - 1


def noise(d):
    return rng.uniform(-1, 1, int(d * SR))


def lowpass(x, cutoff):
    """One-pole low-pass; cutoff may change over time (an array as long as x)."""
    c = np.broadcast_to(np.asarray(cutoff, dtype=float), x.shape)
    a = np.exp(-2 * np.pi * np.clip(c, 20, SR / 2.2) / SR)
    y = np.empty_like(x)
    prev = 0.0
    for i in range(len(x)):
        prev = (1 - a[i]) * x[i] + a[i] * prev
        y[i] = prev
    return y


def lowpass_fixed(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    return lfilter([1 - a], [1, -a], x)


def highpass_fixed(x, cutoff):
    return x - lowpass_fixed(x, cutoff)


def bandpass_fixed(x, lo, hi):
    return lowpass_fixed(highpass_fixed(x, lo), hi)


def soften(x, cutoff=7000):
    return lowpass_fixed(x, cutoff)


def bell(freq, d, ratio=3.5, index=2.0, decay=4.5):
    t = tline(d)
    mod = np.sin(2 * np.pi * freq * ratio * t) * index * np.exp(-t * 6)
    return np.sin(2 * np.pi * freq * t + mod) * np.exp(-t * decay)


def pluck(freq, d, decay=6.0):
    t = tline(d)
    return (np.sin(2 * np.pi * freq * t) + 0.4 * np.sin(4 * np.pi * freq * t) + 0.15 * np.sin(6 * np.pi * freq * t)) * np.exp(-t * decay)


def glide(f0, f1, d, curve=1.0):
    t = np.linspace(0, 1, int(d * SR))
    return f0 * (f1 / f0) ** (t ** curve)


def fit(a, n):
    """Exactly n long (cut, or held at its last value)."""
    a = np.asarray(a, dtype=float)
    return a[:n] if len(a) >= n else np.concatenate([a, np.full(n - len(a), a[-1])])


def mix(length, *parts):
    """parts: (signal, start seconds, gain)."""
    out = np.zeros(int(length * SR))
    for sig, at, g in parts:
        i = int(at * SR)
        if i >= len(out):
            continue
        k = min(len(sig), len(out) - i)
        out[i:i + k] += sig[:k] * g
    return out


def seq(notes, wave="square", duty=0.25, gap=0.92, env=(0.004, 0.04, 0.75, 0.03), cutoff=6500):
    """notes: (midi or None, seconds). A melody line."""
    total = sum(d for _, d in notes)
    out = np.zeros(int(total * SR) + 1)
    t0 = 0.0
    for m, d in notes:
        if m is not None:
            dd = d * gap
            if wave == "square":
                s = square(midi(m), dd, duty)
            elif wave == "tri":
                s = tri(midi(m), dd)
            elif wave == "saw":
                s = saw(midi(m), dd)
            else:
                s = sine(midi(m), dd)
            s = s * adsr(len(s), *env)
            i = int(t0 * SR)
            out[i:i + len(s)] += s[:len(out) - i]
        t0 += d
    return soften(out, cutoff)


def reverb(x, mix_=0.25, tail=0.6):
    out = np.concatenate([x, np.zeros(int(tail * SR))])
    wet = np.zeros_like(out)
    for dly, g in [(0.029, 0.55), (0.043, 0.45), (0.067, 0.38), (0.097, 0.3), (0.131, 0.22), (0.173, 0.16)]:
        k = int(dly * SR)
        wet[k:k + len(x)] += x * g
    wet = lowpass_fixed(wet, 5000)
    return out + wet * mix_


def bitcrush(x, bits=5, hold=6):
    y = np.repeat(x[::hold], hold)[:len(x)]
    q = 2 ** bits
    return np.round(y * q) / q


def finish(x, target_rms=0.17, fade=0.01):
    x = x - np.mean(x)
    n = int(fade * SR)
    if len(x) > 2 * n:
        x[:n] *= np.linspace(0, 1, n)
        x[-n:] *= np.linspace(1, 0, n)
    # trim silence at the end
    nz = np.where(np.abs(x) > 1e-4)[0]
    if len(nz):
        x = x[:nz[-1] + int(0.02 * SR)]
    rms = np.sqrt(np.mean(x ** 2)) + 1e-9
    x = x * (target_rms / rms)
    peak = np.max(np.abs(x))
    if peak > 0.95:
        x = x * (0.95 / peak)
    return x


# ---------------------------------------------------------------------------------------------------------------
# The sounds. Each returns a mono signal.

def coin_pickup():
    # two metal clinks, a little apart, then a short ring (a coin dropped on a counter)
    a = bell(midi(93), 0.35, ratio=2.76, index=1.6, decay=12)
    b = bell(midi(100), 0.5, ratio=2.76, index=1.2, decay=7)
    return reverb(mix(0.6, (a, 0, 0.55), (b, 0.07, 0.6)), 0.2, 0.3)


def one_up_chime():
    # a rising, rippling bell figure: G, D, B, then the high G held
    parts = [(bell(midi(m), 0.6, ratio=2.0, index=1.0, decay=6), i * 0.06, 0.45) for i, m in enumerate([79, 86, 83, 91])]
    parts.append((pluck(midi(98), 0.5, 5), 0.24, 0.25))
    return reverb(mix(0.8, *parts), 0.25, 0.35)


def extra_life_jingle():
    notes = [(72, 0.09), (74, 0.09), (77, 0.09), (81, 0.09), (79, 0.09), (84, 0.45)]
    lead = seq(notes, "square", 0.25)
    bass = seq([(48, 0.36), (53, 0.54)], "tri", gap=0.95)
    return reverb(mix(1.1, (lead, 0, 1), (bass, 0, 0.8)), 0.2, 0.3)


def power_up():
    # a smooth rising sweep with a shimmer on top, ending on a bright chord
    d = 0.7
    f = glide(260, 1300, d, 0.8) * (1 + 0.03 * np.sin(2 * np.pi * 11 * tline(d)))
    sweep = tri(f, d) * adsr(int(d * SR), 0.01, 0.05, 0.8, 0.1)
    chord = sum(bell(midi(m), 0.6, ratio=2.0, index=0.8, decay=5) for m in (84, 88, 91)) / 3
    return reverb(mix(1.3, (sweep, 0, 0.7), (chord, d - 0.05, 0.8)), 0.25, 0.4)


def boing_jump():
    d = 0.5
    f = glide(220, 880, d, 0.5) * (1 + 0.05 * np.sin(2 * np.pi * 14 * tline(d)) * np.exp(-tline(d) * 3))
    s = square(f, d, 0.3) * expdecay(int(d * SR), 5.5)
    return soften(s, 5000)


def stomp():
    d = 0.35
    body = sine(glide(180, 45, d, 0.5), d) * expdecay(int(d * SR), 9)
    squish = lowpass_fixed(noise(0.12), 1800) * expdecay(int(0.12 * SR), 30)
    return mix(d, (body, 0, 1), (squish, 0, 0.6))


def pause_blip():
    a = square(midi(81), 0.08, 0.5) * adsr(int(0.08 * SR), 0.002, 0.02, 0.7, 0.02)
    b = square(midi(88), 0.12, 0.5) * adsr(int(0.12 * SR), 0.002, 0.02, 0.7, 0.05)
    return reverb(soften(mix(0.35, (a, 0, 1), (b, 0.14, 1)), 5500), 0.2, 0.3)


def ouch_hit():
    d = 0.3
    zap = square(glide(700, 140, d, 0.7), d, 0.5) * expdecay(int(d * SR), 9)
    crack = highpass_fixed(noise(0.05), 2000) * expdecay(int(0.05 * SR), 60)
    return soften(mix(d, (zap, 0, 0.8), (crack, 0, 0.5)), 6000)


def encounter_alert():
    parts = []
    for i, m in enumerate([81, 85, 88]):
        s = square(midi(m), 0.07, 0.5) * adsr(int(0.07 * SR), 0.002, 0.01, 0.9, 0.01)
        parts.append((s, i * 0.075, 1))
    hold = square(midi(93), 0.35, 0.25) * adsr(int(0.35 * SR), 0.002, 0.05, 0.6, 0.2)
    parts.append((hold, 0.24, 0.9))
    return reverb(soften(mix(0.7, *parts), 6500), 0.15, 0.25)


def battle_riff():
    # a driving E minor riff: E E G E B A G F# E
    notes = [(64, 0.09), (64, 0.09), (67, 0.09), (64, 0.09), (71, 0.18), (69, 0.09), (67, 0.09), (66, 0.18), (64, 0.27)]
    lead = seq(notes, "square", 0.5, gap=0.85)
    bass = seq([(40, 0.18), (40, 0.18), (43, 0.18), (45, 0.18), (40, 0.45)], "tri", gap=0.9)
    return reverb(mix(1.2, (lead, 0, 1), (bass, 0, 0.7)), 0.12, 0.2)


def battle_won():
    lead = seq([(67, 0.1), (72, 0.1), (76, 0.1), (79, 0.15), (76, 0.08), (79, 0.5)], "square", 0.25)
    bass = seq([(48, 0.3), (43, 0.25), (48, 0.5)], "tri", gap=0.95)
    return reverb(mix(1.1, (lead, 0, 1), (bass, 0, 0.8)), 0.2, 0.3)


def you_win():
    def stab(d):
        return sum(saw(midi(m), d) for m in (60, 64, 67, 72)) / 4 * adsr(int(d * SR), 0.004, 0.06, 0.6, 0.08)
    x = mix(1.1, (lowpass_fixed(stab(0.16), 3500), 0, 1), (lowpass_fixed(stab(0.75), 3500), 0.2, 1.1))
    sparkle = sum((bell(midi(m), 0.6) for m in (96, 100, 103)))
    return reverb(mix(1.2, (x, 0, 1), (sparkle, 0.22, 0.25)), 0.25, 0.4)


def victory_fanfare():
    # a brass fanfare: C E G, up to the high C, A G A, and the high C held
    lead = seq([(72, 0.18), (76, 0.09), (79, 0.27), (84, 0.45), (81, 0.18), (79, 0.18), (81, 0.18), (84, 1.2)],
               "saw", gap=0.9, env=(0.01, 0.06, 0.8, 0.08), cutoff=3800)
    harm = seq([(64, 0.54), (72, 0.45), (77, 0.54), (76, 1.2)], "saw", gap=0.95,
               env=(0.02, 0.06, 0.8, 0.1), cutoff=2500)
    bass = seq([(48, 0.54), (53, 0.45), (55, 0.54), (48, 1.5)], "tri", gap=0.95)
    return reverb(mix(3.3, (lead, 0, 0.9), (harm, 0, 0.45), (bass, 0, 0.7)), 0.3, 0.6)


def victory_jingle():
    mel = [(72, 0.12), (76, 0.12), (79, 0.12), (84, 0.30), (81, 0.12), (83, 0.12), (84, 0.12), (86, 0.12), (88, 0.55)]
    lead = seq(mel, "square", 0.25, gap=0.95)
    bass = seq([(48, 0.36), (53, 0.42), (55, 0.36), (60, 0.9)], "tri", gap=0.95)
    t0 = sum(d for _, d in mel)
    chord = sum(square(midi(m), 0.85, 0.5) for m in (72, 76, 79)) / 3 * adsr(int(0.85 * SR), 0.01, 0.1, 0.6, 0.4)
    return reverb(mix(t0 + 0.9, (lead, 0, 1), (bass, 0, 0.9), (soften(chord, 5000), t0 - 0.02, 0.5)), 0.2, 0.4)


def knight_quest_start():
    lead = seq([(67, 0.1), (72, 0.1), (74, 0.1), (76, 0.25), (74, 0.1), (79, 0.35)], "square", 0.5)
    bass = seq([(43, 0.3), (48, 0.75)], "tri", gap=0.95)
    return reverb(mix(1.05, (lead, 0, 1), (bass, 0, 0.8)), 0.2, 0.25)


def knight_victory():
    lead = seq([(79, 0.08), (81, 0.08), (83, 0.08), (86, 0.4)], "square", 0.25)
    bass = seq([(43, 0.24), (55, 0.4)], "tri", gap=0.95)
    return reverb(mix(0.75, (lead, 0, 1), (bass, 0, 0.8)), 0.2, 0.25)


def level_start():
    parts = []
    for i in range(3):
        s = square(midi(72), 0.09, 0.5) * adsr(int(0.09 * SR), 0.002, 0.02, 0.8, 0.02)
        parts.append((s, i * 0.22, 1))
    go = square(midi(84), 0.35, 0.5) * adsr(int(0.35 * SR), 0.002, 0.05, 0.7, 0.15)
    parts.append((go, 0.66, 1))
    return soften(mix(1.05, *parts), 6000)


def rare_drop():
    parts = [(bell(midi(m), 1.0), i * 0.07, 0.5) for i, m in enumerate([79, 83, 86, 91])]
    r = np.random.default_rng(7)
    for k in range(9):
        parts.append((bell(midi(int(r.choice([95, 98, 100, 103]))), 0.5), 0.32 + k * 0.08, 0.12 * (1 - k / 9)))
    return reverb(mix(1.4, *parts), 0.35, 0.4)


def rare_find():
    parts = [(bell(midi(m), 0.9, decay=5), i * 0.05, 0.4) for i, m in enumerate([84, 88, 91, 95, 96, 100])]
    pad = sum(sine(midi(m), 1.2) for m in (72, 76, 79)) / 3 * adsr(int(1.2 * SR), 0.15, 0.2, 0.6, 0.6)
    parts.append((pad, 0, 0.35))
    return reverb(mix(1.3, *parts), 0.4, 0.5)


def save_sparkle():
    parts = [(bell(midi(m), 0.6, ratio=2.0, index=1.2, decay=7), i * 0.045, 0.45) for i, m in enumerate([88, 84, 91, 96])]
    return reverb(mix(0.6, *parts), 0.3, 0.3)


def golden_fish():
    parts = []
    for i, m in enumerate([74, 76, 79, 81, 83, 86, 88, 91]):
        parts.append((pluck(midi(m), 0.6, 7), i * 0.035, 0.35))
    parts.append((bell(midi(98), 0.6), 0.3, 0.25))
    return reverb(mix(0.86, *parts), 0.3, 0.3)


def surprised_exclaim():
    pop = sine(glide(300, 900, 0.06, 0.6), 0.06) * expdecay(int(0.06 * SR), 25)
    whistle = sine(glide(900, 1800, 0.35, 0.5), 0.35) * adsr(int(0.35 * SR), 0.01, 0.05, 0.7, 0.15)
    return mix(0.6, (pop, 0, 0.8), (whistle, 0.05, 0.5))


def chompy():
    def bite():
        b = lowpass_fixed(noise(0.07), 1400) * expdecay(int(0.07 * SR), 45)
        click = square(midi(45), 0.04, 0.5) * expdecay(int(0.04 * SR), 60)
        return mix(0.08, (b, 0, 1), (click, 0, 0.4))
    return mix(0.37, (bite(), 0, 1), (bite(), 0.16, 0.9))


def honk():
    d = 0.32
    f = 330 * (1 + 0.02 * np.sin(2 * np.pi * 7 * tline(d)))
    s = (saw(f, d) + 0.6 * saw(f * 1.5, d)) * adsr(int(d * SR), 0.01, 0.05, 0.8, 0.05)
    nasal = bandpass_fixed(s, 500, 2200)
    return nasal


def snipe():
    crack = highpass_fixed(noise(0.07), 1500) * expdecay(int(0.07 * SR), 70)
    thump = sine(glide(160, 60, 0.07), 0.07) * expdecay(int(0.07 * SR), 50)
    return mix(0.08, (crack, 0, 1), (thump, 0, 0.6))


def squeak():
    d = 0.1
    f = 1400 + 900 * np.sin(np.pi * np.linspace(0, 1, int(d * SR)))
    return sine(f, d) * adsr(int(d * SR), 0.005, 0.02, 0.8, 0.03)


def tiny_toss():
    d = 0.6
    t = tline(d)
    cutoff = 400 + 4000 * np.sin(np.pi * np.clip(t / 0.45, 0, 1))
    whoosh = lowpass(noise(d), cutoff) * np.sin(np.pi * np.clip(t / 0.5, 0, 1)) ** 2
    pop = sine(glide(500, 1200, 0.05), 0.05) * expdecay(int(0.05 * SR), 30)
    return mix(0.88, (whoosh, 0, 1.4), (pop, 0.55, 0.5))


def dino_mount():
    d = 0.5
    bloop = sine(glide(180, 520, d, 0.6), d) * adsr(int(d * SR), 0.01, 0.1, 0.7, 0.2)
    wob = 1 + 0.25 * np.sin(2 * np.pi * 9 * tline(d))
    pop = sine(glide(600, 1400, 0.08), 0.08) * expdecay(int(0.08 * SR), 25)
    return reverb(mix(1.0, (bloop * wob, 0, 1), (pop, 0.5, 0.6)), 0.2, 0.3)


def puffball_faint():
    d = 2.2
    f = glide(880, 110, d, 1.4) * (1 + 0.04 * np.sin(2 * np.pi * 6 * tline(d)))
    s = square(f, d, 0.3) * adsr(int(d * SR), 0.01, 0.1, 0.8, 0.4)
    return reverb(soften(s, 4500), 0.25, 0.5)


def fallen_giant():
    d = 1.4
    groan = saw(glide(140, 55, 0.9, 0.8), 0.9) * adsr(int(0.9 * SR), 0.05, 0.1, 0.8, 0.3)
    groan = lowpass_fixed(groan, 700)
    thud = sine(glide(90, 35, 0.5, 0.5), 0.5) * expdecay(int(0.5 * SR), 6)
    rumble = lowpass_fixed(noise(0.6), 300) * expdecay(int(0.6 * SR), 5)
    return reverb(mix(d, (groan, 0, 0.8), (thud, 0.85, 1.2), (rumble, 0.85, 1.0)), 0.3, 0.6)


def glitchy_vanish():
    d = 1.6
    f = glide(1600, 120, d, 0.7)
    s = square(f, d, 0.5) * adsr(int(d * SR), 0.005, 0.1, 0.8, 0.5)
    # stutters: gaps that come quicker toward the end
    t = tline(d)
    gate = (np.sin(2 * np.pi * (6 + 20 * t) * t) > -0.3).astype(float)
    x = bitcrush(s * gate, 4, 8)
    return reverb(soften(x, 5500), 0.3, 0.6)


def mystery_sound():
    d = 1.3
    t = tline(d)
    f = 520 + 160 * np.sin(2 * np.pi * 1.6 * t) + 25 * np.sin(2 * np.pi * 6 * t)
    s = sine(f, d) * adsr(int(d * SR), 0.15, 0.2, 0.7, 0.4)
    under = sine(f * 0.5, d) * adsr(int(d * SR), 0.2, 0.2, 0.5, 0.4)
    return reverb(mix(d, (s, 0, 0.8), (under, 0, 0.3)), 0.45, 0.6)


def deep_sea_bubbles():
    d = 5.0
    t = tline(d)
    drone = (sine(55, d) + 0.5 * sine(82.5 * (1 + 0.003 * np.sin(2 * np.pi * 0.3 * t)), d)) * adsr(int(d * SR), 0.8, 0.5, 0.8, 1.2)
    parts = [(lowpass_fixed(drone, 400), 0, 0.6)]
    r = np.random.default_rng(3)
    for k in range(18):
        bd = float(r.uniform(0.04, 0.1))
        f0 = float(r.uniform(300, 900))
        b = sine(glide(f0, f0 * 2.2, bd, 0.5), bd) * expdecay(int(bd * SR), 25)
        parts.append((b, float(r.uniform(0.3, 4.6)), float(r.uniform(0.15, 0.35))))
    return reverb(mix(d, *parts), 0.4, 0.8)


def sea_beast_roar():
    d = 2.8
    t = tline(d)
    f = glide(70, 110, 1.0, 0.6)
    f = fit(np.concatenate([f, glide(110, 60, d - 1.0, 1.2)]), len(t))
    growl = saw(f * (1 + 0.03 * np.sin(2 * np.pi * 23 * t)), d) + 0.5 * saw(f * 1.01, d)
    growl = lowpass_fixed(growl, 900)
    breath = lowpass_fixed(noise(d), 1200)
    env = adsr(int(d * SR), 0.25, 0.3, 0.8, 0.8)
    return reverb((growl * 0.8 + breath * 0.35) * env, 0.35, 0.8)


def yeti_howl():
    d = 0.8
    t = tline(d)
    f = glide(300, 520, 0.3, 0.6)
    f = fit(np.concatenate([f, 520 - 260 * (np.arange(int((d - 0.3) * SR)) / SR / (d - 0.3))]), len(t))
    voice = sine(f * (1 + 0.02 * np.sin(2 * np.pi * 6 * t)), d) + 0.4 * sine(f * 2, d) + 0.2 * saw(f, d)
    wind = bandpass_fixed(noise(d), 1500, 6000) * 0.3
    return reverb((lowpass_fixed(voice, 2500) + wind) * adsr(int(d * SR), 0.05, 0.1, 0.8, 0.3), 0.4, 0.6)


def sleepy_snore():
    d = 0.75
    t = tline(d)
    inhale = lowpass(noise(0.45), 300 + 500 * np.linspace(0, 1, int(0.45 * SR))) * np.sin(np.pi * np.linspace(0, 1, int(0.45 * SR)))
    rattle = (1 + np.sin(2 * np.pi * 30 * tline(0.45))) * 0.5
    whistle = sine(glide(1600, 1200, 0.25), 0.25) * adsr(int(0.25 * SR), 0.03, 0.05, 0.6, 0.1)
    return mix(d, (inhale * rattle, 0, 1.5), (whistle, 0.48, 0.25))


def jackpot_spin():
    parts = []
    tick_at = 0.0
    for k in range(14):
        tk = highpass_fixed(noise(0.012), 2500) * expdecay(int(0.012 * SR), 200)
        parts.append((tk, tick_at, 0.6))
        tick_at += 0.035 + k * 0.006
    for i, m in enumerate([84, 88, 91]):
        parts.append((bell(midi(m), 0.7, ratio=2.0, index=1.5, decay=4), tick_at + i * 0.09, 0.45))
    return reverb(mix(tick_at + 0.9, *parts), 0.25, 0.4)


def money():
    ding = bell(midi(91), 0.9, ratio=2.76, index=1.8, decay=4) + 0.5 * bell(midi(98), 0.9, ratio=2.0, index=1.0, decay=5)
    slide = bandpass_fixed(noise(0.25), 600, 3500) * np.sin(np.pi * np.linspace(0, 1, int(0.25 * SR)))
    clunk = sine(glide(220, 90, 0.08), 0.08) * expdecay(int(0.08 * SR), 30)
    return reverb(mix(1.3, (ding, 0, 0.6), (slide, 0.3, 0.35), (clunk, 0.55, 0.6)), 0.25, 0.4)


def crab_dance():
    beat = 0.15
    bassline = [45, None, 45, 57, None, 45, None, 55, 43, None, 43, 55, None, 48, 50, 52]
    notes = [(m, beat) for m in bassline] * 2
    bass = seq(notes, "saw", gap=0.7, env=(0.004, 0.05, 0.6, 0.03), cutoff=1200)
    lead_notes = [(76, beat), (None, beat), (76, beat), (79, beat), (81, beat * 2), (79, beat), (76, beat),
                  (74, beat * 2), (None, beat), (72, beat), (74, beat), (76, beat * 3)]
    lead = seq(lead_notes * 2, "square", 0.25, gap=0.8)
    total = beat * 32
    hats = []
    for k in range(32):
        h = highpass_fixed(noise(0.03), 6000) * expdecay(int(0.03 * SR), 120)
        hats.append((h, k * beat, 0.25 if k % 2 else 0.12))
    kicks = [(sine(glide(140, 45, 0.15), 0.15) * expdecay(int(0.15 * SR), 18), k * beat * 4, 1.0) for k in range(8)]
    x = mix(total, (bass, 0, 0.9), (lead, 0, 0.45), *hats, *kicks)
    return reverb(x, 0.15, 0.3)


def hype_horn():
    def stab(d):
        return sum(saw(midi(m) * (1 + 0.004 * k), d) for k, m in enumerate((58, 62, 65, 70))) / 4
    a = lowpass(stab(0.22), np.linspace(800, 3500, int(0.22 * SR))) * adsr(int(0.22 * SR), 0.01, 0.05, 0.8, 0.05)
    b = lowpass(stab(0.9), np.linspace(900, 4500, int(0.9 * SR))) * adsr(int(0.9 * SR), 0.02, 0.1, 0.8, 0.4)
    rise = sine(glide(400, 1400, 0.9), 0.9) * adsr(int(0.9 * SR), 0.3, 0.1, 0.4, 0.3) * 0.2
    return reverb(mix(1.4, (a, 0, 1), (b, 0.3, 1.1), (rise, 0.3, 1)), 0.3, 0.5)


def web_hero_sting():
    d = 3.2
    t = tline(d)
    swoosh = lowpass(noise(0.6), 300 + 6000 * np.linspace(0, 1, int(0.6 * SR)) ** 2) * np.linspace(0, 1, int(0.6 * SR)) ** 2
    hit = sum(saw(midi(m), 2.4) for m in (38, 50, 53, 57)) / 4
    hit = lowpass(hit, 400 + 3000 * np.exp(-tline(2.4) * 2.5)) * adsr(int(2.4 * SR), 0.005, 0.3, 0.6, 1.2)
    boom = sine(glide(120, 35, 0.8), 0.8) * expdecay(int(0.8 * SR), 4)
    shimmer = sum(bell(midi(m), 2.0, decay=2) for m in (86, 89, 93)) * 0.15
    return reverb(mix(d, (swoosh, 0, 0.8), (hit, 0.6, 0.9), (boom, 0.6, 0.9), (shimmer, 0.62, 1)), 0.35, 0.8)


def wah_wah_fail():
    parts = []
    t0 = 0.0
    for i, (m, d) in enumerate([(58, 0.3), (57, 0.3), (56, 0.3), (55, 0.9)]):
        n = int(d * SR)
        tt = np.arange(n) / SR
        vib = 0.012 if i == 3 else 0.0
        f = midi(m) * (1 + vib * np.sin(2 * np.pi * 5.5 * tt))
        s = square(f, d, 0.5)
        w = np.sin(np.pi * np.clip(tt / d, 0, 1)) ** 0.7
        s = lowpass(s, 300 + 2200 * w) * adsr(n, 0.02, 0.05, 0.85, 0.08)
        parts.append((s, t0, 1))
        t0 += d
    return reverb(mix(t0, *parts), 0.15, 0.3)


SOUNDS = {
    "coin_pickup": coin_pickup, "one_up_chime": one_up_chime, "extra_life_jingle": extra_life_jingle, "power_up": power_up,
    "boing_jump": boing_jump, "stomp": stomp, "pause_blip": pause_blip, "ouch_hit": ouch_hit, "encounter_alert": encounter_alert,
    "battle_riff": battle_riff, "battle_won": battle_won, "you_win": you_win, "victory_fanfare": victory_fanfare,
    "victory_jingle": victory_jingle, "knight_quest_start": knight_quest_start, "knight_victory": knight_victory,
    "level_start": level_start, "rare_drop": rare_drop, "rare_find": rare_find, "save_sparkle": save_sparkle,
    "golden_fish": golden_fish, "surprised_exclaim": surprised_exclaim, "chompy": chompy, "honk": honk, "snipe": snipe,
    "squeak": squeak, "tiny_toss": tiny_toss, "dino_mount": dino_mount, "puffball_faint": puffball_faint,
    "fallen_giant": fallen_giant, "glitchy_vanish": glitchy_vanish, "mystery_sound": mystery_sound,
    "deep_sea_bubbles": deep_sea_bubbles, "sea_beast_roar": sea_beast_roar, "yeti_howl": yeti_howl,
    "sleepy_snore": sleepy_snore, "jackpot_spin": jackpot_spin, "money": money, "crab_dance": crab_dance,
    "hype_horn": hype_horn, "web_hero_sting": web_hero_sting, "wah_wah_fail": wah_wah_fail,
}

if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    only = sys.argv[2:]
    for name, fn in SOUNDS.items():
        if only and name not in only:
            continue
        x = finish(fn())
        stereo = np.stack([x, np.roll(x, 60)], axis=1)
        wavfile.write(os.path.join(out, name + ".wav"), SR, (np.clip(stereo, -1, 1) * 32767).astype(np.int16))
        print(f"{name:20s} {len(x) / SR:5.2f}s")
