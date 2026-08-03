import re

file_path = '/home/frerox/StudioProjects/toolz/app/src/main/res/values/strings.xml'
with open(file_path, 'r') as f:
    lines = f.readlines()

new_lines = []
seen_keys = set()

for line in lines:
    match = re.search(r'name="([^"]+)"', line)
    if match:
        key = match.group(1)
        if key in seen_keys:
            continue
        seen_keys.add(key)
    new_lines.append(line)

with open(file_path, 'w') as f:
    f.writelines(new_lines)
