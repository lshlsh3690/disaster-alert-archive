# 기능 명세: 위험도 계산 (Region Risk Scoring)

**기능 브랜치**: `002-risk-scoring`

**생성일**: 2026-08-05

**상태**: 구현 완료 (Retroactive — 이미 구현된 시스템의 as-built 문서화)

**입력**: `backend/src/main/java/com/disaster/alert/alertapi/domain/risk/` 전체 패키지 소스 코드 분석 결과

> 본 문서는 speckit 표준 워크플로(스펙 → 계획 → 구현)를 거치지 않고 이미 운영 중인
> 코드를 역으로 분석해 작성한 **as-built 명세**다. "사용자 스토리"는 향후 요구사항이
> 아니라 현재 코드가 실제로 수행하는 동작을 우선순위(중심성) 순으로 기술한 것이며,
> 모든 FR/시나리오는 실제 소스 코드 라인을 인용한다.

## 사용자 시나리오 및 테스트 *(필수)*

### 사용자 스토리 1 - 알림 클러스터링 결과에 따른 지역 위험도 비동기 갱신 (우선순위: P1)

재난문자가 이벤트로 클러스터링되면(`domain/event`), 위험도 모듈이 해당 이벤트의
유형·강도·정부 위급단계(DisasterLevel)를 합성해 영향 법정동에 위험 점수를 기록하고,
영향받은 시군구의 자기 위험도(source)와 인접 지역으로 번진 최종 위험도(effective)를
비동기로(별도 지연시간 SLA 없이) 재계산한다 — "실시간"은 정성적 표현일 뿐 측정된
지연 보장치가 아니다(SC-001 참고). 클러스터링 로직과는 Spring 애플리케이션 이벤트
(`AlertClusteredEvent`)로 완전히 분리되어 있어, 클러스터링 서비스는 위험도 계산
방식을 알지 못한다.

**우선순위 이유**: 위험도 파이프라인의 진입점이자 가장 자주(문자 도착마다) 실행되는
경로. 이게 없으면 나머지(감쇠, 확산, 조회)가 다룰 데이터 자체가 생기지 않는다.

**독립 테스트 방법**: `EventClusteringService`가 `AlertClusteredEvent(eventId, alertId)`를
발행한 뒤, `region_risk_index.source_score`/`risk_score`가 해당 이벤트의 영향 법정동
소속 시군구에 대해 0보다 큰 값으로 갱신되는지 확인.

**인수 시나리오**:

1. **Given** 새 재난문자가 클러스터링되어 신규 이벤트가 생성됨, **When** `EventClusteringService.createNewEvent`가 `eventPublisher.publishEvent(new AlertClusteredEvent(...))`를 호출함, **Then** 트랜잭션 커밋 후(`AFTER_COMMIT`) `ClusteringEventListener.onAlertClustered`가 비동기(`riskTaskExecutor`)로 실행된다 — `backend/.../event/service/EventClusteringService.java:600`, `backend/.../risk/listener/ClusteringEventListener.java:32-34`
2. **Given** `AlertClusteredEvent` 수신, **When** `recomputeEventRisk(eventId)`가 실행됨, **Then** `baseScore = weight[유형] × intensity[강도] × severity[위급단계]`가 계산되어 이벤트의 영향 법정동 전체에 `event_region_impact`로 upsert된다 — `backend/.../risk/service/RiskCalculationService.java:100-138`
3. **Given** `event_region_impact` upsert 완료, **When** 영향받은 시군구 집합이 리스너로 반환됨, **Then** 해당 시군구들에 대해 `recomputeRegionSource`가 호출되어 `region_risk_index.source_score`가 갱신된다 — `backend/.../risk/listener/ClusteringEventListener.java:37-41`
4. **Given** source가 갱신된 시군구가 1개 이상 존재, **When** 리스너 마지막 단계 실행, **Then** `propagateEffective()`가 전체 인접 그래프에 대해 1회 실행되어 `region_risk_index.risk_score`(effective)가 갱신된다 — `backend/.../risk/listener/ClusteringEventListener.java:43-47`
5. **Given** 위험도 재계산 중 예외 발생, **When** `onAlertClustered` 내부에서 예외가 던져짐, **Then** 예외는 로깅만 되고 상위(클러스터링) 파이프라인으로 전파되지 않는다 — `backend/.../risk/listener/ClusteringEventListener.java:49-52`
6. **Given** `recomputeEventRisk`가 실행 중, **When** 내부적으로 `region_risk_index`를 갱신해야 하는 시점에 도달, **Then** 직접 갱신하지 않고 영향받은 시군구 집합만 반환하며, 실제 갱신은 리스너(외부 빈)가 프록시 경유로 `recomputeRegionSource`/`propagateEffective`를 호출해야만 이루어진다(같은 빈 내부 자기호출은 `@Transactional`이 무효화되므로) — `backend/.../risk/service/RiskCalculationService.java:44-51,78-83`, FR-034 참고

