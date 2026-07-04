"use client";

import dynamic from "next/dynamic";
import { useEffect, useMemo, useState, useRef, useCallback } from "react";
import { useAlertRisk, useRegionRisk, useRegionRiskHistory } from "@/lib/queries/useRisk";
import { fetchGeoJsonCached } from "@/lib/geojsonCache";
import { normalizeScore, scoreToGrade, aggregateBySigungu, type ImpactGrade } from "@/lib/riskScore";
import type { RiskHistoryPoint } from "@/types/risk";
import { useI18n } from "@/hooks/useI18n";
import { useLanguageStore } from "@/store/languageStore";

// 지도 + Kakao SDK 코드는 무겁기 때문에 별도 청크로 분리하고,
// 위험도 데이터가 있을 때만 클라이언트에서 로드 (초기 번들/LCP 부담 제거)
const AlertRiskMap = dynamic(() => import("@/components/map/AlertRiskMap"), {
  ssr: false,
  loading: () => <div className="h-[472px] bg-gray-100 rounded-lg animate-pulse" />,
});

// 지도(AlertRiskMap) 등급 팔레트와 동일 계열의 Tailwind 클래스
const GRADE_BOX = [
  "bg-green-50 text-green-700 border-green-200",
  "bg-yellow-50 text-yellow-700 border-yellow-200",
  "bg-orange-50 text-orange-700 border-orange-200",
  "bg-red-50 text-red-700 border-red-200",
] as const;
const GRADE_HEX = ["#22a45d", "#e0a400", "#ea7a3b", "#dc4d3f"] as const;
const GRADE_SOFT = ["#e8f5ec", "#fbf3dd", "#fdeee3", "#fdeceb"] as const;

/**
 * 지역 코드 → 지역명 (지도와 같은 geojson 캐시 재사용 → 추가 다운로드 없음).
 * 시도 전체 발송 코드(xx000, 예: 29000 광주광역시)는 시도명으로 매핑.
 */
function useRegionNames(lang: string) {
  const [names, setNames] = useState<Map<string, string> | null>(null);
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      fetchGeoJsonCached("/sigungu.geojson"),
      fetchGeoJsonCached("/sido.geojson"),
    ])
      .then(([sigunguGeo, sidoGeo]) => {
        if (cancelled) return;
        const m = new Map<string, string>();
        sigunguGeo.features.forEach((f: { properties: { SIG_CD: string; SIG_KOR_NM: string; SIG_ENG_NM?: string } }) => {
          const name = lang === "en"
            ? f.properties.SIG_ENG_NM || f.properties.SIG_KOR_NM
            : f.properties.SIG_KOR_NM;
          m.set(f.properties.SIG_CD, name);
        });
        sidoGeo.features.forEach((f: { properties: { CTPRVN_CD: string; CTP_KOR_NM: string; CTP_ENG_NM?: string } }) => {
          const name = lang === "en"
            ? f.properties.CTP_ENG_NM || f.properties.CTP_KOR_NM
            : f.properties.CTP_KOR_NM;
          m.set(`${f.properties.CTPRVN_CD}000`, name);
        });
        setNames(m);
      })
      .catch(() => { if (!cancelled) setNames(new Map()); });
    return () => { cancelled = true; };
  }, [lang]);
  return names;
}

const SPARK = { W: 100, H: 32, PAD: 2 };

