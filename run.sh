#!/usr/bin/env bash

export RATE=20
export DURATION_SECONDS=60
export SCHEME=https
export HOST=localhost
export PORT=8889
./gradlew manyGets --rerun-tasks
