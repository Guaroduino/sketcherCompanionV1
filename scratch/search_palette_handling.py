with open(r"c:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\ui\layout\StudioLayout.kt", "r", encoding="utf-8") as f:
    for idx, line in enumerate(f, 1):
        if "setStrokeColor" in line or "setFillColor" in line or "Palette" in line or "colorClick" in line:
            print(f"{idx}: {line.strip()}")
