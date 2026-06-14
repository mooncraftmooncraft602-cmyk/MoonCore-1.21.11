"""Génère le corpus d'entraînement (nom d'item -> rampe de 5 couleurs sombre->clair).

Sortie dans data/ :
  - data.txt    : corpus lisible "Nom => #h #h #h #h #h" (une ligne par exemple)
  - meta.pkl    : tokenizer char-level (stoi/itos, vocab_size)
  - train.bin / val.bin : tokens uint16 (si numpy dispo) pour train.py

Usage :
  python build_dataset.py --sample                 # preview (200 lignes, sans .bin)
  python build_dataset.py --target-chars 20000000  # corpus complet
"""
from __future__ import annotations
import argparse
import os
import pickle
import random

import colors
import lexicon

OUT_SEP = " => "
STOPS = 5

CSS_COLORS = {  # ancrage : quelques noms de couleurs standard -> hex (pas besoin de réseau)
    "rouge": "#ff0000", "red": "#ff0000", "vert": "#008000", "green": "#008000",
    "bleu": "#0000ff", "blue": "#0000ff", "jaune": "#ffff00", "yellow": "#ffff00",
    "orange": "#ffa500", "violet": "#8a2be2", "purple": "#800080", "rose": "#ff69b4",
    "pink": "#ff69b4", "cyan": "#00ffff", "magenta": "#ff00ff", "blanc": "#ffffff",
    "white": "#ffffff", "noir": "#101014", "black": "#101014", "gris": "#808080",
    "gray": "#808080", "or": "#ffd700", "gold": "#ffd700", "argent": "#c0c0c0",
    "silver": "#c0c0c0", "marron": "#8b4513", "brown": "#8b4513", "turquoise": "#40e0d0",
    "indigo": "#4b0082", "corail": "#ff7f50", "coral": "#ff7f50", "olive": "#808000",
    "teal": "#008080", "lime": "#bfff00", "écarlate": "#dc143c", "crimson": "#dc143c",
    "émeraude": "#50c878", "emerald": "#50c878", "saphir": "#0f52ba", "sapphire": "#0f52ba",
    "rubis": "#e0115f", "ruby": "#e0115f", "améthyste": "#9966cc", "amethyst": "#9966cc",
    "ambre": "#ffbf00", "amber": "#ffbf00", "ivoire": "#fffff0", "ivory": "#fffff0",
}


def _ramp_line(name: str, hexes: list[str]) -> str:
    return name + OUT_SEP + " ".join(hexes) + "\n"


def _theme_ramp(th: lexicon.Theme, rng: random.Random) -> list[str]:
    """Rampe d'un thème. Variation FAIBLE : signal thème->teinte net (le modèle apprend la bonne teinte)."""
    hue = th["hue"] + rng.uniform(-3, 3)
    sat = max(0.0, min(1.0, th["sat"] + rng.uniform(-0.04, 0.04)))
    hl = th["hl"]
    lo = rng.uniform(0.12, 0.15)
    hi = rng.uniform(0.88, 0.93)
    return colors.make_ramp(hue, sat, stops=STOPS, light_lo=lo, light_hi=hi,
                            highlight_hue=hl, rng=rng, hue_jitter=1.5)


def _make_name(th: lexicon.Theme, rng: random.Random) -> str:
    syn = rng.choice(th["names"]).capitalize()
    mat = rng.choice(lexicon.MATERIALS)
    r = rng.random()
    if r < 0.10:
        return syn                                              # thème seul
    if r < 0.16:
        return f"Couleur {syn}"
    adj = rng.choice(lexicon.ADJECTIVES) if rng.random() < 0.35 else None
    if rng.random() < 0.78:  # gabarit FR : "Épée du Vent"
        conn = rng.choice(lexicon.CONNECTORS_FR)
        core = f"{mat} {conn} {syn}"
    else:                     # gabarit EN : "Wind Blade" / "Blade of Wind"
        if rng.random() < 0.5:
            core = f"{syn} {mat}"
        else:
            conn = rng.choice(lexicon.CONNECTORS_EN)
            core = f"{mat} {conn} {syn}"
    if adj:
        core = f"{adj} {core}" if rng.random() < 0.6 else f"{core} {adj}"
    return core


def generate(target_chars: int, seed: int, sample: bool) -> list[str]:
    rng = random.Random(seed)
    lines: list[str] = []
    total = 0

    # 1) ancrage couleurs nommées (monochrome ramp autour de la couleur)
    for name, hx in CSS_COLORS.items():
        body = colors.hex_to_rgb(hx)
        for _ in range(2):
            ramp = colors.ramp_from_rgb(body, stops=STOPS, rng=rng, hue_jitter=4.0)
            for disp in (name.capitalize(), f"Couleur {name}", f"{name} color"):
                ln = _ramp_line(disp, ramp)
                lines.append(ln); total += len(ln)

    # 2) cœur : thèmes x matériaux x adjectifs, plusieurs variantes de palette par nom
    limit = 200 if sample else 10**9
    while total < target_chars and len(lines) < limit:
        th = rng.choice(lexicon.THEMES)
        name = _make_name(th, rng)
        variants = 1 if sample else rng.randint(1, 2)
        for _ in range(variants):
            ramp = _theme_ramp(th, rng)
            ln = _ramp_line(name, ramp)
            lines.append(ln); total += len(ln)
            if total >= target_chars and not sample:
                break
    rng.shuffle(lines)
    return lines


def write_outputs(lines: list[str], out_dir: str, sample: bool) -> None:
    os.makedirs(out_dir, exist_ok=True)
    text = "".join(lines)
    with open(os.path.join(out_dir, "data.txt"), "w", encoding="utf-8") as f:
        f.write(text)
    print(f"data.txt : {len(lines):,} lignes, {len(text):,} chars")
    if sample:
        print("--- preview ---")
        print("".join(lines[:12]), end="")
        return

    # tokenizer char-level
    chars = sorted(set(text))
    stoi = {c: i for i, c in enumerate(chars)}
    itos = {i: c for i, c in enumerate(chars)}
    with open(os.path.join(out_dir, "meta.pkl"), "wb") as f:
        pickle.dump({"stoi": stoi, "itos": itos, "vocab_size": len(chars)}, f)
    print(f"meta.pkl : vocab_size={len(chars)}")

    try:
        import numpy as np
    except ImportError:
        print("numpy absent -> train.bin/val.bin non écrits (pip install numpy)")
        return
    ids = np.array([stoi[c] for c in text], dtype=np.uint16)
    n = len(ids)
    split = int(n * 0.9)
    ids[:split].tofile(os.path.join(out_dir, "train.bin"))
    ids[split:].tofile(os.path.join(out_dir, "val.bin"))
    print(f"train.bin : {split:,} tokens · val.bin : {n - split:,} tokens")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--target-chars", type=int, default=20_000_000)
    ap.add_argument("--seed", type=int, default=1234)
    ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "data"))
    ap.add_argument("--sample", action="store_true")
    args = ap.parse_args()
    lines = generate(args.target_chars, args.seed, args.sample)
    write_outputs(lines, args.out, args.sample)


if __name__ == "__main__":
    main()
