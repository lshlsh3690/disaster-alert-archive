package com.disaster.alert.alertapi.init;

import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.disasteralert.service.DisasterAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")  // test 프로파일을 활성화하여 테스트 실행
class DisasterDataInitializerTest {

    @Autowired
    private DisasterAlertService disasterAlertService;

    @Autowired
    DisasterAlertRepository disasterAlertRepository;

    @Test
    public void testDisasterDataInitialization() {


        // 초기화 메서드 호출
        disasterAlertService.initAllDisasterData();

        // 데이터가 정상적으로 초기화되었는지 검증
        long count = disasterAlertRepository.count();
        assertTrue(count > 0, "재난 데이터가 초기화되지 않았습니다. 데이터가 존재해야 합니다.");
    }
}