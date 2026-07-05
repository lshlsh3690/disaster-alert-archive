"use client";

import { useMemo, useRef, useState, useEffect, useCallback, useId } from "react";
import { useRouter } from "next/navigation";
import { KOREA_SIDO, SidoRegion } from "./koreaSido.data";
import { useI18n } from "@/hooks/useI18n";
import { useSidoStats } from "@/lib/queries/useAlerts";
import { groupToMetros, Metro } from "@/ui/metros";
import styles from "./KoreaMap25D.module.css";

const skRegions = KOREA_SIDO.filter((r) => r.kind === "sk");

// koreaSido.data.ts의 원본 viewBox("0 0 760 714")는 경상북도 폴리곤에 딸린 울릉도/독도 조각
// (x≈630~642)까지 감싸느라 오른쪽에 불필요하게 넓은 여백을 두고 있어, 육지 전체가 왼쪽으로
// 쏠려 보인다. 좌표 데이터(자동 생성, 수정 금지)는 그대로 두고 뷰포트만 육지 기준으로 좁혀서
// 중앙 정렬한다 (울릉도/독도는 그대로 우측 가장자리에 표시됨).
const MAP_VIEWBOX = "0 0 682 714";

const COLOR_LOW = "#fdeae6"; // 1건
const COLOR_HIGH = "#ef9d92"; // 최다
const COLOR_ZERO = "#f7f9fb"; // 0건

function hexLerp(a: string, b: string, ratio: number) {
  const pa = [parseInt(a.slice(1, 3), 16), parseInt(a.slice(3, 5), 16), parseInt(a.slice(5, 7), 16)];
  const pb = [parseInt(b.slice(1, 3), 16), parseInt(b.slice(3, 5), 16), parseInt(b.slice(5, 7), 16)];
  const c = pa.map((v, i) => Math.round(v + (pb[i] - v) * ratio));
  return "#" + c.map((v) => v.toString(16).padStart(2, "0")).join("");
}
const darken = (hex: string, f: number) => hexLerp(hex, "#000000", f);
const colorForCount = (n: number, max: number) => {
  if (!n) return COLOR_ZERO;
  const ratio = max > 1 ? (n - 1) / (max - 1) : 1;
  return hexLerp(COLOR_LOW, COLOR_HIGH, Math.pow(ratio, 0.7));
};

function todayRange() {
  const d = new Date();
  const s = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  return { startDate: s, endDate: s };
}

interface KoreaMap25DProps {
  height?: string;
  depth?: number; // 지도 두께(viewBox 단위)
  raise?: number; // 호버 시 솟아오르는 높이
  todayOnly?: boolean;
  onSelect?: (ko: string) => void;
}

