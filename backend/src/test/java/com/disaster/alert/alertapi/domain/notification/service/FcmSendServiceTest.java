package com.disaster.alert.alertapi.domain.notification.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link FcmSendService#collectDeadTokens}·{@link FcmSendService#isDeadTokenError} 순수 단위 테스트.
 * SendResponse/BatchResponse/FirebaseMessagingException은 생성자가 없는 Firebase 타입이라 Mockito로 목킹한다
 * (Spring Boot 3.4.4 → Mockito 5 inline mock maker가 기본이라 final 클래스도 목킹 가능).
 * DB·Spring 컨텍스트가 필요 없으므로 @SpringBootTest 없이 순수 JUnit으로 검증한다.
 * 주의: {@code when(...).thenReturn(List.of(successResponse(), ...))}처럼 바깥 스터빙이 끝나기 전에
 * 인자로 넘긴 헬퍼가 또 다른 목을 스터빙하면(중첩 스터빙) Mockito가 UnfinishedStubbingException을 던진다 —
 * 응답 리스트는 반드시 지역 변수로 먼저 완성한 뒤 스터빙해야 한다.
 */
@DisplayName("FcmSendService 죽은 토큰 판별")
class FcmSendServiceTest {

    private SendResponse successResponse() {
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(true);
        return response;
    }

    private SendResponse failedResponse(MessagingErrorCode code) {
        SendResponse response = mock(SendResponse.class);
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(code);
        when(response.isSuccessful()).thenReturn(false);
        when(response.getException()).thenReturn(exception);
        return response;
    }

    @Test
    @DisplayName("실패가 뒤쪽에 몰린 배치에서 '앞에서 N개' 오구현이면 살아있는 토큰을 지우게 되므로, 뒤쪽 실패 토큰만 정확히 골라내야 한다")
    void picksDeadTokenByIndexNotByFailureCount() {
        // 앞의 두 토큰은 성공, 마지막 토큰만 UNREGISTERED로 실패.
        // "실패 1건이니 앞에서 1개"처럼 구현하면 살아있는 token-A를 지우게 되는 사고가 난다.
        List<String> tokens = List.of("token-A", "token-B", "token-C");
        List<SendResponse> responses = List.of(
                successResponse(),
                successResponse(),
                failedResponse(MessagingErrorCode.UNREGISTERED)
        );
        BatchResponse response = mock(BatchResponse.class);
        when(response.getResponses()).thenReturn(responses);

        List<String> deadTokens = FcmSendService.collectDeadTokens(tokens, response);

        assertThat(deadTokens).containsExactly("token-C");
    }

    @Test
    @DisplayName("UNREGISTERED·INVALID_ARGUMENT로 실패한 토큰만 죽은 토큰으로 판정하고, 나머지 실패 코드는 재시도 대상이라 제외한다")
    void onlyPermanentErrorCodesAreCollected() {
        List<String> tokens = List.of(
                "token-unregistered", "token-invalid", "token-unavailable",
                "token-internal", "token-quota", "token-mismatch", "token-ok"
        );
        List<SendResponse> responses = List.of(
                failedResponse(MessagingErrorCode.UNREGISTERED),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.UNAVAILABLE),
                failedResponse(MessagingErrorCode.INTERNAL),
                failedResponse(MessagingErrorCode.QUOTA_EXCEEDED),
                failedResponse(MessagingErrorCode.SENDER_ID_MISMATCH),
                successResponse()
        );
        BatchResponse response = mock(BatchResponse.class);
        when(response.getResponses()).thenReturn(responses);

        List<String> deadTokens = FcmSendService.collectDeadTokens(tokens, response);

        assertThat(deadTokens).containsExactlyInAnyOrder("token-unregistered", "token-invalid");
    }

    @Test
    @DisplayName("토큰 수와 응답 수가 다르면 인덱스 대응을 신뢰할 수 없으므로, 잘못 지워 복구 불가능한 사고를 막기 위해 아무것도 지우지 않는다")
    void returnsEmptyWhenTokenAndResponseCountMismatch() {
        List<String> tokens = List.of("token-A", "token-B", "token-C");
        // 토큰은 3개인데 응답은 2개뿐인, 대응이 깨진 상황을 시뮬레이션한다.
        List<SendResponse> responses = List.of(
                failedResponse(MessagingErrorCode.UNREGISTERED),
                failedResponse(MessagingErrorCode.UNREGISTERED)
        );
        BatchResponse response = mock(BatchResponse.class);
        when(response.getResponses()).thenReturn(responses);

        List<String> deadTokens = FcmSendService.collectDeadTokens(tokens, response);

        assertThat(deadTokens).isEmpty();
    }

    @Test
    @DisplayName("tokens가 null이면 빈 리스트를 안전하게 반환한다")
    void returnsEmptyWhenTokensNull() {
        List<SendResponse> responses = List.of(successResponse());
        BatchResponse response = mock(BatchResponse.class);
        when(response.getResponses()).thenReturn(responses);

        assertThat(FcmSendService.collectDeadTokens(null, response)).isEmpty();
    }

    @Test
    @DisplayName("tokens가 빈 리스트면 빈 리스트를 반환한다")
    void returnsEmptyWhenTokensEmpty() {
        BatchResponse response = mock(BatchResponse.class);
        when(response.getResponses()).thenReturn(List.of());

        assertThat(FcmSendService.collectDeadTokens(List.of(), response)).isEmpty();
    }

    @Test
    @DisplayName("response가 null이면 빈 리스트를 안전하게 반환한다")
    void returnsEmptyWhenResponseNull() {
        List<String> tokens = List.of("token-A");

        assertThat(FcmSendService.collectDeadTokens(tokens, null)).isEmpty();
    }

    @ParameterizedTest(name = "{0}은 영구 무효(삭제 대상)로 판정한다")
    @EnumSource(value = MessagingErrorCode.class, names = {"UNREGISTERED", "INVALID_ARGUMENT"})
    @DisplayName("UNREGISTERED·INVALID_ARGUMENT는 영구 무효로 판정한다")
    void permanentErrorCodesAreDead(MessagingErrorCode code) {
        assertThat(FcmSendService.isDeadTokenError(code)).isTrue();
    }

    @ParameterizedTest(name = "{0}은 일시적 오류이므로 삭제 대상이 아니다")
    @EnumSource(
            value = MessagingErrorCode.class,
            names = {"UNREGISTERED", "INVALID_ARGUMENT"},
            mode = EnumSource.Mode.EXCLUDE
    )
    @DisplayName("쿼터·네트워크·서버 오류 등 일시적 실패는 재시도 대상이라 삭제 판정에서 제외한다")
    void transientErrorCodesAreNotDead(MessagingErrorCode code) {
        assertThat(FcmSendService.isDeadTokenError(code)).isFalse();
    }
}
