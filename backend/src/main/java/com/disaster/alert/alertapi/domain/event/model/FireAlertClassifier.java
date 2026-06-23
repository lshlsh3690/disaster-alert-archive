package com.disaster.alert.alertapi.domain.event.model;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;

import java.util.regex.Pattern;

/**
 * 산불 알림을 실제 사건(INCIDENT) / 안내성(ADVISORY)으로 분류 — 안내성 분리 클러스터링의 게이트.
 *
 * <p>산불 알림의 ~85%는 실제 화재가 아니라 건조특보·소각금지·입산통제·벌금/과태료·예방캠페인 안내다
 * (실데이터: 6,639건 중 안내 ~5,658 / 사건 ~981). 안내성은 건기 내내 같은 시군구에서 반복 발령돼,
 * 지역앵커 유형 머지(REGIONAL_TYPE, 14일 윈도우)에 그대로 태우면 시즌 전체가 하나로 체이닝된
 * blob(span 130일+)이 되고 소수의 실제 산불 이벤트도 오염시킨다.
 *
 * <p><b>분류 방향과 비대칭 위험</b>: 우리가 막아야 할 것은 <i>안내성이 사건 버킷에 끼는 것</i>
 * (blob 재발)이므로 INCIDENT 판정의 <b>정밀도</b>를 우선한다. 반대(실사건이 안내 버킷으로)는
 * 아카이브엔 남고 라벨만 '안내'가 되는 soft loss라 blob을 만들지 않는다. 그래서 규칙은 보수적 —
 * 안내성 정형문이 절대 안 가지는 강신호만 INCIDENT로 본다.
 *
 * <p>추출 정규식은 Postgres {@code ~} 매칭과 공유 가능하도록 POSIX 안전 문자클래스만 쓴다.
 */
public final class FireAlertClassifier {

    /**
     * 강신호 — 안내 정형문이 (거의) 안 쓰는 사건 표현. 대피(실제 대피령) + 진화/진압/완진/소진(진화
     * 상태) + 주불(주된 불) + 불길. 실측: 이 마커가 걸린 LEVEL_1 알림은 예방 보일러플레이트가
     * 덧붙어도 대부분 실제 산불 사건이었다(예: "용전리 산14 산불 진화 완료. 산불 예방에 협조").
     *
     * <p><b>{@code 잔불} 제외</b>: "화목보일러 사용 후 잔불 완전 소화/확인" 처럼 예방 안내문이 흔히
     * 쓰는 단어라 안내성을 사건으로 오인하게 만든다(실측 66건 오탐). 실제 화재의 잔불은 거의 항상
     * 진화·주불을 동반하므로 제거해도 recall 손실은 무시할 수준.
     */
    private static final Pattern INCIDENT_MARKER =
            Pattern.compile("대피|진화|진압|완진|소진|주불|불길");

    /**
     * 미세위치 — 실제 산불은 항상 발화 지점을 명명한다(예: "산24-5", "153번지", "배양리 산96").
     * 안내성 예방문은 시군구만 언급하고 미세위치가 없다. 전화·신고번호 오탐을 피하려고 막연한
     * {@code [0-9]+-[0-9]+}는 안 쓰고, 산N·N번지·(읍면동리)+번지꼴만 본다.
     */
    private static final Pattern MICRO_LOCATION =
            Pattern.compile("산[ ]?[0-9]+|[0-9]+번지|(리|동|읍|면)[ ]?[가-힣0-9]{0,6}[0-9]+-[0-9]+");

    /** 산불/화재 발생 언급 — 미세위치와 함께 있어야 사건. */
    private static final Pattern FIRE_OCCURRENCE = Pattern.compile("산불|화재");

    /**
     * 안내 조건문 — "발생 위험/우려/가능", "발생하지 않도록", 예방·소각·건조특보·위기경보·위험지수.
     * 미세위치(전화번호 등)가 우연히 걸려도 이 신호가 있으면 안내로 되돌린다(precision 보호).
     */
    private static final Pattern ADVISORY_CONDITION =
            Pattern.compile("발생(위험|우려|가능|하지|할|행위|시|률)|예방|소각|건조특보|위기경보|위험지수");

    private FireAlertClassifier() {
    }

    /**
     * 산불 안내성 여부 — 사건(INCIDENT)이 아니면 안내(ADVISORY). 산불 유형에만 쓴다.
     *
     * @param message 알림 본문
     * @param level   긴급도(긴급/위급재난문자=LEVEL_2/3 는 정부가 위급으로 분류한 실사건)
     */
    public static boolean isAdvisory(String message, DisasterLevel level) {
        return !isIncident(message, level);
    }

    /**
     * 실제 산불 사건 여부 — (1) 긴급/위급재난문자, (2) 강신호(대피·진화 등), 또는
     * (3) 미세위치 + 산불/화재 언급이되 안내 조건문이 아닐 때.
     */
    public static boolean isIncident(String message, DisasterLevel level) {
        if (level == DisasterLevel.LEVEL_2 || level == DisasterLevel.LEVEL_3) {
            return true;
        }
        if (message == null || message.isBlank()) {
            return false;
        }
        if (INCIDENT_MARKER.matcher(message).find()) {
            return true;
        }
        return MICRO_LOCATION.matcher(message).find()
                && FIRE_OCCURRENCE.matcher(message).find()
                && !ADVISORY_CONDITION.matcher(message).find();
    }
}
