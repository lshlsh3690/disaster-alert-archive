"use client";

import { useMemo, useState } from "react";
import dynamic from "next/dynamic";
import Link from "next/link";
import { useDashboardSummary } from "@/lib/queries/useAlerts";
import { useTranslation } from "react-i18next";
import type { MapMode } from "@/components/map/KoreaMap25D";
import "./dashboard.css";

// 지도 높이: 1440×900 기준 800px. 화면이 낮으면 헤더(64px)와 위아래 여백을 뺀 만큼으로 줄인다.
const MAP_HEIGHT = "clamp(560px, calc(100vh - 100px), 800px)";

// SVG 좌표 데이터 + 집계 로직이 커서 홈 진입 시 초기 JS에서 분리한다.
const KoreaMap25D = dynamic(() => import("@/components/map/KoreaMap25D"), {
  ssr: false,
  loading: () => <div className="dash-map-placeholder" style={{ height: MAP_HEIGHT }} />,
});

const MAP_MODES: MapMode[] = ["sido", "sigungu"];

// 서버(SSR)와 브라우저의 시간대가 달라도 같은 문자열이 나오게 한국 시간으로 고정한다(하이드레이션 불일치 방지)
const KST_PARTS = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});
function kstParts(ms: number) {
  const p = Object.fromEntries(KST_PARTS.formatToParts(new Date(ms)).map((x) => [x.type, x.value]));
  return { date: `${p.year}-${p.month}-${p.day}`, stamp: `${p.year}.${p.month}.${p.day} ${p.hour}:${p.minute}` };
}

export default function HomeClient() {
  const { t } = useTranslation();
  const { data, dataUpdatedAt } = useDashboardSummary();
  const [mode, setMode] = useState<MapMode>("sigungu");

  const { date: today, stamp } = useMemo(() => kstParts(dataUpdatedAt || Date.now()), [dataUpdatedAt]);
  const segLabels: Record<MapMode, string> = { sido: t("dashboard.segSido"), sigungu: t("dashboard.segSigungu") };
  const unit = t("dashboard.count");

  return (
    <main className="dash">
      <div className="dash-stage">
        <div className="dash-map">
          <KoreaMap25D height={MAP_HEIGHT} mode={mode} onModeChange={setMode} showModeToggle={false} showRegionLabels />
        </div>

        <header className="dash-title">
          <p className="dash-eyebrow">{t("nav.dashboard")}</p>
          <h1>{t("dashboard.mapTitle")}</h1>
          <p className="dash-summary">
            {t("dashboard.asOf", { time: stamp })}
            {" · "}
            {/* 건수를 누르면 기존 KPI 카드처럼 오늘 날짜로 좁힌 목록으로 간다 */}
            <Link
              href={{ pathname: "/alerts", query: { source: "OFFICIAL", startDate: today, endDate: today } } as never}
              prefetch={false}
            >
              {t("dashboard.summaryAlerts")}{" "}
              <b>
                {data?.todayOfficialCount ?? 0}
                {unit}
              </b>
            </Link>
            {" · "}
            <Link
              href={{ pathname: "/alerts", query: { source: "USER", startDate: today, endDate: today } } as never}
              prefetch={false}
            >
              {t("dashboard.summaryUserReports")}{" "}
              <b>
                {data?.todayUserCount ?? 0}
                {unit}
              </b>
            </Link>
          </p>
          <div className="krds-segment dash-seg" role="radiogroup" aria-label={t("dashboard.mapViewLabel")}>
            {MAP_MODES.map((m) => (
              <button key={m} type="button" role="radio" aria-checked={mode === m} onClick={() => setMode(m)}>
                {segLabels[m]}
              </button>
            ))}
          </div>
        </header>
      </div>
    </main>
  );
}
