package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DisasterEventRepository extends JpaRepository<DisasterEvent, Long> {

    // ── 조회 API ─────────────────────────────────────────────
    // active 는 (now - last_alert_at < cooldown_hours) 파생값.
    // DB now() 는 운영 JVM/DB 가 UTC 면 KST 값과 9시간 어긋나므로 쓰지 않고,
    // 호출 측이 KST now 를 :now 로 주입한다.

    /** 필터 없음 — 최신순 전체. */
    Page<DisasterEvent> findAllByOrderByLastAlertAtDesc(Pageable pageable);

    /** 진행 중(active=true): 마지막 알림이 cooldown 이내. */
    @Query(value = """
            SELECT * FROM disaster_events
            WHERE last_alert_at > CAST(:now AS timestamp) - (cooldown_hours * interval '1 hour')
            ORDER BY last_alert_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM disaster_events
            WHERE last_alert_at > CAST(:now AS timestamp) - (cooldown_hours * interval '1 hour')
            """,
            nativeQuery = true)
    Page<DisasterEvent> findActive(@Param("now") LocalDateTime now, Pageable pageable);

    /** 지난 사건(active=false): 마지막 알림이 cooldown 경과. */
    @Query(value = """
            SELECT * FROM disaster_events
            WHERE last_alert_at <= CAST(:now AS timestamp) - (cooldown_hours * interval '1 hour')
            ORDER BY last_alert_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM disaster_events
            WHERE last_alert_at <= CAST(:now AS timestamp) - (cooldown_hours * interval '1 hour')
            """,
            nativeQuery = true)
    Page<DisasterEvent> findInactive(@Param("now") LocalDateTime now, Pageable pageable);

    /**
     * 클러스터링 후보 검색 — 같은 지역(시군구 교집합) 이벤트 중 top-3.
     *
     * <p>조건 (AND):
     * <ol>
     *   <li>이벤트 last_alert_at 이 sinceTime 이후 (7일 윈도우)</li>
     *   <li>이벤트의 알림 중 하나라도 새 알림과 시군구 교집합 (<b>지역 hard 필터</b>)</li>
     *   <li>코사인 거리 (embedding &lt;=&gt; new_emb) 최소값 기준 정렬, 상위 3개</li>
     * </ol>
     *
     * <p><b>지역 hard 필터인 이유</b>: 폭염·호우·물놀이 안전수칙 같은 템플릿형 메시지는 본문이
     * 거의 동일해 다른 시군구끼리도 임베딩 유사도가 매우 높다. 지역을 soft 신호로 두면(cross-region
     * 자동병합) 전북+전남+충남이 한 폭염 이벤트로 뭉치는 다지역 blob 이 생긴다(실데이터 검증).
     * 따라서 지역 교집합을 필수 조건으로 둔다. 지역이 다른 같은 사건(늑대 대전→광주류)은 드물고
     * 관리자 수동 병합으로 처리.
     *
     * <p>반환: {eventId, minDistance} 튜플 0~3 행. distance 0.0=동일, 1.0=직교.
     * top-2/3 는 디버깅·임계값 튜닝 로깅용.
     */
    @Query(value = """
            SELECT e.id AS event_id,
                   MIN(da.embedding <=> CAST(:newEmbedding AS vector)) AS min_distance
            FROM disaster_events e
            JOIN event_alert_mapping m ON m.event_id = e.id
            JOIN disaster_alert da ON da.disaster_alert_id = m.alert_id
            WHERE e.last_alert_at > :sinceTime
              AND e.is_broadcast = false
              AND da.embedding IS NOT NULL
              AND EXISTS (
                  SELECT 1 FROM disaster_alert_region dar
                  WHERE dar.disaster_alert_id = da.disaster_alert_id
                    AND dar.legal_district_code = ANY(CAST(:regionCodes AS varchar[]))
              )
            GROUP BY e.id
            ORDER BY min_distance ASC
            LIMIT 3
            """, nativeQuery = true)
    List<Object[]> findTopCandidates(
            @Param("newEmbedding") String newEmbedding,
            @Param("regionCodes") String[] regionCodes,
            @Param("sinceTime") LocalDateTime sinceTime
    );

    /**
     * 광역 broadcast 이벤트 머지 대상 검색 — 같은 시도 + 같은 유형 + 윈도우 안의 broadcast 이벤트 1개.
     *
     * <p>일반 클러스터링({@link #findTopCandidates})과 달리 <b>임베딩(본문)을 보지 않는다</b>.
     * "폭염주의보 발효" / "폭염주의보 해제" / "폭염경보 격상" 처럼 본문이 달라 코사인이 낮아도
     * 같은 "{시도} {유형}" 사건으로 묶기 위함. 대신 키를 (시도 prefix + disasterType)로 제한해
     * 시도 경계를 넘는 다지역 blob 을 원천 차단한다.
     *
     * <p>{@code is_broadcast=true} 인 이벤트끼리만 묶이고(규칙 1 유지: local 후보검색 안 탐),
     * 머지돼도 플래그는 그대로라 local 알림 후보에서 계속 제외된다(규칙 2 유지).
     *
     * @param primaryDisasterType 새 알림 유형 (예: "폭염")
     * @param sidoPrefix          시도 코드 앞 2자리 (예: "48" = 경남)
     * @param lastAlertAt         윈도우 하한 (이 시각 이후 last_alert_at 인 이벤트만)
     */
    Optional<DisasterEvent> findFirstByBroadcastTrueAndPrimaryDisasterTypeAndPrimaryRegionCodeStartingWithAndLastAlertAtAfterOrderByLastAlertAtDesc(
            String primaryDisasterType, String sidoPrefix, LocalDateTime lastAlertAt);

    /**
     * 전국 broadcast 이벤트 머지 대상 검색 — 같은 유형 + 윈도우 안의 전국 이벤트 1개.
     *
     * <p>전국 이벤트는 특정 시도에 귀속되지 않으므로 {@code primary_region_code = null} 로 저장하고,
     * 시도 키 없이 유형만으로 묶는다(라벨 "전국 {유형}"). 행안부 전국 호우/대설 안내처럼 17개 시도
     * 전체에 발송되는 알림이 시도별로 흩어지지 않게 하나로 통합.
     */
    Optional<DisasterEvent> findFirstByBroadcastTrueAndPrimaryDisasterTypeAndPrimaryRegionCodeIsNullAndLastAlertAtAfterOrderByLastAlertAtDesc(
            String primaryDisasterType, LocalDateTime lastAlertAt);

    /**
     * 새 알림이 머지된 후 이벤트의 last_alert_at, alert_count 갱신.
     *
     * <p>JPA Dirty Checking 도 가능하지만 동시성 + 명시성 위해 native UPDATE 사용.
     * lastAlertAt 은 새 값이 기존보다 클 때만 갱신 (백필 시 시간 역순 입력 방어).
     */
    @Modifying
    @Query(value = """
            UPDATE disaster_events
            SET last_alert_at = GREATEST(last_alert_at, :alertAt),
                alert_count = alert_count + 1
            WHERE id = :eventId
            """, nativeQuery = true)
    int incrementOnMerge(@Param("eventId") Long eventId, @Param("alertAt") LocalDateTime alertAt);
}
