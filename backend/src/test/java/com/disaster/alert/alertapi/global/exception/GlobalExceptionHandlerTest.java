package com.disaster.alert.alertapi.global.exception;

import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import com.disaster.alert.alertapi.global.dto.ApiErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler 단위 테스트")
class GlobalExceptionHandlerTest {

    private static final String URI = "/api/v1/notifications/preference";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", URI);
        request.setRequestURI(URI);
        return request;
    }

    private HttpMessageNotReadableException notReadable() {
        return new HttpMessageNotReadableException(
                "JSON parse error: Cannot deserialize value of type NotificationType from String \"ALARM\"",
                new MockHttpInputMessage(new byte[0]));
    }

    @Test
    @DisplayName("요청 본문을 읽을 수 없으면 400 으로 응답한다")
    void 본문_해석_불가는_400() {
        ResponseEntity<ApiErrorResponse> response = handler.handleHttpMessageNotReadable(notReadable(), request());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("본문 해석 불가 응답은 JSON_PARSE_ERROR 코드와 요청 경로를 담는다")
    void 본문_해석_불가_에러코드와_경로() {
        ApiErrorResponse body = handler.handleHttpMessageNotReadable(notReadable(), request()).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("C003");
        assertThat(body.getKey()).isEqualTo("JSON_PARSE_ERROR");
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getPath()).isEqualTo(URI);
    }

    @Test
    @DisplayName("본문 해석 불가 응답은 Jackson 내부 예외 상세를 노출하지 않고 고정 메시지만 보여준다")
    void 본문_해석_불가_내부상세_미노출() {
        ApiErrorResponse body = handler.handleHttpMessageNotReadable(notReadable(), request()).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getMessage()).isEqualTo(ErrorCode.JSON_PARSE_ERROR.getMessage());
        assertThat(body.getMessage())
                .doesNotContain("Cannot deserialize")
                .doesNotContain("NotificationType")
                .doesNotContain("ALARM");
    }

    @Test
    @DisplayName("일반 RuntimeException 은 기존대로 catch-all 에서 500 / C500 으로 응답한다")
    void 일반_예외는_500_회귀방지() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleException(new RuntimeException("boom"), request());

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("C500");
    }
}
