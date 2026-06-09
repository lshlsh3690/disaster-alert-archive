package com.disaster.alert.alertapi.domain.risk.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시군구 인접 그래프(대칭쌍 양방향 저장).
 *
 * <p>sigungu.geojson 폴리곤 경계공유로 1회 추출한 불변 참조 데이터(V37 seed).
 * 위험도 공간 확산이 이 그래프를 타고 이웃으로 전파된다.
 *
 * <p>실제 조회는 네이티브 쿼리로 인접 리스트(Map)를 한 번에 로드한다. 이 엔티티는 매핑/검증용.
 */
@Entity
@Table(name = "region_adjacency")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionAdjacency {

    @EmbeddedId
    private RegionAdjacencyId id;
}
