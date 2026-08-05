# 기능 명세: 이벤트 클러스터링 파이프라인 (Event Clustering Pipeline)

**기능 브랜치**: `001-event-clustering-pipeline`

**생성일**: 2026-08-05

**상태**: Draft (as-built — 이미 구현된 시스템을 사후 문서화)

**입력**: 사용자 설명: "재난 안전 문자 아카이브 서비스의 이벤트 클러스터링 파이프라인 — 개별 재난문자(disaster_alert)를 같은 사건(disaster_event)으로 자동 그룹핑하는 서브시스템을 기존 코드 기준으로 문서화"

> **문서 성격**: 이 명세는 신규 기능 요청이 아니라 **이미 운영 중인(as-built) 시스템의 사후 명세**다. "사용자 스토리"는 향후 구현할 요구사항이 아니라 현재 코드가 실제로 수행하는 동작을 서술한다. 모든 FR/시나리오는 실제 소스 코드(`backend/src/main/java/.../domain/event/`)와 `backend/src/main/resources/application.yml`의 `clustering.*` 블록을 근거로 작성했으며, 파일 경로:줄번호를 함께 표기한다.

## 사용자 시나리오 및 테스트 *(필수)*

<!--
  이 섹션의 "사용자 스토리"는 실제 관측된 시스템 동작/흐름이며, 파이프라인에서 차지하는
  비중(핵심 경로 여부)에 따라 순서를 매겼다(가상의 비즈니스 우선순위가 아님).
-->

### 사용자 스토리 1 - 동일 시군구 내 유사 알림의 임베딩 기반 자동 병합 (우선순위: P1)

새 재난문자가 수집되면 OpenAI 임베딩(`text-embedding-3-small`, 1536차원)을 생성하고, 같은 시군구(법정동 코드 앞 5자리)에서 발송된 최근 7일 이내 이벤트들과 코사인 유사도를 비교한다. 가장 유사한 후보가 임계값(0.85) 이상이면 그 이벤트에 새 알림을 합류시키고, 아니면 새 이벤트를 만든다. 이는 전체 파이프라인의 기본 경로(default path)로, 인물/전국유형/지역앵커유형/광역 등 다른 모든 특수 경로가 먼저 걸러지지 않은 알림이 최종적으로 도달하는 지점이다.

**우선순위 이유**: 다른 모든 분기(인물, 전국유형, 지역앵커유형, 브로드캐스트)는 이 임베딩 경로가 만들어내던 오류(과소병합/과병합)를 실측 데이터로 발견하고 보완하기 위해 파생된 특수 경로다. 이 경로가 없으면 나머지 로직도 존재 이유가 없다.

**독립 테스트 방법**: `clustering.enabled=true`인 환경에서 같은 시군구·비슷한 시각·유사한 본문의 알림 2건을 순서대로 수집시키면 동일 `disaster_event`로 묶이는지 확인.

**인수 시나리오**:

1. **Given** 같은 시군구에 지난 7일 이내 발생한 미브로드캐스트/미안내성 이벤트가 존재하고 그 대표 알림과의 코사인 거리가 0.15 이하(유사도 0.85 이상)인 신규 알림이 수집되면, **When** `EventClusteringService.clusterNewAlert`가 호출되면, **Then** 기존 이벤트에 `MergeMethod.EMBEDDING`으로 합류하고 `event_alert_mapping`에 유사도 값이 기록된다 (`EventClusteringService.java:236-244`, `238-244`).
2. **Given** 같은 시군구 후보가 없거나 모든 후보의 코사인 거리가 0.15를 초과하고 LLM 폴백도 매칭에 실패하면, **When** `clusterNewAlert`가 호출되면, **Then** 새 `DisasterEvent`가 `MergeMethod.SEED`로 생성된다 (`EventClusteringService.java:254`, `588-603`).
3. **Given** 알림에 법정동 지역 코드가 하나도 없으면, **When** `doCluster`가 호출되면, **Then** 후보 검색 없이 즉시 신규 이벤트로 생성한다 (`EventClusteringService.java:211-217`).

---

### 사용자 스토리 2 - 지역앵커 유형(산불·산사태·홍수)의 유형+시군구 기반 병합 (우선순위: P2)

산불·산사태·홍수처럼 "유형과 시군구 조합 자체가 곧 하나의 사건"인 이산적 재난은, 같은 시군구·같은 유형·유형별 시간 윈도우 안이면 본문 임베딩을 전혀 보지 않고 하나의 이벤트로 묶는다. 실데이터 검증에서 한 산불 사건이 며칠에 걸쳐 burst로 발송되며 본문 텍스트가 달라져(코사인 유사도 0.85 미만) 임베딩 경로가 같은 사건을 최대 16개 조각으로 파편화한 문제(산청 산불 사례)를 차단하기 위한 경로다.

**우선순위 이유**: 임베딩 경로 다음으로 실제 트래픽의 상당 비중(산불류)에 적용되며, 튜닝 이력(주석)에 근거가 가장 상세히 남아 있는 핵심 보정 로직이다.

**독립 테스트 방법**: 같은 시군구에 "산불" 유형 알림 2건을 본문을 다르게 하여 14일 이내 순서대로 수집시키면 하나의 이벤트로 묶이는지 확인.

**인수 시나리오**:

1. **Given** `clustering.regional-types`(`산불:336,산사태:168,홍수:168`)에 매핑된 유형의 알림이 수집되고, 같은 시군구(footprint 교집합)에 해당 유형·`is_broadcast=false`·`is_advisory=false`인 기존 이벤트가 유형별 윈도우(산불 336h/14일, 산사태·홍수 168h/7일) 이내에 있으면, **When** `clusterRegionalType`이 호출되면, **Then** 임베딩 비교 없이 `MergeMethod.REGIONAL_TYPE`으로 그 이벤트에 병합된다 (`EventClusteringService.java:452-490`, 주석 `436-451`, `DisasterEventRepository.java:176-196`).
2. **Given** 산불 알림 지역이 걸친 시군구 수가 `clustering.max-region-span`(10)을 초과하면, **When** `clusterRegionalType`이 호출되면, **Then** 지역앵커 경로를 타지 않고(`false` 반환) 광역 broadcast 경로로 넘어간다 — 전국 단위 산불이 시군구별로 쪼개지는 것을 막기 위함 (`EventClusteringService.java:461-465`).
3. **Given** 알림이 산불이고 `FireAlertClassifier.isAdvisory(message, emergencyLevel)`가 true(건조특보/소각금지/예방캠페인 등 실화재 아님)이면, **When** `clusterRegionalType`이 호출되면, **Then** 사건 버킷이 아니라 시군구별 롤링 안내 이벤트(`is_advisory=true`)에 별도로 합류한다 (`EventClusteringService.java:467-474`, `496-514`, `FireAlertClassifier.java:63-84`).

