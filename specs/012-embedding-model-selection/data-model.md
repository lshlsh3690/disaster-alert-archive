# 데이터 모델: 클러스터링용 임베딩 모델 선정

**명세**: [spec.md](spec.md) | **결정 기록**: [research.md](research.md)

이 기능은 운영 DB 스키마를 바꾸지 않는다. 새로 생기는 것은 실험 결과 파일과 로컬 평가 사본뿐이다.

## 1. 입력

| 이름 | 위치 | 내용 |
|---|---|---|
| 라벨 셋 | `backend/evaluation/datasets/cluster-eval-2026-11.json` | 272쌍. 쌍마다 `label`(SAME/DIFF/UNSURE), `relation`(같은 시군구/이웃 시군구/…), 두 알림의 `id`·`type`·`message` |
| 유형 목록 | `backend/src/main/resources/application.yml` | `clustering.regional-types`, `clustering.sustained-types`(R1 대상 고르기) |
| 기준선 저장 거리 | 평가 사본 `disaster_alert_eval_v6` | 대상 쌍마다 `embedding <=> embedding`(읽기 전용 SQL 로 CSV 추출) |

## 2. 1단계 결과 (측정 모드 출력, 저장소에 커밋)

폴더 `backend/evaluation/reports/embedding-pairs-<날짜>/`.

### 모델별 거리 파일 `embedding-pairs-<별칭>.csv`

| 열 | 설명 |
|---|---|
| `alert_a`, `alert_b` | 알림 id(작은 쪽이 a) |
| `label` | SAME / DIFF |
| `type` | 같은 유형이면 그 유형, 다르면 가나다순으로 이은 것 |
| `relation` | 같은 시군구 / 이웃 시군구 |
| `distance` | 코사인 거리 |

별칭: `stored`(평가 사본에 저장된 `3-small` 임베딩), `3small`, `3large`, `qwen3_06b`, `e5_large_instruct`, `bge_m3_ko`. 머리 주석 줄에 모델 이름·주소·차원·지시문·실행 시각·실제 토큰을 적는다.

### 요약 리포트 `embedding-pairs-summary.md`

- 머리: 대상 쌍 수(같은 사건 N·다른 사건 M, 화재 따로), 제외 쌍 수와 이유, 모델별 실제 토큰·비용.
- 기준선 재현: `3small` 과 `stored` 의 쌍별 차이 최댓값(SC-002, 0.01 이하여야 함).
- 모델별 표(R2): 바로 병합 기준 거리와 그때 다른 사건 n/56·같은 사건 m/81·화재 같은 사건·화재 다른 사건. LLM 후보 기준도 같은 열.
- 바로 병합 기준을 정한 다른 사건 쌍 두 개의 원문.
- 기준선 대비 바뀐 같은 사건 쌍(새로 기준 안 / 새로 기준 밖)과 두 알림 원문(FR-006).

### 순수 클래스

- `EmbeddingPairSubset`: 입력은 라벨 쌍(라벨·관계·두 유형)과 키 경로 유형 목록, 출력은 대상 쌍과 이유별 제외 수(R1).
- `EmbeddingPairComparison`: 입력은 기준선·후보 거리 목록과 기준선 지점(0.15, 0.40), 출력은 모델별 두 지점 기준 거리·쌍 수·바뀐 쌍(R2). K번째와 K+1번째 다른 사건 거리의 가운데 값을 기준으로 삼는다. 거리가 같은 쌍이 경계에 걸리면 리포트에 그 사실을 적는다.

## 3. 2단계 평가 사본 (로컬 Docker Postgres, 커밋하지 않음)

| 사본 | 만드는 법 | 임베딩 |
|---|---|---|
| `disaster_alert_eval_v6` | 기존(호우 튜닝 반영) | `3-small` 1536, 그대로 둠 |
| `disaster_alert_eval_v6_rerun` | v6 템플릿 복제 → 재클러스터링 | `3-small` 그대로(흔들림 확인, R8) |
| `disaster_alert_eval_e_<별칭>` | v6 템플릿 복제 → 임베딩 교체 → 재클러스터링 | 후보 모델, 모든 행 채움(R6) |

- 1024차원 사본은 컬럼을 `vector(1024)` 로 바꾸고 HNSW 인덱스를 다시 만든다(R6).
- 사본마다 쓴 임베딩 설정(주소·모델·차원)과 재클러스터링 때 넘긴 거리 설정 3개(`merge-max-distance`, `llm-fallback.distance-ceil`, `cross-region.similarity-floor`)를 리포트 머리에 적는다.

## 4. 2단계 결과 (커밋)

- 사본마다 평가 리포트 `backend/evaluation/reports/2026-11_<날짜>-embed-<별칭>.md` — 기존 `EvaluationRunTool` 출력 그대로(맞게 묶은 비율·빠짐없이 묶은 비율·점수, 쌍 목록).
- 2026-05 B-cubed 점수도 같은 리포트에 붙인다(`--eval.bcubed`).

## 5. 선정 기록 `specs/012-embedding-model-selection/decision.md` (커밋)

- 후보별 한 줄: 1단계 두 지점 값, 2단계 점수(했으면), 1회 전환 비용, 매일 비용, 스키마 변경, 운영 부담(research R9 항목).
- 판정(spec FR-014: 사용자가 표를 보고 정함)과 그 이유를 숫자로.
- 버린 후보마다 이유 한 줄.

## 6. 바뀌는 운영 코드

| 대상 | 변경 | 운영 영향 |
|---|---|---|
| `EventClusteringService` | 병합 거리 0.15 를 `clustering.merge-max-distance`(기본 0.15) 설정으로 | 없음(기본값 같음) |
| `EvaluationRunTool` + 새 `evaluation` 클래스 3개 | 1단계 측정 모드(`eval` 프로필 전용) | 없음(운영 프로필에서 뜨지 않음) |
| `CLAUDE.md` 이벤트 클러스터링 절 | "0.15 는 설정이 아니라 코드에 고정" 문구 갱신 | 없음 |

운영 임베딩 모델·운영 DB·`application.yml` 의 운영 값은 바꾸지 않는다(spec FR-018).
