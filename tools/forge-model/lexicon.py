"""Lexique thématique multilingue (FR/EN) pour générer des noms d'items et leur couleur de base.

Chaque THEME = des synonymes (entrée du modèle) -> une couleur de base (hue/sat) + une teinte de reflet.
build_dataset.py croise THEMES x MATERIALS x ADJECTIVES x gabarits pour produire un corpus immense et
cohérent (nom -> rampe sombre->clair).
"""
from __future__ import annotations
from typing import List, Optional, TypedDict


class Theme(TypedDict):
    names: List[str]
    hue: float
    sat: float
    hl: Optional[float]   # teinte de reflet (hautes lumières), ou None


# hue en degrés, sat dans [0,1], hl = teinte de reflet. 36 thèmes distincts, teintes bien réparties.
THEMES: List[Theme] = [
    {"names": ["vent", "wind", "air", "tempete", "tempête", "storm", "aero", "aéro", "zephyr", "zéphyr", "tornade", "cyclone", "bourrasque", "gale", "aerien", "aérien"], "hue": 145, "sat": 0.75, "hl": 150},
    {"names": ["feu", "fire", "flamme", "flame", "brasier", "infernal", "magma", "lave", "lava", "ardent", "blaze", "ember", "braise", "pyro", "ignis", "embrase"], "hue": 12, "sat": 0.95, "hl": 44},
    {"names": ["glace", "ice", "gel", "givre", "frost", "frozen", "neige", "snow", "blizzard", "polaire", "arctique", "cryo", "gele", "gelé", "glacial", "hiver"], "hue": 200, "sat": 0.80, "hl": 188},
    {"names": ["foudre", "thunder", "eclair", "éclair", "lightning", "orage", "electrique", "électrique", "voltaique", "voltaïque", "tonnerre", "volt", "spark", "fulgur", "galvanique"], "hue": 268, "sat": 0.72, "hl": 52},
    {"names": ["ombre", "tenebre", "ténèbre", "tenebres", "ténèbres", "shadow", "nuit", "night", "void", "neant", "néant", "obscur", "umbra", "occulte", "sombre", "noirceur"], "hue": 272, "sat": 0.5, "hl": 286},
    {"names": ["poison", "venin", "venom", "toxique", "toxic", "acide", "acid", "corrosif", "venimeux", "putride", "peste", "miasme", "noxious"], "hue": 88, "sat": 0.85, "hl": 72},
    {"names": ["sang", "blood", "sanguin", "crimson", "carnage", "gore", "ecarlate", "écarlate", "hemo", "saignant", "boucher"], "hue": 358, "sat": 0.85, "hl": 8},
    {"names": ["or", "gold", "dore", "doré", "royal", "divin", "holy", "sacre", "sacré", "celeste", "céleste", "lumiere", "lumière", "light", "radiant", "saint", "aureum", "glorieux", "imperial", "impérial"], "hue": 46, "sat": 0.92, "hl": 52},
    {"names": ["nature", "foret", "forêt", "forest", "terre", "earth", "emeraude", "émeraude", "emerald", "sylvestre", "feuille", "leaf", "bois", "wood", "verdant", "druide", "primaveral"], "hue": 110, "sat": 0.62, "hl": 95},
    {"names": ["ocean", "océan", "mer", "sea", "aqua", "eau", "water", "marine", "abyssal", "tide", "maree", "marée", "naval", "ondine", "aquatique", "neptune"], "hue": 212, "sat": 0.85, "hl": 195},
    {"names": ["soleil", "sun", "solaire", "solar", "aurore", "dawn", "midi", "helios", "ensoleille", "ensoleillé", "estival"], "hue": 32, "sat": 0.95, "hl": 48},
    {"names": ["lune", "moon", "lunaire", "lunar", "argent", "silver", "stellaire", "etoile", "étoile", "star", "selene", "nocturne", "astral"], "hue": 226, "sat": 0.32, "hl": 220},
    {"names": ["ender", "enderite", "chorus", "warp", "teleport", "téléport", "endermite", "perle", "warped"], "hue": 168, "sat": 0.6, "hl": 286},
    {"names": ["nether", "enfer", "hell", "wither", "ame", "âme", "soul", "wraith", "damne", "damné", "maudit"], "hue": 348, "sat": 0.55, "hl": 30},
    {"names": ["rose", "pink", "amour", "love", "fleur", "flower", "sakura", "cerise", "blossom", "petale", "pétale", "romantique"], "hue": 332, "sat": 0.7, "hl": 342},
    {"names": ["arcane", "amethyste", "améthyste", "amethyst", "magie", "magic", "mystique", "mystic", "sorcier", "wizard", "mage", "enchante", "enchanté", "rune", "runique", "ensorcele", "ensorcelé"], "hue": 286, "sat": 0.65, "hl": 292},
    {"names": ["cuivre", "copper", "bronze", "rouille", "rust", "laiton", "oxyde"], "hue": 24, "sat": 0.7, "hl": 36},
    {"names": ["cristal", "crystal", "diamant", "diamond", "prisme", "prism", "gemme", "scintillant", "etincelant", "étincelant"], "hue": 186, "sat": 0.7, "hl": 190},
    {"names": ["acier", "iron", "fer", "steel", "metal", "métal", "chrome", "gris", "gray", "grey", "titane", "titan", "blinde", "blindé", "forge", "forgé"], "hue": 210, "sat": 0.08, "hl": None},
    {"names": ["sable", "sand", "desert", "désert", "dune", "ambre", "amber", "topaze", "topaz", "doree", "aride"], "hue": 42, "sat": 0.72, "hl": 52},
    {"names": ["rubis", "ruby", "grenat", "garnet", "vermillon"], "hue": 348, "sat": 0.88, "hl": 4},
    {"names": ["obsidienne", "obsidian", "onyx", "basalte", "basalt", "tenebreux", "ténébreux"], "hue": 280, "sat": 0.35, "hl": 250},
    {"names": ["corail", "coral", "saumon", "salmon", "peche", "pêche"], "hue": 10, "sat": 0.78, "hl": 24},
    {"names": ["menthe", "mint", "fraicheur", "fraîcheur", "mentholé"], "hue": 158, "sat": 0.6, "hl": 150},
    {"names": ["lavande", "lavender", "lilas", "lilac", "prune", "mauve", "glycine"], "hue": 278, "sat": 0.45, "hl": 295},
    {"names": ["citron", "lemon", "lime", "citrus", "agrume", "soufre", "sulfur", "souffre"], "hue": 62, "sat": 0.9, "hl": 56},
    {"names": ["chocolat", "chocolate", "cafe", "café", "coffee", "brun", "brown", "boue", "mud", "terreux", "cacao"], "hue": 24, "sat": 0.5, "hl": 36},
    {"names": ["cendre", "ash", "fumee", "fumée", "smoke", "brume", "mist", "fog", "nuage", "cloud", "poussiere", "poussière"], "hue": 220, "sat": 0.05, "hl": None},
    {"names": ["chaos", "corrompu", "corrupt", "corruption", "eldritch", "folie", "madness", "demence", "démence", "aberrant"], "hue": 308, "sat": 0.6, "hl": 120},
    {"names": ["dragon", "draconique", "drake", "wyrm", "draconien"], "hue": 16, "sat": 0.82, "hl": 44},
    {"names": ["phenix", "phénix", "phoenix", "renaissance", "immortel"], "hue": 22, "sat": 0.95, "hl": 52},
    {"names": ["vampire", "vampirique", "sombrelame", "demoniaque", "démoniaque", "demon", "démon"], "hue": 352, "sat": 0.6, "hl": 358},
    {"names": ["spectral", "fantome", "fantôme", "ghost", "ethere", "éthéré", "pale", "pâle", "revenant", "hante", "hanté"], "hue": 182, "sat": 0.25, "hl": 195},
    {"names": ["jade", "olivine", "peridot", "péridot", "malachite"], "hue": 162, "sat": 0.62, "hl": 168},
    {"names": ["saphir", "sapphire", "azur", "azure", "cobalt", "indigo"], "hue": 228, "sat": 0.8, "hl": 215},
    {"names": ["turquoise", "teal", "sarcelle", "lagon", "cyan"], "hue": 176, "sat": 0.72, "hl": 180},
]

