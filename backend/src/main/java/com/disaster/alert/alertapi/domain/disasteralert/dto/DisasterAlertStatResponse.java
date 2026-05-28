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
    @AllArgsConstructor
    @ToString
    public static class RegionStat {
        private String region;
        private long count;
    }

    @Getter
    @AllArgsConstructor
    @ToString
    public static class LevelStat {
        private DisasterLevel level;
        private long count;
    }

    @Getter
    @AllArgsConstructor
    @ToString
    public static class TypeStat {
        private String type;
        private long count;
    }

    @Getter
    @AllArgsConstructor
    @ToString
    public static class DailyStat {
        private String date;  // "YYYY-MM-DD"
        private long count;
    }

    @Getter
    @AllArgsConstructor
    @ToString
    public static class HourlyStat {
        private int dayOfWeek;  // 1=Sun, 2=Mon, ..., 7=Sat (MySQL DAYOFWEEK)
        private int hour;
        private long count;
    }

    @Getter
    @AllArgsConstructor
    @ToString
    public static class MonthlyTypeStat {
        private String month;  // "YYYY-MM"
        private String type;
        private long count;
    }
}

