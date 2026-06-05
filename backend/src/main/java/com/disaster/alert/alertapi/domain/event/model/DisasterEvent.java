package com.disaster.alert.alertapi.domain.event.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 재난 이벤트 — 시간 흐름에 따라 N개 재난문자가 누적된 사건 단위.
 *
 * <p>클러스터링 규칙: 후보 검색(7일 윈도우 + 지역 교집합) 안에서 코사인 유사도 최대 후보가
 * 임계값을 넘으면 기존 이벤트에 머지. 못 넘으면 신규 이벤트.
 *
 * <p>대표 정보({@link #primaryDisasterType}, {@link #primaryRegionCode}, {@link #primaryRegionName})
 * 는 모두 첫 알림 기준 캐시. 화면 표시용 제목({@link #eventTitle}) 자동 생성.
 *
 * <p><b>상태(active)는 저장하지 않고 파생 계산</b>한다. "사건 종료" 는 우리가 알 수 없는 값
 * (행안부가 종료 신호 안 줌)이라 status 컬럼+스케줄러로 박제하지 않는다. 대신 유형에서 1회
 * 산정한 {@link #cooldownHours} 만 저장하고, 조회 시 {@link #isActive(LocalDateTime)} 로
 * {@code now - lastAlertAt < cooldown} 을 계산한다. 머지로 lastAlertAt 이 갱신되면 자동으로
 * 다시 active 로 돌아온다.
 */
@Entity
@Table(name = "disaster_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class DisasterEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_title", nullable = false, length = 200)
    private String eventTitle;

    @Column(name = "primary_disaster_type", length = 50)
    private String primaryDisasterType;

    @Column(name = "primary_region_code", length = 20)
    private String primaryRegionCode;

    @Column(name = "primary_region_name", length = 100)
    private String primaryRegionName;

    @Column(name = "first_alert_at", nullable = false)
    private LocalDateTime firstAlertAt;

    @Column(name = "last_alert_at", nullable = false)
    private LocalDateTime lastAlertAt;

    @Column(name = "alert_count", nullable = false)
    private int alertCount;

    /** 유형별 cooldown 시간. 생성 시 1회 산정, 이후 불변. active 파생 판정 기준. */
    @Column(name = "cooldown_hours", nullable = false)
    private int cooldownHours;

    /**
     * 광역 브로드캐스트 이벤트 여부. 한 알림이 다수 시군구에 발송된 광역 경보로 만들어진 이벤트.
     * true 면 다른 알림의 머지 후보에서 제외해 transitive blob 을 방지.
     */
    @Column(name = "is_broadcast", nullable = false)
    private boolean broadcast;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 제목 생성 시 disaster_type 이 정보 부족하다고 간주하는 값 목록. */
    private static final java.util.Set<String> UNINFORMATIVE_TYPES =
            java.util.Set.of("기타", "UNKNOWN", "etc", "ETC");

    /** 본문 fallback 사용 시 잘라낼 최대 길이. */
    private static final int MESSAGE_PREVIEW_LEN = 30;

    /**
     * disaster_type 이 사건을 식별할 만큼 정보가 있는지.
     * null/blank/"기타" 류는 false — 제목 본문 fallback, broadcast 시도+유형 병합 제외에 사용.
     */
    public static boolean isInformativeType(String disasterType) {
        return disasterType != null
                && !disasterType.isBlank()
                && !UNINFORMATIVE_TYPES.contains(disasterType);
    }

    /**
     * 첫 알림 기준 신규 이벤트 생성용 팩토리.
     *
     * <p>제목 자동 생성 규칙:
     * <ul>
     *   <li>disaster_type 이 의미 있을 때: {@code "{지역명} {유형} ({yyyy-MM-dd})"}</li>
     *   <li>disaster_type 이 "기타"/null 등 정보 부족 시: {@code "{지역명} {본문앞30자} ({yyyy-MM-dd})"}</li>
     * </ul>
     *
     * <p>본문 fallback 으로 "교통사고 도시철도 1호선 운행지연" 같은 사건 특성이 제목에 드러남.
     * 나중에 LLM 요약 PR 에서 자연어 제목으로 업그레이드 가능.
     */
    public static DisasterEvent createFromFirstAlert(
            String disasterType,
            String regionCode,
            String regionName,
            String message,
            LocalDateTime alertAt,
            boolean broadcast
    ) {
        String safeRegion = regionName == null ? "지역미상" : regionName;
        boolean typeInformative = isInformativeType(disasterType);

        // 유형 있으면 유형, '기타'면 본문에서 규칙으로 깔끔한 라벨 산출.
        String titleMiddle = typeInformative ? disasterType : gitaTitleMiddle(message);
        // 날짜는 제목에 박지 않는다 — 생성 시점에 고정돼 갱신이 안 되기 때문.
        // 화면은 응답의 first_alert_at / last_alert_at(갱신됨)으로 날짜 라벨을 표시한다.
        String title = String.format("%s %s", safeRegion, titleMiddle);

        return DisasterEvent.builder()
                .eventTitle(title)
                .primaryDisasterType(disasterType == null ? "UNKNOWN" : disasterType)
                .primaryRegionCode(regionCode)
                .primaryRegionName(safeRegion)
                .firstAlertAt(alertAt)
                .lastAlertAt(alertAt)
                .alertCount(1)
                .cooldownHours(DisasterCooldown.hoursFor(disasterType))
                .broadcast(broadcast)
                .build();
    }

    /**
     * '기타' 유형의 제목 가운데말 — 본문에서 규칙으로 깔끔한 라벨을 만든다.
     * (유형명이 없어 본문 앞부분을 그대로 잘라 쓰면 문장 중간에서 끊겨 지저분하므로.)
     *
     * <p>실종 인물은 이름+나이로(동일인 식별 로직 재사용), 동물·안전안내는 키워드로 라벨링.
     * 어디에도 안 걸리는 자유서술만 기존처럼 본문 앞부분을 쓴다.
     */
    private static String gitaTitleMiddle(String message) {
        if (message == null || message.isBlank()) {
            return "기타";
        }
        // 1. 실종 인물 — "{이름}({나이}) 실종"
        if (MissingPersonIdentity.isPerson(message)) {
            return MissingPersonIdentity.name(message) + "(" + MissingPersonIdentity.age(message) + ") 실종";
        }
        // 2. 동물
        if (message.contains("멧돼지")) return "멧돼지 출몰";
        if (message.contains("들개")) return "들개 출몰";
        if (message.contains("늑대")) return "늑대 출몰";
        if (message.contains("탈출")) return "동물 탈출";
        if (message.contains("출몰")) return "동물 출몰";
        // 3. 안전안내
        if (message.contains("물놀이") || message.contains("계곡") || message.contains("해수욕")) return "물놀이 안전안내";
        if (message.contains("방류") || message.contains("수문")) return "댐 방류 안내";
        if (message.contains("단수") || message.contains("급수")) return "단수 안내";
        if (message.contains("풍선")) return "대남 풍선";   // 북한 오물/쓰레기 풍선
        // 4. 그 외 자유서술 — 본문 앞부분
        String trimmed = message.strip();
        return trimmed.length() > MESSAGE_PREVIEW_LEN
                ? trimmed.substring(0, MESSAGE_PREVIEW_LEN) + "..."
                : trimmed;
    }

    /**
     * 기존 이벤트에 새 알림이 머지될 때 호출.
     * lastAlertAt 은 단조 증가 가정이지만, 백필에서 순서가 어긋날 수 있으므로 max() 로 보호.
     */
    public void recordMergedAlert(LocalDateTime alertAt) {
        if (alertAt != null && alertAt.isAfter(this.lastAlertAt)) {
            this.lastAlertAt = alertAt;
        }
        this.alertCount++;
    }

    /**
     * 진행 중 여부 파생 계산 — 마지막 알림 후 cooldown 시간 이내면 true.
     *
     * @param now 기준 시각 (KST 주입). 보통 {@code LocalDateTime.now(ZoneId.of("Asia/Seoul"))}.
     */
    public boolean isActive(LocalDateTime now) {
        return lastAlertAt.isAfter(now.minusHours(cooldownHours));
    }
}
