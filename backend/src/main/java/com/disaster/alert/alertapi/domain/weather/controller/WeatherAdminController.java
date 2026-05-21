package com.disaster.alert.alertapi.domain.weather.controller;

import com.disaster.alert.alertapi.domain.weather.service.WeatherCollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 운영자 수동 트리거 컨트롤러.
 *
 * <p><b>용도</b>
 * <ul>
 *   <li>스케줄러가 실패했을 때 즉시 1회 재실행</li>
 *   <li>테스트 환경에서 cron 까지 기다리지 않고 즉시 동작 확인</li>
 *   <li>배포 직후 검증용</li>
 * </ul>
 *
 * <p><b>인증</b>: 현재는 임시로 open. 운영 배포 전 {@code @PreAuthorize("hasRole('ADMIN')")}
 * 또는 별도 admin 인증 추가 필요.
 */
@RestController
@RequestMapping("/api/v1/admin/weather")
@RequiredArgsConstructor
@Slf4j
public class WeatherAdminController {

    private final WeatherCollectService weatherCollectService;

    /**
     * 초단기실황 1회 수동 수집.
     *
     * @return 적재 row 수
     */
    @PostMapping("/collect")
    public ResponseEntity<Map<String, Object>> triggerCollect() {
        log.info("수동 트리거: weather collect");
        int saved = weatherCollectService.collectAndSave();
        return ResponseEntity.ok(Map.of(
                "saved", saved,
                "message", "weather collect completed"
        ));
    }
}