/** 0~1 위험도 시계열 → 인라인 SVG 스파크라인 (호버 시 크로스헤어 + 변화율 툴팁). */
function TrendSparkline({
  points,
  trendLabel,
  vsStartLabel,
}: {
  points: RiskHistoryPoint[];
  trendLabel: string;
  vsStartLabel: string;
}) {
  const { W, H, PAD } = SPARK;
  const trendRef = useRef<HTMLDivElement>(null);
  const [hoverIdx, setHoverIdx] = useState<number | null>(null);

  const xy = useMemo(
    () =>
      points.map((p, i) => {
        const x = points.length === 1 ? W / 2 : PAD + (i / (points.length - 1)) * (W - PAD * 2);
        const y = H - PAD - p.riskScore * (H - PAD * 2);
        return [x, y] as const;
      }),
    [points]
  );
  const line = xy.map(([x, y]) => `${x},${y}`).join(" ");
  const last = xy[xy.length - 1];
  const dotLeft = (last[0] / W) * 100;
  const dotTop = (last[1] / H) * 100;

  const onMove = useCallback(
    (e: React.MouseEvent<HTMLDivElement>) => {
      if (!points.length || !trendRef.current) return;
      const rect = trendRef.current.getBoundingClientRect();
      const ratio = Math.min(1, Math.max(0, (e.clientX - rect.left) / rect.width));
      setHoverIdx(Math.round(ratio * (points.length - 1)));
    },
    [points]
  );

  const trendHover = useMemo(() => {
    const i = hoverIdx;
    if (i == null || !points[i]) return null;
    const n = points.length;
    const pct = Math.round(points[i].riskScore * 100);
    const firstPct = Math.round(points[0].riskScore * 100);
    const x = n === 1 ? W / 2 : PAD + (i / (n - 1)) * (W - PAD * 2);
    const y = H - PAD - points[i].riskScore * (H - PAD * 2);
    return {
      pct,
      fromStart: pct - firstPct,
      leftPct: (x / W) * 100,
      topPct: (y / H) * 100,
    };
  }, [hoverIdx, points]);

  const trendColor = trendHover
    ? trendHover.fromStart > 0
      ? "var(--coral)"
      : trendHover.fromStart < 0
      ? "var(--success)"
      : "var(--text-muted)"
    : undefined;

  return (
    <>
      <div className="mb-1 flex items-center justify-between text-xs">
        <span className="text-[var(--text-muted)]">{trendLabel}</span>
        {trendHover && (
          <span className="font-semibold tabular-nums" style={{ color: trendColor }}>
            {trendHover.pct}% · {vsStartLabel} {trendHover.fromStart > 0 ? "+" : ""}
            {trendHover.fromStart}%p
          </span>
        )}
      </div>
      <div
        ref={trendRef}
        className="relative h-12 w-full cursor-crosshair"
        onMouseMove={onMove}
        onMouseLeave={() => setHoverIdx(null)}
      >
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="h-full w-full">
          <polygon points={`${PAD},${H - PAD} ${line} ${last[0]},${H - PAD}`} fill="var(--blue)" fillOpacity={0.1} />
          <polyline points={line} fill="none" stroke="var(--blue)" strokeWidth={1.5} vectorEffect="non-scaling-stroke" />
          {trendHover && (
            <line
              x1={(trendHover.leftPct / 100) * W}
              y1={0}
              x2={(trendHover.leftPct / 100) * W}
              y2={H}
              stroke="var(--text-subtle)"
              strokeWidth={1}
              strokeDasharray="2 2"
              vectorEffect="non-scaling-stroke"
            />
          )}
        </svg>
        {!trendHover && (
          <span
            className="absolute h-1.5 w-1.5 -translate-x-1/2 -translate-y-1/2 rounded-full bg-[var(--blue)]"
            style={{ left: `${dotLeft}%`, top: `${dotTop}%` }}
          />
        )}
        {trendHover && (
          <>
            <span
              className="absolute h-2 w-2 -translate-x-1/2 -translate-y-1/2 rounded-full bg-[var(--blue)] ring-2 ring-white"
              style={{ left: `${trendHover.leftPct}%`, top: `${trendHover.topPct}%` }}
            />
            <div
              className="absolute z-10 flex flex-col gap-px whitespace-nowrap rounded-[var(--radius-compact)] border border-[var(--line)] bg-[var(--surface)] px-2.5 py-1 text-[11px] shadow-[0_8px_22px_rgba(28,39,60,0.16)]"
              style={{
                left: `${trendHover.leftPct}%`,
                top: 0,
                transform: "translate(-50%, calc(-100% - 6px))",
                pointerEvents: "none",
              }}
            >
              <b className="text-[13px] font-extrabold text-[var(--ink)]">{trendHover.pct}%</b>
              <span style={{ color: trendColor }}>
                {vsStartLabel} {trendHover.fromStart > 0 ? "+" : ""}
                {trendHover.fromStart}%p
              </span>
            </div>
          </>
        )}
      </div>
    </>
  );
}

interface Props {
  alertId: number;
  /** 문자 발송 시각 — 위험도 추이 조회 기간(발생 시점 → 현재) 산정용. */
  alertCreatedAt?: string;
}

