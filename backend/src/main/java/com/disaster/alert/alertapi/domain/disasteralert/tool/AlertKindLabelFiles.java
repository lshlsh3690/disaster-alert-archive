package com.disaster.alert.alertapi.domain.disasteralert.tool;

import com.disaster.alert.alertapi.domain.disasteralert.model.AlertKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 사건/비사건 사람 라벨 파일 읽기. 분류기 평가({@link AlertKindEvalTool})와 판사 신뢰도 측정
 * (JudgeReliabilityTool)이 같은 라벨 규칙을 쓰도록 한 곳에 둔다 — 따로 두면 한쪽 규칙만 바뀌어 두 측정이 조용히 어긋난다.
 *
 * <p>형식은 {@code [{"id":..,"label":"사건|특보|안내|비사건|UNSURE|모르겠음"}]}. 특보·안내는 비사건으로 합치고 모르겠음은 뺀다.
 */
public final class AlertKindLabelFiles {

    private AlertKindLabelFiles() {
    }

    /**
     * @param files         평가 대상 라벨 파일들. 뒤 파일이 같은 id 를 덮어쓴다
     * @param overrideFiles 재판정 라벨 파일들. 평가 대상에 이미 있는 id 의 라벨만 바꾸고 새 id 는 더하지 않는다
     *                      (재판정 파일은 여러 셋의 알림을 함께 담고 있다)
     * @return id → 사람 라벨. 모르겠음은 빠져 있다
     */
    public static Map<Long, AlertKind> read(List<String> files, List<String> overrideFiles) {
        Map<Long, AlertKind> human = new LinkedHashMap<>();
        files.forEach(f -> readInto(f, human));
        Map<Long, AlertKind> override = new LinkedHashMap<>();
        overrideFiles.forEach(f -> readInto(f, override));
        override.forEach((id, kind) -> {
            if (human.containsKey(id)) {
                human.put(id, kind);
            }
        });
        human.values().removeIf(v -> v == null);
        return human;
    }

    private static void readInto(String path, Map<Long, AlertKind> into) {
        try {
            int index = 0;
            for (JsonNode n : new ObjectMapper().readTree(new File(path))) {
                // 어느 파일의 몇 번째 항목이 틀렸는지 알 수 있게 위치를 붙여 멈춘다
                String where = path + " 의 " + index++ + "번째 항목";
                if (!n.hasNonNull("id") || !n.hasNonNull("label")) {
                    throw new IllegalArgumentException(where + "에 id 또는 label 이 없습니다: " + n);
                }
                long id = n.get("id").asLong();
                into.put(id, toKind(n.get("label").asText(), where + "(id=" + id + ")"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("라벨 파일을 읽을 수 없습니다: " + path, e);
        }
    }

    /** 사람 라벨 → 저장 값. 특보·안내는 비사건으로 합친다. 모르겠음은 null(평가에서 뺌). */
    private static AlertKind toKind(String label, String where) {
        return switch (label.trim()) {
            case "사건" -> AlertKind.INCIDENT;
            case "특보", "안내", "비사건" -> AlertKind.ADVISORY;
            case "UNSURE", "모르겠음" -> null;
            default -> throw new IllegalArgumentException(where + ": 알 수 없는 라벨 '" + label + "'");
        };
    }
}
