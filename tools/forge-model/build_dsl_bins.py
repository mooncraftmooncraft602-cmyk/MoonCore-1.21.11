"""Tokenise le corpus DSL (data/dsl_data.txt, généré par le test Java TextureComposerTest) en
artefacts d'entraînement char-level, dans un dossier séparé pour ne pas écraser le modèle couleur.

Sortie dans data-dsl/ :
  - meta.pkl    : tokenizer char-level (stoi/itos, vocab_size)
  - train.bin / val.bin : tokens uint16

Usage :
  python build_dsl_bins.py
  python build_dsl_bins.py --in data/dsl_data.txt --out data-dsl
"""
from __future__ import annotations
import argparse
import os
import pickle
import random

import numpy as np

HERE = os.path.dirname(__file__)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="inp", default=os.path.join(HERE, "data", "dsl_data.txt"))
    ap.add_argument("--out", default=os.path.join(HERE, "data-dsl"))
    ap.add_argument("--val-frac", type=float, default=0.1)
    args = ap.parse_args()

    with open(args.inp, "r", encoding="utf-8") as f:
        rows = [ln for ln in f.read().splitlines() if ln.strip()]
    # MÉLANGE les lignes : le corpus est ordonné par type d'objet, donc sans mélange le split
    # train/val n'est pas iid et les fenêtres d'entraînement restent du même type. On interleave.
    random.Random(1234).shuffle(rows)
    text = "\n".join(rows) + "\n"
    print(f"corpus : {len(rows):,} lignes (mélangées), {len(text):,} chars")

    chars = sorted(set(text))
    stoi = {c: i for i, c in enumerate(chars)}
    itos = {i: c for i, c in enumerate(chars)}
    os.makedirs(args.out, exist_ok=True)
    with open(os.path.join(args.out, "meta.pkl"), "wb") as f:
        pickle.dump({"stoi": stoi, "itos": itos, "vocab_size": len(chars)}, f)
    print(f"meta.pkl : vocab_size={len(chars)}  ->  {args.out}")

    ids = np.array([stoi[c] for c in text], dtype=np.uint16)
    n = len(ids)
    split = int(n * (1.0 - args.val_frac))
    ids[:split].tofile(os.path.join(args.out, "train.bin"))
    ids[split:].tofile(os.path.join(args.out, "val.bin"))
    print(f"train.bin : {split:,} tokens · val.bin : {n - split:,} tokens")


if __name__ == "__main__":
    main()
