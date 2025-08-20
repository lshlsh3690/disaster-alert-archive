package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSearchRequest;
import com.disaster.alert.alertapi.domain.disasteralert.dto.DisasterAlertStatResponse;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.model.QDisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.QDisasterAlertRegion;
import com.disaster.alert.alertapi.domain.legaldistrict.model.QLegalDistrict;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.disaster.alert.alertapi.domain.disasteralert.model.QDisasterAlert.disasterAlert;
import static com.disaster.alert.alertapi.domain.disasteralert.model.QDisasterAlertRegion.disasterAlertRegion;
import static com.disaster.alert.alertapi.domain.legaldistrict.model.QLegalDistrict.legalDistrict;

@RequiredArgsConstructor
@Slf4j
public class DisasterAlertRepositoryImpl implements DisasterAlertRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    /**
     * 재난문자 검색
     *
     * @param cond     검색 조건
     * @param pageable 페이지 정보
     * @return 검색 결과 페이지
     */
    @Override
    public Page<DisasterAlert> searchAlerts(AlertSearchRequest cond, Pageable pageable) {
        // 1) 페이지 ID만 먼저 가져오기
        List<Long> ids = queryFactory
                .select(disasterAlert.id)
                .from(disasterAlert)
                .where(byAlertCondition(cond))
                .orderBy(disasterAlert.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        log.info(ids.size() + " alerts found for condition: " + cond);

        // 2) ID IN으로 필요한 행만 연관까지 한 번에 로드 (fetch join + distinct)
        List<DisasterAlert> contents = queryFactory
                .selectFrom(disasterAlert)
                .leftJoin(disasterAlert.disasterAlertRegions, disasterAlertRegion).fetchJoin()
                .leftJoin(disasterAlertRegion.legalDistrict, legalDistrict).fetchJoin()
                .where(disasterAlert.id.in(ids))
                .distinct()
                .fetch();

        // 3) 원래 페이지 순서 유지 (ids 순서대로 재정렬)
        Map<Long, DisasterAlert> byId = contents.stream()
                .collect(Collectors.toMap(DisasterAlert::getId, it -> it));
        List<DisasterAlert> ordered = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 4) total
        long total = this.countAlertsDistinct(cond);

        return new PageImpl<>(ordered, pageable, total);
    }

    @Override
    public long countAlerts(AlertSearchRequest request) {
        return queryFactory
                .select(disasterAlert.id.countDistinct())
                .from(disasterAlert)
                .where(byAlertCondition(request))
                .fetchOne();
    }

    @Transactional(readOnly = true)
    public long countAlertsDistinct(AlertSearchRequest c) {
        return queryFactory
                .select(disasterAlert.id.countDistinct())
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .where(byAlertCondition(c))
                .fetchOne();
    }

    @Override
    public List<DisasterAlertStatResponse.RegionStat> countByRegion(AlertSearchRequest request) {
        return queryFactory
                .select(Projections.constructor(DisasterAlertStatResponse.RegionStat.class,
                        disasterAlertRegion.legalDistrict.name,
                        disasterAlert.id.countDistinct()
                ))
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .where(
                        byAlertCondition(request),
                        regionFilterOnJoin(request)
                )
                .groupBy(disasterAlertRegion.legalDistrict.name)
                .orderBy(disasterAlert.id.countDistinct().desc(), disasterAlertRegion.legalDistrict.name.asc())
                .fetch();
    }

    @Override
    public List<DisasterAlertStatResponse.TypeStat> countByType(AlertSearchRequest request) {
        BooleanBuilder where = byAlertCondition(request);

        return queryFactory
                .select(Projections.constructor(DisasterAlertStatResponse.TypeStat.class,
                        disasterAlert.disasterType,
                        disasterAlert.id.countDistinct()))
                .from(disasterAlert)
                .where(where, disasterAlert.disasterType.isNotNull())
                .groupBy(disasterAlert.disasterType)
                .orderBy(disasterAlert.id.countDistinct().desc(), disasterAlert.disasterType.asc())
                .fetch();
    }

    @Override
    public List<DisasterAlertStatResponse.LevelStat> countByLevel(AlertSearchRequest request) {
        BooleanBuilder where = byAlertCondition(request);

        return queryFactory
                .select(Projections.constructor(DisasterAlertStatResponse.LevelStat.class,
                        disasterAlert.emergencyLevel,
                        disasterAlert.id.countDistinct()))
                .from(disasterAlert)
                .where(where, disasterAlert.emergencyLevel.isNotNull())
                .groupBy(disasterAlert.emergencyLevel)
                .orderBy(disasterAlert.id.countDistinct().desc(), disasterAlert.emergencyLevel.asc())
                .fetch();
    }

    private BooleanBuilder byAlertCondition(AlertSearchRequest condition) {
        BooleanBuilder b = new BooleanBuilder();
        //시간
        if (condition.getStartDate() != null) b.and(dateGoe(condition.getStartDate()));
        if (condition.getEndDate() != null) b.and(dateLoe(condition.getEndDate()));
        if (condition.getType() != null) b.and(typeEq(condition.getType()));
        if (condition.getLevel() != null) b.and(levelEq(condition.getLevel()));
        if (condition.getKeyword() != null) b.and(keywordContains(condition.getKeyword()));

        //지역
        if (StringUtils.hasText(condition.getRegion())) b.and(regionExists(condition.getRegion()));
        if (StringUtils.hasText(condition.getDistrictCode())) b.and(districtCodeExists(condition.getDistrictCode()));
        return b;
    }

    private BooleanExpression regionFilterOnJoin(AlertSearchRequest c) {
        if (StringUtils.hasText(c.getDistrictCode())) {
            return legalDistrict.code.eq(c.getDistrictCode());
        }
        if (StringUtils.hasText(c.getRegion())) {
            return legalDistrict.name.startsWith(c.getRegion());
        }
        return null;
    }

    private BooleanExpression regionExists(String region) {
        QDisasterAlertRegion dar = QDisasterAlertRegion.disasterAlertRegion;
        QLegalDistrict ld = QLegalDistrict.legalDistrict;
        return JPAExpressions
                .selectOne()
                .from(dar)
                .join(dar.legalDistrict, ld)
                .where(dar.disasterAlert.eq(disasterAlert)
                        .and(ld.name.contains(region)))
                .exists();
    }

    private BooleanExpression districtCodeExists(String code) {
        QDisasterAlertRegion dar = QDisasterAlertRegion.disasterAlertRegion;
        QLegalDistrict ld = QLegalDistrict.legalDistrict;
        return JPAExpressions
                .selectOne()
                .from(dar)
                .join(dar.legalDistrict, ld)
                .where(dar.disasterAlert.eq(disasterAlert)
                        .and(ld.code.eq(code)))
                .exists();
    }

    private BooleanExpression dateGoe(LocalDate startDate) {
        return startDate != null ? disasterAlert.createdAt.goe(startDate.atStartOfDay()) : null;
    }

    private BooleanExpression dateLoe(LocalDate endDate) {
        return endDate != null ? disasterAlert.createdAt.loe(endDate.atTime(23, 59, 59, 999)) : null;
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
