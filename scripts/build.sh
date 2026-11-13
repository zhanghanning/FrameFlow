#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn -f backend/pom.xml verify
npm --prefix frontend ci
npm --prefix frontend run build