/**
 * 재난문자 상세 — 위험도 분석 섹션.
 * 해당 문자가 속한 이벤트의 지역별 영향도를 히트맵 + 수치 통계로 보여준다.
 * 클러스터링 전(204)이면 안내 문구만 표시.
 */
export default function AlertRiskSection({ alertId, alertCreatedAt }: Props) {
  const t = useI18n();
  const language = useLanguageStore((s) => s.language);
  const { data, isLoading, isError, refetch } = useAlertRisk(alertId);
  const regionNames = useRegionNames(language);

  /* ── 수치 통계 (시군구 단위, 지도 폴리곤과 동일 집계) ──
     주: 한 이벤트의 impactScore 는 모든 영향 지역이 동일(baseScore 가 이벤트 단위) →
     지역별 순위/차등은 의미 없음. 따라서 단일 발생 당시 위험도 + 영향 지역 "목록"만 노출. */
  const stats = useMemo(() => {
    if (!data || data.regionImpacts.length === 0) return null;
    const scores = aggregateBySigungu(data.regionImpacts);
    const entries = [...scores.entries()];
    const maxScore = Math.max(...entries.map(([, s]) => s));
    return {
      regionCount: entries.length,
      issuedPct: Math.round(normalizeScore(maxScore) * 100),
      issuedGrade: scoreToGrade(maxScore),
      codes: entries.map(([code]) => code).sort(),
    };
  }, [data]);

  /* ── 대표 지역(영향 지역 중 첫 번째)의 현재(실시간) 위험도 + 발생 이후 추이 ── */
  const topCode = stats?.codes[0];
  // 추이 기간: 발생 시점 → 현재. 단 백엔드 스냅샷 retention(90일)이 상한, 최소 7일.
  const trendDays = useMemo(() => {
    if (!alertCreatedAt) return 7;
    const elapsed = Math.ceil((Date.now() - new Date(alertCreatedAt).getTime()) / 86_400_000);
    return Math.min(Math.max(elapsed + 1, 7), 90);
  }, [alertCreatedAt]);
  const { data: regionRisk } = useRegionRisk(topCode);
  const { data: riskHistory } = useRegionRiskHistory(topCode, trendDays);
  const currentPct = regionRisk ? Math.round(regionRisk.riskScore * 100) : null;
  // 지역 위험도는 이미 0~1 정규화 → 등급 컷만 적용 (NORMALIZE_DIVISOR 재적용 방지)
  const currentGrade: ImpactGrade | null = currentPct === null ? null
    : currentPct < 25 ? 0 : currentPct < 50 ? 1 : currentPct < 75 ? 2 : 3;

  return (
    <section className="space-y-3 rounded-[var(--radius-panel-card)] border border-[var(--line)] bg-[var(--surface)] p-4 shadow-[0_10px_30px_rgba(28,39,60,0.04)]">
      <h2 className="text-lg font-semibold text-[var(--ink)]">{t.risk.title}</h2>

      {/* 로딩 */}
      {isLoading && (
        <div className="space-y-3 animate-pulse">
          <div className="h-4 w-2/3 bg-gray-200 rounded" />
          <div className="h-[472px] bg-gray-100 rounded-lg" />
        </div>
      )}

      {/* 에러 */}
      {isError && (
        <div className="flex items-center justify-between gap-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
          <span className="text-sm text-red-700">{t.risk.loadError}</span>
          <button
            className="px-3 py-1 text-sm rounded border border-red-300 text-red-700 hover:bg-red-100"
            onClick={() => refetch()}
          >{t.risk.retry}</button>
        </div>
      )}

      {/* 분석 전 (204) */}
      {!isLoading && !isError && data === null && (
        <div className="rounded-lg border border-gray-200 bg-gray-50 px-4 py-6 text-center">
          <p className="text-sm text-gray-600">{t.risk.pending}</p>
          <p className="text-xs text-gray-400 mt-1">{t.risk.pendingSub}</p>
        </div>
      )}

      {/* 분석 결과 — 지도 상단 + 수치 통계 하단 (/alerts 사이드바와 동일 스타일) */}
      {!isLoading && !isError && data && (
        <div className="space-y-4">
          {data.regionImpacts.length > 0 ? (
            // 472px = /alerts 지도 캔버스 실제 높이 (카드 520px - 패딩 24px - 헤더 16px - 간격 8px)
            <AlertRiskMap impacts={data.regionImpacts} mapHeight="472px" />
          ) : (
            <div className="h-40 flex items-center justify-center rounded-lg border border-gray-200 bg-gray-50 text-sm text-gray-500">
              {t.risk.noRegions}
            </div>
          )}

          {stats && (
            <div className="space-y-3">
              {/* 요약 스탯 타일 */}
              <div className="grid grid-cols-2 gap-2">
                <div className="rounded-[var(--radius-card)] border border-[var(--line)] bg-[var(--canvas)] px-3 py-2">
                  <div className="text-[11px] text-[var(--text-muted)]">{t.risk.disasterType}</div>
                  <div className="mt-0.5 truncate text-sm font-semibold text-[var(--ink)]">
                    {t.disasterTypes[data.disasterType as keyof typeof t.disasterTypes] ?? data.disasterType ?? "-"}
                  </div>
                </div>
                <div className="rounded-[var(--radius-card)] border border-[var(--line)] bg-[var(--canvas)] px-3 py-2">
                  <div className="text-[11px] text-[var(--text-muted)]">{t.risk.maxRisk}</div>
                  <div className="mt-0.5 text-sm font-semibold" style={{ color: GRADE_HEX[stats.issuedGrade] }}>
                    {stats.issuedPct}% · {t.risk.map.grade[stats.issuedGrade]}
                  </div>
                </div>
              </div>

              {/* 최고 위험 지역의 현재(감쇠·확산 반영) 위험도 게이지 + 추이 */}
              {currentPct !== null && currentGrade !== null && topCode && (
                <div className="border-t border-[var(--line)] pt-3">
                  <div className="mb-2 flex items-baseline justify-between gap-2">
                    <h3 className="truncate text-sm font-semibold text-[var(--text-body)]">
                      {t.risk.currentRisk} · {regionNames?.get(topCode) ?? topCode}
                    </h3>
                    <span
                      className="shrink-0 rounded-[var(--radius-pill)] px-2 py-0.5 text-[11px] font-semibold"
                      style={{ backgroundColor: GRADE_SOFT[currentGrade], color: GRADE_HEX[currentGrade] }}
                    >
                      {t.risk.map.grade[currentGrade]}
                    </span>
                  </div>
                  <div className="flex items-end gap-0.5">
                    <span className="text-3xl font-bold leading-none" style={{ color: GRADE_HEX[currentGrade] }}>
                      {currentPct}
                    </span>
                    <span className="mb-0.5 text-base font-semibold" style={{ color: GRADE_HEX[currentGrade] }}>
                      %
                    </span>
                  </div>
                  <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-[#eef1f5]">
                    <div
                      className="h-full rounded-full transition-[width] duration-300"
                      style={{ width: `${currentPct}%`, backgroundColor: GRADE_HEX[currentGrade] }}
                    />
                  </div>
                  {regionRisk?.updatedAt && (
                    <div className="mt-1 text-[10px] text-[var(--text-subtle)]">
                      {t.risk.updated} {new Date(regionRisk.updatedAt).toLocaleString()}
                    </div>
                  )}
                  <div className="mt-2">
                    {riskHistory && riskHistory.length > 0 ? (
                      <TrendSparkline points={riskHistory} trendLabel={t.risk.trend} vsStartLabel={t.risk.vsStart} />
                    ) : (
                      <p className="py-2 text-center text-xs text-[var(--text-subtle)]">{t.risk.noData}</p>
                    )}
                  </div>
                </div>
              )}

              {/* 영향 지역 목록 (동일 등급이라 순위 없이 나열) */}
              <div className="pt-2 border-t border-[var(--line)]">
                <h3 className="text-sm font-semibold text-[var(--text-body)] mb-2">
                  {t.risk.affectedRegions} · {stats.regionCount}
                </h3>
                <div className="flex flex-wrap gap-1.5">
                  {stats.codes.map((code) => (
                    <span
                      key={code}
                      className={`text-xs px-2 py-0.5 rounded-full border ${GRADE_BOX[stats.issuedGrade]}`}
                    >
                      {regionNames?.get(code) ?? code}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
