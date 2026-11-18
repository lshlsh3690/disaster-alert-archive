# 검증 가이드: 불일치 쌍 사람 라벨링

## 준비

- 로컬: `docker compose -f docker-compose.dev.yml up postgres redis`, 백엔드 `./gradlew bootRun`(V140 적용), 프론트 `npm run dev`.
- ADMIN 회원 하나와 일반 회원 하나.
- 불일치 쌍: 운영에는 2026-11-18 현재 0건이다. 로컬에서는 통합 테스트처럼 `clustering_daily_score`·`clustering_daily_score_pair` 에 과병합·과소병합 쌍을 몇 개 넣는다(운영 DB 는 읽기만).

## 시나리오

1. **권한**
   - 비로그인으로 `PUT /api/v1/admin/clustering/disagreement-labels` → 401, 일반 회원 → 403. 둘 다 `clustering_pair_label` 에 행이 생기지 않는다.
2. **라벨 저장·수정**(spec 스토리 1)
   - `/admin/clustering` 불일치 쌍 표에서 "다른 사건"을 누른다 → 버튼이 강조되고, 새로고침해도 그대로.
   - 같은 쌍에 "같은 사건"을 누른다 → 라벨이 바뀌고 `updated_at` 만 바뀐다(`created_at` 그대로).
   - "라벨 없는 쌍만"을 켜면 방금 라벨링한 쌍이 빠진다.
   - 불일치로 기록되지 않은 쌍을 API 로 저장하면 404.
3. **누가 틀렸는지·판사 성적**(spec 스토리 2)
   - 과소병합 쌍에 SAME → "시스템이 틀림", 과병합 쌍에 SAME → "판사가 틀림", UNSURE → 표시 없음.
   - spec 스토리 2 의 예시(같은 사건 3·다른 사건 1·모르겠음 1)로 라벨링하면 판사 2/4, 시스템 2/4, 모르겠음 1.
   - 라벨링 전후로 판사 버전 이력의 272쌍 일치율과 일일 채점 점수가 같다(SC-003).
   - 라벨이 하나도 없으면 "아직 라벨 없음".
4. **내보내기**(spec 스토리 3)
   - "라벨 파일 내보내기" → `disagreement-labels-YYYY-MM-DD.json` 저장. 쌍 수·라벨이 화면과 같다.
   - 그 파일을 기존 평가 도구에 넣어 `LabelJson` 형식 오류 없이 읽히는지 확인한다(평가 도구 실행 방법은 `backend/evaluation/README.md`).
   - 라벨이 없으면 파일이 만들어지지 않고 안내만 보인다.
5. **하지 않는 것 확인**
   - 라벨을 저장해도 `disaster_events`·`disaster_alert.event_id` 가 바뀌지 않는다(SC-006).
   - 라벨링·집계·내보내기 동안 OpenAI 호출 로그가 없다.

## 테스트 명령

```bash
cd backend
./gradlew test --tests "*DisagreementGradingTest"          # 순수 판정·집계
./gradlew test --tests "*ClusteringAdminLabelingTest"      # 저장·권한·성적·내보내기(로컬 Postgres 필요)
cd ../frontend && npm test && npm run lint && npm run build
```
