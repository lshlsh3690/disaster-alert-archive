# Claude Code와 Codex의 공용 스킬

스킬의 유일한 원본은 `.agents/skills`다. 두 도구는 이 원본을 읽고, 에이전트 정의는
각 도구의 형식을 유지한다. 프로젝트 공통 규칙은 `CLAUDE.md`, Codex 적용 사항은 `AGENTS.md`를 본다.

| 종류 | Claude Code | Codex |
|---|---|---|
| 스킬 12개 원본 | `.agents/skills/` | `.agents/skills/` |
| 스킬 사용 | `CLAUDE.md` 지침으로 공용 파일 직접 읽기 | 공용 원본 자동 탐색 |
| 에이전트 6개 | `.claude/agents/*.md` | `.codex/agents/*.toml` |
| 요청 예시 | `speckit-plan 스킬로 구현 계획을 작성해줘` | `$speckit-plan` |

## 스킬 사용

새 clone이나 worktree에서 스킬 연결을 위한 초기화는 필요 없다. `.claude/skills` 폴더와
디렉터리 링크를 생성하지 않으며, 연결을 만들던 설정 스크립트도 제거했다.

Codex는 `.agents/skills`를 자동 탐색한다. Claude는 `CLAUDE.md`의 안내에 따라 요청받은
스킬의 `SKILL.md`를 직접 읽고 수행한다. Claude의 기본 스킬 탐색 경로에 설치하는 구조가
아니므로 `/스킬이름` 메뉴 자동 등록을 보장하지 않는다. Claude에서는 스킬 이름이나
`.agents/skills/<name>/SKILL.md` 경로를 지정해 요청한다.

## 스킬 수정

`.agents/skills/<name>/SKILL.md`만 직접 수정한다. 스킬 생성·복사·동기화는 필요 없다.
공용 절차에는 도구별 모델·도구명·호출 접두사·작성자 표기를
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
`.specify/integrations`의 매니페스트는 기존 설치 이력을 기록한다. 현재 스킬을 실행할 때
읽는 원본 경로는 매니페스트의 과거 설치 경로가 아닌 `.agents/skills`다.

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
