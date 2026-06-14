"""Entraîne le petit GPT sur le corpus couleurs (CPU). Sauvegarde model.pt (config + poids + tokenizer).

Usage :
  python train.py                          # run par défaut
  python train.py --max-iters 6000 --eval-interval 250
  python train.py --quick                  # sanity très court
"""
from __future__ import annotations
import argparse
import math
import os
import pickle
import time
from dataclasses import asdict

import numpy as np
import torch

from model import GPT, GPTConfig

HERE = os.path.dirname(__file__)
DATA = os.path.join(HERE, "data")


def get_batch(split_arr, block_size, batch_size, device):
    ix = torch.randint(len(split_arr) - block_size - 1, (batch_size,))
    x = torch.stack([torch.from_numpy(split_arr[i:i + block_size].astype(np.int64)) for i in ix])
    y = torch.stack([torch.from_numpy(split_arr[i + 1:i + 1 + block_size].astype(np.int64)) for i in ix])
    return x.to(device), y.to(device)


@torch.no_grad()
def estimate_loss(model, splits, block_size, batch_size, device, iters=50):
    model.eval()
    out = {}
    for name, arr in splits.items():
        losses = torch.zeros(iters)
        for k in range(iters):
            x, y = get_batch(arr, block_size, batch_size, device)
            _, loss = model(x, y)
            losses[k] = loss.item()
        out[name] = losses.mean().item()
    model.train()
    return out


def lr_at(it, lr, warmup, max_iters, min_lr):
    if it < warmup:
        return lr * (it + 1) / warmup
    if it > max_iters:
        return min_lr
    ratio = (it - warmup) / max(1, (max_iters - warmup))
    coeff = 0.5 * (1.0 + math.cos(math.pi * ratio))
    return min_lr + coeff * (lr - min_lr)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--max-iters", type=int, default=8000)
    ap.add_argument("--eval-interval", type=int, default=250)
    ap.add_argument("--batch-size", type=int, default=64)
    ap.add_argument("--block-size", type=int, default=128)
    ap.add_argument("--grad-accum", type=int, default=4)
    ap.add_argument("--lr", type=float, default=3e-4)
    ap.add_argument("--min-lr", type=float, default=3e-5)
    ap.add_argument("--warmup", type=int, default=200)
    ap.add_argument("--n-layer", type=int, default=8)
    ap.add_argument("--n-head", type=int, default=8)
    ap.add_argument("--n-embd", type=int, default=384)
    ap.add_argument("--dropout", type=float, default=0.1)
    ap.add_argument("--threads", type=int, default=os.cpu_count() or 8)
    ap.add_argument("--out", default=os.path.join(HERE, "model.pt"))
    ap.add_argument("--data", default=DATA, help="dossier contenant meta.pkl/train.bin/val.bin")
    ap.add_argument("--quick", action="store_true")
    args = ap.parse_args()

    if args.quick:
        args.max_iters, args.eval_interval, args.n_layer, args.n_embd, args.warmup = 60, 20, 2, 128, 10

    torch.manual_seed(1337)
    torch.set_num_threads(args.threads)
    device = "cpu"

    with open(os.path.join(args.data, "meta.pkl"), "rb") as f:
        meta = pickle.load(f)
    vocab_size = meta["vocab_size"]
    train_arr = np.memmap(os.path.join(args.data, "train.bin"), dtype=np.uint16, mode="r")
    val_arr = np.memmap(os.path.join(args.data, "val.bin"), dtype=np.uint16, mode="r")
    splits = {"train": train_arr, "val": val_arr}
    print(f"vocab={vocab_size} train={len(train_arr):,} val={len(val_arr):,} threads={args.threads}")

    cfg = GPTConfig(vocab_size=vocab_size, block_size=args.block_size,
                    n_layer=args.n_layer, n_head=args.n_head, n_embd=args.n_embd, dropout=args.dropout)
    model = GPT(cfg).to(device)
    print(f"params ~ {model.num_params() / 1e6:.1f}M")
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, betas=(0.9, 0.95), weight_decay=0.1)

    best_val = float("inf")
    t0 = time.time()
    for it in range(args.max_iters + 1):
        lr = lr_at(it, args.lr, args.warmup, args.max_iters, args.min_lr)
        for g in opt.param_groups:
            g["lr"] = lr

        if it % args.eval_interval == 0 or it == args.max_iters:
            losses = estimate_loss(model, splits, args.block_size, args.batch_size, device,
                                   iters=20 if args.quick else 50)
            dt = time.time() - t0
            print(f"iter {it:5d} | train {losses['train']:.4f} | val {losses['val']:.4f} | lr {lr:.2e} | {dt:.0f}s")
            if losses["val"] < best_val:
                best_val = losses["val"]
                torch.save({"model": model.state_dict(), "config": asdict(cfg),
                            "stoi": meta["stoi"], "itos": meta["itos"], "val_loss": best_val}, args.out)
                print(f"  [ok] checkpoint saved (val {best_val:.4f}) -> {os.path.basename(args.out)}")

        model.train()
        for micro in range(args.grad_accum):
            x, y = get_batch(train_arr, args.block_size, args.batch_size, device)
            _, loss = model(x, y)
            (loss / args.grad_accum).backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        opt.step()
        opt.zero_grad(set_to_none=True)

    print(f"done in {time.time() - t0:.0f}s · best val {best_val:.4f}")


if __name__ == "__main__":
    main()
