"use client";

import { memo, useMemo, useRef, useState, useEffect, useLayoutEffect, useCallback, useId } from "react";
import { useRouter } from "next/navigation";
import { KOREA_SIDO } from "./koreaSido.data";
import { useTranslation } from "react-i18next";
import { useSidoStats, useRegionCodeStats } from "@/lib/queries/useAlerts";
import type { AlertSearchRequest } from "@/api/alertApi";
import { useSigunguRegions, useEmdRegions } from "@/lib/queries/useMapRegions";
import { BASE_VIEWBOX, fitViewBox, unionBBox, type BBox } from "@/lib/mapViewBox";
import { useAnimatedViewBox } from "./useAnimatedViewBox";
import { groupToMetros, Metro } from "@/ui/metros";
import styles from "./KoreaMap25D.module.css";

const skRegions = KOREA_SIDO.filter((r) => r.kind === "sk");

// 지도 보기 단위. 시도 경계는 위 번들 데이터(koreaSido.data.ts)를, 시군구/읍면동 경계는 public/map/**.json 을 쓴다.
// 읍면동은 별도 보기 단위가 아니라 시군구 보기에서 구역을 눌렀을 때 그 시군구로 확대해서 보여 준다(drill-down).
export type MapMode = "sido" | "sigungu";
// 지금 보여주는 단계. 시도 보기는 시도 → (시도를 누르면) 그 시도의 시군구 → (시군구를 누르면) 읍면동, 시군구 보기는 시군구 → 읍면동.
type MapLevel = "sido" | "sigungu" | "emd";
const MAP_MODES: MapMode[] = ["sido", "sigungu"];
const DEFAULT_MODE: MapMode = "sigungu";

// 보기 단위와 무관하게 렌더링 가능한 하나의 지역 단위로 맞춘 모양
interface RenderRegion {
  key: string; // 시도 코드(2) / 시군구 코드(5) / 읍면동 코드(8)
  label: string; // 시도는 한글 시도명(i18n 키), 시군구/읍면동은 공식 법정동 명칭
  d: string;
  c: [number, number];
}

// 지도 전체를 보여주는 기본 viewBox 는 mapViewBox.ts 의 BASE_VIEWBOX 다(koreaSido.data.ts 원본 760x714 의 오른쪽 여백을 잘라 낸 값).

// 확대/호버 애니메이션은 매 프레임 컴포넌트를 다시 그리므로, 경로 문자열이 큰 층(지역 윗면)은 메모이즈해 props 가 그대로면 건너뛴다.
const RegionLayer = memo(function RegionLayer({
  regions,
  fillFor,
  className,
  fillRule,
}: {
  regions: RenderRegion[];
  fillFor: (key: string) => string;
  className: string;
  fillRule: "evenodd" | undefined;
}) {
  return (
    <>
      {regions.map((r) => (
        <path key={`r${r.key}`} d={r.d} className={className} fill={fillFor(r.key)} fillRule={fillRule} />
      ))}
    </>
  );
});

// KRDS primary 5단계 램프: 0건(흰색) → 1건 … cap 건 이상(primary-60). 1~cap 건은 뒤 네 단계 사이를 보간한다
const RAMP = ["#ffffff", "#d8e5fd", "#86aff9", "#256ef4", "#0b50d0"] as const;
const COLOR_ZERO = RAMP[0];
const COLOR_PENDING = "#f4f5f6"; // 건수를 아직 모르는 상태(통계 로딩 중) — 0건과 구분해 거짓 0건을 보여주지 않는다
const WALL_COLOR = "#8a949e"; // 지도 두께(입체 벽면)
/** 오늘 건수 기준 램프 상한. 이 건수 이상이면 가장 진한 색이다(대시보드 범례 "12건 이상") */
const TODAY_CAP = 12;
/** 채움이 이 건수 이상이면 라벨을 흰 글자 + 어두운 halo 로 바꾼다 */
const DARK_LABEL_MIN = 6;
// 면적이 작은 광역시(서울·부산·대구·대전·울산·세종). 좁은 화면에서는 이웃 도 라벨과 겹쳐 라벨을 숨기고 색·탭 툴팁으로만 보인다
const SMALL_METROS = new Set(["11", "26", "27", "30", "31", "36"]);
// 지역 라벨 위치 보정(시도 중심점이 라벨끼리 겹치는 곳만). 경기 라벨이 서울·강원 라벨과 겹치지 않게 북쪽으로 올린다
const LABEL_POS: Record<string, [number, number]> = {
  "41": [292, 98],
  "11": [256, 160],
  "28": [196, 178],
  "47": [450, 290],
  "48": [370, 440],
  "12": [250, 485],
  "44": [235, 290],
  "30": [298, 312],
  "36": [276, 272],
};

