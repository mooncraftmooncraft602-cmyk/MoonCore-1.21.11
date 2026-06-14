#!/usr/bin/env bash
# Lance le sidecar de palettes MoonCore. A demarrer a cote du serveur Minecraft.
cd "$(dirname "$0")"
if [ -x ".venv/bin/python" ]; then
  exec .venv/bin/python serve.py
else
  exec python serve.py
fi
