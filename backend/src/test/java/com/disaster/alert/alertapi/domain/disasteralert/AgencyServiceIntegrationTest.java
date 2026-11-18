package com.disaster.alert.alertapi.domain.disasteralert;

import com.disaster.alert.alertapi.domain.disasteralert.model.Agency;
import com.disaster.alert.alertapi.domain.disasteralert.model.AgencyAlias;
import com.disaster.alert.alertapi.domain.disasteralert.repository.AgencyAliasRepository;
import com.disaster.alert.alertapi.domain.disasteralert.repository.AgencyRepository;
import com.disaster.alert.alertapi.domain.disasteralert.service.AgencyService;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수집 배치의 본문 목록을 발신 기관으로 일괄 해석하는 규칙 3개를 실제 Postgres 로 검증한다(specs/007 R11).
 *
 * <p>표기 → 기관 연결이 agency_alias 행으로 남는지, 기관 행이 불필요하게 늘지 않는지를 DB 에서 확인해야 해서
 * 통합 테스트다. 표기 문자열은 운영 데이터와 겹치지 않는 값을 쓰고, 테스트 트랜잭션과 함께 롤백된다.
 */
@IntegrationTest
@Transactional
class AgencyServiceIntegrationTest {

    @Autowired
    private AgencyService agencyService;
    @Autowired
    private AgencyRepository agencyRepository;
    @Autowired
    private AgencyAliasRepository agencyAliasRepository;

    /**
     * 이미 등록된 표기는 새 기관을 만들지 않고 그 표기가 가리키는 기존 기관으로 해석돼야 한다.
     * 새로 만들면 같은 기관이 알림마다 쪼개져 기관별 집계가 무의미해진다.
     * 뼈대는 빈 Map 을 돌려주므로 "표기에 해당하는 기관이 있어야 한다" 단언에서 실패한다.
     */
    @Test
    @DisplayName("이미 등록된 표기는 기존 기관으로 해석되고 새 기관이 생기지 않는다")
    void 등록된_표기는_기존_기관으로_해석된다() {
        // 준비: 기관 하나와 그 기관을 가리키는 표기 하나를 미리 저장해 둔다
        Agency existing = agencyRepository.save(Agency.named("테스트기관-사전등록"));
        agencyAliasRepository.save(AgencyAlias.of("테스트기관-사전등록", existing));
        long agencyCountBefore = agencyRepository.count();

        // 실행: 같은 표기가 본문 끝에 있는 알림 1건을 해석한다
        Map<String, Agency> resolved = agencyService.resolveByMessages(
                List.of("호우 안내입니다. [테스트기관-사전등록]"));

        // 검증: 표기 → 미리 저장한 바로 그 기관(id 일치), 기관 행은 그대로(새로 만들지 않음)
        assertThat(resolved.get("테스트기관-사전등록")).isNotNull();
        assertThat(resolved.get("테스트기관-사전등록").getId()).isEqualTo(existing.getId());
        assertThat(agencyRepository.count()).isEqualTo(agencyCountBefore);
    }

    /**
     * 처음 보는 표기는 기관(이름 = 표기)과 표기 행을 함께 자동 등록한다. 같은 배치에 같은 표기가 두 번 나와도
     * 기관은 하나여야 한다 — 배치 안에서 중복을 걸러내지 않으면 기관이 알림 수만큼 생긴다.
     * 뼈대는 빈 Map 이라 결과에 기관이 없어 단언에서 실패한다.
     */
    @Test
    @DisplayName("처음 보는 표기는 기관과 표기를 자동 등록하고, 같은 표기가 반복돼도 기관은 하나다")
    void 처음_보는_표기는_자동_등록된다() {
        long agencyCountBefore = agencyRepository.count();

        // 실행: 같은 새 표기가 서로 다른 본문 2건에 나온다(하나는 끝, 하나는 맨 앞 형태 — 둘 다 같은 표기로 뽑힌다)
        Map<String, Agency> resolved = agencyService.resolveByMessages(List.of(
                "폭염 주의하세요. [테스트기관-신규]",
                "[테스트기관-신규] 한파 대비하세요."));

        // 검증 1: 자동 등록된 기관의 이름은 표기 그대로
        Agency created = resolved.get("테스트기관-신규");
        assertThat(created).isNotNull();
        assertThat(created.getName()).isEqualTo("테스트기관-신규");
        // 검증 2: agency_alias 에서 그 표기가 같은 기관을 가리킨다(DB 에서 다시 읽어 확인)
        AgencyAlias alias = agencyAliasRepository.findById("테스트기관-신규").orElseThrow();
        assertThat(alias.getAgency().getId()).isEqualTo(created.getId());
        // 검증 3: 표기가 2번 나왔어도 새로 생긴 기관은 1개
        assertThat(agencyRepository.count()).isEqualTo(agencyCountBefore + 1);
    }

    /**
     * 표기가 없는 본문은 기관을 만들지 않고 결과에도 넣지 않는다(호출한 쪽이 알림의 agency 를 null 로 둔다).
     * 빈 Map 뼈대에서도 "없는 쪽이 비어 있다"는 우연히 통과하므로, 표기 있는 본문과 함께 넣어
     * "표기 있는 쪽 하나만 결과에 있다"를 단언한다 — 뼈대는 그 하나도 돌려주지 않아 실패한다.
     */
    @Test
    @DisplayName("표기가 없는 본문은 결과에 항목이 없고 기관도 만들지 않는다")
    void 표기_없는_본문은_결과에서_빠진다() {
        long agencyCountBefore = agencyRepository.count();

        // 실행: 표기 있는 본문 1건과 대괄호가 아예 없는 본문 1건을 한 배치로 넣는다
        Map<String, Agency> resolved = agencyService.resolveByMessages(List.of(
                "대설 주의보입니다. [테스트기관-혼합]",
                "발신 기관 표시가 없는 문자입니다."));

        // 검증: 결과 키는 표기 있는 쪽 하나뿐, 기관도 그 하나만 새로 생겼다
        assertThat(resolved.keySet()).containsExactly("테스트기관-혼합");
        assertThat(agencyRepository.count()).isEqualTo(agencyCountBefore + 1);
    }
}
