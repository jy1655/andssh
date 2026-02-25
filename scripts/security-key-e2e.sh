#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="$ROOT_DIR/.tmp/security-key-e2e"
PORT="${ANDSSH_E2E_PORT:-10022}"
SSHD_BIN="${SSHD_BIN:-/usr/sbin/sshd}"
SSH_BIN="${SSH_BIN:-ssh}"

HOST_KEY="$WORK_DIR/host_ed25519"
AUTH_KEYS_FILE="$WORK_DIR/authorized_keys"
CONFIG_FILE="$WORK_DIR/sshd_config"
PID_FILE="$WORK_DIR/sshd.pid"
LOG_FILE="$WORK_DIR/sshd.log"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing command: $1"
    exit 1
  fi
}

ensure_support() {
  require_cmd "$SSH_BIN"
  require_cmd ssh-keygen
  if [[ ! -x "$SSHD_BIN" ]]; then
    echo "sshd not found or not executable: $SSHD_BIN"
    exit 1
  fi
  if ! "$SSH_BIN" -Q key | grep -q "sk-ecdsa-sha2-nistp256@openssh.com"; then
    echo "OpenSSH client does not report sk-ecdsa support."
    exit 1
  fi
}

ensure_files() {
  mkdir -p "$WORK_DIR"
  touch "$AUTH_KEYS_FILE"
  chmod 600 "$AUTH_KEYS_FILE"
  if [[ ! -f "$HOST_KEY" ]]; then
    ssh-keygen -q -t ed25519 -N "" -f "$HOST_KEY"
  fi
}

generate_config() {
  local user
  user="$(id -un)"
  cat >"$CONFIG_FILE" <<EOF
Port $PORT
ListenAddress 0.0.0.0
HostKey $HOST_KEY
PidFile $PID_FILE
AuthorizedKeysFile $AUTH_KEYS_FILE
PasswordAuthentication no
KbdInteractiveAuthentication no
ChallengeResponseAuthentication no
PubkeyAuthentication yes
AuthenticationMethods publickey
PubkeyAcceptedAlgorithms sk-ecdsa-sha2-nistp256@openssh.com
PermitRootLogin no
AllowUsers $user
UsePAM no
StrictModes no
LogLevel VERBOSE
Subsystem sftp internal-sftp
EOF
}

is_running() {
  if [[ ! -f "$PID_FILE" ]]; then
    return 1
  fi
  local pid
  pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ -z "$pid" ]]; then
    return 1
  fi
  kill -0 "$pid" 2>/dev/null
}

print_access_info() {
  echo "Server work dir : $WORK_DIR"
  echo "authorized_keys : $AUTH_KEYS_FILE"
  echo "server log      : $LOG_FILE"
  echo "listen port     : $PORT"
  echo "username        : $(id -un)"

  for nic in en0 en1 en2; do
    if command -v ipconfig >/dev/null 2>&1; then
      ip="$(ipconfig getifaddr "$nic" 2>/dev/null || true)"
      if [[ -n "${ip:-}" ]]; then
        echo "candidate host  : $ip ($nic)"
      fi
    fi
  done
}

cmd_start() {
  ensure_support
  ensure_files
  generate_config

  if is_running; then
    echo "sshd already running (pid $(cat "$PID_FILE"))."
    print_access_info
    return
  fi

  "$SSHD_BIN" -f "$CONFIG_FILE" -E "$LOG_FILE"
  sleep 1
  if ! is_running; then
    echo "Failed to start sshd. See $LOG_FILE"
    exit 1
  fi

  echo "sshd started (pid $(cat "$PID_FILE"))."
  print_access_info
}

cmd_stop() {
  if ! is_running; then
    echo "sshd is not running."
    rm -f "$PID_FILE"
    return
  fi
  local pid
  pid="$(cat "$PID_FILE")"
  kill "$pid"
  rm -f "$PID_FILE"
  echo "sshd stopped (pid $pid)."
}

cmd_status() {
  if is_running; then
    echo "sshd running (pid $(cat "$PID_FILE"))."
  else
    echo "sshd not running."
  fi
  print_access_info
}

cmd_reset_keys() {
  ensure_files
  : >"$AUTH_KEYS_FILE"
  chmod 600 "$AUTH_KEYS_FILE"
  echo "Cleared $AUTH_KEYS_FILE"
}

cmd_show_log() {
  ensure_files
  if [[ ! -f "$LOG_FILE" ]]; then
    echo "No log file yet: $LOG_FILE"
    return
  fi
  tail -n 120 "$LOG_FILE"
}

cmd_help() {
  cat <<EOF
Usage: scripts/security-key-e2e.sh <command>

Commands:
  start       Start local OpenSSH server for sk-ecdsa hardware-key E2E
  stop        Stop local OpenSSH server
  status      Show server status and connection info
  reset-keys  Clear authorized_keys used by E2E server
  show-log    Show recent sshd log lines
  help        Show this help

Environment:
  ANDSSH_E2E_PORT   SSH port (default: 10022)
  SSHD_BIN          sshd binary (default: /usr/sbin/sshd)
  SSH_BIN           ssh binary (default: ssh)
EOF
}

main() {
  case "${1:-help}" in
    start) cmd_start ;;
    stop) cmd_stop ;;
    status) cmd_status ;;
    reset-keys) cmd_reset_keys ;;
    show-log) cmd_show_log ;;
    help|--help|-h) cmd_help ;;
    *)
      echo "Unknown command: $1"
      cmd_help
      exit 1
      ;;
  esac
}

main "${1:-help}"
