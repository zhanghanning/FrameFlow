#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
sudo mysql --table frameflow < database/performance.sql > docs/performance-result.txt
printf '性能实验结果：docs/performance-result.txt\n'
