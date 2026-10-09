-- 구가 있는 시(용인·청주·천안·포항·창원)의 활성 읍·면 영문 법정동 명칭 정정.
--
-- V7 시드가 이 읍·면 행에 같은 시의 다른 읍·면 하위 리 영문명을 붙여 놨다
-- (예: 용인시 처인구 포곡읍 -> "Gyeonggi-do Cheoin-gu, Yongin-si Baegam-myeon Samgye-ri",
--      포항시 남구 구룡포읍 -> "Gyeongsangbuk-do Buk-gu, Pohang-si Nam-gu Guryongpo-ri").
-- 올바른 영문명은 "구 행 영문명 + 읍·면 영문 이름"이다. 구 행(앞 5자리 + 00000)의 영문명을 앞부분으로 쓰고,
-- 읍·면 이름은 같은 읍·면 하위 리 행의 영문명("... Pogok-eup Samgye-ri")에서 가져온다.
-- 영문 철자는 기존 시드를 그대로 따른다(공식 표기와 대조한 것이 아니라 DB 내부 정합성만 맞춘다).
-- JA/ZH 번역은 정상이라 EN 행만 고친다. 영문명이 이미 -eup/-myeon 으로 끝나는 행은 건드리지 않으므로 재실행해도 결과가 같다.
WITH target AS (
    SELECT d.code, d.name AS ko
    FROM legal_district d
    JOIN legal_district_translation t ON t.code = d.code AND t.language_code = 'EN'
    WHERE d.is_active AND d.name ~ '[읍면]$'
      AND lower(regexp_replace(t.name, '^.*[ ,]([A-Za-z0-9()\-]+)$', '\1')) !~ '(eup|myeon)$'
), fix AS (
    SELECT tg.code,
           (SELECT s.name FROM legal_district_translation s
             WHERE s.language_code = 'EN' AND s.code = left(tg.code, 5) || '00000') AS base,
           (SELECT regexp_replace(c.name, '^(.*) ([A-Za-z0-9()\-]+-(eup|myeon)) [A-Za-z0-9()\-]+$', '\2')
              FROM legal_district_translation c JOIN legal_district cd ON cd.code = c.code
             WHERE c.language_code = 'EN' AND left(c.code, 7) = left(tg.code, 7) AND c.code <> tg.code
               AND cd.name LIKE tg.ko || ' %'
               AND c.name ~ '^(.*) [A-Za-z0-9()\-]+-(eup|myeon) [A-Za-z0-9()\-]+$'
             LIMIT 1) AS token
    FROM target tg
)
UPDATE legal_district_translation t
SET name = fix.base || ' ' || fix.token
FROM fix
WHERE t.language_code = 'EN' AND t.code = fix.code AND fix.base IS NOT NULL AND fix.token IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM legal_district d
               JOIN legal_district_translation t ON t.code = d.code AND t.language_code = 'EN'
               WHERE d.is_active
                 AND ((d.name ~ '읍$' AND t.name !~ '-eup$') OR (d.name ~ '면$' AND t.name !~ '-myeon$'))) THEN
        RAISE EXCEPTION 'V128 left an eup/myeon English name that does not end with -eup/-myeon';
    END IF;
END $$;
