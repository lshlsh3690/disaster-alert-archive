-- 청주시 구(서원·흥덕·청원) 하위 코드의 영문 법정동 명칭 오류 정정.
--
-- V7 시드가 청원군 통합(2014) 이전 구조를 그대로 가져와, 서원구(43112)·흥덕구(43113)·청원구(43114) 아래 코드의
-- 영문 구 이름이 모두 "Sangdang-gu, Cheongju-si" 로 들어가 있고, 일부 읍·면은 다른 읍·면·리 이름까지 붙어 있다
-- (예: 흥덕구 오송읍 -> "Gangnae-myeon Hogye-ri"). JA/ZH 번역은 정상이라 EN 행만 고친다.
--
-- 1) 비활성 행을 포함해 구 코드 prefix 에 맞는 구 이름으로 통일한다.
UPDATE legal_district_translation
SET name = regexp_replace(name, '(Sangdang|Seowon|Heungdeok|Cheongwon)-gu, Cheongju-si',
                          CASE
                              WHEN code LIKE '43112%' THEN 'Seowon-gu, Cheongju-si'
                              WHEN code LIKE '43113%' THEN 'Heungdeok-gu, Cheongju-si'
                              WHEN code LIKE '43114%' THEN 'Cheongwon-gu, Cheongju-si'
                          END)
WHERE language_code = 'EN'
  AND (code LIKE '43112%' OR code LIKE '43113%' OR code LIKE '43114%')
  AND name ~ '(Sangdang|Seowon|Heungdeok|Cheongwon)-gu, Cheongju-si';

-- 2) 한글 명칭과 어긋난 읍·면 영문명 8개(활성 코드)를 한글 명칭 기준으로 바로잡는다.
UPDATE legal_district_translation t
SET name = v.name
FROM (VALUES
    ('4311231000', 'Chungcheongbuk-do Seowon-gu, Cheongju-si Nami-myeon'),
    ('4311232000', 'Chungcheongbuk-do Seowon-gu, Cheongju-si Hyeondo-myeon'),
    ('4311325000', 'Chungcheongbuk-do Heungdeok-gu, Cheongju-si Osong-eup'),
    ('4311331000', 'Chungcheongbuk-do Heungdeok-gu, Cheongju-si Gangnae-myeon'),
    ('4311332000', 'Chungcheongbuk-do Heungdeok-gu, Cheongju-si Oksan-myeon'),
    ('4311425000', 'Chungcheongbuk-do Cheongwon-gu, Cheongju-si Naesu-eup'),
    ('4311425300', 'Chungcheongbuk-do Cheongwon-gu, Cheongju-si Ochang-eup'),
    ('4311431000', 'Chungcheongbuk-do Cheongwon-gu, Cheongju-si Bugi-myeon')
) AS v(code, name)
WHERE t.code = v.code AND t.language_code = 'EN';

DO $$
BEGIN
    IF (SELECT count(*) FROM legal_district_translation
        WHERE language_code = 'EN'
          AND code IN ('4311231000','4311232000','4311325000','4311331000','4311332000','4311425000','4311425300','4311431000')
          AND name !~ '(Seowon|Heungdeok|Cheongwon)-gu, Cheongju-si') <> 0 THEN
        RAISE EXCEPTION 'V127 left a wrong Cheongju gu name';
    END IF;
END $$;