---

### 사용자 스토리 2 - 시간 경과에 따른 위험도 감쇠 및 만료 (우선순위: P2)

각 이벤트의 위험 영향은 half-life 기반 지수 감쇠를 따르며, 스케줄러가 3분마다
활성 지역을 재계산해 감쇠를 반영한다. 감쇠로 무의미해지는 30일(`ACTIVE_WINDOW_DAYS`)이
지난 이벤트의 영향은 자기 위험도 계산에서 제외되고, 더 이상 활성 impact가 없는
지역은 점수가 0으로 내려간다.

**우선순위 이유**: 클러스터링 직후 갱신만으로는 "시간이 지나면 위험도가 낮아진다"는
핵심 속성을 만족하지 못한다 — 새 알림이 없어도 점수가 줄어들어야 하므로 별도
스케줄러 경로가 필수.

**독립 테스트 방법**: 특정 시군구의 `source_score`가 최근 이벤트 없이 시간만 경과했을 때
스케줄러 주기(3분)마다 감소하는지 확인. 활성 윈도우(30일)를 넘긴 이벤트만 있는
지역은 다음 주기에 0이 되는지 확인.

**인수 시나리오**:

1. **Given** `region_risk_index.source_score`를 구성하는 impact가 존재, **When** `recomputeRegionSource`가 재실행됨, **Then** `decayed = baseScore × exp(-Δt·ln2/halfLife)`(= `baseScore × 2^(-Δt/halfLife)`)로 재계산되고 활성 impact 중 최댓값(MAX)이 source로 채택된다 — `backend/.../risk/model/RiskScore.java:40-44`, `backend/.../risk/service/RiskCalculationService.java:167-179`
2. **Given** half-life가 프로파일에 지정되지 않음, **When** 감쇠 계산 시, **Then** 기본값 24시간(`DEFAULT_HALF_LIFE_HOURS`)을 사용한다 — `backend/.../risk/RiskConstants.java:20`, `backend/.../risk/service/RiskCalculationService.java:262-263`
3. **Given** 시군구에 `last_alert_at`이 현재로부터 30일(`ACTIVE_WINDOW_DAYS`)보다 오래된 이벤트만 남음, **When** `recomputeRegionSource`가 실행됨, **Then** `findActiveBySigungu`가 빈 목록을 반환하여 `source_score`가 0으로 upsert된다 — `backend/.../risk/repository/EventRegionImpactRepository.java:34-46`, `backend/.../risk/service/RiskCalculationService.java:161-164`
4. **Given** 3분 경과, **When** `RiskDecayScheduler.recomputeActiveRegions`가 트리거됨, **Then** 활성 impact 보유 시군구 + 현재 nonzero 시군구 전체에 대해 source 재계산 후 전파를 1회 실행한다 — `backend/.../risk/scheduler/RiskDecayScheduler.java:20-24`, `backend/.../risk/service/RiskMaintenanceService.java:36-60`
5. **Given** 감쇠/정규화 결과가 1.0을 초과할 수 있는 raw 점수, **When** 정규화 적용, **Then** `min(1.0, decayed/2.0)`로 0~1 범위로 클램프된다 — `backend/.../risk/model/RiskScore.java:47-49`, `backend/.../risk/RiskConstants.java:17`

---

### 사용자 스토리 3 - 인접 시군구로의 공간적 위험도 확산 (우선순위: P3)

시군구 자기 위험도(source)는 정부가 broadcast한 법정동에만 국한되지만, 최종 노출
점수(effective, `risk_score`)는 `region_adjacency`(경계 공유 그래프)를 타고 이웃
시군구까지 유형별 확산 계수(`spread_coeff`)의 거듭제곱으로 감쇠 전파된다.
여러 진원(source)의 영향이 겹치면 MAX로 결합한다.

**우선순위 이유**: 클러스터링/감쇠 다음으로 위험도 값에 영향을 주는 계산이며, FE
히트맵이 실제로 노출하는 `risk_score`(effective)를 최종적으로 결정한다.

**독립 테스트 방법**: `spread_coeff > 0`인 유형의 이벤트가 발생한 시군구의 이웃
시군구(`region_adjacency`에 등록됨)의 `risk_score`가 0보다 커지는지, `spread_coeff`가
0 이하인 유형(예: 화재, 정전)은 이웃으로 번지지 않는지 확인.

**인수 시나리오**:

