import json

transcript_path = r'C:\Users\Guaroduino\.gemini\antigravity\brain\6b26f58f-1184-423a-a165-d911b51d5ec1\.system_generated\logs\transcript_full.jsonl'

lines = []
with open(transcript_path, 'r', encoding='utf-8') as f:
    for line in f:
        try:
            data = json.loads(line)
            if data.get('type') == 'TOOL_RESPONSE' and 'SketcherViewModel.kt' in data.get('content', ''):
                lines.append(data.get('content'))
        except:
            pass

with open('scratch/recovered.txt', 'w', encoding='utf-8') as f:
    for block in lines:
        f.write(block)
        f.write('\n\n---NEXT---\n\n')
print(f'Recovered {len(lines)} blocks.')
