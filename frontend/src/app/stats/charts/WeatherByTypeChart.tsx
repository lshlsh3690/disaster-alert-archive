"use client";

import { useState, useRef, useEffect } from "react";
import {
  ComposedChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer,
} from "recharts";
import type { WeatherTypeStat } from "@/types/alerts";
import { STACKED_COLORS } from "../_constants";
import { EmptyChart } from "../_charts";
import { LoadingDonut } from "@/components/ui/LoadingDonut";
import { useTranslation } from "react-i18next";
import { useLanguageStore } from "@/store/languageStore";
import { LANG_LOCALE, TT_BOX, TT_LABEL, TTProps } from "./common";
import { getAggMode, getAggKey, fmtKey, aggModeLabel } from "./weatherHelpers";

/**
 * WeatherByTypeChart
 *
 * 재난 유형별 일별 누적 막대 + 평균기온 꺾은선.
 * 날짜 수가 많으면 일별→주별→월별로 자동 집계해 막대 수를 제한합니다(getAggMode).
 * 1.2초 이상 호버 시 툴팁이 고정(pin)되는 인터랙션이 있습니다(LoadingDonut → 고정 팝업).
 */
export function WeatherByTypeChart({ data }: { data: WeatherTypeStat[] }) {
  const { t } = useTranslation();
  const locale = LANG_LOCALE[useLanguageStore((s) => s.language)] ?? "ko-KR";
  const translateType = (type: string) => t(`disasterTypes.${type}`, { defaultValue: type });
  // 툴팁 핀 로딩 진행률 (0~100)
  const [progress, setProgress] = useState(0);
  // 툴팁 고정 여부
  const [pinned, setPinned] = useState(false);
  // 고정된 시점의 툴팁 데이터 스냅샷
  const [pinnedSnap, setPinnedSnap] = useState<{ label: string; payload: TTProps["payload"] } | null>(null);
  // 1.2초 카운트다운 인터벌 ref
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // 마지막으로 호버된 툴팁 데이터 (핀 시점에 스냅샷으로 복사됨)
  const lastPayloadRef = useRef<{ label: string; payload: TTProps["payload"] } | null>(null);
  // 마우스가 차트 컨테이너 안에 있는지 여부 (X 버튼 클릭 후 재시작 판단용)
  const isHoveringRef = useRef(false);
  useEffect(() => () => { if (intervalRef.current) clearInterval(intervalRef.current); }, []);

  // 1.2초 타이머 실행 (50ms 간격으로 progress 업데이트, 완료 시 핀 고정)
  const runTimer = () => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    let elapsed = 0;
    intervalRef.current = setInterval(() => {
      elapsed += 50;
      const pct = Math.min(100, (elapsed / 1200) * 100);
      setProgress(pct);
      if (pct >= 100) {
        clearInterval(intervalRef.current!);
        // 차트 컨테이너에 들어오자마자(아직 막대 위를 지나가기 전) 타이머가 끝나면
        // lastPayloadRef가 비어있을 수 있다 — 그 상태로 고정하면 pinnedSnap이 null인 채
        // pinned=true가 되어 툴팁이 영영 안 뜨는 상태로 멈춘다. 데이터가 있을 때만 고정한다.
        if (lastPayloadRef.current) {
          setPinnedSnap(lastPayloadRef.current);
          setPinned(true);
        } else {
          setProgress(0);
        }
      }
    }, 50);
  };
  // 아직 고정되지 않은 경우에만 타이머 시작
  const startTimer = () => { if (!pinned) runTimer(); };
  // 마우스가 나가면 타이머 중단, 미고정 상태면 progress 초기화
  const stopTimer = () => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    if (!pinned) setProgress(0);
  };
  // X 버튼: 고정 해제 후 마우스가 안에 있으면 타이머 즉시 재시작
  const handleClose = () => {
    setPinned(false);
    setPinnedSnap(null);
    setProgress(0);
    if (isHoveringRef.current) runTimer();
  };

  if (data.length === 0) return <EmptyChart />;

  // 날짜별로 쪼개진 data를 유형 단위로 합산 → 상위 6개만 차트에 표시
  const typeTotals = new Map<string, number>();
  data.forEach(d => typeTotals.set(d.type ?? t("statsPage.other"), (typeTotals.get(d.type ?? t("statsPage.other")) ?? 0) + d.count));
  const types = [...typeTotals.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6).map(([type]) => type);

  // 중복 제거한 날짜 목록 → 집계 단위 결정
  const allDates = [...new Set(data.map(d => d.date))].sort();
  const mode = getAggMode(allDates.length);

  // 날짜별 평균 기온 (날짜 중복 방지: 첫 번째 값만 기록)
  const dateTemp = new Map<string, number | null>();
  data.forEach(d => { if (!dateTemp.has(d.date)) dateTemp.set(d.date, d.avgTemp ?? null); });

  // 집계 키별 유형 건수 (key → type → count)
  const keyTypeCount = new Map<string, Map<string, number>>();
  // 집계 키에 속한 원본 날짜 목록 (기온 평균 계산에 사용)
  const keyDates = new Map<string, Set<string>>();
  data.forEach(d => {
    const key = getAggKey(d.date, mode);
    const type = d.type ?? t("statsPage.other");
    if (!keyTypeCount.has(key)) keyTypeCount.set(key, new Map());
    keyTypeCount.get(key)!.set(type, (keyTypeCount.get(key)!.get(type) ?? 0) + d.count);
    if (!keyDates.has(key)) keyDates.set(key, new Set());
    keyDates.get(key)!.add(d.date);
  });

  // 정렬된 집계 키 목록
  const keys = [...keyTypeCount.keys()].sort();
  // X축 레이블이 겹치지 않도록 표시 간격 (최대 6개 레이블 기준)
  const labelStride = Math.max(1, Math.floor(keys.length / 6));

  // Recharts에 넘길 피벗 데이터: 각 행이 하나의 집계 기간, 열이 유형별 건수 + 평균기온
  type PivotRow = Record<string, number | null | string>;
  const pivoted: PivotRow[] = keys.map(key => {
    const typeMap = keyTypeCount.get(key)!;
    // 해당 집계 기간에 속한 날짜들의 기온 평균
    const temps = [...keyDates.get(key)!].map(d => dateTemp.get(d) ?? null).filter((v): v is number => v != null);
    const obj: PivotRow = { date: fmtKey(key, mode, t("statsPage.monthSuffix")) };
    types.forEach(type => { obj[type] = typeMap.get(type) ?? 0; });
    obj._avgTemp = temps.length > 0 ? Math.round(temps.reduce((a, b) => a + b, 0) / temps.length * 10) / 10 : null;
    return obj;
  });

  const TooltipContent = ({ active, payload, label }: TTProps) => {
    if (active && payload?.length && label) lastPayloadRef.current = { label, payload };
    if (!active || !payload?.length || pinned) return null;
    const tempEntry = payload.find(p => p.dataKey === "_avgTemp");
    const bars = payload.filter(p => p.dataKey !== "_avgTemp" && (p.value ?? 0) > 0);
    return (
      <div style={{ ...TT_BOX, position: "relative", paddingRight: 28, minWidth: 160 }}>
        <div style={{ position: "absolute", top: 6, right: 6 }}><LoadingDonut progress={progress} /></div>
        <p style={TT_LABEL}>{label}</p>
        <div style={{ overflowY: "hidden" }}>
          {bars.map((p, i) => (
            <div key={i} style={{ display: "flex", alignItems: "center", gap: 5, marginBottom: 2 }}>
              <span style={{ width: 8, height: 8, borderRadius: 2, background: STACKED_COLORS[types.indexOf(String(p.dataKey)) % STACKED_COLORS.length], display: "inline-block", flexShrink: 0 }} />
              <span style={{ color: "#e2e8f0", fontSize: 11 }}>{translateType(String(p.dataKey))}: {(p.value ?? 0).toLocaleString(locale)}{t("statsPage.countUnit")}</span>
            </div>
          ))}
        </div>
        {tempEntry?.value != null && <p style={{ ...TT_LABEL, marginTop: 4 }}>{t("statsPage.weatherChart.temperatureLabel")} {tempEntry.value}°C</p>}
      </div>
    );
  };

  return (
    <div className="flex flex-col flex-1 min-h-0 gap-1 relative"
      onMouseEnter={() => { isHoveringRef.current = true; startTimer(); }}
      onMouseLeave={() => { isHoveringRef.current = false; stopTimer(); }}>
      {mode !== "daily" && <p className="text-[10px] text-gray-400 text-right pr-1">{aggModeLabel(mode, t)}</p>}

      {pinned && pinnedSnap && (
        <div style={{ position: "absolute", top: 8, right: 8, zIndex: 20, ...TT_BOX, minWidth: 180, maxWidth: 220, maxHeight: 260, display: "flex", flexDirection: "column" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 4, flexShrink: 0 }}>
            <p style={{ ...TT_LABEL, margin: 0 }}>{pinnedSnap.label}</p>
            <button type="button" onClick={handleClose} aria-label={t("notificationBanner.close")}
              style={{ background: "none", border: "none", color: "#94a3b8", cursor: "pointer", fontSize: 14, lineHeight: 1, padding: 0, marginLeft: 12 }}>
              ✕
            </button>
          </div>
          <div style={{ overflowY: "hidden", flex: 1 }}>
            {pinnedSnap.payload?.filter(p => p.dataKey !== "_avgTemp" && (p.value ?? 0) > 0).map((p, i) => (
              <div key={i} style={{ display: "flex", alignItems: "center", gap: 5, marginBottom: 2 }}>
                <span style={{ width: 8, height: 8, borderRadius: 2, background: STACKED_COLORS[types.indexOf(String(p.dataKey)) % STACKED_COLORS.length], display: "inline-block", flexShrink: 0 }} />
                <span style={{ color: "#e2e8f0", fontSize: 11 }}>{translateType(String(p.dataKey))}: {(p.value ?? 0).toLocaleString(locale)}{t("statsPage.countUnit")}</span>
              </div>
            ))}
          </div>
          {pinnedSnap.payload?.find(p => p.dataKey === "_avgTemp")?.value != null && (
            <p style={{ ...TT_LABEL, marginTop: 4, flexShrink: 0 }}>{t("statsPage.weatherChart.temperatureLabel")} {pinnedSnap.payload.find(p => p.dataKey === "_avgTemp")?.value}°C</p>
          )}
        </div>
      )}

      <div className="flex-1 min-h-0 relative">
        <ResponsiveContainer width="100%" height="100%" className="absolute inset-0">
          <ComposedChart data={pivoted} margin={{ top: 8, right: 36, bottom: 4, left: 4 }}>
            <CartesianGrid vertical={false} stroke="#f3f4f6" />
            <XAxis dataKey="date" axisLine={false} tickLine={false}
              tick={{ fontSize: 9, fill: "#9ca3af" }} interval={labelStride - 1} />
            <YAxis yAxisId="cnt" axisLine={false} tickLine={false} tick={{ fontSize: 9, fill: "#9ca3af" }} width={32} />
            <Tooltip content={<TooltipContent />} wrapperStyle={{ zIndex: 30 }} />
            <Legend wrapperStyle={{ fontSize: 10 }} formatter={(v: string) => translateType(v)} />
            {types.map((type, i) => (
              <Bar key={type} yAxisId="cnt" dataKey={type} stackId="s"
                fill={STACKED_COLORS[i % STACKED_COLORS.length]} maxBarSize={24}
                radius={i === types.length - 1 ? [2, 2, 0, 0] : [0, 0, 0, 0]} isAnimationActive={false} />
            ))}
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