1. **Given** `source_score > 0`인 시군구(진원 S, 유형 계수 c), **When** `propagateEffective()`가 실행됨, **Then** 홉 h만큼 떨어진 이웃의 값은 `source(S) × c^h`로 계산되며 첫 도달(BFS 최단 홉)이 그 진원 기준 최댓값으로 채택된다 — `backend/.../risk/service/RiskCalculationService.java:184-237`
2. **Given** 유형의 `spread_coeff <= 0`, **When** 전파 루프 진입, **Then** 자기 자신(홉0)만 반영되고 이웃 전파는 건너뛴다 — `backend/.../risk/service/RiskCalculationService.java:218`
3. **Given** 전파 값이 0.001(`SPREAD_EPSILON`) 미만으로 떨어짐, **When** BFS 다음 홉 계산, **Then** 더 이상 전파하지 않고 가지치기한다 — `backend/.../risk/service/RiskCalculationService.java:72,225`
4. **Given** 여러 진원의 영향이 같은 지역에 도달, **When** effective 값 결합, **Then** MAX(더 큰 값)로 결합된다 — `backend/.../risk/service/RiskCalculationService.java:251-259`
5. **Given** 이전에 nonzero였던 지역이 이번 전파에서 어떤 진원으로부터도 도달하지 못함, **When** 전파 종료 후 기록, **Then** 해당 지역의 effective 점수는 0으로 내려간다 — `backend/.../risk/service/RiskCalculationService.java:239-245`
6. **Given** 재난 유형별로 확산 강도가 다름(광역형 0.5, 국지형 0.1~0.3), **When** 시드 데이터 기준, **Then** 산불/태풍/황사/미세먼지/원전사고/방사능=0.5, 호우/대설/폭염/한파/지진해일/감염병/가축질병/적조=0.4, 지진/강풍/풍랑/해일/화학사고/환경오염=0.3, 산사태/침수/가스누출/해양사고/기타=0.2, 화재/폭발/붕괴/항공사고/철도사고/도로사고/정전/단수/통신장애/민방위=0.1이다 — `backend/src/main/resources/db/migration/V37__region_risk_spatial_spread.sql:1341-1348`

---

### 사용자 스토리 4 - 미등록 재난 유형에 대한 LLM 기반 위험 프로파일 생성 (우선순위: P4)

35종으로 사전 시드된 프로파일(`disaster_risk_profile`)에 없는 재난 유형(예:
동물탈출, 화학사고 변종)이 발생하면 `LlmRiskProfiler`가 `gpt-4o-mini`를 호출해
가중치(base_weight)와 반감기(half_life_hours)를 산출하고 DB에 캐싱한다. 다음부터는
동일 유형에 대해 LLM을 다시 호출하지 않는다.

**우선순위 이유**: 신규/희귀 유형 처리를 위한 fallback이며, 호출 빈도가 낮고(이벤트당
1회, 캐시 hit 후 0회) 클러스터링 도메인의 `llm-fallback.enabled` 같은 별도 게이팅
플래그 없이 항상 활성 상태다.

**독립 테스트 방법**: `disaster_risk_profile`에 없는 새 유형의 이벤트로
`recomputeEventRisk`를 호출했을 때 `LlmRiskProfiler.resolveForEvent`가 호출되고,
성공 시 새 행이 `is_llm_generated=true`로 저장되어 다음 호출부터는 LLM을 타지 않는지
확인.

**인수 시나리오**:

1. **Given** `disaster_risk_profile`에 해당 유형이 없음, **When** `RiskCalculationService.recomputeEventRisk`가 프로파일 조회, **Then** `profileRepo.findById(finalType)`이 비어 있어 `llmProfiler.resolveForEvent(event)`로 폴백한다 — `backend/.../risk/service/RiskCalculationService.java:104-105`
2. **Given** 이벤트 유형이 `"UNKNOWN"`, **When** `resolveForEvent` 호출, **Then** LLM 호출 없이 즉시 `"기타"` 프로파일로 폴백한다 — `backend/.../risk/service/LlmRiskProfiler.java:46-52`
3. **Given** LLM 호출 성공, **When** 응답 파싱, **Then** `base_weight`는 `[0.0, 1.5]`로 clamp, `half_life_hours`는 최소 1로 clamp되어 `disaster_risk_profile`에 `is_llm_generated=true`, `spread_coeff=0.3`(DEFAULT), `operator_confirmed=false`로 저장된다 — `backend/.../risk/service/LlmRiskProfiler.java:54-68`, `backend/.../risk/model/DisasterRiskProfile.java:30-31,58-67`
4. **Given** LLM 호출이 예외(거부/비JSON/네트워크 오류 등)를 던짐, **When** `resolveForEvent` catch 블록 진입, **Then** 결과를 캐싱하지 않고 `"기타"` 시드 프로파일로 폴백한다 — `backend/.../risk/service/LlmRiskProfiler.java:70-74`
5. **Given** `"기타"` 시드 프로파일이 DB에 없음(seed 마이그레이션 미적용 등), **When** 위 폴백 경로 진입, **Then** `IllegalStateException`이 던져진다(무한 방어 없음) — `backend/.../risk/service/LlmRiskProfiler.java:51,73`

---

