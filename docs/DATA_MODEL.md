# 데이터 모델 (DATA_MODEL) — Disaster Alert Archive

> JPA 엔티티(`backend/src/main/java/.../domain/*/model/`)와 Flyway 마이그레이션(`backend/src/main/resources/db/migration/`)을 직접 대조해 작성했다. DB는 PostgreSQL + pgvector 확장을 사용하며, 스키마 변경은 전부 Flyway 마이그레이션 파일로 관리된다(`ddl-auto: validate`).

## 1. 도메인별 테이블

### 1.1 회원 (member)

**`member`**
| 컬럼 | 타입 | 비고 |
|---|---|---|
| member_id (PK) | BIGINT | IDENTITY |
| email | VARCHAR | UNIQUE, NOT NULL |
| password | VARCHAR | NOT NULL |
| nickname | VARCHAR | NOT NULL |
| role | VARCHAR | ENUM(`USER`,`ADMIN`) |
| is_deleted | BOOLEAN | 기본 false — 소프트 삭제, JPA 조회 시 `is_deleted=false` 자동 필터(`@Where`) |
| created_at / updated_at | TIMESTAMP | |

**`member_social`** — 소셜 로그인 연동 정보
| 컬럼 | 타입 | 비고 |
|---|---|---|
| member_social_id (PK) | BIGINT | |
| member_id (FK → member) | BIGINT | NOT NULL |
| provider | VARCHAR(20) | |
| provider_user_id | VARCHAR(100) | |
| email_from_provider | VARCHAR | |
| created_at | TIMESTAMP | |

UNIQUE(`provider`, `provider_user_id`)

**`member_favorite_region`** — 관심 지역 (복합키)
| 컬럼 | 타입 | 비고 |
|---|---|---|
| member_id (PK, FK → member) | BIGINT | |
| legal_district_code (PK, FK → legal_district) | VARCHAR | 시/도 전체 등록 시 구 단위가 "00000000"으로 채워진 코드 저장 |
| created_at | TIMESTAMP | |

### 1.2 재난 문자 (disasteralert)

**`disaster_alert`**
| 컬럼 | 타입 | 비고 |
|---|---|---|
| disaster_alert_id (PK) | BIGINT | SEQUENCE(allocationSize=500) |
| sn | BIGINT | UNIQUE, NOT NULL — 원본 API의 일련번호 |
| message | TEXT | |
| created_at / modified_date | TIMESTAMP | |
| emergency_level | VARCHAR | ENUM(`LEVEL_1`=안전안내, `LEVEL_2`=긴급재난, `LEVEL_3`=위급재난) |
| disaster_type | VARCHAR | |
| original_region | VARCHAR(1000) | |
| **embedding** | **VECTOR(512)** | ⚠️ V30 마이그레이션이 추가한 컬럼(HNSW 코사인 인덱스, OpenAI text-embedding-3-small 512차원). **JPA 엔티티(`DisasterAlert.java`)에는 매핑되어 있지 않음** — 이벤트 클러스터링 로직에서 네이티브 쿼리로만 다루는 것으로 추정됨 |

**`disaster_alert_region`** (복합키) — 알림 1건이 걸치는 법정동 목록
| 컬럼 | 타입 | 비고 |
|---|---|---|
| disaster_alert_id (PK, FK) | BIGINT | |
| legal_district_code (PK, FK) | VARCHAR | |

**`disaster_alert_translation`** (복합키: alert_id + language_code)
| 컬럼 | 타입 | 비고 |
|---|---|---|
| disaster_alert_id (PK, FK) | BIGINT | |
| language_code (PK) | VARCHAR | |
| translated_message | TEXT | NOT NULL |
| translated_disaster_type | VARCHAR | |
| translated_region_names | TEXT | |
| translated_at | TIMESTAMP | |

### 1.3 이벤트 클러스터링 (event)

**`disaster_events`**
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id (PK) | BIGINT | IDENTITY |
| event_title | VARCHAR(200) | |
| primary_disaster_type | VARCHAR(50) | |
| primary_region_code | VARCHAR(20) | |
| primary_region_name | VARCHAR(100) | |
| first_alert_at / last_alert_at | TIMESTAMP | |
| alert_count | INT | |
| cooldown_hours | INT | 이후 마이그레이션에서 추가 |
| is_broadcast | BOOLEAN | 이후 마이그레이션에서 추가 — 광역 브로드캐스트 이벤트 여부 |
| is_advisory | BOOLEAN | 이후 마이그레이션에서 추가 — 안내성(일반 안전안내) 이벤트 여부 |
| created_at | TIMESTAMP | |

