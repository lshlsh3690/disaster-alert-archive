package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.EmbeddingCandidates.EmbeddingModelCandidate;
import com.disaster.alert.alertapi.domain.event.evaluation.EmbeddingPairCsv.Content;
import com.disaster.alert.alertapi.domain.event.evaluation.EmbeddingPairCsv.Row;
import com.disaster.alert.alertapi.domain.event.evaluation.EmbeddingPairSubset.TypedPair;
import com.disaster.alert.alertapi.domain.event.model.EvaluationLabel;
import com.disaster.alert.alertapi.domain.event.repository.EvaluationLabelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 임베딩 모델 비교 1단계 측정({@code --eval.embedding-pairs}, {@code --eval.embedding-pairs-summary}, specs/012 contracts 3절).
 * 라벨 쌍 중 임베딩 거리로 병합이 정해지는 쌍(research R1)의 코사인 거리를 모델마다 재서 CSV 로 남기고,
 * 모은 CSV 로 두 지점 비교 리포트를 만든다. 후보(주소·모델·차원)는 {@link EmbeddingCandidates} 에 적어 두고, 운영과 같은
 * Spring AI OpenAI 임베딩 클라이언트를 후보마다 그 값으로 만들어 로컬 LiteLLM 프록시로 부른다(사용량이 프록시에 기록된다).
 *
 * <p>후보가 유료(단가 &gt; 0)이면 {@code --eval.approve-paid-embedding} 없이는 호출하지 않고 예상 비용만 보인다(spec FR-016).
 * 접속한 DB 는 평가 사본이어야 한다 — 이 클래스는 읽기만 한다.
 */
@Slf4j
@Component
@Profile("eval")
@RequiredArgsConstructor
public class EmbeddingPairRunner {

    // TEI 의 --max-client-batch-size 200 과 같다(contracts 1절). 이보다 크면 TEI 가 요청을 거부한다
    static final int BATCH_SIZE = 200;

    private final AssignmentReader assignmentReader;
    private final EvaluationLabelRepository labelRepository;
    private final JdbcTemplate jdbcTemplate;

    // 키 경로 유형 목록은 복사해 두지 않고 운영과 같은 설정에서 읽는다(research R1) — 설정이 바뀌면 대상 쌍도 따라 바뀐다
    @Value("${clustering.regional-types:산불:168,산사태:168,홍수:168,호우:72}")
    private String regionalTypesCsv;
    @Value("${clustering.sustained-types:폭염,한파,대설,강풍,가축질병}")
    private String sustainedTypesCsv;

    /** 대상 쌍 선별 결과와, 쌍에 나온 알림의 값(유형·원문). */
    private record Targets(EmbeddingPairSubset.Result subset, Map<Long, AlertSnapshot> alerts) {
    }

    /**
     * 모델 하나로 대상 쌍의 거리를 재 {@code <폴더>/embedding-pairs-<별칭>.csv} 에 쓴다.
     * STORED 면 평가 사본에 저장된 임베딩으로 SQL 거리만 재고(호출 없음), 그 밖이면 설정된 임베딩 모델로 다시 임베딩한다.
     *
     * @return 파일을 썼으면 true. OpenAI 후보인데 --eval.approve-paid-embedding 이 없어 예상 비용만 보이고 멈췄으면 false
     */
    public boolean measure(String dataset, EmbeddingModelCandidate candidate, Path folder, boolean approvePaidEmbedding) throws IOException {
        // 1) 라벨을 읽어 대상 쌍을 고른다 — 모든 모델이 같은 쌍을 재야 해서 모델과 상관없이 한 곳에서 정한다
        Targets targets = loadTargets(dataset);
        List<TypedPair> pairs = targets.subset().targets();
        log.info("임베딩 쌍 거리 측정: 셋 {}, 별칭 {}, 대상 {}쌍(제외: 키 경로 {}·먼 쌍 {}·모르겠음 {})", dataset, candidate.alias(), pairs.size(),
                targets.subset().excludedKeyPathType(), targets.subset().excludedNotNear(), targets.subset().excludedUnsure());
        Map<String, String> header = new LinkedHashMap<>();
        List<Row> rows;
        if (candidate.isStored()) {
            // 2) stored: 저장된 벡터를 DB 가 직접 비교한다(<=>) — 임베딩 호출·비용 없음
            header.put("model", "stored (평가 사본에 저장된 임베딩)");
            header.put("tokens", "0");
            header.put("cost_usd", "0");
            rows = pairs.stream().map(p -> toRow(p, storedDistance(p))).toList();
        } else {
            // 3) 다시 임베딩: 유료 후보면 승인 전에는 비용만 보이고 멈춘다
            if (candidate.isPaid() && !approvePaidEmbedding) {
                printEstimate(pairs, targets.alerts(), candidate);
                return false;
            }
            rows = embedRows(pairs, targets.alerts(), header, candidate);
        }
        Files.createDirectories(folder);
        Path file = csvPath(folder, candidate);
        Files.writeString(file, EmbeddingPairCsv.format(header, rows), StandardCharsets.UTF_8);
        log.info("거리 파일 저장: {} ({}쌍)", file, rows.size());
        return true;
    }

