"use client";

import dynamic from "next/dynamic";
import { useEffect, useMemo, useState } from "react";
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
const GRADE_TEXT = ["text-green-700", "text-yellow-700", "text-orange-700", "text-red-700"] as const;

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

/** 0~1 위험도 시계열 → 인라인 SVG 스파크라인 (recharts 청크 없이 가볍게). */
function TrendSparkline({ points }: { points: RiskHistoryPoint[] }) {
  const W = 100, H = 32, PAD = 2;
  const xy = points.map((p, i) => {
    const x = points.length === 1 ? W / 2 : PAD + (i / (points.length - 1)) * (W - PAD * 2);
    const y = H - PAD - p.riskScore * (H - PAD * 2);
    return [x, y] as const;
  });
  const line = xy.map(([x, y]) => `${x},${y}`).join(" ");
  const last = xy[xy.length - 1];
  // 끝점 점은 HTML 요소로 분리 — SVG 의 non-uniform stretch(preserveAspectRatio="none")에
  // 끌려가면 원이 타원으로 찌그러지므로, % 좌표로 올려 진짜 원을 유지한다.
  const dotLeft = (last[0] / W) * 100;
  const dotTop = (last[1] / H) * 100;
  return (
    <div className="relative w-full h-10">
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="w-full h-full">
        <polygon points={`${PAD},${H - PAD} ${line} ${last[0]},${H - PAD}`} fill="#3b82f6" fillOpacity={0.1} />
        <polyline points={line} fill="none" stroke="#3b82f6" strokeWidth={1.5} vectorEffect="non-scaling-stroke" />
      </svg>
      <span
        className="absolute h-1.5 w-1.5 -translate-x-1/2 -translate-y-1/2 rounded-full bg-blue-600"
        style={{ left: `${dotLeft}%`, top: `${dotTop}%` }}
      />
    </div>
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
    <section className="bg-white rounded-xl shadow p-4 space-y-3">
      <h2 className="text-lg font-semibold">{t.risk.title}</h2>

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
              {/* 요약 수치 */}
              <div className="space-y-2">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-gray-500">{t.risk.disasterType}</span>
                  <span className="font-semibold">{data.disasterType ?? "-"}</span>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-gray-500">{t.risk.maxRisk}</span>
                  <span className={`font-semibold ${GRADE_TEXT[stats.issuedGrade]}`}>
                    {stats.issuedPct}% · {t.risk.map.grade[stats.issuedGrade]}
                  </span>
                </div>
              </div>

              {/* 최고 위험 지역의 현재(감쇠·확산 반영) 위험도 + 7일 추이 */}
              {currentPct !== null && currentGrade !== null && topCode && (
                <div className="pt-2 border-t">
                  <h3 className="text-sm font-semibold text-gray-700 mb-2">
                    {t.risk.currentRisk} · {regionNames?.get(topCode) ?? topCode}
                  </h3>
                  <div className={`flex items-center justify-between rounded border px-3 py-2 ${GRADE_BOX[currentGrade]}`}>
                    <span className="text-xl font-bold">{currentPct}%</span>
                    <div className="text-right">
                      <div className="text-xs font-semibold">{t.risk.map.grade[currentGrade]}</div>
                      {regionRisk?.updatedAt && (
                        <div className="text-[10px] opacity-70">
                          {t.risk.updated} {new Date(regionRisk.updatedAt).toLocaleString()}
                        </div>
                      )}
                    </div>
                  </div>
                  <div className="mt-2">
                    <div className="flex justify-between text-xs mb-0.5">
                      <span className="text-gray-500">{t.risk.trend}</span>
                    </div>
                    {riskHistory && riskHistory.length > 0 ? (
                      <TrendSparkline points={riskHistory} />
                    ) : (
                      <p className="text-xs text-gray-400 text-center py-2">{t.risk.noData}</p>
                    )}
                  </div>
                </div>
              )}

              {/* 영향 지역 목록 (동일 등급이라 순위 없이 나열) */}
              <div className="pt-2 border-t">
                <h3 className="text-sm font-semibold text-gray-700 mb-2">
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
