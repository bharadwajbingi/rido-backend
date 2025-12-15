#!/usr/bin/env python3
import os
import re
from pathlib import Path

# Directory containing integration tests
test_dir = Path("src/test/java/com/rido/auth/integration")

# Count changes
files_changed = 0
total_replacements = 0

# Process each Java file
for java_file in test_dir.glob("*.java"):
    with open(java_file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original_content = content
    changes_in_file = 0
    
    # 1. Fix TokenResponse method calls (getters, not record accessors)
    content, n = re.subn(r'\.accessToken\(\)', '.getAccessToken()', content)
    changes_in_file += n
    content, n = re.subn(r'\.refreshToken\(\)', '.getRefreshToken()', content)
    changes_in_file += n
    content, n = re.subn(r'\.expiresIn\(\)', '.getExpiresIn()', content)
    changes_in_file += n
    
    # 2. Fix AuditEvent enum constants
    content, n = re.subn(r'AuditEvent\.REGISTRATION\b', 'AuditEvent.SIGNUP', content)
    changes_in_file += n
    content, n = re.subn(r'AuditEvent\.TOKEN_REFRESH\b', 'AuditEvent.REFRESH_TOKEN', content)
    changes_in_file += n
    
    # 3. Fix LoginRequest constructor calls with 2 params -> 5 params
    # Match: new LoginRequest("username", "password")
    # Replace with: new LoginRequest("username", "password", null, null, null)
    content, n = re.subn(
        r'new LoginRequest\(([^,\)]+),\s*([^,\)]+)\)',
        r'new LoginRequest(\1, \2, null, null, null)',
        content
    )
    changes_in_file += n
    
    # Only write if changed
    if content != original_content:
        with open(java_file, 'w', encoding='utf-8') as f:
            f.write(content)
        files_changed += 1
        total_replacements += changes_in_file
        print(f"✓ {java_file.name}: {changes_in_file} replacements")

print(f"\nTotal: {files_changed} files updated, {total_replacements} replacements made")
