package com.disaster.alert.alertapi.domain.disasteralert.init;

import com.disaster.alert.alertapi.api.DisasterOpenApiClient;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import com.disaster.alert.alertapi.domain.legaldistrict.service.LegalDistrictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("init")  // env파일에서 특정속성값으로 실행되게 만듬
@RequiredArgsConstructor
@Slf4j
public class DisasterDataInitializer implements CommandLineRunner {
    /**
     * 애플리케이션 시작 시 재난 데이터를 초기화하는 클래스입니다.
     * init 프로파일이 활성화된 경우에만 실행됩니다.
     */
    private final DisasterAlertService disasterAlertService;
    private final LegalDistrictService legalDistrictService;

    @Override
    public void run(String... args) {
        log.info("DisasterDataInitializer 실행: 재난 데이터 초기화 시작");

        try {
            legalDistrictService.saveAllLegalDistricts();
            log.info("법정동 데이터 초기화 완료");
            disasterAlertService.initAllDisasterData();
        } catch (Exception e) {
            log.error("DisasterDataInitializer.run 재난 데이터 초기화 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}