---

### 사용자 스토리 3 - 광역·전국 브로드캐스트 알림의 시도/전국 단위 통합 (우선순위: P3)

한 알림이 다수 시군구(10개 초과, `clustering.max-region-span`)에 걸쳐 발송되면 "광역 브로드캐스트"로 간주해 지역 단위(local) 이벤트와 절대 섞지 않는다. 걸친 시도 수가 8개(`clustering.nationwide-sido-span`) 이상이면 "전국 {유형}"으로, 미만이면 "최다 시군구 시도 + 유형"으로 묶는다. 폭염주의보 발효→해제→격상처럼 본문이 서로 달라도 같은 사건으로 인식해야 하므로 임베딩은 보지 않는다.

**우선순위 이유**: 로컬 임베딩 경로가 지역 hard 필터 특성상 광역 알림을 감당할 수 없어(폭염·호우 등 템플릿형 문구가 여러 시군구에서 동시에 높은 유사도로 나타나 다지역 blob 생성) 별도로 격리해야 하는 경로다.

**독립 테스트 방법**: 17개 시도 전체에 "폭염" 유형 알림을 발송시키면 하나의 "전국 폭염" 이벤트로 묶이는지 확인.

**인수 시나리오**:

1. **Given** 신규 알림이 걸친 distinct 시군구 수가 `max-region-span`(10)을 초과하고 distinct 시도 수가 `nationwide-sido-span`(8) 이상이면, **When** `clusterBroadcast`가 호출되면, **Then** `primary_region_code=null`, 제목 "전국 {유형}"인 broadcast 이벤트에 유형만으로 묶인다(정보성 유형에 한함) (`EventClusteringService.java:638-660`).
2. **Given** 시도 span이 8 미만이면, **When** `clusterBroadcast`가 호출되면, **Then** 지역 목록 순서에 무관하게 "가장 많은 시군구를 차지한 시도"(dominant sido)와 유형을 키로 삼아 병합하거나 신규 생성한다 (`EventClusteringService.java:662-678`, `692-700`).
3. **Given** 유형이 `null` 또는 "기타"(`DisasterEvent.isInformativeType`이 false)이면, **When** `clusterBroadcast`가 호출되면, **Then** 병합을 시도하지 않고 항상 신규 이벤트로 생성한다 — 물놀이 안전수칙·댐 방류 등 본문이 제각각인 안내를 잘못 묶지 않기 위함 (`EventClusteringService.java:640`, `646-656`, `664-673`).

---

### 사용자 스토리 4 - 전국 통합 유형(태풍)의 지역·임베딩 무관 단일화 (우선순위: P4)

태풍처럼 본질적으로 전국 단일 사건인 유형(`clustering.global-types`, 기본 "태풍")은 지역이나 본문 유사도를 전혀 보지 않고, 시간 윈도우(168h/7일) 안의 기존 전국 broadcast 이벤트에 무조건 합류한다. 태풍 알림이 시군구 단위로 쪼개져 발송되어 광역 span 게이트를 넘지 못하고 로컬 경로의 지역 hard 필터에도 막혀 시군구마다 별도 이벤트가 생기던 문제를 유형 자체를 키로 삼아 막는다.

**우선순위 이유**: 인물 IDENTITY 병합과 같은 "유형/신원 자체가 곧 사건 키"라는 철학을 공유하는 특수 경로이지만, 적용 대상(연 수 회의 태풍)이 적어 임베딩 경로 대비 비중은 낮다.

**독립 테스트 방법**: 서로 다른 시군구에 이름이 다르게 언급된(또는 언급 안 된) 태풍 알림을 순서대로 수집시키면 하나의 이벤트로 묶이고 제목이 승급되는지 확인.

**인수 시나리오**:

1. **Given** 알림 유형이 `clustering.global-types`(기본 "태풍")에 속하면, **When** `clusterGlobalType`이 호출되면, **Then** 지역·임베딩과 무관하게 168시간(`clustering.global-window-hours`) 이내의 `is_broadcast=true & primary_region_code IS NULL & 같은 유형` 이벤트에 `MergeMethod.GLOBAL_TYPE`으로 합류한다 (`EventClusteringService.java:388-408`).
2. **Given** 첫 알림에 태풍 고유명이 없어 이벤트 제목이 "전국 태풍"(기본 제목)으로 생성되었고, 이후 합류한 알림 본문에서 정규식(`제N호 태풍 「이름」` 등)으로 고유명이 추출되면, **When** `upgradeGlobalTitleIfNamed`가 호출되면, **Then** 제목이 "태풍 {이름}"으로 갱신된다. 단, 이미 이름이 붙어 있으면 조건부 UPDATE가 no-op이라 먼저 붙은 이름이 유지된다 (`EventClusteringService.java:423-434`, `DisasterEvent.java:99-131`, `DisasterEventRepository.java:283-291`).

---

### 사용자 스토리 5 - 실종 인물의 신원(이름+나이+키) 기반 결정적 병합 (우선순위: P5)

`disaster_type='기타'`이면서 "OOO씨(N세) ... NNNcm" 형식이 인식되는 실종 경찰 알림은, 임베딩이나 지역이 아니라 이름+나이(+키, 있으면)라는 신원 키로만 클러스터링한다. 같은 사람이 지역을 옮겨 다니며 재신고되어도 하나의 이벤트로, 같은 시군구의 다른 실종자는 신원이 다르면 별개 이벤트로 유지된다. 윈도우는 14일(336h).

**우선순위 이유**: 임베딩+지역 방식으로는 (a) 지역을 옮긴 동일인을 못 묶고 (b) 같은 시군구의 다른 실종자를 템플릿 유사도로 잘못 묶는 두 가지 실패 모드가 실측됐기에, 전용 결정적 규칙으로 완전히 분리한 경로다. LLM도 임베딩도 쓰지 않아 재클러스터링 비용이 없다.

**독립 테스트 방법**: 같은 이름+나이의 실종 알림을 서로 다른 시군구에서 발송시키면 하나의 이벤트로 묶이고, 나이가 다른 동명이인은 별개 이벤트로 생성되는지 확인.

**인수 시나리오**:

