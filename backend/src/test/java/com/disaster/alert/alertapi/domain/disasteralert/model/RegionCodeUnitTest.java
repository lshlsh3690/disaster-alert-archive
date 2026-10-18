package com.disaster.alert.alertapi.domain.disasteralert.model;

import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 외부 의존성이 없는 순수 로직이라 Spring 컨텍스트 없이 검증한다.
 */
class RegionCodeUnitTest {

    @Test
    void 집계_단위별_코드_접두어_길이는_시군구_5_읍면동_8이다() {
        assertEquals(5, RegionCodeUnit.SIGUNGU.prefixLength());
        assertEquals(8, RegionCodeUnit.EMD.prefixLength());
    }

    @Test
    void from은_대소문자와_앞뒤_공백을_무시하고_변환한다() {
        assertEquals(RegionCodeUnit.SIGUNGU, RegionCodeUnit.from("SIGUNGU"));
        assertEquals(RegionCodeUnit.EMD, RegionCodeUnit.from("emd"));
        assertEquals(RegionCodeUnit.EMD, RegionCodeUnit.from("  Emd "));
    }

    @Test
    void from은_값이_없으면_시군구가_기본이다() {
        assertEquals(RegionCodeUnit.SIGUNGU, RegionCodeUnit.from(null));
        assertEquals(RegionCodeUnit.SIGUNGU, RegionCodeUnit.from(""));
        assertEquals(RegionCodeUnit.SIGUNGU, RegionCodeUnit.from("   "));
    }

    @Test
    void from은_모르는_값이면_500이_아니라_INVALID_REQUEST_400으로_거절한다() {
        CustomException e = assertThrows(CustomException.class, () -> RegionCodeUnit.from("SIDO"));

        assertEquals(ErrorCode.INVALID_REQUEST, e.getErrorCode());
        assertEquals(400, e.getErrorCode().getHttpStatus());
    }
}
