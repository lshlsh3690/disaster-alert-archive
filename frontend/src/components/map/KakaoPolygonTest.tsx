"use client";

import { useEffect, useRef, useState } from "react";
import { loadKakaoMapSdk } from "@/lib/kakaoMapLoader";

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
  desc: string;
  icon: string;
  wind: number;
  humidity: number;
}

const DANGER_LABEL  = ["없음", "관심", "주의", "경계", "심각"] as const;
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

// ── 가상 위험도 데이터 (실제 API 연동 시 교체) ─────────────────────
function mockDanger(sigCode: string): DangerLevel {
  const hash = sigCode.split("").reduce((acc, c) => (acc * 31 + c.charCodeAt(0)) | 0, 0);
  const v = Math.abs(hash) % 100;
  if (v < 40) return 0; // 없음 40%
  if (v < 65) return 1; // 관심 25%
  if (v < 80) return 2; // 주의 15%
  if (v < 92) return 3; // 경계 12%
  return 4;             // 심각  8%
}

// ── 날씨 코드 → 한국어 + 이모지 ───────────────────────────────────
function parseWeatherCode(code: number): { desc: string; icon: string } {
  if (code === 0)              return { desc: "맑음",     icon: "☀️" };
  if (code <= 2)               return { desc: "구름 조금", icon: "🌤️" };
  if (code <= 3)               return { desc: "흐림",     icon: "☁️" };
  if (code <= 48)              return { desc: "안개",     icon: "🌫️" };
  if (code <= 57)              return { desc: "이슬비",   icon: "🌦️" };
  if (code <= 67)              return { desc: "비",       icon: "🌧️" };
  if (code <= 77)              return { desc: "눈",       icon: "❄️" };
  if (code <= 82)              return { desc: "소나기",   icon: "🌦️" };
  if (code <= 94)              return { desc: "우박",     icon: "🌨️" };
  return { desc: "뇌우",     icon: "⛈️" };
}

async function fetchWeather(lat: number, lng: number): Promise<WeatherData> {
  const url =
    `https://api.open-meteo.com/v1/forecast` +
    `?latitude=${lat.toFixed(4)}&longitude=${lng.toFixed(4)}` +
    `&current=temperature_2m,wind_speed_10m,relative_humidity_2m,weather_code` +
    `&timezone=Asia%2FSeoul`;
  const data = await fetch(url).then((r) => r.json());
  const c = data.current;
  const { desc, icon } = parseWeatherCode(c.weather_code);
  return {
    temp: Math.round(c.temperature_2m),
    wind: Math.round(c.wind_speed_10m * 10) / 10,
    humidity: c.relative_humidity_2m,
    desc,
    icon,
  };
}

// ── GeoJSON 유틸 ───────────────────────────────────────────────────
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

