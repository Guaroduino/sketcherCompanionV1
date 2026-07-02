with open(r"c:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\StrokePipeline.kt", "r", encoding="utf-8") as f:
    for idx, line in enumerate(f, 1):
        if "activeFillColor =" in line or "activeFillColor" in line:
            print(f"{idx}: {line.strip()}")
