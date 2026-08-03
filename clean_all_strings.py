import re
import os

base_dir = '/home/frerox/StudioProjects/toolz/app/src/main/res/'
folders = ['values', 'values-es', 'values-fr', 'values-pt-rBR']

for folder in folders:
    file_path = os.path.join(base_dir, folder, 'strings.xml')
    if not os.path.exists(file_path):
        continue

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
