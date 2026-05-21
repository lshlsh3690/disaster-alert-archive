package com.disaster.alert.alertapi.domain.weather.repository;

import com.disaster.alert.alertapi.domain.weather.model.WeatherStationMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 시군구 ↔ 기상 관측 소스 매핑 레포지토리.
 *
 * <p><b>주요 조회 경로</b>
 * <ul>
 *   <li>{@code findByAsosStationId} — 백필 시 "이 ASOS 지점에 매핑된 시군구가 어느 것들인가" 역방향.
 *       한 ASOS API 호출 결과를 N 시군구로 복제 INSERT 할 때 사용.</li>
 *   <li>{@code findByKmaNxAndKmaNy} — 실시간 예보 시 동일 격자를 공유하는 시군구 묶음 조회.</li>
 * </ul>
 *
 * <p>PK ({@code legalDistrictCode}) 기반 단건 조회는 {@code JpaRepository.findById()} 그대로 사용.
 */
public interface WeatherStationMappingRepository
        extends JpaRepository<WeatherStationMapping, String> {

    /**
     * 특정 ASOS 지점에 매핑된 시군구 목록.
     * 예: asosStationId="108" → 서울 25개 자치구 모두 반환.
     */
    List<WeatherStationMapping> findByAsosStationId(String asosStationId);

    /**
     * 동일 격자 좌표를 공유하는 시군구 목록.
     * 기상청 단기예보 API 1회 호출 결과를 여러 시군구로 분배할 때 사용.
     */
    List<WeatherStationMapping> findByKmaNxAndKmaNy(Integer kmaNx, Integer kmaNy);

    /**
     * 매핑에 사용 중인 ASOS 지점 번호 전체 (중복 제거, 정렬).
     * 백필러 시작 시 "어떤 지점들을 호출해야 하는가" 결정.
     */
    @Query("SELECT DISTINCT m.asosStationId FROM WeatherStationMapping m " +
           "WHERE m.asosStationId IS NOT NULL ORDER BY m.asosStationId")
    List<String> findDistinctAsosStationIds();
}
