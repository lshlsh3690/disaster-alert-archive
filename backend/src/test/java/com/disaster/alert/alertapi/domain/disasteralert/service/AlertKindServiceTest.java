package com.disaster.alert.alertapi.domain.disasteralert.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.AlertKind;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertKindServiceTest {

    private final DisasterAlertRepository repository = mock(DisasterAlertRepository.class);
    private final AlertKindClassifier classifier = mock(AlertKindClassifier.class);
    private final AlertKindService service = new AlertKindService(repository, classifier);
    private final DisasterAlert alert = DisasterAlert.builder().id(1L).sn(100L).disasterType("산불").message("산불 발생").build();

    // 결정: 분류 결과가 있으면 엔티티 메서드로 바꾸고 repository.save 를 직접 호출한다(트랜잭션 밖이라 더티 체킹 불가).
    @Test
    @DisplayName("분류 결과가 있으면 alertKind 를 바꾸고 저장한다")
    void savesClassifiedKind() {
        when(repository.findById(1L)).thenReturn(Optional.of(alert));
        when(classifier.classify("산불", "산불 발생")).thenReturn(Optional.of(AlertKind.INCIDENT));

        service.classifyAndSave(1L);

        assertThat(alert.getAlertKind()).isEqualTo(AlertKind.INCIDENT);
        verify(repository).save(alert);
    }

    // 결정: 분류 실패(empty)면 null 그대로 두고 저장하지 않는다 - 미분류와 구분되어야 폴백이 된다.
    @Test
    @DisplayName("분류 실패(empty)면 alertKind 는 null, 저장 안 함")
    void keepsNullWhenClassificationFails() {
        when(repository.findById(1L)).thenReturn(Optional.of(alert));
        when(classifier.classify("산불", "산불 발생")).thenReturn(Optional.empty());

        service.classifyAndSave(1L);

        assertThat(alert.getAlertKind()).isNull();
        verify(repository, never()).save(any());
    }

    // 결정: DB 읽기/저장 예외도 밖으로 내보내지 않는다. 수집 루프가 분류 때문에 멈추면 안 된다.
    @Test
    @DisplayName("findById 또는 save 가 던져도 예외를 밖으로 내보내지 않는다")
    void swallowsDatabaseExceptions() {
        when(repository.findById(1L)).thenThrow(new RuntimeException("DB down"));
        assertThatCode(() -> service.classifyAndSave(1L)).doesNotThrowAnyException();

        when(repository.findById(2L)).thenReturn(Optional.of(alert));
        when(classifier.classify("산불", "산불 발생")).thenReturn(Optional.of(AlertKind.INCIDENT));
        when(repository.save(any())).thenThrow(new RuntimeException("write failed"));
        assertThatCode(() -> service.classifyAndSave(2L)).doesNotThrowAnyException();
    }
}
