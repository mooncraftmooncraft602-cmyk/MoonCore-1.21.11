"""Exporte model.pt vers un binaire compact (forge-gpt.bin) lisible par l'inference Java PURE.

Aucune dependance runtime cote serveur : le plugin lit ce fichier et fait le forward pass en Java.

Format (little-endian) :
  magic "MGPT" (4o) | version int32=1 | vocab,block,n_layer,n_head,n_embd (5x int32) | bias int32 (1=oui)
  puis les tenseurs en float32, dans cet ordre EXACT :
    wte[vocab,n_embd], wpe[block,n_embd],
    par couche : ln1_w[E], ln1_b[E], attn_w[3E,E], attn_b[3E], proj_w[E,E], proj_b[E],
                 ln2_w[E], ln2_b[E], fc_w[4E,E], fc_b[4E], fcproj_w[E,4E], fcproj_b[E]
    ln_f_w[E], ln_f_b[E]
  puis vocab x int32 : les codepoints (itos[0..vocab-1]).
La tete lm_head partage wte (weight tying) -> non exportee.
"""
from __future__ import annotations
import argparse
import os
import struct

import numpy as np
import torch

from model import GPT, GPTConfig

HERE = os.path.dirname(__file__)


def w(f, t: torch.Tensor):
    f.write(t.detach().to(torch.float32).contiguous().view(-1).numpy().astype("<f4").tobytes())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="inp", default=os.path.join(HERE, "model.pt"))
    ap.add_argument("--out", default=os.path.join(HERE, "forge-gpt.bin"))
    args = ap.parse_args()

    ck = torch.load(args.inp, map_location="cpu", weights_only=False)
    cfg = GPTConfig(**ck["config"])
    m = GPT(cfg); m.load_state_dict(ck["model"]); m.eval()
    sd = m.state_dict()
    E = cfg.n_embd

    with open(args.out, "wb") as f:
        f.write(b"MGPT")
        f.write(struct.pack("<6i", 1, cfg.vocab_size, cfg.block_size, cfg.n_layer, cfg.n_head, cfg.n_embd))
        f.write(struct.pack("<i", 1 if cfg.bias else 0))
        w(f, sd["transformer.wte.weight"])
        w(f, sd["transformer.wpe.weight"])
        for i in range(cfg.n_layer):
            p = f"transformer.h.{i}."
            for key in ("ln_1.weight", "ln_1.bias",
                        "attn.c_attn.weight", "attn.c_attn.bias",
                        "attn.c_proj.weight", "attn.c_proj.bias",
                        "ln_2.weight", "ln_2.bias",
                        "mlp.c_fc.weight", "mlp.c_fc.bias",
                        "mlp.c_proj.weight", "mlp.c_proj.bias"):
                w(f, sd[p + key])
        w(f, sd["transformer.ln_f.weight"])
        w(f, sd["transformer.ln_f.bias"])
        # tokenizer : codepoints itos[0..vocab-1]
        itos = ck["itos"]
        for idx in range(cfg.vocab_size):
            f.write(struct.pack("<i", ord(itos[idx])))

    size = os.path.getsize(args.out)
    print(f"export OK -> {args.out} ({size/1e6:.1f} MB, params ~{m.num_params()/1e6:.1f}M, E={E}, layers={cfg.n_layer})")


if __name__ == "__main__":
    main()
