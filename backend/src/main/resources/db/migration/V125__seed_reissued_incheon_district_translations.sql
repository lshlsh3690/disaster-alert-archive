-- 인천 신설 4개 구와 하위 법정동 84곳의 EN/JA/ZH 번역을 함께 시드한다.
-- JA 구명 출처(한국관광공사 공식 지역 목록): https://japanese.visitkorea.or.kr/svc/whereToGo/allRgn/allRegionList.do?menuSn=216
--   済物浦区, 永宗区, 西海区, 黔丹区
-- ZH 구명 출처(한국관광공사 공식 지역 목록): https://chinese.visitkorea.or.kr/svc/whereToGo/allRgn/allRegionList.do?menuSn=216
--   济物浦区, 永宗区, 西海区, 黔丹区
-- EN은 기존 Incheon ...-gu 형식과 국문 로마자 표기에 맞춘다.
-- 하위 명칭은 옛 코드의 기존 번역을 그대로 보존하고 시군구 부분만 치환한다.
-- 금곡동 두 곳은 각자의 옛 구(동구/서구) 번역을 대응하는 새 구로 가져온다.
-- JA 는 구 이름만 신자체(区)이고 하위 명칭은 옛 시드의 洞·街와 구자체 한자(榮·顔 등)가 그대로 남아 표기가 섞인다.
--   옛 시드의 구 이름은 區(구자체)인데 앞 두 토큰 치환으로 사라진다. V113 도 시도 접두어만 치환하고 옛 표기를 그대로 둔다.
--   ZH 는 옛 시드가 이미 간체라 일관된다.
-- V123 이후에 실행해야 한다: 새 코드가 활성이고 옛 코드가 폐지되어 있어야 하며, 아래 DO 블록이 이를 검사해 아니면 예외로 중단한다.

CREATE TEMP TABLE _v125_incheon_names (
    prefix VARCHAR(5) PRIMARY KEY, en VARCHAR(80) NOT NULL,
    ja VARCHAR(80) NOT NULL, zh VARCHAR(80) NOT NULL
);
INSERT INTO _v125_incheon_names VALUES
('28125', 'Incheon Jemulpo-gu', '仁川広域市 済物浦区', '仁川广域市 济物浦区'),
('28155', 'Incheon Yeongjong-gu', '仁川広域市 永宗区', '仁川广域市 永宗区'),
('28275', 'Incheon Seohae-gu', '仁川広域市 西海区', '仁川广域市 西海区'),
('28290', 'Incheon Geomdan-gu', '仁川広域市 黔丹区', '仁川广域市 黔丹区');

INSERT INTO legal_district_translation (code, language_code, name)
SELECT n.prefix || '00000', lang.language_code, lang.name
FROM _v125_incheon_names n
CROSS JOIN LATERAL (VALUES ('EN',n.en),('JA',n.ja),('ZH',n.zh)) lang(language_code,name)
JOIN legal_district ld ON ld.code = n.prefix || '00000' AND ld.is_active
ON CONFLICT (code, language_code) DO NOTHING;

