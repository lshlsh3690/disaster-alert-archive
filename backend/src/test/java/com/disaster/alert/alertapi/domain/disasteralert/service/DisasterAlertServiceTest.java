package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringBootTest
class DisasterAlertServiceTest {

    @Autowired
    private DisasterAlertRepository disasterAlertRepository;

    @Autowired
    private DisasterAlertService disasterAlertService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        disasterAlertRepository = mock(DisasterAlertRepository.class);
    }


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
              "RCPTN_RGN_NM": "서울시",
              "CRT_DT": "2023/09/16 11:00:00",
              "EMRG_STEP_NM": "안전안내",
              "SN": 123456,
              "DST_SE_NM": "호우",
              "MDFCN_YMD": "2023-09-16"
            },
            {
              "MSG_CN": "또 다른 경보",
              "RCPTN_RGN_NM": "부산시",
              "CRT_DT": "2023/09/16 11:01:00",
              "EMRG_STEP_NM": "안전안내",
              "SN": 123457,
              "DST_SE_NM": "지진",
              "MDFCN_YMD": "2023-09-16"
            }
          ]
        }
        """;

        // 이미 저장된 SN 123456은 제외되어야 함
        when(disasterAlertRepository.findExistingSn(List.of(123456L, 123457L)))
                .thenReturn(List.of(123456L));

        // when
        disasterAlertService.saveData(rawJson);

        // then: saveAll 호출된 Alert는 1개여야 함 (123457만 저장)
        ArgumentCaptor<List<DisasterAlert>> captor = ArgumentCaptor.forClass(List.class);
        verify(disasterAlertRepository, times(1)).saveAll(captor.capture());

        List<DisasterAlert> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals(123457L, saved.get(0).getSn());
        assertEquals("부산시", saved.get(0).getRegion());
    }
}