### 사용자 스토리 5 - 지역 위험도 조회 API (우선순위: P5)

프론트엔드는 전국 히트맵, 특정 시군구 상세(+기여 이벤트), 기간별 히트맵 프레임,
시군구 시계열, 특정 재난문자 단건의 위험 영향을 조회할 수 있다.

**우선순위 이유**: 계산된 값을 실제로 노출하는 경로이지만, 계산 로직 자체(스토리
1~4)가 선행되어야 의미가 있어 우선순위가 가장 낮다.

**독립 테스트 방법**: 각 엔드포인트를 호출해 응답 스키마와 204/200 분기가 코드와
일치하는지 확인.

**인수 시나리오**:

1. **Given** 클라이언트가 `GET /api/v1/regions/risk-map` 호출, **When** 처리, **Then** `region_risk_index` 전체 행을 `{regionCode, riskScore, topEventId, updatedAt}`로 매핑해 반환한다 — `backend/.../risk/controller/RegionRiskController.java:25-28`, `backend/.../risk/service/RegionRiskQueryService.java:39-44`
2. **Given** `GET /{regionCode}/risk` 호출 시 해당 지역의 `region_risk_index` 행이 없음, **When** 처리, **Then** 0점·`topEventId=null`·`updatedAt=now()`로 응답한다(오류 아님) — `backend/.../risk/service/RegionRiskQueryService.java:47-51`
3. **Given** `GET /risk-map/history?start&end` 호출, **When** `start`가 90일(retention) 이내, **Then** `region_risk_history`(시간 단위) 프레임을, 그 이전이면 `region_risk_daily`(일 단위) 프레임을 반환한다 — `backend/.../risk/service/RegionRiskQueryService.java:70-99`
4. **Given** `GET /alerts/{alertId}/risk` 호출된 alertId가 아직 이벤트에 매핑되지 않음(클러스터링 전), **When** `findByAlertId`가 빈 목록 반환, **Then** HTTP 204 No Content를 응답한다 — `backend/.../risk/controller/RegionRiskController.java:61-66`, `backend/.../risk/service/RegionRiskQueryService.java:106-108`
5. **Given** `GET /{regionCode}/risk/history?days=N` 호출, **When** 처리, **Then** 최근 N일간의 `region_risk_history` 시계열 포인트를 반환한다(기본 7일) — `backend/.../risk/controller/RegionRiskController.java:54-58`, `backend/.../risk/service/RegionRiskQueryService.java:118-123`

---

### 예외 상황

- 이벤트에 매핑된 알림이 하나도 없으면(`findByEventId` 빈 목록) `recomputeEventRisk`는 로깅 후 빈 집합을 반환하고 아무 것도 기록하지 않는다 — `backend/.../risk/service/RiskCalculationService.java:94-98`
- 이벤트의 영향 법정동이 비어 있으면(알림에 지역 매핑이 없음) impact를 기록하지 않고 빈 집합을 반환한다 — `backend/.../risk/service/RiskCalculationService.java:132-135`
- `primaryDisasterType`이 `"UNKNOWN"`이면 위험도 계산 시 `"기타"`로 치환되어 처리된다 → 상세 규칙은 FR-032 참고.
- 강도 추출 정규식이 매칭되지 않거나 해당 유형에 매핑이 없으면 intensity multiplier는 1.0(미반영)으로 처리된다 → 상세 규칙은 FR-005 참고.
- `event.getEmergencyLevel()`이 null이면 severity multiplier는 1.0으로 처리된다(중립) → 상세 규칙은 FR-006 참고.
- 이벤트 위험도 재계산(`recomputeEventRisk`) 경로는 `region_risk_index`를 직접 갱신하지 않는다(자기 프록시 호출로 인한 `@Transactional` 무효화 회피) → 상세 규칙과 인수 시나리오는 FR-034 참고.

## 요구사항 *(필수)*

### 기능 요구사항