**`event_alert_mapping`** (복합키: event_id + alert_id, alert_id는 UNIQUE — 알림 1건은 이벤트 1개에만 속함)
| 컬럼 | 타입 | 비고 |
|---|---|---|
| event_id (PK, FK, CASCADE) | BIGINT | |
| alert_id (PK, FK, CASCADE) | BIGINT | UNIQUE |
| sequence_no | INT | |
| added_at | TIMESTAMP | |
| similarity_score | DOUBLE | 이후 마이그레이션에서 추가 |
| merge_method | VARCHAR(16) | 이후 마이그레이션에서 추가 — ENUM(`SEED`,`EMBEDDING`,`BROADCAST`,`IDENTITY`,`LLM`,`LLM_FALLBACK`,`GLOBAL_TYPE`,`REGIONAL_TYPE`,`ADVISORY`) |

**`disaster_event_translation`** (복합키: disaster_event_id + language_code)
| 컬럼 | 타입 | 비고 |
|---|---|---|
| disaster_event_id (PK) | BIGINT | |
| language_code (PK) | VARCHAR | |
| translated_title | TEXT | NOT NULL |
| translated_at | TIMESTAMP | |

> `AnimalIdentity`, `DisasterCooldown`, `FireAlertClassifier`, `MissingPersonIdentity`(event 패키지)는 테이블이 없는 순수 로직 클래스(정규식/판별 유틸)다.

### 1.4 법정동 (legaldistrict)

**`legal_district`**
| 컬럼 | 타입 | 비고 |
|---|---|---|
| code (PK) | VARCHAR(10) | 1-2자리 시/도, 3-5자리 시군구, 6-8자리 읍면동, 9-10자리 리 |
| name | VARCHAR | NOT NULL |
| is_active | BOOLEAN | NOT NULL |
| is_active_string | VARCHAR | NOT NULL |

**`legal_district_translation`** (복합키: code + language_code)
| 컬럼 | 타입 | 비고 |
|---|---|---|
| code (PK, FK) | VARCHAR | |
| language_code (PK) | VARCHAR | |
| name | VARCHAR | NOT NULL |

### 1.5 재난 제보 (useralert) / 커뮤니티(community) / 댓글(comment)

**`user_disaster_alert`** — 사용자 작성 재난 제보
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id (PK) | BIGINT | IDENTITY |
| created_by_id | BIGINT | Member ID (FK 매핑 없이 값만 저장) |
| title | VARCHAR(120) | NOT NULL |
| message | VARCHAR(300) | NOT NULL |
| disaster_type | VARCHAR(50) | |
| disaster_level | VARCHAR | ENUM(`DisasterLevel`) |
| occurred_at | TIMESTAMP | NOT NULL |
| created_at / modified_at | TIMESTAMP | |
| is_deleted | BOOLEAN | 기본 false |

**`user_disaster_alert_region`** (복합키)
| 컬럼 | 타입 | 비고 |
|---|---|---|
| user_disaster_alert_id (PK, FK) | BIGINT | |
| legal_district_code (PK, FK) | VARCHAR(10) | |

**`community_post`**
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id (PK) | BIGINT | IDENTITY |
| category | VARCHAR | ENUM(`NOTICE`,`FREE`) |
| member_id (FK) | BIGINT | NOT NULL |
| title | VARCHAR(120) | NOT NULL |
| content | VARCHAR(4000) | NOT NULL |
| created_at / updated_at | TIMESTAMP | |
| is_deleted | BOOLEAN | 기본 false |

**`comment`** — 재난 문자·사용자 제보 공용 댓글
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id (PK) | BIGINT | IDENTITY |
| content | VARCHAR(500) | NOT NULL |
| member_id (FK, 작성자) | BIGINT | NOT NULL |
| disaster_alert_id (FK, nullable) | BIGINT | 공식 재난 문자 댓글인 경우 |
| user_disaster_alert_id (FK, nullable) | BIGINT | 사용자 제보 댓글인 경우 |
| is_deleted | BOOLEAN | 기본 false |
| created_at / updated_at | TIMESTAMP | |

> **PRD 5.7 사용자 제재 관련**: 위 세 테이블 모두 소프트 삭제(`is_deleted`)만 있고, "신고됨"/"제재됨" 같은 상태 컬럼은 없다. 관리자 제재 기능 구현 시 `member`에 계정 상태 컬럼을 추가하는 것과 별개로, 콘텐츠 자체의 숨김 사유를 남기려면 이 테이블들에도 컬럼 추가가 필요할 수 있다.

### 1.6 실종자 (missingperson)

