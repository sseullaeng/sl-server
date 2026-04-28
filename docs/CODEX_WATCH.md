# Codex 리뷰 실시간 모니터

Codex MCP 호출 입출력을 IntelliJ 별도 터미널 탭에서 실시간으로 흘려보기 위한 셋업.

> **왜 별도 탭인가?** Claude Code 패널은 화면이 좁아 Codex 풀 응답이 쏟아지면 가독성이 떨어진다.
> 호출은 Claude Code에서, 응답 모니터링은 별도 탭에서 — 역할 분리.

---

## 사용 방법

### IntelliJ에서 터미널 추가 띄우기

1. `Alt+F12` 로 기본 터미널 열기 (평소 작업용)
2. 터미널 좌측의 `+` 버튼 클릭 → 새 탭 생성
3. 새 탭 우클릭 → `Rename Tab` → `codex-watch` 로 변경
4. 그 탭에서 다음 명령 실행:
   - **Mac / Linux / WSL**: `./scripts/codex-watch.sh`
   - **Windows (PowerShell)**: `powershell -File ./scripts/codex-watch.ps1`
5. 평소엔 기본 탭에서 작업, Codex 리뷰 호출하면 `codex-watch` 탭으로 잠깐 전환해서 확인

### 컬러링 규칙

| 색  | 트리거 키워드                                 | 의미       |
|-----|-----------------------------------------------|------------|
| 🔴  | `Critical`, `🔴`, `error`, `ERROR`, `Error`   | 즉시 대응  |
| 🟡  | `Warning`, `🟡`, `warn`, `WARN`               | 주의 환기  |
| 🔵  | `Suggestion`, `🟢`, `hint`, `HINT`            | 개선 제안  |
| 🟢  | `✅`, `good`, `passed`, `PASS`                | 통과 신호  |

### 게이트 시스템 연동

- **🔴 즉시 리뷰** 호출 후 → `codex-watch` 탭에서 풀 응답 확인 → Critical 항목 우선 대응
- **🟡 PR 리뷰** 는 응답 길이가 큼 → 반드시 `codex-watch` 탭에서 전체 흐름 확인
- **🟢 디버깅 막힘** → 응답 보고 단서만 잡고 본인이 다시 디버깅 (Codex 의존 X)

### 로그 비우기 (가끔 필요)

```bash
# 작업 중 로그가 너무 쌓였을 때
> .logs/codex-review.log
```

> ⚠️ **비밀키나 민감 정보가 섞인 코드**(JWT secret, OAuth client secret, 토스 secret key, DB 비밀번호 등)를
> Codex에 보여준 직후엔 **반드시 로그를 비울 것**. `.logs/` 는 .gitignore 처리되어 있지만 로컬 디스크엔 남는다.

---

## 트러블슈팅

### 로그가 안 찍힘

- `~/.claude/config.json` 의 `mcpServers.codex` 설정 확인
- `tee` 또는 `Tee-Object` 파이프가 제대로 적용됐는지 확인
- **Claude Code 재시작 필수** (MCP 서버 설정은 시작 시점 로드)

### `jq` 미설치 (pretty mode 안 뜸)

pretty mode 는 `jq` 가 필요합니다. 미설치 시 자동으로 raw mode 로 폴백되며 경고가 뜹니다.

```bash
brew install jq        # macOS
sudo apt install jq    # Debian / Ubuntu / WSL
```

의도적으로 raw 원본을 보고 싶으면: `./scripts/codex-watch.sh --raw` 또는 `CODEX_WATCH_RAW=1`.

### `.sh` 권한 에러

```bash
chmod +x scripts/codex-watch.sh
```

### Windows 에서 스크립트 실행 차단

PowerShell 관리자 모드에서:

```powershell
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### `tail: cannot open '.logs/codex-review.log'`

스크립트가 자동 생성하지만 권한 문제로 실패하면:

```bash
mkdir -p .logs && touch .logs/codex-review.log
```

---

## 관련 문서

- `.claude/CLAUDE.md` §9 — Codex 협업 규칙 (게이트 1/2/3)
- `.claude/AGENTS.md` §6 — 리뷰 강도 (Codex 측 컨벤션)
- `docs/SSEULANG_BACKEND_GUIDE.md` §8 — Day별 게이트 매핑