- **FR-001**: 시스템은 알림이 이벤트로 클러스터링 완료되면(`AlertClusteredEvent` 발행) 트랜잭션 커밋 이후(`AFTER_COMMIT`) 비동기로 위험도 재계산을 트리거해야 한다(MUST) — `backend/.../event/service/EventClusteringService.java:510,583,600`, `backend/.../risk/listener/ClusteringEventListener.java:32-34`
- **FR-002**: 시스템은 `baseScore = weight[유형] × intensity[강도] × severity[위급단계]` 공식으로 이벤트 단위 위험 점수를 산출해야 한다(MUST) — `backend/.../risk/service/RiskCalculationService.java:38,122`, `backend/.../risk/model/RiskScore.java:17-19`
- **FR-004**: 강도(intensity) multiplier는 이벤트에 속한 알림 본문에서 정규식으로 추출한 수치를 `intensity_bracket` 구간에 매핑해 산출하며, 이벤트 내 여러 알림 중 최댓값을 사용해야 한다(MUST) — `backend/.../risk/service/RiskCalculationService.java:108-113`, `backend/.../risk/service/IntensityExtractor.java:22-29`
- **FR-005**: 강도 추출/구간 매핑이 정의되지 않은 유형이거나 정규식 매칭에 실패하면 intensity multiplier는 1.0(미반영)이어야 한다(MUST) — `backend/.../risk/service/IntensityExtractor.java:16-17,37-41`, `backend/.../risk/repository/IntensityBracketRepository.java:12-22`
- **FR-006**: 위급단계에 따라 severity multiplier를 `LEVEL_3=1.2`, `LEVEL_2=1.0`, `LEVEL_1=0.6`, `null=1.0`으로 적용하고, 이벤트 내 알림 중 최댓값을 사용해야 한다(MUST) — `backend/.../risk/model/RiskScore.java:25-32`, `backend/.../risk/service/RiskCalculationService.java:116-119`
- **FR-007**: 이벤트의 영향 법정동은 이벤트에 매핑된 모든 알림의 법정동 코드 union이며, 별도의 공간 추론 없이 정부 broadcast 그대로 사용해야 한다(MUST) — `backend/.../risk/service/RiskCalculationService.java:124-130`
- **FR-008**: `event_region_impact`는 이벤트 단위로 upsert되어야 하며, 동일 사건에 알림이 N건 도착해도 영향 점수는 이중 카운팅되지 않아야 한다(MUST) — `backend/.../risk/service/RiskCalculationService.java:137-138`, `backend/.../risk/model/EventRegionImpact.java:17-19`
- **FR-009**: 시군구 자기 위험도(`source_score`)는 활성 윈도우(30일, `ACTIVE_WINDOW_DAYS`) 내 법정동 impact를 시군구(코드 앞 5자리)로 집계하고 시간 감쇠를 적용한 뒤 합산이 아닌 MAX로 결합해야 한다(MUST). 동점(동일 감쇠값)인 경우 점수 자체는 동일하지만, 설명용 `source_top_event_id`는 백킹 쿼리(`findActiveBySigungu`)에 `ORDER BY`가 없어 어떤 이벤트가 채택되는지 보장되지 않는다(비결정적) — `backend/.../risk/service/RiskCalculationService.java:156-180`, `backend/.../risk/RiskConstants.java:14`
- **FR-010**: 활성 impact가 하나도 없는 시군구는 `source_score`를 0으로 upsert해야 한다(만료 지역 zeroing)(MUST) — `backend/.../risk/service/RiskCalculationService.java:162-164`
- **FR-011**: 시간 감쇠는 half-life 기반 지수감쇠(`decayed = baseScore × 2^(-Δt/halfLife)`)로 계산해야 하며, half-life는 유형별 프로파일 값(없으면 기본 24시간, `DEFAULT_HALF_LIFE_HOURS`)을 사용해야 한다(MUST) — `backend/.../risk/model/RiskScore.java:40-44`, `backend/.../risk/RiskConstants.java:20`, `backend/.../risk/service/RiskCalculationService.java:261-266`
- **FR-012**: 감쇠 점수는 `min(1.0, decayed / NORMALIZE_DIVISOR(2.0))`로 0~1 범위 정규화해야 한다(MUST) — `backend/.../risk/model/RiskScore.java:47-49`, `backend/.../risk/RiskConstants.java:16-17`
- **FR-013**: 최종 노출 위험도(effective, `risk_score`)는 인접 그래프(`region_adjacency`)를 따라 진원(source)별 BFS로 감쇠 전파(`value × spreadCoeff^hop`)해야 하며, 여러 진원의 영향이 겹치면 MAX로 결합해야 한다(MUST). 동점일 경우 점수는 동일하나 설명용 진원 이벤트(top event) 선택은 비결정적이다(엄격한 `>` 비교로 먼저 처리된 진원이 유지되며, 처리 순서는 보장되지 않음) — `backend/.../risk/service/RiskCalculationService.java:184-237`
- **FR-014**: 유형의 `spread_coeff`가 0 이하이면 해당 진원은 이웃으로 전파하지 않고 자기 자신(홉0)만 반영해야 한다(MUST) — `backend/.../risk/service/RiskCalculationService.java:218`
- **FR-015**: 전파 값이 `SPREAD_EPSILON`(0.001) 미만으로 떨어지면 더 이상 전파하지 않아야 한다(BFS 가지치기)(MUST) — `backend/.../risk/service/RiskCalculationService.java:72,225`
- **FR-016**: 전파가 끝난 뒤 어떤 진원으로부터도 도달하지 못한 지역(과거 nonzero였던 지역 포함)의 effective 점수는 0으로 내려야 한다(MUST) — `backend/.../risk/service/RiskCalculationService.java:239-245`
- **FR-017**: `AlertClusteredEvent` 처리 시 영향받은 시군구가 1개 이상이면, 개별 지역이 아닌 전체 인접 그래프에 대해 전파를 1회 재실행해야 한다(MUST) — `backend/.../risk/listener/ClusteringEventListener.java:43-47`
- **FR-018**: 위험도 재계산 실패는 예외를 삼키고 로깅만 해야 하며, 클러스터링/수집 파이프라인으로 전파되어서는 안 된다(MUST) — `backend/.../risk/listener/ClusteringEventListener.java:49-52`
- **FR-019**: 위험도 재계산은 전용 스레드 풀(`riskTaskExecutor`, core=2/max=4/queue=500)에서 비동기로 실행되어야 한다(MUST) — `backend/.../global/config/AsyncConfig.java:33-42`
- **FR-020**: 3분마다(cron `0 */3 * * * *`) 활성 impact 보유 시군구와 현재 nonzero 시군구 전체에 대해 source 재계산 및 1회 전파를 수행해 시간 감쇠를 반영해야 한다(MUST) — `backend/.../risk/scheduler/RiskDecayScheduler.java:20-24`, `backend/.../risk/service/RiskMaintenanceService.java:36-60`
- **FR-021**: 1시간마다(cron `0 0 * * * *`) `risk_score > 0`인 지역의 현재 effective 점수를 `region_risk_history`에 스냅샷 저장해야 한다(MUST) — `backend/.../risk/scheduler/RiskDecayScheduler.java:26-30`, `backend/.../risk/service/RiskMaintenanceService.java:62-70`
- **FR-022**: 매일 23:50에(cron `0 50 23 * * *`) 당일 `region_risk_history`를 지역별 최고값 기준으로 `region_risk_daily`에 요약(rollup)해야 한다(MUST) — `backend/.../risk/scheduler/RiskDecayScheduler.java:32-36`, `backend/.../risk/repository/RegionRiskDailyRepository.java:26-38`
- **FR-023**: 매일 04:30에(cron `0 30 4 * * *`) 90일(`HISTORY_RETENTION_DAYS`)보다 오래된 `region_risk_history` 행을 삭제해야 한다(MUST) — `backend/.../risk/scheduler/RiskDecayScheduler.java:38-42`, `backend/.../risk/RiskConstants.java:23`
- **FR-024**: 사전 정의된 35종 프로파일(V33 seed)에 없는 재난 유형은 `LlmRiskProfiler`가 `gpt-4o-mini`로 `base_weight`(0.0~1.5 clamp)와 `half_life_hours`(최소 1)를 산출해 `disaster_risk_profile`에 `is_llm_generated=true`로 캐싱하고, 이후 동일 유형은 캐시를 재사용해야 한다(MUST). 유형 가중치(weight) 조회 자체는 `disaster_risk_profile`을 우선 조회하고 없을 때만 이 LLM 경로로 폴백한다(구 FR-003을 여기로 통합) — `backend/.../risk/service/LlmRiskProfiler.java:38-68`, `backend/src/main/resources/db/migration/V33__seed_risk_profile.sql`, `backend/.../risk/service/RiskCalculationService.java:104-105`
- **FR-025**: 재난 유형이 `"UNKNOWN"`이면 LLM 호출 없이 즉시 `"기타"` 프로파일로 폴백해야 한다(MUST) — `backend/.../risk/service/LlmRiskProfiler.java:46-52`
- **FR-026**: LLM 호출이 실패(거부/비JSON/예외)하면 결과를 캐싱하지 않고 `"기타"` 시드 프로파일로 폴백해야 한다(MUST) — `backend/.../risk/service/LlmRiskProfiler.java:70-74`
- **FR-027**: LLM이 생성한 신규 프로파일의 확산계수(`spread_coeff`)는 기본값 0.3(`DEFAULT_SPREAD_COEFF`)으로 시작해야 한다(MUST) — `backend/.../risk/model/DisasterRiskProfile.java:30-31,58-67`
- **FR-028**: 시스템은 전국 시군구 위험도 히트맵(`GET /api/v1/regions/risk-map`), 지역 상세+기여이벤트(`GET /{regionCode}/risk`), 기간별 히트맵 프레임(`GET /risk-map/history`), 시군구 시계열(`GET /{regionCode}/risk/history`), 특정 재난문자의 위험 영향(`GET /alerts/{alertId}/risk`) 조회 API를 제공해야 한다(MUST) — `backend/.../risk/controller/RegionRiskController.java:25-66`
- **FR-029**: 지역 상세 조회 시 해당 지역의 `region_risk_index` 행이 없으면 오류 대신 0점으로 응답해야 한다(MUST) — `backend/.../risk/service/RegionRiskQueryService.java:47-51`
- **FR-030**: 기간별 히트맵 조회는 조회 시작일이 90일(retention) 이내면 `region_risk_history`(시간 단위) 데이터를, 그 이전이면 `region_risk_daily`(일 단위) 데이터를 사용해야 한다(MUST) — `backend/.../risk/service/RegionRiskQueryService.java:70-99`
- **FR-031**: 아직 클러스터링/위험도 계산이 되지 않은 `alertId`로 위험도를 조회하면 HTTP 204 No Content를 반환해야 한다(MUST) — `backend/.../risk/controller/RegionRiskController.java:61-66`, `backend/.../risk/service/RegionRiskQueryService.java:106-108`
- **FR-032**: `disaster_events.primary_disaster_type`이 `"UNKNOWN"`이면 프로파일 조회와 확산계수 조회 양쪽 모두에서 `"기타"`로 치환되어야 한다(MUST) — `backend/.../risk/service/RiskCalculationService.java:100-101`, `backend/.../risk/repository/RegionRiskIndexRepository.java:59-61`
- **FR-033**: 시스템은 재난 유형별로 서로 다른 공간 확산 계수를 사용해야 한다 — 광역/이동성 유형(산불·태풍·황사·미세먼지·원전사고·방사능)은 0.5, 광역성 유형(호우·대설·폭염·한파·지진해일·감염병·가축질병·적조)은 0.4, 국지/광역 중간(지진·강풍·풍랑·해일·화학사고·환경오염)은 0.3, 국지형(산사태·침수·가스누출·해양사고·기타)은 0.2, 단일지점 사고(화재·폭발·붕괴·항공사고·철도사고·도로사고·정전·단수·통신장애·민방위)는 0.1을 사용한다(MUST) — `backend/src/main/resources/db/migration/V37__region_risk_spatial_spread.sql:1341-1348`
- **FR-034**: `recomputeEventRisk`는 `region_risk_index`를 직접 갱신해서는 안 되며(MUST NOT), 갱신은 반드시 외부 빈을 통한 별도 프록시 호출(`recomputeRegionSource`/`propagateEffective`)로만 수행되어야 한다 — 같은 빈 내부에서 자기 자신을 직접 호출하면 Spring `@Transactional` 프록시가 적용되지 않아 트랜잭션 경계가 깨지기 때문이다(MUST) — `backend/.../risk/service/RiskCalculationService.java:44-51,78-83`, `backend/.../risk/listener/ClusteringEventListener.java:37-47`