_OLD_THEMES: List[Theme] = [
    {"names": ["vent", "wind", "air", "tempete", "tempête", "storm", "aero", "aéro", "zephyr", "zéphyr", "tornade", "cyclone", "bourrasque", "gale"], "hue": 125, "sat": 0.70, "hl": 140},
    {"names": ["feu", "fire", "flamme", "flame", "brasier", "infernal", "magma", "lave", "lava", "ardent", "blaze", "ember", "braise", "pyro"], "hue": 16, "sat": 0.95, "hl": 45},
    {"names": ["glace", "ice", "gel", "givre", "frost", "frozen", "neige", "snow", "blizzard", "polaire", "arctique", "cryo", "gelé"], "hue": 205, "sat": 0.80, "hl": 190},
    {"names": ["foudre", "thunder", "eclair", "éclair", "lightning", "orage", "electrique", "électrique", "voltaique", "voltaïque", "tonnerre", "volt", "spark"], "hue": 275, "sat": 0.70, "hl": 52},
    {"names": ["ombre", "tenebre", "ténèbre", "tenebres", "ténèbres", "shadow", "dark", "nuit", "night", "void", "neant", "néant", "abysse", "obscur", "noir", "umbra"], "hue": 270, "sat": 0.45, "hl": 285},
    {"names": ["poison", "venin", "venom", "toxique", "toxic", "acide", "acid", "corrosif", "venimeux", "putride"], "hue": 95, "sat": 0.85, "hl": 80},
    {"names": ["sang", "blood", "sanguin", "crimson", "demon", "démon", "demoniaque", "démoniaque", "carnage", "gore", "ecarlate", "écarlate"], "hue": 0, "sat": 0.85, "hl": 12},
    {"names": ["or", "gold", "dore", "doré", "royal", "divin", "holy", "sacre", "sacré", "celeste", "céleste", "lumiere", "lumière", "light", "radiant", "saint", "aureum"], "hue": 45, "sat": 0.90, "hl": 50},
    {"names": ["nature", "foret", "forêt", "forest", "terre", "earth", "emeraude", "émeraude", "emerald", "sylvestre", "feuille", "leaf", "bois", "wood", "jade", "verdant"], "hue": 132, "sat": 0.62, "hl": 110},
    {"names": ["ocean", "océan", "mer", "sea", "aqua", "eau", "water", "marine", "abyssal", "tide", "maree", "marée", "naval", "ondine"], "hue": 195, "sat": 0.85, "hl": 180},
    {"names": ["soleil", "sun", "solaire", "solar", "aurore", "dawn", "midi", "helios"], "hue": 35, "sat": 0.95, "hl": 50},
    {"names": ["lune", "moon", "lunaire", "lunar", "argent", "silver", "stellaire", "etoile", "étoile", "star", "selene"], "hue": 220, "sat": 0.30, "hl": 210},
    {"names": ["ender", "enderite", "chorus", "warp", "teleport", "téléport", "endermite"], "hue": 170, "sat": 0.60, "hl": 280},
    {"names": ["nether", "enfer", "hell", "wither", "ame", "âme", "soul", "wraith", "spectre"], "hue": 300, "sat": 0.50, "hl": 185},
    {"names": ["rose", "pink", "amour", "love", "fleur", "flower", "sakura", "cerise", "blossom"], "hue": 330, "sat": 0.70, "hl": 340},
    {"names": ["arcane", "violet", "purple", "amethyste", "améthyste", "amethyst", "magie", "magic", "mystique", "mystic", "sorcier", "wizard", "mage", "occulte"], "hue": 270, "sat": 0.65, "hl": 285},
    {"names": ["cuivre", "copper", "bronze", "rouille", "rust", "laiton"], "hue": 25, "sat": 0.70, "hl": 35},
    {"names": ["diamant", "diamond", "cristal", "crystal", "saphir", "sapphire", "prisme", "gemme"], "hue": 185, "sat": 0.70, "hl": 190},
    {"names": ["fer", "iron", "acier", "steel", "metal", "métal", "chrome", "gris", "gray", "grey", "titan", "titane"], "hue": 210, "sat": 0.08, "hl": None},
    {"names": ["sable", "sand", "desert", "désert", "dune", "ambre", "amber", "topaze", "topaz"], "hue": 42, "sat": 0.75, "hl": 52},
    {"names": ["rubis", "ruby", "grenat", "garnet"], "hue": 350, "sat": 0.85, "hl": 5},
    {"names": ["obsidienne", "obsidian", "onyx", "basalte", "basalt"], "hue": 280, "sat": 0.35, "hl": 250},
    {"names": ["corail", "coral", "saumon", "salmon"], "hue": 12, "sat": 0.78, "hl": 25},
    {"names": ["menthe", "mint", "turquoise", "teal", "sarcelle"], "hue": 168, "sat": 0.62, "hl": 175},
    {"names": ["lavande", "lavender", "lilas", "lilac", "prune", "plum", "mauve"], "hue": 280, "sat": 0.45, "hl": 300},
    {"names": ["citron", "lemon", "lime", "citrus", "agrume", "soufre", "sulfur"], "hue": 68, "sat": 0.90, "hl": 60},
    {"names": ["chocolat", "chocolate", "cafe", "café", "coffee", "brun", "brown", "boue", "mud", "terreux"], "hue": 24, "sat": 0.55, "hl": 36},
    {"names": ["cendre", "ash", "fumee", "fumée", "smoke", "brume", "mist", "fog", "nuage", "cloud"], "hue": 220, "sat": 0.05, "hl": None},
    {"names": ["chaos", "corrompu", "corrupt", "corruption", "eldritch", "folie", "madness"], "hue": 305, "sat": 0.60, "hl": 120},
    {"names": ["dragon", "draconique", "drake", "wyrm"], "hue": 8, "sat": 0.82, "hl": 42},
    {"names": ["phenix", "phénix", "phoenix"], "hue": 22, "sat": 0.95, "hl": 52},
    {"names": ["vampire", "vampirique", "sombrelame", "nocturne"], "hue": 352, "sat": 0.60, "hl": 0},
    {"names": ["spectral", "fantome", "fantôme", "ghost", "ethere", "éthéré", "pale", "pâle"], "hue": 180, "sat": 0.25, "hl": 195},
    {"names": ["runique", "rune", "ancien", "antique", "primordial"], "hue": 200, "sat": 0.55, "hl": 260},
    {"names": ["ocean"], "hue": 195, "sat": 0.85, "hl": 180},
]

