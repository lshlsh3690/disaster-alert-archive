-- V108이 전남광주통합특별시(12xx) 신규 법정동코드 3,204건을 시딩했지만
-- legal_district_translation(EN/JA/ZH)에는 대응 번역이 하나도 없었다.
-- 옛 광주광역시(29xx)·전라남도(46xx) 코드는 각 언어별로 이미 번역이 존재하므로,
-- V108의 한글명과 동일한 방식(시도 접두어만 치환)으로 번역 문자열도 그대로 재사용한다.
-- (예: EN "Gwangju Dong-gu" → "Jeonnam-Gwangju Dong-gu")

-- 1) 영어(EN)
INSERT INTO legal_district_translation (code, language_code, name)
SELECT new_ld.code, 'EN',
    CASE
        WHEN old_t.name IN ('Gwangju', 'Jeollanam-do') THEN 'Jeonnam-Gwangju'
        WHEN old_t.name LIKE 'Gwangju %' THEN 'Jeonnam-Gwangju ' || substring(old_t.name FROM length('Gwangju ') + 1)
        WHEN old_t.name LIKE 'Jeollanam-do %' THEN 'Jeonnam-Gwangju ' || substring(old_t.name FROM length('Jeollanam-do ') + 1)
    END
FROM legal_district_translation old_t
JOIN legal_district old_ld ON old_ld.code = old_t.code
    AND old_ld.is_active = false
    AND (old_ld.code LIKE '29%' OR old_ld.code LIKE '46%')
JOIN legal_district new_ld ON new_ld.is_active = true
    AND new_ld.name = (
        CASE
            WHEN old_ld.name IN ('광주광역시', '전라남도') THEN '전남광주통합특별시'
            WHEN old_ld.name LIKE '광주광역시 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('광주광역시 ') + 1)
            WHEN old_ld.name LIKE '전라남도 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('전라남도 ') + 1)
        END
    )
WHERE old_t.language_code = 'EN'
ON CONFLICT (code, language_code) DO NOTHING;

-- 2) 일본어(JA)
INSERT INTO legal_district_translation (code, language_code, name)
SELECT new_ld.code, 'JA',
    CASE
        WHEN old_t.name IN ('光州広域市', '全羅南道') THEN '全南光州統合特別市'
        WHEN old_t.name LIKE '光州広域市 %' THEN '全南光州統合特別市 ' || substring(old_t.name FROM length('光州広域市 ') + 1)
        WHEN old_t.name LIKE '全羅南道 %' THEN '全南光州統合特別市 ' || substring(old_t.name FROM length('全羅南道 ') + 1)
    END
FROM legal_district_translation old_t
JOIN legal_district old_ld ON old_ld.code = old_t.code
    AND old_ld.is_active = false
    AND (old_ld.code LIKE '29%' OR old_ld.code LIKE '46%')
JOIN legal_district new_ld ON new_ld.is_active = true
    AND new_ld.name = (
        CASE
            WHEN old_ld.name IN ('광주광역시', '전라남도') THEN '전남광주통합특별시'
            WHEN old_ld.name LIKE '광주광역시 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('광주광역시 ') + 1)
            WHEN old_ld.name LIKE '전라남도 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('전라남도 ') + 1)
        END
    )
WHERE old_t.language_code = 'JA'
ON CONFLICT (code, language_code) DO NOTHING;

-- 3) 중국어(ZH)
INSERT INTO legal_district_translation (code, language_code, name)
SELECT new_ld.code, 'ZH',
    CASE
        WHEN old_t.name IN ('光州广域市', '全罗南道') THEN '全南光州统合特别市'
        WHEN old_t.name LIKE '光州广域市 %' THEN '全南光州统合特别市 ' || substring(old_t.name FROM length('光州广域市 ') + 1)
        WHEN old_t.name LIKE '全罗南道 %' THEN '全南光州统合特别市 ' || substring(old_t.name FROM length('全罗南道 ') + 1)
    END
FROM legal_district_translation old_t
JOIN legal_district old_ld ON old_ld.code = old_t.code
    AND old_ld.is_active = false
    AND (old_ld.code LIKE '29%' OR old_ld.code LIKE '46%')
JOIN legal_district new_ld ON new_ld.is_active = true
    AND new_ld.name = (
        CASE
            WHEN old_ld.name IN ('광주광역시', '전라남도') THEN '전남광주통합특별시'
            WHEN old_ld.name LIKE '광주광역시 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('광주광역시 ') + 1)
            WHEN old_ld.name LIKE '전라남도 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('전라남도 ') + 1)
        END
    )
WHERE old_t.language_code = 'ZH'
ON CONFLICT (code, language_code) DO NOTHING;