// ── 컴포넌트 ───────────────────────────────────────────────────────
export default function KakaoPolygonTest() {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef       = useRef<any>(null);
  const kakaoRef     = useRef<any>(null);

  const sidoPolyMap  = useRef<Map<string, any[]>>(new Map());
  const sigunguPolys = useRef<any[]>([]);
  const hoverOverlay = useRef<any>(null);
  const tooltipRef   = useRef<any>(null);

  const sidoDataRef    = useRef<SidoFeature[]>([]);
  const sigunguDataRef = useRef<SigunguFeature[]>([]);

  const hoveredCdRef    = useRef<string | null>(null);
  const selectedSidoRef = useRef<SidoFeature | null>(null);

  const [hoveredCd,    setHoveredCd]    = useState<string | null>(null);
  const [selectedSido, setSelectedSido] = useState<SidoFeature | null>(null);
  const [sidoList,     setSidoList]     = useState<SidoFeature[]>([]);
  const [sigunguInfo,  setSigunguInfo]  = useState<{ name: string; danger: DangerLevel }[]>([]);
  const [weather,      setWeather]      = useState<WeatherData | null>(null);
  const [weatherLoad,  setWeatherLoad]  = useState(false);
  const [status,       setStatus]       = useState("지도 로딩 중...");

  useEffect(() => { selectedSidoRef.current = selectedSido; }, [selectedSido]);

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

  /* ── 폴리곤 렌더 ── */

  function clearTooltip() {
    tooltipRef.current?.setMap(null);
    tooltipRef.current = null;
  }

  function showTooltip(kakao: any, map: any, latLng: any, name: string, danger: DangerLevel) {
    const div = document.createElement("div");
    div.style.cssText =
      "background:white;border:1px solid #e5e7eb;border-radius:8px;padding:6px 10px;" +
      "pointer-events:none;white-space:nowrap;box-shadow:0 2px 8px rgba(0,0,0,0.15);";
    div.innerHTML =
      `<div style="font-weight:700;color:#111827;font-size:13px">${name}</div>` +
      `<div style="display:flex;align-items:center;gap:4px;margin-top:3px">` +
      `<div style="width:8px;height:8px;border-radius:50%;background:${DANGER_TEXT[danger]};flex-shrink:0"></div>` +
      `<span style="font-size:12px;color:${DANGER_TEXT[danger]};font-weight:600">${DANGER_LABEL[danger]}</span>` +
      `</div>`;
    clearTooltip();
    tooltipRef.current = new kakao.maps.CustomOverlay({
      map, position: latLng, content: div,
      yAnchor: 1.5, xAnchor: 0.5, zIndex: 10,
    });
  }

  function moveTooltip(latLng: any) {
    tooltipRef.current?.setPosition(latLng);
  }

  function clearAll() {
    sidoPolyMap.current.forEach((polys) => polys.forEach((p) => p.setMap(null)));
    sidoPolyMap.current.clear();
    sigunguPolys.current.forEach((p) => p.setMap(null));
    sigunguPolys.current = [];
    clearTooltip();
    clearHoverOverlay();
  }

  function drawSido() {
    const kakao = kakaoRef.current;
    const map   = mapRef.current;
    clearAll();

    sidoDataRef.current.forEach((feature) => {
      const cd    = feature.properties.CTPRVN_CD;
      const polys: any[] = [];
      toLatLngPaths(kakao, feature.geometry).forEach((path) => {
        const p = new kakao.maps.Polygon({ map, path, ...SIDO_NORMAL });
        kakao.maps.event.addListener(p, "click", () => setSelectedSido(feature));
        polys.push(p);
      });
      sidoPolyMap.current.set(cd, polys);
    });

    zoomToKorea();
    setStatus("오른쪽 목록에서 시도에 마우스를 올려보세요");
  }

  function drawSigunguOf(sido: SidoFeature) {
    const kakao = kakaoRef.current;
    const map   = mapRef.current;
    clearAll();

    toLatLngPaths(kakao, sido.geometry).forEach((path) => {
      const p = new kakao.maps.Polygon({
        map, path,
        strokeWeight: 2, strokeColor: "#1d4ed8", strokeOpacity: 1,
        fillColor: "#eff6ff", fillOpacity: 0.08,
      });
      sigunguPolys.current.push(p);
    });

    const cd   = sido.properties.CTPRVN_CD;
    const list = sigunguDataRef.current.filter((f) => f.properties.SIG_CD.startsWith(cd));
    const infoList: { name: string; danger: DangerLevel }[] = [];

    list.forEach((feature) => {
      const danger = mockDanger(feature.properties.SIG_CD);
      const name   = feature.properties.SIG_KOR_NM;
      const style  = { ...DANGER_POLY[danger] };
      const hStyle = { ...DANGER_POLY[danger], ...DANGER_POLY_HOVER[danger] };
      infoList.push({ name, danger });

      toLatLngPaths(kakao, feature.geometry).forEach((path) => {
        const p = new kakao.maps.Polygon({ map, path, ...style });
        kakao.maps.event.addListener(p, "mouseover", (e: any) => {
          p.setOptions(hStyle);
          showTooltip(kakao, map, e.latLng, name, danger);
        });
        kakao.maps.event.addListener(p, "mousemove", (e: any) => {
          moveTooltip(e.latLng);
        });
        kakao.maps.event.addListener(p, "mouseout", () => {
          p.setOptions(style);
          clearTooltip();
        });
        sigunguPolys.current.push(p);
      });
    });

    setSigunguInfo(infoList);
    map.setBounds(calcBounds(kakao, sido.geometry), 30, 30, 30, 30);
    setStatus(`${sido.properties.CTP_KOR_NM} — ${list.length}개 시군구`);
  }

  /* ── 날씨 fetch (시도 선택 시) ── */
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

  /* ── Effects ── */
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
      mapRef.current = map;
      drawSido();
    }).catch(() => setStatus("로드 실패"));
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  /* ── Render ── */
  return (
    <div className="flex gap-3 p-4 h-screen">
      {/* 지도 */}
      <div className="flex flex-col gap-2 flex-1 min-w-0">
        <div className="flex items-center gap-3">
          <h1 className="text-lg font-bold">카카오맵 폴리곤 테스트</h1>
          {selectedSido && (
            <button
              onClick={() => setSelectedSido(null)}
              className="px-3 py-1 rounded text-sm font-medium bg-gray-100 hover:bg-gray-200 border border-gray-300"
            >
              ← 전체 보기
            </button>
          )}
          <span className="text-sm text-gray-500">{status}</span>
        </div>
        <div ref={containerRef} className="flex-1 rounded-xl border border-gray-200 shadow" />
      </div>

      {/* 사이드바 */}
      <div className="w-48 flex flex-col gap-2 overflow-y-auto shrink-0">

        {/* 시도 목록 */}
        {!selectedSido && (
          <>
            <p className="text-xs font-semibold text-gray-400 px-1">시 · 도</p>
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
                  {sido.properties.CTP_KOR_NM}
                </button>
              );
            })}
          </>
        )}

        {/* 정보 패널 (시도 선택 후) */}
        {selectedSido && (
          <div className="flex flex-col gap-3">
            <div>
              <p className="text-xs text-gray-400">선택 지역</p>
              <p className="text-base font-bold text-gray-800">{selectedSido.properties.CTP_KOR_NM}</p>
            </div>

            {/* 종합 위험도 */}
            <div
              className="rounded-lg border p-3"
              style={{ background: DANGER_BG[maxDanger], borderColor: DANGER_BORDER[maxDanger] }}
            >
              <p className="text-xs font-semibold text-gray-500 mb-1">종합 위험도</p>
              <p className="text-xl font-bold" style={{ color: DANGER_TEXT[maxDanger] }}>
                {DANGER_LABEL[maxDanger]}
              </p>
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

            {/* 시군구 위험도 분포 */}
            <div className="rounded-lg border border-gray-200 p-3 bg-white">
              <p className="text-xs font-semibold text-gray-500 mb-2">시군구 현황</p>
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
                      <span className="text-xs text-gray-600">{dangerCount[lv]}개</span>
                    </div>
                  ) : null
                )}
              </div>
            </div>

            {/* 날씨 정보 (Open-Meteo 실제 데이터) */}
            <div className="rounded-lg border border-gray-200 p-3 bg-white">
              <p className="text-xs font-semibold text-gray-500 mb-2">날씨 정보</p>
              {weatherLoad && (
                <p className="text-xs text-gray-400">불러오는 중...</p>
              )}
              {!weatherLoad && weather && (
                <>
                  <div className="flex items-center gap-2">
                    <span className="text-2xl">{weather.icon}</span>
                    <span className="text-2xl font-bold text-gray-800">{weather.temp}°C</span>
                  </div>
                  <p className="text-sm text-gray-600 mt-0.5">{weather.desc}</p>
                  <div className="flex gap-3 mt-1 text-xs text-gray-500">
                    <span>💨 {weather.wind}m/s</span>
                    <span>💧 {weather.humidity}%</span>
                  </div>
                </>
              )}
              {!weatherLoad && !weather && (
                <p className="text-xs text-gray-400">날씨 정보 없음</p>
              )}
            </div>

            {/* 위험도 범례 */}
            <div className="rounded-lg border border-gray-200 p-3 bg-white">
              <p className="text-xs font-semibold text-gray-500 mb-2">위험도 범례</p>
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
      </div>
    </div>
  );
}