1. **Given** `disaster_type='기타'`이고 `MissingPersonIdentity.isPerson(message)`가 true(이름+나이 추출 가능)이면, **When** `clusterPerson`이 호출되면, **Then** 임베딩/지역 로직으로 넘어가지 않고 이 경로에서 종료된다(`return true`) (`EventClusteringService.java:352-355`).
2. **Given** 같은 "이름+나이(+키)" 키로 지난 336시간(`clustering.person-window-hours`) 이내에 생성된 이벤트가 존재하면, **When** `findCrossRegionCandidatesByPerson`이 조회되면, **Then** 시군구와 무관하게 그 이벤트에 `MergeMethod.IDENTITY`로 합류한다 (`EventClusteringService.java:361-368`, `DisasterEventRepository.java:436-459`).
3. **Given** 키(cm)가 추출되었고 이름+나이는 같지만 키가 다른 알림이면, **When** 후보 검색 쿼리가 실행되면, **Then** 다른 사람(동명이인)으로 판단해 후보에서 제외된다 — 키 정보가 없으면 이름+나이만으로 매칭한다 (`DisasterEventRepository.java:432-434`, `447`).

---

### 사용자 스토리 6 - 동물 등 비정형 이동 사건의 cross-region LLM 병합 (우선순위: P6)

탈출한 멧돼지·들개·늑대 등 지역을 넘나드는 비정형 사건은, 정형 신원 키가 없어 실종 인물처럼 결정적으로 묶을 수 없다. 대신 종(species) 하드 게이트 + 지역 인접(토착종은 시군구 인접, 이동종은 시도 인접) + 시간 윈도우로 후보를 좁힌 뒤 LLM(`gpt-4o-mini`)이 "동일 개체의 같은 사건"인지 최종 판정한다. 로컬 클러스터링(`clusterNewAlert`)이 이미 어떤 이벤트에 배정한 뒤 실행되는 별도 후속 패스다.

**우선순위 이유**: 발생 건수가 적고(멧돼지·들개 등 한정된 종) LLM 비용이 드는 borderline 경로라 전체 파이프라인에서 차지하는 비중은 낮지만, 정형 키가 없는 유일한 이동성 사건 유형이라 별도 서비스(`EventCrossRegionService`)로 분리돼 있다.

**독립 테스트 방법**: `clustering.cross-region.enabled=true`인 환경에서 같은 멧돼지 출몰 알림을 인접 시군구 2곳에서 발송시키면 두 이벤트가 하나로 합쳐지는지 확인.

**인수 시나리오**:

1. **Given** `disaster_type='기타'`이고 실종 인물이 아니며 `AnimalIdentity.species(message)`로 종이 식별되면, **When** `linkCrossRegion`이 호출되면, **Then** `linkAnimal`로 진입한다. 사전에 없는 종(미식별)이면 보수적으로 아무 것도 하지 않는다 (`EventCrossRegionService.java:92-101`, `142-147`).
2. **Given** 대상 알림이 이미 2개 이상 시도에 걸친(=이미 cross-region 링크됨) 이벤트에 속해 있으면, **When** `linkAnimal`이 호출되면, **Then** 재처리를 skip한다(idempotency 가드) (`EventCrossRegionService.java:138-141`).
3. **Given** 토착종(멧돼지·들개·뱀)이면 시군구 인접, 그 외(늑대·사슴·곰·소)면 시도 인접 조건으로 top-5(`clustering.cross-region.top-k`) 후보를 코사인 유사도 0.6(`similarity-floor`) 이상으로 좁히고, **When** `EventLLMDecisionService.pickSameIncident`가 "동일 개체"로 판정하면, **Then** 두 이벤트가 `MergeMethod.LLM`으로 병합된다. 단 병합 후 알림 수가 30건(`span-cap-alerts`)을 넘거나 걸친 시도 수가 10개(`span-cap-sido`) 이상이 되면 병합을 취소한다 (`EventCrossRegionService.java:160-198`, `209-220`).

---

### 사용자 스토리 7 - 사고성 사건의 LLM borderline 폴백 병합 (우선순위: P7)

같은 시군구 안에서 발생했지만 여러 기관이 서로 다른 측면(도로 통제/열차 운행 중지/복구 안내)으로 알림을 보내 본문 임베딩 유사도가 임계(0.85) 미만으로 떨어지는 사고("서소문 고가 붕괴" 유형)를, 사고성 유형 화이트리스트("기타,화재,산불,붕괴,교통사고,교통통제,교통,환경오염사고,정전,통신,테러,지진,지진해일,수도") + 코사인 거리 (0.15, 0.40] 구간 후보에 한해 LLM으로 동일 사건 여부를 판정한다. 폭염·호우 등 기상특보 반복 발령은 화이트리스트에서 제외해 과병합을 막는다.

**우선순위 이유**: 로컬 임베딩 경로의 미세 보정 장치로, 적용 범위가 좁게 게이트돼 있어(유형 화이트리스트 + 거리 구간 + 동물 제외) 발동 빈도는 낮다.

**독립 테스트 방법**: `clustering.llm-fallback.enabled=true`인 환경에서 같은 시군구·같은 사고를 다르게 서술한 "교통통제" 알림 2건을 수집시키면 LLM 판정을 거쳐 하나로 묶이는지 확인.

**인수 시나리오**:

1. **Given** `llm-fallback.enabled=true`이고 알림 유형이 사고성 화이트리스트에 속하며 동물 키워드(`탈출|출몰|멧돼지|들개|늑대`)에 매칭되지 않으면, **When** `tryLlmFallback`이 호출되면, **Then** 코사인 거리가 (0.15, 0.40](`llm-fallback.distance-ceil`) 구간인 같은 지역 후보만 LLM 질의 대상이 된다 (`EventClusteringService.java:266-282`).
2. **Given** borderline 후보 이벤트의 대표(seed) 유형도 사고성 화이트리스트에 속해야만, **When** LLM 후보 목록을 구성하면, **Then** 그 후보가 LLM 프롬프트에 포함된다 — 화이트리스트 알림이 기상특보 이벤트에 흡수되는 과병합을 차단 (`EventClusteringService.java:284-297`).
3. **Given** LLM이 특정 후보를 "동일 사건"으로 지목하면, **When** `pickSameGeneralIncident`가 응답을 파싱하면, **Then** `MergeMethod.LLM_FALLBACK`으로 병합한다. LLM 호출 실패·응답이 "NONE"·응답 번호가 후보 범위를 벗어나면 모두 보수적으로 null 처리해 신규 이벤트로 진행한다 (`EventClusteringService.java:299-308`, `EventLLMDecisionService.java:115-140`).

---

### 사용자 스토리 8 - 쿨다운 기반 진행 중(active) 상태의 조회 시점 파생 판정 (우선순위: P8)

이벤트의 "진행 중/종료" 상태는 별도 컬럼이나 스케줄러로 관리하지 않는다(행안부가 사건 종료 신호를 주지 않기 때문). 대신 이벤트 생성 시 재난 유형별로 1회 산정한 `cooldown_hours`만 저장하고, 조회 시점에 `now - last_alert_at < cooldown_hours`를 계산해 active 여부를 파생한다. 이후 같은 이벤트에 알림이 병합돼 `last_alert_at`이 갱신되면 자동으로 다시 active로 돌아간다.

