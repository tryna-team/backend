#!/bin/sh
set -e

# 컨테이너 메모리 한도의 70~80% 수준으로 힙 상한을 명시 (-Xmx).
# ECS/Docker cgroup 한도를 초과해 OOM Killer(SIGKILL, exit 137)로 종료되는 것을 방지한다.
HEAP_PERCENT="${JAVA_HEAP_PERCENT:-75}"

read_mem_limit_bytes() {
  if [ -f /sys/fs/cgroup/memory.max ]; then
    limit="$(cat /sys/fs/cgroup/memory.max)"
    if [ "$limit" = "max" ]; then
      echo 1073741824
      return
    fi
    echo "$limit"
    return
  fi

  if [ -f /sys/fs/cgroup/memory/memory.limit_in_bytes ]; then
    limit="$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)"
    # cgroup v1: 제한 없음일 때 매우 큰 값이 들어온다.
    if [ "$limit" -gt 9223372036854771712 ] 2>/dev/null; then
      echo 1073741824
      return
    fi
    echo "$limit"
    return
  fi

  echo 1073741824
}

MEM_LIMIT_BYTES="$(read_mem_limit_bytes)"
XMX_MB=$((MEM_LIMIT_BYTES * HEAP_PERCENT / 100 / 1024 / 1024))

if [ "$XMX_MB" -lt 128 ]; then
  XMX_MB=128
fi

XMS_MB=$((XMX_MB / 2))
if [ "$XMS_MB" -lt 64 ]; then
  XMS_MB=64
fi

# 비힙(metaspace 등) 사용량이 컨테이너 한도를 잡아먹지 않도록 상한을 둔다.
META_MB=$((MEM_LIMIT_BYTES / 1024 / 1024 * 15 / 100))
if [ "$META_MB" -gt 256 ]; then
  META_MB=256
fi
if [ "$META_MB" -lt 64 ]; then
  META_MB=64
fi

echo "JVM heap: container=${MEM_LIMIT_BYTES}B, -Xms=${XMS_MB}m, -Xmx=${XMX_MB}m (${HEAP_PERCENT}%), MaxMetaspaceSize=${META_MB}m"

exec java \
  ${JAVA_OPTS} \
  -Xms"${XMS_MB}m" \
  -Xmx"${XMX_MB}m" \
  -XX:MaxMetaspaceSize="${META_MB}m" \
  -jar app.jar
