---
name: pr-message
description: 현재 브랜치의 커밋들을 분석해 PR 제목과 본문(Summary/Test plan) 초안을 작성한다. 사용자가 "PR 메시지 작성해줘", "PR 초안 만들어줘"라고 요청할 때 사용한다.
allowed-tools: Bash, Read
---

# PR Message 작성 지침

## 언제 사용하는가
- 사용자가 PR 제목/본문 작성을 요청했을 때
- base 브랜치(main 또는 develop) 대비 현재 브랜치에 커밋이 있을 때

## 무엇을 하는가
1. `git log <base>..<current>`와 `git diff <base>...<current> --stat`로 변경 커밋과 파일 목록을 확인한다.
2. 변경의 목적과 범위를 파악해 짧은 제목을 작성한다.
3. 본문은 Summary(불릿 목록)와 Test plan(체크리스트)로 구성한다.
4. base 브랜치를 사용자에게 확인한다 (기본값: develop).
5. 초안을 제시하고, 사용자가 원하면 `gh pr create`로 PR을 생성한다.

## 주의사항
- PR 생성은 사용자가 명시적으로 요청했을 때만 진행한다.
- 커밋 메시지만으로 목적이 불분명하면 diff 내용을 직접 확인해 보완한다.
