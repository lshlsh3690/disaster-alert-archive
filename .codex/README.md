# Claude Code와 Codex의 공용 스킬

스킬의 유일한 원본은 `.agents/skills`다. 두 도구가 같은 파일을 읽고, 에이전트 정의는
각 도구의 형식을 유지한다. 프로젝트 공통 규칙은 `CLAUDE.md`, Codex 적용 사항은 `AGENTS.md`를 본다.

| 종류 | Claude Code | Codex |
|---|---|---|
| 스킬 12개 원본 | `.agents/skills/` | `.agents/skills/` |
| 스킬 탐색 | `.claude/skills` → 공용 원본 | 공용 원본 직접 탐색 |
| 에이전트 6개 | `.claude/agents/*.md` | `.codex/agents/*.toml` |
| 호출 방식 | `/speckit-plan` | `$speckit-plan` |

## 새 clone 또는 worktree에서 연결

저장소 루트에서 한 번 실행한다. Python 3.11 이상을 사용한다.

```powershell
python .agents/scripts/setup-shared-skills.py
python .agents/scripts/setup-shared-skills.py --check
```

Windows는 관리자 권한이 필요 없는 디렉터리 junction을, 다른 OS는 상대 경로 심볼릭 링크를
만든다. `.claude/skills` 연결은 로컬 파일시스템에만 생성하며 Git에서 제외한다.
Git에는 `.agents/skills`의 실제 내용만 저장하므로 `core.symlinks=false`인 Windows에서도
체크아웃 후 위 명령으로 연결할 수 있다. 기존 `.claude/skills`가 다른 디렉터리라면
자동으로 삭제하거나 덮어쓰지 않고 오류를 보고한다.

연결을 만든 뒤 스킬 목록이 갱신되지 않으면 도구의 새 세션을 시작한다.

## 스킬 수정

`.agents/skills/<name>/SKILL.md`만 직접 수정한다. Claude 연결 경로로 읽어도 같은 파일이며
스킬 생성·복사·동기화는 필요 없다. 공용 절차에는 도구별 모델·도구명·호출 접두사·작성자 표기를
고정하지 않고, Claude의 차이는 `CLAUDE.md`, Codex의 차이는 `AGENTS.md`에 둔다.

스킬은 `commit-message`, `pr-message`, `speckit-analyze`, `speckit-checklist`,
`speckit-clarify`, `speckit-constitution`, `speckit-converge`, `speckit-implement`,
`speckit-plan`, `speckit-specify`, `speckit-tasks`, `speckit-taskstoissues`를 제공한다.

공유 Spec Kit 스크립트는 다음과 같이 실행한다. Windows에서는 Git Bash로 호출한다.

```bash
bash .agents/scripts/run-speckit.sh check-prerequisites --json --paths-only
```

래퍼는 Windows의 실행 불가능한 `python3` 별칭을 피한다. `.specify`의 기존 템플릿·헌법·설치
메타데이터는 공유하며, 호환성 검증에서는 기능 선택 상태를 덮어쓰지 않는다.

## 도구별 에이전트

`code-reviewer`, `coderabbit-responder`, `docs-drift`, `tdd-developer`, `ui-checker`,
`ui-fixer`의 Claude 정의는 `.claude/agents/*.md`, Codex 정의는 `.codex/agents/*.toml`에 둔다.
Codex는 부모 세션의 모델·권한·MCP를 상속한다. 읽기 전용 역할의 제약은 지침이며,
Claude의 도구 허용 목록과 같은 기술적 권한 격리를 보장하지는 않는다.

Claude 에이전트 원본을 고친 뒤 Codex 정의도 갱신하려면 실행한다(Python 3.11 이상·PyYAML 필요).

```powershell
python .codex/scripts/sync-claude-agents.py
python .codex/scripts/sync-claude-agents.py --check
```

이 스크립트는 에이전트 6개만 생성하고 공유 스킬을 읽거나 수정하지 않는다.
공유 스킬을 덮어쓰던 `sync-claude-assets.py`는 제거했다.

Claude의 로컬 권한 목록과 Stop/PostToolUse 훅은 Codex에 자동 적용하지 않는다.
RED 사용자 확인과 GREEN 후 조건부 리뷰 절차는 각 도구의 지침에 유지한다.

형식과 탐색 경로는 [Codex 스킬](https://learn.chatgpt.com/docs/build-skills),
[Codex 서브에이전트](https://learn.chatgpt.com/docs/agent-configuration/subagents),
[Claude 스킬](https://code.claude.com/docs/en/skills#choose-where-skills-load) 문서를 참고한다.
