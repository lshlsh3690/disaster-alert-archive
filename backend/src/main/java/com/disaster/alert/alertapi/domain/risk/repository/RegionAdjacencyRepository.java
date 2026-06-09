package com.disaster.alert.alertapi.domain.risk.repository;

import com.disaster.alert.alertapi.domain.risk.model.RegionAdjacency;
import com.disaster.alert.alertapi.domain.risk.model.RegionAdjacencyId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 인접 그래프 조회. 확산 전파는 전체 그래프를 한 번에 메모리로 올려 BFS 하므로
 * {@code findAll()} 로 전체 인접쌍을 읽어 인접 리스트(Map)를 구성한다(전체 ~1.3k행, 가벼움).
 */
public interface RegionAdjacencyRepository extends JpaRepository<RegionAdjacency, RegionAdjacencyId> {
}
