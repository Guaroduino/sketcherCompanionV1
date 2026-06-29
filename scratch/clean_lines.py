import sys

with open('app/src/main/java/com/sketcher/sketchercompanionv1/ui/layout/StudioLayout.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
consecutive_blanks = 0
for line in lines:
    if line.strip() == '':
        consecutive_blanks += 1
        if consecutive_blanks <= 2:
            new_lines.append('\n')
    else:
        consecutive_blanks = 0
        new_lines.append(line)

with open('app/src/main/java/com/sketcher/sketchercompanionv1/ui/layout/StudioLayout.kt', 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
print(f'Cleaned. Original: {len(lines)} lines. New: {len(new_lines)} lines.')
