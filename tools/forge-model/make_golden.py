"""Fabrique un mini-modèle GPT déterministe + ses logits attendus, pour le test de conformité Java.

Produit :
  src/test/resources/forge/tiny-gpt.bin       (mini modèle exporté, qq Ko)
  src/test/resources/forge/tiny-expected.txt  (input + logits attendus de la dernière position)

Le test Java charge tiny-gpt.bin, exécute son forward sur le même input, et vérifie l'égalité (~1e-3).
"""
from __future__ import annotations
import os
import struct

import torch

from model import GPT, GPTConfig

HERE = os.path.dirname(__file__)
OUTDIR = os.path.abspath(os.path.join(HERE, "..", "..", "src", "test", "resources", "forge"))


def w(f, t):
    f.write(t.detach().to(torch.float32).contiguous().view(-1).numpy().astype("<f4").tobytes())


def export(m, cfg, itos, path):
    sd = m.state_dict(); E = cfg.n_embd
    with open(path, "wb") as f:
        f.write(b"MGPT")
        f.write(struct.pack("<6i", 1, cfg.vocab_size, cfg.block_size, cfg.n_layer, cfg.n_head, cfg.n_embd))
        f.write(struct.pack("<i", 1))
        w(f, sd["transformer.wte.weight"]); w(f, sd["transformer.wpe.weight"])
        for i in range(cfg.n_layer):
            p = f"transformer.h.{i}."
            for k in ("ln_1.weight","ln_1.bias","attn.c_attn.weight","attn.c_attn.bias",
                      "attn.c_proj.weight","attn.c_proj.bias","ln_2.weight","ln_2.bias",
                      "mlp.c_fc.weight","mlp.c_fc.bias","mlp.c_proj.weight","mlp.c_proj.bias"):
                w(f, sd[p + k])
        w(f, sd["transformer.ln_f.weight"]); w(f, sd["transformer.ln_f.bias"])
        for idx in range(cfg.vocab_size):
            f.write(struct.pack("<i", ord(itos[idx])))


def main():
    os.makedirs(OUTDIR, exist_ok=True)
    torch.manual_seed(7)
    cfg = GPTConfig(vocab_size=20, block_size=8, n_layer=2, n_head=2, n_embd=16, dropout=0.0)
    m = GPT(cfg); m.eval()
    # poids aléatoires mais déterministes (seed) ; on n'entraîne pas : on teste juste le forward.
    itos = {i: chr(ord('a') + i) for i in range(cfg.vocab_size)}
    export(m, cfg, itos, os.path.join(OUTDIR, "tiny-gpt.bin"))

    ids = [1, 2, 3, 4, 5]
    with torch.no_grad():
        x = torch.tensor([ids])
        logits, _ = m(x)             # forward complet ; dernière position
    last = logits[0, -1, :].tolist()
    with open(os.path.join(OUTDIR, "tiny-expected.txt"), "w", encoding="utf-8") as f:
        f.write(",".join(str(i) for i in ids) + "\n")
        f.write(" ".join(f"{v:.6f}" for v in last) + "\n")
    print("golden ecrit dans", OUTDIR)
    print("logits[:5] =", [round(v, 4) for v in last[:5]])


if __name__ == "__main__":
    main()
