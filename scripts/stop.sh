#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
for service in backend frontend; do
  if [ -f ".runtime/$service.pid" ]; then
    pid=$(cat ".runtime/$service.pid")
    pkill -TERM -P "$pid" 2>/dev/null || true
    kill "$pid" 2>/dev/null || true
    rm ".runtime/$service.pid"
  fi
done
printf '已停止项目开发服务。\n'
