package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.AlertKind;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertKindServiceTest {

    private final DisasterAlertRepository repository = mock(DisasterAlertRepository.class);
    private final AlertKindClassifier classifier = mock(AlertKindClassifier.class);
    private final AlertKindService service = new AlertKindService(repository, classifier);

    private DisasterAlert alert() {
        return DisasterAlert.builder().id(1L).sn(100L).disasterType("산불").message("OO읍 산24 산불 발생").build();
    }

    /**
     * 결정: 분류기가 INCIDENT 를 주면 알림의 alertKind 를 바꾸고 저장소에 저장까지 한다.
     * Red: 뼈대가 아무것도 하지 않아 alertKind 가 null 이고 save 도 호출되지 않아 실패한다.
     */
    @Test
    @DisplayName("분류 결과가 있으면 alertKind 를 바꾸고 저장한다")
    void savesClassifiedKind() {
        // 준비: 미분류 알림 + INCIDENT 를 답하는 분류기
        DisasterAlert alert = alert();
        when(repository.findById(1L)).thenReturn(Optional.of(alert));
        when(classifier.classify("산불", "OO읍 산24 산불 발생")).thenReturn(Optional.of(AlertKind.INCIDENT));

        // 실행
        service.classifyAndSave(1L);

        // 검증: 엔티티 값 변경 + 명시적 save (더티 체킹에 기대지 않는다)
        assertThat(alert.getAlertKind()).isEqualTo(AlertKind.INCIDENT);
        verify(repository).save(alert);
    }

    /**
     * 결정: 분류 실패(empty)면 null 그대로 두고 저장도 하지 않으며 예외도 내지 않는다(현재 동작으로 폴백).
     * Red: 뼈대가 아무것도 안 해서 이미 통과한다 — 구현이 empty 를 잘못 저장하는 회귀를 막는 가드다.
     */
    @Test
    @DisplayName("분류 실패(empty)면 alertKind 는 null, 저장 안 함, 예외 없음")
    void keepsNullWhenClassificationFails() {
        // 준비: 분류기가 실패(empty)를 돌려주는 상황
        DisasterAlert alert = alert();
        when(repository.findById(1L)).thenReturn(Optional.of(alert));
        when(classifier.classify("산불", "OO읍 산24 산불 발생")).thenReturn(Optional.empty());

        // 실행: 예외가 나면 테스트가 깨진다
        service.classifyAndSave(1L);

        // 검증: 미분류 유지, save 미호출
        assertThat(alert.getAlertKind()).isNull();
        verify(repository, never()).save(any());
    }
}