function hexLerp(a: string, b: string, ratio: number) {
  const pa = [parseInt(a.slice(1, 3), 16), parseInt(a.slice(3, 5), 16), parseInt(a.slice(5, 7), 16)];
  const pb = [parseInt(b.slice(1, 3), 16), parseInt(b.slice(3, 5), 16), parseInt(b.slice(5, 7), 16)];
  const c = pa.map((v, i) => Math.round(v + (pb[i] - v) * ratio));
  return "#" + c.map((v) => v.toString(16).padStart(2, "0")).join("");
}
const darken = (hex: string, f: number) => hexLerp(hex, "#000000", f);
const colorForCount = (n: number, cap: number) => {
  if (!n) return COLOR_ZERO;
  const t = (Math.min(1, cap > 1 ? (n - 1) / (cap - 1) : 1)) * 3;
  const i = Math.min(2, Math.floor(t));
  return hexLerp(RAMP[i + 1], RAMP[i + 2], t - i);
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
  /** 건수를 셀 검색 조건. 주면 todayOnly 대신 이 조건으로 집계한다(알림 목록 페이지의 필터와 맞출 때) */
  params?: AlertSearchRequest;
  /** 툴팁에 붙는 건수 설명. 기본은 "오늘 재난문자" */
  countLabel?: string;
  onSelect?: (ko: string) => void;
  /**
   * 주면 지역을 누를 때마다(확대하는 클릭 포함) 이 콜백을 부르고, /alerts 로 직접 이동하지 않는다.
   * 알림 목록 페이지처럼 이미 목록이 있는 곳에서 필터만 바꾸려는 용도. sigungu 는 "수원시 장안구 [읍면동]" 처럼 시도명을 뗀 값이다.
   */
  onRegionSelect?: (sel: { sido: string; sigungu?: string }) => void;
  /** 시도별/시군구별 보기 전환 버튼을 보여 줄지. 보기 단위를 고정하는 곳(알림 목록 페이지)에서는 false */
  showModeToggle?: boolean;
  /** 처음 보여 줄 보기 단위. 기본은 시군구별 보기 */
  defaultMode?: MapMode;
  /** 보기 단위를 바깥에서 쥘 때(대시보드 제목 블록의 시도|시군구 세그먼트). 주면 내부 상태 대신 이 값을 쓴다 */
  mode?: MapMode;
  onModeChange?: (mode: MapMode) => void;
  /** 시도 단계에서 지도 위에 지역 이름과 건수를 직접 쓴다(대시보드) */
  showRegionLabels?: boolean;
  /**
   * 바깥(예: 알림 목록의 검색 필터)에서 고른 지역으로 지도를 확대한다. 시도명("경기도")과 시군구("수원시 장안구", 읍면동이
   * 붙어 있어도 된다)를 주면 그 시군구의 읍면동까지, 시도만 주면 그 시도의 시군구까지 확대하고, 둘 다 비우면 전체 보기로 돌아간다.
   * 구가 있는 시 전체("수원시")처럼 지도에 단독 경계가 없는 값은 시도까지만 확대한다.
   */
  focusSido?: string;
  focusSigungu?: string;
  /** 목록 페이지에 붙일 때처럼 위쪽에 제목 카드가 없으면 컨트롤을 위로 올린다 */
  compact?: boolean;
  /** 현재 보기 상태(예: "시도별 보기 › 경기도 › 수원시 장안구 › 읍면동")가 바뀔 때마다 알린다. 제목 카드에 표시하는 용도 */
  onStatusChange?: (status: string) => void;
}