export default function KoreaMap25D({
  height = "560px",
  depth = 10,
  raise = 16,
  todayOnly = true,
  onSelect,
}: KoreaMap25DProps) {
  const t = useI18n();
  const router = useRouter();

  // useId(): 서버/클라이언트 렌더 간 동일한 값을 보장 (Math.random은 하이드레이션 불일치 발생)
  const uid = useId().replace(/[^a-zA-Z0-9]/g, "");
  const landId = `land-${uid}`;
  const shadowFilterId = `soft-${uid}`;
  const liftShadowId = `lift-${uid}`;

  const today = useMemo(() => todayRange(), []);
  const dateParams = todayOnly ? today : {};
  const { data: sidoData } = useSidoStats(dateParams);
  const counts = useMemo(() => groupToMetros(sidoData ?? []), [sidoData]);
  const countFor = useCallback((ko: string) => counts[ko as Metro] ?? 0, [counts]);
  const maxCount = useMemo(() => Math.max(1, ...Object.values(counts)), [counts]);
  const fillFor = useCallback((ko: string) => colorForCount(countFor(ko), maxCount), [countFor, maxCount]);

  const wallLayers = useMemo(() => {
    const arr: { y: number; fill: string }[] = [];
    for (let i = depth; i >= 1; i--) {
      arr.push({ y: i, fill: hexLerp("#dde2e9", "#bcc3cf", i / depth) });
    }
    return arr;
  }, [depth]);

  const legendBins = useMemo(() => {
    const max = maxCount;
    const n = Math.min(5, max);
    const bins: { label: string; color: string }[] = [];
    for (let k = 0; k < n; k++) {
      const lo = Math.floor((k * max) / n) + 1;
      const hi = Math.floor(((k + 1) * max) / n);
      bins.push({
        label: lo === hi ? `${lo}` : `${lo}~${hi}`,
        color: colorForCount(hi, max),
      });
    }
    return bins;
  }, [maxCount]);

  const containerRef = useRef<HTMLDivElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const rafIdRef = useRef(0);
  const liftRef = useRef(0);

  const [hovered, setHovered] = useState<string | null>(null);
  const [lift, setLift] = useState(0);
  const [anchor, setAnchor] = useState({ x: 0, y: 0 });
  const [lineEnd, setLineEnd] = useState({ x: 0, y: 0 });
  const [side, setSide] = useState<"left" | "right">("right");

  const hoveredRegion = useMemo(
    () => skRegions.find((r) => r.ko === hovered) || null,
    [hovered]
  );
  const wallCount = Math.floor(lift);
  const liftBaseFill = hoveredRegion ? fillFor(hoveredRegion.ko) : COLOR_LOW;
  const wallFill = useCallback(
    (i: number) => darken(liftBaseFill, 0.1 + 0.26 * (1 - i / raise)),
    [liftBaseFill, raise]
  );

  const animateLift = useCallback(
    (target: number) => {
      cancelAnimationFrame(rafIdRef.current);
      const from = liftRef.current;
      const dur = 280;
      const t0 = performance.now();
      const step = (now: number) => {
        const p = Math.min(1, (now - t0) / dur);
        const e = 1 - Math.pow(1 - p, 3); // easeOutCubic
        const value = from + (target - from) * e;
        liftRef.current = value;
        setLift(value);
        if (p < 1) rafIdRef.current = requestAnimationFrame(step);
      };
      rafIdRef.current = requestAnimationFrame(step);
    },
    []
  );

  const onEnter = useCallback(
    (r: SidoRegion) => {
      const fresh = hovered !== r.ko;
      setHovered(r.ko);
      if (fresh) {
        liftRef.current = 0;
        setLift(0);
      }
      animateLift(raise);

      const svg = svgRef.current;
      const cont = containerRef.current;
      if (!svg || !cont || !svg.getScreenCTM) return;
      const ctm = svg.getScreenCTM();
      if (!ctm) return;
      const pt = svg.createSVGPoint();
      pt.x = r.c[0];
      pt.y = r.c[1] - raise;
      const sp = pt.matrixTransform(ctm);
      const rect = cont.getBoundingClientRect();
      const ax = sp.x - rect.left;
      const ay = sp.y - rect.top;
      setAnchor({ x: ax, y: ay });
      const goRight = ax < rect.width / 2;
      setSide(goRight ? "right" : "left");
      const len = 64;
      setLineEnd({ x: goRight ? ax + len : ax - len, y: ay - 6 });
    },
    [hovered, raise, animateLift]
  );

  const onLeave = useCallback(() => {
    cancelAnimationFrame(rafIdRef.current);
    setHovered(null);
    liftRef.current = 0;
    setLift(0);
  }, []);

  const onClick = useCallback(
    (r: SidoRegion) => {
      onSelect?.(r.ko);
      const q = new URLSearchParams({ sido: r.ko });
      if (todayOnly) {
        q.set("startDate", today.startDate);
        q.set("endDate", today.endDate);
      }
      router.push(`/alerts?${q.toString()}#list`);
    },
    [onSelect, todayOnly, today, router]
  );

  useEffect(() => {
    return () => cancelAnimationFrame(rafIdRef.current);
  }, []);

  const localName = (ko: string) => t.metros?.[ko as keyof typeof t.metros] ?? ko;
  const mapLabel = t.dashboard.todayAlerts;
  const mapUnit = t.dashboard.count;
  const boxStyle = { left: `${lineEnd.x}px`, top: `${lineEnd.y}px` };

  return (
    <div className={`${styles.koreaMap} korea-map`}>
      <div ref={containerRef} className={`${styles.canvas} korea-map__canvas`} style={{ height }}>
        <svg
          ref={svgRef}
          viewBox={MAP_VIEWBOX}
          className={`${styles.svg} korea-map__svg`}
          role="img"
          aria-label="지역별 오늘 재난문자 지도"
          onMouseLeave={onLeave}
        >
          <defs>
            <g id={landId}>
              {skRegions.map((r) => (
                <path key={r.code} d={r.d} />
              ))}
            </g>
            <filter id={shadowFilterId} x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="9" />
            </filter>
            <filter id={liftShadowId} x="-40%" y="-40%" width="180%" height="180%">
              <feDropShadow dx="0" dy="6" stdDeviation="5" floodColor="#1f2a5c" floodOpacity="0.25" />
            </filter>
          </defs>

          {/* 바닥 그림자 */}
          <use
            href={`#${landId}`}
            transform={`translate(8 ${depth + 14})`}
            fill="#9aa3b2"
            filter={`url(#${shadowFilterId})`}
            opacity="0.26"
          />

          {/* 지도 두께(회색 벽) */}
          {wallLayers.map((w) => (
            <use key={`w${w.y}`} href={`#${landId}`} transform={`translate(0 ${w.y})`} fill={w.fill} />
          ))}

          {/* 지역 윗면 (건수 기반 색농도) */}
          {skRegions.map((r) => (
            <path key={`sk${r.code}`} d={r.d} className={styles.region} fill={fillFor(r.ko)} />
          ))}

          {/* 호버 지역: 입체 블록 (바닥→윗면 벽 겹쌓기 + 윗면 + 그림자) */}
          {hoveredRegion && (
            <>
              <path d={hoveredRegion.d} fill={wallFill(0)} className={styles.wall} />
              {Array.from({ length: wallCount }, (_, idx) => idx + 1).map((i) => (
                <path
                  key={`wl${i}`}
                  d={hoveredRegion.d}
                  transform={`translate(0 ${-i})`}
                  fill={wallFill(i)}
                  className={styles.wall}
                />
              ))}
              <path
                d={hoveredRegion.d}
                transform={`translate(0 ${-lift})`}
                fill={fillFor(hoveredRegion.ko)}
                className={`${styles.region} ${styles.liftTop}`}
                filter={`url(#${liftShadowId})`}
              />
            </>
          )}

          {/* 투명 hit-layer: 위치 고정이라 호버 감지가 안정적 */}
          {skRegions.map((r) => (
            <path
              key={`hit${r.code}`}
              d={r.d}
              className={styles.hit}
              onMouseEnter={() => onEnter(r)}
              onClick={() => onClick(r)}
            />
          ))}
        </svg>

        {/* 연결선 */}
        {hoveredRegion && (
          <svg key={`line-${hovered}`} className={styles.lead} aria-hidden="true">
            <line x1={anchor.x} y1={anchor.y} x2={lineEnd.x} y2={lineEnd.y} className={styles.leadLine} pathLength={1} />
            <circle cx={anchor.x} cy={anchor.y} r="3.5" className={styles.leadDot} />
          </svg>
        )}

        {/* 말풍선 */}
        {hoveredRegion && (
          <div
            key={`tip-${hovered}`}
            className={`${styles.tip} ${side === "right" ? styles.tipRight : styles.tipLeft}`}
            style={boxStyle}
          >
            <div className={styles.tipRegion}>
              <span className={styles.tipSwatch} style={{ background: fillFor(hoveredRegion.ko) }} />
              {localName(hoveredRegion.ko)}
            </div>
            <div className={styles.tipCount}>
              {mapLabel} <b>{countFor(hoveredRegion.ko)}</b>
              {mapUnit}
            </div>
          </div>
        )}
      </div>

      {/* 범례: 구간별 색상 */}
      <div className={`${styles.legend} legend`}>
        <span className={styles.legendItem}>
          <i className={styles.legendSq} style={{ background: COLOR_ZERO }} />0{mapUnit}
        </span>
        {legendBins.map((b) => (
          <span key={b.label} className={styles.legendItem}>
            <i className={styles.legendSq} style={{ background: b.color }} />
            {b.label}
            {mapUnit}
          </span>
        ))}
      </div>
    </div>
  );
}
