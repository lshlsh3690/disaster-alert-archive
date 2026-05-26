package com.disaster.alert.alertapi.domain.openapi.service;

import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSearchRequest;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import com.disaster.alert.alertapi.domain.openapi.dto.OpenApiAlertResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * OpenAPI 재난문자 조회 서비스.
 *
 * <p>기존 DisasterAlertService의 검색 기능을 외부 공개 API 유스케이스로 감싼다.
 * 컨트롤러가 도메인 서비스에 직접 의존하지 않게 하고, 내부 DTO를 OpenAPI 전용 DTO로
 * 변환해 외부 API 계약과 내부 구현을 분리한다.
 */
@Service
@RequiredArgsConstructor
public class OpenApiAlertService {
    /** 내부 재난문자 검색 정책을 그대로 재사용하기 위한 기존 도메인 서비스. */
    private final DisasterAlertService disasterAlertService;

    /**
     * 날짜 범위로 재난문자를 조회하고 OpenAPI 전용 DTO로 변환한다.
     *
     * <p>외부에 노출하는 검색 조건은 날짜 범위만으로 단순화한다.
     * 내부 AlertSearchRequest 조립은 서비스에서 담당해 외부 API 계약과 내부 구현을 분리한다.
     */
    public Page<OpenApiAlertResponse> search(LocalDate startDate, LocalDate endDate, Pageable pageable, String lang) {
        AlertSearchRequest condition = AlertSearchRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .build();
        return disasterAlertService.searchAlerts(condition, pageable, lang)
                .map(OpenApiAlertResponse::from);
    }
}
