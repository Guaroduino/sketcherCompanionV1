import os

files = [
    r"c:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\managers\ToolManager.kt",
    r"c:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\SketcherViewModel.kt"
]

for file in files:
    if os.path.exists(file):
        with open(file, "r", encoding="utf-8", errors="ignore") as f:
            for idx, line in enumerate(f, 1):
                if "ToolType.PAINT" in line or "ToolType.WATERCOLOR" in line:
                    print(f"{os.path.basename(file)}:{idx}: {line.strip()}")
