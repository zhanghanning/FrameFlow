#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
set -a
source .env
set +a
mkdir -p .runtime
if [ ! -d frontend/node_modules ]; then npm --prefix frontend ci; fi
if [ ! -f .runtime/backend.pid ] || ! kill -0 "$(cat .runtime/backend.pid)" 2>/dev/null; then
  (cd backend && exec mvn -q spring-boot:run) > .runtime/backend.log 2>&1 &
  echo $! > .runtime/backend.pid
fi
if [ ! -f .runtime/frontend.pid ] || ! kill -0 "$(cat .runtime/frontend.pid)" 2>/dev/null; then
  (cd frontend && exec npm run dev) > .runtime/frontend.log 2>&1 &
  echo $! > .runtime/frontend.pid
fi
printf '帧序启动中： http://localhost:3000\n日志：.runtime/backend.log 与 .runtime/frontend.log\n'
wait
