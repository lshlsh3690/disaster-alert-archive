package com.disaster.alert.alertapi.domain.legaldistrict.migration;

import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 청주시 구(상당·서원·흥덕·청원) 하위 읍·면의 영문 법정동 명칭이 한글 명칭과 맞는지 마이그레이션 결과로 검증한다.
 *
 * <p>V7 시드는 청원군 통합 이전 구조를 그대로 가져와, 서원·흥덕·청원구 하위 코드의 영문 구 이름이 모두
 * "Sangdang-gu" 로 들어가 있고 일부는 다른 읍·면·리 이름이 붙어 있었다(예: 흥덕구 오송읍 -> "Gangnae-myeon Hogye-ri").
 */
@IntegrationTest
class CheongjuGuEnglishNameTest {

    @Autowired
    private JdbcTemplate jdbc;

    private String englishName(String code) {
        return jdbc.queryForObject(
                "SELECT name FROM legal_district_translation WHERE code = ? AND language_code = 'EN'", String.class, code);
    }

    @Test
    void 서원구_읍면의_영문명은_서원구_기준이다() {
        assertEquals("Chungcheongbuk-do Seowon-gu, Cheongju-si Nami-myeon", englishName("4311231000"));
        assertEquals("Chungcheongbuk-do Seowon-gu, Cheongju-si Hyeondo-myeon", englishName("4311232000"));
    }

    @Test
    void 흥덕구_읍면의_영문명은_흥덕구_기준이다() {
        assertEquals("Chungcheongbuk-do Heungdeok-gu, Cheongju-si Osong-eup", englishName("4311325000"));
        assertEquals("Chungcheongbuk-do Heungdeok-gu, Cheongju-si Gangnae-myeon", englishName("4311331000"));
        assertEquals("Chungcheongbuk-do Heungdeok-gu, Cheongju-si Oksan-myeon", englishName("4311332000"));
    }

    @Test
    void 청원구_읍면의_영문명은_청원구_기준이다() {
        assertEquals("Chungcheongbuk-do Cheongwon-gu, Cheongju-si Naesu-eup", englishName("4311425000"));
        assertEquals("Chungcheongbuk-do Cheongwon-gu, Cheongju-si Ochang-eup", englishName("4311425300"));
        assertEquals("Chungcheongbuk-do Cheongwon-gu, Cheongju-si Bugi-myeon", englishName("4311431000"));
    }

    @Test
    void 청주_구_하위_영문명의_구_이름은_코드의_구와_일치한다() {
        // 4311(1~4) = 상당·서원·흥덕·청원. 비활성 행 포함 전체가 같은 구 이름을 써야 한다.
        Map<String, String> guByCodePrefix = Map.of(
                "43111", "Sangdang-gu, Cheongju-si", "43112", "Seowon-gu, Cheongju-si",
                "43113", "Heungdeok-gu, Cheongju-si", "43114", "Cheongwon-gu, Cheongju-si");
        for (Map.Entry<String, String> e : guByCodePrefix.entrySet()) {
            List<String> wrong = jdbc.queryForList(
                    "SELECT code FROM legal_district_translation WHERE language_code = 'EN' AND code LIKE ? "
                            + "AND name NOT LIKE ? ORDER BY code",
                    String.class, e.getKey() + "%", "%" + e.getValue() + "%");
            assertTrue(wrong.isEmpty(), e.getKey() + " 아래 영문 구 이름이 틀린 코드: " + wrong);
        }
    }
}
