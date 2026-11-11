package com.disaster.alert.alertapi.domain.event.repository;

import com.disaster.alert.alertapi.domain.event.dto.EventTimelineRow;
import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DisasterEventRepository extends JpaRepository<DisasterEvent, Long> {

    // ── 조회 API ─────────────────────────────────────────────
    // active 는 (now - last_alert_at < cooldown_hours) 파생값.
    // DB now() 는 운영 JVM/DB 가 UTC 면 KST 값과 9시간 어긋나므로 쓰지 않고,
    // 호출 측이 KST now 를 :now 로 주입한다.

    /**
     * 조회 API 필터 검색 — active + 유형/지역/기간/키워드를 nullable 가드로 결합한 단일 쿼리.
     *
     * <p>각 파라미터는 null 이면 해당 조건 스킵({@code CAST(:x AS …) IS NULL OR …}). native 쿼리에서
     * null 파라미터의 타입 추론 실패({@code could not determine data type})를 막기 위해 모두 명시 CAST.
     *
     * <ul>
     *   <li>{@code type}   — primary_disaster_type exact</li>
     *   <li>{@code region} — primary_region_name prefix(LIKE 'region%'). 시도/"시도 시군구" 둘 다. <b>근사치</b>
     *                        (전국 broadcast 이벤트는 region_code=null·name 도 시도 아님 → 빠질 수 있음)</li>
     *   <li>{@code regionCode} — primary_region_code exact (선택)</li>
     *   <li>기간 — <b>겹침(overlap)</b>: first_alert_at ≤ end AND last_alert_at ≥ start.
     *             startDate 가드는 last_alert_at≥start, endDate 가드는 first_alert_at≤end 로 분리해 각각 nullable.</li>
     *   <li>{@code keyword} — event_title contains</li>
     *   <li>{@code active} — null=전체 / true=cooldown 이내(진행 중) / false=경과(지난 사건). per-row cooldown_hours interval 연산.</li>
     * </ul>
     *
     * <p>정렬은 목록 전체와 동일하게 last_alert_at DESC 고정. countQuery 는 동일 WHERE 를 복제(누락 시 오집계).
     */
    @Query(value = """
            SELECT * FROM disaster_events e
            WHERE e.alert_count >= 2
              AND (CAST(:type AS varchar) IS NULL OR e.primary_disaster_type = :type)
              AND (CAST(:region AS varchar) IS NULL OR e.primary_region_name LIKE :region || '%')
              AND (CAST(:regionCode AS varchar) IS NULL OR e.primary_region_code = :regionCode)
              AND (CAST(:startDate AS timestamp) IS NULL OR e.last_alert_at  >= CAST(:startDate AS timestamp))
              AND (CAST(:endDate   AS timestamp) IS NULL OR e.first_alert_at <= CAST(:endDate   AS timestamp))
              AND (CAST(:keyword AS varchar) IS NULL OR e.event_title LIKE '%' || :keyword || '%')
              AND (CAST(:active AS boolean) IS NULL
                   OR (CAST(:active AS boolean) = TRUE  AND e.last_alert_at >  CAST(:now AS timestamp) - (cooldown_hours * interval '1 hour'))
                   OR (CAST(:active AS boolean) = FALSE AND e.last_alert_at <= CAST(:now AS timestamp) - (cooldown_hours * interval '1 hour')))
            ORDER BY e.last_alert_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM disaster_events e
            WHERE e.alert_count >= 2
              AND (CAST(:type AS varchar) IS NULL OR e.primary_disaster_type = :type)
              AND (CAST(:region AS varchar) IS NULL OR e.primary_region_name LIKE :region || '%')
              AND (CAST(:regionCode AS varchar) IS NULL OR e.primary_region_code = :regionCode)
              AND (CAST(:startDate AS timestamp) IS NULL OR e.last_alert_at  >= CAST(:startDate AS timestamp))
              AND (CAST(:endDate   AS timestamp) IS NULL OR e.first_alert_at <= CAST(:endDate   AS timestamp))
              AND (CAST(:keyword AS varchar) IS NULL OR e.event_title LIKE '%' || :keyword || '%')
              AND (CAST(:active AS boolean) IS NULL
                   OR (CAST(:active AS boolean) = TRUE  AND e.last_alert_at >  CAST(:now AS timestamp) - (cooldown_hours * interval '1 hour'))
                   OR (CAST(:active AS boolean) = FALSE AND e.last_alert_at <= CAST(:now AS timestamp) - (cooldown_hours * interval '1 hour')))
            """,
            nativeQuery = true)
    Page<DisasterEvent> search(
            @Param("active") Boolean active,
            @Param("now") LocalDateTime now,
            @Param("type") String type,
            @Param("region") String region,
            @Param("regionCode") String regionCode,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("keyword") String keyword,
            Pageable pageable
    );

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
     * <p>두 쿼리로 나눈다: 기간 안의 비광역 이벤트를 받고({@link #findRecentEventIds}), 그중 새 알림과 같은
     * 시군구 알림만으로 거리를 재 가까운 3개를 고른다({@link #findClosestEvents}). 거리는 이벤트의 모든 알림이
     * 아니라 <b>같은 시군구 알림</b>만으로 잰다(다른 시군구 알림과의 거리는 섞지 않는다).
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
    default List<Object[]> findTopCandidates(String newEmbedding, String[] sigunguCodes, LocalDateTime sinceTime) {
        List<Long> candidates = findRecentEventIds(sinceTime);
        if (candidates.isEmpty()) {
            return List.of();
        }
        return findClosestEvents(newEmbedding, candidates, sigunguCodes);
    }

    /** 마지막 알림이 기간 안인 비광역 이벤트(유형 무관). */
    @Query("""
            SELECT e.id
            FROM DisasterEvent e
            WHERE e.lastAlertAt > :since
              AND e.broadcast = false
            """)
    List<Long> findRecentEventIds(@Param("since") LocalDateTime since);

    /** 후보 이벤트별로, 주어진 시군구에 속한 알림과 새 알림의 최소 거리. 가까운 순 3개, {eventId, minDistance}. */
    @Query(value = """
            SELECT a.event_id,
                   MIN(a.embedding <=> CAST(:newEmbedding AS vector)) AS min_distance
            FROM disaster_alert a
            JOIN disaster_alert_region r ON r.disaster_alert_id = a.disaster_alert_id
            WHERE a.event_id IN (:eventIds)
              AND a.embedding IS NOT NULL
              AND LEFT(r.legal_district_code, 5) = ANY(CAST(:sigunguCodes AS varchar[]))
            GROUP BY a.event_id
            ORDER BY min_distance ASC
            LIMIT 3
            """, nativeQuery = true)
    List<Object[]> findClosestEvents(@Param("newEmbedding") String newEmbedding,
                                     @Param("eventIds") List<Long> eventIds,
                                     @Param("sigunguCodes") String[] sigunguCodes);

    /**
     * 사고성 유형의 인접 시군구 후보 검색 — {@link #findTopCandidates} 와 같은 반환 형태({eventId, minDistance}).
     *
     * <p>지역 조건만 다르다 — 이벤트 알림의 시군구가 새 알림 시군구의 {@code region_adjacency} 이웃이어야 한다
     * (기존 cross-region 시군구 쿼리와 같은 방향: region_code=새 알림, neighbor_code=이벤트).
     * 대상은 "이벤트 알림 중 하나라도 이웃 시군구에 속하는 이벤트"라서, 같은 시군구 알림이 섞인 이벤트나
     * 다지역 알림이 섞인 이벤트도 포함될 수 있다(이웃만 대상인 것은 아니다).
     * 인접 병합된 이벤트의 primary_region_code 는 첫 알림 지역으로 고정된다.
     *
     * <p>{@link #findTopCandidates} 와 같은 후보 목록을 쓰고, 거리는 이웃 시군구 알림만으로 잰다.
     */
    default List<Object[]> findTopAdjacentCandidates(String newEmbedding, String[] sigunguCodes, LocalDateTime sinceTime) {
        List<Long> candidates = findRecentEventIds(sinceTime);
        if (candidates.isEmpty()) {
            return List.of();
        }
        return findClosestAdjacentEvents(newEmbedding, candidates, sigunguCodes);
    }

    /** 후보 이벤트별로, 주어진 시군구의 이웃(region_adjacency)에 속한 알림과 새 알림의 최소 거리. 가까운 순 3개. */
    @Query(value = """
            SELECT a.event_id,
                   MIN(a.embedding <=> CAST(:newEmbedding AS vector)) AS min_distance
            FROM disaster_alert a
            JOIN disaster_alert_region r ON r.disaster_alert_id = a.disaster_alert_id
            JOIN region_adjacency ra     ON ra.neighbor_code = LEFT(r.legal_district_code, 5)
            WHERE a.event_id IN (:eventIds)
              AND a.embedding IS NOT NULL
              AND ra.region_code = ANY(CAST(:sigunguCodes AS varchar[]))
            GROUP BY a.event_id
            ORDER BY min_distance ASC
            LIMIT 3
            """, nativeQuery = true)
    List<Object[]> findClosestAdjacentEvents(@Param("newEmbedding") String newEmbedding,
                                             @Param("eventIds") List<Long> eventIds,
                                             @Param("sigunguCodes") String[] sigunguCodes);

    /**
     * 지역 한정 유형 머지 대상 검색 — 같은 유형 + 같은 시군구(footprint 교집합) + 윈도우 안의
     * non-broadcast 이벤트 중 가장 최근 1개. 산불·산사태·홍수처럼 "유형+시군구가 곧 사건"인 이산
     * 재난용.
     *
     * <p>{@link #findTopCandidates}와 달리 <b>임베딩(본문)을 보지 않는다</b> — 한 산불/호우 episode 가
     * 같은 시군구에서 며칠 burst 로 쏟아질 때 본문 텍스트가 갈려(코사인&lt;0.85) 임베딩으로는 한 사건이
     * 수십 개로 파편화되기 때문. 유형 자체를 키로 삼되, 태풍({@link #findFirstByBroadcastTrue...})처럼
     * 전국으로 풀지 않고 <b>시군구 교집합</b>으로 묶어 전국 blob 을 차단한다.
     *
     * <p>두 쿼리로 나눈다: 후보 이벤트(유형·기간, {@link #findRegionalTypeCandidates})를 최근순으로 받고,
     * 그중 이벤트 footprint 전체(걸친 모든 알림 지역)가 새 알림 시군구와 겹치는 첫 이벤트를 고른다
     * ({@link #findEventsWithSigungu}). {@code is_broadcast=false}만 — 광역 산불은 호출 측이 span 게이트로
     * 걸러 broadcast 경로로 보낸다. 마지막 알림 시각이 같은 후보는 id 가 큰(나중에 생긴) 쪽을 고른다.
     *
     * @param type         새 알림 유형 (예: "산불", "폭염")
     * @param sigunguCodes 새 알림 시군구 코드(앞 5자) 배열 — footprint 교집합 키
     * @param sinceTime    윈도우 하한 (유형별: 현재 산불·산사태·홍수 모두 7일 전)
     * @return 머지할 이벤트 id, 없으면 empty(신규 이벤트)
     */
    default Optional<Long> findRegionalTypeMergeTarget(String type, String[] sigunguCodes, LocalDateTime sinceTime) {
        // 1) 같은 유형·기간 안의 후보 이벤트를 최근순으로 받고, 2) 그중 새 알림과 같은 시군구 알림을 가진 이벤트를 고른다
        List<Long> candidates = findRegionalTypeCandidates(type, sinceTime);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Set<Long> matched = new HashSet<>(findEventsWithSigungu(candidates, sigunguCodes));
        return candidates.stream().filter(matched::contains).findFirst();
    }

    /** 같은 유형이고 마지막 알림이 기간 안인 이벤트(광역 제외), 최근순(동률은 id 큰 순). */
    @Query("""
            SELECT e.id
            FROM DisasterEvent e
            WHERE e.primaryDisasterType = :type
              AND e.lastAlertAt > :since
              AND e.broadcast = false
            ORDER BY e.lastAlertAt DESC, e.id DESC
            """)
    List<Long> findRegionalTypeCandidates(@Param("type") String type, @Param("since") LocalDateTime since);

    /** 후보 이벤트 중 그 안의 알림이 주어진 시군구에 걸친 이벤트. */
    @Query(value = """
            SELECT DISTINCT a.event_id
            FROM disaster_alert a
            JOIN disaster_alert_region r ON r.disaster_alert_id = a.disaster_alert_id
            WHERE a.event_id IN (:eventIds)
              AND LEFT(r.legal_district_code, 5) = ANY(CAST(:sigunguCodes AS varchar[]))
            """, nativeQuery = true)
    List<Long> findEventsWithSigungu(@Param("eventIds") List<Long> eventIds,
                                     @Param("sigunguCodes") String[] sigunguCodes);

    /**
     * 같은 시군구에 대상이 없을 때, 인접 시군구의 진행 중인 사건(비광역) 이벤트 중 last_alert_at 이
     * 가장 최근인 1개를 찾는다.
     *
     * <p>2025-03 경북 대형 산불(의성→안동→청송→영양→영덕)이 시군구별 5개 이벤트로 쪼개진 것을 잇기 위한
     * 쿼리다. 같은 시군구 이벤트({@link #findRegionalTypeMergeTarget})가 없을 때만 호출 측이 쓴다.
     *
     * <p>시군구 개수 상한은 두지 않고 {@code last_alert_at > since}(호출 측이 24h) 조건만 둔다 — 실제 확산은
     * 며칠 사이 여러 시군구를 건너므로 개수로 자르면 연쇄가 끊기고, 별개의 산불은 마지막 알림이 24h 안에
     * 겹치지 않아 시간 조건만으로 걸러진다. {@code region_adjacency} 는 양방향(대칭) 저장이라
     * {@code region_code = 새 알림 시군구} 로 보면 그 이웃({@code neighbor_code})의 이벤트가 잡힌다.
     * 광역 이벤트는 {@link #findRegionalTypeMergeTarget} 와 같은 이유로 제외한다.
     *
     * @param type         새 알림 유형 (예: "산불")
     * @param sigunguCodes 새 알림 시군구 코드(앞 5자) 배열
     * @param since        이벤트 마지막 알림 시각 하한
     * @return 머지할 이벤트 id, 없으면 empty
     */
    default Optional<Long> findRegionalTypeAdjacentMergeTarget(String type, String[] sigunguCodes, LocalDateTime since) {
        // 같은 시군구 버전과 같은 후보 목록에서, 새 알림 시군구의 이웃 시군구 알림을 가진 이벤트를 고른다
        List<Long> candidates = findRegionalTypeCandidates(type, since);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Set<Long> matched = new HashSet<>(findEventsWithAdjacentSigungu(candidates, sigunguCodes));
        return candidates.stream().filter(matched::contains).findFirst();
    }

    /** 후보 이벤트 중 그 안의 알림이 주어진 시군구의 이웃(region_adjacency)에 걸친 이벤트. */
    @Query(value = """
            SELECT DISTINCT a.event_id
            FROM disaster_alert a
            JOIN disaster_alert_region r ON r.disaster_alert_id = a.disaster_alert_id
            JOIN region_adjacency ra     ON ra.neighbor_code = LEFT(r.legal_district_code, 5)
            WHERE a.event_id IN (:eventIds)
              AND ra.region_code = ANY(CAST(:sigunguCodes AS varchar[]))
            """, nativeQuery = true)
    List<Long> findEventsWithAdjacentSigungu(@Param("eventIds") List<Long> eventIds,
                                             @Param("sigunguCodes") String[] sigunguCodes);

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
    @Query(value = """
            SELECT * FROM disaster_events
            WHERE is_broadcast = true
              AND primary_disaster_type = :primaryDisasterType
              AND primary_region_code LIKE CONCAT(:sidoPrefix, '%')
              AND last_alert_at > :lastAlertAt
            ORDER BY last_alert_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<DisasterEvent> findLatestBroadcastBySido(
            @Param("primaryDisasterType") String primaryDisasterType,
            @Param("sidoPrefix") String sidoPrefix,
            @Param("lastAlertAt") LocalDateTime lastAlertAt);

    /**
     * 전국 broadcast 이벤트 머지 대상 검색 — 같은 유형 + 윈도우 안의 전국 이벤트 1개.
     *
     * <p>전국 이벤트는 특정 시도에 귀속되지 않으므로 {@code primary_region_code = null} 로 저장하고,
     * 시도 키 없이 유형만으로 묶는다(라벨 "전국 {유형}"). 행안부 전국 호우/대설 안내처럼 17개 시도
     * 전체에 발송되는 알림이 시도별로 흩어지지 않게 하나로 통합.
     */
    @Query(value = """
            SELECT * FROM disaster_events
            WHERE is_broadcast = true
              AND primary_disaster_type = :primaryDisasterType
              AND primary_region_code IS NULL
              AND last_alert_at > :lastAlertAt
            ORDER BY last_alert_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<DisasterEvent> findLatestBroadcastNationwide(
            @Param("primaryDisasterType") String primaryDisasterType,
            @Param("lastAlertAt") LocalDateTime lastAlertAt);

    /**
     * 제목 승급 — 현재 제목이 {@code ifTitle}(기본 제목)일 때만 {@code title}로 교체.
     *
     * <p>전국 통합 태풍 이벤트의 첫 알림에 태풍명이 없어 "전국 태풍"으로 생성된 뒤, 이름을 가진
     * 후속 알림이 머지될 때 "태풍 종다리"로 올린다. 이미 이름이 붙었으면(기본 제목 아님) no-op이라
     * 먼저 붙은 이름이 유지된다(한 윈도우에 두 태풍이 섞여도 덮어쓰지 않음).
     */
    @Modifying
    @Query(value = """
            UPDATE disaster_events
            SET event_title = :title
            WHERE id = :eventId AND event_title = :ifTitle
            """, nativeQuery = true)
    int updateTitleIfEquals(@Param("eventId") Long eventId,
                            @Param("title") String title,
                            @Param("ifTitle") String ifTitle);

    // ── cross-region LLM 병합 ─────────────────────────────────────

    // cross-region 후보 검색은 {@link #findTopCandidates}와 달리 지역 hard 필터(교집합)를 제거하되
    // 세 게이트로 한정한다(동물 과병합 방지):
    //   ① 종 게이트: 후보 이벤트가 대상과 같은 종(speciesRegex) 알림을 가져야 함. 동물 문자는 본문
    //      임베딩이 종과 무관하게 균일해(늑대 탈출 ≈ 멧돼지 출몰) 종을 안 가르면 다른 종끼리 묶인다.
    //   ② 인접 게이트: 후보 이벤트가 대상 지역과 같거나 인접한 지역을 포함해야 함. 동물은 물리적으로
    //      옆 지역을 거쳐 이동하므로 비인접은 다른 개체로 보고 차단. 인접 단위는 종에 따라 둘로 나뉜다
    //      (아래 Sido/Sigungu 두 메서드). 통과한 top-K 후보의 동일개체 판정은 LLM 이 한다.
    //   ③ 시간 게이트(양방향): 후보 이벤트가 처리 알림의 ±윈도우와 겹쳐야 함
    //      (last_alert_at > sinceTime AND first_alert_at <= untilTime). 상한은 포함이다 — 동물 경로는
    //      untilTime 에 새 알림 시각을 넘기는데, 같은 초에 여러 지역으로 동시 발송된 같은 사건의 앞 알림이
    //      만든 이벤트(first_alert_at 이 같은 시각)가 "<" 이면 후보에서 빠져 사건이 둘로 갈라진다. 하한만 두면 cross-region 백필이
    //      base 후 '모든 이벤트 선존재' 상태로 돌아, 처리 알림보다 몇 년 미래의 같은 종·인접 이벤트도
    //      last_alert_at > sinceTime 을 만족해 후보가 된다 → LLM 이 "같은 동네 같은 동물" 이라 묶어
    //      같은 시군구 멧돼지 출몰 수년치가 한 사건(수백일 span)으로 과병합. 상한(untilTime)으로
    //      윈도우 밖에서 시작한 이벤트를 거르면 live(미래 알림 미존재)와 동일 의미가 된다.

    /**
     * cross-region 후보 검색 — <b>시도(코드 앞 2자) 인접</b> 게이트. 이동종(늑대·사슴·곰·소)용.
     *
     * <p>탈출 동물은 멀리 이동하고 알림이 시도 레벨 코드(예 {@code 30000} 대전 전체)로 태깅되는
     * 경우가 많아 시군구 인접 그래프에 안 잡힌다 → 앞 2자(시도)로 비교. 인접 시도는
     * {@code region_adjacency}(시군구 인접쌍)를 시도로 투영해 도출. 이동 개체는 인접 시도 체인으로
     * footprint 가 이어져 한 이벤트로 유지된다.
     *
     * @param sidoCodes 대상 알림 시도 코드(앞 2자) — 인접 게이트 기준
     * @return {eventId, minDistance} 0~K 행, 거리 오름차순
     */
    @Query(value = """
            SELECT e.id AS event_id,
                   MIN(da.embedding <=> CAST(:newEmbedding AS vector)) AS min_distance
            FROM disaster_events e
            JOIN disaster_alert da ON da.event_id = e.id
            WHERE e.last_alert_at > :sinceTime
              AND e.first_alert_at <= :untilTime
              AND e.is_broadcast = false
              AND e.id <> :selfEventId
              AND da.embedding IS NOT NULL
              AND EXISTS (
                  SELECT 1 FROM disaster_alert da2
                  WHERE da2.event_id = e.id
                    AND da2.disaster_type = '기타'
                    AND da2.message ~ :speciesRegex
              )
              AND EXISTS (
                  SELECT 1 FROM disaster_alert da3
                  JOIN disaster_alert_region dar ON dar.disaster_alert_id = da3.disaster_alert_id
                  WHERE da3.event_id = e.id
                    AND (
                        LEFT(dar.legal_district_code, 2) IN (:sidoCodes)
                        OR EXISTS (
                            SELECT 1 FROM region_adjacency ra
                            WHERE LEFT(ra.region_code, 2) IN (:sidoCodes)
                              AND LEFT(ra.neighbor_code, 2) = LEFT(dar.legal_district_code, 2)
                        )
                    )
              )
            GROUP BY e.id
            HAVING MIN(da.embedding <=> CAST(:newEmbedding AS vector)) <= :maxDistance
            ORDER BY min_distance ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findCrossRegionCandidatesSido(
            @Param("newEmbedding") String newEmbedding,
            @Param("sinceTime") LocalDateTime sinceTime,
            @Param("untilTime") LocalDateTime untilTime,
            @Param("selfEventId") Long selfEventId,
            @Param("speciesRegex") String speciesRegex,
            @Param("sidoCodes") List<String> sidoCodes,
            @Param("maxDistance") double maxDistance,
            @Param("limit") int limit
    );

    /**
     * cross-region 후보 검색 — <b>시군구(코드 앞 5자) 인접</b> 게이트. 토착종(멧돼지·들개·뱀)용.
     *
     * <p>토착 야생동물은 지역에 귀속돼 국지적으로만 움직이고 알림이 구체 시군구로 정밀 태깅된다.
     * 시도 인접을 쓰면 같은 종 출몰이 인접 시도를 전이적으로 타고 전국이 한 사건으로 묶이는
     * 과병합이 난다(부산→경남→대구). 시군구 인접({@code region_adjacency} 직접 매칭)으로 좁혀
     * "옆 동네 이동"(예: 부산진구↔동래구)만 살리고 전이적 과확장을 막는다.
     *
     * @param sigunguCodes 대상 알림 시군구 코드(앞 5자) — 인접 게이트 기준
     * @return {eventId, minDistance} 0~K 행, 거리 오름차순
     */
    @Query(value = """
            SELECT e.id AS event_id,
                   MIN(da.embedding <=> CAST(:newEmbedding AS vector)) AS min_distance
            FROM disaster_events e
            JOIN disaster_alert da ON da.event_id = e.id
            WHERE e.last_alert_at > :sinceTime
              AND e.first_alert_at <= :untilTime
              AND e.is_broadcast = false
              AND e.id <> :selfEventId
              AND da.embedding IS NOT NULL
              AND EXISTS (
                  SELECT 1 FROM disaster_alert da2
                  WHERE da2.event_id = e.id
                    AND da2.disaster_type = '기타'
                    AND da2.message ~ :speciesRegex
              )
              AND EXISTS (
                  SELECT 1 FROM disaster_alert da3
                  JOIN disaster_alert_region dar ON dar.disaster_alert_id = da3.disaster_alert_id
                  WHERE da3.event_id = e.id
                    AND (
                        LEFT(dar.legal_district_code, 5) IN (:sigunguCodes)
                        OR EXISTS (
                            SELECT 1 FROM region_adjacency ra
                            WHERE ra.region_code IN (:sigunguCodes)
                              AND ra.neighbor_code = LEFT(dar.legal_district_code, 5)
                        )
                    )
              )
            GROUP BY e.id
            HAVING MIN(da.embedding <=> CAST(:newEmbedding AS vector)) <= :maxDistance
            ORDER BY min_distance ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findCrossRegionCandidatesSigungu(
            @Param("newEmbedding") String newEmbedding,
            @Param("sinceTime") LocalDateTime sinceTime,
            @Param("untilTime") LocalDateTime untilTime,
            @Param("selfEventId") Long selfEventId,
            @Param("speciesRegex") String speciesRegex,
            @Param("sigunguCodes") List<String> sigunguCodes,
            @Param("maxDistance") double maxDistance,
            @Param("limit") int limit
    );

    /**
     * cross-region 인물 후보 검색 — 임베딩이 아니라 <b>이름+나이 하드 매칭</b>.
     *
     * <p>실종 경찰문자는 "OOO씨(성별,N세) …" 로 정형화돼 있어, 같은 이름+나이면 거의 같은 사람이다.
     * 임베딩 top-K(전부 ~0.9 유사한 실종 템플릿)로는 다른 사람을 잘못 묶어(정경남↔조래삼) precision
     * 이 나빴음 → 이름+나이를 후보 게이트로 두고 LLM 은 그 안에서 인상착의 보조 확인만 한다.
     *
     * <p>나이는 정규식으로 매칭하되 앞에 숫자가 없도록 해 "170세"가 "70세"로 오매칭되는 것 방지.
     * 키({@code height})가 있으면 같은 키(cm)까지 요구 — 같은 이름+나이라도 다른 사람(동명이인) 차단.
     * 키가 없으면(추출 실패) 이름+나이만으로 매칭.
     */
    @Query(value = """
            SELECT e.id AS event_id, MAX(e.last_alert_at) AS recency
            FROM disaster_events e
            JOIN disaster_alert da ON da.event_id = e.id
            WHERE e.last_alert_at > :sinceTime
              AND e.is_broadcast = false
              AND e.id <> :selfEventId
              AND da.disaster_type = '기타'
              AND da.message LIKE ('%' || :name || '씨%')
              AND da.message ~ ('(^|[^0-9])' || :age || '세')
              AND (CAST(:height AS varchar) IS NULL OR da.message LIKE ('%' || :height || 'cm%'))
            GROUP BY e.id
            ORDER BY recency DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findCrossRegionCandidatesByPerson(
            @Param("name") String name,
            @Param("age") String age,
            @Param("height") String height,
            @Param("sinceTime") LocalDateTime sinceTime,
            @Param("selfEventId") Long selfEventId,
            @Param("limit") int limit
    );

    // ── 이벤트에 든 알림 (disaster_alert.event_id) ──────────────────

    /** 이벤트 타임라인 — 소속 재난문자를 최신순으로. 지역명은 빠져 있으며 서비스에서 batch 조회 후 합친다. */
    @Query("""
            SELECT new com.disaster.alert.alertapi.domain.event.dto.EventTimelineRow(
                a.id, a.eventSequenceNo, a.message, a.disasterType, a.emergencyLevel, a.createdAt)
            FROM DisasterAlert a
            WHERE a.event.id = :eventId
            ORDER BY a.createdAt DESC
            """)
    List<EventTimelineRow> findTimelineRows(@Param("eventId") Long eventId);

    /** 이벤트가 걸친 distinct 시도 수 (법정동 코드 앞 2자리) — span-cap 판정용. */
    @Query(value = """
            SELECT count(DISTINCT left(r.legal_district_code, 2))
            FROM disaster_alert a
            JOIN disaster_alert_region r ON r.disaster_alert_id = a.disaster_alert_id
            WHERE a.event_id = :eventId
            """, nativeQuery = true)
    int countDistinctSidoByEventId(@Param("eventId") Long eventId);

    /** 후보 이벤트들의 대표(1번, 첫) 알림 본문 — LLM 프롬프트용. 반환 {event_id, message}. */
    @Query(value = """
            SELECT a.event_id, a.message
            FROM disaster_alert a
            WHERE a.event_id IN (:eventIds) AND a.event_sequence_no = 1
            """, nativeQuery = true)
    List<Object[]> findRepresentativeMessages(@Param("eventIds") List<Long> eventIds);
}
