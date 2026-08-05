#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 5 ]]; then
  echo "usage: $0 RUN_ID PROMPT_FILE MODEL REASONING OUTPUT_ROOT [PROFILE] [SANDBOX] [APPROVAL_POLICY] [TIMEOUT_SECONDS] [IGNORE_USER_CONFIG]" >&2
  exit 2
fi

RUN_ID="$1"
PROMPT_FILE="$2"
MODEL="$3"
REASONING="$4"
OUTPUT_ROOT="$5"
PROFILE="${6:-}"
SANDBOX="${7:-workspace-write}"
APPROVAL_POLICY="${8:-never}"
TIMEOUT_SECONDS="${9:-1800}"
IGNORE_USER_CONFIG="${10:-false}"
RUN_DIR="$OUTPUT_ROOT/$RUN_ID"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if [[ -z "$OUTPUT_ROOT" ]]; then
  echo "Output root must be a week-local experiments directory." >&2
  exit 2
fi

if [[ ! -f "$PROMPT_FILE" ]]; then
  echo "Prompt file not found: $PROMPT_FILE" >&2
  exit 2
fi
if [[ ! -s "$PROMPT_FILE" ]]; then
  echo "Prompt file is empty: $PROMPT_FILE" >&2
  exit 2
fi
if [[ -e "$RUN_DIR" ]]; then
  echo "Run directory already exists: $RUN_DIR" >&2
  exit 2
fi

mkdir -p "$RUN_DIR"
CAPTURE_ARGS=(
  "$SCRIPT_DIR/capture_environment.py"
  --output "$RUN_DIR/environment.json"
  --model "$MODEL"
  --reasoning "$REASONING"
  --profile "$PROFILE"
  --approval-policy "$APPROVAL_POLICY"
  --sandbox "$SANDBOX"
)
if [[ "$IGNORE_USER_CONFIG" == "true" ]]; then
  CAPTURE_ARGS+=(--ignore-user-config)
fi
python "${CAPTURE_ARGS[@]}"
cp "$PROMPT_FILE" "$RUN_DIR/prompt.md"
START_EPOCH=$(python -c 'import time; print(time.time())')
START_ISO=$(date -u +%Y-%m-%dT%H:%M:%SZ)

set +e
EXEC_ARGS=(
  "$SCRIPT_DIR/run_codex_exec.py"
  --prompt "$PROMPT_FILE"
  --events "$RUN_DIR/events.jsonl"
  --stderr "$RUN_DIR/stderr.log"
  --model "$MODEL"
  --reasoning "$REASONING"
  --sandbox "$SANDBOX"
  --approval-policy "$APPROVAL_POLICY"
  --timeout-seconds "$TIMEOUT_SECONDS"
)
if [[ -n "$PROFILE" ]]; then
  EXEC_ARGS+=(--profile "$PROFILE")
fi
if [[ "$IGNORE_USER_CONFIG" == "true" ]]; then
  EXEC_ARGS+=(--ignore-user-config)
fi
python "${EXEC_ARGS[@]}"
EXIT_CODE=$?
set -e

END_EPOCH=$(python -c 'import time; print(time.time())')
END_ISO=$(date -u +%Y-%m-%dT%H:%M:%SZ)
WALL_SECONDS=$(python -c "print(round(float('$END_EPOCH') - float('$START_EPOCH'), 3))")
METADATA_ARGS=(
  "$SCRIPT_DIR/write_run_metadata.py"
  --output "$RUN_DIR/run.json"
  --run-id "$RUN_ID"
  --started-at "$START_ISO"
  --finished-at "$END_ISO"
  --wall-seconds "$WALL_SECONDS"
  --exit-code "$EXIT_CODE"
  --sandbox "$SANDBOX"
  --model "$MODEL"
  --reasoning "$REASONING"
  --profile "$PROFILE"
  --approval-policy "$APPROVAL_POLICY"
  --timeout-seconds "$TIMEOUT_SECONDS"
)
if [[ "$IGNORE_USER_CONFIG" == "true" ]]; then
  METADATA_ARGS+=(--ignore-user-config)
fi
python "${METADATA_ARGS[@]}"
python "$SCRIPT_DIR/parse_codex_jsonl.py" \
  "$RUN_DIR/events.jsonl" \
  --metadata "$RUN_DIR/run.json" \
  --output "$RUN_DIR/summary.json"
echo "Run saved to $RUN_DIR"
exit "$EXIT_CODE"