**우선순위 이유**: 클러스터링 자체의 병합 로직은 아니지만, 클러스터링 결과(이벤트)의 상태를 소비하는 모든 조회 경로(이벤트 목록/상세, 위험도 계산 등)가 의존하는 파생 규칙이라 파이프라인의 일부로 포함한다.

**독립 테스트 방법**: cooldown 72시간인 이벤트의 `last_alert_at`을 71시간 전/73시간 전으로 놓고 조회하면 각각 active=true/false로 나오는지 확인.

**인수 시나리오**:

1. **Given** 이벤트 생성 시 `primary_disaster_type`이 `DisasterCooldown`의 LONG_TYPES(산불·지진·지진해일·폭염·한파·전염병·가축질병·가뭄)에 속하면, **When** `createFromFirstAlert`/`createAdvisory`가 호출되면, **Then** `cooldown_hours=168`(7일)로 저장된다. MID_TYPES(태풍·홍수·호우·대설·산사태·풍랑·황사·환경오염·미세먼지·에너지)는 72시간, SHORT_TYPES(화재·붕괴·폭발 등)는 24시간, 그 외/null은 기본 72시간이다 (`DisasterCooldown.java:23-41`, `DisasterEvent.java:191`, `219`).
2. **Given** 이벤트의 `last_alert_at`이 `now - cooldown_hours`보다 이후면, **When** `DisasterEvent.isActive(now)` 또는 동등한 네이티브 쿼리(`findActive`/`findInactive`/`search`)가 평가되면, **Then** active=true로 계산된다. 이 값은 컬럼으로 저장되지 않는다 (`DisasterEvent.java:274-276`, `DisasterEventRepository.java:26-49`).

---

### 사용자 스토리 9 - 백필 도구를 통한 과거 알림 재처리·임계값 튜닝 (우선순위: P9)

`EventClusteringBackfillTool`은 Spring Profile `backfill`에서만 활성화되는 `ApplicationRunner`로, 과거 `disaster_alert` 전체를 현재 `clustering.*` 설정으로 재처리한다. 임베딩은 1회만 생성해 저장하고(비용 절감), 이벤트 테이블을 비운 뒤 저장된 임베딩으로 재클러스터링하는 것을 반복해(OpenAI 재호출 없음) 임계값을 공짜로 튜닝할 수 있게 한다.

**우선순위 이유**: 실시간 클러스터링 경로 자체는 아니지만, 코드 곳곳의 주석이 "실측 데이터 기반 튜닝"을 근거로 들고 있어 이 도구 없이는 그 튜닝 과정이 재현 불가능하다.

**독립 테스트 방법**: `--backfill.embed-only=true`로 임베딩만 배치 생성한 뒤, 이벤트 테이블을 TRUNCATE하고 `--backfill.recluster=true`로 재실행해 OpenAI 호출 없이 이벤트가 재구성되는지 확인.

**인수 시나리오**:

1. **Given** `SPRING_PROFILES_ACTIVE=backfill`이고 `--backfill.embed-only=true`이면, **When** 애플리케이션이 기동되면, **Then** `embedding IS NULL`인 알림을 200건(`EMBED_BATCH_SIZE`)씩 묶어 OpenAI 임베딩 API를 배치 호출하고 클러스터링은 수행하지 않는다 (`EventClusteringBackfillTool.java:57`, `67-68`, `129-160`).
2. **Given** `--backfill.recluster=true`이면, **When** 애플리케이션이 기동되면, **Then** `embedding IS NOT NULL`인 알림 전체를 `created_at ASC` 순으로 `clusterNewAlert`에 재통과시킨다(OpenAI 미호출) — 호출 전 `event_alert_mapping`/`disaster_events`를 비워야 함을 로그로 경고한다 (`EventClusteringBackfillTool.java:76-83`, `205-211`).
3. **Given** `--backfill.cross-region=true`이면, **When** 애플리케이션이 기동되면, **Then** `disaster_type='기타'`이고 임베딩이 존재하는 알림만 `created_at ASC`로 `linkCrossRegion`에 재통과시킨다(recluster 완료 후 실행을 전제) (`EventClusteringBackfillTool.java:71-74`, `162-196`).

---

### 예외 상황

- **클러스터링 비활성화**(`clustering.enabled=false`, 기본값): `clusterNewAlert`가 알림을 조회하지도 않고 즉시 반환한다(no-op) (`EventClusteringService.java:137-140`).
- **알림 본문이 비어 있음**: `alert.getMessage()`가 null/blank면 경고 로그만 남기고 skip한다 (`EventClusteringService.java:148-151`).
- **알림 조회 실패**(존재하지 않는 alertId): 경고 로그 후 조용히 반환한다 (`EventClusteringService.java:142-146`).
- **클러스터링 처리 중 예외 발생**(OpenAI 임베딩 API 실패 등): 개별 알림 단위에서 예외를 잡아 로그만 남기고 스케줄러 사이클 전체를 막지 않는다. 예외를 재던지지 않으므로 실패한 알림은 다음 수집 사이클이나 백필 도구로만 복구된다 (`EventClusteringService.java:153-158`, `EventCrossRegionService.java:83-88`).
- **cross-region LLM 호출 실패**: `ChatModel.call`이 예외를 던지면 경고 로그 후 null(매칭 없음)로 처리 — 병합하지 않고 원래 이벤트에 남긴다 (`EventLLMDecisionService.java:115-122`).
- **LLM 응답이 모호하거나 범위를 벗어남**("NONE" 포함, 숫자 없음, 후보 범위 밖 숫자): 모두 보수적으로 null 처리한다 (`EventLLMDecisionService.java:126-138`).
- **동물 종을 식별할 수 없음**(`AnimalIdentity.species`가 null): cross-region 병합을 시도하지 않는다(보수적) (`EventCrossRegionService.java:142-147`).
- **광역 알림의 유형이 정보성이 아님**("기타"/null): 병합을 시도하지 않고 항상 신규 이벤트로 생성한다 (`EventClusteringService.java:640`, `646`, `664`).
- **regional-types CSV 파싱 실패**(잘못된 `유형:시간` 토큰): 해당 토큰만 건너뛰고 나머지는 정상 파싱한다 — 부분 실패가 전체를 막지 않는다 (`EventClusteringService.java:539-547`).
- **백필 중 알림 순서가 뒤섞임**(시간 역행 입력): `incrementOnMerge`의 `GREATEST(last_alert_at, :alertAt)` 연산으로 `last_alert_at`이 감소하지 않도록 방어한다 (`DisasterEventRepository.java:267-274`).

