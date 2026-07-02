import os

files = [
    r"c:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\managers\ToolManager.kt",
    r"c:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\SketcherViewModel.kt"
]

for file in files:
    if os.path.exists(file):
        with open(file, "r", encoding="utf-8", errors="ignore") as f:
            for idx, line in enumerate(f, 1):
                if "strokeColor" in line or "fillColor" in line or "activeStrokeColor" in line or "activeFillColor" in line:
                    if len(line.strip()) < 120:
                        print(f"{os.path.basename(file)}:{idx}: {line.strip()}")