### 주요 엔티티 *(데이터가 관련된 경우 포함)*

- **DisasterRiskProfile** (`disaster_risk_profile`): 재난 유형(PK)별 `base_weight`, `half_life_hours`, `spread_coeff`, LLM 생성 여부(`is_llm_generated`), 운영자 확정 여부(`operator_confirmed`). 35종은 V33에서 시드, 이후 미등록 유형은 LLM이 런타임 추가.
- **IntensityBracket** (`intensity_bracket`): 재난 유형별 `[min_value, max_value)` 구간 → `multiplier` 매핑. 6종(지진/호우/폭염/한파/산불/강풍)만 시드됨.
- **EventRegionImpact** (`event_region_impact`, PK: `event_id`+`region_code`): 이벤트가 특정 법정동에 미치는 감쇠 적용 전 `impact_score`(baseScore). 이벤트 단위 기록으로 이중 카운팅 방지.
- **RegionRiskIndex** (`region_risk_index`, PK: `region_code`=시군구): 시군구별 현재 위험도. `source_score`(자기, 전파 전)와 `risk_score`(effective, 전파 후 — FE 노출값)를 분리 저장. 각각 `source_top_event_id`/`top_event_id`로 설명용 진원 이벤트를 함께 기록.
- **RegionRiskHistory** (`region_risk_history`, PK: `region_code`+`snapshot_at`): 1시간 주기 시계열 스냅샷(nonzero만). 90일 retention.
- **RegionRiskDaily** (`region_risk_daily`, PK: `region_code`+`snapshot_date`): 일별 최고값 요약(다운샘플링). 90일을 넘는 과거 조회에 사용.
- **RegionAdjacency** (`region_adjacency`, PK: `region_code`+`neighbor_code`): 시군구 경계 공유 기반 인접 그래프(양방향 1,314행, V37 seed). 공간 확산 전파의 입력.
- **AlertClusteredEvent**: `domain/event`가 발행하고 `domain/risk`가 구독하는 Spring 도메인 이벤트. `record AlertClusteredEvent(Long eventId, Long alertId)`.

