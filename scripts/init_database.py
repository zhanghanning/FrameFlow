"""用数据库管理员身份初始化本项目，保留已有业务数据。WSL: sudo python3 scripts/init_database.py"""
import os
import pwd
import secrets
import shlex
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
env_file = root / '.env'
if os.geteuid() != 0:
    raise SystemExit('请用 sudo python3 scripts/init_database.py 运行，或从 Windows 用 wsl -d Ubuntu-22.04 -u root 调用。')
settings = {}
if env_file.exists():
    for line in env_file.read_text().splitlines():
        if '=' in line and not line.startswith('#'):
            key, value = line.split('=', 1)
            settings[key] = (shlex.split(value) or [''])[0]
password = settings.get('DB_PASSWORD', secrets.token_urlsafe(30))
quoted_password = password.replace("'", "''")
owner = pwd.getpwnam(root.owner())
for database, user, env_name, uploads, port in [
    ('frameflow', 'frameflow_app', '.env', root / 'uploads', 8080),
    ('frameflow_test', 'frameflow_test_app', '.env.test', root / '.runtime/test-uploads', 8082),
]:
    sql = f"CREATE DATABASE IF NOT EXISTS {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER IF NOT EXISTS '{user}'@'localhost' IDENTIFIED BY '{quoted_password}';"
    subprocess.run(['mysql'], input=sql, text=True, check=True)
    table_count = subprocess.check_output(['mysql','-Nse',f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{database}' AND table_type='BASE TABLE'"], text=True).strip()
    if table_count == '0':
        for name in ['schema.sql', 'objects.sql', 'seed.sql']:
            subprocess.run(['mysql', database], input=(root / 'database' / name).read_text(), text=True, check=True)
        print(f'{database}: 已初始化表、数据库对象与演示数据')
    else:
        print(f'{database}: 已存在业务表，保留数据')
    subprocess.run(['mysql', database], input=(root / 'database/identity-workflow.sql').read_text(), text=True, check=True)
    subprocess.run(['mysql', database], input=(root / 'database/platform-integration.sql').read_text(), text=True, check=True)
    subprocess.run(['mysql', database], input=(root / 'database/settlement-workflow.sql').read_text(), text=True, check=True)
    target = root / env_name
    if not target.exists():
        target.write_text(f"DB_URL='jdbc:mysql://localhost:3306/{database}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'\nDB_USER={user}\nDB_PASSWORD='{password}'\nUPLOAD_DIR={uploads}\nSERVER_PORT={port}\nJAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64\n", encoding='utf-8')
        os.chmod(target, 0o600)
        os.chown(target, owner.pw_uid, owner.pw_gid)
subprocess.run(['mysql'], input=(root / 'database/security.sql').read_text(), text=True, check=True)
subprocess.run(['mysql'], input="GRANT 'frameflow_app_role' TO 'frameflow_app'@'localhost'; SET DEFAULT ROLE 'frameflow_app_role' TO 'frameflow_app'@'localhost'; GRANT SELECT,INSERT,UPDATE,DELETE,EXECUTE ON frameflow_test.* TO 'frameflow_test_app'@'localhost';", text=True, check=True)
for database in ['frameflow','frameflow_test']:
    for file in (root / 'uploads/assets/demo').glob('*.svg'):
        subprocess.run(['mysql',database],input=f"UPDATE asset SET size_bytes={file.stat().st_size} WHERE storage_path='assets/demo/{file.name}';",text=True,check=True)
print('本地连接信息保存在 .env 与 .env.test；源码包不包含这些文件。')
