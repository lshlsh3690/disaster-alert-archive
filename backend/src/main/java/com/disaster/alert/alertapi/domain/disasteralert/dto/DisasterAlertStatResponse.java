package com.disaster.alert.alertapi.domain.disasteralert.dto;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DisasterAlertStatResponse {
    private long totalCount;
    private List<RegionStat> regionStats;
    private List<LevelStat> levelStats;
    private List<TypeStat> typeStats;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class RegionStat {
        private String region;
        private long count;
    }

    /**
     * 시군구별 등급(LEVEL_1/2/3)별 건수를 한 번에 담은 breakdown.
     * {@link RegionStat}을 level별로 4번(전체+L1+L2+L3) 조회하던 것을 한 쿼리로 합치기 위해 도입.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class RegionLevelStat {
        private String region;
        private long total;
        private long level1Count;
        private long level2Count;
        private long level3Count;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class LevelStat {
        private DisasterLevel level;
        private long count;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class TypeStat {
        private String type;
        private long count;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class DailyStat {
        private String date;  // "YYYY-MM-DD"
        private long count;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class HourlyStat {
        private int dayOfWeek;  // 1=Sun, 2=Mon, ..., 7=Sat (MySQL DAYOFWEEK)
        private int hour;
        private long count;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class MonthlyTypeStat {
        private String month;  // "YYYY-MM"
        private String type;
        private long count;
    }
}