## 성공 기준 *(필수)*

<!--
  본 시스템은 아직 위험도 계산 결과에 대한 별도 운영 지표(정확도, 알림 지연시간 SLA,
  사용자 만족도 등)를 계측하고 있지 않다. 아래는 코드에서 실제로 관찰 가능한
  "측정 가능한 결과"만 기술하며, 계측되지 않은 지표는 추측으로 채우지 않는다.
-->

### 측정 가능한 결과

- **SC-001**: 알림 클러스터링 완료(커밋) 후 위험도 재계산은 비동기로 트리거되며, 별도 성능 SLA(예: "N초 이내")는 코드/설정 어디에도 명시되어 있지 않다 — 실측되지 않음.
- **SC-002**: 시간 감쇠 재계산 스케줄러는 3분 주기로 고정되어 있어(`RiskDecayScheduler.java:21`), 위험도 표시 값은 최악의 경우 최대 3분 지연으로 시간 경과를 반영한다.
- **SC-003**: 전체 그래프 전파(`propagateEffective`)는 시군구 약 230개, 인접 약 1,300쌍 규모에서 "ms 단위"로 완료된다고 코드 주석에 명시되어 있으나(`RiskCalculationService.java:191`), 실측 벤치마크나 부하 테스트 결과는 저장소에 존재하지 않는다 — 주석상의 주장이며 검증된 수치가 아니다.
- **SC-004**: 위험도 계산 비즈니스 로직(`RiskCalculationService`, `RiskScore`, `LlmRiskProfiler` 등)에 대한 자동화 테스트는 저장소에 존재하지 않는다(`backend/src/test` 하위에 risk 관련 테스트 파일 없음) — 정확성 검증은 현재 코드 리뷰와 수동 확인에만 의존하고 있다.

