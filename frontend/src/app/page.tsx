"use client";

import { useMemo } from "react";
import Link from "next/link";
import LatestAlertsSection from "./LatestAlertsSection";
import { useDashboardSummary, useSidoStats } from "@/lib/queries/useAlerts";
import { useLatestComments } from "@/lib/queries/useComments";
import { useTranslation } from "react-i18next";
import { groupToMetros, Metro } from "@/ui/metros";
import KoreaMap25D from "@/components/map/KoreaMap25D";
import "./dashboard.css";

const clean = (value: string) => value.replace(/[\p{Extended_Pictographic}️]/gu, "").trim();

export default function Home() {
  const { t } = useTranslation();
  const { data } = useDashboardSummary();
  const latestComments = useLatestComments(5);

  const today = useMemo(() => {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
  }, []);

  const { data: sidoData } = useSidoStats({ startDate: today, endDate: today });
  const topRegions = useMemo(() => {
    const ranked = (Object.entries(groupToMetros(sidoData ?? [])) as [Metro, number][])
      .filter(([, count]) => count > 0)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 3);
    const max = ranked[0]?.[1] ?? 1;
    return ranked.map(([name, count]) => ({ name, count, ratio: (count / max) * 100 }));
  }, [sidoData]);

  const localRegionName = (name: string) => t(`metros.${name}`, { defaultValue: name });

  return (
    <main className="safety-dashboard">
      <div className="dashboard-wrap">
        <div className="dashboard-layout">
          <section className="dashboard-sidebar">
            <header className="dashboard-heading">
              <div>
                <p className="dashboard-label">
                  <span></span>
                  {t("nav.dashboard")}
                </p>
                <h1>{t("dashboard.overviewTitle")}</h1>
                <p className="dashboard-description">{t("home.subtitle")}</p>
              </div>
            </header>

            <div className="stat-grid">
              <Link
                className="stat-card stat-card--coral"
                href={{ pathname: "/alerts", query: { source: "OFFICIAL", startDate: today, endDate: today } } as never}
              >
                <span className="stat-card__icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24">
                    <path d="M12 3a7 7 0 0 0-7 7v4l-2 3h18l-2-3v-4a7 7 0 0 0-7-7Zm0 19a3 3 0 0 0 2.83-2h-5.66A3 3 0 0 0 12 22Z" />
                  </svg>
                </span>
                <strong>{data?.todayOfficialCount ?? 0}</strong>
                <p>{t("dashboard.todayAlerts")}</p>
              </Link>
              <Link
                className="stat-card stat-card--blue"
                href={{ pathname: "/alerts", query: { source: "USER", startDate: today, endDate: today } } as never}
              >
                <span className="stat-card__icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24">
                    <path d="M4 4h16v12H7l-3 3V4Zm4 4v2h8V8H8Zm0 4v2h5v-2H8Z" />
                  </svg>
                </span>
                <strong>{data?.todayUserCount ?? 0}</strong>
                <p>{t("dashboard.todayUserReports")}</p>
              </Link>
              <Link className="stat-card stat-card--green" href={{ pathname: "/alerts", query: { source: "USER" } } as never}>
                <span className="stat-card__icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24">
                    <path d="M4 19h16v2H4v-2Zm2-2V9h3v8H6Zm5 0V3h3v14h-3Zm5 0v-5h3v5h-3Z" />
                  </svg>
                </span>
                <strong>{data?.totalUserCount ?? 0}</strong>
                <p>{t("dashboard.totalUserReports")}</p>
              </Link>
            </div>

            <article className="feed-card">
              <header className="card-heading">
                <div>
                  <p>{t("nav.alerts")}</p>
                  <h2>{clean(t("dashboard.latestAlerts"))}</h2>
                </div>
                <Link href="/alerts" className="card-link">
                  {t("dashboard.viewAll")}
                </Link>
              </header>
              <div className="alert-feed">
                <LatestAlertsSection limit={5} />
              </div>
            </article>

            <article className="region-card">
              <header className="card-heading card-heading--compact">
                <div>
                  <p>{t("nav.stats")}</p>
                  <h2>{t("dashboard.topRegions")}</h2>
                </div>
              </header>
              {topRegions.length ? (
                <div className="region-list">
                  {topRegions.map((region, index) => (
                    <Link
                      key={region.name}
                      href={`/alerts?sido=${encodeURIComponent(region.name)}&startDate=${today}&endDate=${today}#list`}
                      className="region-row"
                    >
                      <span className="region-rank">{String(index + 1).padStart(2, "0")}</span>
                      <span className="region-name">{localRegionName(region.name)}</span>
                      <span className="region-bar">
                        <i style={{ width: `${region.ratio}%` }}></i>
                      </span>
                      <strong>
                        {region.count}
                        {t("dashboard.count")}
                      </strong>
                    </Link>
                  ))}
                </div>
              ) : (
                <div className="empty-copy">{t("dashboard.noRegionalAlerts")}</div>
              )}
            </article>
          </section>

          <section className="map-card">
            <header className="map-heading">
              <div>
                <p>{t("nav.dashboard")}</p>
                <h2>{clean(t("dashboard.alertMap"))}</h2>
              </div>
              <div className="map-heading__actions">
                <span className="live-status">
                  <i></i>LIVE
                </span>
              </div>
            </header>
            <div className="map-surface">
              <div className="map-decoration" aria-hidden="true"></div>
              <KoreaMap25D height="clamp(650px, calc(100vh - 96px), 920px)" />
            </div>
          </section>
        </div>
      </div>

      {/* 커뮤니티 최신 댓글 (Vue 레퍼런스에는 없는 Next 전용 위젯, 톤만 디자인 시스템에 맞춤) */}
      <div className="dashboard-wrap" style={{ padding: "16px 20px 32px" }}>
        <article
          style={{
            border: "1px solid var(--line)",
            borderRadius: "var(--radius-panel-card)",
            background: "var(--surface)",
            boxShadow: "0 10px 30px rgba(28,39,60,0.04)",
          }}
        >
          <header className="card-heading">
            <div>
              <p>{t("nav.community")}</p>
              <h2>{clean(t("dashboard.latestComments"))}</h2>
            </div>
            <Link href="/community" className="card-link">
              {t("dashboard.viewAll")}
            </Link>
          </header>
          <div style={{ padding: "8px 20px 18px" }} className="text-sm text-[var(--text-body)] space-y-2">
            {latestComments.isLoading && <div className="feed-state">{t("loading")}</div>}
            {latestComments.data?.map((c) => (
              <div key={c.id} className="flex items-start justify-between gap-3 py-1.5 border-b border-[var(--line)] last:border-0">
                <div className="flex-1 min-w-0">
                  <div className="text-[var(--ink)] line-clamp-1">{c.content}</div>
                  <div className="text-xs text-[var(--text-subtle)]">
                    {c.authorNickname ?? t("dashboard.anonymous")} · {new Date(c.createdAt).toLocaleString()}
                  </div>
                </div>
              </div>
            ))}
            {latestComments.data && latestComments.data.length === 0 && (
              <div className="feed-state">{t("dashboard.noComments")}</div>
            )}
          </div>
        </article>
      </div>
    </main>
  );
}
