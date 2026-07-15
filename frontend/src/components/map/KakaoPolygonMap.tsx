"use client";

import { useEffect, useRef, useState } from "react";
import { loadKakaoMapSdk } from "@/lib/kakaoMapLoader";
import { useSigunguStats } from "@/lib/queries/useAlerts";
import type { AlertSearchRequest } from "@/api/alertApi";
import { useI18n } from "@/hooks/useI18n";
import { useLanguageStore } from "@/store/languageStore";
import { formatMessage } from "@/utils/formatMessage";

const LANG_LOCALE: Record<string, string> = { ko: "ko-KR", en: "en-US", zh: "zh-CN", ja: "ja-JP" };
type WeatherLabels = ReturnType<typeof useI18n>["weatherMap"]["weather"];

interface SidoFeature {
  type: "Feature";
  geometry: any;
  properties: { CTPRVN_CD: string; CTP_KOR_NM: string; CTP_ENG_NM: string };
}
interface SigunguFeature {
  type: "Feature";
  geometry: any;
  properties: { SIG_CD: string; SIG_KOR_NM: string; SIG_ENG_NM: string };
}

type DangerLevel = 0 | 1 | 2 | 3 | 4;

interface WeatherData {
  temp: number;
  weatherCode: number;
  wind: number;
  humidity: number;
}

const DANGER_BG     = ["#eff6ff", "#f0fdf4", "#fefce8", "#fff7ed", "#fef2f2"] as const;
const DANGER_TEXT   = ["#1d4ed8", "#15803d", "#a16207", "#c2410c", "#b91c1c"] as const;
const DANGER_BORDER = ["#bfdbfe", "#bbf7d0", "#fef08a", "#fdba74", "#fca5a5"] as const;

const DANGER_POLY = [
  { fillColor: "#bfdbfe", fillOpacity: 0.30, strokeColor: "#2563eb", strokeOpacity: 0.8, strokeWeight: 1 },
  { fillColor: "#86efac", fillOpacity: 0.40, strokeColor: "#16a34a", strokeOpacity: 0.8, strokeWeight: 1 },
  { fillColor: "#fde047", fillOpacity: 0.45, strokeColor: "#ca8a04", strokeOpacity: 0.9, strokeWeight: 1 },
  { fillColor: "#fb923c", fillOpacity: 0.55, strokeColor: "#ea580c", strokeOpacity: 0.9, strokeWeight: 1 },
  { fillColor: "#f87171", fillOpacity: 0.65, strokeColor: "#dc2626", strokeOpacity: 1.0, strokeWeight: 1 },
] as const;

const DANGER_POLY_HOVER = [
  { fillColor: "#93c5fd", fillOpacity: 0.55 },
  { fillColor: "#4ade80", fillOpacity: 0.60 },
  { fillColor: "#facc15", fillOpacity: 0.65 },
  { fillColor: "#f97316", fillOpacity: 0.75 },
  { fillColor: "#ef4444", fillOpacity: 0.80 },
] as const;

const SIDO_NORMAL = { fillColor: "#bfdbfe", fillOpacity: 0.35, strokeColor: "#2563eb", strokeOpacity: 0.9, strokeWeight: 1.5 };
const SIDO_HOVER  = { fillColor: "#3b82f6", fillOpacity: 0.60, strokeColor: "#1d4ed8", strokeOpacity: 1.0, strokeWeight: 1.5 };

const KOREA_SW = [33.1, 125.8] as const;
const KOREA_NE = [38.7, 129.6] as const;

const SIDO_ZOOM: Record<string, number> = {
  "서울특별시": 10, "부산광역시": 10, "대구광역시": 10, "인천광역시": 10,
  "광주광역시": 10, "대전광역시": 10, "울산광역시": 10, "세종특별자치시": 10,
  "경기도": 11, "강원특별자치도": 11, "충청북도": 11, "충청남도": 11,
  "전북특별자치도": 11, "전라남도": 11, "경상북도": 11, "경상남도": 11,
  "제주특별자치도": 11, "전남광주통합특별시": 11,
};

function mercLat(lat: number): number {
  const r = (lat * Math.PI) / 180;
  return Math.log(Math.tan(Math.PI / 4 + r / 2));
}

function countToLevel(count: number, maxCount: number): DangerLevel {
  if (count === 0 || maxCount === 0) return 0;
  const ratio = count / maxCount;
  if (ratio <= 0.25) return 1;
  if (ratio <= 0.50) return 2;
  if (ratio <= 0.75) return 3;
  return 4;
}

