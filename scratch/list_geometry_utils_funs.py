import os
import re

path = r"c:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\utils\GeometryUtils.kt"
with open(path, "r", encoding="utf-8", errors="ignore") as f:
    for idx, line in enumerate(f, 1):
        if "fun " in line:
            print(f"{idx}: {line.strip()}")
