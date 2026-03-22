#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.run"
LOG_DIR="$RUN_DIR/logs"
BACKEND_PID_FILE="$RUN_DIR/backend.pid"
FRONTEND_PID_FILE="$RUN_DIR/frontend.pid"

mkdir -p "$LOG_DIR"

stop_if_running() {
  local pid_file="$1"
  if [[ -f "$pid_file" ]]; then
    local pid
    pid="$(cat "$pid_file")"
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
      wait "$pid" 2>/dev/null || true
    fi
    rm -f "$pid_file"
  fi
}

wait_http() {
  local url="$1"
  local name="$2"
  for _ in $(seq 1 60); do
    if curl -sf "$url" >/dev/null 2>&1; then
      echo "$name is ready: $url"
      return 0
    fi
    sleep 1
  done
  echo "$name did not become ready in time: $url" >&2
  return 1
}

stop_if_running "$BACKEND_PID_FILE"
stop_if_running "$FRONTEND_PID_FILE"

echo "Installing local Maven modules..."
"$ROOT_DIR/.tools/apache-maven-3.9.11/bin/mvn" -q -DskipTests install

echo "Starting backend..."
(
  cd "$ROOT_DIR/sync-admin-server"
  nohup "$ROOT_DIR/.tools/apache-maven-3.9.11/bin/mvn" -q spring-boot:run >"$LOG_DIR/backend.log" 2>&1 &
  echo $! > "$BACKEND_PID_FILE"
)

echo "Starting frontend..."
(
  cd "$ROOT_DIR/sync-ui"
  nohup npm run dev -- --host 0.0.0.0 >"$LOG_DIR/frontend.log" 2>&1 &
  echo $! > "$FRONTEND_PID_FILE"
)

wait_http "http://localhost:8080/actuator/health" "Backend"
wait_http "http://localhost:5173/" "Frontend"

echo "Frontend: http://localhost:5173"
echo "Backend:  http://localhost:8080"
echo "Logs:     $LOG_DIR"
