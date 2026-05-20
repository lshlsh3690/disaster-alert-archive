package com.disaster.alert.alertapi.domain.openapi.service;

import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSearchRequest;
import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertResponseDto;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
/**
 * OpenAPI 재난문자 조회 서비스.
 *
 * <p>기존 DisasterAlertService의 검색 기능을 외부 공개 API 유스케이스로 감싼다.
     * 컨트롤러가 도메인 검색 서비스에 직접 강하게 의존하지 않게 하고,
     * 이후 OpenAPI 전용 조회 정책이 생겨도 일반 서비스 검색 로직과 분리해 확장할 수 있게 했다.
     */
public class OpenApiAlertService {
    /** 내부 재난문자 검색 정책을 그대로 재사용하기 위한 기존 도메인 서비스. */
    private final DisasterAlertService disasterAlertService;

    /** OpenAPI 검색 조건을 기존 재난문자 검색 서비스에 위임한다. */
    public Page<DisasterAlertResponseDto> search(AlertSearchRequest searchCondition, Pageable pageable, String lang) {
        return disasterAlertService.searchAlerts(searchCondition, pageable, lang);
    }
}
