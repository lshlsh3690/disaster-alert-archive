package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.dto.LatestAlertResponse;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface DisasterAlertRepository extends JpaRepository<DisasterAlert, Long>, DisasterAlertRepositoryCustom {
    boolean existsBySn(Long sn);

    @Query("SELECT d.sn FROM DisasterAlert d WHERE d.sn IN :snList")
    List<Long> findExistingSn(@Param("snList") List<Long> snList);

    @Query("SELECT d.legalDistrict.name FROM DisasterAlertRegion d WHERE d.disasterAlert.id = :id")
    List<String> legalDistrictNamesByAlertId(Long id);

    /**
     * 한 재난문자에 연결된 모든 법정동 코드를 순서대로 조회.
     * 다국어 응답에서 {@code legal_district_translation} 조회용 키로 사용.
     */
    @Query("SELECT d.legalDistrict.code FROM DisasterAlertRegion d WHERE d.disasterAlert.id = :id")
    List<String> legalDistrictCodesByAlertId(Long id);

    /**
     * 여러 재난문자의 (alertId, legalDistrictCode) 쌍을 한 번에 조회.
     * 목록 응답에서 지역명 번역을 일괄 적용하기 위한 N+1 방지용 쿼리.
     *
     * <p>반환: Object[] {alertId, code} — 호출 측에서 Map 으로 변환해 사용.
     */
    @Query("""
              SELECT d.disasterAlert.id, d.legalDistrict.code
              FROM DisasterAlertRegion d
              WHERE d.disasterAlert.id IN :ids
            """)
    List<Object[]> findAlertIdAndCodePairs(@Param("ids") List<Long> ids);

    /**
     * 여러 재난문자의 (alertId, 법정동 이름) 쌍을 한 번에 조회.
     * 이벤트 타임라인에서 알림별 지역명을 N+1 없이 채우기 위한 batch 쿼리.
     */
    @Query("""
              SELECT d.disasterAlert.id, d.legalDistrict.name
              FROM DisasterAlertRegion d
              WHERE d.disasterAlert.id IN :ids
            """)
    List<Object[]> findAlertIdAndRegionNamePairs(@Param("ids") List<Long> ids);


    @Query("""
              select new com.disaster.alert.alertapi.domain.disasteralert.dto.LatestAlertResponse(
                d.id,
                d.message,
                d.disasterType,
                d.createdAt,
                coalesce(d.originalRegion, min(ld.name))
              )
              from DisasterAlert d
              left join d.disasterAlertRegions r
              left join r.legalDistrict ld
              group by d.id, d.message, d.createdAt, d.disasterType, d.originalRegion
              order by d.createdAt desc
            """)
    List<LatestAlertResponse> latestAlerts(Pageable pageable);

    /**
     * {@code createdAt}은 타임존 정보 없는 TIMESTAMP에 KST 벽시계 값으로 저장되므로,
     * DB 세션 타임존(기본 UTC)에 좌우되는 {@code CURRENT_DATE} 대신 호출 측에서
     * KST 기준 자정 시각을 명시적으로 계산해 넘긴다 (자정~오전 9시 사이에 전날 데이터까지
     * "오늘"로 잘못 카운트되던 버그 수정).
     */
    @Query("select count(d) from DisasterAlert d where d.createdAt >= :startOfDay")
    long countToday(@Param("startOfDay") LocalDateTime startOfDay);

    /**
     * 한 이벤트에 속한 모든 재난문자 + 영향 법정동을 fetch join 으로 조회 (위험도 계산용, N+1 방지).
     * 알림→이벤트는 event_alert_mapping 경유 (entity join).
     */
    @Query("""
            SELECT DISTINCT da FROM DisasterAlert da
            LEFT JOIN FETCH da.disasterAlertRegions dar
            LEFT JOIN FETCH dar.legalDistrict
            JOIN EventAlertMapping m ON m.id.alertId = da.id
            WHERE m.id.eventId = :eventId
            """)
    List<DisasterAlert> findByEventId(@org.springframework.data.repository.query.Param("eventId") Long eventId);
}
