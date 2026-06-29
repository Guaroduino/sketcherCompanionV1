import json
import re

transcript_path = r'C:\Users\Guaroduino\.gemini\antigravity\brain\6b26f58f-1184-423a-a165-d911b51d5ec1\.system_generated\logs\transcript_full.jsonl'

diff_output = ""
with open(transcript_path, 'r', encoding='utf-8') as f:
    for line in f:
        try:
            data = json.loads(line)
            if data.get('type') == 'SYSTEM_MESSAGE' and 'The following changes were made by the multi_replace_file_content tool to' in data.get('content', ''):
                content = data.get('content')
                if 'updateTheme(newConfig: UiThemeConfig)' in content:
                    diff_output = content
                    break
        except:
            pass

if not diff_output:
    print("Diff not found in SYSTEM_MESSAGE!")
    # Check GENERIC
    with open(transcript_path, 'r', encoding='utf-8') as f:
        for line in f:
            try:
                data = json.loads(line)
                if data.get('type') == 'GENERIC' and 'updateTheme(newConfig' in data.get('content', ''):
                    diff_output = data.get('content')
                    break
            except:
                pass
    if not diff_output:
        # Check tool responses
        with open(transcript_path, 'r', encoding='utf-8') as f:
            for line in f:
                try:
                    data = json.loads(line)
                    if data.get('type') == 'TOOL_RESPONSE' and 'updateTheme(newConfig' in data.get('content', ''):
                        diff_output = data.get('content')
                        break
                except:
                    pass

if diff_output:
    lines = diff_output.split('\n')
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
                # Handle \r inside the string if any
                clean_line = line[1:].replace('\r', '')
                extracted.append(clean_line)
    
    with open('scratch/extracted.txt', 'w', encoding='utf-8') as f:
        for ex in extracted:
            f.write(ex + '\n')
    print(f"Extracted {len(extracted)} lines.")
else:
    print("Could not find the diff output anywhere.")
