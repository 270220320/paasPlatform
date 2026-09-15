#!/usr/bin/env bash
curl -sL -m 30 'https://api.github.com/repos/dji-sdk/Cloud-API-Doc/git/trees/master?recursive=1' -o /mnt/d/AiFlow/scripts/_paas_debug/tree.json
grep -o '"path": "[^"]*"' /mnt/d/AiFlow/scripts/_paas_debug/tree.json | grep -iE 'dock3|dock-3' | head -40
echo '---services md---'
grep -o '"path": "[^"]*"' /mnt/d/AiFlow/scripts/_paas_debug/tree.json | grep -iE 'mqtt.*service|service.*md' | head -20
