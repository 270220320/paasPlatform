#!/usr/bin/env bash
grep -oE '"path": "[^"]*"' /mnt/d/AiFlow/scripts/_paas_debug/tree.json | sed 's/"path": //' | tr -d '"' > /mnt/d/AiFlow/scripts/_paas_debug/paths.txt
wc -l /mnt/d/AiFlow/scripts/_paas_debug/paths.txt
grep -i 'mqtt' /mnt/d/AiFlow/scripts/_paas_debug/paths.txt | head -40
