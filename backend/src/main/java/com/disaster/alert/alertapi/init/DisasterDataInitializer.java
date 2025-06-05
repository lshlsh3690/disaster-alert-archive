package com.disaster.alert.alertapi.init;

import com.disaster.alert.alertapi.api.DisasterOpenApiClient;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("init")  // 배포할 때만 동작하게 프로파일 지정
@RequiredArgsConstructor
@Slf4j
public class DisasterDataInitializer implements CommandLineRunner {
    /**
     * 애플리케이션 시작 시 재난 데이터를 초기화하는 클래스입니다.
     * init 프로파일이 활성화된 경우에만 실행됩니다.
     */
    private final DisasterAlertService disasterAlertService;

    @Override
    public void run(String... args) {
        log.info("DisasterDataInitializer 실행: 재난 데이터 초기화 시작");
        disasterAlertService.initAllDisasterData();
    }
}