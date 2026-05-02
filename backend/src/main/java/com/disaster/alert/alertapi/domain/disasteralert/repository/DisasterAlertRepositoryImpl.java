package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.dto.*;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.model.QDisasterAlertRegion;
import com.disaster.alert.alertapi.domain.legaldistrict.model.QLegalDistrict;
import com.disaster.alert.alertapi.domain.useralert.model.QUserDisasterAlert;
import com.disaster.alert.alertapi.domain.useralert.model.QUserDisasterAlertRegion;
import com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlert;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringTemplate;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

    @Override
    public List<DisasterAlertStatResponse.RegionStat> getStatsSido(AlertSearchRequest request) {
        // 공백 정규화 + trim
        StringTemplate norm =
                Expressions.stringTemplate(
                        "function('btrim', function('regexp_replace', {0}, '\\\\s+', ' ', 'g'))",
                        legalDistrict.name
                );

        // 시/도 = 첫 토큰
        StringTemplate sido =
                Expressions.stringTemplate("function('split_part', {0}, ' ', 1)", norm);

        return queryFactory
                .select(Projections.constructor(DisasterAlertStatResponse.RegionStat.class,
                        sido,                            // region (시/도명)
                        disasterAlert.id.countDistinct() // 해당 시/도 내 알림 건수(중복 제거)
                ))
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .where(
                        byAlertCondition(request),     // 이미 쓰고 있는 공통 조건 (EXISTS 포함)
                        regionFilterOnJoin(request)    // 지역 필터를 조인 대상에 적용하는 보조 조건
                )
                .groupBy(sido)
                .orderBy(disasterAlert.id.countDistinct().desc(), sido.asc())
                .fetch();    }

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

    // ===== USER ALERTS =====
    private Page<com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlert> searchUserAlerts(AlertSearchRequest cond, Pageable pageable) {
        QUserDisasterAlert ua = QUserDisasterAlert.userDisasterAlert;
        QUserDisasterAlertRegion uar = QUserDisasterAlertRegion.userDisasterAlertRegion;

        List<Long> ids = queryFactory
                .select(ua.id)
                .from(ua)
                .where(byUserAlertCondition(cond))
                .orderBy(ua.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlert> contents = queryFactory
                .selectFrom(ua)
                .leftJoin(ua.regions, uar).fetchJoin()
                .leftJoin(uar.legalDistrict, legalDistrict).fetchJoin()
                .where(ua.id.in(ids))
                .distinct()
                .fetch();

        Map<Long, com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlert> byId = contents.stream()
                .collect(Collectors.toMap(com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlert::getId, it -> it));
        List<com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlert> ordered = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        long total = countUserAlertsDistinct(cond);
        return new PageImpl<>(ordered, pageable, total);
    }

    private long countUserAlertsDistinct(AlertSearchRequest c) {
        QUserDisasterAlert ua = QUserDisasterAlert.userDisasterAlert;
        QUserDisasterAlertRegion uar = QUserDisasterAlertRegion.userDisasterAlertRegion;
        return queryFactory
                .select(ua.id.countDistinct())
                .from(ua)
                .join(ua.regions, uar)
                .join(uar.legalDistrict, legalDistrict)
                .where(byUserAlertCondition(c))
                .fetchOne();
    }

    private BooleanBuilder byUserAlertCondition(AlertSearchRequest condition) {
        QUserDisasterAlert ua = QUserDisasterAlert.userDisasterAlert;
        BooleanBuilder b = new BooleanBuilder();
        // 소프트 삭제 제외
        b.and(ua.isDeleted.isFalse());
        if (condition.getStartDate() != null) b.and(ua.createdAt.goe(condition.getStartDate().atStartOfDay()));
        if (condition.getEndDate() != null) b.and(ua.createdAt.loe(condition.getEndDate().atTime(23, 59, 59, 999)));
        if (condition.getType() != null) b.and(StringUtils.hasText(condition.getType()) ? ua.disasterType.eq(condition.getType()) : null);
        if (condition.getLevel() != null) b.and(ua.disasterLevel.eq(condition.getLevel()));
        if (condition.getKeyword() != null) b.and(StringUtils.hasText(condition.getKeyword()) ? ua.message.contains(condition.getKeyword()) : null);

        if (StringUtils.hasText(condition.getRegion())) b.and(userRegionExists(condition.getRegion()));
        if (StringUtils.hasText(condition.getDistrictCode())) b.and(userDistrictCodeExists(condition.getDistrictCode()));

        return b;
    }

    private BooleanExpression userRegionExists(String region) {
        QUserDisasterAlert ua = QUserDisasterAlert.userDisasterAlert;
        QUserDisasterAlertRegion uar = QUserDisasterAlertRegion.userDisasterAlertRegion;
        return JPAExpressions
                .selectOne()
                .from(uar)
                .join(uar.legalDistrict, legalDistrict)
                .where(uar.userDisasterAlert.eq(ua)
                        .and(legalDistrict.name.contains(region)))
                .exists();
    }

    private BooleanExpression userDistrictCodeExists(String code) {
        QUserDisasterAlert ua = QUserDisasterAlert.userDisasterAlert;
        QUserDisasterAlertRegion uar = QUserDisasterAlertRegion.userDisasterAlertRegion;
        return JPAExpressions
                .selectOne()
                .from(uar)
                .join(uar.legalDistrict, legalDistrict)
                .where(uar.userDisasterAlert.eq(ua)
                        .and(legalDistrict.code.eq(code)))
                .exists();
    }

    @Override
    public Page<CombinedAlertResponse> searchCombined(AlertSearchRequest req, String source, Pageable pageable) {
        boolean includeOfficial = source == null || source.equalsIgnoreCase("ALL") || source.equalsIgnoreCase("OFFICIAL");
        boolean includeUser = source == null || source.equalsIgnoreCase("ALL") || source.equalsIgnoreCase("USER");

        // 단일 소스만 포함되는 경우에는 해당 소스의 페이지네이션을 그대로 이용한다
        if (includeOfficial && !includeUser) {
            Page<DisasterAlert> page = this.searchAlerts(req, pageable);
            List<CombinedAlertResponse> mapped = page.getContent().stream()
                    .map(DisasterAlertResponseDto::from)
                    .map(CombinedAlertResponse::fromOfficial)
                    .toList();
            return new PageImpl<>(mapped, pageable, page.getTotalElements());
        }

        if (includeUser && !includeOfficial) {
            Page<UserDisasterAlert> page = this.searchUserAlerts(req, pageable);
            List<CombinedAlertResponse> mapped = page.getContent().stream()
                    .map(UserAlertDtos.Response::from)
                    .map(CombinedAlertResponse::fromUser)
                    .toList();
            return new PageImpl<>(mapped, pageable, page.getTotalElements());
        }

        List<CombinedAlertResponse> merged = new ArrayList<>();

        // ALL 케이스: 현재 페이지 윈도우를 정확히 계산하기 위해 offset+size 만큼을 각 소스에서 가져와 병합 후 슬라이싱
        int need = (int) Math.min(Integer.MAX_VALUE, pageable.getOffset() + pageable.getPageSize());
        int fetchSize = Math.max(need, pageable.getPageSize());

        if (includeOfficial) {
            Page<DisasterAlert> off = this.searchAlerts(req, PageRequest.of(0, fetchSize));
            merged.addAll(
                    off.getContent().stream()
                            .map(DisasterAlertResponseDto::from)
                            .map(CombinedAlertResponse::fromOfficial)
                            .toList()
            );
        }

        if (includeUser) {
            Page<UserDisasterAlert> ua = this.searchUserAlerts(req, PageRequest.of(0, fetchSize));
            merged.addAll(
                    ua.getContent().stream()
                            .map(UserAlertDtos.Response::from)
                            .map(CombinedAlertResponse::fromUser)
                            .toList()
            );
        }

        merged.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        int start = Math.min((int) pageable.getOffset(), merged.size());
        int end = Math.min(start + pageable.getPageSize(), merged.size());
        List<CombinedAlertResponse> slice = merged.subList(start, end);

        // total은 각 소스의 total 합으로 계산해 정확한 totalPages 제공
        long total = 0L;
        if (includeOfficial) total += this.countAlertsDistinct(req);
        if (includeUser) total += this.countUserAlertsDistinct(req);
        return new PageImpl<>(slice, pageable, total);
    }
}
