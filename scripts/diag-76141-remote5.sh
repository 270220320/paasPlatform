#!/usr/bin/env bash
# 在 76.141 上执行：dockerd socket FD 逐 inode 对照内核协议表 + 泄漏速率采样
SUDO="sudo -S"
PW="Tksc@197653"
echo "$PW" | $SUDO -v 2>/dev/null   # 预热 sudo 凭证，避免后续与 heredoc 冲突

echo "===== T0 SNAPSHOT $(date -u '+%F %T UTC') ====="
echo "$PW" | $SUDO bash -c 'ls /proc/1266/fd 2>/dev/null | wc -l; ls -l /proc/1266/fd 2>/dev/null | grep -c "socket:"'

echo "===== DOCKER INFO ====="
echo "$PW" | $SUDO curl -s -m 10 --unix-socket /var/run/docker.sock http://localhost/info | python3 -c "import sys,json;d=json.load(sys.stdin);print({k:d.get(k) for k in ['ServerVersion','NFd','NGoroutines','Containers','ContainersRunning','HealthCheckRunning' if False else 'NCPU','Driver']})"

echo "===== SOCKET INODE -> KERNEL TABLE MAPPING ====="
$SUDO python3 - <<'PYEOF'
import os,re
fd_dir='/proc/1266/fd'
sock=[]
for fd in os.listdir(fd_dir):
    try: t=os.readlink(os.path.join(fd_dir,fd))
    except Exception: continue
    m=re.match(r'socket:\[(\d+)\]',t)
    if m: sock.append(int(m.group(1)))
sock=set(sock)

def load(path,col,skip):
    s=set()
    try: lines=open(path).read().splitlines()[skip:]
    except Exception: return s
    for ln in lines:
        f=ln.split()
        try: s.add(int(f[col-1]))
        except Exception: pass
    return s

tcp   = load('/proc/net/tcp',10,1) | load('/proc/net/tcp6',10,1)
unix_ = load('/proc/net/unix',7,1)
netlk = load('/proc/net/netlink',8,1)
pkt   = load('/proc/net/packet',8,1)
known = tcp|unix_|netlk|pkt
print('dockerd socket FD total :',len(sock))
print('  found in tcp/tcp6     :',len(sock & tcp))
print('  found in unix         :',len(sock & unix_))
print('  found in netlink      :',len(sock & netlk))
print('  found in packet       :',len(sock & pkt))
print('  ORPHAN (no table entry):',len(sock - known))
# unix 表里 dockerd 的 socket 行样例（看 Path/类型）
cnt=0
try:
    for ln in open('/proc/net/unix').read().splitlines()[1:]:
        f=ln.split()
        if len(f)>=7 and f[6].isdigit() and int(f[6]) in sock:
            print('  unix-row:',ln[:120]); cnt+=1
            if cnt>=5: break
except Exception as e: print(e)
PYEOF

echo "===== GLOBAL CLOSE_WAIT ====="
echo "$PW" | $SUDO ss -tan state close-wait 2>/dev/null | wc -l

echo "===== DOCKER NETWORK COUNT ====="
echo "$PW" | $SUDO docker network ls 2>/dev/null | wc -l

echo "===== SLEEP 60s FOR RATE ====="
sleep 60
echo "===== T1 SNAPSHOT $(date -u '+%F %T UTC') ====="
echo "$PW" | $SUDO bash -c 'ls /proc/1266/fd 2>/dev/null | wc -l; ls -l /proc/1266/fd 2>/dev/null | grep -c "socket:"'

echo "===== DOCKER INFO AGAIN (NFd/NGoroutines drift) ====="
echo "$PW" | $SUDO curl -s -m 10 --unix-socket /var/run/docker.sock http://localhost/info | python3 -c "import sys,json;d=json.load(sys.stdin);print({k:d.get(k) for k in ['NFd','NGoroutines']})"
