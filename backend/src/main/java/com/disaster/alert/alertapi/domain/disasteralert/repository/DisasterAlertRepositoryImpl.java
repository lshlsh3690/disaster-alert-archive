package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSearchCondition;
import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertRegionStatDto;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.disaster.alert.alertapi.domain.disasteralert.model.QDisasterAlert.disasterAlert;
import static com.disaster.alert.alertapi.domain.disasteralert.model.QDisasterAlertRegion.disasterAlertRegion;
import static com.disaster.alert.alertapi.domain.legaldistrict.model.QLegalDistrict.legalDistrict;

@RequiredArgsConstructor
public class DisasterAlertRepositoryImpl implements DisasterAlertRepositoryCustom{
    private final JPAQueryFactory queryFactory;

    /**
     * 재난문자 검색
     *
     * @param condition 검색 조건
     * @param pageable  페이지 정보
     * @return 검색 결과 페이지
     */
    @Override
    public Page<DisasterAlert> searchAlerts(AlertSearchCondition condition, Pageable pageable) {
        List<DisasterAlert> contents = queryFactory
                .selectFrom(disasterAlert)
                .where(byAlertCondition(condition))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(disasterAlert.createdAt.desc())
                .fetch();


        long total = queryFactory
                .select(disasterAlert.count())
                .from(disasterAlert)
                .where(byAlertCondition(condition))
                .fetchOne();

        return new PageImpl<>(contents, pageable, total);
    }

    @Override
    public List<DisasterAlert> getRegionStats(AlertSearchCondition condition) {
        return queryFactory
                .selectFrom(disasterAlert)
                .distinct()
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion).fetchJoin()
                .join(disasterAlertRegion.legalDistrict, legalDistrict).fetchJoin()
                .where(byAlertCondition(condition))
                .fetch();
    }


    private BooleanBuilder byAlertCondition(AlertSearchCondition condition) {
        BooleanBuilder builder = new BooleanBuilder();

        if (condition.getRegion() != null) builder.and(regionEq(condition.getRegion()));
        if (condition.getDistrictCode() != null) builder.and(districtCodeEq(condition.getDistrictCode()));
        if (condition.getStartDate() != null) builder.and(dateGoe(condition.getStartDate()));
        if (condition.getEndDate() != null) builder.and(dateLoe(condition.getEndDate()));
        if (condition.getType() != null) builder.and(typeEq(condition.getType()));
        if (condition.getLevel() != null) builder.and(levelEq(condition.getLevel()));
        if (condition.getKeyword() != null) builder.and(keywordContains(condition.getKeyword()));

        return builder;
    }

    private BooleanExpression regionEq(String region) {
        return StringUtils.hasText(region) ? disasterAlertRegion.legalDistrict.name.contains(region) : null;
    }

    private BooleanExpression districtCodeEq(String code) {
        return StringUtils.hasText(code) ? disasterAlertRegion.legalDistrict.code.eq(code) : null;
    }

    private BooleanExpression dateGoe(LocalDate startDate) {
        return startDate != null ? disasterAlert.createdAt.goe(startDate.atStartOfDay()) : null;
    }

    private BooleanExpression dateLoe(LocalDate endDate) {
        return endDate != null ? disasterAlert.createdAt.loe(endDate.atTime(23, 59, 59,999)) : null;
    }

    private BooleanExpression typeEq(String type) {
        return StringUtils.hasText(type) ? disasterAlert.disasterType.eq(type) : null;
    }

    private BooleanExpression levelEq(DisasterLevel level) {
        return level != null ? disasterAlert.emergencyLevel.eq(level) : null;
    }

    private BooleanExpression keywordContains(String keyword) {
        return StringUtils.hasText(keyword) ? disasterAlert.message.contains(keyword) : null;
    }
}