## 요구사항 *(필수)*

### 기능 요구사항

**공통 게이트 / 트리거**

- **FR-001**: 시스템은 재난문자 수집 스케줄러(`DisasterFetchScheduler`, 10분 주기 cron `0 0/10 * * * *`)가 새 알림을 저장한 직후, 알림마다 순서대로 번역 → FCM 알림 트리거 → `EventClusteringService.clusterNewAlert` → `EventCrossRegionService.linkCrossRegion`을 호출해야 한다(MUST) (`DisasterFetchScheduler.java:29-51`).
- **FR-002**: 시스템은 `clustering.enabled`(환경변수 `CLUSTERING_ENABLED`, 기본값 `false`)가 꺼져 있으면 `clusterNewAlert`를 완전한 no-op으로 만들어야 한다(MUST) (`EventClusteringService.java:62-63`, `137-140`; `application.yml:124`).
- **FR-003**: 시스템은 `clustering.cross-region.enabled`(환경변수 `CROSS_REGION_ENABLED`, 기본값 `false`)가 꺼져 있으면 `linkCrossRegion`을 no-op으로 만들어야 한다(MUST) (`EventCrossRegionService.java:48-49`, `72-74`).
- **FR-004**: 시스템은 `clustering.llm-fallback.enabled`(환경변수 `LLM_FALLBACK_ENABLED`, 기본값 `false`)가 꺼져 있으면 로컬 borderline LLM 폴백을 시도하지 않아야 한다(MUST) (`EventClusteringService.java:84-85`, `266-269`).

**임베딩 기반 로컬 클러스터링**

- **FR-005**: 시스템은 알림 임베딩이 이미 저장돼 있으면 재사용하고, 없을 때만 OpenAI Embedding API(`text-embedding-3-small`, 1536차원)를 호출해야 한다(MUST) — 재클러스터링(임계값 튜닝) 시 임베딩 비용이 재발생하지 않도록 함 (`EventClusteringService.java:201-208`; `application.yml:111-114`).
- **FR-006**: 시스템은 새 알림의 후보 이벤트를, 같은 시군구(법정동 코드 앞 5자리) 교집합을 갖고 `last_alert_at`이 `clustering.candidate-time-window-hours`(기본 168시간/7일) 이내이며 `is_broadcast=false`·`is_advisory=false`인 이벤트로 한정해 코사인 거리 오름차순 상위 3개까지 조회해야 한다(MUST) (`EventClusteringService.java:68-69`, `227-231`; `DisasterEventRepository.java:132-155`).
- **FR-007**: 시스템은 후보 중 코사인 거리가 `1.0 - clustering.similarity-threshold`(기본 `0.85` → 거리 `0.15`) 이하인 최우선 후보가 있으면 그 이벤트에 `MergeMethod.EMBEDDING`으로 병합해야 한다(MUST). 없으면 LLM 폴백을 시도한 뒤에도 실패하면 신규 이벤트를 생성해야 한다(MUST) (`EventClusteringService.java:65-66`, `237-254`).
- **FR-008**: 시스템은 알림에 유효한 법정동 지역 코드가 하나도 없으면 후보 검색 없이 즉시 신규 이벤트를 생성해야 한다(MUST) (`EventClusteringService.java:211-217`, `749-758`).

**광역 브로드캐스트**

- **FR-009**: 시스템은 알림이 걸친 distinct 시군구 수가 `clustering.max-region-span`(기본 10)을 초과하면 로컬 임베딩 경로 대신 브로드캐스트 경로(`clusterBroadcast`)로 분기해야 한다(MUST) (`EventClusteringService.java:72-73`, `221-225`).
- **FR-010**: 시스템은 브로드캐스트 알림이 걸친 distinct 시도 수가 `clustering.nationwide-sido-span`(기본 8) 이상이면 지역 무관 "전국 {유형}" 키(`primary_region_code=null`)로, 미만이면 "최다 시군구를 차지한 시도 + 유형" 키로 병합/생성해야 한다(MUST) (`EventClusteringService.java:75-77`, `638-678`).
- **FR-011**: 시스템은 `is_broadcast=true`인 이벤트를 로컬 임베딩 후보 검색과 지역앵커 유형 후보 검색에서 영구히 제외해야 한다(MUST) — broadcast와 local 이벤트가 서로 섞이지 않도록 함(플래그는 병합 후에도 유지) (`DisasterEventRepository.java:139`, `179`).
- **FR-012**: 시스템은 브로드캐스트 알림의 유형이 정보성이 아니면("기타"/null, `DisasterEvent.isInformativeType`이 false) 기존 broadcast 이벤트와 병합을 시도하지 않고 항상 신규 이벤트를 생성해야 한다(MUST) (`EventClusteringService.java:640`, `646-660`, `664-673`; `DisasterEvent.java:142-146`).

**전국 통합 유형(태풍 등)**

- **FR-013**: 시스템은 알림 유형이 `clustering.global-types`(기본 `"태풍"`, 쉼표 구분)에 속하면, 지역·임베딩과 무관하게 `clustering.global-window-hours`(기본 168시간) 이내의 `is_broadcast=true & primary_region_code IS NULL & 같은 유형` 이벤트에 `MergeMethod.GLOBAL_TYPE`으로 병합해야 한다(MUST). 없으면 `primary_region_code=null`, 제목 "전국"인 신규 broadcast 이벤트를 생성해야 한다(MUST) (`EventClusteringService.java:100-105`, `388-408`).
- **FR-014**: 시스템은 태풍 알림 본문에서 정규식(`제N호 태풍 [「『(''"]이름` 또는 `태풍 [「『(''"]이름`)으로 고유명을 추출할 수 있고, 이벤트 제목이 아직 기본값("전국 태풍")이면 "태풍 {이름}"으로 조건부 승급해야 한다(MUST). 이미 이름이 붙어 있으면 갱신하지 않아야 한다(MUST NOT) (`DisasterEvent.java:99-131`, `134-136`; `DisasterEventRepository.java:283-291`).

**지역앵커 유형(산불·산사태·홍수)**

