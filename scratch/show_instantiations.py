with open(r"c:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\StrokePipeline.kt", "r", encoding="utf-8") as f:
    lines = f.readlines()

instantiation_lines = [666, 741, 841, 901, 950, 1017]
for l_idx in instantiation_lines:
    print(f"--- Line {l_idx} ---")
    start = max(0, l_idx - 5)
    end = min(len(lines), l_idx + 25)
    for idx in range(start, end):
        print(f"{idx+1}: {lines[idx].rstrip()}")
