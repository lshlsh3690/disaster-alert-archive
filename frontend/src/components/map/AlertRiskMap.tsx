"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { KOREA_SIDO } from "./koreaSido.data";
import { useSigunguRegions, type MapRegion } from "@/lib/queries/useMapRegions";
import { BASE_VIEWBOX, fitViewBox, unionBBox, type BBox } from "@/lib/mapViewBox";
import { useAnimatedViewBox } from "./useAnimatedViewBox";
import { normalizeScore, scoreToGrade, aggregateBySigungu, type ImpactGrade } from "@/lib/riskScore";
import type { RegionImpact } from "@/types/risk";
import type { I18nKey } from "@/constants/i18n";

// 등급 계산은 @/lib/riskScore, 등급 표시명은 i18n(t("risk.map.grade"))에서 가져옴
const GRADE_TEXT = ["#15803d", "#a16207", "#c2410c", "#b91c1c"] as const;

const GRADE_POLY = [
  { fill: "#86efac", fillOpacity: 0.55, stroke: "#16a34a", strokeWidth: 1 },
  { fill: "#fde047", fillOpacity: 0.6, stroke: "#ca8a04", strokeWidth: 1 },
  { fill: "#fb923c", fillOpacity: 0.7, stroke: "#ea580c", strokeWidth: 1 },
  { fill: "#f87171", fillOpacity: 0.8, stroke: "#dc2626", strokeWidth: 1.5 },
] as const;

const GRADE_POLY_HOVER = ["#4ade80", "#facc15", "#f97316", "#ef4444"] as const;

// 지역 이름을 그릴 최소 화면 너비(px)
const LABEL_MIN_PX = 34;

const skRegions = KOREA_SIDO.filter((r) => r.kind === "sk");

interface Impacted {
  key: string; // 영향 코드(시군구 5자리 또는 시도 전체 xx000)
  name: string;
  d: string;
  c: [number, number];
  score: number;
  grade: ImpactGrade;
  bbox: BBox | null;
}

interface Props {
  impacts: RegionImpact[];
  mapHeight?: string;
}

/**
 * 재난문자 상세 페이지용 영향 지역 히트맵.
 * 영향받은 시군구를 영향 등급별 색상으로 칠하고, 그 외 지역은 회색 배경으로 표시한다. 영향 지역 전체가 보이도록 확대한다.
 */