function parseWeatherCode(code: number, w: WeatherLabels): { desc: string; icon: string } {
  if (code === 0)   return { desc: w.clear,      icon: "☀️" };
  if (code <= 2)    return { desc: w.fewClouds,  icon: "🌤️" };
  if (code <= 3)    return { desc: w.cloudy,     icon: "☁️" };
  if (code <= 48)   return { desc: w.fog,        icon: "🌫️" };
  if (code <= 57)   return { desc: w.drizzle,    icon: "🌦️" };
  if (code <= 67)   return { desc: w.rain,       icon: "🌧️" };
  if (code <= 77)   return { desc: w.snow,       icon: "❄️" };
  if (code <= 82)   return { desc: w.showers,    icon: "🌦️" };
  if (code <= 94)   return { desc: w.hail,       icon: "🌨️" };
  return { desc: w.thunderstorm, icon: "⛈️" };
}

async function fetchWeather(lat: number, lng: number): Promise<WeatherData> {
  const url =
    `https://api.open-meteo.com/v1/forecast` +
    `?latitude=${lat.toFixed(4)}&longitude=${lng.toFixed(4)}` +
    `&current=temperature_2m,wind_speed_10m,relative_humidity_2m,weather_code` +
    `&timezone=Asia%2FSeoul`;
  const data = await fetch(url).then((r) => r.json());
  const c = data.current;
  return {
    temp: Math.round(c.temperature_2m),
    weatherCode: c.weather_code,
    wind: Math.round(c.wind_speed_10m * 10) / 10,
    humidity: c.relative_humidity_2m,
  };
}

function toLatLngPaths(kakao: any, geometry: any): any[][] {
  const rings: number[][][] =
    geometry.type === "MultiPolygon" ? geometry.coordinates.flat(1) : geometry.coordinates;
  return rings.map((ring) => ring.map((c) => new kakao.maps.LatLng(c[1], c[0])));
}

function calcBounds(kakao: any, geometry: any) {
  const b = new kakao.maps.LatLngBounds();
  const coords: number[][] =
    geometry.type === "MultiPolygon" ? geometry.coordinates.flat(2) : geometry.coordinates.flat(1);
  coords.forEach((c) => b.extend(new kakao.maps.LatLng(c[1], c[0])));
  return b;
}

function largestRingCenter(geometry: any): [number, number] {
  const rings: number[][][] =
    geometry.type === "MultiPolygon" ? geometry.coordinates.flat(1) : geometry.coordinates;
  let best = rings[0], bestA = -Infinity;
  rings.forEach((ring) => {
    let n = Infinity, s = -Infinity, w = Infinity, e = -Infinity;
    ring.forEach((c) => { if (c[1]<n) n=c[1]; if (c[1]>s) s=c[1]; if (c[0]<w) w=c[0]; if (c[0]>e) e=c[0]; });
    const a = (s-n)*(e-w); if (a > bestA) { bestA=a; best=ring; }
  });
  let n=Infinity, s=-Infinity, w=Infinity, e=-Infinity;
  best.forEach((c) => { if (c[1]<n) n=c[1]; if (c[1]>s) s=c[1]; if (c[0]<w) w=c[0]; if (c[0]>e) e=c[0]; });
  return [(n+s)/2, (w+e)/2];
}

interface Props {
  params?: AlertSearchRequest;
  mapHeight?: string;
  showSidebar?: boolean;
  externalSido?: string;
  onSidoSelect?: (sido: string | null) => void;
}

