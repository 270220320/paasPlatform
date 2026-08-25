#!/usr/bin/env bash
# 临时诊断 41：验证 30812 无前缀 manage 路由（主站真实调用的路径）
TOK="eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3ODczNzA5NzMsImp3dF92ZXJzaW9uIjoiNSIsIm5iZiI6MTc4NzI4NDU3Mywic3ViIjoiZmgyLXByaXZhdGl6YXRpb24tc3lzdGVtIn0.9XXlkVHlTrobXdYutfZSPdJ6KLK65gi7qD3apCqZE2vs-leY1EsHXdA-TCVVkKd2mmgALdw1I4Dz6mcUPp7RzA"
G="http://192.168.76.141:30812"
echo "=== A) deploy-licenses（主站启动时公开调用的）==="
curl -sS -i -m 8 "$G/manage/api/v1/deploy-licenses" 2>&1 | head -c 700
echo; echo
echo "=== B) 带主站 token 再测 organizations（GET）==="
curl -sS -m 8 -H "X-User-Token: $TOK" "$G/manage/api/v1/organizations" 2>&1 | head -c 300
echo; echo
echo "=== C) web-login（主站登录跳转路径）==="
curl -sS -i -m 8 "$G/manage/api/v1/web-login" 2>&1 | head -c 400
