---
name: pr-message
description: 현재 브랜치의 커밋들을 분석해 PR 제목, 본문(Summary/Test plan), 붙일 라벨 초안을 작성한다. 사용자가 "PR 메시지 작성해줘", "PR 초안 만들어줘"라고 요청할 때 사용한다.
allowed-tools: Bash, Read
---

# PR Message 작성 지침

## 언제 사용하는가
- 사용자가 PR 제목/본문 작성을 요청했을 때
- develop 브랜치 대비 현재 브랜치에 커밋이 있을 때

## 무엇을 하는가
1. `git log origin/develop..<current>`와 `git diff origin/develop...<current> --stat`로 변경 커밋과 파일 목록을 확인한다.
2. 변경의 목적과 범위를 파악해 `type(scope): 한글 설명` 형식(Conventional Commits, commit-message 스킬과 동일 규칙)으로 짧은 제목을 작성한다.
3. 본문은 Summary(불릿 목록)와 Test plan(체크리스트)로 구성한다.
4. 붙일 라벨을 정한다. 먼저 `gh label list`로 저장소에 실제로 있는 라벨을 확인하고, 그 안에서만 고른다(없는 라벨을 새로 만들지 않는다).
   - 제목의 type 기준: `feat` → `enhancement`, `fix` → `bug`, `docs` → `documentation`.
   - `구현노트`는 PR이 구현 과정이나 기술적 의사결정을 기록하는 문서(런북, 결정 기록 등)를 담고 있을 때, 위 라벨과 **함께** 붙인다(라벨 설명: "feature·fix 등 다른 라벨과 함께 사용").
   - `refactor`/`perf`/`test`/`chore`/`ci` 처럼 맞는 라벨이 없으면 억지로 붙이지 말고, 라벨을 붙이지 않는다고 초안에 밝힌다.
5. 초안(제목, 본문, 라벨)을 제시하고, 사용자가 원하면 `gh pr create --base develop --label <라벨>`로 PR을 생성한다. 라벨이 여러 개면 `--label`을 반복한다. 이미 만든 PR에는 `gh pr edit <번호> --add-label <라벨>`을 쓴다.

## 주의사항
- PR 제목과 본문은 항상 한글로 작성한다 (이 레포의 기존 커밋/PR 컨벤션).
- PR의 base(도착점) 브랜치는 항상 `develop`으로 고정한다. main으로의 PR은 이 스킬로 만들지 않는다 — 사용자가 main을 명시적으로 요청해도 확인 없이 develop 대신 열지 말고 되물어본다.
- PR 생성은 사용자가 명시적으로 요청했을 때만 진행한다.
- 라벨은 PR 생성과 함께 붙이되, 어떤 라벨을 왜 골랐는지 초안에 한 줄로 밝힌다. 판단이 애매하면 붙이지 말고 사용자에게 되물어본다.
- 커밋 메시지만으로 목적이 불분명하면 diff 내용을 직접 확인해 보완한다.