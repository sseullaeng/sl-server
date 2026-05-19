#!/usr/bin/env bash
# Codex 리뷰 실시간 모니터 (Mac / Linux / WSL)
# 사용     : ./scripts/codex-watch.sh
# raw 모드 : ./scripts/codex-watch.sh --raw   (또는 CODEX_WATCH_RAW=1)
# 종료     : Ctrl+C

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_FILE="${PROJECT_ROOT}/.logs/codex-review.log"

# ---- ANSI 컬러 ----
RED=$'\033[31m'; YELLOW=$'\033[33m'; CYAN=$'\033[36m'
GREEN=$'\033[32m'; GREY=$'\033[90m'; BOLD=$'\033[1m'; RESET=$'\033[0m'

# ---- 모드 판정 ----
RAW_MODE="${CODEX_WATCH_RAW:-0}"
[[ "${1:-}" == "--raw" ]] && RAW_MODE=1

if [[ "${RAW_MODE}" -eq 0 ]] && ! command -v jq >/dev/null 2>&1; then
  echo "${YELLOW}⚠️  jq 미설치 — raw mode로 fallback. 설치 권장: brew install jq (Mac) / apt install jq (Linux)${RESET}"
  RAW_MODE=1
fi

# ---- 로그 파일 보장 ----
[[ -f "${LOG_FILE}" ]] || { mkdir -p "$(dirname "${LOG_FILE}")"; : > "${LOG_FILE}"; }

# ---- 헤더 ----
SEP="════════════════════════════════════════════════════════════════"
SUB="────────────────────────────────────────────────────────────────"
echo "${BOLD}${SEP}${RESET}"
echo "${BOLD} Codex 리뷰 실시간 모니터${RESET}  ${GREY}[$( [[ ${RAW_MODE} -eq 1 ]] && echo raw || echo pretty )]${RESET}"
echo " 시작 시각 : $(date '+%Y-%m-%d %H:%M:%S')"
echo " 로그 경로 : ${LOG_FILE}"
echo " 종료      : Ctrl+C"
echo "${BOLD}${SEP}${RESET}"

# ---- 본문 라인별 4색 컬러링 ----
colorize_body() {
  while IFS= read -r line; do
    case "${line}" in
      *Critical*|*CRITICAL*|*"🔴"*|*error*|*ERROR*|*Error*)
        printf '  %s%s%s\n' "${RED}"    "${line}" "${RESET}" ;;
      *Warning*|*WARNING*|*"🟡"*|*warn*|*WARN*)
        printf '  %s%s%s\n' "${YELLOW}" "${line}" "${RESET}" ;;
      *Suggestion*|*SUGGEST*|*"🟢"*|*hint*|*HINT*)
        printf '  %s%s%s\n' "${CYAN}"   "${line}" "${RESET}" ;;
      *"✅"*|*good*|*GOOD*|*passed*|*PASS*)
        printf '  %s%s%s\n' "${GREEN}"  "${line}" "${RESET}" ;;
      *)
        printf '  %s\n' "${line}" ;;
    esac
  done
}

# ---- raw fallback: 기존 동작 ----
if [[ "${RAW_MODE}" -eq 1 ]]; then
  tail -n 0 -F "${LOG_FILE}" | while IFS= read -r line; do
    case "${line}" in
      *Critical*|*"🔴"*|*error*|*ERROR*|*Error*) printf '%s%s%s\n' "${RED}"    "${line}" "${RESET}" ;;
      *Warning*|*"🟡"*|*warn*|*WARN*)            printf '%s%s%s\n' "${YELLOW}" "${line}" "${RESET}" ;;
      *Suggestion*|*"🟢"*|*hint*|*HINT*)         printf '%s%s%s\n' "${CYAN}"   "${line}" "${RESET}" ;;
      *"✅"*|*good*|*passed*|*PASS*)             printf '%s%s%s\n' "${GREEN}"  "${line}" "${RESET}" ;;
      *)                                          printf '%s\n' "${line}" ;;
    esac
  done
  exit 0
fi

# ---- pretty: jq 필터 ----
JQ_FILTER='
  select(. != null) |
  if (.method == "tools/call" and .params.name == "codex") then
    "REQ\t" + ((.params.arguments.prompt // "") | gsub("\t";"    ") | gsub("\n";"\\n"))
  elif .method == "codex/event" then
    .params.msg as $m |
    if $m.type == "task_started" then "START\t" + ($m.turn_id // "?")
    elif $m.type == "task_complete" then
      "DONE\t" + ($m.duration_ms|tostring) + "\t" + ($m.time_to_first_token_ms|tostring) + "\t" +
        (($m.last_agent_message // "") | gsub("\t";"    ") | gsub("\n";"\\n"))
    else empty end
  else empty end
'

tail -n 0 -F "${LOG_FILE}" | jq -r --unbuffered "${JQ_FILTER}" 2>/dev/null | \
  while IFS=$'\t' read -r tag a b c; do
    case "${tag}" in
      REQ)
        echo
        echo "${CYAN}${BOLD}📤 REQUEST${RESET} ${GREY}($(date '+%H:%M:%S'))${RESET}"
        echo "${GREY}${SUB}${RESET}"
        printf '%b\n' "${a}" | sed 's/^/  /'
        ;;
      START)
        echo "${GREY}🤔 thinking... (turn ${a})${RESET}"
        ;;
      DONE)
        echo
        echo "${GREEN}${BOLD}📥 RESPONSE${RESET} ${GREY}(${a}ms, ttft ${b}ms)${RESET}"
        echo "${GREY}${SUB}${RESET}"
        printf '%b\n' "${c}" | colorize_body
        echo "${GREY}${SEP}${RESET}"
        echo
        ;;
    esac
  done
