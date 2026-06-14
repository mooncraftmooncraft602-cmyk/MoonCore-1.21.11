"""Échantillonne le modèle DSL : nom -> programme. Vérifie que l'IA écrit le langage."""
import os, pickle, torch
from model import GPT, GPTConfig

HERE = os.path.dirname(__file__)
ck = torch.load(os.path.join(HERE, "model-dsl.pt"), map_location="cpu", weights_only=False)
cfg = GPTConfig(**ck["config"])
m = GPT(cfg); m.load_state_dict(ck["model"]); m.eval()
stoi, itos = ck["stoi"], ck["itos"]
nl = stoi["\n"]


def gen(prompt, temp=0.05):
    idx = torch.tensor([[stoi[c] for c in prompt if c in stoi]], dtype=torch.long)
    out = m.generate(idx, max_new_tokens=240, temperature=temp, top_k=1, stop_token=nl)  # greedy ~ Java
    return "".join(itos[i] for i in out[0].tolist())


for name in ["Épée du Feu", "Pioche Royale de Glace", "Dague de l'Ombre",
             "Hache Runique de Foudre", "Plastron Sacré du Soleil", "Casque du Dragon"]:
    print(gen(name + " => ").strip())
    print("-" * 80)
