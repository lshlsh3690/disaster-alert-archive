-- 발신 기관(agency)과 본문 표기(agency_alias), 알림의 기관 참조(disaster_alert.agency_id)
--
-- 재난문자 본문 끝(또는 맨 앞) 대괄호 안 표기("[행정안전부]", "[서울특별시청]")를 기관으로 묶어 두는 표다(specs/007 R11).
-- 표기와 기관을 분리한 이유: 같은 기관이 "서울시"/"서울특별시" 처럼 여러 표기로 나오므로, 나중에 정규화로 표기들을
-- 한 기관에 다시 연결할 수 있어야 한다(agency_alias 의 agency_id 만 바꾸면 된다).
--
-- agency       : 기관. 이름은 유일하지 않다(정규화 뒤 합쳐질 수 있어 UNIQUE 를 걸지 않는다).
-- agency_alias : 본문에서 뽑은 표기 그대로(공백 포함) → 기관 하나. 표기가 PK.
-- disaster_alert.agency_id : 표기가 없는 본문은 NULL.
--
-- 스키마만 만든다. 기존 알림의 백필(표기 → 기관 → 알림 갱신)은 SQL 정규식과 Java 규칙(SenderLabel)의 일치를
-- 따로 검증해야 해서 별도 마이그레이션(V141)으로 한다.
CREATE TABLE agency (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

CREATE TABLE agency_alias (
    label      VARCHAR(100) PRIMARY KEY,
    agency_id  BIGINT       NOT NULL REFERENCES agency (id),
    created_at TIMESTAMP    NOT NULL
);

ALTER TABLE disaster_alert ADD COLUMN agency_id BIGINT NULL REFERENCES agency (id);

CREATE INDEX idx_disaster_alert_agency ON disaster_alert (agency_id);
