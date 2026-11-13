from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
output = root / 'dist/FrameFlow-source.zip'
output.parent.mkdir(exist_ok=True)
folders = ['backend/src', 'frontend/src', 'frontend/public', 'database', 'scripts', 'docs']
files = ['README.md','AGENTS.md','CONTEXT.md','.gitignore','.env.example','backend/pom.xml','frontend/package.json','frontend/package-lock.json','frontend/index.html','frontend/vite.config.js']
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as archive:
    for name in files:
        archive.write(root/name, 'FrameFlow/'+name)
    for folder in folders:
        for path in sorted((root/folder).rglob('*')):
            if path.is_file() and '__pycache__' not in path.parts:
                archive.write(path, 'FrameFlow/'+str(path.relative_to(root)))
print(output)
