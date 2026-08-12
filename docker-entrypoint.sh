#!/bin/sh
set -e

# 컨테이너 메모리 한도의 70~80% 수준으로 힙 상한을 명시 (-Xmx).
# ECS/Docker cgroup 한도를 초과해 OOM Killer(SIGKILL, exit 137)로 종료되는 것을 방지한다.
HEAP_PERCENT="${JAVA_HEAP_PERCENT:-75}"
MEMORY_SOURCE="fallback"
MEM_LIMIT_BYTES=$((1024 * 1024 * 1024))

is_unlimited_memory() {
  limit="$1"
  [ -z "$limit" ] && return 0
  [ "$limit" = "max" ] && return 0
  # cgroup v1: 제한 없음일 때 9223372036854771712 (0x7FFFFFFFFFFFF000) 반환
  if [ "$limit" -ge 9223372036854771712 ] 2>/dev/null; then
    return 0
  fi
  return 1
}

read_cgroup_v2_limit_bytes() {
  [ -f /sys/fs/cgroup/cgroup.controllers ] || return 1

  best=""
  for f in $(find /sys/fs/cgroup -name memory.max 2>/dev/null); do
    val="$(cat "$f" 2>/dev/null)" || continue
    if is_unlimited_memory "$val"; then
      continue
    fi
    if [ -z "$best" ] || [ "$val" -lt "$best" ]; then
      best="$val"
    fi
  done

  [ -n "$best" ] || return 1
  MEM_LIMIT_BYTES="$best"
  MEMORY_SOURCE="cgroup-v2"
  return 0
}

resolve_mem_limit_bytes() {
  if [ -n "$CONTAINER_MEMORY_MB" ]; then
    MEM_LIMIT_BYTES=$((CONTAINER_MEMORY_MB * 1024 * 1024))
    MEMORY_SOURCE="CONTAINER_MEMORY_MB"
    return
  fi

  if read_cgroup_v2_limit_bytes; then
    return
  fi

  if [ -f /sys/fs/cgroup/memory.max ]; then
    limit="$(cat /sys/fs/cgroup/memory.max)"
    if ! is_unlimited_memory "$limit"; then
      MEM_LIMIT_BYTES="$limit"
      MEMORY_SOURCE="cgroup-v2-root"
      return
    fi
  fi

  if [ -f /sys/fs/cgroup/memory/memory.limit_in_bytes ]; then
    limit="$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes)"
    if ! is_unlimited_memory "$limit"; then
      MEM_LIMIT_BYTES="$limit"
      MEMORY_SOURCE="cgroup-v1"
      return
    fi
  fi

  MEMORY_SOURCE="fallback"
}

resolve_mem_limit_bytes

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

echo "JVM heap: source=${MEMORY_SOURCE}, container=${MEM_LIMIT_BYTES}B, -Xms=${XMS_MB}m, -Xmx=${XMX_MB}m (${HEAP_PERCENT}%), MaxMetaspaceSize=${META_MB}m"

exec java \
  ${JAVA_OPTS} \
  -Xms"${XMS_MB}m" \
  -Xmx"${XMX_MB}m" \
  -XX:MaxMetaspaceSize="${META_MB}m" \
  -jar app.jar
