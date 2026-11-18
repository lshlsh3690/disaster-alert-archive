# Quickstart: 재난 기록관 검증 (스토리 3)

스토리 3(연도별 주요 사건 + 사건 영구 링크)이 끝났는지 확인하는 절차. 계약은 [contracts/archive-api.md](contracts/archive-api.md), 규칙은 [data-model.md](data-model.md) 참고.

## 준비

```bash
docker compose -f docker-compose.dev.yml up postgres redis
cd backend && ./gradlew bootRun
cd frontend && npm run dev
```

로컬 DB 에 사건이 있어야 한다. 비어 있으면 운영 사본 덤프를 쓴다(백필로 사건을 만들면 LLM 호출 비용이 든다 — 비용 드는 실행은 사용자 승인 후).

## 1. 자동 테스트

```bash
cd backend
./gradlew test --tests "*Archive*"
```

통과해야 하는 핵심 동작(`@IntegrationTest`, 실제 Postgres):

1. 주요 사건에서 지속형 유형 사건이 빠진다.
2. 알림 수 내림차순으로 최대 10개가 나온다.
3. 대표 알림은 사건에서 가장 먼저 발령된 알림이다(같은 시각이면 작은 sn).
4. sn → 현재 사건 해석: 묶인 알림은 사건 ID 를, 묶이지 않은 알림은 `bound=false` 를 돌려준다.

## 2. API 수동 확인

```bash
curl -s "localhost:8080/api/v1/archive/major-events?year=2026" | jq '.data.events | length, .[0]'
curl -s "localhost:8080/api/v1/archive/events/<위 결과의 representativeSn>" | jq '.data'
curl -s -o /dev/null -w "%{http_code}\n" "localhost:8080/api/v1/archive/events/1"   # 없는 sn → 404
```

기대:
- `events` 길이 ≤ 10, `primaryDisasterType` 에 폭염·한파·대설·강풍·가축질병 없음.
- 해석 결과 `bound=true`, `representativeSn` 이 요청한 sn 과 같음.

## 3. 영구성 확인 (SC-002 축소판)

1. `/archive` 에서 사건 링크 5개를 메모한다.
2. 사본 DB 에서 백필 정리 절차(`EventClusteringBackfillTool` javadoc)의 `DELETE FROM disaster_events` + 시퀀스 리셋 후 재클러스터링을 실행한다. **LLM 비용이 드는 단계이므로 사용자 승인 후에만 실행한다.**
3. 같은 링크 5개를 다시 열어, 각 링크의 sn 알림이 포함된 사건이 보이는지 확인한다.

## 4. 화면 확인

- `/archive`: 연도 선택 시 목록이 바뀌고, 순위 번호·점수가 보이지 않는다.
- `/archive/events/{sn}`: 주소창이 `/events/{id}` 로 바뀌지 않는다.
- `/events/{id}`: "영구 링크" 복사 시 `/archive/events/{대표 sn}` 이 복사된다.
- 모바일 폭은 `ui-checker` 로 점검한다.
