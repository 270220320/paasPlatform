#!/usr/bin/env bash
grep -iE 'dock3|dock-3|debug|services' /mnt/d/AiFlow/scripts/_paas_debug/paths.txt | head -30
echo '---all dock dirs---'
grep -E '20\.dock/' /mnt/d/AiFlow/scripts/_paas_debug/paths.txt | sed 's|docs/cn/60.api-reference/20.dock-to-cloud/00.mqtt/20.dock/||' | cut -d/ -f1 | sort -u
