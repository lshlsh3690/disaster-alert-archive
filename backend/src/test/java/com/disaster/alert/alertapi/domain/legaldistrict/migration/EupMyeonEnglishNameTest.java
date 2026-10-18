package com.disaster.alert.alertapi.domain.legaldistrict.migration;

import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 구가 있는 시(용인·청주·천안·포항·창원)의 활성 읍·면 행 영문 법정동 명칭이 한글 명칭과 맞는지 마이그레이션 결과로 검증한다.
 *
 * <p>V7 시드는 이 읍·면 행에 같은 시의 다른 읍·면 하위 리 영문명을 붙여 놨다
 * (예: 용인시 처인구 포곡읍 -> "Baegam-myeon Samgye-ri", 포항시 남구 구룡포읍 -> "Buk-gu, Pohang-si Nam-gu Guryongpo-ri").
 * 올바른 영문명은 "구 행 영문명 + 읍·면 영문 이름"이고, 읍·면 이름은 같은 읍·면 하위 리 행의 영문명에 들어 있다.
 */
@IntegrationTest
class EupMyeonEnglishNameTest {

    @Autowired
    private JdbcTemplate jdbc;

    private String englishName(String code) {
        return jdbc.queryForObject(
                "SELECT name FROM legal_district_translation WHERE code = ? AND language_code = 'EN'", String.class, code);
    }

    @Test
    void 구가_있는_시의_읍면_영문명은_구_이름과_읍면_이름으로_이루어진다() {
        assertEquals("Gyeonggi-do Cheoin-gu, Yongin-si Pogok-eup", englishName("4146125000"));
        assertEquals("Gyeonggi-do Cheoin-gu, Yongin-si Baegam-myeon", englishName("4146135000"));
        assertEquals("Chungcheongbuk-do Sangdang-gu, Cheongju-si Nangseong-myeon", englishName("4311131000"));
        assertEquals("Chungcheongnam-do Seobuk-gu, Cheonan-si Seonghwan-eup", englishName("4413325000"));
        assertEquals("Chungcheongnam-do Dongnam-gu, Cheonan-si Pungse-myeon", englishName("4413131000"));
        assertEquals("Gyeongsangbuk-do Nam-gu, Pohang-si Guryongpo-eup", englishName("4711125000"));
        assertEquals("Gyeongsangbuk-do Buk-gu, Pohang-si Heunghae-eup", englishName("4711325000"));
        assertEquals("Gyeongsangnam-do Masanhappo-gu, Changwon-si Jinjeon-myeon", englishName("4812534000"));
        assertEquals("Gyeongsangnam-do Masanhoewon-gu, Changwon-si Naeseo-eup", englishName("4812725000"));
    }

    @Test
    void 활성_읍면의_영문명_끝_단어는_한글_읍_면과_일치한다() {
        List<String> wrong = jdbc.queryForList("""
                SELECT d.code FROM legal_district d
                JOIN legal_district_translation t ON t.code = d.code AND t.language_code = 'EN'
                WHERE d.is_active
                  AND ((d.name ~ '읍$' AND t.name !~ '-eup$') OR (d.name ~ '면$' AND t.name !~ '-myeon$'))
                ORDER BY d.code
                """, String.class);
        assertTrue(wrong.isEmpty(), "읍·면 영문명이 -eup/-myeon 으로 끝나지 않는 활성 코드: " + wrong);
    }
}