## 가정

- **활성 윈도우 값의 이중 사용**: `RiskConstants.ACTIVE_WINDOW_DAYS = 30`은 "위험도 계산에 반영할 이벤트의 최대 나이"와 "과거 기여 이벤트 검색 확장 범위" 양쪽에 재사용된다. 다만 `RegionRiskQueryRepository.findHistoricalEvents`의 주석은 "7일(ACTIVE_WINDOW_DAYS)"라고 적혀 있어 실제 값(30일)과 불일치한다 — 코드 동작은 30일 기준이며 주석이 stale하다(`backend/.../risk/repository/RegionRiskQueryRepository.java:74-76`).
- **운영자 보정 기능은 미사용(고아 코드)**: `DisasterRiskProfile.applyOperatorOverride(...)`는 프로파일을 수동 보정하는 메서드로 존재하지만, 이를 호출하는 컨트롤러/서비스/관리자 API가 저장소 어디에도 없다. `operator_confirmed=true`는 V33 seed 데이터에서만 설정되고, LLM 생성분(`operator_confirmed=false`)을 운영 중 수동 확정할 경로는 현재 구현되어 있지 않다.
- **LLM 프로파일 생성에는 클러스터링 도메인과 달리 별도 게이팅 플래그가 없다**: `EventLLMDecisionService` 등 클러스터링 쪽 LLM 호출은 `llm-fallback.enabled`/`cross-region.enabled` 같은 설정 플래그 뒤에 있지만, `LlmRiskProfiler`는 그런 `@ConditionalOnProperty`/설정 검사가 코드에 없다 — 35종에 없는 유형이 처음 감지되는 즉시 항상 LLM을 호출한다(단, 유형당 최초 1회만).
- **공간 확산은 "정부 broadcast 영향 법정동 자체"에는 적용되지 않는다**: FR-007에서 보듯 이벤트의 1차 영향 법정동은 알림에 포함된 법정동 그대로 사용하고 공간 추론을 하지 않는다. 공간 확산(BFS, 유형별 spread_coeff)은 오직 "시군구 source → effective" 단계에만 적용된다 — 두 개념(법정동 broadcast 집합 vs 시군구 인접 전파)이 서로 다른 레이어임을 전제한다.
- **클러스터링 도메인과의 완전한 결합도 분리**: 위험도 계산은 `AlertClusteredEvent`라는 단일 이벤트 payload(`eventId`, `alertId`)만 받으며, 클러스터링 임계값·병합 방식(EMBEDDING/BROADCAST/REGIONAL_TYPE/ADVISORY 등)에 대해 전혀 알지 못한다는 것을 전제로 설계되어 있다.
- **`AlertClusteredEvent` 발행 지점은 저장소 전체에 3곳뿐이다**: `domain/event/service/EventClusteringService.java:510,583,600`(신규 안내성 이벤트 생성, 기존 이벤트 병합, 신규 이벤트 생성). `CLAUDE.md`가 언급하는 `EventFragmentMergeService`(파편 이벤트 정합화 스케줄러)는 조사 시점 기준 이 저장소의 `domain/event/service/`에 실제로 존재하지 않는다 — 해당 패키지에는 `EventClusteringService`, `EventCrossRegionService`, `EventLLMDecisionService`, `EventQueryService`, `EventTranslationService`만 있다. 따라서 이벤트 병합/파편 흡수가 `event_region_impact`를 갱신하는 별도 경로는 현재 코드베이스에 존재하지 않는다(향후 해당 서비스가 추가되면 `AlertClusteredEvent` 재발행 여부를 함께 검토해야 한다).