**`missing_child`**
| 컬럼 | 타입 | 비고 |
|---|---|---|
| msspsn_idntfccd (PK) | BIGINT | 외부(재난 문자) 식별자를 그대로 사용, AUTO_INCREMENT 아님 |
| name | VARCHAR(50) | |
| age | INT | |
| gender | VARCHAR(10) | |
| target_type_code | VARCHAR(10) | |
| address | VARCHAR(200) | |
| special_feature | VARCHAR(500) | |
| photo_base64 | TEXT | |
| photo_size | INT | |

> 별도 FK 없이 재난 문자에서 추출한 정보를 그대로 적재하는 구조 — PRD/TRD에 명시한 대로 경찰청 등 외부 API 연동은 없음.

### 1.7 알림 (notification)

**`fcm_token`**
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id (PK) | BIGINT | IDENTITY |
| member_id (FK, nullable) | BIGINT | 게스트는 별도 테이블 사용 |
| token | TEXT | NOT NULL |
| device_type | VARCHAR | NOT NULL |
| created_at / updated_at | TIMESTAMP | |

**`guest_fcm_region`** — 비로그인 사용자의 관심 지역별 FCM 토큰
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id (PK) | BIGINT | IDENTITY |
| fcm_token | TEXT | NOT NULL |
| legal_district_code | VARCHAR(10) | NOT NULL, FK 관계 미선언(단순 문자열) |
| created_at | TIMESTAMP | |

**`notification_log`** — 발송 로그(회원)
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id (PK) | BIGINT | IDENTITY |
| member_id (FK) | BIGINT | NOT NULL |
| disaster_alert_id (FK) | BIGINT | NOT NULL |
| is_read | BOOLEAN | NOT NULL |
| sent_at | TIMESTAMP | NOT NULL |

**`user_notification_log`** — 발송 성공/실패 로그
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id (PK) | BIGINT | IDENTITY |
| member_id | BIGINT | NOT NULL(FK 매핑 없음) |
| alert_id (FK) | BIGINT | NOT NULL |
| status | VARCHAR | `SENT` / `FAILED` |
| notification_type | VARCHAR | Enum 아닌 단순 문자열 |
| is_read | BOOLEAN | 기본 false |
| created_at | TIMESTAMP | |

**`notification_preference`**
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id (PK) | BIGINT | IDENTITY |
| member_id (FK) | BIGINT | UNIQUE, NOT NULL — 회원당 1행 |
| notification_type | VARCHAR | ENUM(`NONE`,`PUSH`,`ALARM`), 기본 `PUSH` |
| min_risk_score | INT | 기본 0 |
| created_at / updated_at | TIMESTAMP | |

### 1.8 공개 API (openapi)

**`open_api_token`**
| 컬럼 | 타입 | 비고 |
|---|---|---|
| open_api_token_id (PK) | BIGINT | IDENTITY |
| member_id (FK) | BIGINT | NOT NULL |
| name | VARCHAR(100) | NOT NULL |
| token_hash | VARCHAR(64) | UNIQUE, NOT NULL — SHA-256 해시, 원문 토큰은 저장하지 않음 |
| token_prefix | VARCHAR(20) | NOT NULL — 목록 표시용 |
| expires_at | TIMESTAMP | NOT NULL |
| last_used_at | TIMESTAMP | nullable |
| call_count | BIGINT | 기본 0 |
| revoked_at | TIMESTAMP | nullable, null이면 미폐기 |
| created_at | TIMESTAMP | |

### 1.9 위험도 (risk)

**`disaster_risk_profile`** — 재난 유형별 위험도 계산 파라미터
| 컬럼 | 타입 | 비고 |
|---|---|---|
| disaster_type (PK) | VARCHAR(50) | |
| base_weight | DOUBLE | |
| half_life_hours | INT | 시간 감쇠 반감기 |
| spread_coeff | DOUBLE | 기본 0.3 |
| is_llm_generated | BOOLEAN | LLM이 자동 생성한 프로파일인지 |
| operator_confirmed | BOOLEAN | 운영자 확인 여부 |
| updated_at | TIMESTAMP | |

**`event_region_impact`** (복합키: event_id + region_code)
| 컬럼 | 타입 | 비고 |
|---|---|---|
| event_id (PK) | BIGINT | |
| region_code (PK) | VARCHAR(20) | |
| impact_score | DOUBLE | |
| created_at | TIMESTAMP | |

**`intensity_bracket`** — 재난 강도 구간별 배율표
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id (PK) | BIGINT | IDENTITY |
| disaster_type | VARCHAR(50) | |
| min_value / max_value | DOUBLE | |
| unit | VARCHAR(20) | |
| multiplier | DOUBLE | |
| label | VARCHAR(50) | |

**`region_adjacency`** (복합키: region_code + neighbor_code) — 인접 시군구 쌍(양방향 저장)

