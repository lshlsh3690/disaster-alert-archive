# API 명세서 (API_SPEC) — Disaster Alert Archive

> 요청/응답 필드 단위의 상세 스키마는 로컬 실행 후 Swagger UI(`/swagger-ui.html`, SpringDoc 자동 생성)를 참고. 이 문서는 엔드포인트 전체 목록과 공통 규격, 인증 요구사항을 정리한다.

## 1. 공통 규격

- **Base URL**: `https://api.disaster-alert-archive.co.kr` (운영), `http://localhost:8080` (로컬)
- **응답 포맷**: 모든 성공 응답은 `ApiResponse<T>` 래퍼로 감싼다.

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "code": 200,
  "data": { }
}
```

- **에러 포맷**: `GlobalExceptionHandler`가 `ApiErrorResponse`로 통일 처리.

```json
{
  "code": "M404",
  "key": "MEMBER_NOT_FOUND",
  "message": "회원을 찾을 수 없습니다",
  "status": 404,
  "path": "/api/v1/members/me",
  "timestamp": "2026-07-03T12:00:00+09:00",
  "field": null
}
```

- **인증**: JWT access token을 httpOnly 쿠키로 전달. 401 응답 시 클라이언트는 `/api/v1/auth/reissue`로 재발급 후 원 요청을 재시도한다(프론트 axios 인터셉터가 자동 처리).
- **공개 API 인증**: `/api/v1/open-api/**`는 JWT가 아닌 발급된 토큰(`open-api/tokens`로 발급) 기반 별도 인증 필터(`OpenApiTokenAuthenticationFilter`)를 사용한다.

## 2. 엔드포인트 목록

### 2.1 인증 (`/api/v1/auth`) — 전체 공개
| Method | Path | 설명 |
|---|---|---|
| POST | `/login` | 이메일 로그인 |
| POST | `/signup` | 회원가입 |
| POST | `/reissue` | Access Token 재발급 |
| POST | `/logout` | 로그아웃 |
| POST | `/email/verify` | 이메일 인증 코드 발송 |
| POST | `/email/verify/code` | 이메일 인증 코드 검증 |
| GET | `/oauth/{provider}/authorize` | 소셜 로그인 시작 (google/kakao/naver) |
| GET | `/oauth/{provider}/callback` | 소셜 로그인 콜백 |

### 2.2 회원 (`/api/v1/members`)
| Method | Path | 설명 | 인증 |
|---|---|---|---|
| GET | `/me` | 내 정보 조회 | 필요 |
| GET | `/check-nickname` | 닉네임 중복 확인 | 공개 |
| PATCH | `/{id}` | 회원 정보 수정 | 필요 |
| DELETE | `/{id}` | 회원 탈퇴 | 필요 |
| GET | `/me/favorite-regions` | 관심 지역 목록 조회 | 필요 |
| POST | `/me/favorite-regions` | 관심 지역 등록 | 필요 |
| DELETE | `/me/favorite-regions/{legalDistrictCode}` | 관심 지역 삭제 | 필요 |

### 2.3 재난 문자 (`/api/v1/alerts`) — 전체 공개
| Method | Path | 설명 |
|---|---|---|
| GET | `/search` | 조건 검색 |
| GET | `/search/combined` | 공식+사용자 제보 통합 검색 |
| GET | `/{id}` | 상세 조회 |
| GET | `/latest` | 최신 목록 |
| GET | `/{id}/weather` | 해당 알림 시점 기상 조회 |
| GET | `/dashboard/summary` | 대시보드 요약 |
| GET | `/stats`, `/stats/sido`, `/stats/sigungu`, `/stats/daily`, `/stats/hourly`, `/stats/monthly-type`, `/stats/daily-type` | 지역별·기간별·유형별 통계 |
| GET | `/stats/weather-correlation`, `/stats/weather-by-type`, `/stats/weather-by-region` | 기상 상관 통계 |
| GET | `/stats/weather-correlation-hourly`, `/stats/weather-by-type-hourly`, `/stats/weather-by-region-hourly` | 기상 상관 통계(시간 단위) |

### 2.4 이벤트 (`/api/v1/events`) — GET 공개
| Method | Path | 설명 |
|---|---|---|
| GET | `` | 이벤트 목록 조회(검색/필터) |
| GET | `/{id}` | 이벤트 상세(타임라인 포함) |

### 2.5 사용자 제보 (`/api/v1/user-alerts`) — GET 공개, 나머지 인증 필요
| Method | Path | 설명 | 인증 |
|---|---|---|---|
| GET | `` | 사용자 제보 목록 | 공개 |
| GET | `/{id}` | 사용자 제보 상세 | 공개 |
| POST | `` | 사용자 제보 작성 | 필요 |
| PATCH | `/{id}` | 사용자 제보 수정 | 필요 |
| DELETE | `/{id}` | 사용자 제보 삭제 | 필요 |

### 2.6 댓글 (`/api/v1/comments`) — GET 공개, 나머지 인증 필요
| Method | Path | 설명 | 인증 |
|---|---|---|---|
| GET | `` | 댓글 목록 | 공개 |
| GET | `/latest` | 최신 댓글 목록 | 공개 |
| POST | `` | 댓글 작성 | 필요 |
| PATCH | `/{id}` | 댓글 수정 | 필요 |
| DELETE | `/{id}` | 댓글 삭제 | 필요 |

> **신규 요구사항(PRD 5.7 사용자 제재)**: 관리자가 허위/장난 댓글·제보를 숨김 처리하는 엔드포인트(예: `DELETE /api/v1/admin/comments/{id}`, `POST /api/v1/admin/members/{id}/suspend`)는 아직 없음. 신설 필요.

### 2.7 커뮤니티 (`/api/v1/community`) — GET 공개, 나머지 인증 필요
| Method | Path | 설명 | 인증 |
|---|---|---|---|
| GET | `` | 게시글 목록 | 공개 |
| POST | `` | 게시글 작성 | 필요 |

### 2.8 알림/FCM (`/api/v1/fcm-token`, `/api/v1/notification-logs`, `/api/v1/notification-preference`) — 전체 인증 필요
| Method | Path | 설명 |
|---|---|---|
| POST | `/fcm-token` | FCM 토큰 등록 |
| DELETE | `/fcm-token` | FCM 토큰 삭제 |
| POST | `/fcm-token/guest` | 게스트 FCM 토큰 등록 |
| POST | `/fcm-token/guest/link` | 게스트 토큰을 로그인 계정에 연결 |
| DELETE | `/fcm-token/guest` | 게스트 FCM 토큰 삭제 |
| GET | `/notification-logs` | 알림 수신 이력 조회 |
| PATCH | `/notification-logs/{id}/read` | 알림 읽음 처리 |
| GET | `/notification-preference` | 알림 수신 설정 조회 |
| PUT | `/notification-preference` | 알림 수신 설정 변경 |

### 2.9 위험도 (`/api/v1/regions`) — 전체 공개
| Method | Path | 설명 |
|---|---|---|
| GET | `/risk-map` | 전체 지역 위험도 지도 |
| GET | `/risk-map/history` | 위험도 지도 이력 |
| GET | `/{regionCode}/risk` | 지역 위험도 상세 |
| GET | `/{regionCode}/risk/events` | 위험도에 기여한 이벤트 목록 |
| GET | `/{regionCode}/risk/history` | 지역 위험도 이력 |
| GET | `/alerts/{alertId}/risk` | 특정 알림의 위험도 기여분 |

### 2.10 법정동 (`/api/v1/districts`) — 전체 공개
| Method | Path | 설명 |
|---|---|---|
| GET | `/sigungu` | 시/도별 시군구 목록 조회 |

### 2.11 공개 OpenAPI (`/api/v1/open-api`) — 발급 토큰 인증
| Method | Path | 설명 |
|---|---|---|
| GET | `/alerts` | 재난 문자 공개 조회(JSON/CSV) |
| POST | `/tokens` | 공개 API 토큰 발급 |
| GET | `/tokens` | 발급 토큰 목록 조회 |
| DELETE | `/tokens/{tokenId}` | 토큰 폐기 |

### 2.12 관리자 (`/api/v1/admin`) — ⚠️ 인증 미적용
| Method | Path | 설명 |
|---|---|---|
| POST | `/trigger-fetch` | 재난문자 수집 + 알림 수동 트리거 |
| POST | `/trigger-notification/{alertId}` | 특정 알림 재발송 트리거 |
| POST | `/weather/collect` | 기상 관측 수동 수집 (실제 경로: `/api/v1/admin/weather/collect`) |

> **보안 이슈**: `SecurityConfig`에서 `/api/v1/admin/**` 전체가 `permitAll()`로 설정되어 있어 인증 없이 누구나 호출 가능한 상태다. 사용자 제재 기능(PRD 5.7)을 이 경로 아래에 추가할 경우 반드시 `ADMIN` 권한 검증을 먼저 적용해야 한다 (TRD 4 참고).

## 3. 참고
- [PRD.md](./PRD.md), [TRD.md](./TRD.md), [REQUIREMENTS.md](./REQUIREMENTS.md)
- Swagger UI: `/swagger-ui.html`, OpenAPI 스펙: `/v3/api-docs`
