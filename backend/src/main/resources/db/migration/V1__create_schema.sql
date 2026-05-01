-- =============================================================
-- 1. legal_district (다른 테이블이 참조하므로 가장 먼저 생성)
-- =============================================================
CREATE TABLE IF NOT EXISTS legal_district (
                                              code             VARCHAR(10)  NOT NULL,
    name             VARCHAR(255) NOT NULL,
    is_active        BOOLEAN      NOT NULL,
    is_active_string VARCHAR(255) NOT NULL,
    CONSTRAINT pk_legal_district PRIMARY KEY (code)
    );


-- =============================================================
-- 2. member
-- =============================================================
CREATE TABLE IF NOT EXISTS member (
                                      member_id  BIGSERIAL    PRIMARY KEY,
                                      email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    nickname   VARCHAR(255) NOT NULL,
    role       VARCHAR(255) NOT NULL,
    is_deleted BOOLEAN      DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
    );


-- =============================================================
-- 3. member_social
-- =============================================================
CREATE TABLE IF NOT EXISTS member_social (
                                             member_social_id    BIGSERIAL    PRIMARY KEY,
                                             member_id           BIGINT       NOT NULL,
                                             provider            VARCHAR(20)  NOT NULL,
    provider_user_id    VARCHAR(100) NOT NULL,
    email_from_provider VARCHAR(255),
    created_at          TIMESTAMP,
    CONSTRAINT uk_provider_user        UNIQUE (provider, provider_user_id),
    CONSTRAINT fk_member_social_member FOREIGN KEY (member_id) REFERENCES member (member_id)
    );


-- =============================================================
-- 4. disaster_alert
--    @SequenceGenerator(allocationSize = 500) 반영
-- =============================================================
CREATE SEQUENCE IF NOT EXISTS disaster_alert_seq
    INCREMENT BY 500
    START WITH 1;

CREATE TABLE IF NOT EXISTS disaster_alert (
                                              disaster_alert_id BIGINT        NOT NULL,
                                              sn                BIGINT        NOT NULL UNIQUE,
                                              message           TEXT,
                                              created_at        TIMESTAMP,
                                              emergency_level   VARCHAR(255),
    disaster_type     VARCHAR(255),
    modified_date     TIMESTAMP,
    original_region   VARCHAR(1000),
    CONSTRAINT pk_disaster_alert  PRIMARY KEY (disaster_alert_id),
    CONSTRAINT uq_disaster_alert_id UNIQUE (disaster_alert_id)  -- @Column(unique = true) 반영
    );


-- =============================================================
-- 5. disaster_alert_region  (복합 PK)
--    DisasterAlertRegionId: alertId + districtCode(length 미지정 → VARCHAR(255))
-- =============================================================
CREATE TABLE IF NOT EXISTS disaster_alert_region (
                                                     disaster_alert_id   BIGINT       NOT NULL,
                                                     legal_district_code VARCHAR(255),
    CONSTRAINT pk_disaster_alert_region  PRIMARY KEY (disaster_alert_id, legal_district_code),
    CONSTRAINT fk_dar_disaster_alert     FOREIGN KEY (disaster_alert_id)   REFERENCES disaster_alert  (disaster_alert_id),
    CONSTRAINT fk_dar_legal_district     FOREIGN KEY (legal_district_code) REFERENCES legal_district  (code)
    );


-- =============================================================
-- 6. user_disaster_alert
-- =============================================================
CREATE TABLE IF NOT EXISTS user_disaster_alert (
                                                   id             BIGSERIAL    PRIMARY KEY,
                                                   created_by_id  BIGINT       NOT NULL,
                                                   title          VARCHAR(120) NOT NULL,
    message        VARCHAR(300) NOT NULL,
    disaster_type  VARCHAR(50),
    disaster_level VARCHAR(255),
    occurred_at    TIMESTAMP    NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    modified_at    TIMESTAMP,
    is_deleted     BOOLEAN      DEFAULT FALSE
    );


-- =============================================================
-- 7. user_disaster_alert_region  (복합 PK)
--    UserDisasterAlertRegionId: userDisasterAlertId + legalDistrictCode(length=10)
-- =============================================================
CREATE TABLE IF NOT EXISTS user_disaster_alert_region (
                                                          user_disaster_alert_id BIGINT      NOT NULL,
                                                          legal_district_code    VARCHAR(10) NOT NULL,
    CONSTRAINT pk_user_disaster_alert_region PRIMARY KEY (user_disaster_alert_id, legal_district_code),
    CONSTRAINT fk_udar_user_alert            FOREIGN KEY (user_disaster_alert_id) REFERENCES user_disaster_alert (id),
    CONSTRAINT fk_udar_legal_district        FOREIGN KEY (legal_district_code)    REFERENCES legal_district      (code)
    );


-- =============================================================
-- 8. comment
--    @Table indexes 3개 반영
-- =============================================================
CREATE TABLE IF NOT EXISTS comment (
                                       id                     BIGSERIAL    PRIMARY KEY,
                                       content                VARCHAR(500) NOT NULL,
    created_at             TIMESTAMP    NOT NULL,
    updated_at             TIMESTAMP,
    is_deleted             BOOLEAN      DEFAULT FALSE,
    member_id              BIGINT       NOT NULL,
    disaster_alert_id      BIGINT,
    user_disaster_alert_id BIGINT,
    CONSTRAINT fk_comment_member                FOREIGN KEY (member_id)              REFERENCES member             (member_id),
    CONSTRAINT fk_comment_disaster_alert        FOREIGN KEY (disaster_alert_id)      REFERENCES disaster_alert     (disaster_alert_id),
    CONSTRAINT fk_comment_user_disaster_alert   FOREIGN KEY (user_disaster_alert_id) REFERENCES user_disaster_alert(id)
    );

CREATE INDEX IF NOT EXISTS idx_comment_disaster_alert_id      ON comment (disaster_alert_id);
CREATE INDEX IF NOT EXISTS idx_comment_user_disaster_alert_id ON comment (user_disaster_alert_id);
CREATE INDEX IF NOT EXISTS idx_comment_member_id              ON comment (member_id);


-- =============================================================
-- 9. community_post
-- =============================================================
CREATE TABLE IF NOT EXISTS community_post (
                                              id         BIGSERIAL     PRIMARY KEY,
                                              category   VARCHAR(255)  NOT NULL,
    member_id  BIGINT        NOT NULL,
    title      VARCHAR(120)  NOT NULL,
    content    VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP     NOT NULL,
    updated_at TIMESTAMP,
    is_deleted BOOLEAN       DEFAULT FALSE,
    CONSTRAINT fk_community_post_member FOREIGN KEY (member_id) REFERENCES member (member_id)
    );


-- =============================================================
-- 10. missing_child
--     필드명 camelCase → Spring PhysicalNamingStrategy snake_case 변환 반영
-- =============================================================
CREATE TABLE IF NOT EXISTS missing_child (
                                             msspsnidntfccd   BIGINT       PRIMARY KEY,  -- msspsnIdntfccd
                                             name             VARCHAR(50),
    age              INTEGER,
    gender           VARCHAR(10),
    target_type_code VARCHAR(10),               -- targetTypeCode
    address          VARCHAR(200),
    special_feature  VARCHAR(500),              -- specialFeature
    photo_base64     TEXT,                      -- photoBase64 (@Lob)
    photo_size       INTEGER                    -- photoSize
    );