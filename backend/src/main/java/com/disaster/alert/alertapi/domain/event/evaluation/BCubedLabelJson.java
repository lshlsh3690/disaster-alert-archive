package com.disaster.alert.alertapi.domain.event.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * B-cubed 정답 JSON 텍스트를 BCubedGold 로 바꾼다. 형식 오류·검토 미완료는 IllegalArgumentException.
 *
 * <p>형식: {@code {"events":[{"id":7,"alertIds":[1,2]}], "solo":[4,5], "unsure":[6], "unreviewed":[]}}.
 * window·exportedAt 같은 사람이 보려는 필드는 읽지 않고 버린다.
 * 정답 파일은 점수의 기준이라, 조금이라도 의심스러우면(검토 미완료·중복·누락) 조용히 넘기지 않고 실패한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BCubedLabelJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static BCubedGold parse(String jsonText) {
        // 1) BOM 제거 — 윈도우 편집기 저장본은 맨 앞에 BOM 이 붙어 JSON 파싱이 깨진다(LabelJson 과 같은 처리)
        String text = jsonText.startsWith("﻿") ? jsonText.substring(1) : jsonText;
        JsonNode root = readRoot(text);

        // 2) 검토가 끝나지 않은 정답으로는 점수를 내지 않는다 — 안 본 알림은 점수에서 조용히 빠져 숫자가 틀어진다
        JsonNode unreviewed = requireArray(root, "unreviewed");
        if (!unreviewed.isEmpty()) {
            throw new IllegalArgumentException("아직 검토하지 않은 알림이 " + unreviewed.size()
                    + "건 남아 있습니다(unreviewed): 라벨링을 끝낸 뒤 다시 내보내세요");
        }

        Map<Long, Long> clusterOf = new HashMap<>();
        Set<Long> unsure = new HashSet<>();
        // 3) events: 같은 event id 의 알림은 같은 키. 알림 하나가 두 곳에 나오면 어느 쪽이 맞는지 알 수 없어 실패한다
        long maxEventId = readEvents(requireArray(root, "events"), clusterOf);
        // 4) solo: 혼자인 알림마다 서로 다른 키를 준다. events 의 id 보다 큰 값부터 매겨 겹치지 않게 한다
        long nextSoloKey = maxEventId + 1;
        for (JsonNode node : requireArray(root, "solo")) {
            putOnce(clusterOf, readLong(node, "solo"), nextSoloKey++, "solo");
        }
        // 5) unsure: 점수에서 빼는 알림. events·solo 에 또 나오면 사람이 두 번 판단한 것이라 실패한다
        for (JsonNode node : requireArray(root, "unsure")) {
            long alertId = readLong(node, "unsure");
            if (clusterOf.containsKey(alertId) || !unsure.add(alertId)) {
                throw new IllegalArgumentException("같은 알림이 정답 파일에 두 번 이상 나옵니다(unsure): " + alertId);
            }
        }
        return new BCubedGold(clusterOf, unsure);
    }

    // events 를 clusterOf 에 채우고 가장 큰 event id 를 돌려준다(solo 키 시작점 계산용)
    private static long readEvents(JsonNode events, Map<Long, Long> clusterOf) {
        long maxEventId = 0;
        Set<Long> eventIds = new HashSet<>();
        for (JsonNode event : events) {
            long eventId = readLong(event.path("id"), "events[].id");
            // event id 가 겹치면 서로 다른 두 사건이 한 키로 합쳐져 정답이 달라진다
            if (!eventIds.add(eventId)) {
                throw new IllegalArgumentException("같은 event id 가 두 번 나옵니다(events): " + eventId);
            }
            maxEventId = Math.max(maxEventId, eventId);
            for (JsonNode alert : requireArray(event, "alertIds")) {
                putOnce(clusterOf, readLong(alert, "events[].alertIds"), eventId, "events");
            }
        }
        return maxEventId;
    }

    private static void putOnce(Map<Long, Long> clusterOf, long alertId, long key, String where) {
        if (clusterOf.putIfAbsent(alertId, key) != null) {
            throw new IllegalArgumentException("같은 알림이 정답 파일에 두 번 이상 나옵니다(" + where + "): " + alertId);
        }
    }

    private static JsonNode readRoot(String text) {
        JsonNode root;
        try {
            root = MAPPER.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON 을 읽을 수 없습니다: " + e.getOriginalMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("최상위가 객체여야 합니다");
        }
        return root;
    }

    // 없는 필드(MissingNode)와 배열이 아닌 필드가 같은 검사로 걸린다
    private static JsonNode requireArray(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (!node.isArray()) {
            throw new IllegalArgumentException("필수 필드가 없거나 배열이 아닙니다: " + field);
        }
        return node;
    }

    private static long readLong(JsonNode node, String field) {
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new IllegalArgumentException("알림 id(" + field + ") 가 숫자가 아니거나 없습니다: " + node);
        }
        return node.asLong();
    }
}
