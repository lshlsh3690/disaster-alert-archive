-- 시군구 ↔ 기상 관측 소스 매핑 테이블
--
-- 한 시군구가 어떤 ASOS 지점에서 과거 데이터를 가져오고
-- 어떤 기상청 격자(nx, ny) 에서 단기예보를 받을지 정의한다.
--
-- ASOS 지점은 시군구당 1:1 매핑이 안 됨 (전국 102개 지점뿐).
-- 따라서 인접 시군구 여러 개가 같은 ASOS 지점을 공유할 수 있다.
-- 예: 서울 강남구/강북구/종로구 모두 ASOS 지점 108(서울) 사용.

CREATE TABLE IF NOT EXISTS weather_station_mapping (
    legal_district_code  VARCHAR(10)  NOT NULL,
    asos_station_id      VARCHAR(10),                -- 과거 ASOS 지점번호 (예: "108"=서울)
    asos_station_name    VARCHAR(50),                -- 표시용 ("서울", "부산")
    kma_nx               INTEGER,                    -- 기상청 단기예보 격자 X
    kma_ny               INTEGER,                    -- 기상청 단기예보 격자 Y
    CONSTRAINT pk_wsm
        PRIMARY KEY (legal_district_code),
    CONSTRAINT fk_wsm_legal_district
        FOREIGN KEY (legal_district_code) REFERENCES legal_district (code)
);

-- 과거 데이터 백필 시 "이 ASOS 지점에 매핑된 시군구가 어느 것들인가" 역방향 조회용.
-- 한 ASOS 호출 결과를 N 시군구로 복제 INSERT 할 때 핵심 경로.
CREATE INDEX IF NOT EXISTS idx_wsm_asos_station_id
    ON weather_station_mapping (asos_station_id);
