# Forge Model — modèle local de palettes/dégradés pour la forge MoonCore

Petit GPT (architecture GPT-2, ~14M paramètres) entraîné **localement sur CPU** qui décide une
**rampe de couleurs (dégradé sombre→clair)** à partir d'un nom d'item (ex. « Épée du Vent » → vert/blanc).
Tourne en **sidecar HTTP local** ; le plugin MoonCore l'appelle, et **retombe sur son moteur déterministe**
(`PaletteResolver`) si le sidecar est éteint. Aucune dépendance cloud, aucune clé API.

## Pourquoi ~14M et pas 200M ?
Ce PC n'a pas de GPU NVIDIA — entraîner un 200M *from scratch* y prendrait des semaines. Pour cette tâche
**étroite** (nom → palette), un petit GPT-2 sur un **gros dataset généré par théorie des couleurs** est
réellement bon : la qualité vient du **dataset**, pas du nombre de paramètres. La recette est scalable vers
200M plus tard sur un GPU cloud (même `build_dataset.py`/`model.py`, config plus grande).

## Composants
- `colors.py` — théorie des couleurs pure (HSL, harmonies, rampes de dégradé).
- `lexicon.py` — lexique multilingue FR/EN (thèmes→couleurs, matériaux, adjectifs).
- `build_dataset.py` — génère le corpus `data/` (nom → rampe) + tokenizer char-level.
- `model.py` — GPT minimal (nanoGPT-style).
- `train.py` — entraînement CPU optimisé (AdamW, warmup+cosine, accumulation, early-stop).
- `serve.py` — sidecar FastAPI : `POST /palette {"name": "..."}` → `{"colors": ["#..", ...]}`.

## Installation (une fois)
```bat
cd tools\forge-model
python -m venv .venv
.venv\Scripts\python -m pip install -U pip
.venv\Scripts\python -m pip install -r requirements.txt
```

## Entraînement
```bat
.venv\Scripts\python build_dataset.py --target-chars 20000000
.venv\Scripts\python train.py            REM quelques heures sur CPU ; produit model.pt
REM sanity rapide : .venv\Scripts\python train.py --quick
```

## Lancer le sidecar (à côté du serveur Minecraft)
```bat
run.bat            REM écoute sur 127.0.0.1:8770
```
Test : `curl -s -X POST localhost:8770/palette -H "content-type: application/json" -d "{\"name\":\"Épée du Vent\"}"`

## Activer côté plugin
Dans `plugins/MoonCore/modules/ai-assistant.yml` :
```yaml
local-model:
  enabled: true
  endpoint: "http://127.0.0.1:8770/palette"
  timeout-seconds: 8
```
Puis en jeu : `/moon forge model diamond_sword Épée du Vent`. Sidecar éteint → repli automatique.
