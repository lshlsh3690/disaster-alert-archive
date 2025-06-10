package com.disaster.alert.alertapi.domain.disasteralert.repository;

import com.disaster.alert.alertapi.domain.disasteralert.dto.AlertSearchCondition;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
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
                .where(
                        regionEq(condition.getRegion()),
                        districtCodeEq(condition.getDistrictCode()),
                        dateGoe(condition.getStartDate()),
                        dateLoe(condition.getEndDate()),
                        typeEq(condition.getType()),
                        levelEq(condition.getLevel()),
                        keywordContains(condition.getKeyword())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(disasterAlert.createdAt.desc())
                .fetch();


        long total = queryFactory
                .select(disasterAlert.count())
                .from(disasterAlert)
                .where(
                        regionEq(condition.getRegion()),
                        districtCodeEq(condition.getDistrictCode()),
                        dateGoe(condition.getStartDate()),
                        dateLoe(condition.getEndDate()),
                        typeEq(condition.getType()),
                        levelEq(condition.getLevel()),
                        keywordContains(condition.getKeyword())
                )
                .fetchOne();

        return new PageImpl<>(contents, pageable, total);
    }


    private BooleanExpression regionEq(String region) {
        return StringUtils.hasText(region) ? disasterAlert.legalDistrict.name.contains(region) : null;
    }

    private BooleanExpression districtCodeEq(String code) {
        return StringUtils.hasText(code) ? disasterAlert.legalDistrict.code.eq(code) : null;
    }

    private BooleanExpression dateGoe(LocalDate startDate) {
        return startDate != null ? disasterAlert.createdAt.goe(startDate.atStartOfDay()) : null;
    }

    private BooleanExpression dateLoe(LocalDate endDate) {
        return endDate != null ? disasterAlert.createdAt.loe(endDate.atTime(23, 59, 59)) : null;
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
