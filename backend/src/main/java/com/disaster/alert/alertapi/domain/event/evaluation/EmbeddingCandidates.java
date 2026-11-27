package com.disaster.alert.alertapi.domain.event.evaluation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 임베딩 모델 비교(specs/012)의 측정 대상 목록. <b>후보를 바꿀 때는 아래 {@link #ALL} 을 직접 고친다</b> —
 * 주소·모델·차원을 여기 적어 두므로 실행 명령에는 {@code --eval.embedding-pairs=<별칭>} 만 주면 된다.
 *
 * <p>별칭은 거리 파일 이름(embedding-pairs-&lt;별칭&gt;.csv)이 된다. 지시문은 TEI 의 {@code --default-prompt} 로 서버에 준다(이 목록에는 없다).
 *
 * <p>호출은 모두 로컬 LiteLLM 프록시({@link #LITELLM_URL})로 보낸다. 프록시가 모델 이름으로 OpenAI 나 TEI 컨테이너로 넘기고,
 * 호출마다 모델·토큰·비용을 기록해 Grafana 로 본다(infra/llm-usage/README.md). 모델 이름은 프록시 설정
 * (infra/llm-usage/litellm-config.yaml)의 model_name 과 같아야 한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmbeddingCandidates {

    /**
     * @param alias               명령줄 값이자 파일 이름. 영문·숫자·_ 만
     * @param baseUrl             OpenAI 형식 /v1/embeddings 주소(지금은 모두 LiteLLM 프록시). null 이면 다시 임베딩하지 않는다(저장 임베딩)
     * @param modelId             요청에 넣을 모델 이름(프록시 설정의 model_name)
     * @param dimensions          요청에 넣을 차원
     * @param usdPerMillionTokens 100만 입력 토큰당 달러(2026-11-20 OpenAI 가격표, research R4). 공개 모델은 0.
     *                            호출 전 예상 비용과 승인 판정에 쓴다 — 실제 비용은 프록시 기록(LiteLLM_SpendLogs.spend)이 원본이다
     */
    public record EmbeddingModelCandidate(String alias, String baseUrl, String modelId, Integer dimensions, double usdPerMillionTokens) {

        /**
         * 유료라 --eval.approve-paid-embedding 이 있어야 호출하는 후보. 주소가 아니라 단가로 가른다 — 모든 후보가 같은 프록시 주소를
         * 쓰고, 주소 문자열 비교는 끝 슬래시 하나로 게이트가 풀린다. 단가를 모르는 유료 모델을 더할 때는 0 이 아닌 값을 적는다
         */
        public boolean isPaid() {
            return usdPerMillionTokens > 0;
        }

        public boolean isStored() {
            return baseUrl == null;
        }
    }

    /** 로컬 LiteLLM 프록시(docker-compose.dev.yml 의 llm-usage 프로필). 127.0.0.1 에만 열린다. */
    public static final String LITELLM_URL = "http://localhost:4000";
    /** 로컬 전용 프록시 키(docker-compose.dev.yml 의 LITELLM_MASTER_KEY). 진짜 OpenAI 키는 프록시 컨테이너만 가진다. */
    public static final String LITELLM_KEY = "sk-local-llm-usage";
    /** 프록시 기록(request_tags)에서 이 실험의 호출만 걸러 보기 위한 태그. 별칭도 함께 붙는다. */
    public static final String USAGE_TAG = "012-embedding-pairs";

    /** 평가 사본에 저장된 임베딩(text-embedding-3-small 로 만든 것). 모든 비교의 기준이며 다시 임베딩하지 않는다. */
    public static final EmbeddingModelCandidate STORED = new EmbeddingModelCandidate("stored", null, null, null, 0);
    /** 기준선 모델을 다시 불러 저장 임베딩과 쌍별로 맞는지 보는 재현 확인용. */
    public static final EmbeddingModelCandidate REPRODUCTION = new EmbeddingModelCandidate("3small", LITELLM_URL, "text-embedding-3-small", 1536, 0.02);

    /** 요약을 값 없이 주면 이 순서로 모두 읽는다. */
    public static final List<EmbeddingModelCandidate> ALL = List.of(
            STORED,
            REPRODUCTION,
            new EmbeddingModelCandidate("3large", LITELLM_URL, "text-embedding-3-large", 1536, 0.13),
            // TEI 컨테이너(포트 8081~8083, contracts 1절)는 프록시가 넘겨준다
            new EmbeddingModelCandidate("qwen3_06b", LITELLM_URL, "Qwen/Qwen3-Embedding-0.6B", 1024, 0),
            new EmbeddingModelCandidate("e5_large_instruct", LITELLM_URL, "intfloat/multilingual-e5-large-instruct", 1024, 0),
            new EmbeddingModelCandidate("bge_m3_ko", LITELLM_URL, "dragonkue/BGE-m3-ko", 1024, 0)
    );

    /** 별칭으로 찾는다. 모르는 별칭이면 쓸 수 있는 별칭을 함께 알린다(오타로 엉뚱한 파일이 생기지 않게). */
    public static EmbeddingModelCandidate find(String alias) {
        return ALL.stream()
                .filter(c -> c.alias().equals(alias))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("모르는 임베딩 후보 별칭입니다: " + alias + " (쓸 수 있는 별칭: "
                        + ALL.stream().map(EmbeddingModelCandidate::alias).collect(Collectors.joining(",")) + ")"));
    }

    /** "a,b,c" 를 후보 목록으로. 빈 값이면 전체({@link #ALL}). */
    public static List<EmbeddingModelCandidate> parseList(String csv) {
        if (csv == null || csv.isBlank()) {
            return ALL;
        }
        return Arrays.stream(csv.split(",")).map(String::trim).map(EmbeddingCandidates::find).distinct().toList();
    }
}
