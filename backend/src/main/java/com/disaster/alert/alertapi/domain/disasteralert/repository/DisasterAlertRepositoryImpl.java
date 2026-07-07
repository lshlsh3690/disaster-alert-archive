package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.dto.*;
import com.disaster.alert.alertapi.domain.weather.model.QWeatherDailySummary;
import com.disaster.alert.alertapi.domain.weather.model.QWeatherHourlyCorrelationRollup;
import com.disaster.alert.alertapi.domain.weather.model.QWeatherObservation;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.disasteralert.model.QDisasterAlertRegion;
import com.disaster.alert.alertapi.domain.legaldistrict.model.QLegalDistrict;
import com.disaster.alert.alertapi.domain.useralert.model.QUserDisasterAlert;
import com.disaster.alert.alertapi.domain.useralert.model.QUserDisasterAlertRegion;
import com.disaster.alert.alertapi.domain.useralert.model.UserDisasterAlert;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTemplate;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.NumberPath;
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
import java.time.LocalDateTime;
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
        // sidoCodeExpr()/sidoNameByCode() 참고 — getWeatherBySido와 동일한 이유로
        // legalDistrict.name을 regexp_replace/split_part로 파싱하는 대신 코드 prefix로 그룹핑
        StringTemplate sido = sidoCodeExpr();

        List<Tuple> rows = queryFactory
                .select(sido, disasterAlert.id.countDistinct())
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .where(
                        byAlertCondition(request),     // 이미 쓰고 있는 공통 조건 (EXISTS 포함)
                        regionFilterOnJoin(request)    // 지역 필터를 조인 대상에 적용하는 보조 조건
                )
                .groupBy(sido)
                .fetch();

        Map<String, String> sidoNames = sidoNameByCode();
        return rows.stream()
                .map(t -> new DisasterAlertStatResponse.RegionStat(
                        sidoNames.getOrDefault(t.get(0, String.class), t.get(0, String.class)),
                        t.get(1, Long.class)))
                .sorted(Comparator.comparingLong(DisasterAlertStatResponse.RegionStat::getCount).reversed()
                        .thenComparing(DisasterAlertStatResponse.RegionStat::getRegion))
                .collect(Collectors.toList());
    }

    @Override
    public List<DisasterAlertStatResponse.RegionStat> getStatsSigungu(AlertSearchRequest request) {
        StringTemplate norm =
                Expressions.stringTemplate(
                        "function('btrim', function('regexp_replace', {0}, '\\\\s+', ' ', 'g'))",
                        legalDistrict.name
                );

        // 시/도 + 시/군/구 = 첫 두 토큰 (두 번째 토큰이 없으면 첫 토큰만)
        StringTemplate sigungu = Expressions.stringTemplate(
                "CASE WHEN function('split_part', {0}, ' ', 2) = '' " +
                "THEN function('split_part', {0}, ' ', 1) " +
                "ELSE function('split_part', {0}, ' ', 1) || ' ' || function('split_part', {0}, ' ', 2) END",
                norm
        );

        return queryFactory
                .select(Projections.constructor(DisasterAlertStatResponse.RegionStat.class,
                        sigungu,
                        disasterAlert.id.countDistinct()
                ))
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .where(
                        byAlertCondition(request),
                        regionFilterOnJoin(request)
                )
                .groupBy(sigungu)
                .orderBy(disasterAlert.id.countDistinct().desc(), sigungu.asc())
                .fetch();
    }

    @Override
    public List<DisasterAlertStatResponse.DailyStat> getStatsByDate(AlertSearchRequest request) {
        NumberExpression<Integer> year  = disasterAlert.createdAt.year();
        NumberExpression<Integer> month = disasterAlert.createdAt.month();
        NumberExpression<Integer> day   = disasterAlert.createdAt.dayOfMonth();

        List<Tuple> rows = queryFactory
                .select(year, month, day, disasterAlert.id.countDistinct())
                .from(disasterAlert)
                .where(byAlertCondition(request))
                .groupBy(year, month, day)
                .orderBy(year.asc(), month.asc(), day.asc())
                .fetch();

        return rows.stream()
                .filter(t -> t.get(0, Integer.class) != null)
                .map(t -> new DisasterAlertStatResponse.DailyStat(
                        String.format("%04d-%02d-%02d",
                                t.get(0, Integer.class),
                                t.get(1, Integer.class),
                                t.get(2, Integer.class)),
                        t.get(3, Long.class)
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<DisasterAlertStatResponse.HourlyStat> getStatsByHour(AlertSearchRequest request) {
        // PostgreSQL: date_part('dow', ...) returns 0=Sun, 1=Mon, ..., 6=Sat
        NumberExpression<Integer> dow  = Expressions.numberTemplate(Integer.class, "function('date_part', 'dow', {0})", disasterAlert.createdAt);
        NumberExpression<Integer> hour = Expressions.numberTemplate(Integer.class, "function('date_part', 'hour', {0})", disasterAlert.createdAt);
        List<Tuple> rows = queryFactory
                .select(dow, hour, disasterAlert.id.countDistinct())
                .from(disasterAlert)
                .where(byAlertCondition(request))
                .groupBy(dow, hour)
                .orderBy(dow.asc(), hour.asc())
                .fetch();
        return rows.stream()
                .filter(t -> t.get(0, Integer.class) != null)
                .map(t -> new DisasterAlertStatResponse.HourlyStat(
                        t.get(0, Integer.class),
                        t.get(1, Integer.class),
                        t.get(2, Long.class)
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<DisasterAlertStatResponse.MonthlyTypeStat> getStatsByMonthType(AlertSearchRequest request) {
        NumberExpression<Integer> year  = disasterAlert.createdAt.year();
        NumberExpression<Integer> month = disasterAlert.createdAt.month();
        List<Tuple> rows = queryFactory
                .select(year, month, disasterAlert.disasterType, disasterAlert.id.countDistinct())
                .from(disasterAlert)
                .where(byAlertCondition(request).and(disasterAlert.disasterType.isNotNull()))
                .groupBy(year, month, disasterAlert.disasterType)
                .orderBy(year.asc(), month.asc())
                .fetch();
        return rows.stream()
                .filter(t -> t.get(0, Integer.class) != null)
                .map(t -> new DisasterAlertStatResponse.MonthlyTypeStat(
                        String.format("%04d-%02d", t.get(0, Integer.class), t.get(1, Integer.class)),
                        t.get(2, String.class),
                        t.get(3, Long.class)
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<DisasterAlertStatResponse.MonthlyTypeStat> getStatsByDateType(AlertSearchRequest request) {
        NumberExpression<Integer> year  = disasterAlert.createdAt.year();
        NumberExpression<Integer> month = disasterAlert.createdAt.month();
        NumberExpression<Integer> day   = disasterAlert.createdAt.dayOfMonth();
        List<Tuple> rows = queryFactory
                .select(year, month, day, disasterAlert.disasterType, disasterAlert.id.countDistinct())
                .from(disasterAlert)
                .where(byAlertCondition(request).and(disasterAlert.disasterType.isNotNull()))
                .groupBy(year, month, day, disasterAlert.disasterType)
                .orderBy(year.asc(), month.asc(), day.asc())
                .fetch();
        return rows.stream()
                .filter(t -> t.get(0, Integer.class) != null)
                .map(t -> new DisasterAlertStatResponse.MonthlyTypeStat(
                        String.format("%04d-%02d-%02d",
                                t.get(0, Integer.class), t.get(1, Integer.class), t.get(2, Integer.class)),
                        t.get(3, String.class),
                        t.get(4, Long.class)
                ))
                .collect(Collectors.toList());
    }

    /**
     * 법정동 코드 1-2번째 자리(시도)로 GROUP BY 하기 위한 표현식.
     * legalDistrict.name 을 regexp_replace/split_part 로 파싱하는 것보다 훨씬 저렴함
     * (인덱스는 못 타지만 정렬/비교 비용 자체가 거의 없음 — 실측 4.5s → 0.17s).
     */
    private StringTemplate sidoCodeExpr() {
        return Expressions.stringTemplate("function('left', {0}, 2)", legalDistrict.code);
    }

    /**
     * legal_district 테이블 전체(약 5만건, 저렴함)를 한 번만 읽어 시도 코드(2자리) → 표시명 맵을 만든다.
     * 세종특별자치시처럼 시군구 구분이 없어 "코드 뒤 N자리가 0"인 관례를 따르지 않는 코드가 있어
     * "전체" 코드가 아니라 legal_district.name 파싱 결과로 매핑해야 기존 동작과 100% 동일하다.
     *
     * 시군구 레벨(getWeatherBySigungu 등)은 코드 prefix로 그룹핑하지 않는다 — 수원시/성남시처럼
     * 구가 있는 일반시는 legal_district.code상 구별로 다른 코드를 쓰지만 기존 로직은 이름 파싱으로
     * "시 단위"까지만 묶으므로, code prefix로 바꾸면 구가 별도 그룹으로 쪼개져 회귀가 생긴다.
     */
    private Map<String, String> sidoNameByCode() {
        Map<String, String> result = new HashMap<>();
        queryFactory.select(legalDistrict.code, legalDistrict.name).from(legalDistrict).fetch()
                .forEach(t -> result.putIfAbsent(t.get(legalDistrict.code).substring(0, 2), sidoOf(t.get(legalDistrict.name))));
        return result;
    }

    private String sidoOf(String name) {
        return name.trim().replaceAll("\\s+", " ").split(" ")[0];
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

    @Override
    public List<WeatherCorrelationDto> getWeatherCorrelation(AlertSearchRequest request) {
        QWeatherDailySummary wds = QWeatherDailySummary.weatherDailySummary;

        NumberExpression<Integer> year  = disasterAlert.createdAt.year();
        NumberExpression<Integer> month = disasterAlert.createdAt.month();
        NumberExpression<Integer> day   = disasterAlert.createdAt.dayOfMonth();

        List<Tuple> rows = queryFactory
                .select(
                        year,
                        month,
                        day,
                        disasterAlert.id.countDistinct(),
                        wds.avgTemp.avg(),
                        wds.minTemp.min(),
                        wds.maxTemp.max(),
                        wds.totalPrecip.max(),
                        wds.avgWindSpeed.avg()
                )
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .leftJoin(wds).on(
                        wds.legalDistrictCode.eq(legalDistrict.code)
                        .and(wds.date.eq(Expressions.dateTemplate(java.time.LocalDate.class, "cast({0} as date)", disasterAlert.createdAt)))
                )
                .where(byAlertCondition(request), regionFilterOnJoin(request))
                .groupBy(year, month, day)
                .orderBy(year.asc(), month.asc(), day.asc())
                .fetch();

        if (rows.isEmpty()) return List.of();

        // 날짜별 최다 재난 유형 (별도 쿼리)
        List<Tuple> typeRows = queryFactory
                .select(year, month, day, disasterAlert.disasterType, disasterAlert.id.countDistinct())
                .from(disasterAlert)
                .where(byAlertCondition(request), disasterAlert.disasterType.isNotNull())
                .groupBy(year, month, day, disasterAlert.disasterType)
                .orderBy(year.asc(), month.asc(), day.asc(), disasterAlert.id.countDistinct().desc())
                .fetch();

        Map<String, String> primaryTypeByDate = new LinkedHashMap<>();
        for (Tuple t : typeRows) {
            if (t.get(0, Integer.class) == null) continue;
            String key = String.format("%04d-%02d-%02d",
                    t.get(0, Integer.class), t.get(1, Integer.class), t.get(2, Integer.class));
            primaryTypeByDate.putIfAbsent(key, t.get(3, String.class));
        }

        return rows.stream()
                .filter(t -> t.get(0, Integer.class) != null)
                .map(t -> {
                    String dateKey = String.format("%04d-%02d-%02d",
                            t.get(0, Integer.class), t.get(1, Integer.class), t.get(2, Integer.class));
                    return new WeatherCorrelationDto(
                            dateKey,
                            t.get(3, Long.class),
                            t.get(4, Double.class),
                            t.get(5, Double.class),
                            t.get(6, Double.class),
                            t.get(7, Double.class),
                            t.get(8, Double.class),
                            primaryTypeByDate.get(dateKey)
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<WeatherTypeStatDto> getWeatherByType(AlertSearchRequest request) {
        QWeatherDailySummary wds = QWeatherDailySummary.weatherDailySummary;
        NumberExpression<Integer> year  = disasterAlert.createdAt.year();
        NumberExpression<Integer> month = disasterAlert.createdAt.month();
        NumberExpression<Integer> day   = disasterAlert.createdAt.dayOfMonth();

        List<Tuple> rows = queryFactory
                .select(year, month, day,
                        disasterAlert.disasterType,
                        disasterAlert.id.countDistinct(),
                        wds.avgTemp.avg(),
                        wds.minTemp.min(),
                        wds.maxTemp.max(),
                        wds.totalPrecip.max())
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .leftJoin(wds).on(
                        wds.legalDistrictCode.eq(legalDistrict.code)
                        .and(wds.date.eq(Expressions.dateTemplate(java.time.LocalDate.class, "cast({0} as date)", disasterAlert.createdAt))))
                .where(byAlertCondition(request), regionFilterOnJoin(request),
                        disasterAlert.disasterType.isNotNull())
                .groupBy(year, month, day, disasterAlert.disasterType)
                .orderBy(year.asc(), month.asc(), day.asc(), disasterAlert.id.countDistinct().desc())
                .fetch();

        return rows.stream()
                .filter(t -> t.get(0, Integer.class) != null)
                .map(t -> new WeatherTypeStatDto(
                        String.format("%04d-%02d-%02d",
                                t.get(0, Integer.class), t.get(1, Integer.class), t.get(2, Integer.class)),
                        t.get(3, String.class),
                        t.get(4, Long.class),
                        t.get(5, Double.class),
                        t.get(6, Double.class),
                        t.get(7, Double.class),
                        t.get(8, Double.class)))
                .collect(Collectors.toList());
    }

    @Override
    public List<WeatherRegionStatDto> getWeatherBySido(AlertSearchRequest request) {
        QWeatherDailySummary wds = QWeatherDailySummary.weatherDailySummary;
        NumberExpression<Integer> year  = disasterAlert.createdAt.year();
        NumberExpression<Integer> month = disasterAlert.createdAt.month();
        NumberExpression<Integer> day   = disasterAlert.createdAt.dayOfMonth();

        StringTemplate sido = sidoCodeExpr();

        List<Tuple> rows = queryFactory
                .select(year, month, day, sido,
                        disasterAlert.id.countDistinct(),
                        wds.avgTemp.avg(),
                        wds.minTemp.min(),
                        wds.maxTemp.max(),
                        wds.totalPrecip.max())
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .leftJoin(wds).on(
                        wds.legalDistrictCode.eq(legalDistrict.code)
                        .and(wds.date.eq(Expressions.dateTemplate(java.time.LocalDate.class, "cast({0} as date)", disasterAlert.createdAt))))
                .where(byAlertCondition(request), regionFilterOnJoin(request))
                .groupBy(year, month, day, sido)
                .orderBy(year.asc(), month.asc(), day.asc())
                .fetch();

        Map<String, String> sidoNames = sidoNameByCode();
        return rows.stream()
                .filter(t -> t.get(0, Integer.class) != null)
                .map(t -> new WeatherRegionStatDto(
                        String.format("%04d-%02d-%02d",
                                t.get(0, Integer.class), t.get(1, Integer.class), t.get(2, Integer.class)),
                        sidoNames.getOrDefault(t.get(3, String.class), t.get(3, String.class)),
                        t.get(4, Long.class),
                        t.get(5, Double.class),
                        t.get(6, Double.class),
                        t.get(7, Double.class),
                        t.get(8, Double.class)))
                .collect(Collectors.toList());
    }

    @Override
    public List<WeatherRegionStatDto> getWeatherBySigungu(AlertSearchRequest request) {
        QWeatherDailySummary wds = QWeatherDailySummary.weatherDailySummary;
        NumberExpression<Integer> year  = disasterAlert.createdAt.year();
        NumberExpression<Integer> month = disasterAlert.createdAt.month();
        NumberExpression<Integer> day   = disasterAlert.createdAt.dayOfMonth();

        StringTemplate norm = Expressions.stringTemplate(
                "function('btrim', function('regexp_replace', {0}, '\\\\s+', ' ', 'g'))", legalDistrict.name);
        StringTemplate sigungu = Expressions.stringTemplate(
                "CASE WHEN function('split_part', {0}, ' ', 2) = '' " +
                "THEN function('split_part', {0}, ' ', 1) " +
                "ELSE function('split_part', {0}, ' ', 1) || ' ' || function('split_part', {0}, ' ', 2) END",
                norm);

        List<Tuple> rows = queryFactory
                .select(year, month, day, sigungu,
                        disasterAlert.id.countDistinct(),
                        wds.avgTemp.avg(),
                        wds.minTemp.min(),
                        wds.maxTemp.max(),
                        wds.totalPrecip.max())
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .leftJoin(wds).on(
                        wds.legalDistrictCode.eq(legalDistrict.code)
                        .and(wds.date.eq(Expressions.dateTemplate(java.time.LocalDate.class, "cast({0} as date)", disasterAlert.createdAt))))
                .where(byAlertCondition(request), regionFilterOnJoin(request))
                .groupBy(year, month, day, sigungu)
                .orderBy(year.asc(), month.asc(), day.asc())
                .fetch();

        return rows.stream()
                .filter(t -> t.get(0, Integer.class) != null)
                .map(t -> new WeatherRegionStatDto(
                        String.format("%04d-%02d-%02d",
                                t.get(0, Integer.class), t.get(1, Integer.class), t.get(2, Integer.class)),
                        t.get(3, String.class),
                        t.get(4, Long.class),
                        t.get(5, Double.class),
                        t.get(6, Double.class),
                        t.get(7, Double.class),
                        t.get(8, Double.class)))
                .collect(Collectors.toList());
    }

    // ─── 시간별 날씨 상관 (weather_hourly_correlation_rollup, type/level/keyword 필터 없을 때) ──
    // type/level/keyword 필터가 있으면 롤업(alert_count가 유형 무관 전체 건수)으로 답할 수 없어
    // weather_observation 라이브 조인으로 폴백한다 (7일 이하 기간 전용 — 프론트에서만 강제).

    @Override
    public List<WeatherCorrelationDto> getWeatherHourlyCorrelation(AlertSearchRequest request) {
        return isUnfilteredForRollup(request)
                ? getWeatherHourlyCorrelationFromRollup(request)
                : getWeatherHourlyCorrelationLive(request);
    }

    private List<WeatherCorrelationDto> getWeatherHourlyCorrelationFromRollup(AlertSearchRequest request) {
        QWeatherHourlyCorrelationRollup r = QWeatherHourlyCorrelationRollup.weatherHourlyCorrelationRollup;

        // 주의: r.alertCount는 시군구별 값이라 그대로 SUM하면 여러 시군구에 걸친 alert가 중복
        // 집계된다 (countDistinct가 아님). 표시용 건수는 weather 조인 없는 별도 경량 쿼리로
        // 정확히 구하고, 롤업의 alertCount는 가중평균의 가중치로만 쓴다.
        List<Tuple> rows = queryFactory
                .select(r.bucketHour,
                        weightedAvg(r.avgTemp, r.alertCount),
                        weightedAvg(r.avgPrecip, r.alertCount),
                        weightedAvg(r.avgWindSpeed, r.alertCount))
                .from(r)
                .join(legalDistrict).on(legalDistrict.code.eq(r.legalDistrictCode))
                .where(rollupDateCondition(request, r.bucketHour), regionFilterOnJoin(request))
                .groupBy(r.bucketHour)
                .orderBy(r.bucketHour.asc())
                .fetch();

        if (rows.isEmpty()) return List.of();

        Map<String, String> primaryTypeByHour = primaryTypeByHour(request);
        Map<String, Long> alertCountByHour = alertCountByHour(request);

        return rows.stream()
                .map(t -> {
                    String key = hourKey(t.get(0, LocalDateTime.class));
                    return new WeatherCorrelationDto(
                            key,
                            alertCountByHour.getOrDefault(key, 0L),
                            t.get(1, Double.class),
                            null, null,           // minTemp/maxTemp — 시간별에는 없음
                            t.get(2, Double.class),
                            t.get(3, Double.class),
                            primaryTypeByHour.get(key)
                    );
                })
                .collect(Collectors.toList());
    }

    /** 시간당 정확한 alert 건수(countDistinct) — weather 조인 없이 disasterAlert만 대상이라 이미 충분히 빠르다. */
    private Map<String, Long> alertCountByHour(AlertSearchRequest request) {
        NumberExpression<Integer> year  = disasterAlert.createdAt.year();
        NumberExpression<Integer> month = disasterAlert.createdAt.month();
        NumberExpression<Integer> day   = disasterAlert.createdAt.dayOfMonth();
        NumberExpression<Integer> hour  = disasterAlert.createdAt.hour();

        List<Tuple> rows = queryFactory
                .select(year, month, day, hour, disasterAlert.id.countDistinct())
                .from(disasterAlert)
                .where(byAlertCondition(request))
                .groupBy(year, month, day, hour)
                .fetch();

        Map<String, Long> result = new HashMap<>();
        for (Tuple t : rows) {
            if (t.get(0, Integer.class) == null) continue;
            result.put(String.format("%04d-%02d-%02d %02d:00",
                            t.get(0, Integer.class), t.get(1, Integer.class),
                            t.get(2, Integer.class), t.get(3, Integer.class)),
                    t.get(4, Long.class));
        }
        return result;
    }

    private List<WeatherCorrelationDto> getWeatherHourlyCorrelationLive(AlertSearchRequest request) {
        QWeatherObservation wo = QWeatherObservation.weatherObservation;

        NumberExpression<Integer> year  = disasterAlert.createdAt.year();
        NumberExpression<Integer> month = disasterAlert.createdAt.month();
        NumberExpression<Integer> day   = disasterAlert.createdAt.dayOfMonth();
        NumberExpression<Integer> hour  = disasterAlert.createdAt.hour();

        List<Tuple> rows = queryFactory
                .select(year, month, day, hour,
                        disasterAlert.id.countDistinct(),
                        wo.temperature.avg(),
                        wo.precipitation.avg(),
                        wo.windSpeed.avg())
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .leftJoin(wo).on(
                        wo.legalDistrictCode.eq(legalDistrict.code)
                        .and(wo.observedAt.year().eq(disasterAlert.createdAt.year()))
                        .and(wo.observedAt.month().eq(disasterAlert.createdAt.month()))
                        .and(wo.observedAt.dayOfMonth().eq(disasterAlert.createdAt.dayOfMonth()))
                        .and(wo.observedAt.hour().eq(disasterAlert.createdAt.hour())))
                .where(byAlertCondition(request), regionFilterOnJoin(request))
                .groupBy(year, month, day, hour)
                .orderBy(year.asc(), month.asc(), day.asc(), hour.asc())
                .fetch();

        if (rows.isEmpty()) return List.of();

        Map<String, String> primaryTypeByHour = primaryTypeByHour(request);

        return rows.stream()
                .filter(t -> t.get(0, Integer.class) != null)
                .map(t -> {
                    String key = String.format("%04d-%02d-%02d %02d:00",
                            t.get(0, Integer.class), t.get(1, Integer.class),
                            t.get(2, Integer.class), t.get(3, Integer.class));
                    return new WeatherCorrelationDto(
                            key,
                            t.get(4, Long.class),
                            t.get(5, Double.class),
                            null, null,           // minTemp/maxTemp — 시간별에는 없음
                            t.get(6, Double.class),
                            t.get(7, Double.class),
                            primaryTypeByHour.get(key)
                    );
                })
                .collect(Collectors.toList());
    }

    /** disasterType별 시간당 건수 상위 1건 — weather 조인과 무관하므로 롤업/라이브 양쪽에서 공용. */
    private Map<String, String> primaryTypeByHour(AlertSearchRequest request) {
        NumberExpression<Integer> year  = disasterAlert.createdAt.year();
        NumberExpression<Integer> month = disasterAlert.createdAt.month();
        NumberExpression<Integer> day   = disasterAlert.createdAt.dayOfMonth();
        NumberExpression<Integer> hour  = disasterAlert.createdAt.hour();

        List<Tuple> typeRows = queryFactory
                .select(year, month, day, hour, disasterAlert.disasterType, disasterAlert.id.countDistinct())
                .from(disasterAlert)
                .where(byAlertCondition(request), disasterAlert.disasterType.isNotNull())
                .groupBy(year, month, day, hour, disasterAlert.disasterType)
                .orderBy(year.asc(), month.asc(), day.asc(), hour.asc(), disasterAlert.id.countDistinct().desc())
                .fetch();

        Map<String, String> result = new LinkedHashMap<>();
        for (Tuple t : typeRows) {
            if (t.get(0, Integer.class) == null) continue;
            String key = String.format("%04d-%02d-%02d %02d:00",
                    t.get(0, Integer.class), t.get(1, Integer.class),
                    t.get(2, Integer.class), t.get(3, Integer.class));
            result.putIfAbsent(key, t.get(4, String.class));
        }
        return result;
    }

    /** type/level/keyword 필터가 없어야 롤업(alert_count가 유형 무관 전체 건수)으로 답할 수 있다. */
    private boolean isUnfilteredForRollup(AlertSearchRequest request) {
        return request.getType() == null && request.getLevel() == null && !StringUtils.hasText(request.getKeyword());
    }

    private BooleanBuilder rollupDateCondition(AlertSearchRequest request, DateTimePath<LocalDateTime> bucketHour) {
        BooleanBuilder b = new BooleanBuilder();
        if (request.getStartDate() != null) b.and(bucketHour.goe(request.getStartDate().atStartOfDay()));
        if (request.getEndDate() != null) b.and(bucketHour.loe(request.getEndDate().atTime(23, 59, 59, 999)));
        return b;
    }

    /** alert_count로 가중한 평균 — NULL(날씨 미수집) 행은 분자/분모 양쪽에서 제외. */
    private NumberExpression<Double> weightedAvg(NumberPath<Double> valueCol, NumberPath<Integer> weightCol) {
        return Expressions.numberTemplate(Double.class,
                "SUM(CASE WHEN {0} IS NOT NULL THEN {0} * {1} ELSE 0 END) / NULLIF(SUM(CASE WHEN {0} IS NOT NULL THEN {1} ELSE 0 END), 0)",
                valueCol, weightCol);
    }

    private String hourKey(LocalDateTime dt) {
        return String.format("%04d-%02d-%02d %02d:00", dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(), dt.getHour());
    }

    @Override
    public List<WeatherTypeStatDto> getWeatherHourlyByType(AlertSearchRequest request) {
        QWeatherObservation wo = QWeatherObservation.weatherObservation;

        NumberExpression<Integer> year  = disasterAlert.createdAt.year();
        NumberExpression<Integer> month = disasterAlert.createdAt.month();
        NumberExpression<Integer> day   = disasterAlert.createdAt.dayOfMonth();
        NumberExpression<Integer> hour  = disasterAlert.createdAt.hour();

        List<Tuple> rows = queryFactory
                .select(year, month, day, hour,
                        disasterAlert.disasterType,
                        disasterAlert.id.countDistinct(),
                        wo.temperature.avg(),
                        wo.precipitation.avg())
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .leftJoin(wo).on(
                        wo.legalDistrictCode.eq(legalDistrict.code)
                        .and(wo.observedAt.year().eq(disasterAlert.createdAt.year()))
                        .and(wo.observedAt.month().eq(disasterAlert.createdAt.month()))
                        .and(wo.observedAt.dayOfMonth().eq(disasterAlert.createdAt.dayOfMonth()))
                        .and(wo.observedAt.hour().eq(disasterAlert.createdAt.hour())))
                .where(byAlertCondition(request), regionFilterOnJoin(request),
                        disasterAlert.disasterType.isNotNull())
                .groupBy(year, month, day, hour, disasterAlert.disasterType)
                .orderBy(year.asc(), month.asc(), day.asc(), hour.asc(), disasterAlert.id.countDistinct().desc())
                .fetch();

        return rows.stream()
                .filter(t -> t.get(0, Integer.class) != null)
                .map(t -> new WeatherTypeStatDto(
                        String.format("%04d-%02d-%02d %02d:00",
                                t.get(0, Integer.class), t.get(1, Integer.class),
                                t.get(2, Integer.class), t.get(3, Integer.class)),
                        t.get(4, String.class),
                        t.get(5, Long.class),
                        t.get(6, Double.class),
                        null, null,
                        t.get(7, Double.class)))
                .collect(Collectors.toList());
    }

    @Override
    public List<WeatherRegionStatDto> getWeatherHourlyBySido(AlertSearchRequest request) {
        return isUnfilteredForRollup(request)
                ? getWeatherHourlyBySidoFromRollup(request)
                : getWeatherHourlyBySidoLive(request);
    }

    private List<WeatherRegionStatDto> getWeatherHourlyBySidoFromRollup(AlertSearchRequest request) {
        QWeatherHourlyCorrelationRollup r = QWeatherHourlyCorrelationRollup.weatherHourlyCorrelationRollup;
        StringTemplate sido = Expressions.stringTemplate("function('left', {0}, 2)", r.legalDistrictCode);

        // 표시용 건수는 weather 조인 없는 alertCountByHourAndSido로 정확히 구한다 (사유는
        // getWeatherHourlyCorrelationFromRollup 주석 참고 — 같은 시도 내 여러 시군구에 걸친
        // alert가 r.alertCount 단순 SUM으로는 중복 집계됨).
        List<Tuple> rows = queryFactory
                .select(r.bucketHour, sido,
                        weightedAvg(r.avgTemp, r.alertCount),
                        weightedAvg(r.avgPrecip, r.alertCount))
                .from(r)
                .join(legalDistrict).on(legalDistrict.code.eq(r.legalDistrictCode))
                .where(rollupDateCondition(request, r.bucketHour), regionFilterOnJoin(request))
                .groupBy(r.bucketHour, sido)
                .orderBy(r.bucketHour.asc())
                .fetch();

        Map<String, String> sidoNames = sidoNameByCode();
        Map<String, Long> alertCountByHourAndSido = alertCountByHourAndSido(request);
        return rows.stream()
                .map(t -> {
                    String sidoCode = t.get(1, String.class);
                    String key = hourKey(t.get(0, LocalDateTime.class)) + "|" + sidoCode;
                    return new WeatherRegionStatDto(
                            hourKey(t.get(0, LocalDateTime.class)),
                            sidoNames.getOrDefault(sidoCode, sidoCode),
                            alertCountByHourAndSido.getOrDefault(key, 0L),
                            t.get(2, Double.class),
                            null, null,
                            t.get(3, Double.class));
                })
                .collect(Collectors.toList());
    }

    /** 시간×시도별 정확한 alert 건수 — weather 조인 없이 disasterAlertRegion/legalDistrict만 대상이라 빠르다. */
    private Map<String, Long> alertCountByHourAndSido(AlertSearchRequest request) {
        NumberExpression<Integer> year  = disasterAlert.createdAt.year();
        NumberExpression<Integer> month = disasterAlert.createdAt.month();
        NumberExpression<Integer> day   = disasterAlert.createdAt.dayOfMonth();
        NumberExpression<Integer> hour  = disasterAlert.createdAt.hour();
        StringTemplate sido = sidoCodeExpr();

        List<Tuple> rows = queryFactory
                .select(year, month, day, hour, sido, disasterAlert.id.countDistinct())
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .where(byAlertCondition(request), regionFilterOnJoin(request))
                .groupBy(year, month, day, hour, sido)
                .fetch();

        Map<String, Long> result = new HashMap<>();
        for (Tuple t : rows) {
            if (t.get(0, Integer.class) == null) continue;
            String key = String.format("%04d-%02d-%02d %02d:00",
                            t.get(0, Integer.class), t.get(1, Integer.class),
                            t.get(2, Integer.class), t.get(3, Integer.class))
                    + "|" + t.get(4, String.class);
            result.put(key, t.get(5, Long.class));
        }
        return result;
    }

    private List<WeatherRegionStatDto> getWeatherHourlyBySidoLive(AlertSearchRequest request) {
        QWeatherObservation wo = QWeatherObservation.weatherObservation;

        NumberExpression<Integer> year  = disasterAlert.createdAt.year();
        NumberExpression<Integer> month = disasterAlert.createdAt.month();
        NumberExpression<Integer> day   = disasterAlert.createdAt.dayOfMonth();
        NumberExpression<Integer> hour  = disasterAlert.createdAt.hour();

        StringTemplate sido = sidoCodeExpr();

        List<Tuple> rows = queryFactory
                .select(year, month, day, hour, sido,
                        disasterAlert.id.countDistinct(),
                        wo.temperature.avg(),
                        wo.precipitation.avg())
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .leftJoin(wo).on(
                        wo.legalDistrictCode.eq(legalDistrict.code)
                        .and(wo.observedAt.year().eq(disasterAlert.createdAt.year()))
                        .and(wo.observedAt.month().eq(disasterAlert.createdAt.month()))
                        .and(wo.observedAt.dayOfMonth().eq(disasterAlert.createdAt.dayOfMonth()))
                        .and(wo.observedAt.hour().eq(disasterAlert.createdAt.hour())))
                .where(byAlertCondition(request), regionFilterOnJoin(request))
                .groupBy(year, month, day, hour, sido)
                .orderBy(year.asc(), month.asc(), day.asc(), hour.asc())
                .fetch();

        Map<String, String> sidoNames = sidoNameByCode();
        return rows.stream()
                .filter(t -> t.get(0, Integer.class) != null)
                .map(t -> new WeatherRegionStatDto(
                        String.format("%04d-%02d-%02d %02d:00",
                                t.get(0, Integer.class), t.get(1, Integer.class),
                                t.get(2, Integer.class), t.get(3, Integer.class)),
                        sidoNames.getOrDefault(t.get(4, String.class), t.get(4, String.class)),
                        t.get(5, Long.class),
                        t.get(6, Double.class),
                        null, null,
                        t.get(7, Double.class)))
                .collect(Collectors.toList());
    }

    @Override
    public List<WeatherRegionStatDto> getWeatherHourlyBySigungu(AlertSearchRequest request) {
        return isUnfilteredForRollup(request)
                ? getWeatherHourlyBySigunguFromRollup(request)
                : getWeatherHourlyBySigunguLive(request);
    }

    private List<WeatherRegionStatDto> getWeatherHourlyBySigunguFromRollup(AlertSearchRequest request) {
        QWeatherHourlyCorrelationRollup r = QWeatherHourlyCorrelationRollup.weatherHourlyCorrelationRollup;
        StringTemplate sigungu = sigunguExpr();

        // 표시용 건수는 weather 조인 없는 alertCountByHourAndSigungu로 정확히 구한다 (사유는
        // getWeatherHourlyCorrelationFromRollup 주석 참고 — 수원시/성남시처럼 구가 나뉜 일반시는
        // legal_district_code가 구별로 달라 같은 시군구 표시명 안에서도 r.alertCount 단순 SUM이
        // 중복 집계될 수 있음).
        List<Tuple> rows = queryFactory
                .select(r.bucketHour, sigungu,
                        weightedAvg(r.avgTemp, r.alertCount),
                        weightedAvg(r.avgPrecip, r.alertCount))
                .from(r)
                .join(legalDistrict).on(legalDistrict.code.eq(r.legalDistrictCode))
                .where(rollupDateCondition(request, r.bucketHour), regionFilterOnJoin(request))
                .groupBy(r.bucketHour, sigungu)
                .orderBy(r.bucketHour.asc())
                .fetch();

        Map<String, Long> alertCountByHourAndSigungu = alertCountByHourAndSigungu(request);
        return rows.stream()
                .map(t -> {
                    String sigunguName = t.get(1, String.class);
                    String key = hourKey(t.get(0, LocalDateTime.class)) + "|" + sigunguName;
                    return new WeatherRegionStatDto(
                            hourKey(t.get(0, LocalDateTime.class)),
                            sigunguName,
                            alertCountByHourAndSigungu.getOrDefault(key, 0L),
                            t.get(2, Double.class),
                            null, null,
                            t.get(3, Double.class));
                })
                .collect(Collectors.toList());
    }

    /** 시간×시군구별 정확한 alert 건수 — weather 조인 없이 disasterAlertRegion/legalDistrict만 대상이라 빠르다. */
    private Map<String, Long> alertCountByHourAndSigungu(AlertSearchRequest request) {
        NumberExpression<Integer> year  = disasterAlert.createdAt.year();
        NumberExpression<Integer> month = disasterAlert.createdAt.month();
        NumberExpression<Integer> day   = disasterAlert.createdAt.dayOfMonth();
        NumberExpression<Integer> hour  = disasterAlert.createdAt.hour();
        StringTemplate sigungu = sigunguExpr();

        List<Tuple> rows = queryFactory
                .select(year, month, day, hour, sigungu, disasterAlert.id.countDistinct())
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .where(byAlertCondition(request), regionFilterOnJoin(request))
                .groupBy(year, month, day, hour, sigungu)
                .fetch();

        Map<String, Long> result = new HashMap<>();
        for (Tuple t : rows) {
            if (t.get(0, Integer.class) == null) continue;
            String key = String.format("%04d-%02d-%02d %02d:00",
                            t.get(0, Integer.class), t.get(1, Integer.class),
                            t.get(2, Integer.class), t.get(3, Integer.class))
                    + "|" + t.get(4, String.class);
            result.put(key, t.get(5, Long.class));
        }
        return result;
    }

    private List<WeatherRegionStatDto> getWeatherHourlyBySigunguLive(AlertSearchRequest request) {
        QWeatherObservation wo = QWeatherObservation.weatherObservation;

        NumberExpression<Integer> year  = disasterAlert.createdAt.year();
        NumberExpression<Integer> month = disasterAlert.createdAt.month();
        NumberExpression<Integer> day   = disasterAlert.createdAt.dayOfMonth();
        NumberExpression<Integer> hour  = disasterAlert.createdAt.hour();

        StringTemplate sigungu = sigunguExpr();

        List<Tuple> rows = queryFactory
                .select(year, month, day, hour, sigungu,
                        disasterAlert.id.countDistinct(),
                        wo.temperature.avg(),
                        wo.precipitation.avg())
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .leftJoin(wo).on(
                        wo.legalDistrictCode.eq(legalDistrict.code)
                        .and(wo.observedAt.year().eq(disasterAlert.createdAt.year()))
                        .and(wo.observedAt.month().eq(disasterAlert.createdAt.month()))
                        .and(wo.observedAt.dayOfMonth().eq(disasterAlert.createdAt.dayOfMonth()))
                        .and(wo.observedAt.hour().eq(disasterAlert.createdAt.hour())))
                .where(byAlertCondition(request), regionFilterOnJoin(request))
                .groupBy(year, month, day, hour, sigungu)
                .orderBy(year.asc(), month.asc(), day.asc(), hour.asc())
                .fetch();

        return rows.stream()
                .filter(t -> t.get(0, Integer.class) != null)
                .map(t -> new WeatherRegionStatDto(
                        String.format("%04d-%02d-%02d %02d:00",
                                t.get(0, Integer.class), t.get(1, Integer.class),
                                t.get(2, Integer.class), t.get(3, Integer.class)),
                        t.get(4, String.class),
                        t.get(5, Long.class),
                        t.get(6, Double.class),
                        null, null,
                        t.get(7, Double.class)))
                .collect(Collectors.toList());
    }

    /** legalDistrict.name의 첫 두 토큰("시/도 + 시/군/구")을 시군구 표시명으로 파싱. */
    private StringTemplate sigunguExpr() {
        StringTemplate norm = Expressions.stringTemplate(
                "function('btrim', function('regexp_replace', {0}, '\\\\s+', ' ', 'g'))", legalDistrict.name);
        return Expressions.stringTemplate(
                "CASE WHEN function('split_part', {0}, ' ', 2) = '' " +
                "THEN function('split_part', {0}, ' ', 1) " +
                "ELSE function('split_part', {0}, ' ', 1) || ' ' || function('split_part', {0}, ' ', 2) END",
                norm);
    }

    @Override
    public Optional<AlertWeatherDto> getAlertWeather(Long alertId) {
        QWeatherDailySummary wds = QWeatherDailySummary.weatherDailySummary;

        Tuple result = queryFactory
                .select(wds.avgTemp.avg(), wds.totalPrecip.avg(), wds.avgWindSpeed.avg())
                .from(disasterAlert)
                .join(disasterAlert.disasterAlertRegions, disasterAlertRegion)
                .join(disasterAlertRegion.legalDistrict, legalDistrict)
                .leftJoin(wds).on(
                        wds.legalDistrictCode.eq(legalDistrict.code)
                        .and(wds.date.eq(Expressions.dateTemplate(java.time.LocalDate.class, "cast({0} as date)", disasterAlert.createdAt)))
                )
                .where(disasterAlert.id.eq(alertId))
                .fetchOne();

        if (result == null) return Optional.empty();
        Double temp   = result.get(0, Double.class);
        Double precip = result.get(1, Double.class);
        Double wind   = result.get(2, Double.class);
        if (temp == null && precip == null && wind == null) return Optional.empty();
        return Optional.of(new AlertWeatherDto(temp, precip, wind));
    }
}
