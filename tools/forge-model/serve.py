"""Sidecar d'inférence : sert le modèle de palettes en HTTP local.

POST /palette  {"name": "Épée du Vent"}  ->  {"colors": ["#0a3d12", ...], "source": "model"}
GET  /health   ->  {"ok": true, "params_m": 14.0}

Lancement : python serve.py   (écoute sur 127.0.0.1:8770)
Le plugin MoonCore appelle cet endpoint ; si le service est éteint, il retombe sur son moteur déterministe.
"""
from __future__ import annotations
import os
import re
import pickle

import torch
from fastapi import FastAPI
from pydantic import BaseModel

from model import GPT, GPTConfig

HERE = os.path.dirname(__file__)
MODEL_PATH = os.environ.get("FORGE_MODEL", os.path.join(HERE, "model.pt"))
HEX_RE = re.compile(r"#[0-9a-fA-F]{6}")

app = FastAPI(title="MoonCore Forge Palette Model")
_state = {"model": None, "stoi": None, "itos": None, "cfg": None}


def load():
    ckpt = torch.load(MODEL_PATH, map_location="cpu", weights_only=False)
    cfg = GPTConfig(**ckpt["config"])
    model = GPT(cfg)
    model.load_state_dict(ckpt["model"])
    model.eval()
    torch.set_num_threads(max(1, (os.cpu_count() or 4) // 2))
    _state.update(model=model, stoi=ckpt["stoi"], itos=ckpt["itos"], cfg=cfg)
    print(f"model loaded ({model.num_params()/1e6:.1f}M params, val_loss={ckpt.get('val_loss')})")


class Req(BaseModel):
    name: str
    temperature: float = 0.7
    top_k: int = 30


def _luminance(hx: str) -> float:
    r, g, b = int(hx[1:3], 16), int(hx[3:5], 16), int(hx[5:7], 16)
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0


@app.get("/health")
def health():
    m = _state["model"]
    return {"ok": m is not None, "params_m": round(m.num_params() / 1e6, 1) if m else 0}


@app.post("/palette")
def palette(req: Req):
    model, stoi, itos, cfg = _state["model"], _state["stoi"], _state["itos"], _state["cfg"]
    if model is None:
        return {"colors": [], "source": "unloaded"}
    prompt = req.name.strip() + " => "
    ids = [stoi[c] for c in prompt if c in stoi]
    if not ids:
        ids = [stoi.get(" ", 0)]
    idx = torch.tensor([ids], dtype=torch.long)
    nl = stoi.get("\n")
    out = model.generate(idx, max_new_tokens=64, temperature=max(0.1, req.temperature),
                         top_k=req.top_k, stop_token=nl)
    text = "".join(itos[int(i)] for i in out[0].tolist())
    gen = text[len(prompt):].split("\n")[0]
    colors = HEX_RE.findall(gen)
    # dédoublonne en gardant l'ordre, trie sombre->clair
    seen, uniq = set(), []
    for c in colors:
        cl = c.lower()
        if cl not in seen:
            seen.add(cl); uniq.append(cl)
    uniq.sort(key=_luminance)
    return {"colors": uniq, "source": "model", "raw": gen}


if __name__ == "__main__":
    import uvicorn
    load()
    uvicorn.run(app, host="127.0.0.1", port=int(os.environ.get("FORGE_PORT", "8770")), log_level="warning")