export default function KoreaMap25D({
  height = "560px",
  depth = 9,
  raise = 16,
  todayOnly = true,
  params,
  countLabel,
  onSelect,
  onRegionSelect,
  compact = false,
  showModeToggle = true,
  defaultMode = DEFAULT_MODE,
  mode: modeProp,
  onModeChange: onModeChangeProp,
  showRegionLabels = false,
  focusSido,
  focusSigungu,
  onStatusChange,
}: KoreaMap25DProps) {
  const { t } = useTranslation();
  const router = useRouter();

  // useId(): 서버/클라이언트 렌더 간 동일한 값을 보장 (Math.random은 하이드레이션 불일치 발생)
  const uid = useId().replace(/[^a-zA-Z0-9]/g, "");
  const landId = `land-${uid}`;
  const shadowFilterId = `soft-${uid}`;
  const liftShadowId = `lift-${uid}`;

  const [innerMode, setInnerMode] = useState<MapMode>(defaultMode);
  const mode = modeProp ?? innerMode;
  const controlled = modeProp !== undefined;
  const setMode = useCallback(
    (next: MapMode) => {
      if (!controlled) setInnerMode(next);
      onModeChangeProp?.(next);
    },
    [controlled, onModeChangeProp]
  );
  // 확대 단계: 시도 보기에서 누른 시도(코드 2자리) → 그 시도의 시군구 → 누른 시군구(코드 5자리) → 그 시군구의 읍면동.
  // 시군구 보기는 처음부터 시군구 단계라 drillSido 없이 drillSigungu 만 쓴다.
  const [drillSido, setDrillSido] = useState<string | null>(null);
  const [drillSigungu, setDrillSigungu] = useState<string | null>(null);
  const activeSido = mode === "sido" ? drillSido : null;
  const level: MapLevel =
    drillSigungu !== null ? "emd" : mode === "sigungu" || activeSido !== null ? "sigungu" : "sido";
  const zoomed = level === "emd" || activeSido !== null; // 확대한 상태(지도 전체가 아님)
  const needSigunguData = level !== "sido";

  const today = useMemo(() => todayRange(), []);
  const dateParams = params ?? (todayOnly ? today : {});

  // 시도 단계: 기존 이름 기준 시도 통계 / 시군구·읍면동 단계: 코드 기준 통계 — 폴리곤 코드와 직접 매칭
  const { data: sidoData } = useSidoStats(dateParams);
  const statsQuery = useRegionCodeStats(dateParams, level === "emd" ? "EMD" : "SIGUNGU", level !== "sido");
  const sigunguQuery = useSigunguRegions(needSigunguData);
  const emdQuery = useEmdRegions(drillSigungu !== null ? drillSigungu.slice(0, 2) : null);

  // 바깥에서 고른 지역으로 확대한다. 사용자가 지도에서 직접 한 단계 올라간 것(goBack)은 focus 값이 그대로라 되돌리지 않는다.
  const hadFocusRef = useRef(false);
  useEffect(() => {
    const sido = focusSido ? skRegions.find((r) => r.ko === focusSido) : undefined;
    if (!sido) {
      if (hadFocusRef.current) {
        hadFocusRef.current = false;
        // 확대하려고 시도 보기로 바꿨던 것을 처음 보기 단위로 되돌린다(필터를 비우면 시군구 보기로 돌아가게).
        setMode(defaultMode);
        setDrillSido(null);
        setDrillSigungu(null);
      }
      return;
    }
    hadFocusRef.current = true;
    setMode("sido");
    setDrillSido(sido.code);
    if (!focusSigungu) {
      setDrillSigungu(null);
      return;
    }
    // 공식 명칭("경기도 수원시 장안구")이 "시도 + 시군구[ 읍면동]" 의 앞부분과 일치하는 가장 긴 시군구를 찾는다.
    const full = `${focusSido} ${focusSigungu} `;
    const regions = (sigunguQuery.data ?? []).filter((r) => r.c.startsWith(sido.code));
    const match = regions.filter((r) => full.startsWith(`${r.n} `)).sort((a, b) => b.n.length - a.n.length)[0];
    // 구가 있는 시 전체("수원시")는 단독 경계가 없으니 그 시의 구들을 묶는 코드 앞 4자리로 확대한다.
    const city = match ? null : regions.filter((r) => `${r.n} `.startsWith(`${focusSido} ${focusSigungu} `));
    setDrillSigungu(match ? match.c : city && city.length > 0 ? city[0].c.slice(0, 4) : null);
  }, [focusSido, focusSigungu, sigunguQuery.data, defaultMode, setMode]);

  const metroCounts = useMemo(() => groupToMetros(sidoData ?? []), [sidoData]);

  // 확대한 시군구. 구가 있는 시(수원시 등)는 시 전체 경계가 없어 코드 앞 4자리로 묶인 구들을 하나의 영역으로 합친다.
  const drilledSigungu = useMemo(() => {
    if (drillSigungu === null) return null;
    const members = (sigunguQuery.data ?? []).filter((r) => r.c.startsWith(drillSigungu));
    if (members.length === 0) return null;
    if (members.length === 1) return members[0];
    const [sido, city] = members[0].n.split(" ");
    return {
      n: `${sido} ${city}`,
      d: members.map((r) => r.d).join(" "),
      b: unionBBox(members.map((r) => r.b).filter((b): b is BBox => !!b)) ?? undefined,
    };
  }, [drillSigungu, sigunguQuery.data]);
  const drilledSido = useMemo(
    () => (activeSido !== null ? skRegions.find((r) => r.code === activeSido) ?? null : null),
    [activeSido]
  );

  const regions = useMemo<RenderRegion[]>(() => {
    if (level === "sido") return skRegions.map((r) => ({ key: r.code, label: r.ko, d: r.d, c: r.c }));
    if (level === "sigungu") {
      const all = sigunguQuery.data ?? [];
      const shown = activeSido !== null ? all.filter((r) => r.c.startsWith(activeSido)) : all;
      return shown.map((r) => ({ key: r.c, label: r.n, d: r.d, c: r.p }));
    }
    const prefix = drillSigungu ?? "";
    return (emdQuery.data ?? [])
      .filter((r) => r.c.startsWith(prefix))
      .map((r) => ({ key: r.c, label: r.n, d: r.d, c: r.p }));
  }, [level, activeSido, drillSigungu, sigunguQuery.data, emdQuery.data]);

  // 통계 쿼리는 keepPreviousData 라 단위가 바뀐 직후(시군구→읍면동)에는 이전 단위의 코드 목록이 남아 있다.
  // 그대로 칠하면 모든 구역이 거짓 "0건"으로 보이므로, 새 응답이 올 때까지는 건수를 모르는 상태로 둔다.
  const statsPending = level !== "sido" && (statsQuery.isPending || statsQuery.isPlaceholderData);

  const countMap = useMemo(() => {
    const m = new Map<string, number>();
    if (level === "sido") {
      for (const r of skRegions) m.set(r.code, metroCounts[r.ko as Metro] ?? 0);
    } else {
      for (const s of statsQuery.data ?? []) m.set(s.code, s.count);
    }
    return m;
  }, [level, metroCounts, statsQuery.data]);

  const countFor = useCallback((key: string) => countMap.get(key) ?? 0, [countMap]);

  // 상위 단위 전체로 발송돼 하위 구역 어디에도 귀속시킬 수 없는 알림 수. 구역 건수와 섞지 않고 따로 보여 준다
  // (섞으면 한 알림이 여러 구역에 중복돼 합계가 어긋난다). 백엔드는 "시군구코드+000"(읍면동 보기)·"시 전체 코드"(시군구 보기)로 내려준다.
  const wideNames = useMemo(() => {
    const m = new Map<string, string>();
    for (const r of sigunguQuery.data ?? []) {
      const [, ...rest] = r.n.split(" "); // 시도명은 앞에서 뗀다
      m.set(r.c, rest.join(" ") || r.n);
      const city = r.c.slice(0, 4) + "0";
      if (rest.length > 1 && !m.has(city)) m.set(city, rest[0]); // 구가 있는 시는 시 이름으로
    }
    return m;
  }, [sigunguQuery.data]);
  const wideEntry = useCallback(
    (statKey: string, nameKey: string) => {
      const count = countMap.get(statKey) ?? 0;
      return count > 0 ? [{ name: wideNames.get(nameKey) ?? nameKey, count }] : [];
    },
    [countMap, wideNames]
  );
  // 읍면동 단계: 확대한 시군구(구가 있는 시는 구, 또는 시 전체)에 해당하는 전체 단위 알림
  const emdWide = useMemo(() => {
    if (level !== "emd" || drillSigungu === null || statsPending) return [];
    const base = drillSigungu.padEnd(5, "0");
    const out = wideEntry(`${base}000`, base);
    return base.endsWith("0") ? out : [...out, ...wideEntry(`${base.slice(0, 4)}0000`, `${base.slice(0, 4)}0`)];
  }, [level, drillSigungu, statsPending, wideEntry]);
  // 시군구 단계: 구가 있는 시의 구 위에 올리면 "시 전체 단위" 알림을 알려 준다
  const wideFor = useCallback(
    (key: string) => {
      if (statsPending) return [];
      if (level === "emd") return emdWide;
      if (level !== "sigungu" || key.endsWith("0")) return [];
      const city = `${key.slice(0, 4)}0`;
      return wideEntry(city, city);
    },
    [statsPending, level, emdWide, wideEntry]
  );
  // 폴리곤이 없는 코드(예: 구가 있는 시 전체 알림)가 최댓값을 키우지 않도록 그려지는 지역만으로 계산한다.
  const maxCount = useMemo(() => {
    let max = 1;
    for (const r of regions) max = Math.max(max, countMap.get(r.key) ?? 0);
    return max;
  }, [regions, countMap]);
  // 기본(오늘 건수)은 12건을 상한으로 고정해 날마다 같은 색이 같은 건수를 뜻하게 한다. 기간 필터를 준 곳(알림 목록)은
  // 건수가 훨씬 커질 수 있어 그 기간의 최댓값까지 늘린다.
  const colorCap = params || !todayOnly ? Math.max(TODAY_CAP, maxCount) : TODAY_CAP;
  const fillFor = useCallback(
    (key: string) => (statsPending ? COLOR_PENDING : colorForCount(countFor(key), colorCap)),
    [statsPending, countFor, colorCap]
  );

  // 시도를 확대할 영역: 그 시도에 속한 시군구 경계 상자들의 합(시도 코드는 시군구 코드 앞 2자리와 같다)
  const sidoBBox = useMemo(() => {
    if (activeSido === null) return null;
    const boxes = (sigunguQuery.data ?? [])
      .filter((r) => r.c.startsWith(activeSido) && r.b)
      .map((r) => r.b as BBox);
    return unionBBox(boxes);
  }, [activeSido, sigunguQuery.data]);

  // 확대 영역(viewBox). 읍면동 단계는 시군구 경계 상자로, 시도를 확대한 시군구 단계는 시도 영역으로, 아니면 지도 전체로
  // 부드럽게 이동한다. 확대할 데이터가 아직 오지 않았으면 도착할 때까지 지도 전체에 머문다.
  const targetViewBox = useMemo(() => {
    if (level === "emd") return drilledSigungu?.b ? fitViewBox(drilledSigungu.b) : BASE_VIEWBOX;
    if (activeSido !== null && sidoBBox) return fitViewBox(sidoBBox);
    return BASE_VIEWBOX;
  }, [level, drilledSigungu, activeSido, sidoBBox]);
  const viewBox = useAnimatedViewBox(targetViewBox);
  // 확대할수록 호버 입체감(솟아오르는 높이)도 같은 비율로 줄여 화면에서 보이는 크기를 일정하게 유지한다.
  // 솟아오름(lift)은 항상 "기본 배율 기준 단위"(0~raise)로 보관하고 그릴 때만 zoom 을 곱한다. 확대 애니메이션 도중에
  // 배율이 바뀌어도 이미 정해 둔 값이 틀어지지 않는다(처음에는 배율 적용값을 보관해 호버 지역이 거대한 기둥처럼 솟았다).
  const zoom = viewBox.w / BASE_VIEWBOX.w;


  const containerRef = useRef<HTMLDivElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const rafIdRef = useRef(0);
  const liftRef = useRef(0);

  const [hovered, setHovered] = useState<string | null>(null);
  const [lift, setLift] = useState(0);
  const [anchor, setAnchor] = useState({ x: 0, y: 0 });
  const [lineEnd, setLineEnd] = useState({ x: 0, y: 0 });
  const [side, setSide] = useState<"left" | "right">("right");
  const tipRef = useRef<HTMLDivElement>(null);
  const [tipShift, setTipShift] = useState(0);

  const hoveredRegion = useMemo(
    () => regions.find((r) => r.key === hovered) || null,
    [regions, hovered]
  );
  const wallCount = Math.floor(lift);
  const liftBaseFill = hoveredRegion ? fillFor(hoveredRegion.key) : RAMP[1];
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

  // 확대/복귀 애니메이션이 끝나기 전(viewBox 가 목표에 도달하기 전)에는 호버를 받지 않는다. 말풍선·연결선 위치를 호버
  // 시점의 화면 좌표로 한 번만 계산하므로, 이동 중에 정하면 애니메이션이 끝난 뒤 위치가 어긋난다.
  const viewBoxSettled =
    Math.abs(viewBox.x - targetViewBox.x) < 0.01 &&
    Math.abs(viewBox.y - targetViewBox.y) < 0.01 &&
    Math.abs(viewBox.w - targetViewBox.w) < 0.01;

  const onEnter = useCallback(
    (r: RenderRegion) => {
      if (!viewBoxSettled) return;
      const fresh = hovered !== r.key;
      setHovered(r.key);
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
      pt.y = r.c[1] - raise * zoom;
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
    [viewBoxSettled, hovered, raise, zoom, animateLift]
  );

  // 말풍선이 지도 영역 밖으로 나가 잘리지 않도록 가로로 밀어 넣는다. 말풍선은 연결선 끝(lineEnd) 기준 좌/우에 8px 떨어져 놓이고
  // (CSS translate), 너비는 내용에 따라 달라 그릴 때 한 번 재서 맞춘다. 좁은 사이드 패널에서는 어느 쪽에 놓아도 넘칠 수 있다.
  useLayoutEffect(() => {
    const tip = tipRef.current;
    const cont = containerRef.current;
    if (!tip || !cont || hovered === null) {
      setTipShift(0);
      return;
    }
    const w = tip.offsetWidth;
    const edge = 8;
    const left = side === "right" ? lineEnd.x + 8 : lineEnd.x - 8 - w;
    let shift = 0;
    if (left < edge) shift = edge - left;
    else if (left + w > cont.clientWidth - edge) shift = cont.clientWidth - edge - (left + w);
    setTipShift(shift);
  }, [hovered, side, lineEnd.x]);

  const onLeave = useCallback(() => {
    cancelAnimationFrame(rafIdRef.current);
    setHovered(null);
    liftRef.current = 0;
    setLift(0);
  }, []);

  const onModeChange = useCallback(
    (next: MapMode) => {
      onLeave();
      setDrillSido(null);
      setDrillSigungu(null);
      setMode(next);
    },
    [onLeave, setMode]
  );

  // 바깥에서 보기 단위를 바꾸면(제어형) 확대 상태를 풀고 새 단위의 전체 보기로 돌아간다
  const prevModeRef = useRef(mode);
  useEffect(() => {
    if (prevModeRef.current === mode) return;
    prevModeRef.current = mode;
    if (!controlled) return;
    onLeave();
    if (!focusSido) {
      setDrillSido(null);
      setDrillSigungu(null);
    }
  }, [mode, controlled, onLeave, focusSido]);

  // 한 단계 위로: 읍면동 → (시도 보기였다면 그 시도의 시군구, 아니면 시군구 전체) → 시도 전체
  const goBack = useCallback(() => {
    onLeave();
    if (drillSigungu !== null) setDrillSigungu(null);
    else setDrillSido(null);
  }, [onLeave, drillSigungu]);

  // 확대한 상태에서 Esc 로 한 단계 위로 돌아간다
  useEffect(() => {
    if (!zoomed) return;
    const onKey = (e: KeyboardEvent) => {
      const el = e.target as HTMLElement | null;
      if (el && (el.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName))) return;
      if (e.key === "Escape") goBack();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [zoomed, goBack]);

  // kind: "sido" 는 시도명 그대로, "district" 는 공식 명칭 "시도 시군구 [읍면동]" 을 나눠서 보낸다.
  const goToAlerts = useCallback(
    (label: string, kind: "sido" | "district") => {
      // 이름이 비어 있으면(경계 데이터에 이름이 없는 구역) sido="" 로 이동하게 되므로 아무것도 하지 않는다.
      if (!label.trim()) return;
      onSelect?.(label);
      if (onRegionSelect) {
        const [sido, ...rest] = kind === "sido" ? [label] : label.split(" ");
        onRegionSelect({ sido, sigungu: rest.length > 0 ? rest.join(" ") : undefined });
        return;
      }
      const q = new URLSearchParams();
      if (kind === "sido") {
        q.set("sido", label);
      } else {
        // 공식 명칭 "시도 시군구 [읍면동]" 을 /alerts 검색 파라미터(sido, sigungu)로 나눈다.
        // 백엔드가 "시도 + 시군구" 를 이름 앞부분 일치(startsWith)로 검색하므로 구·읍면동까지 이어 붙여도 된다.
        const [sido, ...rest] = label.split(" ");
        q.set("sido", sido);
        if (rest.length > 0) q.set("sigungu", rest.join(" "));
      }
      if (todayOnly) {
        q.set("startDate", today.startDate);
        q.set("endDate", today.endDate);
      }
      router.push(`/alerts?${q.toString()}#list`);
    },
    [onSelect, onRegionSelect, todayOnly, today, router]
  );

  // 시도를 누르면 그 시도의 시군구로, 시군구를 누르면 그 시군구의 읍면동으로 확대하고, 읍면동을 누르면 알림 목록으로 이동한다.
  const onClick = useCallback(
    (r: RenderRegion) => {
      if (level === "sido") {
        onLeave();
        setDrillSido(r.key);
        if (onRegionSelect) goToAlerts(r.label, "sido");
        return;
      }
      if (level === "sigungu") {
        onLeave();
        setDrillSigungu(r.key);
        if (onRegionSelect) goToAlerts(r.label, "district");
        return;
      }
      goToAlerts(r.label, "district");
    },
    [level, onLeave, goToAlerts, onRegionSelect]
  );

  useEffect(() => {
    return () => cancelAnimationFrame(rafIdRef.current);
  }, []);

  // 지도 위 지역 라벨(시도 단계 전용). 시군구 단계는 구역이 250개라 라벨을 쓰면 지도를 덮는다.
  // SVG <text> 로 그리면 지도 배율에 따라 글자 크기가 바뀌므로, HTML 로 띄우고 위치만 화면 좌표로 맞춘다.
  const showLabels = showRegionLabels && level === "sido" && viewBoxSettled;
  const [labelPos, setLabelPos] = useState<Record<string, { x: number; y: number }>>({});
  useLayoutEffect(() => {
    if (!showLabels) return;
    const svg = svgRef.current;
    const cont = containerRef.current;
    if (!svg || !cont || !svg.getScreenCTM) return;
    const measure = () => {
      const ctm = svg.getScreenCTM();
      if (!ctm) return;
      const rect = cont.getBoundingClientRect();
      const pt = svg.createSVGPoint();
      const next: Record<string, { x: number; y: number }> = {};
      for (const r of skRegions) {
        [pt.x, pt.y] = LABEL_POS[r.code] ?? r.c;
        const sp = pt.matrixTransform(ctm);
        next[r.code] = { x: sp.x - rect.left, y: sp.y - rect.top };
      }
      setLabelPos(next);
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(cont);
    return () => ro.disconnect();
  }, [showLabels]);

  const sidoName = (ko: string) => t(`metros.${ko}`, { defaultValue: ko });
  const sidoShort = (ko: string) => t(`metroShort.${ko}`, { defaultValue: sidoName(ko) });
  const displayName = (r: RenderRegion) => (level === "sido" ? sidoName(r.label) : r.label);
  const mapLabel = countLabel ?? t("dashboard.todayAlerts");
  const mapUnit = t("dashboard.count");
  const boxStyle = { left: `${lineEnd.x}px`, top: `${lineEnd.y}px`, marginLeft: `${tipShift}px` };
  const modeLabels: Record<MapMode, string> = {
    sido: t("dashboard.mapViewSido"),
    sigungu: t("dashboard.mapViewSigungu"),
  };
  const regionClass = `${styles.region} ${level === "emd" ? styles.regionEmd : level === "sigungu" ? styles.regionSigungu : ""}`;
  // 시군구/읍면동 경계는 PostGIS 에서 합친 MultiPolygon(구멍 포함)이라 even-odd 로 칠해야 구멍이 메워지지 않는다.
  const fillRule = level !== "sido" ? "evenodd" : undefined;
  const geoLoading = needSigunguData && (sigunguQuery.isPending || (level === "emd" && emdQuery.isPending));
  // 통계 요청이 실패하면 isPlaceholderData 가 풀리고 data 가 비어 모든 구역이 "0건"으로 칠해지므로 오류를 알린다.
  const geoError =
    needSigunguData && (sigunguQuery.isError || (level === "emd" && emdQuery.isError) || statsQuery.isError);

  // 확대 막대: 돌아가기 · 지금 보는 곳 이름 · 알림 목록 보기. 시도 보기에서 읍면동까지 내려왔다면 돌아가기가 그 시도의 시군구로 간다.
  const backLabel =
    level === "emd" && drilledSido
      ? sidoName(drilledSido.ko)
      : level === "emd"
        ? t("dashboard.mapBack")
        : t("dashboard.mapBackSido");
  const drillTitle = level === "emd" ? drilledSigungu?.n : drilledSido ? sidoName(drilledSido.ko) : undefined;

  // 현재 보기 상태: "보기 단위 › (확대한 시도) › (확대한 시군구) › 읍면동". 제목 카드에서 지금 무엇을 보고 있는지 알려 준다.
  const statusText = [
    modeLabels[mode],
    drilledSido ? sidoName(drilledSido.ko) : null,
    // 시도를 확대해서 내려왔다면 시도명이 이미 앞에 나오므로 시군구 공식 명칭("경기도 수원시 장안구")에서 시도명을 뗀다.
    level === "emd" ? (drilledSido ? drilledSigungu?.n.replace(/^\S+\s/, "") : drilledSigungu?.n) ?? null : null,
    level === "emd" ? t("dashboard.mapLevelEmd") : null,
  ]
    .filter(Boolean)
    .join(" › ");
  useEffect(() => {
    onStatusChange?.(statusText);
  }, [statusText, onStatusChange]);

  return (
    <div className={`${styles.koreaMap} korea-map`}>
      <div ref={containerRef} className={`${styles.canvas} korea-map__canvas`} style={{ height }}>
        <div className={`${styles.controls} ${compact ? styles.controlsCompact : ""}`}>
          {/* 보기 단위: 선택지를 접어 두지 않고 항상 펼쳐 보여 준다(드롭다운은 화살표 아래에 뭐가 있는지 알 수 없었다) */}
          {showModeToggle && (
            <div className={`krds-segment ${styles.segmented}`} role="radiogroup" aria-label={t("dashboard.mapViewLabel")}>
              {MAP_MODES.map((m) => (
                <button
                  key={m}
                  type="button"
                  role="radio"
                  aria-checked={mode === m}
                  className={styles.segment}
                  onClick={() => onModeChange(m)}
                >
                  {modeLabels[m]}
                </button>
              ))}
            </div>
          )}

          {emdWide.length > 0 && (
            <div className={styles.wideNote} role="note">
              {emdWide.map((w) => t("dashboard.mapWideBanner", { name: w.name, count: w.count })).join(" · ")}
            </div>
          )}

          {zoomed && (
            <div className={styles.drillBar}>
              <button type="button" className={styles.drillBack} onClick={goBack}>
                ← {backLabel}
              </button>
              {drillTitle && <span className={styles.drillName}>{drillTitle}</span>}
              {/* 목록 페이지에 붙인 경우(onRegionSelect)는 확대하는 클릭이 이미 목록 필터를 바꾸므로 "알림 목록 보기" 가 필요 없다 */}
              {drillTitle && !onRegionSelect && (
                <button
                  type="button"
                  className={styles.drillGo}
                  onClick={() =>
                    level === "emd"
                      ? goToAlerts(drilledSigungu?.n ?? "", "district")
                      : goToAlerts(drilledSido?.ko ?? "", "sido")
                  }
                >
                  {t("dashboard.mapViewAlerts")}
                </button>
              )}
            </div>
          )}
        </div>

        {geoLoading && <div className={styles.status}>{t("dashboard.mapLoading")}</div>}
        {geoError && <div className={styles.status}>{t("dashboard.mapLoadError")}</div>}

        <svg
          ref={svgRef}
          viewBox={`${viewBox.x} ${viewBox.y} ${viewBox.w} ${viewBox.h}`}
          className={`${styles.svg} korea-map__svg`}
          role="img"
          aria-label={t("dashboard.mapAriaLabel")}
          aria-busy={statsPending}
          onMouseLeave={onLeave}
        >
          <defs>
            {/* 바닥 그림자/벽은 보기 단위와 무관하게 시도 윤곽(경로 17개 미만)을 재사용한다 — 읍면동 수천 개를 겹쳐 그리지 않는다 */}
            <g id={landId}>
              {skRegions.map((r) => (
                <path key={r.code} d={r.d} />
              ))}
            </g>
            <filter id={shadowFilterId} x="-20%" y="-20%" width="140%" height="150%">
              <feDropShadow dx="0" dy="12" stdDeviation="9" floodColor="#1e2124" floodOpacity="0.12" />
            </filter>
            <filter id={liftShadowId} x="-40%" y="-40%" width="180%" height="180%">
              {/* 필터 값은 viewBox 단위라 확대하면 그림자가 수백 px 로 번진다 — 벽·윗면과 같이 zoom 을 곱한다 */}
              <feDropShadow dx="0" dy={6 * zoom} stdDeviation={5 * zoom} floodColor="#1f2a5c" floodOpacity="0.25" />
            </filter>
          </defs>

          {/* 확대하지 않은 상태에서만: 지도 두께(같은 윤곽을 아래로 내려 칠한 벽면)와 그 그림자. 확대하면 평면으로 보여 준다 */}
          {!zoomed && (
            <use
              href={`#${landId}`}
              transform={`translate(0 ${depth})`}
              fill={WALL_COLOR}
              filter={`url(#${shadowFilterId})`}
            />
          )}

          {/* 확대하면 선택한 영역만 보인다 — 나머지 시도/시군구는 흐리게도 그리지 않는다.
              선택한 시군구의 바닥(거친 윤곽): 읍면동 경계가 더 촘촘해서 생기는 틈을 메운다 */}
          {drilledSigungu && <path d={drilledSigungu.d} className={styles.drilledBase} fillRule="evenodd" />}

          {/* 지역 윗면 (건수 기반 색농도) */}
          <RegionLayer regions={regions} fillFor={fillFor} className={regionClass} fillRule={fillRule} />

          {/* 호버 지역: 입체 블록 (바닥→윗면 벽 겹쌓기 + 윗면 + 그림자) */}
          {hoveredRegion && (
            <>
              <path d={hoveredRegion.d} fill={wallFill(0)} fillRule={fillRule} className={styles.wall} />
              {Array.from({ length: wallCount }, (_, idx) => idx + 1).map((i) => (
                <path
                  key={`wl${i}`}
                  d={hoveredRegion.d}
                  transform={`translate(0 ${-i * zoom})`}
                  fill={wallFill(i)}
                  fillRule={fillRule}
                  className={styles.wall}
                />
              ))}
              <path
                d={hoveredRegion.d}
                transform={`translate(0 ${-lift * zoom})`}
                fill={fillFor(hoveredRegion.key)}
                fillRule={fillRule}
                className={`${regionClass} ${styles.liftTop}`}
                filter={`url(#${liftShadowId})`}
              />
            </>
          )}

          {/* 투명 hit-layer: 위치 고정이라 호버 감지가 안정적 */}
          {regions.map((r) => (
            <path
              key={`hit${r.key}`}
              d={r.d}
              fillRule={fillRule}
              className={styles.hit}
              onMouseEnter={() => onEnter(r)}
              onClick={() => onClick(r)}
            />
          ))}
        </svg>

        {/* 지역 라벨: 0건은 약칭만, 건수가 있으면 이름 위·건수 아래. 진한 채움 위는 흰 글자 + 어두운 halo */}
        {showLabels && (
          <div className={styles.labels} aria-hidden="true">
            {skRegions.map((r) => {
              const p = labelPos[r.code];
              if (!p || r.code === hovered) return null;
              const n = countFor(r.code);
              const metro = SMALL_METROS.has(r.code) || undefined;
              if (!n || statsPending) {
                return (
                  <span key={r.code} className={styles.labelCold} data-small-metro={metro} style={{ left: p.x, top: p.y - 4 }}>
                    {sidoShort(r.ko)}
                  </span>
                );
              }
              return (
                <span
                  key={r.code}
                  className={`${styles.labelHot} ${n >= DARK_LABEL_MIN ? styles.labelDark : ""}`}
                  data-small-metro={metro}
                  style={{ left: p.x, top: p.y }}
                >
                  <span className={styles.labelName}>{sidoShort(r.ko)}</span>
                  <span className={styles.labelCount}>
                    {n}
                    {mapUnit}
                  </span>
                </span>
              );
            })}
          </div>
        )}

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
            ref={tipRef}
            className={`${styles.tip} ${side === "right" ? styles.tipRight : styles.tipLeft} ${compact ? styles.tipCompact : ""}`}
            style={boxStyle}
          >
            <div className={styles.tipRegion}>
              <span className={styles.tipSwatch} style={{ background: fillFor(hoveredRegion.key) }} />
              {displayName(hoveredRegion)}
            </div>
            <div className={styles.tipCount}>
              {mapLabel} <b>{statsPending ? "…" : countFor(hoveredRegion.key)}</b>
              {mapUnit}
            </div>
            {wideFor(hoveredRegion.key).map((w) => (
              <div key={w.name} className={styles.tipWide}>
                {t("dashboard.mapWideTip", { name: w.name, count: w.count })}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 범례: 5단계 램프. 상한 건수 이상은 가장 진한 색 */}
      <div className={`${styles.legend} legend`}>
        <span className={styles.legendTitle}>{countLabel ?? t("dashboard.legendTitle")}</span>
        <span className={styles.legendRamp}>
          {RAMP.map((c) => (
            <i key={c} className={styles.legendSq} style={{ background: c }} />
          ))}
        </span>
        <span className={styles.legendScale}>
          <span>0{mapUnit}</span>
          <span>{t("dashboard.legendMax", { count: colorCap })}</span>
        </span>
      </div>
    </div>
  );
}