    /**
     * 모은 거리 파일들로 {@code <폴더>/embedding-pairs-summary.md} 를 만든다. stored 는 필수(모든 비교의 기준)고,
     * 3small 이 있으면 기준선 재현(쌍별 거리 차이 0.01 이하)을 확인한다.
     *
     * @return 기준선 재현에 실패했으면 false(호출한 쪽이 종료 코드 1 로 끝낸다). 리포트는 실패해도 경고를 붙여 쓴다
     */
    public boolean summarize(String dataset, List<EmbeddingModelCandidate> candidates, Path folder) throws IOException {
        if (!candidates.contains(EmbeddingCandidates.STORED)) {
            throw new IllegalArgumentException("--eval.embedding-pairs-summary 에 " + EmbeddingCandidates.STORED.alias()
                    + " 가 있어야 합니다(모든 비교의 기준)");
        }
        // 1) 같은 라벨에서 대상 쌍을 다시 골라 제외 수와 원문을 얻는다 — 거리 파일의 쌍·라벨이 지금과 다르면 낡은 파일이다
        Targets targets = loadTargets(dataset);
        Set<LabeledKey> expected = targets.subset().targets().stream()
                .map(p -> new LabeledKey(p.alertA(), p.alertB(), p.label())).collect(Collectors.toSet());
        Map<String, Content> files = new LinkedHashMap<>();
        for (EmbeddingModelCandidate candidate : candidates) {
            Content content = EmbeddingPairCsv.parse(Files.readString(csvPath(folder, candidate), StandardCharsets.UTF_8));
            requireCurrentLabels(candidate.alias(), content, expected);
            files.put(candidate.alias(), content);
        }
        // 2) 기준선 재현을 먼저 확인해 리포트 맨 위 경고와 종료 코드에 쓴다
        Content baseline = files.get(EmbeddingCandidates.STORED.alias());
        var reproduction = EmbeddingPairSummaryFormatter.reproduction(baseline, files.get(EmbeddingCandidates.REPRODUCTION.alias()));
        List<EmbeddingPairSummaryFormatter.Model> models = files.entrySet().stream()
                .filter(e -> !EmbeddingCandidates.STORED.alias().equals(e.getKey()))
                .map(e -> new EmbeddingPairSummaryFormatter.Model(e.getKey(), e.getValue())).toList();
        Map<Long, String> messages = new HashMap<>();
        targets.alerts().forEach((id, a) -> messages.put(id, a.message()));
        // 3) 리포트는 로그와 파일 둘 다에 쓴다
        String report = EmbeddingPairSummaryFormatter.format(new EmbeddingPairSummaryFormatter.Input(
                dataset, targets.subset(), baseline, models, messages, reproduction));
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("embedding-pairs-summary.md"), report, StandardCharsets.UTF_8);
        log.info("\n{}", report);
        return !reproduction.failed();
    }

    // 라벨(관계 포함)과 알림 유형을 읽어 대상 쌍을 고른다. 알림 id 가 DB 에 없으면 AssignmentReader 가 멈춘다(라벨과 사본이 어긋남)
    private Targets loadTargets(String dataset) {
        List<EvaluationLabel> labels = labelRepository.findByDatasetOrderByIdAsc(dataset);
        if (labels.isEmpty()) {
            throw new IllegalArgumentException("평가 셋 '" + dataset + "' 의 라벨이 이 DB 에 없습니다");
        }
        Map<Long, AlertSnapshot> alerts = new TreeMap<>();
        List<TypedPair> pairs = new ArrayList<>();
        for (EvaluationLabel l : labels) {
            // 작은 번호를 a 로 맞춘다(거리 파일 규칙) — 유형도 같은 순서로 따라간다
            long a = Math.min(l.getAlertAId(), l.getAlertBId());
            long b = Math.max(l.getAlertAId(), l.getAlertBId());
            AlertSnapshot first = alerts.computeIfAbsent(a, assignmentReader::findAlert);
            AlertSnapshot second = alerts.computeIfAbsent(b, assignmentReader::findAlert);
            pairs.add(new TypedPair(a, b, l.getLabel(), l.getRelation(), first.type(), second.type()));
        }
        EmbeddingPairSubset.Result subset = EmbeddingPairSubset.select(pairs, keyPathTypes(regionalTypesCsv, sustainedTypesCsv));
        return new Targets(subset, alerts);
    }

    // 거리 파일의 (쌍, 라벨) 묶음이 지금 대상 쌍과 정확히 같아야 한다. 쌍 수만 보면 관리자 화면에서 라벨만 SAME↔DIFF 로 고친 경우를
    // 놓쳐, 기준선과 후보가 서로 다른 라벨로 채점된 비교가 경고 없이 나온다
    private static void requireCurrentLabels(String alias, Content content, Set<LabeledKey> expected) {
        Set<LabeledKey> actual = content.rows().stream()
                .map(r -> new LabeledKey(r.alertA(), r.alertB(), r.label())).collect(Collectors.toSet());
        if (actual.equals(expected)) {
            return;
        }
        List<LabeledKey> stale = actual.stream().filter(k -> !expected.contains(k)).sorted(LabeledKey.ORDER).limit(5).toList();
        List<LabeledKey> missing = expected.stream().filter(k -> !actual.contains(k)).sorted(LabeledKey.ORDER).limit(5).toList();
        throw new IllegalStateException(alias + " 거리 파일의 쌍·라벨이 지금 라벨과 다릅니다(파일에만 있음 예: " + stale
                + ", 지금 라벨에만 있음 예: " + missing + ") — 라벨이 바뀌었으니 --eval.embedding-pairs=" + alias + " 로 다시 재야 합니다");
    }

    /** 낡은 거리 파일 확인용 (쌍, 라벨) 묶음. */
    private record LabeledKey(long alertA, long alertB, LabelJson.Label label) {
        static final Comparator<LabeledKey> ORDER = Comparator.comparingLong(LabeledKey::alertA).thenComparingLong(LabeledKey::alertB);

        @Override
        public String toString() {
            return alertA + "-" + alertB + " " + label;
        }
    }

    // "산불:168,호우:72" 에서 유형만, "폭염,한파" 는 그대로. 운영의 EventClusteringService 가 읽는 두 설정의 합집합이다.
    // HashSet 이라 유형이 빈(NULL) 알림을 contains 해도 예외 없이 false — TreeSet 은 null 비교에서 NPE 를 던진다
    static Set<String> keyPathTypes(String regionalCsv, String sustainedCsv) {
        Set<String> types = new HashSet<>();
        for (String token : regionalCsv.split(",")) {
            String type = token.split(":")[0].trim();
            if (!type.isEmpty()) {
                types.add(type);
            }
        }
        for (String token : sustainedCsv.split(",")) {
            if (!token.isBlank()) {
                types.add(token.trim());
            }
        }
        return types;
    }

    // 거리는 DB 의 <=> (코사인 거리)로 잰다. 한쪽 임베딩이 없으면 NULL — 그 쌍을 조용히 빼면 분모가 줄어 기준선이 달라지므로 멈춘다
    private double storedDistance(TypedPair p) {
        Double d = jdbcTemplate.queryForObject("SELECT a.embedding <=> b.embedding FROM disaster_alert a, disaster_alert b "
                + "WHERE a.disaster_alert_id = ? AND b.disaster_alert_id = ?", Double.class, p.alertA(), p.alertB());
        if (d == null) {
            throw new IllegalStateException("저장된 임베딩이 없는 쌍입니다: " + p.alertA() + ", " + p.alertB()
                    + " — 평가 사본이 맞는지 확인하세요");
        }
        return d;
    }

    // 대상 알림을 200건씩 임베딩하고 쌍마다 코사인 거리를 낸다. 머리에는 모델·주소·차원·시각·실제 토큰·비용을 남긴다
    private List<Row> embedRows(List<TypedPair> pairs, Map<Long, AlertSnapshot> alerts, Map<String, String> header,
                                EmbeddingModelCandidate candidate) {
        List<Long> ids = distinctIds(pairs);
        EmbeddingModel embeddingModel = buildModel(candidate);
        Map<Long, float[]> vectors = new HashMap<>();
        long tokens = 0;
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            List<Long> batch = ids.subList(from, Math.min(from + BATCH_SIZE, ids.size()));
            EmbeddingResponse response = embeddingModel.embedForResponse(batch.stream().map(id -> alerts.get(id).message()).toList());
            // 응답은 입력 순서 그대로다 — 개수가 다르면 쌍이 어긋나므로 그대로 쓰지 않는다
            if (response.getResults().size() != batch.size()) {
                throw new IllegalStateException("임베딩 응답 개수(" + response.getResults().size() + ")가 요청(" + batch.size() + ")과 다릅니다");
            }
            for (int i = 0; i < batch.size(); i++) {
                vectors.put(batch.get(i), response.getResults().get(i).getOutput());
            }
            var usage = response.getMetadata().getUsage();
            tokens += usage == null || usage.getTotalTokens() == null ? 0 : usage.getTotalTokens();
            log.info("임베딩 진행: {}/{}건", Math.min(from + BATCH_SIZE, ids.size()), ids.size());
        }
        fillHeader(header, vectors.values().iterator().next().length, tokens, candidate);
        return pairs.stream().map(p -> toRow(p, cosineDistance(vectors.get(p.alertA()), vectors.get(p.alertB())))).toList();
    }

    private void fillHeader(Map<String, String> header, int dimensions, long tokens, EmbeddingModelCandidate candidate) {
        header.put("model", candidate.modelId());
        header.put("base_url", candidate.baseUrl());
        header.put("dimensions", String.valueOf(dimensions));
        header.put("instruction", candidate.isPaid() ? "없음" : "서버(TEI --default-prompt) 설정이라 이 도구는 알 수 없음");
        header.put("run_at", LocalDateTime.now().toString());
        header.put("tokens", tokens == 0 ? "응답에 없음" : String.valueOf(tokens));
        header.put("cost_usd", !candidate.isPaid() ? "0" : tokens == 0 ? "-"
                : String.format(Locale.ROOT, "%.6f", tokens * candidate.usdPerMillionTokens() / 1_000_000));
    }

    // 예상 비용만 출력하고 호출하지 않는다. 토큰은 글자 수를 그대로 잡은 추정이라 실제와 다를 수 있다(실제 토큰은 호출 뒤 거리 파일 머리에 남는다)
    private void printEstimate(List<TypedPair> pairs, Map<Long, AlertSnapshot> alerts, EmbeddingModelCandidate candidate) {
        List<Long> ids = distinctIds(pairs);
        long chars = ids.stream().mapToLong(id -> alerts.get(id).message().length()).sum();
        log.info("유료 후보({})라 호출하지 않았습니다. 대상 알림 {}건, 글자 {}자(토큰은 글자 수로 추정), 모델 {}, 예상 비용 {}",
                candidate.alias(), ids.size(), chars, candidate.modelId(),
                String.format(Locale.ROOT, "$%.6f", chars * candidate.usdPerMillionTokens() / 1_000_000));
        log.info("호출하려면 --eval.approve-paid-embedding 을 더해 다시 실행하세요");
    }

    private static List<Long> distinctIds(List<TypedPair> pairs) {
        return pairs.stream().flatMap(p -> Stream.of(p.alertA(), p.alertB())).distinct().sorted().toList();
    }

    // 후보의 주소·모델·차원으로 클라이언트를 만든다. 운영 EmbeddingModel 빈과 같은 OpenAiEmbeddingModel 이고, 입력은 본문 그대로(EMBED).
    // 프록시 키로 부르고, 프록시 기록에서 이 실험·후보만 걸러 볼 수 있게 x-litellm-tags 를 붙인다
    private EmbeddingModel buildModel(EmbeddingModelCandidate candidate) {
        LinkedMultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("x-litellm-tags", EmbeddingCandidates.USAGE_TAG + "," + candidate.alias());
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(candidate.baseUrl())
                .apiKey(EmbeddingCandidates.LITELLM_KEY)
                .headers(headers)
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(candidate.modelId())
                .dimensions(candidate.dimensions())
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }

    // 코사인 거리 = 1 - 코사인 유사도. DB 의 <=> 와 같은 정의라 stored 와 3small 을 쌍별로 맞춰 볼 수 있다
    static double cosineDistance(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        return 1.0 - dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static Row toRow(TypedPair p, double distance) {
        return new Row(p.alertA(), p.alertB(), p.label(), EmbeddingPairCsv.typeText(p.typeA(), p.typeB()),
                EmbeddingPairCsv.relationText(p.relation()), distance);
    }

    private static Path csvPath(Path folder, EmbeddingModelCandidate candidate) {
        return folder.resolve("embedding-pairs-" + candidate.alias() + ".csv");
    }

}
