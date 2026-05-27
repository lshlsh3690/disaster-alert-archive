package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DisasterEventRepository extends JpaRepository<DisasterEvent, Long> {

    /**
     * 클러스터링 후보 검색.
     *
     * <p>조건:
     * <ol>
     *   <li>이벤트 last_alert_at 이 sinceTime 이후 (7일 윈도우)</li>
     *   <li>이벤트에 속한 알림 중 하나라도 새 알림의 시군구와 겹침 (지역 교집합)</li>
     *   <li>코사인 거리 (embedding &lt;=&gt; new_emb) 최소값 기준 정렬, 상위 1개</li>
     * </ol>
     *
     * <p>반환: {eventId, minDistance} 한 행. distance 0.0=동일, 1.0=직교. 임계값 비교는 호출 측.
     *
     * <p>new_emb 는 pgvector text 표현 ("[0.1, 0.2, ...]") 으로 넘겨받아 ::vector 캐스팅.
     * 정수 배열 IN 절은 PostgreSQL ANY(:regionCodes) 로 처리.
     */
    @Query(value = """
            SELECT e.id AS event_id,
                   MIN(da.embedding <=> CAST(:newEmbedding AS vector)) AS min_distance
            FROM disaster_events e
            JOIN event_alert_mapping m ON m.event_id = e.id
            JOIN disaster_alert da ON da.disaster_alert_id = m.alert_id
            WHERE e.last_alert_at > :sinceTime
              AND da.embedding IS NOT NULL
              AND EXISTS (
                  SELECT 1 FROM disaster_alert_region dar
                  WHERE dar.disaster_alert_id = da.disaster_alert_id
                    AND dar.legal_district_code = ANY(CAST(:regionCodes AS varchar[]))
              )
            GROUP BY e.id
            ORDER BY min_distance ASC
            LIMIT 1
            """, nativeQuery = true)
    List<Object[]> findTopCandidate(
            @Param("newEmbedding") String newEmbedding,
            @Param("regionCodes") String[] regionCodes,
            @Param("sinceTime") LocalDateTime sinceTime
    );

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
