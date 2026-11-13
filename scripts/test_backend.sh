#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
set -a
source .env.test
set +a
cd backend
exec mvn -q spring-boot:run
