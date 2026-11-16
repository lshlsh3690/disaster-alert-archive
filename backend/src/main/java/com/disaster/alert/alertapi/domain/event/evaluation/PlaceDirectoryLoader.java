package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.PlaceDirectoryBuilder.AdjacencyRow;
import com.disaster.alert.alertapi.domain.event.evaluation.PlaceDirectoryBuilder.DistrictRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 판정 사실 계산기({@link AlertPlaceFacts})가 쓰는 시군구 이름 표·이웃 표를 DB 에서 읽는다. 조회 전용.
 *
 * <p>행을 읽어 넘기기만 하고, 이름 만들기·시도 별칭·구 있는 시와 인천 옛 구의 이웃 계산은
 * {@link PlaceDirectoryBuilder} 가 한다(순수 로직이라 단위 테스트로 검증).
 */
@Component
@RequiredArgsConstructor
public class PlaceDirectoryLoader {

    // 시도·시군구·구 행은 모두 뒤 5자리가 00000 이다(읍면동·리 행은 이름 표에 필요 없어 읽지 않는다 — 약 5만 행 → 수백 행).
    private static final String DISTRICT_SQL =
            "SELECT code, name, is_active FROM legal_district WHERE code LIKE '%00000'";

    private static final String ADJACENCY_SQL =
            "SELECT region_code, neighbor_code FROM region_adjacency";

    private final JdbcTemplate jdbcTemplate;

    /** 지금 DB 의 법정동·이웃 표로 이름 표·이웃 표를 만든다. */
    public PlaceDirectory load() {
        List<DistrictRow> districts = jdbcTemplate.query(DISTRICT_SQL, (rs, i) ->
                new DistrictRow(rs.getString("code"), rs.getString("name"), rs.getBoolean("is_active")));
        List<AdjacencyRow> adjacency = jdbcTemplate.query(ADJACENCY_SQL, (rs, i) ->
                new AdjacencyRow(rs.getString("region_code"), rs.getString("neighbor_code")));
        return PlaceDirectoryBuilder.build(districts, adjacency);
    }
}