- **FR-015**: 시스템은 알림 유형이 `clustering.regional-types`(기본 `"산불:336,산사태:168,홍수:168"`)에 매핑돼 있으면, 지역 정보가 있고 걸친 시군구 수가 `max-region-span` 이하인 경우, 같은 시군구·같은 유형·유형별 윈도우(산불 336시간/14일, 산사태·홍수 168시간/7일) 안의 `is_broadcast=false & is_advisory=false` 이벤트에 임베딩·LLM 없이 `MergeMethod.REGIONAL_TYPE`으로 병합해야 한다(MUST) (`EventClusteringService.java:113-114`, `452-490`; `DisasterEventRepository.java:176-196`).
- **FR-016**: 시스템은 알림 유형이 `clustering.advisory-split-types`(기본 `"산불"`, `regional-types`의 부분집합)에 속하고 `FireAlertClassifier.isAdvisory(message, emergencyLevel)`가 true이면, 사건 버킷이 아니라 같은 시군구·같은 유형·같은 윈도우의 `is_advisory=true` 롤링 안내 이벤트(제목 `"{지역명} {유형}예방안내"`)에 합류시켜야 한다(MUST) (`EventClusteringService.java:121-122`, `467-514`; `DisasterEventRepository.java:212-231`).
- **FR-017**: 시스템은 산불 알림을 다음 기준으로 사건(INCIDENT)/안내(ADVISORY)로 분류해야 한다(MUST): 긴급/위급재난문자(`DisasterLevel.LEVEL_2`/`LEVEL_3`)이거나, 강신호 키워드(`대피|진화|진압|완진|소진|주불|불길`)가 있으면 사건. 그 외에는 미세위치 패턴(`산N`, `N번지`, `{리/동/읍/면}+번지꼴`) + 화재 언급(`산불|화재`)이 있으면서 안내 조건문(`발생위험|예방|소각|건조특보|위기경보|위험지수` 등)이 없을 때만 사건으로 판정해야 한다(MUST) (`FireAlertClassifier.java:33-84`).

**실종 인물 신원 클러스터링**

- **FR-018**: 시스템은 `disaster_type='기타'`이고 본문에서 이름(`[가-힣]{2,4}씨`)과 나이(`\d{1,3}세`)를 모두 추출할 수 있으면, 임베딩/지역 로직을 건너뛰고 신원(이름+나이+키, 있으면 `\d{2,3}cm`) 기반 클러스터링을 수행해야 한다(MUST) (`EventClusteringService.java:352-375`; `MissingPersonIdentity.java:15-40`).
- **FR-019**: 시스템은 `clustering.person-window-hours`(기본 336시간/14일) 이내에 같은 이름+나이(+키)를 가진 이벤트가 있으면 시군구와 무관하게 `MergeMethod.IDENTITY`로 병합해야 한다(MUST). 키가 추출되지 않으면 이름+나이만으로 매칭해야 한다(MUST) (`EventClusteringService.java:80-81`, `361-368`; `DisasterEventRepository.java:436-459`).

**동물 등 비정형 이동 사건(cross-region)**

- **FR-020**: 시스템은 `disaster_type='기타'`이고 실종 인물이 아니며 `AnimalIdentity.species(message)`로 종이 식별될 때만 cross-region 병합을 시도해야 한다(MUST). 사전에 없는 종은 처리하지 않아야 한다(MUST NOT) (`EventCrossRegionService.java:92-101`; `AnimalIdentity.java:32-39`, `61-71`).
- **FR-021**: 시스템은 이미 2개 이상 시도에 걸친 이벤트에 속한 알림은 cross-region 재처리를 skip해야 한다(MUST) — 동일 알림에 대한 idempotent 재실행 보장 (`EventCrossRegionService.java:138-141`).
- **FR-022**: 시스템은 토착종(멧돼지·들개·뱀)은 시군구(코드 앞 5자) 인접, 그 외 이동종(늑대·사슴·곰·소)은 시도(코드 앞 2자) 인접을 게이트로 사용해 후보를 찾아야 한다(MUST). 인접 관계는 `region_adjacency` 테이블의 직접 매칭 1-hop만 사용한다(MUST) — 다단계(BFS) 전이 확장은 하지 않는다 (`EventCrossRegionService.java:157-174`; `AnimalIdentity.java:53`; `DisasterEventRepository.java:320-423`).
- **FR-023**: 시스템은 cross-region 후보를, 대상 알림의 `[created_at - candidate-window-hours, created_at + candidate-window-hours]`(기본 ±336시간) 양방향 시간 윈도우와 코사인 거리 `1.0 - similarity-floor`(기본 0.6) 이하로 제한하고, 같은 종의 알림을 가진 이벤트만(종 하드 게이트) 상위 `top-k`(기본 5)건 조회해야 한다(MUST) (`EventCrossRegionService.java:51-64`, `152-174`; `DisasterEventRepository.java:320-423`).
- **FR-024**: 시스템은 `EventLLMDecisionService.pickSameIncident`가 후보 중 하나를 "동일 개체"로 판정하면, 병합 후 알림 수가 `span-cap-alerts`(기본 30)를 초과하거나 걸친 시도 수가 `span-cap-sido`(기본 10) 이상이 되지 않는 한 두 이벤트를 `MergeMethod.LLM`으로 병합해야 한다(MUST) (`EventCrossRegionService.java:188-198`, `209-220`).

**사고성 사건 LLM 폴백**

- **FR-025**: 시스템은 `llm-fallback.enabled=true`이고 알림 유형이 `llm-fallback.accident-types`(기본 `"기타,화재,산불,붕괴,교통사고,교통통제,교통,환경오염사고,정전,통신,테러,지진,지진해일,수도"`)에 속하며 동물 키워드(`탈출|출몰|멧돼지|들개|늑대`)에 매칭되지 않을 때만 LLM 폴백을 시도해야 한다(MUST) (`EventClusteringService.java:91-93`, `266-269`).
- **FR-026**: 시스템은 코사인 거리가 `(mergeMaxDistance, llm-fallback.distance-ceil]`(기본 `(0.15, 0.40]`) 구간인 같은 지역 후보만 LLM 질의 대상으로 삼아야 한다(MUST). 후보 이벤트의 대표(seed) 유형도 사고성 화이트리스트에 속해야 한다(MUST) (`EventClusteringService.java:87-89`, `271-297`).
- **FR-027**: 시스템은 LLM이 후보를 "동일 사건"으로 지목하면 `MergeMethod.LLM_FALLBACK`으로 병합해야 한다(MUST). LLM 호출 실패, 응답에 "NONE" 포함, 응답에서 숫자를 못 찾음, 또는 응답 번호가 후보 범위를 벗어나면 모두 매칭 없음(null)으로 처리해 병합하지 않아야 한다(MUST) — 모호할 때는 병합하지 않는 보수적 정책 (`EventClusteringService.java:299-307`; `EventLLMDecisionService.java:115-140`).

**쿨다운 / 진행 중 상태**

