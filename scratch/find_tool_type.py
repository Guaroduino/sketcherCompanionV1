import os
import re

search_dir = r"c:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1"
pattern = re.compile(r"enum class ToolType")

for root, dirs, files in os.walk(search_dir):
    for file in files:
        if file.endswith(".kt"):
            path = os.path.join(root, file)
            with open(path, "r", encoding="utf-8", errors="ignore") as f:
                for line_idx, line in enumerate(f, 1):
                    if pattern.search(line):
                        print(f"{file}:{line_idx}: {line.strip()}")
                        # print surrounding lines
                        f.seek(0)
                        lines = f.readlines()
                        start = max(0, line_idx - 5)
                        end = min(len(lines), line_idx + 15)
                        for i in range(start, end):
                            print(f"  {i+1}: {lines[i].strip()}")
