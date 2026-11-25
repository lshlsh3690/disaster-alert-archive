# 검증 가이드: 클러스터링 관리자 대시보드

## 준비

- 로컬: `docker compose -f docker-compose.dev.yml up postgres redis`, 백엔드 `./gradlew bootRun`, 프론트 `npm run dev`.
- ADMIN 권한 회원 하나와 일반 회원 하나.
- 일일 채점·구조 검사 기록: 로컬 DB 에는 없을 수 있다. 통합 테스트처럼 테스트 데이터로 넣거나, 운영 배포 후 운영에서 본다(운영 DB 는 읽기만).

## 시나리오

1. **권한**
   - 비로그인으로 `GET /api/v1/admin/clustering/daily-scores?from=2026-11-17&to=2026-11-17` → 401(날짜는 필수 — 빼면 권한과 무관하게 400).
   - 일반 회원으로 같은 요청 → 403. `/admin/clustering` 화면은 "관리자만 볼 수 있는 화면입니다." 안내 모달 뒤 홈으로 가고 API 요청이 나가지 않는다(비로그인은 "로그인이 필요한 화면입니다." 뒤 로그인 화면).
   - ADMIN 으로 → 200.
2. **일일 채점 표**(spec 스토리 1)
   - 균형 정확도 → 빠짐없이 묶은 비율 → 섞지 않은 비율 → 맞게 묶은 비율 → F1 순서인지(2026-11-24 013 변경).
   - 기본 범위가 오늘 제외 최근 30일인지.
   - 기록 있는 날의 숫자가 `clustering_daily_score` 행과 같은지(표본 날짜 대조, SC-007).
   - 모든 비율이 `a/b` 로 보이고, 같은 줄에 판사 버전과 일치율이 있는지(SC-002·SC-003).
   - 판정 0쌍인 날은 "판정 0쌍"·"-", 기록 없는 날은 "기록 없음"으로 서로 다르게 보이는지.
   - 한쪽 클래스 분모가 0이면 API에서 `balancedAccuracy` 키가 생략되고 화면은 "-"인지. Ratio 객체의 분자·분모와 `percent: null`은 유지되며 0점은 숨기지 않는지.
   - 과병합·과소병합이 있는 날은 "자세히 보기" 로 그날 불일치 쌍의 두 알림 원문과 당시 사건이 줄 아래에 펼쳐지는지(불일치 쌍 조회를 그날 하루로 부름).
   - 날짜별 집계가 있으면 쌍별 기록이 없어도 `correctlySeparated`·`separation`이 계산되는지. 기존 `score`는 F1인지. 검증은 [013 quickstart 3·4절](../013-balanced-accuracy-metric/quickstart.md)의 읽기 전용 절차를 따른다.
   - 범위를 367일로 요청하면 오류가 나는지.
3. **불일치 쌍**(스토리 2)
   - 날짜를 골라 쌍 수가 그날 과병합 + 과소병합 수와 같은지.
   - "과병합만" 필터, 페이지 넘김.
   - 원문을 찾을 수 없는 알림이 "원문 없음"으로 보이는지(테스트 데이터로 없는 id).
4. **구조 검사**(스토리 3): 미배정·임베딩 없음이 0 이 아닌 날이 강조되는지.
5. **판사 버전 이력**(스토리 4): v1(gpt-4o, 124/142·77/80, 2026-11-17 하루), v2(gpt-4o-mini, 127/141·74/81, 2026-11-18~) 가 맞게 보이는지. 기록 없는 버전이 있으면 "채점 기록 없음".
6. **비용 없음**(SC-005): 화면을 여러 번 열어도 백엔드 로그에 판사(LLM) 호출이 없는지.

## 운영 확인(배포 후, 읽기만)

- 관리자 계정으로 `/admin/clustering` 을 열어 어제 점수와 판사 일치율을 10초 안에 찾을 수 있는지(SC-001).
- 운영 DB 대조용(읽기 전용):
```sql
SELECT score_date, judge_version, pair_count, correctly_merged, over_merged, missed,
       agreement_same_num, agreement_same_den, agreement_diff_num, agreement_diff_den
FROM clustering_daily_score ORDER BY score_date DESC LIMIT 7;
```
