# Codex 저장소 가이드

작업 전에 루트의 [CLAUDE.md](CLAUDE.md)를 읽는다. 프로젝트 구조, 도메인 규칙,
빌드·테스트 명령, 한국어 커밋·PR 규칙은 그 문서를 공통 기준으로 사용한다.
Codex에서의 도구·호출 방식과 아래 충돌 정리는 이 파일을 따른다.

## 스킬과 에이전트

- 스킬: `.agents/skills/<name>/SKILL.md`. `$commit-message`, `$pr-message`,
  `$speckit-plan`처럼 호출한다. `/skills` 또는 `$` 선택기에서도 찾을 수 있다.
- 에이전트: `.codex/agents/<name>.toml`. 역할에 맞는 에이전트를 이름으로 지정한다.
  모델·추론 수준·권한·MCP는 부모 세션에서 상속한다.
- Claude 원본과 Codex 사본의 관리 방법은 [.codex/README.md](.codex/README.md)를 본다.
- `Read`/`Grep`/`Glob`은 사용 가능한 파일 읽기·검색 도구로, `Bash`는 셸 실행 도구로,
  `Edit`/`Write`는 파일 편집 도구로 해석한다. 파일 검색은 `rg`/`rg --files`를 우선한다.
  `Skill` 도구를 가정하지 않고 해당 `SKILL.md`를 읽고 실제 절차를 수행한다.
- 서브에이전트가 가능한 환경에서는 역할 정의를 사용한다. 불가능하면 해당 TOML의
  `developer_instructions`를 읽고 같은 범위를 직접 수행하며, 위임했다고 보고하지 않는다.

## TDD와 변경 범위

- 백엔드와 프론트엔드 모두 RED 작성·검증 뒤 멈춘다. 사용자가 RED를 확인했다고
  명시하기 전에는 GREEN을 시작하지 않는다. `CLAUDE.md` 끝부분의 프론트엔드에서
  한 번에 RED→GREEN을 진행한다는 문장은 앞선 공통 확인 규칙과 충돌하므로 적용하지 않는다.
- `tdd-developer`의 RED 자동 커밋은 그 역할을 실제로 호출한 RED 작업에만 적용한다.
  일반 작업·GREEN·설정 이식으로 승인을 확대하지 않는다. 커밋·푸시의 기존 승인 범위를 지킨다.
- GREEN 후 `code-reviewer` 위임은 로직·보안·마이그레이션 변경이 대략 100줄 이상일 때
  메인 에이전트가 수행한다. 문서·설정 변경과 작은 수정은 메인 에이전트가 직접 확인한다.
- 기존 미커밋 변경은 보존한다. 검증 목적으로 전체 워크트리를 stash하거나 정리하지 않는다.
- Claude의 공동 작성자 트레일러를 Codex 결과에 붙이지 않는다. 공동 작성자 정보는
  사용자가 지정했거나 실제 세션에서 설정한 경우에만 사용한다.

## Windows 실행

- PowerShell에서는 `./gradlew` 대신 `backend`에서 `.\gradlew.bat`를 사용할 수 있다.
  실행 정책으로 npm의 `.ps1` 래퍼가 막히면 `npm.cmd`, `npx.cmd`, `codex.cmd`를 사용한다.
- `.specify/scripts/bash/*.sh`와 Bash 문법 예시는 Bash로 실행한다. Windows의
  `bash.exe`가 WSL 런처일 수 있으므로 Git Bash 설치 경로를 먼저 확인한다. 예:

  ```powershell
  & 'C:\Program Files\Git\bin\bash.exe' .codex/scripts/run-speckit.sh check-prerequisites --json --paths-only
  ```

- Bash 예시의 `/tmp`, `VAR=value command`, `$(...)`, heredoc, `trap`, `&`를
  PowerShell에 그대로 붙여 넣지 않는다. Git Bash에서 실행하거나 PowerShell 문법으로 바꾼다.
  PowerShell의 파일 읽기·쓰기는 UTF-8을 명시하고, 커밋 메시지는 UTF-8 임시 파일과
  `git commit -F <파일>`로 전달한다. UI 스크린샷은 `view_image` 등 이미지 보기 도구로 확인한다.
- UI 검증은 실행별 `frontend/.ui-check/<run-id>/`를 사용한다. 직접 시작한 서버만 정리하며
  이미 떠 있던 서버나 다른 실행의 산출물을 건드리지 않는다.

## Spec Kit

- Codex에서는 `bash .codex/scripts/run-speckit.sh <스크립트 이름> <인자>`로 공통
  스크립트를 실행한다. 래퍼는 Windows `python3` 별칭이 실행 불가능하면 동작하는
  `python`을 해당 프로세스에서만 사용한다. Python 설치나 전역 PATH를 바꾸지 않는다.
- `.specify/`의 기존 템플릿·스크립트·헌법·feature 상태를 공유한다. Claude 통합 설정을
  Codex 사용을 위해 덮어쓰지 않는다. 사용자 요청에 다른 feature가 명시되면 그것을 우선한다.
- `feature.json`이 가리키는 폴더가 현재 체크아웃에 없으면 작업 트리와 사용자 요청의
  대상부터 확인한다. 조회·호환성 검증에는 `check-prerequisites --json --paths-only`를
  사용한다. 다른 옵션의 공통 스크립트는 feature 상태를 저장할 수 있으므로, 검증 목적으로
  현재 기능을 바꾸지 않는다.
- 스킬 입력은 현재 호출에 첨부한 사용자 요청이다. `$ARGUMENTS` 같은 템플릿 변수가
  자동으로 치환된다고 가정하거나 환경변수로 평가하지 않는다.
- `.specify/extensions.yml`에 실제로 등록된 훅만 처리한다. 명령 이름의 점을 하이픈으로
  바꿔 스킬을 찾고 해당 지침을 실행한다. `EXECUTE_COMMAND:` 출력만으로 실행됐다고 보지 않는다.
  필수 훅에 필요한 스킬·도구가 없으면 구체적인 누락을 보고하고 해당 절차를 멈춘다.
- GitHub는 연결된 도구를 먼저 확인하고, 기능이 부족하면 인증된 `gh`를 사용한다.
  이슈·PR·답글은 해당 작업을 요청받은 범위에서만 생성한다.
- `.claude/settings*.json`의 권한 및 Stop/PostToolUse 훅은 Codex에 자동 적용되지 않는다.
  이 이식은 세션 종료 자동 커밋이나 자동 푸시를 추가하지 않는다.