**`region_risk_daily`** (복합키: region_code + snapshot_date) — 일별 위험도 스냅샷

**`region_risk_history`** (복합키: region_code + snapshot_at) — 시간별 위험도 스냅샷(0 초과 지역만, 추후 시계열 예측 입력 용도)

**`region_risk_index`** — 지역별 현재 위험도(전파 반영 완료 상태)
| 컬럼 | 타입 | 비고 |
|---|---|---|
| region_code (PK) | VARCHAR(20) | |
| risk_score | DOUBLE | 인접 지역 전파가 반영된 최종 점수 |
| top_event_id | BIGINT | |
| source_score | DOUBLE | 전파 반영 전, 해당 지역 직접 영향분 |
| source_top_event_id | BIGINT | |
| updated_at | TIMESTAMP | |

> `RiskScore`는 테이블이 아니라 점수 계산 로직을 캡슐화한 자바 `record` 값 객체다.

### 1.10 기상 (weather)

**`weather_observation`** — 시간 단위 관측값 (UNIQUE: legal_district_code + observed_at)
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id (PK) | BIGINT | IDENTITY |
| legal_district_code (FK) | VARCHAR(10) | |
| observed_at | TIMESTAMP | |
| temperature, precipitation, wind_speed, wind_direction, humidity, pressure | DOUBLE/INT | nullable |
| source | VARCHAR(20) | ENUM(`ASOS_HISTORY`,`KMA_FORECAST`,`KMA_NOWCAST`) |
| created_at | TIMESTAMP | |

**`weather_daily_summary`** — 일별 집계 (UNIQUE: legal_district_code + date)
| 컬럼 | 타입 | 비고 |
|---|---|---|
| id (PK) | BIGINT | IDENTITY |
| legal_district_code (FK) | VARCHAR(10) | |
| date | DATE | |
| avg/min/max_temp, total_precip, max_hourly_precip, precip_hours, avg/max_wind_speed, avg/min_humidity, avg_pressure | DOUBLE/INT | |
| obs_count | INT | |
| created_at | TIMESTAMP | |

**`weather_station_mapping`** — 법정동 ↔ 관측소 매핑
| 컬럼 | 타입 | 비고 |
|---|---|---|
| legal_district_code (PK, FK) | VARCHAR(10) | |
| asos_station_id / asos_station_name | VARCHAR | ASOS(종관기상관측) 지점 |
| kma_nx / kma_ny | INT | 기상청 격자 좌표(초단기실황 API용) |

## 2. 주요 관계 요약

- `member` 1—N `member_favorite_region` N—1 `legal_district`
- `member` 1—N `member_social` (소셜 계정 연동)
- `disaster_alert` 1—N `disaster_alert_region` N—1 `legal_district`
- `disaster_alert` 1—N `disaster_alert_translation`, 1—N `comment`, 1—N `notification_log`
- `disaster_event` 1—N `event_alert_mapping` N—1 `disaster_alert` (alert_id는 UNIQUE — 다대다처럼 보이지만 실제로는 이벤트:알림 = 1:N)
- `disaster_event` 1—N `disaster_event_translation`
- `user_disaster_alert` 1—N `user_disaster_alert_region` N—1 `legal_district`, 1—N `comment`
- `community_post`, `comment` 각각 N—1 `member`
- `region_adjacency`는 `legal_district` 간 인접 관계를 코드값으로만 저장(FK 미선언)

## 3. 알려진 이슈

1. **`disaster_alert.embedding`(VECTOR 512) 컬럼이 JPA 엔티티에 매핑되어 있지 않음.** 이벤트 클러스터링 서비스가 네이티브 쿼리로 직접 다루는 것으로 보인다 — 엔티티만 보고 스키마를 파악하면 이 컬럼을 놓치기 쉽다.
2. `disaster_events`의 `cooldown_hours`/`is_broadcast`/`is_advisory`, `event_alert_mapping`의 `similarity_score`/`merge_method`는 최초 테이블 생성 마이그레이션 이후 별도 `ALTER TABLE`로 추가된 컬럼이다 — 클러스터링 기능이 반복적으로 확장되어 온 이력을 반영한다.
3. PRD 5.7(사용자 제재) 구현 시 `member`뿐 아니라 `comment`/`community_post`/`user_disaster_alert`에도 상태·사유 컬럼이 필요할 수 있다 (1.5절 참고).

## 4. 참고
- [PRD.md](./PRD.md), [TRD.md](./TRD.md), [REQUIREMENTS.md](./REQUIREMENTS.md), [API_SPEC.md](./API_SPEC.md)
- 전체 마이그레이션 이력: `backend/src/main/resources/db/migration/V*.sql`
