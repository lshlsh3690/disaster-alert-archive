# Codex 설정

이 저장소는 Claude Code와 Codex를 함께 지원한다. 프로젝트 규칙은 루트 `CLAUDE.md`에
유지하며, `AGENTS.md`가 Codex 호출 방식과 플랫폼 차이를 보완한다.

| 종류 | Claude Code | Codex |
|---|---|---|
| 프로젝트 지침 | `CLAUDE.md` | `AGENTS.md` → `CLAUDE.md` |
| 스킬 12개 | `.claude/skills/` | `.agents/skills/` |
| 에이전트 6개 | `.claude/agents/*.md` | `.codex/agents/*.toml` |
| 에이전트 활성화 | Claude 설정 | `.codex/config.toml` |

Codex의 프로젝트 스킬 탐색 경로는 `.agents/skills`이므로 `.codex/skills`에 중복 배치하지
않는다. Windows에서 심볼릭 링크 권한이나 체크아웃 설정에 의존하지 않도록 실제 파일을 둔다.

## 사용

프로젝트를 신뢰하는 Codex 세션에서 설정을 읽는다. 기존 세션에 목록이 갱신되지 않으면
새 세션을 시작하거나 Codex를 다시 연다. 스킬은 `/skills` 또는 `$` 선택기에서 확인한다.

```text
$commit-message 스테이징한 변경의 커밋 메시지 초안을 작성해줘
$pr-message 현재 브랜치의 PR 초안을 작성해줘
$speckit-plan 이 기능의 구현 계획을 작성해줘
code-reviewer 에이전트로 현재 diff를 검토해줘
tdd-developer 에이전트로 단계: RED — 대상 로직과 기대 동작을 테스트해줘
```

스킬은 `commit-message`, `pr-message`, `speckit-analyze`, `speckit-checklist`,
`speckit-clarify`, `speckit-constitution`, `speckit-converge`, `speckit-implement`,
`speckit-plan`, `speckit-specify`, `speckit-tasks`, `speckit-taskstoissues`를 제공한다.

에이전트는 `code-reviewer`, `coderabbit-responder`, `docs-drift`, `tdd-developer`,
`ui-checker`, `ui-fixer`를 제공한다. Claude 모델명과 도구 허용 목록은 복사하지 않으며,
Codex의 모델·권한·MCP를 상속한다. 읽기 전용 역할의 금지사항은 역할 지침이며,
Claude의 도구 허용 목록과 동일한 기술적 권한 격리를 보장하지는 않는다.

Spec Kit의 Bash 스크립트는 `bash .codex/scripts/run-speckit.sh <이름> <인자>`로
실행한다. 이 래퍼는 Windows의 실행 불가능한 `python3` 별칭을 피하고 저장소 루트를
기준으로 실행한다. PowerShell에서는 Git Bash 실행 파일로 래퍼를 호출한다(`AGENTS.md` 참고).

## 원본 변경 후 동기화

Python 3.11 이상과 PyYAML이 필요하다(현재 환경에 설치되어 있음).
Claude 원본을 수정한 뒤 저장소 루트에서 실행한다. Codex용 스킬과 에이전트 파일을
다시 생성하므로 그 파일을 직접 수정하지 말고, 공통 내용은 원본에, Codex 변환은 아래
스크립트에 반영한다. `AGENTS.md`, `config.toml`, 이 문서는 덮어쓰지 않는다.

```powershell
python .codex/scripts/sync-claude-assets.py
python .codex/scripts/sync-claude-assets.py --check
```

`--check`는 원본과 변환 규칙을 기준으로 누락·차이를 확인하며 파일을 쓰지 않는다.
정식 `specify init`으로 Codex 통합을 재설치한 상태는 아니며, 기존 `.specify`의
Claude 설치 메타데이터와 feature 상태는 보존한다.

Claude의 로컬 권한 목록과 Stop/PostToolUse 훅은 이식하지 않는다. RED 후 사용자 확인,
GREEN 후 조건부 리뷰, UI 확인 절차는 Codex 지침과 에이전트에 유지한다.
스킬·에이전트를 설치하는 작업 자체가 커밋·푸시·PR 답글을 실행하지는 않는다.

형식과 탐색 경로는 OpenAI의 [스킬 문서](https://learn.chatgpt.com/docs/build-skills)와
[서브에이전트 문서](https://learn.chatgpt.com/docs/agent-configuration/subagents)를 기준으로 한다.
