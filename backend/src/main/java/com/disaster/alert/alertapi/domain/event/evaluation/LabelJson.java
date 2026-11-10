package com.disaster.alert.alertapi.domain.event.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 라벨 JSON 텍스트를 쌍 목록으로 바꾼다. 형식 오류는 "N번째 항목" 을 담은 IllegalArgumentException.
 * 사람이 읽으려고 붙인 필드(원문·시각 등)는 읽고 버린다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LabelJson {

    public enum Label { SAME, DIFF, UNSURE }

    /** 라벨된 알림 쌍. 사람이 보려는 필드(원문·시각 등)는 읽고 버리므로 담지 않는다. */
    public record LabeledPair(long alertA, long alertB, Label label) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * JSON 배열 전체를 읽어 쌍 목록을 만든다. 한 항목이라도 틀리면 건너뛰지 않고 즉시 예외 —
     * 조용히 버리면 평가 숫자가 슬그머니 달라지기 때문이다. 결과는 입력 순서 그대로다.
     */
    public static List<LabeledPair> parse(String jsonText) {
        // 1) BOM 제거 — 윈도우 편집기 저장본은 맨 앞에 BOM 이 붙어 JSON 파싱이 깨진다
        String text = jsonText.startsWith("﻿") ? jsonText.substring(1) : jsonText;

        // 2) JSON 자체가 깨졌으면 항목 번호를 댈 수 없으므로 번호 없이 알린다
        JsonNode root;
        try {
            root = MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 을 읽을 수 없습니다: " + e.getOriginalMessage(), e);
        }
        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException("최상위가 배열이어야 합니다");
        }

        List<LabeledPair> pairs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int itemNo = 0;
        for (JsonNode item : root) {
            // 3) 번호는 1부터 — 편집기에서 "몇 번째 항목" 을 세는 방식과 맞춘다
            itemNo++;
            pairs.add(parseItem(item, itemNo, seen));
        }
        return pairs;
    }

    private static LabeledPair parseItem(JsonNode item, int itemNo, Set<String> seen) {
        long a = readId(item.path("a").path("id"), "a.id", itemNo);
        long b = readId(item.path("b").path("id"), "b.id", itemNo);
        if (a == b) {
            throw error(itemNo, "같은 알림끼리의 쌍입니다: " + a);
        }
        Label label = readLabel(item.path("label"), itemNo);

        // 4) (a,b)/(b,a) 를 같은 키로 만들어 중복을 잡는다 — 뒤쪽 항목이 지목된다
        String key = Math.min(a, b) + ":" + Math.max(a, b);
        if (!seen.add(key)) {
            throw error(itemNo, "이미 나온 쌍입니다(순서 무관): " + a + "," + b);
        }
        return new LabeledPair(a, b, label);
    }

    // 없는 필드는 path() 가 MissingNode 를 돌려주므로 "없음" 과 "숫자 아님" 이 같은 검사로 걸린다
    private static long readId(JsonNode node, String field, int itemNo) {
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw error(itemNo, "알림 id(" + field + ") 가 숫자가 아니거나 없습니다: " + node);
        }
        return node.asLong();
    }

    private static Label readLabel(JsonNode node, int itemNo) {
        try {
            if (!node.isTextual()) {
                throw new IllegalArgumentException();
            }
            return Label.valueOf(node.asText().trim());
        } catch (IllegalArgumentException e) {
            throw error(itemNo, "알 수 없는 label 입니다(SAME/DIFF/UNSURE): " + node);
        }
    }

    private static IllegalArgumentException error(int itemNo, String reason) {
        return new IllegalArgumentException(itemNo + "번째 항목: " + reason);
    }
}
