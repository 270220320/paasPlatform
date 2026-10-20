#!/usr/bin/env bash
# One-off helper: regenerate admin password reset SQL (bcrypt $2a$ for admin123).
# Reads nothing sensitive; output SQL is executed on Windows side via:
#   Get-Content d:\AiFlow\scripts\_reset_admin_pwd.sql -Raw | & "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" --host=127.0.0.1 --user=root --password=meng1234 UAVPlatform
set -euo pipefail
HASH=$(python3 /mnt/d/AiFlow/scripts/_gen_bcrypt.py admin123)
SQL=/mnt/d/AiFlow/scripts/_reset_admin_pwd.sql
printf "UPDATE t_user SET password_hash='%s' WHERE id=1 AND account='admin@aiflow.local';\n" "$HASH" > "$SQL"
wc -c "$SQL"
cat "$SQL"
