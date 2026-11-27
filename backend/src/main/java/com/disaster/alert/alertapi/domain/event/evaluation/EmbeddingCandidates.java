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
 * <p>별칭은 거리 파일 이름(embedding-pairs-&lt;별칭&gt;.csv)이 된다. 공개 모델 주소는 TEI 컨테이너 포트(contracts 1절)와 맞춘다.
 * 지시문은 TEI 의 {@code --default-prompt} 로 서버에 준다(이 목록에는 없다).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmbeddingCandidates {

    /**
     * @param alias               명령줄 값이자 파일 이름. 영문·숫자·_ 만
     * @param baseUrl             OpenAI 형식 /v1/embeddings 주소. null 이면 다시 임베딩하지 않는다(저장 임베딩)
     * @param modelId             요청에 넣을 모델 이름
     * @param dimensions          요청에 넣을 차원
     * @param usdPerMillionTokens 100만 입력 토큰당 달러(2026-11-20 OpenAI 가격표, research R4). 공개 모델은 0
     */
    public record EmbeddingModelCandidate(String alias, String baseUrl, String modelId, Integer dimensions, double usdPerMillionTokens) {

        /** 유료라 --eval.approve-paid-embedding 이 있어야 호출하는 후보. */
        public boolean isOpenAi() {
            return OPENAI.equals(baseUrl);
        }

        public boolean isStored() {
            return baseUrl == null;
        }
    }

    private static final String OPENAI = "https://api.openai.com";

    /** 평가 사본에 저장된 임베딩(text-embedding-3-small 로 만든 것). 모든 비교의 기준이며 다시 임베딩하지 않는다. */
    public static final EmbeddingModelCandidate STORED = new EmbeddingModelCandidate("stored", null, null, null, 0);
    /** 기준선 모델을 다시 불러 저장 임베딩과 쌍별로 맞는지 보는 재현 확인용. */
    public static final EmbeddingModelCandidate REPRODUCTION = new EmbeddingModelCandidate("3small", OPENAI, "text-embedding-3-small", 1536, 0.02);

    /** 요약을 값 없이 주면 이 순서로 모두 읽는다. */
    public static final List<EmbeddingModelCandidate> ALL = List.of(
            STORED,
            REPRODUCTION,
            new EmbeddingModelCandidate("3large", OPENAI, "text-embedding-3-large", 1536, 0.13),
            new EmbeddingModelCandidate("qwen3_06b", "http://localhost:8081", "Qwen/Qwen3-Embedding-0.6B", 1024, 0),
            new EmbeddingModelCandidate("e5_large_instruct", "http://localhost:8082", "intfloat/multilingual-e5-large-instruct", 1024, 0),
            new EmbeddingModelCandidate("bge_m3_ko", "http://localhost:8083", "dragonkue/BGE-m3-ko", 1024, 0)
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
