import os
import re

def deduplicate_xml(file_path):
    if not os.path.exists(file_path):
        return
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    new_lines = []
    seen_keys = set()

    for line in lines:
        # Check if line contains a string resource
        match = re.search(r'<string name="([^"]+)"', line)
        if match:
            key = match.group(1)
            if key in seen_keys:
                continue
            seen_keys.add(key)
        new_lines.append(line)

    with open(file_path, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)
    print(f"Deduplicated {file_path}")

base_path = "/home/frerox/StudioProjects/toolz/app/src/main/res"
folders = ["values", "values-es", "values-fr", "values-pt-rBR"]

for folder in folders:
    deduplicate_xml(os.path.join(base_path, folder, "strings.xml"))
