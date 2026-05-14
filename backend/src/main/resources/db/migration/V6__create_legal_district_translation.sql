-- =============================================================
-- legal_district_translation
--   법정동 코드별 다국어 번역명 저장 테이블.
--
-- 목적:
--   1) DeepL 호출 비용 절감 — 재난문자에 포함된 법정동 이름을
--      매번 번역하지 않고 이 테이블에서 조회.
--   2) 번역 일관성 확보 — 같은 "서울특별시"가 다른 영문 표기로
--      나오지 않도록 단일 소스 유지.
--
-- 스키마 설계:
--   복합 PK (code, language_code) — 같은 법정동에 대해 언어별 1개 행.
--   확장성: 새 언어 추가는 language_code 값만 늘리면 됨 (스키마 변경 불필요).
--
-- 참고: disaster_alert_translation 과 동일 패턴.
-- =============================================================
CREATE TABLE IF NOT EXISTS legal_district_translation (
    code             VARCHAR(10)  NOT NULL,    -- 법정동 코드 (legal_district.code FK)
    language_code    VARCHAR(10)  NOT NULL,    -- 언어 코드 (예: 'EN', 'JA', 'ZH')
    name             VARCHAR(255) NOT NULL,    -- 해당 언어로 번역된 법정동 이름
    CONSTRAINT pk_legal_district_translation
        PRIMARY KEY (code, language_code),
    CONSTRAINT fk_ldt_legal_district
        FOREIGN KEY (code) REFERENCES legal_district (code)
);

-- 언어별 일괄 조회용 인덱스.
-- 재난문자 1건은 여러 법정동을 가지므로, 응답 생성 시
-- "이 언어로 된 법정동 N개를 한 번에 조회" 쿼리가 핵심 경로다.
-- PK가 (code, language_code)라 language_code 단독 조회 시 인덱스를 못 타기 때문에 추가.
CREATE INDEX IF NOT EXISTS idx_ldt_language_code
    ON legal_district_translation (language_code);
