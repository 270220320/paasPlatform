#!/usr/bin/env bash
tail -n 120 /mnt/d/AiFlow/backend/logs/aiflow-backend.log | grep -B 2 -A 20 'ERROR' | tail -n 60
