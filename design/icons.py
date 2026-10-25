# Source unique des icônes (tracés SVG, grille 24 pour les icônes, 108 pour le logo).
# Lancer `python3 design/generate.py` pour régénérer les fichiers Android à partir de ce fichier.
# Icônes d'onglets : grille 24, trait 1.8, bouts ronds, sans remplissage (sauf petits points).
STROKE = 1.8

TABS = {
  # name: (list of stroke paths, list of filled paths)
  "home": (
    ["M4 11 L12 4.2 L20 11", "M6 9.6 V18.2 Q6 20 7.8 20 H16.2 Q18 20 18 18.2 V9.6", "M10.2 20 V15.6 Q10.2 13.8 12 13.8 Q13.8 13.8 13.8 15.6 V20"],
    []),
  "clock": (  # réveil = programme / rappel
    ["M4.8 13.4 a7.2 7.2 0 1 0 14.4 0 a7.2 7.2 0 1 0 -14.4 0 Z", "M12 9.6 V13.6 L14.7 15.2", "M4.2 7.6 L7.2 4.4", "M19.8 7.6 L16.8 4.4", "M7.4 19.4 L6 21", "M16.6 19.4 L18 21"],
    []),
  "history": (  # presse-papier coché = repas passés
    ["M7.6 4.5 H16.4 Q19 4.5 19 7.1 V17.9 Q19 20.5 16.4 20.5 H7.6 Q5 20.5 5 17.9 V7.1 Q5 4.5 7.6 4.5 Z", "M8.8 12.9 L11 15.1 L15.3 10.6"],
    []),
  "cat": (   # tête de chat
    ["M4.6 4.4 L9.2 7.3 Q12 6.6 14.8 7.3 L19.4 4.4 V12.6 C19.4 16.9 16.2 20 12 20 C7.8 20 4.6 16.9 4.6 12.6 Z", "M10.9 15.3 Q12 16.3 13.1 15.3"],
    ["M8.2 12.2 a1.05 1.05 0 1 0 2.1 0 a1.05 1.05 0 1 0 -2.1 0 Z", "M13.7 12.2 a1.05 1.05 0 1 0 2.1 0 a1.05 1.05 0 1 0 -2.1 0 Z"]),
  "can": (   # boîte de nourriture : couvercle + anneau, étiquette avec un poisson
    ["M5.4 6.6 A6.6 2.4 0 1 1 18.6 6.6 A6.6 2.4 0 1 1 5.4 6.6 Z", "M5.4 6.6 V17.4 C5.4 18.8 8.3 19.9 12 19.9 C15.7 19.9 18.6 18.8 18.6 17.4 V6.6", "M10.2 5.9 Q12 4.6 13.8 5.9",
     "M8.4 12.6 Q10.7 10.4 13 12.6 Q10.7 14.8 8.4 12.6 Z", "M13 12.6 L15 11.2 V14 Z"],
    []),
}

# --- Logo (grille 108, zone sûre = cercle de rayon 33 au centre) ---
C_BODY="#C2672B"; C_RIM="#E59A4F"; C_FOOT="#8F4513"; C_FACE="#FFF1E3"; C_BG="#FFE3C7"; C_KIB="#8F4513"
LOGO = {
  "body":  "M25 49 L27.2 26 Q27.9 23 30.5 24.7 L44.5 35.4 Q54 33.2 63.5 35.4 L77.5 24.7 Q80.1 23 80.8 26 L83 49 C83 69 73.5 80 54 80 C34.5 80 25 69 25 49 Z",
  "inner": ["M31.2 38.6 L32 30.8 L39.2 36.2 Z", "M76.8 38.6 L76 30.8 L68.8 36.2 Z"],
  "foot":  "M42.5 79 H65.5 L67.6 83.6 Q67.9 85 66.4 85 H41.6 Q40.1 85 40.4 83.6 Z",
  "rim":   "M27.6 46.6 Q54 53.4 80.4 46.6 V50 Q54 57 27.6 50 Z",
  "eyes":  ["M39.9 60 a3.1 4.2 0 1 0 6.2 0 a3.1 4.2 0 1 0 -6.2 0 Z", "M61.9 60 a3.1 4.2 0 1 0 6.2 0 a3.1 4.2 0 1 0 -6.2 0 Z"],
  "nose":  "M51.6 67.8 H56.4 Q57.2 67.8 56.7 68.5 L54.8 71 Q54 71.8 53.2 71 L51.3 68.5 Q50.8 67.8 51.6 67.8 Z",
  "mouth": ["M54 71.8 V73.4 Q51.8 76 49.4 74.3", "M54 73.4 Q56.2 76 58.6 74.3"],
  "kibble":["M49.4 32.2 a2.7 2.7 0 1 0 5.4 0 a2.7 2.7 0 1 0 -5.4 0 Z", "M54.6 31.4 a2.7 2.7 0 1 0 5.4 0 a2.7 2.7 0 1 0 -5.4 0 Z", "M44.6 33.6 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0 Z", "M59.6 33.4 a2.5 2.5 0 1 0 5 0 a2.5 2.5 0 1 0 -5 0 Z"],
}
def logo_group(mono=False):
    pass

# Icônes d'action (même famille, trait 1.8)
ACTIONS = {
  "plus":     (["M12 5.5 V18.5", "M5.5 12 H18.5"], []),
  "refresh":  (["M18.6 11 A6.6 6.6 0 0 0 6.4 8.4", "M6 4.8 V8.6 H9.8", "M5.4 13 A6.6 6.6 0 0 0 17.6 15.6", "M18 19.2 V15.4 H14.2"], []),
  "repeat":   (["M16.5 3.5 L19.5 6.5 L16.5 9.5", "M4.5 11 V10 A3.5 3.5 0 0 1 8 6.5 H19.5", "M7.5 20.5 L4.5 17.5 L7.5 14.5", "M19.5 13 V14 A3.5 3.5 0 0 1 16 17.5 H4.5"], []),
  "chevron_left":  (["M14.5 6 L8.5 12 L14.5 18"], []),
  "chevron_right": (["M9.5 6 L15.5 12 L9.5 18"], []),
  "chevron_down":  (["M6 9.5 L12 15.5 L18 9.5"], []),
  "settings": (["M4.5 8 H11.2", "M16.8 8 H19.5", "M4.5 16 H7.2", "M12.8 16 H19.5",
                "M14 5.8 a2.2 2.2 0 1 0 0 4.4 a2.2 2.2 0 1 0 0 -4.4 Z", "M10 13.8 a2.2 2.2 0 1 0 0 4.4 a2.2 2.2 0 1 0 0 -4.4 Z"], []),
}
# Monochrome (icône thématique Android + petite icône de notification) : silhouette avec yeux, nez et lèvre découpés
MONO = {"fill": [LOGO["body"], LOGO["foot"]], "holes": LOGO["eyes"] + [LOGO["nose"], LOGO["rim"]]}