export default function AlertRiskMap({ impacts, mapHeight = "420px" }: Props) {
  const { t } = useTranslation();
  const sigunguQuery = useSigunguRegions(true);
  const [hover, setHover] = useState<Impacted | null>(null);
  const [mousePos, setMousePos] = useState<{ x: number; y: number } | null>(null);
  const [width, setWidth] = useState(0);
  const boxRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = boxRef.current;
    if (!el || typeof ResizeObserver === "undefined") return;
    const ro = new ResizeObserver(() => setWidth(el.clientWidth));
    ro.observe(el);
    setWidth(el.clientWidth);
    return () => ro.disconnect();
  }, []);

  const { items, unmatched } = useMemo(() => {
    const all: MapRegion[] = sigunguQuery.data ?? [];
    const out: Impacted[] = [];
    const missing: string[] = [];
    aggregateBySigungu(impacts).forEach((score, sigCd) => {
      const grade = scoreToGrade(score);
      // 1) 시군구 코드 그대로 → 2) 구가 있는 시 전체 코드(41110 → 41111·41113…) → 3) 시도 전체 코드(xx000)
      let matched = all.filter((r) => r.c === sigCd);
      if (matched.length === 0 && !sigCd.endsWith("000") && sigCd.endsWith("0")) {
        matched = all.filter((r) => r.c.startsWith(sigCd.slice(0, 4)));
      }
      if (matched.length > 0) {
        for (const r of matched) {
          out.push({ key: `${sigCd}:${r.c}`, name: r.n, d: r.d, c: r.p, score, grade, bbox: r.b ?? null });
        }
        return;
      }
      if (sigCd.endsWith("000")) {
        const sido = skRegions.find((r) => r.code === sigCd.slice(0, 2));
        if (sido) {
          const boxes = all.filter((r) => r.c.startsWith(sido.code) && r.b).map((r) => r.b as BBox);
          out.push({ key: sigCd, name: sido.ko, d: sido.d, c: sido.c, score, grade, bbox: unionBBox(boxes) });
          return;
        }
      }
      missing.push(sigCd);
    });
    return { items: out, unmatched: missing };
  }, [impacts, sigunguQuery.data]);

  const target = useMemo(() => {
    const box = unionBBox(items.map((i) => i.bbox).filter((b): b is BBox => b !== null));
    return box ? fitViewBox(box, BASE_VIEWBOX, 0.35, 0.12) : BASE_VIEWBOX;
  }, [items]);
  const viewBox = useAnimatedViewBox(target);
  // 글자 크기는 화면에서 일정하게 보이도록 viewBox 단위로 환산한다
  const unit = width > 0 ? viewBox.w / width : 1;

  if (sigunguQuery.isError) {
    return (
      <div className="h-40 flex items-center justify-center rounded-lg border border-gray-200 bg-gray-50 text-sm text-gray-500">
        {t("risk.map.error")}
      </div>
    );
  }

  const ready = !sigunguQuery.isPending;
  const gradeNames = t("risk.map.grade", { returnObjects: true }) as I18nKey["ko"]["risk"]["map"]["grade"];

  return (
    <div
      ref={boxRef}
      className="relative rounded-lg border border-gray-200 overflow-hidden bg-[#f5f7fa]"
      style={{ height: mapHeight }}
      onMouseMove={(e) => {
        const rect = e.currentTarget.getBoundingClientRect();
        setMousePos({ x: e.clientX - rect.left, y: e.clientY - rect.top });
      }}
      onMouseLeave={() => {
        setHover(null);
        setMousePos(null);
      }}
    >
      <svg
        role="img"
        aria-label={t("risk.map.legend")}
        viewBox={`${viewBox.x} ${viewBox.y} ${viewBox.w} ${viewBox.h}`}
        preserveAspectRatio="xMidYMid meet"
        className="absolute inset-0 h-full w-full"
      >
        {/* 영향권 밖 시도 배경(컨텍스트, 인터랙션 없음) */}
        {skRegions.map((r) => (
          <path key={r.code} d={r.d} fill="#f3f4f6" stroke="#cbd2db" strokeWidth={1} vectorEffect="non-scaling-stroke" />
        ))}
        {ready &&
          items.map((i) => {
            const style = GRADE_POLY[i.grade];
            const hovered = hover?.key === i.key;
            return (
              <path
                key={i.key}
                d={i.d}
                fillRule="evenodd"
                fill={hovered ? GRADE_POLY_HOVER[i.grade] : style.fill}
                fillOpacity={hovered ? Math.min(1, style.fillOpacity + 0.15) : style.fillOpacity}
                stroke={style.stroke}
                strokeWidth={style.strokeWidth}
                vectorEffect="non-scaling-stroke"
                onMouseEnter={() => setHover(i)}
                onMouseLeave={() => setHover(null)}
              />
            );
          })}
        {ready &&
          items
            // 화면에서 너무 작은 지역은 이름이 서로 겹쳐 지도를 가리므로 이름을 생략한다(호버하면 툴팁으로 나온다)
            .filter((i) => i.bbox !== null && (i.bbox[2] - i.bbox[0]) / unit >= LABEL_MIN_PX)
            .map((i) => (
            <text
              key={`l${i.key}`}
              x={i.c[0]}
              y={i.c[1]}
              textAnchor="middle"
              dominantBaseline="central"
              fontSize={11 * unit}
              fontWeight={700}
              fill="#1f2937"
              stroke="#fff"
              strokeWidth={3 * unit}
              paintOrder="stroke"
              pointerEvents="none"
            >
              {i.name.split(" ").pop()}
            </text>
          ))}
      </svg>

      {!ready && (
        <div className="absolute inset-0 flex items-center justify-center bg-gray-50 text-sm text-gray-500">
          {t("risk.map.loading")}
        </div>
      )}

      {/* 등급 범례 */}
      {ready && (
        <div className="absolute bottom-3 left-3 z-10 bg-white/90 rounded-lg shadow px-3 py-2">
          <p className="text-[11px] font-semibold text-gray-500 mb-1">{t("risk.map.legend")}</p>
          <div className="flex items-center gap-2">
            {gradeNames.map((label, lv) => (
              <div key={label} className="flex items-center gap-1">
                <span
                  className="inline-block w-3 h-3 rounded-sm border"
                  style={{ background: GRADE_POLY[lv].fill, borderColor: GRADE_POLY[lv].stroke }}
                />
                <span className="text-[11px] text-gray-600">{label}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 지오메트리 매칭 실패 안내 (드물지만 코드 개편 등으로 발생 가능) */}
      {unmatched.length > 0 && (
        <div className="absolute top-3 left-3 z-10 bg-amber-50/95 border border-amber-200 rounded-lg px-3 py-1.5 text-[11px] text-amber-700">
          {t("risk.map.unmatched")}
        </div>
      )}

      {/* 호버 툴팁 */}
      {hover && mousePos && (
        <div
          className="absolute pointer-events-none z-20 bg-white border border-gray-200 rounded-lg px-3 py-2 shadow"
          style={{ left: mousePos.x + 14, top: mousePos.y - 10, transform: "translateY(-100%)", whiteSpace: "nowrap" }}
        >
          <div className="text-[13px] font-bold text-gray-900">{hover.name}</div>
          <div className="flex items-center justify-between gap-3 mt-0.5">
            <span className="text-[11px] text-gray-500">{t("risk.map.legend")}</span>
            <span className="text-xs font-bold" style={{ color: GRADE_TEXT[hover.grade] }}>
              {t(`risk.map.grade.${hover.grade}`)}
            </span>
          </div>
          <div className="flex items-center justify-between gap-3">
            <span className="text-[11px] text-gray-500">{t("risk.map.score")}</span>
            <span className="text-[11px] font-semibold text-gray-700">
              {Math.round(normalizeScore(hover.score) * 100)}%
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
