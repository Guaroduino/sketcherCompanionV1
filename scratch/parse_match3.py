import re

with open('scratch/transcript_match.txt', 'r', encoding='utf-8') as f:
    text = f.read()

# Find the content block between [diff_block_start] and [diff_block_end]
match = re.search(r'\[diff_block_start\](.*?)\[diff_block_end\]', text, re.DOTALL)
if match:
    diff_text = match.group(1)
    # The JSON string escapes \r and \n. We should unescape it first.
    diff_text = diff_text.replace(r'\r', '\r').replace(r'\n', '\n').replace(r'\\"', '"')
    
    lines = diff_text.split('\n')
    extracted = []
    for line in lines:
        if line.startswith('-'):
            extracted.append(line[1:])
    
    with open('scratch/extracted.txt', 'w', encoding='utf-8') as out:
        for ex in extracted:
            out.write(ex + '\n')
    print(f"Extracted {len(extracted)} lines properly.")
else:
    print("Could not find diff_block_start")
