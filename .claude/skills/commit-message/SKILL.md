---
name: commit-message
description: 스테이징된 변경 사항을 분석해 커밋 메시지 초안을 작성한다. 사용자가 "커밋 메시지 만들어줘", "커밋 정리해줘"라고 요청할 때 사용한다.
allowed-tools: Bash, Read
---

# Commit Message 작성 지침

## 언제 사용하는가
- 사용자가 커밋 메시지 작성을 요청했을 때
- git add로 스테이징된 변경 사항이 있을 때

## 무엇을 하는가
1. `git diff --staged`로 변경 내용을 확인한다.
2. 변경의 목적(버그 수정, 기능 추가, 리팩터링 등)을 파악한다.
3. `type(scope): 한글 설명` 형식(Conventional Commits)으로 제목을 작성한다.
   - type: `feat`(기능 추가), `fix`(버그 수정), `chore`(잡무·설정), `docs`(문서), `refactor`, `test`, `ci` 등
   - scope: 변경된 도메인/모듈명(예: `event`, `notification`, `config`), 애매하면 생략 가능
   - 설명은 "왜" 변경했는지를 중심으로 1문장
4. 사용자에게 초안을 제시하고, 확정되면 `git commit -m`으로 커밋한다.

## 주의사항
- 커밋 메시지는 항상 한글로 작성한다 (이 레포의 기존 커밋 컨벤션).
- 커밋은 사용자가 명시적으로 요청했을 때만 생성한다.
- `.env`, `credentials.json` 등 민감한 파일은 커밋 대상에서 제외한다.
- 커밋 메시지 끝에 `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` 같은 AI 서명을 붙이지 않는다.