- **FR-028**: 시스템은 이벤트 생성 시 `primary_disaster_type`으로부터 `cooldown_hours`를 1회 산정해 저장해야 한다(MUST): 산불·지진·지진해일·폭염·한파·전염병·가축질병·가뭄 = 168시간, 태풍·홍수·호우·대설·산사태·풍랑·황사·환경오염·미세먼지·에너지 = 72시간, 화재·붕괴·폭발·강풍·정전·수도·통신·금융·테러·민방공·교통사고·교통통제·교통·건조·안개 = 24시간, 그 외/null = 72시간(기본값) (`DisasterCooldown.java:23-41`; `DisasterEvent.java:191`, `219`).
- **FR-029**: 시스템은 이벤트의 "진행 중(active)" 여부를 컬럼으로 저장하지 않고, 조회 시점에 `now - last_alert_at < cooldown_hours`로 파생 계산해야 한다(MUST) (`DisasterEvent.java:274-276`; `DisasterEventRepository.java:18-49`, `80-97`).
- **FR-030**: 시스템은 이벤트에 새 알림이 병합될 때 `last_alert_at`을 `GREATEST(기존값, 새 알림 시각)`로만 갱신해야 한다(MUST) — 백필 등에서 시간이 역행하는 입력에도 `last_alert_at`이 감소하지 않도록 보장 (`DisasterEventRepository.java:267-274`).

**병합 기록 / 감사**

- **FR-031**: 시스템은 모든 병합/생성 결과를 `event_alert_mapping`에 `merge_method`(`MergeMethod` enum: `SEED`/`EMBEDDING`/`BROADCAST`/`IDENTITY`/`LLM`/`LLM_FALLBACK`/`GLOBAL_TYPE`/`REGIONAL_TYPE`/`ADVISORY`)로 기록해야 한다(MUST) — 특히 `LLM` 계열은 재구성 불가능한 일회성 판정이라 사후 검수·되돌리기를 위해 반드시 남겨야 한다 (`MergeMethod.java:9-46`).
- **FR-032**: 시스템은 이벤트가 병합/생성될 때마다 `AlertClusteredEvent`(Spring 애플리케이션 이벤트)를 발행해야 한다(MUST) — 위험도 계산 모듈(`ClusteringEventListener`)이 이를 트랜잭션 커밋 후 구독해 클러스터링과 위험도 계산을 분리한다 (`EventClusteringService.java:583`, `600`; `AlertClusteredEvent.java:9`).
- **FR-033**: 시스템은 신규 이벤트 제목을, 유형이 정보성이면 `"{지역명} {유형}"`, "기타"류로 정보 부족이면 `"{지역명} {본문 규칙 기반 라벨}"`(실종 인물/동물/안전안내 키워드 매칭, 그 외는 본문 앞 30자) 형태로 자동 생성해야 한다(MUST) (`DisasterEvent.java:148-181`, `225-256`).

**백필/재클러스터링 도구**

- **FR-034**: 시스템은 Spring Profile `backfill`이 활성화됐을 때만 `EventClusteringBackfillTool` 빈을 등록해야 한다(MUST) — 일반 dev/운영 부팅에서는 실행되지 않는다 (`EventClusteringBackfillTool.java:46-47`).
- **FR-035**: 시스템은 `--backfill.embed-only=true`일 때 임베딩이 없는 알림만 200건(`EMBED_BATCH_SIZE`) 단위 배치로 OpenAI 임베딩 API를 호출해 저장하고 클러스터링은 수행하지 않아야 한다(MUST) (`EventClusteringBackfillTool.java:57-58`, `129-160`).
- **FR-036**: 시스템은 `--backfill.recluster=true`일 때 이미 임베딩된 알림 전체를 `created_at ASC` 순으로 재처리하되 OpenAI 임베딩 API는 재호출하지 않아야 한다(MUST) (`EventClusteringBackfillTool.java:119-122`, `198-211`).
- **FR-037**: 시스템은 백필 도구가 알림 1건 처리 중 예외가 발생해도 나머지 알림 처리를 계속해야 한다(MUST) — 개별 실패를 errors 카운터로만 집계 (`EventClusteringBackfillTool.java:88-101`).

### 주요 엔티티 *(데이터가 관련된 경우 포함)*

- **DisasterEvent** (`disaster_events` 테이블): 시간 흐름에 따라 N개 재난문자가 누적된 사건 단위. `primaryDisasterType`/`primaryRegionCode`/`primaryRegionName`은 첫 알림 기준 캐시, `eventTitle`은 자동 생성. `cooldownHours`는 생성 시 1회 산정 후 불변, `active`는 저장하지 않고 파생 계산. `broadcast`/`advisory` 플래그로 로컬 사건 버킷과 영구히 분리 (`DisasterEvent.java`).
- **EventAlertMapping** (`event_alert_mapping` 테이블): 하나의 `DisasterEvent`에 어떤 `DisasterAlert`가 몇 번째(`sequence_no`)로, 어떤 방식(`merge_method`)으로, 어떤 유사도(`similarity`, nullable)로 합류했는지 기록. LLM 병합의 유일한 재구성 근거 (`EventAlertMapping.java`, `MergeMethod.java`).
- **DisasterAlert** (외부 도메인, `disaster_alert` 테이블): 원본 재난문자. `embedding`(pgvector, 1536차원), `disasterType`, `message`, `disasterAlertRegions`(발송 대상 법정동 코드 목록)를 클러스터링이 소비.
- **MergeMethod** (enum): 알림이 이벤트에 합류한 방식 표식 — `SEED`(첫 알림)/`EMBEDDING`(코사인 유사도)/`BROADCAST`(광역)/`IDENTITY`(인물 신원)/`LLM`(cross-region 판정)/`LLM_FALLBACK`(사고성 borderline)/`GLOBAL_TYPE`(태풍 등 전국유형)/`REGIONAL_TYPE`(산불 등 지역앵커유형)/`ADVISORY`(안내성 롤링).
- **DisasterCooldown** (정적 규칙 테이블, 엔티티 아님): 재난 유형 문자열 → cooldown 시간(hour)의 고정 매핑. `DisasterEvent.cooldownHours` 산정에만 쓰이는 순수 룩업 로직 (`DisasterCooldown.java`).

## 성공 기준 *(필수)*

### 측정 가능한 결과

