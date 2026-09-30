package com.disaster.alert.alertapi.domain.notification.repository;

import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import com.disaster.alert.alertapi.domain.member.repository.MemberRepository;
import com.disaster.alert.alertapi.domain.notification.dto.MemberToken;
import com.disaster.alert.alertapi.domain.notification.model.FcmToken;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * {@link FcmTokenRepository#findTokensByMemberIdIn} 벌크 조회 테스트.
 *
 * <p>{@code NotificationPreferenceRepositoryTest} 와 같은 배경 — 회원별 {@code findAllByMemberId}
 * 건별 호출을 한 번의 IN 쿼리 + JPQL 생성자 프로젝션(DTO)으로 대체해 영속성 컨텍스트 팽창을 막는다.
 */
@IntegrationTest
class FcmTokenRepositoryTest {

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @Transactional
    @DisplayName("토큰이 등록된 회원의 토큰만 모두 반환한다 — 토큰 0개인 회원은 결과에서 아예 빠지고, " +
            "여러 토큰을 가진 회원은 그만큼 여러 건으로 나와야 한다")
    void 토큰이_있는_회원의_토큰만_모두_반환하고_각각_정확히_매칭된다() {
        // given
        Member memberA = memberRepository.save(newMember("two-tokens"));
        Member memberB = memberRepository.save(newMember("no-token")); // 토큰 없음
        Member memberC = memberRepository.save(newMember("one-token"));

        String tokenA1 = uniqueToken();
        String tokenA2 = uniqueToken();
        String tokenC1 = uniqueToken();

        fcmTokenRepository.save(FcmToken.builder().member(memberA).token(tokenA1).deviceType("WEB").build());
        fcmTokenRepository.save(FcmToken.builder().member(memberA).token(tokenA2).deviceType("ANDROID").build());
        fcmTokenRepository.save(FcmToken.builder().member(memberC).token(tokenC1).deviceType("WEB").build());
        fcmTokenRepository.flush();

        // when
        List<MemberToken> result =
                fcmTokenRepository.findTokensByMemberIdIn(
                        List.of(memberA.getId(), memberB.getId(), memberC.getId()));

        // then
        assertThat(result).hasSize(3);
        assertThat(result)
                .extracting(MemberToken::memberId, MemberToken::token)
                .containsExactlyInAnyOrder(
                        tuple(memberA.getId(), tokenA1),
                        tuple(memberA.getId(), tokenA2),
                        tuple(memberC.getId(), tokenC1));
    }

    private Member newMember(String label) {
        String unique = label + "-" + UUID.randomUUID();
        return Member.create(unique + "@test.com", "password", unique, MemberRole.USER);
    }

    private String uniqueToken() {
        return "token-" + UUID.randomUUID();
    }
}
