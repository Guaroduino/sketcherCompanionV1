import os

files = [
    r"c:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\managers\ToolManager.kt",
    r"c:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\SketcherViewModel.kt"
]

for file in files:
    if os.path.exists(file):
        with open(file, "r", encoding="utf-8", errors="ignore") as f:
            content = f.read()
            if "ToolType.PAINT" in content or "ToolType.WATERCOLOR" in content:
                print(f"File: {os.path.basename(file)}")
                lines = content.splitlines()
                for idx, line in enumerate(lines, 1):
                    if "fillColor" in line and ("strokeColor" in line or "color" in line) and "=" in line:
                        print(f"  {idx}: {line.strip()}")
