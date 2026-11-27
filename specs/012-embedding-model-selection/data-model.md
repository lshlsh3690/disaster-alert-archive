# 데이터 모델: 클러스터링용 임베딩 모델 선정

**명세**: [spec.md](spec.md) | **결정 기록**: [research.md](research.md)

이 기능은 운영 DB 스키마를 바꾸지 않는다. 새로 생기는 것은 실험 결과 파일과 로컬 평가 사본뿐이다.

## 1. 입력

| 이름 | 위치 | 내용 |
|---|---|---|
| 라벨 셋 | 평가 사본 DB `evaluation_pair_label`(`dataset='cluster-eval-2026-11'`) | 272쌍. `label`(SAME/DIFF/UNSURE), `relation`(SAME_SIGUNGU/ADJACENT_SIGUNGU). 두 알림의 유형·본문은 `disaster_alert` 에서 읽는다 |
| 유형 목록 | `backend/src/main/resources/application.yml` | `clustering.regional-types`, `clustering.sustained-types`(R1 대상 고르기) |
| 기준선 저장 거리 | 평가 사본 `disaster_alert_eval_v6` | 대상 쌍마다 `embedding <=> embedding`(읽기 전용 SQL 로 CSV 추출) |

## 3. 2단계 평가 사본 (로컬 Docker Postgres, 커밋하지 않음)

| 사본 | 만드는 법 | 임베딩 |
|---|---|---|
| `disaster_alert_eval_v6` | 기존(호우 튜닝 반영) | `3-small` 1536, 그대로 둠 |
| `disaster_alert_eval_e_3small` | v6 템플릿 복제 → 재클러스터링 | `3-small` 그대로(기준선 재실행·흔들림, R8·R10) |
| `disaster_alert_eval_e_<별칭>` | v6 템플릿 복제 → 임베딩 교체(보관본 또는 채우기) → 재클러스터링 | 후보 모델, 모든 행 채움(R6·R10) |

**보관 DB `embedding_store`** (R10, 커밋하지 않음): 표 `alert_embedding(alert_id bigint, model text, dimensions int, embedding vector, created_at timestamptz, primary key (alert_id, model))`. 후보 4개 × 62,554건. 처음 채운 사본에서 `COPY` 로 내보내고, 다시 쓸 때 가져온다.

- 1024차원 사본은 컬럼을 `vector(1024)` 로 바꾸고 HNSW 인덱스를 다시 만든다(R6).
- 사본마다 쓴 임베딩 설정(모델·차원), 가상 키 별칭과 그 키의 실제 비용을 리포트 옆 기록에 적는다. 거리 설정은 모든 사본이 운영 값 그대로다(R10).

## 4. 2단계 결과 (커밋)

- 사본마다 평가 리포트 `backend/evaluation/reports/2026-11_<날짜>-embed-<별칭>.md` — 기존 `EvaluationRunTool` 출력 그대로(맞게 묶은 비율·빠짐없이 묶은 비율·점수, 쌍 목록).
- 2026-05 B-cubed 점수도 같은 리포트에 붙인다(`--eval.bcubed`).

## 5. 선정 기록 `specs/012-embedding-model-selection/decision.md` (커밋)

- 모델별 한 줄: 재클러스터링 점수와 흔들림 대비 차이, 1회 전환 비용, 매일 비용, 스키마 변경, 운영 부담(research R9 항목).
- 판정(spec FR-014: 사용자가 표를 보고 정함)과 그 이유를 숫자로.
- 버린 후보마다 이유 한 줄.

## 6. 바뀌는 운영 코드

| 대상 | 변경 | 운영 영향 |
|---|---|---|
| `EventClusteringBackfillTool` | `embed-only` 묶음 200 → 500 (2026-11-27 사용자 결정) | 없음(수동 실행 도구) |

운영 임베딩 모델·운영 DB·`application.yml` 의 운영 값은 바꾸지 않는다(spec FR-018).
