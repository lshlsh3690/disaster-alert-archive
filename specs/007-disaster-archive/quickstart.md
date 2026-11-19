# Quickstart: 사건 영구 링크·발신 기관 검증

2026-11-19 "기록관" 폐지 뒤 남은 범위(사건 영구 링크, 발신 기관)를 확인하는 절차. 계약은 [contracts/archive-api.md](contracts/archive-api.md), 규칙은 [data-model.md](data-model.md) 참고.

## 준비

```bash
docker compose -f docker-compose.dev.yml up postgres redis
cd backend && ./gradlew bootRun
cd frontend && npm run dev
```

로컬 DB 에 사건이 있어야 한다. 비어 있으면 운영 사본 덤프를 쓴다(백필로 사건을 만들면 LLM 호출 비용이 든다 — 비용 드는 실행은 사용자 승인 후). 사본에 처음 띄우면 V140·V141 이 적용되며 앱 시작이 약 3분 늦어진다(HNSW 인덱스, data-model.md 백필 항목).

## 1. 자동 테스트

```bash
cd backend
./gradlew test --tests "*ArchiveServiceIntegrationTest" --tests "*AgencyServiceIntegrationTest" --tests "*DisasterAlertSaveAgencyIntegrationTest" --tests "*AlertPlaceFactsTest"
```

통과해야 하는 핵심 동작(`@IntegrationTest`, 실제 Postgres):

1. 대표 알림은 사건에서 가장 먼저 발령된 알림이다(같은 시각이면 작은 sn).
2. sn → 현재 사건 해석: 묶인 알림은 사건 ID 를, 묶이지 않은 알림은 `bound=false` 를 돌려준다.
3. 발신 기관: 등록된 표기는 기존 기관, 처음 보는 표기는 자동 등록, 표기 없는 본문은 기관 없음.
4. 수집(`saveData`): 표기 있는 알림에만 기관이 붙고, 기관 해석이 실패해도 알림은 기관 없이 저장된다.

## 2. API 수동 확인

```bash
curl -s "localhost:8080/api/v1/events/<사건 id>" | jq '.representativeSn'
curl -s "localhost:8080/api/v1/archive/events/<위 representativeSn>" | jq '.data'
curl -s -o /dev/null -w "%{http_code}\n" "localhost:8080/api/v1/archive/events/1"   # 없는 sn → 404
```

기대: 해석 결과 `bound=true`, `representativeSn` 이 요청한 sn 과 같음.

## 3. 영구성 확인 (SC-002 축소판)

1. 사건 상세(`/events/{id}`)에서 영구 링크 5개를 메모한다.
2. 사본 DB 에서 백필 정리 절차(`EventClusteringBackfillTool` javadoc)의 `DELETE FROM disaster_events` + 시퀀스 리셋 후 재클러스터링을 실행한다. **LLM 비용이 드는 단계이므로 사용자 승인 후에만 실행한다.**
3. 같은 링크 5개를 다시 열어, 각 링크의 sn 알림이 포함된 사건이 보이는지 확인한다.

## 4. 화면 확인

- `/archive/events/{sn}`: 주소창이 `/events/{id}` 로 바뀌지 않는다.
- `/events/{id}`: "영구 링크" 복사 시 `/archive/events/{대표 sn}` 이 복사된다.
- 모바일 폭은 `ui-checker` 로 점검한다.
