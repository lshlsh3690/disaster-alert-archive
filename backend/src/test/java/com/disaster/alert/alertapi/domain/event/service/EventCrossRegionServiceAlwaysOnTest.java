package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.event.repository.AlertEmbeddingRepository;
import com.disaster.alert.alertapi.domain.event.repository.DisasterEventRepository;
import com.disaster.alert.alertapi.domain.event.repository.EventAlertMappingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * cross-region 연결이 on/off 설정 없이 항상 동작하는지 검증한다(CROSS_REGION_ENABLED 는 2026-10-10 삭제).
 */
@ExtendWith(MockitoExtension.class)
class EventCrossRegionServiceAlwaysOnTest {

    @Mock DisasterAlertRepository disasterAlertRepository;
    @Mock AlertEmbeddingRepository alertEmbeddingRepository;
    @Mock DisasterEventRepository disasterEventRepository;
    @Mock EventAlertMappingRepository eventAlertMappingRepository;
    @Mock EventLLMDecisionService llmDecisionService;
    @Mock EventClusteringService eventClusteringService;

    @Test
    @DisplayName("설정 없이도 linkCrossRegion 은 알림을 조회한다")
    void linkCrossRegion_queriesAlertWithoutAnyFlag() {
        EventCrossRegionService service = new EventCrossRegionService(disasterAlertRepository,
                alertEmbeddingRepository, disasterEventRepository, eventAlertMappingRepository,
                llmDecisionService, eventClusteringService);
        when(disasterAlertRepository.findById(1L)).thenReturn(Optional.empty());

        service.linkCrossRegion(1L);

        verify(disasterAlertRepository).findById(1L);
    }
}
