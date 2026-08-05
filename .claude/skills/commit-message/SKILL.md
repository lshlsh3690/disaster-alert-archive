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
1. `git status`/`git diff`로 스테이징 여부와 관계없이 변경 전체를 먼저 파악한다.
2. **관련 없는 변경이 섞여 있으면 커밋을 분리한다.** 예: 보안 수정, 소유권 검증 추가, 예외 처리 통일, API 응답 포맷 통일, 죽은 코드 제거가 한 세션에서 함께 나왔더라도 원인이 다르면 별도 커밋으로 — 리뷰어가 "왜 이 변경들이 한 커밋에 있는지" 되묻지 않게. 하나의 논리적 이유로 묶이는 파일들만 같이 스테이징한다.
3. 변경의 목적(버그 수정, 기능 추가, 리팩터링 등)을 파악한다.
4. `type(scope): 한글 설명` 형식(Conventional Commits)으로 제목을 작성한다.
   - type: `feat`(기능 추가), `fix`(버그 수정), `chore`(잡무·설정), `docs`(문서), `refactor`, `test`, `ci` 등
   - scope: 변경된 도메인/모듈명(예: `event`, `notification`, `config`), 애매하면 생략 가능
   - 제목은 "왜" 변경했는지를 중심으로 1문장
5. **버그 수정이거나 원인이 바로 안 드러나는 변경이면 본문(body)을 추가한다.** 무엇을 고쳤는지가 아니라, 어떤 문제가 있었는지(근본 원인)와 왜 이 방식으로 고쳤는지를 설명. 한 줄 제목만으로 충분한 사소한 변경(오타, 포맷팅 등)은 본문 생략 가능.
6. 본문 마지막에 빈 줄을 하나 두고 다음 줄을 추가한다:
   ```
   Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
   ```
7. 사용자에게 초안을 제시하고, 확정되면 heredoc으로 `git commit -m "$(cat <<'EOF' ... EOF)"` 형태로 커밋한다 (여러 줄 메시지가 셸에서 깨지지 않도록).

## 예시 (이 레포에서 실제로 쓴 형태)

```
fix(security): /api/v1/admin/** 인증 없이 열려있던 문제 수정

SecurityConfig에서 /api/v1/admin/**을 permitAll로 열어두고 있어
AdminController의 수동 알림 재발송 트리거를 누구나 인증 없이 호출할
수 있었다. permitAll을 제거하고 컨트롤러에 @PreAuthorize("hasRole('ADMIN')")를
추가해 ADMIN 권한 회원만 호출 가능하도록 제한했다.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

## 주의사항
- 커밋 메시지는 항상 한글로 작성한다 (이 레포의 기존 커밋 컨벤션).
- 커밋은 사용자가 명시적으로 요청했을 때만 생성한다.
- `.env`, `credentials.json` 등 민감한 파일은 커밋 대상에서 제외한다.
- `git commit --amend`, `--no-verify`는 사용자가 명시적으로 요청하지 않는 한 쓰지 않는다 — pre-commit hook이 실패했다면 새 커밋으로 다시 만든다.