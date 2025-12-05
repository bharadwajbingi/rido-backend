import os

smoke_dir = 'smoke'
for filename in os.listdir(smoke_dir):
    if filename.endswith('.sh'):
        filepath = os.path.join(smoke_dir, filename)
        with open(filepath, 'rb') as f:
            content = f.read()
        
        # Replace CRLF with LF
        new_content = content.replace(b'\r\n', b'\n')
        
        # Also replace any remaining CR that are not part of CRLF (just in case)
        new_content = new_content.replace(b'\r', b'')
        
        with open(filepath, 'wb') as f:
            f.write(new_content)
        print(f"Fixed {filename}")
