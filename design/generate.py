#!/usr/bin/env python3
"""Régénère les icônes Android (res/drawable + icône adaptative) à partir de design/icons.py.

Usage :  python3 design/generate.py
Les icônes sont dessinées en un seul endroit (icons.py) ; ce script ne fait que les convertir
au format "vector drawable" d'Android.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from icons import *  # noqa: E402,F403

RES = os.path.join(HERE, "..", "android", "app", "src", "main", "res")
HEAD = '<?xml version="1.0" encoding="utf-8"?>\n'
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'


def write(rel, text):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def stroke_path(d, color="#FF000000", width=STROKE):
    return (f'    <path\n        android:pathData="{d}"\n        android:strokeColor="{color}"\n'
            f'        android:strokeWidth="{width}"\n        android:strokeLineCap="round"\n'
            f'        android:strokeLineJoin="round" />\n')


def fill_path(d, color="#FF000000", alpha=None):
    if alpha:
        color = "#" + alpha + color[1:]
    return f'    <path\n        android:fillColor="{color}"\n        android:pathData="{d}" />\n'


def icon24(name, strokes, fills):
    body = "".join(stroke_path(p) for p in strokes) + "".join(fill_path(p) for p in fills)
    write(f"drawable/{name}.xml", HEAD + f'<vector {NS}\n    android:width="24dp"\n    android:height="24dp"\n'
          f'    android:viewportWidth="24"\n    android:viewportHeight="24">\n{body}</vector>\n')


NAV = {"home": "ic_nav_home", "clock": "ic_nav_programs", "history": "ic_nav_history",
       "cat": "ic_nav_cats", "can": "ic_nav_food"}
for key, name in NAV.items():
    icon24(name, *TABS[key])
for key, spec in ACTIONS.items():
    icon24("ic_action_" + key, *spec)

L = LOGO
logo = (fill_path(L["foot"], C_FOOT) + fill_path(L["body"], C_BODY)
        + "".join(fill_path(p, C_RIM, "99") for p in L["inner"]) + fill_path(L["rim"], C_RIM)
        + "".join(fill_path(p, C_KIB) for p in L["kibble"]) + "".join(fill_path(p, C_FACE) for p in L["eyes"])
        + fill_path(L["nose"], C_FACE) + "".join(stroke_path(p, C_FACE, 1.6) for p in L["mouth"]))
write("drawable/ic_launcher_foreground.xml", HEAD + "<!-- Logo : un bol en forme de tête de chat, plein de croquettes. -->\n"
      f'<vector {NS}\n    android:width="108dp"\n    android:height="108dp"\n    android:viewportWidth="108"\n'
      f'    android:viewportHeight="108">\n{logo}</vector>\n')

mono = " ".join(MONO["fill"] + MONO["holes"])
write("drawable/ic_launcher_monochrome.xml", HEAD + f'<vector {NS}\n    android:width="108dp"\n    android:height="108dp"\n'
      f'    android:viewportWidth="108"\n    android:viewportHeight="108">\n    <path\n        android:fillColor="#FF000000"\n'
      f'        android:fillType="evenOdd"\n        android:pathData="{mono}" />\n</vector>\n')
write("drawable/ic_logo_mark.xml", HEAD + "<!-- Logo monochrome rogné : icône de notification et en-tête de l'appli. -->\n"
      f'<vector {NS}\n    android:width="24dp"\n    android:height="24dp"\n    android:viewportWidth="72"\n'
      f'    android:viewportHeight="72">\n    <group\n        android:translateX="-18"\n        android:translateY="-18">\n'
      f'        <path\n            android:fillColor="#FFFFFFFF"\n            android:fillType="evenOdd"\n'
      f'            android:pathData="{mono}" />\n    </group>\n</vector>\n')
write("mipmap-anydpi-v26/ic_launcher.xml", HEAD + f'<adaptive-icon {NS}>\n'
      '    <background android:drawable="@color/launcher_bg" />\n'
      '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
      '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n</adaptive-icon>\n')
print("Icônes régénérées dans", os.path.normpath(RES))
