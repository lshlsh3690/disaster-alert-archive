# 🚨 Disaster Alert Archive

재난 안전 문자 아카이브 및 사용자 맞춤형 알림 서비스입니다.  
재난 문자를 수집하고, 사용자에게 실시간으로 알림을 제공하며, 지역별 재난 통계를 제공합니다.

---

## 📌 프로젝트 개요

- **서비스명**: Disaster Alert Archive
- **목표**: 실시간 재난 정보 수집 및 사용자 맞춤형 알림 제공
- **핵심 기능**:
  - 재난 안전 문자 수집 및 저장
  - 사용자 위치 기반 맞춤 알림 제공
  - 지역별 재난 통계 대시보드
  - 사용자 후기 및 실시간 제보 기능
  - AI 기반 재난 트렌드 분석 (확장 예정)
  - 다국어 지원 (확장 예정)

---

## 🚀 기술 스택

### ✅ 백엔드
- `Spring Boot 3.x`
- `JPA` + `QueryDSL`
- `PostgreSQL`
- `Redis` (캐시 및 인증 코드 관리)
- `Kafka` (비동기 메시지 처리)
- `Spring Security` + `OAuth2` (Google,Naver, Kakao 로그인)
- `Docker` + `Docker Compose`

### ✅ 프론트엔드
- `Next.js` (React 최신 버전)
- `TypeScript`
- `TailwindCSS` + `Styled Components`
- `React Query (TanStack Query)`
- `Zustand`
- `Framer Motion` (애니메이션)
- 배포: `Vercel`

### ✅ CI/CD & 인프라
- `GitHub Actions` (테스트, 빌드, 배포 자동화)
- `Docker Hub` (백엔드 이미지 저장소)
- (옵션) `Slack/Discord 알림` 연동 예정

---

## ⚙️ 시스템 아키텍처
> 아키텍처 다이어그램 추가 예정

- `클라이언트 (Next.js)` → 사용자 인터페이스 및 알림 제공
- `API 서버 (Spring Boot)` → 재난 문자 수집, 가공, 사용자 데이터 처리
- `Kafka` → 이벤트 기반 알림 처리
- `Redis` → 캐시 및 인증 코드 관리
- `PostgreSQL` → 데이터 저장소
- `Docker Compose` → 로컬 및 서버 환경 구성
- `CI/CD (GitHub Actions)` → 자동화된 테스트 및 배포 파이프라인

---

## 📚 요구사항 분석

### ✅ 기능적 요구사항
1. 재난 안전 문자를 실시간으로 수집하고 저장한다.
2. 사용자에게 위치 기반 맞춤형 재난 알림을 제공한다.
3. 지역별 재난 통계 및 트렌드를 시각화하여 제공한다.
4. 사용자 후기 및 실시간 제보 기능을 제공한다.

### ✅ 비기능적 요구사항
1. 실시간 처리 지연 시간 최소화 (Kafka 기반 비동기 처리)
2. 데이터 무결성과 안정성 확보 (트랜잭션 및 데이터 백업)
3. RESTful API 제공 및 확장성 고려
4. Docker 기반 개발/운영 환경 통일
5. CI/CD를 통한 무중단 배포 및 자동화