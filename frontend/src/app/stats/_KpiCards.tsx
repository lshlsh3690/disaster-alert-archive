"use client";

/**
 * _KpiCards.tsx
 *
 * 통계 페이지 상단에 배치되는 UI 컴포넌트 모음입니다.
 *
 * 포함된 컴포넌트:
 *   - KpiBox       : 총 발생 건수, 일 평균 등 핵심 수치를 보여주는 숫자 카드
 *   - FilterBanner : 재난문자 페이지에서 설정한 필터 조건을 태그로 보여주는 배너
 */

import Link from "next/link";
import { useTranslation } from "react-i18next";

// ─── KPI 카드 ─────────────────────────────────────────────────────────────────

/**
 * KpiBox
 *
 * 레이블 + 큰 숫자 + 부가 설명으로 구성된 단순 정보 카드입니다.
 * 총 발생 건수, 일 평균, 최다 유형, 최다 지역을 각각 하나씩 표시합니다.
 * KRDS 강조색 5% 규칙에 따라 카드별 색 상단 보더와 아이콘은 쓰지 않습니다.
 *
 * @param label   - 카드 제목 (예: "총 발생 건수")
 * @param value   - 강조 표시할 주요 값 (예: "1,234건")
 * @param sub     - 보조 설명 텍스트 (예: "최근 30일 기준")
 */
export function KpiBox({
  label,
  value,
  sub,
}: {
  label: string;
  value: string;
  sub: string;
}) {
  return (
    <article className="stats-kpi">
      <p className="stats-kpi-label">{label}</p>
      <p className="stats-kpi-value truncate">{value}</p>
      <p className="stats-kpi-meta">{sub}</p>
    </article>
  );
}

// ─── 필터 배너 ───────────────────────────────────────────────────────────────

/**
 * FilterBanner
 *
 * 재난문자 페이지(/alerts)에서 URL 파라미터로 넘어온 필터 조건을
 * 회색 배지 목록으로 시각화합니다.
 *
 * 필터가 하나도 없으면 "전체 데이터" 안내 메시지를 보여줍니다.
 * 필터가 있으면 왼쪽에 태그들, 오른쪽에 필터 변경 링크를 표시합니다.
 *
 * @param sido      - 시·도 이름 (예: "경기도")
 * @param sigungu   - 시·군·구 이름 (예: "수원시")
 * @param startDate - 조회 시작일 "YYYY-MM-DD"
 * @param endDate   - 조회 종료일 "YYYY-MM-DD"
 * @param type      - 재난 유형 (예: "호우")
 * @param levelText - 경보 단계 텍스트 (예: "위급재난")
 * @param keyword   - 검색 키워드
 * @param source    - 출처 필터 "ALL" | "OFFICIAL" | "USER"
 */
export function FilterBanner({
  sido,
  sigungu,
  startDate,
  endDate,
  type,
  levelText,
  keyword,
  source,
  onFilterOpen,
}: {
  sido?: string;
  sigungu?: string;
  startDate?: string;
  endDate?: string;
  type?: string;
  levelText?: string;
  keyword?: string;
  source?: string;
  onFilterOpen?: () => void;
}) {
  const { t } = useTranslation();
  // 값이 있는 필터만 태그 배열로 모읍니다
  // filter(Boolean): falsy 값(undefined, null, false)을 제거합니다
  const tags = [
    sido                             && { label: t(`metros.${sido}`, { defaultValue: sido }) },
    sigungu                          && { label: sigungu },
    (startDate || endDate)           && { label: `${startDate ?? "~"} ~ ${endDate ?? "~"}` },
    type                             && { label: `${t("statsPage.typePrefix")}: ${t(`disasterTypes.${type}`, { defaultValue: type })}` },
    levelText                        && { label: `${t("statsPage.levelPrefix")}: ${levelText}` },
    keyword                          && { label: `"${keyword}"` },
    source && source !== "ALL"       && { label: source === "OFFICIAL" ? t("alertList.filter.sourceOfficial") : t("alertList.filter.sourceUser") },
  ].filter(Boolean) as { label: string }[];

  // 재난문자 페이지로 돌아갈 때 현재 필터를 유지하는 URL을 만듭니다
  const alertsHref = `/alerts?${[
    sido       && `sido=${encodeURIComponent(sido)}`,
    sigungu    && `sigungu=${encodeURIComponent(sigungu)}`,
    startDate  && `startDate=${startDate}`,
    endDate    && `endDate=${endDate}`,
    type       && `type=${encodeURIComponent(type)}`,
    levelText  && `levelText=${encodeURIComponent(levelText)}`,
    keyword    && `keyword=${encodeURIComponent(keyword)}`,
    source     && `source=${source}`,
  ].filter(Boolean).join("&")}`;

  // 필터가 없으면 전체 데이터 안내
  if (tags.length === 0) {
    return (
      <div className="krds-card px-5 py-4 text-[16px] text-[var(--krds-gray-70)]">
        {t("statsPage.noFilters")}{" "}
        {onFilterOpen ? (
          <button onClick={onFilterOpen} className="font-bold text-[var(--krds-primary-50)] hover:underline">
            {t("statsPage.setFilters")}
          </button>
        ) : (
          <Link href="/alerts" className="font-bold text-[var(--krds-primary-50)] hover:underline">
            {t("statsPage.changeFilters")}
          </Link>
        )}
      </div>
    );
  }

  return (
    <div className="krds-card px-5 py-3 flex items-center gap-2 flex-wrap">
      {/* 필터 적용 중 레이블 */}
      <span className="text-[15px] font-bold text-[var(--krds-gray-90)]">
        {/* 인라인 SVG 깔때기 아이콘 */}
        <svg className="inline mr-1" style={{ verticalAlign: "-2px" }}
          width="13" height="13" viewBox="0 0 14 14" fill="none">
          <path d="M2 3h10l-3.5 4.5V12L5.5 10.5V7.5L2 3z"
            stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
        </svg>
        {t("statsPage.filtersApplied")}
      </span>

      {/* 필터 태그들 */}
      {tags.map((tag, i) => (
        <span key={i} className="krds-badge krds-badge--neutral">
          {tag.label}
        </span>
      ))}

      {/* 우측 정렬을 위한 빈 공간 */}
      <span className="flex-1" />

      {onFilterOpen ? (
        <button onClick={onFilterOpen} className="text-[15px] font-bold text-[var(--krds-primary-50)] hover:underline">
          {t("statsPage.changeFilters")}
        </button>
      ) : (
        <Link href={alertsHref} className="text-[15px] font-bold text-[var(--krds-primary-50)] hover:underline">
          {t("statsPage.changeFilters")}
        </Link>
      )}
    </div>
  );
}
