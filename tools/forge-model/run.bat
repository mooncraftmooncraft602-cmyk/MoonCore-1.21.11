@echo off
REM Lance le sidecar de palettes MoonCore (Windows). A demarrer a cote du serveur Minecraft.
cd /d "%~dp0"
if exist ".venv\Scripts\python.exe" (
  ".venv\Scripts\python.exe" serve.py
) else (
  python serve.py
)
