---
name: pr-message
description: 현재 브랜치의 커밋들을 분석해 PR 제목과 본문(Summary/Test plan) 초안을 작성한다. 사용자가 "PR 메시지 작성해줘", "PR 초안 만들어줘"라고 요청할 때 사용한다.
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
4. 초안을 제시하고, 사용자가 원하면 `gh pr create --base develop`으로 PR을 생성한다.

## 주의사항
- PR 제목과 본문은 항상 한글로 작성한다 (이 레포의 기존 커밋/PR 컨벤션).
- PR의 base(도착점) 브랜치는 항상 `develop`으로 고정한다. main으로의 PR은 이 스킬로 만들지 않는다 — 사용자가 main을 명시적으로 요청해도 확인 없이 develop 대신 열지 말고 되물어본다.
- PR 생성은 사용자가 명시적으로 요청했을 때만 진행한다.
- 커밋 메시지만으로 목적이 불분명하면 diff 내용을 직접 확인해 보완한다.