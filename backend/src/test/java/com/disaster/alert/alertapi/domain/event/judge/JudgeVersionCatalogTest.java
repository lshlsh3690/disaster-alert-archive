package com.disaster.alert.alertapi.domain.event.judge;

import com.disaster.alert.alertapi.domain.event.judge.JudgeVersionCatalog.Entry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판사 버전 목록의 순수 로직 테스트. DB·Spring 없이 도는 단위 테스트다.
 */
class JudgeVersionCatalogTest {

    /**
     * 버전별 모델·단가가 한 곳에 정확히 있어야 한다. 값이 틀리면 대시보드의 모델 표시와 비용이 같이 틀어진다.
     * 일치율·측정일은 2026-11-26 부터 이 목록이 아니라 DB 측정 기록에 있다(specs/014).
     */
    @Test
    void 버전별_모델_단가가_목록에_있다() {
        // 준비 없음 — 목록은 코드에 고정된 값이다

        // 실행: 버전 이름으로 각각 찾는다
        Optional<Entry> v1 = JudgeVersionCatalog.find("v1");
        Optional<Entry> v2 = JudgeVersionCatalog.find("v2");

        // 검증 v1: gpt-4o, 단가 입력 2.50 / 출력 10.00
        assertThat(v1).contains(new Entry("v1", "gpt-4o", 2.50, 10.00));
        // 검증 v2: gpt-4o-mini, 단가 입력 0.15 / 출력 0.60
        assertThat(v2).contains(new Entry("v2", "gpt-4o-mini", 0.15, 0.60));
    }

    /**
     * 목록은 최신 버전이 먼저여야 한다 — 관리자 화면의 판사 버전 이력 표가 이 순서 그대로 나가기 때문이다.
     * Red 단계에서는 빈 목록이라 두 버전이 순서대로 들어 있다는 단언에서 실패한다.
     */
    @Test
    void 최신_버전이_먼저_나온다() {
        // 실행: 전체 목록을 꺼낸다
        List<Entry> all = JudgeVersionCatalog.all();

        // 검증: 나중 버전 v2 가 앞, v1 이 뒤
        assertThat(all).extracting(Entry::version).containsExactly("v2", "v1");
    }
}
