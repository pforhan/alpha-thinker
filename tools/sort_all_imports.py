import os
import re
import glob

def process_file(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    lines = content.splitlines()
    
    # Find package line
    package_idx = -1
    for i, line in enumerate(lines):
        if line.strip().startswith('package '):
            package_idx = i
            break
            
    if package_idx == -1:
        # Check for wildcards even if no package is found
        has_wildcard = any('*' in line for line in lines if line.strip().startswith('import '))
        return None, has_wildcard

    # Find import range
    import_lines = []
    first_import_idx = -1
    last_import_idx = -1
    
    # Scan from package_idx + 1
    for i in range(package_idx + 1, len(lines)):
        line = lines[i]
        if line.strip().startswith('import '):
            if first_import_idx == -1:
                first_import_idx = i
            import_lines.append(line.strip())
            last_import_idx = i
        elif first_import_idx != -1 and line.strip() != '':
            # Encountered non-blank line after imports started
            break
            
    if not import_lines:
        # Check for wildcards in the rest of the file if no imports were found in the expected block
        has_wildcard = any('*' in line for line in lines if line.strip().startswith('import '))
        return None, has_wildcard

    # Simple alphabetical sort for all imports
    has_wildcard = any('*' in imp for imp in import_lines)
    import_lines.sort()
    
    # Construct new content
    # 1. Headers and package
    new_lines = lines[:package_idx + 1]
    # 2. One blank line
    new_lines.append('')
    # 3. Sorted imports (no blank lines between them)
    new_lines.extend(import_lines)
    
    # 4. Remainder of the file
    # Everything after the last import line
    remainder = lines[last_import_idx + 1:]
    # Trim leading blank lines from remainder
    while remainder and remainder[0].strip() == '':
        remainder.pop(0)
    
    if remainder:
        new_lines.append('') # One blank line before the code
        new_lines.extend(remainder)
        
    # Ensure trailing newline
    final_content = '\n'.join(new_lines)
    if not final_content.endswith('\n'):
        final_content += '\n'
    
    # Compare
    if content.rstrip('\n') == final_content.rstrip('\n'):
        return None, has_wildcard
        
    return final_content, has_wildcard

def main():
    changed_count = 0
    wildcard_files = []
    # Find all .kt files, avoiding the tools directory if we are in it,
    # but usually we just want all in the project.
    kt_files = glob.glob('**/*.kt', recursive=True)
    
    for file_path in kt_files:
        new_content, has_wildcard = process_file(file_path)
        if has_wildcard:
            wildcard_files.append(file_path)
        
        if new_content:
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(new_content)
            print(f"Changed {file_path}")
            changed_count += 1
            
    print(f"\nSummary:")
    print(f"Files changed: {changed_count}")
    if wildcard_files:
        print(f"Files with * imports:")
        for f in wildcard_files:
            print(f"  - {f}")

if __name__ == '__main__':
    main()
