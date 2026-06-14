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

## Utilisation : inférence DANS le serveur (recommandé, zéro dépendance)
L'inférence tourne **dans la JVM du plugin** — pas de Python, pas de sidecar à l'exécution.
1. Exporter le modèle entraîné vers un binaire compact :
   ```bat
   .venv\Scripts\python export.py        REM produit forge-gpt.bin (~13 Mo)
   ```
2. Copier `forge-gpt.bin` dans le dossier du plugin : `plugins/MoonCore/forge-gpt.bin`.
3. En jeu : **`/moon forge model diamond_sword Épée du Vent`** → le serveur charge le modèle et
   décide les couleurs lui-même. Modèle absent → repli automatique sur le moteur déterministe.

Modes de la commande :
- `/moon forge <base> <nom>` — moteur déterministe (instantané, marche pour tout nom).
- `/moon forge model <base> <nom>` — modèle GPT exécuté sur le serveur.
- `/moon forge <base> <nom> #aaa #bbb #ccc` — **tes couleurs** (tu choisis).
- `/moon forge suggest <nom>` — **conseille** des couleurs (à copier/ajuster).

## (Optionnel) sidecar HTTP
Une alternative `serve.py` (FastAPI, `POST /palette`) existe si tu préfères un service séparé
(`run.bat`, port 8770, config `local-model` dans `ai-assistant.yml`). Non requis : l'inférence Java
intégrée est le mode par défaut.

## Conformité
Le forward pass Java ({@code GptInference}) est validé contre PyTorch par un test golden
(`make_golden.py` + `GptInferenceTest`) : logits identiques → mêmes couleurs.
