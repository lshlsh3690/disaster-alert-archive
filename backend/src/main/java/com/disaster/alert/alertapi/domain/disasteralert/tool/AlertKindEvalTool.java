package com.disaster.alert.alertapi.domain.disasteralert.tool;

import com.disaster.alert.alertapi.domain.disasteralert.model.AlertKind;
import com.disaster.alert.alertapi.domain.disasteralert.model.AlertKindScore;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.disasteralert.service.AlertKindClassifier;
import com.disaster.alert.alertapi.global.util.ToolOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 사건/비사건 분류기를 사람 라벨과 대조해 결과를 로그로 남기는 평가 도구. 실제 {@link AlertKindClassifier} 를 그대로
 * 부르고 DB 에는 아무것도 저장하지 않는다. 라벨된 알림 1건당 LLM 1회 호출(약 $0.0003).
 *
 * <p><b>옵션</b>
 * <ul>
 *   <li>{@code --alert-kind-eval.labels=a.json,b.json}: 필수. {@code [{"id":..,"label":"사건|특보|안내|비사건|UNSURE"}]} 형식</li>
 *   <li>{@code --alert-kind-eval.override=recheck.json}: 선택. 같은 형식의 재판정 라벨, 앞 파일보다 우선</li>
 * </ul>
 * 알림 원문은 연결된 DB(평가 사본)에서 읽는다. 끝나면 앱을 종료한다.
 * <pre>
 * ./gradlew bootRun --args='--spring.profiles.active=alert-kind-eval --alert-kind-eval.labels=evaluation/datasets/alert-kind-val-labels.json --alert-kind-eval.override=evaluation/datasets/alert-kind-recheck-labels.json'
 * </pre>
 */
@Component
@Profile("alert-kind-eval")
@RequiredArgsConstructor
@Slf4j
public class AlertKindEvalTool implements ApplicationRunner {

    private static final int MESSAGE_PREVIEW = 80;

    private final DisasterAlertRepository disasterAlertRepository;
    private final AlertKindClassifier alertKindClassifier;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        List<String> labelFiles = ToolOptions.optionList(args, "alert-kind-eval.labels");
        if (labelFiles.isEmpty()) {
            throw new IllegalArgumentException("--alert-kind-eval.labels 옵션이 필요합니다");
        }
        // 1) 라벨 읽기 — 재판정 파일은 평가 대상 id 의 라벨만 덮어쓰고, 모르겠음은 빠진다
        Map<Long, AlertKind> human = AlertKindLabelFiles.read(
                labelFiles, ToolOptions.optionList(args, "alert-kind-eval.override"));

        // 2) 원문은 DB 에서, 분류는 운영과 같은 분류기로(저장 안 함)
        Map<Long, DisasterAlert> alerts = disasterAlertRepository.findAllById(human.keySet()).stream()
                .collect(Collectors.toMap(DisasterAlert::getId, Function.identity()));
        log.info("AlertKindEvalTool 시작: 라벨 {}건, DB 에서 찾은 알림 {}건 (LLM 호출 {}회)",
                human.size(), alerts.size(), alerts.size());
        List<AlertKindScore.Item> items = new ArrayList<>();
        List<Long> notInDb = new ArrayList<>();
        human.forEach((id, kind) -> {
            DisasterAlert a = alerts.get(id);
            if (a == null) {
                log.warn("알림 {} 이 DB 에 없어 건너뜁니다", id);
                notInDb.add(id);
                return;
            }
            AlertKind predicted = alertKindClassifier.classify(a.getDisasterType(), a.getMessage()).orElse(null);
            items.add(new AlertKindScore.Item(id, kind, predicted));
        });

        // 3) 보고서는 로그로
        log.info("사건/비사건 분류 평가 결과\n{}", report(labelFiles, notInDb, AlertKindScore.score(items), alerts));
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    private static String report(List<String> files, List<Long> notInDb, AlertKindScore.Result r,
                                 Map<Long, DisasterAlert> alerts) {
        StringBuilder sb = new StringBuilder();
        sb.append("라벨 파일: ").append(String.join(", ", files)).append('\n');
        sb.append("- DB 에 없어 제외 ").append(notInDb.size()).append("건").append(notInDb.isEmpty() ? "" : " " + notInDb)
                .append('\n');
        sb.append("- 사건 ").append(r.incidents()).append("건 중 비사건으로 분류 ")
                .append(r.incidentAsAdvisory().size()).append("건\n");
        sb.append("- 비사건 ").append(r.advisories()).append("건 중 사건으로 분류 ")
                .append(r.advisoryAsIncident().size()).append("건\n");
        sb.append("- 분류 실패(미분류) ").append(r.failed().size()).append("건\n");
        appendRows(sb, "사람=사건, 분류기=비사건", r.incidentAsAdvisory(), alerts);
        appendRows(sb, "사람=비사건, 분류기=사건", r.advisoryAsIncident(), alerts);
        appendRows(sb, "분류 실패", r.failed(), alerts);
        return sb.toString();
    }

    private static void appendRows(StringBuilder sb, String title, List<Long> ids, Map<Long, DisasterAlert> alerts) {
        if (ids.isEmpty()) {
            return;
        }
        sb.append('\n').append(title).append('\n');
        for (Long id : ids) {
            DisasterAlert a = alerts.get(id);
            // 본문이 없으면 분류기가 호출하지 않고 실패로 돌려준다 — 그 행에서 보고서가 깨지면 안 된다
            String msg = Objects.toString(a.getMessage(), "(본문 없음)").replaceAll("\\s+", " ").trim();
            sb.append("  ").append(id).append(" [").append(a.getDisasterType()).append("] ")
                    .append(msg, 0, Math.min(MESSAGE_PREVIEW, msg.length())).append('\n');
        }
    }
}
