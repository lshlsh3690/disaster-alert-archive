package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 클러스터링 성능 평가의 정답을 매기는 판사(LLM-as-a-Judge). 설계안 ③ 참조
 * (docs/event-clustering/ADMIN_EVAL_DASHBOARD_DESIGN.md).
 * 평가 회차와 판사 신뢰도 측정 도구가 부른다. 실패·해석 불가는 예외 대신 empty(=판사 판정 불가)로 돌려줘
 * 호출자가 추측 없이 따로 센다.
 * 공유 ChatModel 의 기본 모델(gpt-4o-mini)이 아니라 호출마다 {@link #MODEL} 로 지정한다 —
 * 클러스터링 LLM 폴백과 같은 모델이면 같은 실수를 같이 해서 판사가 오류를 못 잡는다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@Service
public class ClusteringJudge {

    static final String MODEL = "gpt-4o";
    static final String PAIR_PROMPT_RESOURCE = "judge/pair-system-prompt.txt";
    static final String KIND_PROMPT_RESOURCE = "judge/kind-system-prompt.txt";

    private final ChatModel chatModel;

    public ClusteringJudge(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /** @return 두 알림이 같은 사건인지. LLM 실패·해석 불가면 empty. */
    public Optional<PairVerdict> judgePair(DisasterAlert earlier, DisasterAlert later) {
        return Optional.empty();
    }

    /** @return 알림이 사건인지 비사건인지. LLM 실패·해석 불가면 empty. */
    public Optional<KindVerdict> judgeKind(DisasterAlert alert) {
        return Optional.empty();
    }
}
