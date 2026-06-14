"""Théorie des couleurs pure (stdlib only) pour générer des palettes/dégradés cohérents.

Tout est déterministe (RNG injecté) — utilisé par build_dataset.py pour fabriquer le corpus
d'entraînement (nom d'item -> rampe sombre->clair de 5 stops = le dégradé d'ombrage de la texture).
"""
from __future__ import annotations
import colorsys
import math
import random
from typing import List, Optional, Sequence, Tuple

RGB = Tuple[int, int, int]


# ----------------------------- conversions -----------------------------

def clamp8(v: float) -> int:
    return max(0, min(255, int(round(v))))


def hsl_to_rgb(h: float, s: float, l: float) -> RGB:
    """h en degrés [0,360), s/l dans [0,1]. (colorsys utilise HLS.)"""
    r, g, b = colorsys.hls_to_rgb((h % 360) / 360.0, max(0.0, min(1.0, l)), max(0.0, min(1.0, s)))
    return (clamp8(r * 255), clamp8(g * 255), clamp8(b * 255))


def rgb_to_hsl(rgb: RGB) -> Tuple[float, float, float]:
    r, g, b = (c / 255.0 for c in rgb)
    hl, ll, sl = colorsys.rgb_to_hls(r, g, b)
    return (hl * 360.0, sl, ll)


def hex_to_rgb(h: str) -> RGB:
    h = h.strip().lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def rgb_to_hex(rgb: RGB) -> str:
    return "#%02x%02x%02x" % (clamp8(rgb[0]), clamp8(rgb[1]), clamp8(rgb[2]))


def luminance(rgb: RGB) -> float:
    """Luminance perçue [0,1] (Rec. 601) — pour trier une rampe sombre->clair."""
    return (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255.0


# ----------------------------- harmonies -----------------------------

def complementary(h: float) -> float:
    return (h + 180) % 360


def analogous(h: float, spread: float = 30) -> Tuple[float, float]:
    return ((h - spread) % 360, (h + spread) % 360)


def triadic(h: float) -> Tuple[float, float]:
    return ((h + 120) % 360, (h + 240) % 360)


def split_complementary(h: float) -> Tuple[float, float]:
    return ((h + 150) % 360, (h + 210) % 360)


# ----------------------------- rampes / dégradés -----------------------------

def _ease(t: float) -> float:
    """Courbe douce (smoothstep) pour répartir la luminance plus naturellement."""
    return t * t * (3 - 2 * t)


def make_ramp(
    base_hue: float,
    base_sat: float,
    *,
    stops: int = 5,
    light_lo: float = 0.14,
    light_hi: float = 0.92,
    highlight_hue: Optional[float] = None,
    highlight_at: float = 0.7,
    rng: Optional[random.Random] = None,
    hue_jitter: float = 0.0,
    sat_curve: float = 0.85,
) -> List[str]:
    """Rampe de `stops` couleurs hex, de la plus SOMBRE à la plus CLAIRE.

    - la teinte part de `base_hue` et glisse vers `highlight_hue` dans les hautes lumières
      (ex. corps vert -> reflet blanc/cyan) ;
    - la saturation est plus forte au milieu (volume) et retombe aux extrêmes ;
    - `rng`/`hue_jitter` ajoutent une légère variation déterministe (diversité du dataset).
    """
    rng = rng or random.Random(0)
    out: List[str] = []
    for i in range(stops):
        t = i / (stops - 1) if stops > 1 else 0.0
        te = _ease(t)
        light = light_lo + (light_hi - light_lo) * te
        # saturation en cloche : faible aux extrêmes, max au milieu
        sat = base_sat * (1.0 - sat_curve * (abs(t - 0.5) * 2) ** 2)
        sat = max(0.0, min(1.0, sat))
        hue = base_hue + (rng.uniform(-hue_jitter, hue_jitter) if hue_jitter else 0.0)
        if highlight_hue is not None and t >= highlight_at:
            # mélange progressif de la teinte vers la teinte de reflet
            k = (t - highlight_at) / max(1e-6, (1.0 - highlight_at))
            hue = _hue_lerp(base_hue, highlight_hue, _ease(k))
            sat = sat * (1.0 - 0.55 * k)  # reflets désaturés (vers le blanc)
        out.append(rgb_to_hex(hsl_to_rgb(hue, sat, light)))
    return out


def _hue_lerp(a: float, b: float, t: float) -> float:
    """Interpolation circulaire de teinte (chemin le plus court)."""
    d = ((b - a + 540) % 360) - 180
    return (a + d * t) % 360


def ramp_from_rgb(
    body: RGB,
    *,
    highlight: Optional[RGB] = None,
    stops: int = 5,
    rng: Optional[random.Random] = None,
    hue_jitter: float = 0.0,
) -> List[str]:
    """Rampe construite autour d'une couleur de corps (et d'un reflet optionnel)."""
    h, s, _l = rgb_to_hsl(body)
    hh = rgb_to_hsl(highlight)[0] if highlight else None
    # un corps désaturé (gris/blanc/noir) -> garde une rampe neutre lisible
    s = max(s, 0.12) if s > 0.02 else 0.0
    return make_ramp(h, min(1.0, s + 0.1), stops=stops, highlight_hue=hh,
                     rng=rng, hue_jitter=hue_jitter)


def sort_dark_to_light(hexes: Sequence[str]) -> List[str]:
    return sorted(hexes, key=lambda hx: luminance(hex_to_rgb(hx)))


if __name__ == "__main__":  # petit check visuel
    rng = random.Random(42)
    print("vent  :", make_ramp(125, 0.7, highlight_hue=140, rng=rng))   # vert -> blanc
    print("feu   :", make_ramp(18, 0.95, highlight_hue=48, rng=rng))    # rouge -> jaune
    print("glace :", make_ramp(205, 0.8, highlight_hue=190, rng=rng))   # bleu -> cyan/blanc