- **운영 지표 미수집**: 이 파이프라인에 대한 정량적 운영 지표(병합 정확도, 파편화율, false-merge율 등)를 수집·집계하는 코드나 대시보드는 코드베이스 내에 존재하지 않는다. 소스에 남은 근거는 개발 과정에서 수동으로 확인한 실측 사례(예: "산청 산불 16조각", "산불 6,639건 중 안내 ~5,658/사건 ~981", "동명이인 임베딩 0.897 > 동일인 0.834")에 대한 코드 주석뿐이며, 이는 튜닝 근거 기록이지 지속적으로 수집되는 운영 지표가 아니다. 따라서 SC-001 이하는 **수치 목표를 임의로 만들지 않고, 코드가 실제로 보장하는 정성적 동작 기준**으로 기술한다.
- **SC-001 (정성)**: `clustering.enabled=true`인 환경에서, 같은 시군구·7일 이내·코사인 유사도 0.85 이상인 재난문자는 예외 없이 하나의 이벤트로 병합된다(코드 경로상 보장, 운영 성공률 측정치 없음).
- **SC-002 (정성)**: 산불·산사태·홍수 알림은 지역앵커 유형 경로에 의해 본문 텍스트 차이와 무관하게 같은 시군구·유형·윈도우 안에서 항상 하나의 이벤트로 유지된다(코드 경로상 보장).
- **SC-003 (정성)**: LLM 판정이 개입하는 모든 경로(`LLM`, `LLM_FALLBACK`)는 실패·모호·범위 초과 시 예외 없이 병합하지 않는(false) 쪽으로 수렴한다 — 즉 "모르면 합치지 않는다"는 보수적 정책이 코드 전 경로에서 일관되게 적용된다.
- **SC-004 (정성)**: 파이프라인의 모든 진입점(`clusterNewAlert`, `linkCrossRegion`, 백필 도구)은 알림 1건의 처리 실패가 나머지 알림이나 스케줄러 사이클 전체의 실행을 막지 않는다(각 진입점에서 try/catch로 격리).

## 가정

- **환경변수 플래그가 기본적으로 모두 꺼져 있다**: `CLUSTERING_ENABLED`/`LLM_FALLBACK_ENABLED`/`CROSS_REGION_ENABLED`는 코드상 기본값이 모두 `false`다(`application.yml:124`, `149`, `156`). 이 문서는 각 경로가 **켜져 있다고 가정했을 때**의 동작을 서술하며, 실제 운영/개발 환경에서 어떤 플래그가 켜져 있는지는 이 문서 작성 시점에 별도로 확인하지 않았다 — 특정 경로가 실제로 실행 중이라고 가정하기 전에 대상 환경의 값을 확인해야 한다.
- **자동 로컬 병합은 항상 동일 시군구(코드 앞 5자리) 내에서만 발생한다.** 인접 시군구로의 확산은 오직 cross-region 동물 케이스(`EventCrossRegionService`)에서 `region_adjacency` 테이블의 1-hop 직접 인접만으로 이루어지며, 산불·지진 등 일반 재난 유형에는 인접 확산(다단계 BFS 등) 로직이 전혀 없다. (아래 "발견된 문서-코드 불일치" 참고.)
- **인물(실종)과 태풍/지역앵커 유형은 임베딩을 전혀 쓰지 않는다.** 신원/유형 자체가 결정적 키이므로 재클러스터링 시 OpenAI 비용이 들지 않는다.
- **`ClusteringProperties`라는 단일 `@ConfigurationProperties` 바인딩 클래스는 존재하지 않는다.** `clustering.*` 설정은 `EventClusteringService`와 `EventCrossRegionService`에 개별 `@Value` 필드로 흩어져 바인딩돼 있다. (아래 "발견된 문서-코드 불일치" 참고.)
- **`EventFragmentMergeService`(30분 주기 파편 이벤트 정합화 스케줄러)는 코드베이스에 존재하지 않는다.** `git log --all`로 전체 히스토리를 확인해도 해당 이름의 파일이 추가된 이력이 없다. `application.yml`의 Sentry 섹션 주석(`application.yml:165`)에 "fragment-merge"라는 표현이 남아 있을 뿐 실제 서비스/스케줄러/설정 키(`FRAGMENT_MERGE_ENABLED`, `incident-specific-check`)는 어디에도 없다. (아래 "발견된 문서-코드 불일치" 참고.)
- **계절성 안전안내의 쿨다운 억제는 "쿨다운 갱신을 건너뛰는" 방식이 아니라 "안내성 알림을 아예 별도의 이벤트 버킷(`is_advisory=true`)으로 분리"하는 방식으로 구현돼 있다.** 안내 이벤트 자체는 자신의 `cooldownHours`를 정상적으로 가지며(`DisasterEvent.java:219`), 다만 사건(incident) 이벤트의 `last_alert_at`을 안내성 알림이 갱신하지 않을 뿐이다. 현재 이 분리는 산불(`clustering.advisory-split-types`)에만 적용돼 있다.
- **`clustering.cross-region.mover-keywords` 설정 키는 `application.yml`에 정의돼 있지만 백엔드 어떤 코드에서도 읽지 않는다(dead config).** 실제 동물 게이트는 `AnimalIdentity.species()` 사전 매칭이 담당한다.
- **`DisasterEvent.recordMergedAlert()` 엔티티 메서드는 정의돼 있지만 어디서도 호출되지 않는다(dead code).** 실제 `last_alert_at`/`alert_count` 갱신은 `DisasterEventRepository.incrementOnMerge()`의 네이티브 SQL `UPDATE`가 전담한다. (plan.md 헌법 검사 참고.)

### 발견된 문서-코드 불일치 (정직한 문서화 원칙에 따른 기록)

`CLAUDE.md`는 이 파이프라인을 설명하며 다음을 언급하지만, 실제 코드베이스(`git log --all` 전체 이력 포함)에서 확인되지 않았다:

1. **`EventFragmentMergeService`** — "30분 주기 스케줄러로 동작하는 정합화 패스, 작은 파편 이벤트를 흡수" 라고 설명되지만 해당 클래스/파일이 존재한 적이 없다.
2. **`FRAGMENT_MERGE_ENABLED`, `INCIDENT_SPECIFIC_CHECK_ENABLED` 환경변수 및 `incident-specific-check` 설정** — `application.yml`, Java 소스 어디에도 없다.
3. **`ClusteringProperties` 바인딩 클래스** — 존재하지 않으며, 설정은 `EventClusteringService`/`EventCrossRegionService`의 개별 `@Value` 필드로 흩어져 있다.
4. **"산불/지진 등 인접 시군구 BFS 확산 hop" 설정** — 이 표현이 맞는 곳은 클러스터링이 아니라 **위험도(risk) 모듈**의 `RiskCalculationService`/`RegionAdjacencyRepository`다. 클러스터링 쪽 `region_adjacency` 사용(`EventCrossRegionService`)은 다단계 BFS가 아니라 1-hop 직접 인접 매칭뿐이다.

이 항목들은 이번 명세 작성 과정에서 소스 코드 대조를 통해 발견됐으며, 추측이 아니라 실제 부재를 확인한 것이다(`git log --all --diff-filter=A --name-only | grep -i fragment` 결과 없음, `grep -r ClusteringProperties` 결과 없음).
