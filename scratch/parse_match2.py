import re

with open('scratch/transcript_match.txt', 'r', encoding='utf-8') as f:
    text = f.read()

# find all lines that start with - in the diff
extracted = []
parts = text.split(r'\n-')
if len(parts) < 2:
    parts = text.split(r'\r\n-')
if len(parts) < 2:
    parts = text.split(r'\\r\\n-')

if len(parts) > 1:
    for p in parts[1:]:
        line = p.split(r'\r')[0].split(r'\n')[0]
        extracted.append(line.replace('\\"', '"'))

with open('scratch/extracted.txt', 'w', encoding='utf-8') as out:
    for ex in extracted:
        out.write(ex + '\n')
print(f"Extracted {len(extracted)} lines.")