# matériaux / types d'objets (FR + quelques EN)
MATERIALS: List[str] = [
    "Épée", "Epee", "Lame", "Glaive", "Katana", "Rapière", "Cimeterre", "Dague", "Poignard", "Couteau",
    "Hache", "Hachette", "Pioche", "Pelle", "Houe", "Faux", "Faucille", "Masse", "Marteau", "Maillet",
    "Lance", "Pique", "Hallebarde", "Trident", "Fléau", "Bâton", "Sceptre", "Canne", "Arc", "Arbalète",
    "Bouclier", "Égide", "Plastron", "Cuirasse", "Armure", "Casque", "Heaume", "Jambières", "Bottes",
    "Gantelets", "Gants", "Cape", "Tunique", "Robe", "Anneau", "Bague", "Amulette", "Talisman", "Collier",
    "Couronne", "Diadème", "Orbe", "Relique", "Grimoire", "Parchemin", "Potion", "Fiole", "Cœur", "Croc",
    "Griffe", "Aile", "Écaille", "Minerai", "Lingot", "Cristal", "Gemme", "Pépite", "Éclat", "Fragment",
    "Sword", "Blade", "Axe", "Pickaxe", "Bow", "Staff", "Wand", "Shield", "Helmet", "Chestplate",
    "Ring", "Amulet", "Crown", "Orb", "Gem", "Ore", "Ingot", "Shard",
]

# adjectifs (préfixe ou suffixe)
ADJECTIVES: List[str] = [
    "Ancien", "Antique", "Maudit", "Sacré", "Royal", "Légendaire", "Mythique", "Brisé", "Infernal",
    "Divin", "Éternel", "Oublié", "Maléfique", "Béni", "Runique", "Spectral", "Ardent", "Glacial",
    "Céleste", "Démoniaque", "Sauvage", "Noble", "Impérial", "Primordial", "Ultime", "Suprême", "Mineur",
    "Majeur", "Corrompu", "Pur", "Sombre", "Lumineux", "Enchanté", "Forgé", "Sanglant", "Vénéneux",
    "Foudroyant", "Flamboyant", "Cristallin", "Brumeux", "Stellaire", "Lunaire", "Solaire", "Abyssal",
]

# connecteurs pour gabarits "Épée du Vent", "Lame de Feu", "Anneau des Ténèbres"
CONNECTORS_FR: List[str] = ["du", "de", "de la", "des", "de l'"]
CONNECTORS_EN: List[str] = ["of", "of the"]


def theme_by_name(token: str) -> Optional[Theme]:
    t = token.lower()
    for th in THEMES:
        if t in (n.lower() for n in th["names"]):
            return th
    return None