export default function KakaoPolygonMap({ params = {}, mapHeight = "500px", showSidebar = true, externalSido, onSidoSelect }: Props) {
  const t = useI18n();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  const DANGER_LABEL = t.weatherMap.dangerLabels;
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef       = useRef<any>(null);
  const kakaoRef     = useRef<any>(null);

  const sidoPolyMap  = useRef<Map<string, any[]>>(new Map());
  const sigunguPolys = useRef<any[]>([]);
  const hoverOverlay = useRef<any>(null);

  const sidoDataRef    = useRef<SidoFeature[]>([]);
  const sigunguDataRef = useRef<SigunguFeature[]>([]);
  const sigunguStatsRef = useRef<Map<string, number>>(new Map());
  const sigunguLevelRef = useRef<Map<string, { l1: number; l2: number; l3: number }>>(new Map());

  const hoveredCdRef    = useRef<string | null>(null);
  const selectedSidoRef = useRef<SidoFeature | null>(null);
  const svgOverlayRef   = useRef<SVGSVGElement | null>(null);
  const dimmedSidosRef  = useRef<SidoFeature[]>([]);
  const hatchPathRef    = useRef<SVGPathElement | null>(null);
  const rafIdRef        = useRef<number | null>(null);

  const [hoveredCd,    setHoveredCd]    = useState<string | null>(null);
  const [selectedSido, setSelectedSido] = useState<SidoFeature | null>(null);
  const [sidoList,     setSidoList]     = useState<SidoFeature[]>([]);
  const [sigunguInfo,  setSigunguInfo]  = useState<{ name: string; danger: DangerLevel; count: number }[]>([]);
  const [weather,      setWeather]      = useState<WeatherData | null>(null);
  const [weatherLoad,  setWeatherLoad]  = useState(false);
  // 상태 메시지: 이미 번역된 문자열이 아니라 "어떤 상태인지"만 저장한다.
  // (렌더마다 t로 새로 포맷팅해야 언어 전환 시 즉시 갱신됨 — setStatus는 대부분
  //  useEffect(deps: []) 안에서 최초 1회만 호출되므로 문자열을 직접 저장하면
  //  마운트 시점 언어에 고정되어 이후 언어를 바꿔도 갱신되지 않는 문제가 있었음)
  const [statusKind, setStatusKind] = useState<
    { type: "loading" | "clickHint" | "loadFailed" } | { type: "sidoSummary"; sido: string; count: number }
  >({ type: "loading" });
  const [mapReady,     setMapReady]     = useState(false);
  const [hoverInfo,    setHoverInfo]    = useState<{ name: string; count: number; danger: DangerLevel; l1: number; l2: number; l3: number } | null>(null);
  const [mousePos,     setMousePos]     = useState<{ x: number; y: number } | null>(null);

  const sigunguStatsQuery = useSigunguStats(
    { ...params, region: selectedSido?.properties.CTP_KOR_NM ?? undefined },
    !!selectedSido
  );
  const sigunguStatsL1 = useSigunguStats(
    { ...params, region: selectedSido?.properties.CTP_KOR_NM ?? undefined, level: "LEVEL_1" },
    !!selectedSido
  );
  const sigunguStatsL2 = useSigunguStats(
    { ...params, region: selectedSido?.properties.CTP_KOR_NM ?? undefined, level: "LEVEL_2" },
    !!selectedSido
  );
  const sigunguStatsL3 = useSigunguStats(
    { ...params, region: selectedSido?.properties.CTP_KOR_NM ?? undefined, level: "LEVEL_3" },
    !!selectedSido
  );

  useEffect(() => { selectedSidoRef.current = selectedSido; }, [selectedSido]);

  /* ── 시군구 stats 데이터 → ref 업데이트 후 재렌더 ── */
  useEffect(() => {
    const m = new Map<string, number>();
    const prefix = selectedSidoRef.current
      ? selectedSidoRef.current.properties.CTP_KOR_NM + " "
      : "";
    sigunguStatsQuery.data?.forEach(({ region, count }) => {
      const name = prefix && region.startsWith(prefix) ? region.slice(prefix.length) : region;
      m.set(name, count);
    });
    sigunguStatsRef.current = m;
    if (selectedSidoRef.current && mapRef.current && kakaoRef.current) {
      drawSigunguOf(selectedSidoRef.current);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sigunguStatsQuery.data]);

  /* ── 레벨별 sigungu stats → ref 업데이트 ── */
  useEffect(() => {
    const prefix = selectedSidoRef.current
      ? selectedSidoRef.current.properties.CTP_KOR_NM + " "
      : "";
    const strip = (r: string) => prefix && r.startsWith(prefix) ? r.slice(prefix.length) : r;

    const lm = new Map<string, { l1: number; l2: number; l3: number }>();
    const ensure = (name: string) => {
      if (!lm.has(name)) lm.set(name, { l1: 0, l2: 0, l3: 0 });
      return lm.get(name)!;
    };
    sigunguStatsL1.data?.forEach(({ region, count }) => { ensure(strip(region)).l1 = count; });
    sigunguStatsL2.data?.forEach(({ region, count }) => { ensure(strip(region)).l2 = count; });
    sigunguStatsL3.data?.forEach(({ region, count }) => { ensure(strip(region)).l3 = count; });
    sigunguLevelRef.current = lm;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sigunguStatsL1.data, sigunguStatsL2.data, sigunguStatsL3.data]);

  /* ── 헬퍼 ── */

  function clearHoverOverlay() {
    hoverOverlay.current?.setMap(null);
    hoverOverlay.current = null;
  }

  function showHoverLabel(kakao: any, map: any, sido: SidoFeature) {
    clearHoverOverlay();
    const [lat, lng] = largestRingCenter(sido.geometry);
    const div = document.createElement("div");
    div.style.cssText =
      "font-size:14px;font-weight:700;color:#1e3a8a;background:rgba(255,255,255,0.93);" +
      "padding:4px 10px;border-radius:8px;pointer-events:none;white-space:nowrap;" +
      "box-shadow:0 2px 6px rgba(0,0,0,0.15);";
    div.textContent = sido.properties.CTP_KOR_NM;
    hoverOverlay.current = new kakao.maps.CustomOverlay({
      map, position: new kakao.maps.LatLng(lat, lng),
      content: div, yAnchor: 0.5, xAnchor: 0.5,
    });
  }

  function highlightSido(cd: string | null) {
    sidoPolyMap.current.forEach((polys, key) =>
      polys.forEach((p) => p.setOptions(key === cd ? SIDO_HOVER : SIDO_NORMAL))
    );
  }

  function zoomToSido(sido: SidoFeature) {
    const kakao = kakaoRef.current;
    const map   = mapRef.current;
    if (!kakao || !map) return;
    map.setBounds(calcBounds(kakao, sido.geometry), 40, 40, 40, 40);
    showHoverLabel(kakao, map, sido);
  }

  function zoomToKorea() {
    const kakao = kakaoRef.current;
    const map   = mapRef.current;
    if (!kakao || !map) return;
    clearHoverOverlay();
    map.setBounds(new kakao.maps.LatLngBounds(
      new kakao.maps.LatLng(KOREA_SW[0], KOREA_SW[1]),
      new kakao.maps.LatLng(KOREA_NE[0], KOREA_NE[1]),
    ));
  }

  /* ── SVG 빗금 오버레이 ── */
  function ensureHatchPath(): SVGPathElement {
    if (!hatchPathRef.current) {
      const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
      path.setAttribute("fill", "url(#hatch-pattern)");
      svgOverlayRef.current?.appendChild(path);
      hatchPathRef.current = path;
    }
    return hatchPathRef.current;
  }

  function renderHatchSvg() {
    const map       = mapRef.current;
    const svg       = svgOverlayRef.current;
    const container = containerRef.current;
    if (!map || !svg || !container || dimmedSidosRef.current.length === 0) return;

    const bounds = map.getBounds();
    const sw     = bounds.getSouthWest();
    const ne     = bounds.getNorthEast();
    const minLng = sw.getLng(), maxLng = ne.getLng();
    const minLat = sw.getLat(), maxLat = ne.getLat();
    const minY   = mercLat(minLat), maxY = mercLat(maxLat);
    const w = container.clientWidth;
    const h = container.clientHeight;

    function toSvg(lat: number, lng: number): string {
      const x = ((lng - minLng) / (maxLng - minLng)) * w;
      const y = (1 - (mercLat(lat) - minY) / (maxY - minY)) * h;
      return `${x},${y}`;
    }

    const d = dimmedSidosRef.current.map((sido) => {
      const rings: number[][][] =
        sido.geometry.type === "MultiPolygon"
          ? sido.geometry.coordinates.flat(1)
          : sido.geometry.coordinates;
      return rings
        .map((ring: number[][]) => `M ${ring.map((c) => toSvg(c[1], c[0])).join(" L ")} Z`)
        .join(" ");
    }).join(" ");

    ensureHatchPath().setAttribute("d", d);
  }

  function scheduleHatchRender() {
    if (rafIdRef.current !== null) cancelAnimationFrame(rafIdRef.current);
    rafIdRef.current = requestAnimationFrame(() => {
      rafIdRef.current = null;
      renderHatchSvg();
    });
  }

  function clearHatch() {
    dimmedSidosRef.current = [];
    if (hatchPathRef.current) hatchPathRef.current.setAttribute("d", "");
  }

  function clearAll() {
    sidoPolyMap.current.forEach((polys) => polys.forEach((p) => p.setMap(null)));
    sidoPolyMap.current.clear();
    sigunguPolys.current.forEach((p) => p.setMap(null));
    sigunguPolys.current = [];
    setHoverInfo(null);
    clearHoverOverlay();
    clearHatch();
  }

  function drawSido() {
    const kakao = kakaoRef.current;
    const map   = mapRef.current;
    clearAll();

    // 도(道)를 먼저, 광역시·특별시를 나중에 그려야 비지(광주·대구·대전·세종)가 위에 올라와 hover 이벤트를 받음
    const sorted = [...sidoDataRef.current].sort((a, b) => {
      const isCity = (f: SidoFeature) => /광역시|특별시|특별자치시/.test(f.properties.CTP_KOR_NM);
      return Number(isCity(a)) - Number(isCity(b));
    });
    sorted.forEach((feature) => {
      const cd    = feature.properties.CTPRVN_CD;
      const polys: any[] = [];
      toLatLngPaths(kakao, feature.geometry).forEach((path) => {
        const p = new kakao.maps.Polygon({ map, path, ...SIDO_NORMAL });
        kakao.maps.event.addListener(p, "mouseover", () => {
          if (selectedSidoRef.current) return;
          highlightSido(cd);
          showHoverLabel(kakao, map, feature);
        });
        kakao.maps.event.addListener(p, "mouseout", () => {
          if (selectedSidoRef.current) return;
          highlightSido(null);
          clearHoverOverlay();
        });
        kakao.maps.event.addListener(p, "click", () => {
          setSelectedSido(feature);
          onSidoSelect?.(feature.properties.CTP_KOR_NM);
        });
        polys.push(p);
      });
      sidoPolyMap.current.set(cd, polys);
    });

    zoomToKorea();
    setStatusKind({ type: "clickHint" });
  }

  function drawSigunguOf(sido: SidoFeature) {
    const kakao = kakaoRef.current;
    const map   = mapRef.current;

    // 시군구 폴리곤만 초기화, 시도 폴리곤은 유지
    sigunguPolys.current.forEach((p) => p.setMap(null));
    sigunguPolys.current = [];
    setHoverInfo(null);
    clearHoverOverlay();

    // 선택된 시/도는 투명, 나머지는 회색으로 마스킹
    const selectedCd = sido.properties.CTPRVN_CD;
    sidoPolyMap.current.forEach((polys, cd) => {
      polys.forEach((p) =>
        p.setOptions(
          cd === selectedCd
            ? { fillOpacity: 0, strokeOpacity: 0 }
            : { fillColor: "#d1d5db", fillOpacity: 0.80, strokeColor: "#9ca3af", strokeOpacity: 0.5, strokeWeight: 1 }
        )
      );
    });

    // 비선택 시/도에 파란 빗금 오버레이
    dimmedSidosRef.current = sidoDataRef.current.filter(
      (f) => f.properties.CTPRVN_CD !== selectedCd
    );
    renderHatchSvg();

    const cd   = sido.properties.CTPRVN_CD;
    const list = sigunguDataRef.current.filter((f) => f.properties.SIG_CD.startsWith(cd));
    const statsMap = sigunguStatsRef.current;

    const counts = list.map((f) => statsMap.get(f.properties.SIG_KOR_NM) ?? 0);
    const maxCount = Math.max(...counts, 0);

    const infoList: { name: string; danger: DangerLevel; count: number }[] = [];

    list.forEach((feature) => {
      const name   = feature.properties.SIG_KOR_NM;
      const count  = statsMap.get(name) ?? 0;
      const danger = countToLevel(count, maxCount);
      const style  = { ...DANGER_POLY[danger] };
      const hStyle = { ...DANGER_POLY[danger], ...DANGER_POLY_HOVER[danger] };
      infoList.push({ name, danger, count });

      // 시군구 이름 정적 레이블
      const [cLat, cLng] = largestRingCenter(feature.geometry);
      const labelDiv = document.createElement("div");
      labelDiv.style.cssText =
        "font-size:11px;font-weight:600;color:#1f2937;pointer-events:none;white-space:nowrap;" +
        "text-shadow:0 0 3px white,0 0 3px white,0 0 3px white;";
      labelDiv.textContent = name;
      const labelOverlay = new kakao.maps.CustomOverlay({
        map, position: new kakao.maps.LatLng(cLat, cLng),
        content: labelDiv, yAnchor: 0.5, xAnchor: 0.5,
      });
      sigunguPolys.current.push(labelOverlay);

      toLatLngPaths(kakao, feature.geometry).forEach((path) => {
        const p = new kakao.maps.Polygon({ map, path, ...style });
        kakao.maps.event.addListener(p, "mouseover", () => {
          p.setOptions(hStyle);
          const lv = sigunguLevelRef.current.get(name) ?? { l1: 0, l2: 0, l3: 0 };
          setHoverInfo({ name, count, danger, l1: lv.l1, l2: lv.l2, l3: lv.l3 });
        });
        kakao.maps.event.addListener(p, "mouseout", () => {
          p.setOptions(style);
          setHoverInfo(null);
        });
        sigunguPolys.current.push(p);
      });
    });

    setSigunguInfo(infoList);
    // setBounds는 먼 섬(백령도, 울릉도 등)까지 포함해 중심이 벗어나므로
    // 가장 큰 폴리곤(본토) 중심으로 setCenter 사용
    const [lat, lng] = largestRingCenter(sido.geometry);
    const zoomLevel = SIDO_ZOOM[sido.properties.CTP_KOR_NM] ?? 10;
    map.setCenter(new kakao.maps.LatLng(lat, lng));
    map.setLevel(zoomLevel);
    setStatusKind({ type: "sidoSummary", sido: sido.properties.CTP_KOR_NM, count: list.length });
  }

  /* ── 날씨 fetch ── */
  useEffect(() => {
    if (!selectedSido) { setWeather(null); return; }
    const [lat, lng] = largestRingCenter(selectedSido.geometry);
    setWeatherLoad(true);
    setWeather(null);
    fetchWeather(lat, lng)
      .then(setWeather)
      .catch(() => setWeather(null))
      .finally(() => setWeatherLoad(false));
  }, [selectedSido]);

  /* ── selectedSido 변경 시 폴리곤 재렌더 ── */
  useEffect(() => {
    if (!mapRef.current) return;
    if (selectedSido) drawSigunguOf(selectedSido);
    else { drawSido(); if (hoveredCdRef.current) highlightSido(hoveredCdRef.current); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedSido]);

  useEffect(() => {
    if (!selectedSido) highlightSido(hoveredCd);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hoveredCd]);

  /* ── 지도 초기화 ── */
  useEffect(() => {
    let cancelled = false;
    Promise.all([
      loadKakaoMapSdk(),
      fetch("/sido.geojson").then((r) => r.json()),
      fetch("/sigungu.geojson").then((r) => r.json()),
    ]).then(([kakao, sidoGeo, sigunguGeo]) => {
      if (cancelled || !containerRef.current) return;
      kakaoRef.current       = kakao;
      sidoDataRef.current    = sidoGeo.features;
      sigunguDataRef.current = sigunguGeo.features;
      setSidoList(sidoGeo.features);
      const map = new kakao.maps.Map(containerRef.current, {
        center: new kakao.maps.LatLng(36.2, 127.7), level: 13,
      });
      const zoomControl = new kakao.maps.ZoomControl();
      map.addControl(zoomControl, kakao.maps.ControlPosition.RIGHT);
      mapRef.current = map;

      // SVG를 containerRef 내부에 삽입 (Kakao 내부 z-index 체계 안에서 동작)
      // Kakao zoom control은 내부에서 z-index: 200 이상을 가지므로 SVG(z-index 없음, DOM 순서 기반)보다 위에 표시됨
      const svgEl = document.createElementNS("http://www.w3.org/2000/svg", "svg");
      svgEl.style.cssText = "position:absolute;top:0;left:0;width:100%;height:100%;pointer-events:none;";
      const defs = document.createElementNS("http://www.w3.org/2000/svg", "defs");
      const pattern = document.createElementNS("http://www.w3.org/2000/svg", "pattern");
      pattern.setAttribute("id", "hatch-pattern");
      pattern.setAttribute("patternUnits", "userSpaceOnUse");
      pattern.setAttribute("width", "10");
      pattern.setAttribute("height", "10");
      pattern.setAttribute("patternTransform", "rotate(45)");
      const line = document.createElementNS("http://www.w3.org/2000/svg", "line");
      line.setAttribute("x1", "0"); line.setAttribute("y1", "0");
      line.setAttribute("x2", "0"); line.setAttribute("y2", "10");
      line.setAttribute("stroke", "#3b82f6");
      line.setAttribute("stroke-width", "1.5");
      line.setAttribute("stroke-opacity", "0.5");
      pattern.appendChild(line);
      defs.appendChild(pattern);
      svgEl.appendChild(defs);
      containerRef.current.appendChild(svgEl);
      svgOverlayRef.current = svgEl as unknown as SVGSVGElement;

      kakao.maps.event.addListener(map, "center_changed", scheduleHatchRender);
      kakao.maps.event.addListener(map, "zoom_changed", scheduleHatchRender);
      drawSido();
      setMapReady(true);
    }).catch(() => setStatusKind({ type: "loadFailed" }));
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /* ── 외부 필터(sido)와 지도 동기화 ── */
  useEffect(() => {
    if (!mapReady) return;
    if (!externalSido) {
      setSelectedSido(null);
      return;
    }
    const feature = sidoDataRef.current.find(
      (f) => f.properties.CTP_KOR_NM === externalSido
    );
    if (feature) setSelectedSido(feature);
  }, [externalSido, mapReady]);

  /* ── 사이드바 핸들러 ── */
  function handleEnter(sido: SidoFeature) {
    if (selectedSidoRef.current) return;
    hoveredCdRef.current = sido.properties.CTPRVN_CD;
    setHoveredCd(sido.properties.CTPRVN_CD);
    zoomToSido(sido);
  }
  function handleLeave() {
    if (selectedSidoRef.current) return;
    hoveredCdRef.current = null;
    setHoveredCd(null);
    zoomToKorea();
  }

  /* ── 파생 값 ── */
  const dangerCount = DANGER_LABEL.map((_, lv) => sigunguInfo.filter((s) => s.danger === lv).length);
  const maxDanger   = sigunguInfo.reduce<number>((m, s) => Math.max(m, s.danger), 0) as DangerLevel;
  const totalCount  = sigunguInfo.reduce((sum, s) => sum + s.count, 0);

  // statusKind → 현재 언어(t) 기준으로 매 렌더마다 새로 포맷팅
  const statusText =
    statusKind.type === "clickHint" ? t.weatherMap.clickHint
    : statusKind.type === "loadFailed" ? t.weatherMap.mapLoadFailed
    : statusKind.type === "sidoSummary"
      ? formatMessage(t.weatherMap.sidoSummary, { sido: t.metros[statusKind.sido as keyof typeof t.metros] ?? statusKind.sido, count: statusKind.count })
      : t.weatherMap.mapLoading;

  /* ── Render ── */
  return (
    <div className="flex gap-3 bg-white rounded-xl shadow overflow-hidden" style={{ height: mapHeight }}>
      {/* 지도 */}
      <div className={`flex flex-col gap-2 min-w-0 p-3 ${showSidebar ? "flex-1" : "w-full"}`}>
        <div className="flex items-center gap-3 shrink-0">
          {selectedSido && (
            <button
              onClick={() => { setSelectedSido(null); onSidoSelect?.(null); }}
              className="px-3 py-1 rounded text-sm font-medium bg-gray-100 hover:bg-gray-200 border border-gray-300"
            >
              {t.weatherMap.backToAll}
            </button>
          )}
          <span className="text-xs text-gray-400">{statusText}</span>
        </div>
        <div
          className="flex-1 min-h-0 relative rounded-lg border border-gray-200 overflow-hidden"
          onMouseMove={(e) => {
            const rect = e.currentTarget.getBoundingClientRect();
            setMousePos({ x: e.clientX - rect.left, y: e.clientY - rect.top });
          }}
          onMouseLeave={() => { setHoverInfo(null); setMousePos(null); }}
        >
          <div ref={containerRef} className="absolute inset-0" />
          {hoverInfo && mousePos && (
            <div
              className="absolute pointer-events-none"
              style={{
                left: mousePos.x + 14,
                top: mousePos.y - 10,
                zIndex: 20,
                background: "white",
                border: "1px solid #e5e7eb",
                borderRadius: "8px",
                padding: "7px 11px",
                boxShadow: "0 2px 8px rgba(0,0,0,0.15)",
                minWidth: "140px",
                whiteSpace: "nowrap",
                transform: "translateY(-100%)",
              }}
            >
              <div style={{ fontWeight: 700, color: "#111827", fontSize: "13px", marginBottom: "4px" }}>{hoverInfo.name}</div>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "12px", paddingBottom: "4px", borderBottom: "1px solid #f3f4f6" }}>
                <span style={{ fontSize: "11px", color: "#6b7280" }}>{t.statsPage.totalAlerts}</span>
                <span style={{ fontSize: "12px", fontWeight: 700, color: DANGER_TEXT[hoverInfo.danger] }}>{hoverInfo.count.toLocaleString(locale)}{t.statsPage.countUnit}</span>
              </div>
              {(
                [
                  { label: t.levels.안전안내, val: hoverInfo.l1, color: "#1d4ed8" },
                  { label: t.levels.긴급재난, val: hoverInfo.l2, color: "#c2410c" },
                  { label: t.levels.위급재난, val: hoverInfo.l3, color: "#b91c1c" },
                ] as const
              ).map(({ label, val, color }) => (
                <div key={label} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "12px", marginTop: "2px" }}>
                  <span style={{ fontSize: "11px", color }}>{label}</span>
                  <span style={{ fontSize: "11px", fontWeight: 600, color: "#374151" }}>{val.toLocaleString(locale)}{t.statsPage.countUnit}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* 사이드바 */}
      {showSidebar && <div className="w-52 flex flex-col gap-2 overflow-y-auto shrink-0 p-3 border-l border-gray-100">

        {/* 시도 목록 (전국 뷰) */}
        {!selectedSido && (
          <>
            <p className="text-xs font-semibold text-gray-400">{t.weatherMap.sidoListTitle}</p>
            {sidoList.map((sido) => {
              const cd    = sido.properties.CTPRVN_CD;
              const isHov = hoveredCd === cd;
              return (
                <button
                  key={cd}
                  onMouseEnter={() => handleEnter(sido)}
                  onMouseLeave={handleLeave}
                  onClick={() => setSelectedSido(sido)}
                  className={[
                    "text-left px-2 py-1.5 rounded border text-sm transition-colors duration-100",
                    isHov
                      ? "bg-blue-100 text-blue-800 border-blue-300"
                      : "bg-white text-gray-700 border-gray-200 hover:bg-blue-50",
                  ].join(" ")}
                >
                  {t.metros[sido.properties.CTP_KOR_NM as keyof typeof t.metros] ?? sido.properties.CTP_KOR_NM}
                </button>
              );
            })}
          </>
        )}

        {/* 정보 패널 (시도 선택 후) */}
        {selectedSido && (
          <div className="flex flex-col gap-3">
            <div>
              <p className="text-xs text-gray-400">{t.weatherMap.selectedRegion}</p>
              <p className="text-base font-bold text-gray-800">{t.metros[selectedSido.properties.CTP_KOR_NM as keyof typeof t.metros] ?? selectedSido.properties.CTP_KOR_NM}</p>
            </div>

            {/* 종합 현황 */}
            <div
              className="rounded-lg border p-3"
              style={{ background: DANGER_BG[maxDanger], borderColor: DANGER_BORDER[maxDanger] }}
            >
              <p className="text-xs font-semibold text-gray-500 mb-1">{t.weatherMap.overallDanger}</p>
              <p className="text-xl font-bold" style={{ color: DANGER_TEXT[maxDanger] }}>
                {DANGER_LABEL[maxDanger]}
              </p>
              <p className="text-xs text-gray-500 mt-0.5">{formatMessage(t.weatherMap.totalCountLabel, { count: totalCount.toLocaleString(locale) })}</p>
              <div className="flex gap-0.5 mt-2">
                {DANGER_LABEL.map((_, lv) => (
                  <div
                    key={lv}
                    className="h-1.5 flex-1 rounded-full"
                    style={{ background: lv <= maxDanger ? DANGER_TEXT[lv as DangerLevel] : "#e5e7eb" }}
                  />
                ))}
              </div>
            </div>

            {/* 시군구 발생 현황 */}
            <div className="rounded-lg border border-gray-200 p-3 bg-white">
              <p className="text-xs font-semibold text-gray-500 mb-2">{t.weatherMap.districtStatusTitle}</p>
              {sigunguStatsQuery.isLoading && (
                <p className="text-xs text-gray-400">{t.loading}</p>
              )}
              <div className="flex flex-col gap-1">
                {DANGER_LABEL.map((label, lv) =>
                  dangerCount[lv] > 0 ? (
                    <div key={lv} className="flex items-center justify-between">
                      <span
                        className="text-xs font-medium px-1.5 py-0.5 rounded"
                        style={{ background: DANGER_BG[lv], color: DANGER_TEXT[lv] }}
                      >
                        {label}
                      </span>
                      <span className="text-xs text-gray-600">{dangerCount[lv]}{t.weatherMap.districtUnit}</span>
                    </div>
                  ) : null
                )}
              </div>
            </div>

            {/* 날씨 정보 */}
            <div className="rounded-lg border border-gray-200 p-3 bg-white">
              <p className="text-xs font-semibold text-gray-500 mb-2">{t.weatherMap.weatherInfoTitle}</p>
              {weatherLoad && <p className="text-xs text-gray-400">{t.loading}</p>}
              {!weatherLoad && weather && (() => {
                const { desc, icon } = parseWeatherCode(weather.weatherCode, t.weatherMap.weather);
                return (
                  <>
                    <div className="flex items-center gap-2">
                      <span className="text-2xl">{icon}</span>
                      <span className="text-2xl font-bold text-gray-800">{weather.temp}°C</span>
                    </div>
                    <p className="text-sm text-gray-600 mt-0.5">{desc}</p>
                    <div className="flex gap-3 mt-1 text-xs text-gray-500">
                      <span>💨 {weather.wind}m/s</span>
                      <span>💧 {weather.humidity}%</span>
                    </div>
                  </>
                );
              })()}
              {!weatherLoad && !weather && (
                <p className="text-xs text-gray-400">{t.weatherMap.noWeatherData}</p>
              )}
            </div>

            {/* 위험도 범례 */}
            <div className="rounded-lg border border-gray-200 p-3 bg-white">
              <p className="text-xs font-semibold text-gray-500 mb-2">{t.weatherMap.dangerLegendTitle}</p>
              <div className="flex flex-col gap-1">
                {DANGER_LABEL.map((label, lv) => (
                  <div key={lv} className="flex items-center gap-2">
                    <div
                      className="w-4 h-3 rounded-sm border"
                      style={{
                        background: DANGER_POLY[lv].fillColor,
                        borderColor: DANGER_POLY[lv].strokeColor,
                      }}
                    />
                    <span className="text-xs text-gray-600">{label}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>}
    </div>
  );
}