CREATE TEMP TABLE _v125_incheon_map (old_code VARCHAR(10) NOT NULL, new_code VARCHAR(10) PRIMARY KEY);
INSERT INTO _v125_incheon_map (old_code, new_code) VALUES
('2811010100', '2812510800'),
('2811010200', '2812510900'),
('2811010300', '2812511000'),
('2811010400', '2812511100'),
('2811010500', '2812511200'),
('2811010600', '2812511300'),
('2811010700', '2812511400'),
('2811010800', '2812511500'),
('2811010900', '2812511600'),
('2811011000', '2812511700'),
('2811011100', '2812511800'),
('2811011200', '2812511900'),
('2811011300', '2812512000'),
('2811011400', '2812512100'),
('2811011500', '2812512200'),
('2811011600', '2812512300'),
('2811011700', '2812512400'),
('2811011800', '2812512500'),
('2811011900', '2812512600'),
('2811012000', '2812512700'),
('2811012100', '2812512800'),
('2811012200', '2812512900'),
('2811012300', '2812513000'),
('2811012400', '2812513100'),
('2811012500', '2812513200'),
('2811012600', '2812513300'),
('2811012700', '2812513400'),
('2811012800', '2812513500'),
('2811012900', '2812513600'),
('2811013000', '2812513700'),
('2811013100', '2812513800'),
('2811013200', '2812513900'),
('2811013300', '2812514000'),
('2811013400', '2812514100'),
('2811013500', '2812514200'),
('2811013600', '2812514300'),
('2811013700', '2812514400'),
('2811013800', '2812514500'),
('2811013900', '2812514600'),
('2811014000', '2812514700'),
('2811014100', '2812514800'),
('2811014200', '2812514900'),
('2811014300', '2812515000'),
('2811014400', '2812515100'),
('2811014500', '2815510100'),
('2811014600', '2815510200'),
('2811014700', '2815510300'),
('2811014800', '2815510400'),
('2811014900', '2815510500'),
('2811015000', '2815510600'),
('2811015100', '2815510700'),
('2811015200', '2815510800'),
('2814010100', '2812510100'),
('2814010200', '2812510200'),
('2814010300', '2812510300'),
('2814010400', '2812510400'),
('2814010500', '2812510500'),
('2814010700', '2812510700'),
('2826010100', '2829010100'),
('2826010200', '2829010200'),
('2826010300', '2827510100'),
('2826010400', '2827510200'),
('2826010500', '2827510300'),
('2826010600', '2827510400'),
('2826010700', '2827510500'),
('2826010800', '2827510600'),
('2826010900', '2827510700'),
('2826011000', '2827510800'),
('2826011100', '2827510900'),
('2826011200', '2827511000'),
('2826011300', '2829010300'),
('2826011400', '2829010400'),
('2826011500', '2829010500'),
('2826011700', '2829010600'),
('2826011900', '2829010800'),
('2826012000', '2829010900'),
('2826012100', '2829011000'),
('2826012200', '2827511100'),
('2814010600', '2812510600'),
('2826011800', '2829010700');

DO $$
BEGIN
    IF (SELECT count(*) FROM _v125_incheon_map) <> 80 OR
       (SELECT count(*) FROM _v125_incheon_map m
        JOIN legal_district old_ld ON old_ld.code = m.old_code AND NOT old_ld.is_active
        JOIN legal_district new_ld ON new_ld.code = m.new_code AND new_ld.is_active) <> 80 OR
       (SELECT count(*) FROM _v125_incheon_map m
        JOIN legal_district_translation t ON t.code = m.old_code
        WHERE (t.language_code = 'EN' AND t.name LIKE 'Incheon % %') OR
              (t.language_code = 'JA' AND t.name LIKE '仁川広域市 % %') OR
              (t.language_code = 'ZH' AND t.name LIKE '仁川广域市 % %')) <> 240 THEN
        RAISE EXCEPTION 'V125 requires V123 and all 80 source rows translated in EN/JA/ZH';
    END IF;
END $$;

INSERT INTO legal_district_translation (code, language_code, name)
SELECT m.new_code, t.language_code,
       regexp_replace(t.name, '^[^ ]+ [^ ]+',
           CASE t.language_code WHEN 'EN' THEN n.en WHEN 'JA' THEN n.ja ELSE n.zh END)
FROM _v125_incheon_map m
JOIN _v125_incheon_names n ON n.prefix = left(m.new_code, 5)
JOIN legal_district_translation t ON t.code = m.old_code AND t.language_code IN ('EN','JA','ZH')
ON CONFLICT (code, language_code) DO NOTHING;

DO $$
BEGIN
    IF (SELECT count(*) FROM legal_district_translation t
        JOIN legal_district ld ON ld.code = t.code AND ld.is_active
        WHERE left(t.code,5) IN ('28125','28155','28275','28290')
          AND t.language_code IN ('EN','JA','ZH')) <> 252 THEN
        RAISE EXCEPTION 'V125 must seed all 84 Incheon rows in all three languages';
    END IF;
END $$;

-- 임시 테이블 정리 (같은 Flyway 세션에서 뒤따르는 마이그레이션과 이름이 겹치지 않게)
DROP TABLE _v125_incheon_names, _v125_incheon_map;
