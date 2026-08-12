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
    @DisplayName("UNREGISTERED로 실패한 토큰만 죽은 토큰으로 판정한다 — INVALID_ARGUMENT는 토큰 형식뿐 아니라 " +
            "메시지 페이로드(제목·본문·data·AndroidConfig) 오류에도 동일하게 뜨는 모호한 코드라 삭제 대상에서 제외한다 " +
            "(그렇지 않으면 발송 코드/설정 변경으로 페이로드가 깨졌을 때 멀쩡한 구독자를 전량 삭제하게 된다)")
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

        assertThat(deadTokens).containsExactly("token-unregistered");
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

    @Test
    @DisplayName("토큰 수보다 응답 수가 더 많은 역방향 불일치에서도 인덱스 대응을 신뢰할 수 없으므로 아무것도 지우지 않는다")
    void returnsEmptyWhenResponseCountExceedsTokenCount() {
        List<String> tokens = List.of("token-A", "token-B");
        // 토큰은 2개인데 응답은 3개인, 반대 방향으로 대응이 깨진 상황을 시뮬레이션한다.
        List<SendResponse> responses = List.of(
                failedResponse(MessagingErrorCode.UNREGISTERED),
                failedResponse(MessagingErrorCode.UNREGISTERED),
                failedResponse(MessagingErrorCode.UNREGISTERED)
        );
        BatchResponse response = mock(BatchResponse.class);
        when(response.getResponses()).thenReturn(responses);

        List<String> deadTokens = FcmSendService.collectDeadTokens(tokens, response);

        assertThat(deadTokens).isEmpty();
    }

    @Test
    @DisplayName("UNREGISTERED는 '앱/브라우저가 등록 해제됨'을 명확히 뜻하므로 영구 무효(삭제 대상)로 판정한다")
    void permanentErrorCodeIsDead() {
        assertThat(FcmSendService.isDeadTokenError(MessagingErrorCode.UNREGISTERED)).isTrue();
    }

    @ParameterizedTest(name = "{0}은 삭제 대상이 아니다")
    @EnumSource(
            value = MessagingErrorCode.class,
            names = {"UNREGISTERED"},
            mode = EnumSource.Mode.EXCLUDE
    )
    @DisplayName("UNREGISTERED를 제외한 나머지는 삭제 대상이 아니다 — 쿼터·네트워크·서버 오류는 " +
            "재시도하면 되는 일시적 실패라 제외하고, INVALID_ARGUMENT는 일시적이라서가 아니라 토큰 형식 오류와 " +
            "메시지 페이로드 오류를 구분할 수 없는 모호한 코드라서 별도 이유로 제외한다 " +
            "(둘을 구분 못 하면 페이로드가 깨졌을 때 멀쩡한 구독자를 전량 삭제하게 된다)")
    void nonUnregisteredErrorCodesAreNotDead(MessagingErrorCode code) {
        assertThat(FcmSendService.isDeadTokenError(code)).isFalse();
    }

    @Test
    @DisplayName("getMessagingErrorCode()가 null인 미분류 오류(code == null)는 NPE 없이 false를 반환한다 " +
            "— switch(code) 형태로 리팩터링하면 이 케이스에서 NPE가 나므로 그 회귀를 막는 못이다")
    void nullErrorCodeIsNotDead() {
        assertThat(FcmSendService.isDeadTokenError(null)).isFalse();
    }

    @Test
    @DisplayName("배치 전체가 INVALID_ARGUMENT로 실패하고 배치 크기가 임계값(5) 이상이면 페이로드 버그로 의심한다 " +
            "— 현재 스텁(항상 false)에서는 실패해야 하는 Red 케이스다")
    void allInvalidArgumentAtOrAboveThresholdIsSuspected() {
        List<String> tokens = List.of("t1", "t2", "t3", "t4", "t5");
        List<SendResponse> responses = List.of(
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT)
        );
        BatchResponse response = mock(BatchResponse.class);
        when(response.getResponses()).thenReturn(responses);

        assertThat(FcmSendService.isSuspectedPayloadFailure(tokens, response)).isTrue();
    }

    @Test
    @DisplayName("하나라도 성공한 토큰이 있으면 페이로드는 유효하다는 뜻이므로, 나머지 5개가 전부 " +
            "INVALID_ARGUMENT로 실패해도 의심하지 않는다 — 판별의 핵심 계약")
    void anySuccessMeansNotSuspectedEvenWithManyInvalidArgumentFailures() {
        List<String> tokens = List.of("t1", "t2", "t3", "t4", "t5", "t6");
        List<SendResponse> responses = List.of(
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                successResponse()
        );
        BatchResponse response = mock(BatchResponse.class);
        when(response.getResponses()).thenReturn(responses);

        assertThat(FcmSendService.isSuspectedPayloadFailure(tokens, response)).isFalse();
    }

    @Test
    @DisplayName("전부 실패했더라도 실패 코드 중 INVALID_ARGUMENT가 아닌 코드(UNREGISTERED)가 섞여 있으면 " +
            "페이로드 문제만으로는 설명되지 않으므로 의심하지 않는다")
    void mixedFailureCodesAreNotSuspected() {
        List<String> tokens = List.of("t1", "t2", "t3", "t4", "t5");
        List<SendResponse> responses = List.of(
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.UNREGISTERED)
        );
        BatchResponse response = mock(BatchResponse.class);
        when(response.getResponses()).thenReturn(responses);

        assertThat(FcmSendService.isSuspectedPayloadFailure(tokens, response)).isFalse();
    }

    @Test
    @DisplayName("배치 크기가 임계값(5) 미만인 4개는 전부 INVALID_ARGUMENT로 실패해도 의심하지 않는다 " +
            "— 진짜 죽은 토큰만 우연히 모였을 가능성과 구분이 안 되는 경계값 4")
    void belowThresholdBatchIsNotSuspected() {
        List<String> tokens = List.of("t1", "t2", "t3", "t4");
        List<SendResponse> responses = List.of(
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT)
        );
        BatchResponse response = mock(BatchResponse.class);
        when(response.getResponses()).thenReturn(responses);

        assertThat(FcmSendService.isSuspectedPayloadFailure(tokens, response)).isFalse();
    }

    @Test
    @DisplayName("tokens가 null이면 판단 근거가 없으므로 의심하지 않는다")
    void nullTokensAreNotSuspected() {
        BatchResponse response = mock(BatchResponse.class);

        assertThat(FcmSendService.isSuspectedPayloadFailure(null, response)).isFalse();
    }

    @Test
    @DisplayName("tokens가 빈 리스트면 판단 근거가 없으므로 의심하지 않는다")
    void emptyTokensAreNotSuspected() {
        BatchResponse response = mock(BatchResponse.class);

        assertThat(FcmSendService.isSuspectedPayloadFailure(List.of(), response)).isFalse();
    }

    @Test
    @DisplayName("response가 null이면 판단 근거가 없으므로 의심하지 않는다")
    void nullResponseIsNotSuspected() {
        List<String> tokens = List.of("t1", "t2", "t3", "t4", "t5");

        assertThat(FcmSendService.isSuspectedPayloadFailure(tokens, null)).isFalse();
    }

    @Test
    @DisplayName("토큰 수와 응답 수가 불일치하면 인덱스 대응을 신뢰할 수 없으므로 의심하지 않는다")
    void mismatchedTokenAndResponseCountIsNotSuspected() {
        List<String> tokens = List.of("t1", "t2", "t3", "t4", "t5");
        List<SendResponse> responses = List.of(
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT),
                failedResponse(MessagingErrorCode.INVALID_ARGUMENT)
        );
        BatchResponse response = mock(BatchResponse.class);
        when(response.getResponses()).thenReturn(responses);

        assertThat(FcmSendService.isSuspectedPayloadFailure(tokens, response)).isFalse();
    }
}
