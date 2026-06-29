import json

with open('scratch/transcript_match.txt', 'r', encoding='utf-8') as f:
    text = f.read()

# Try to find the JSON object string and parse it
import re
match = re.search(r'\{.*\}', text, re.DOTALL)
if match:
    json_str = match.group(0)
    try:
        data = json.loads(json_str)
        content = data.get('content', '')
        
        lines = content.split('\n')
        extracted = []
        in_diff = False
        for line in lines:
            if line.startswith('@@'):
                in_diff = True
                continue
            if in_diff:
                if line == '[diff_block_end]':
                    break
                if line.startswith('-'):
                    clean_line = line[1:].replace('\r', '')
                    extracted.append(clean_line)
        
        with open('scratch/extracted.txt', 'w', encoding='utf-8') as out:
            for ex in extracted:
                out.write(ex + '\n')
        print(f"Successfully extracted {len(extracted)} lines.")
    except Exception as e:
        print(f"Error parsing JSON: {e}")
else:
    print("No JSON found.")
