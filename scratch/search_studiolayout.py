with open(r"c:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\ui\layout\StudioLayout.kt", "r", encoding="utf-8") as f:
    for idx, line in enumerate(f, 1):
        if "activeFill" in line or "activeStroke" in line or "fillColor" in line:
            print(f"{idx}: {line.strip()}")
