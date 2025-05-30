package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class DisasterAlertServiceTest {
    @Autowired
    private DisasterAlertRepository disasterAlertRepository;

    @Autowired
    private DisasterAlertService disasterAlertService;

    @Test
    @Transactional
    void saveData() {
        // given
        String rawJson = """
        {
          "header": {
            "resultMsg": "NORMAL SERVICE",
            "resultCode": "00",
            "errorMsg": null
          },
          "numOfRows": 10,
          "pageNo": 1,
          "totalCount": 2,
          "body": [
            {
              "MSG_CN": "비상 상황입니다",
              "RCPTN_RGN_NM": "경상남도 진주시",
              "CRT_DT": "2023/09/16 11:00:00",
              "EMRG_STEP_NM": "안전안내",
              "SN": 123456,
              "DST_SE_NM": "호우",
              "MDFCN_YMD": "2023/09/16 08:32:00.000000000"
            },
            {
              "MSG_CN": "또 다른 경보",
              "RCPTN_RGN_NM": "강원특별자치도 영월군",
              "CRT_DT": "2023/09/16 11:01:00",
              "EMRG_STEP_NM": "안전안내",
              "SN": 123457,
              "DST_SE_NM": "지진",
              "MDFCN_YMD": "2025/05/27 08:32:00.000000000"
            }
          ]
        }
        """;

        // when
        disasterAlertService.saveData(rawJson);


        // then
        List<DisasterAlert> alerts = disasterAlertRepository.findAll();
        assertEquals(2, alerts.size());
    }
}