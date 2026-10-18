package com.disaster.alert.alertapi.domain.disasteralert.model;

import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;

/**
 * 코드 기준 지역 통계({@code /stats/region-code})의 집계 단위.
 *
 * <p>법정동 코드 앞 N자리로 묶는다. 대시보드 지도가 폴리곤(행정구역 경계)의 코드와 그대로 매칭하기 위한 단위이며,
 * 이름 파싱으로 "시 단위"까지만 묶는 기존 {@code /stats/sigungu} 와 달리 구가 있는 일반시(수원시 장안구 등)를
 * 구별로 나눈다.
 */
public enum RegionCodeUnit {
    /** 시군구(5자리, 구가 있는 시는 구 단위). 시도 전체 코드(xx00000000)는 제외한다. */
    SIGUNGU(5),
    /** 읍면동(8자리). 시도·시군구 전체 코드와 리 단위(10자리)는 읍면동으로 합산한다. */
    EMD(8);

    private final int prefixLength;

    RegionCodeUnit(int prefixLength) {
        this.prefixLength = prefixLength;
    }

    public int prefixLength() {
        return prefixLength;
    }

    /**
     * 요청 파라미터를 단위로 변환한다. 값이 없으면 SIGUNGU, 대소문자·앞뒤 공백은 무시한다.
     *
     * <p>{@code @RequestParam} 을 enum 으로 바로 받으면 잘못된 값이 타입 변환 예외(500)로 떨어지므로,
     * 문자열로 받아 여기서 변환하고 모르는 값은 INVALID_REQUEST(400)로 거절한다.
     */
    public static RegionCodeUnit from(String raw) {
        if (raw == null || raw.isBlank()) {
            return SIGUNGU;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "unit 은 SIGUNGU 또는 EMD 여야 합니다: " + raw);
        }
    }
}
