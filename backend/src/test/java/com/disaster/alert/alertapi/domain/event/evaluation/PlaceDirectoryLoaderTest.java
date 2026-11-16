package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.PlaceDirectory.NameEntry;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 로 시딩된 실제 법정동·이웃 표를 읽어 이름 표·이웃 표가 만들어지는지 본다.
 * 이름·이웃 계산 규칙 자체는 {@code PlaceDirectoryBuilderTest} 가 검증하고, 여기서는 SQL·컬럼 매핑만 확인한다.
 */
@IntegrationTest
class PlaceDirectoryLoaderTest {

    @Autowired
    PlaceDirectoryLoader loader;

    @Test
    void 시딩된_법정동과_이웃_표로_이름_표와_이웃_표를_만든다() {
        PlaceDirectory directory = loader.load();

        // 활성 시군구(구 있는 시 포함), 폐지됐지만 남기는 인천 옛 서구(E), 세종 통칭이 이름 표에 든다
        assertThat(directory.names()).contains(
                new NameEntry("천안시", "44", "44130"),
                new NameEntry("서구", "28", "28260"),
                new NameEntry("세종시", "36", "36110"));
        // 이웃 표: 시 코드는 하위 구 이웃 합집합으로 채워지고(천안시-아산시), 인천 옛 서구는 후속 구 이웃을 빌린다
        assertThat(directory.neighbors().get("44130")).contains("44200");
        assertThat(directory.neighbors().get("28260")).contains("28275", "28290");
        // 시도 별칭은 코드 상수라 DB 와 무관하지만, 읽은 결과에 함께 실려야 한다
        assertThat(directory.sidoByAlias()).containsEntry("부산시", "26");
    }
}
