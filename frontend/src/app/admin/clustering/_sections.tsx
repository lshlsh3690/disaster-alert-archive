"use client";

import { Fragment, useEffect, useState } from "react";
import { isAxiosError } from "axios";
import type { ClusteringAlertSummary, ClusteringDisagreementPair, DayStatus, DisagreementKind, HumanLabel, WrongSide } from "@/api/clusteringAdminApi";
import { useSaveDisagreementLabel } from "@/lib/mutations/useSaveDisagreementLabel";
import {
  useHardPairGrades,
  useClusteringDailyCheckAlerts,
  useClusteringDailyChecks,
  useClusteringDailyScores,
  useClusteringDisagreements,
  useClusteringJudgeVersions,
} from "@/lib/queries/useClusteringAdmin";
import { formatDateTime, formatNumber, formatRatio } from "./_format";

// 관리자 클러스터링 화면의 네 영역. 모두 읽기 전용이고 이미 기록된 값만 보여 준다(새 LLM 호출 없음).

const TABLE = "w-full text-sm border-collapse";
const TH = "px-3 py-2 text-left font-medium text-[var(--text-muted)] border-b border-[var(--line)] whitespace-nowrap";
const TD = "px-3 py-2 border-b border-[var(--line)] align-top whitespace-nowrap";

const STATUS_LABEL: Record<DayStatus, string> = {
  SCORED: "채점됨",
  NO_PAIRS: "판정 0쌍",
  NO_RECORD: "기록 없음",
};

const KIND_LABEL: Record<DisagreementKind, string> = {
  OVER_MERGED: "과병합",
  MISSED: "과소병합",
};

export function Section({ title, description, children }: { title: string; description?: string; children: React.ReactNode }) {
  return (
    <section className="bg-[var(--surface)] border border-[var(--line)] rounded-[var(--radius-compact)] p-4 space-y-3">
      <div>
        <h2 className="text-base font-semibold text-[var(--ink)]">{title}</h2>
        {description && <p className="text-xs text-[var(--text-muted)] mt-1">{description}</p>}
      </div>
      {children}
    </section>
  );
}

// 실패하면 서버가 준 이유(예: 조회 범위 오류)를 함께 보인다. 이유가 없으면 고정 문구만
function QueryState({ isLoading, error }: { isLoading: boolean; error: unknown }) {
  if (isLoading) return <p className="text-sm text-[var(--text-muted)]">불러오는 중…</p>;
  if (!error) return null;
  const message = isAxiosError(error) ? (error.response?.data as { message?: string } | undefined)?.message : undefined;
  return <p className="text-sm text-[var(--coral)]">불러오지 못했습니다.{message ? ` (${message})` : ""}</p>;
}

/** 일일 채점 — 날짜마다 점수와 같은 줄에 그날 판사 버전·일치율을 둔다(점수만 따로 보이지 않게). */
export function DailyScoresSection({ from, to }: { from: string; to: string }) {
  const { data, isLoading, error } = useClusteringDailyScores(from, to);
  return (
    <Section title="일일 채점" description="추정 비용은 프롬프트 캐시 할인을 빼지 않은 상한값입니다.">
      <QueryState isLoading={isLoading} error={error} />
      {data && (
        <div className="overflow-x-auto">
          <table className={TABLE}>
            <thead>
              <tr>
                <th className={TH}>날짜</th>
                <th className={TH}>상태</th>
                <th className={TH}>사건 알림</th>
                <th className={TH}>판정 쌍</th>
                <th className={TH}>맞게 묶은 비율</th>
                <th className={TH}>빠짐없이 묶은 비율</th>
                <th className={TH}>점수</th>
                <th className={TH}>과병합 / 과소병합</th>
                <th className={TH}>UNSURE / 형식 실패</th>
                <th className={TH}>판사</th>
                <th className={TH}>판사 일치율 (같은 / 다른)</th>
                <th className={TH}>토큰 (입력 / 출력)</th>
                <th className={TH}>추정 비용 (상한)</th>
              </tr>
            </thead>
            <tbody>
              {data.days.map((d) => (
                <tr key={d.date} className={d.status === "NO_RECORD" ? "text-[var(--text-muted)]" : ""}>
                  <td className={TD}>{d.date}</td>
                  <td className={TD}>{STATUS_LABEL[d.status]}</td>
                  {d.status === "NO_RECORD" ? (
                    <td className={TD} colSpan={11}>-</td>
                  ) : (
                    <>
                      <td className={TD}>{formatNumber(d.incidentCount)}</td>
                      <td className={TD}>{formatNumber(d.pairCount)}</td>
                      <td className={TD}>{formatRatio(d.purity)}</td>
                      <td className={TD}>{formatRatio(d.completeness)}</td>
                      <td className={TD}>{d.score === undefined ? "계산할 수 없음" : d.score.toFixed(1)}</td>
                      <td className={TD}>{formatNumber(d.overMerged)} / {formatNumber(d.missed)}</td>
                      <td className={TD}>{formatNumber(d.unsureCount)} / {formatNumber(d.formatFailureCount)}</td>
                      <td className={TD}>{d.judge ? `${d.judge.version}${d.judge.model ? ` · ${d.judge.model}` : ""}` : "-"}</td>
                      <td className={TD}>
                        {d.judge ? `${formatRatio(d.judge.sameAgreement)} / ${formatRatio(d.judge.diffAgreement)}` : "-"}
                      </td>
                      <td className={TD}>{formatNumber(d.promptTokens)} / {formatNumber(d.completionTokens)}</td>
                      <td className={TD}>{d.estimatedCostUsd === undefined ? "알 수 없음" : `$${d.estimatedCostUsd.toFixed(3)}`}</td>
                    </>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Section>
  );
}

function AlertCell({ alert }: { alert: ClusteringAlertSummary }) {
  if (alert.message === null) {
    return <span className="text-[var(--text-muted)]">#{alert.id} · 원문 없음</span>;
  }
  return (
    <div className="space-y-1 whitespace-normal min-w-[16rem] max-w-[28rem]">
      <div className="text-xs text-[var(--text-muted)]">
        #{alert.id} · {formatDateTime(alert.sentAt)} · {alert.type ?? "-"}
      </div>
      <div className="text-[var(--text-body)]">{alert.message}</div>
    </div>
  );
}

const HUMAN_LABEL_OPTIONS: { value: HumanLabel; label: string }[] = [
  { value: "SAME", label: "같은 사건" },
  { value: "DIFF", label: "다른 사건" },
  { value: "UNSURE", label: "모르겠음" },
];

const WRONG_SIDE_LABEL: Record<WrongSide, string> = {
  JUDGE: "판사가 틀림",
  SYSTEM: "시스템이 틀림",
};

// 쌍 하나의 사람 라벨 버튼 3개. 현재 라벨은 강조되고, 저장 중에는 이 쌍의 버튼만 막는다
function LabelButtons({ pair, saving, onSave }: { pair: ClusteringDisagreementPair; saving: boolean; onSave: (label: HumanLabel) => void }) {
  return (
    <div className="space-y-1">
      <div className="flex gap-1" role="group" aria-label={`#${pair.alertA.id}·#${pair.alertB.id} 사람 라벨`}>
        {HUMAN_LABEL_OPTIONS.map((o) => {
          const selected = pair.humanLabel === o.value;
          return (
            <button
              key={o.value}
              type="button"
              aria-pressed={selected}
              disabled={saving}
              onClick={() => onSave(o.value)}
              className={`px-2 py-0.5 text-xs rounded-[var(--radius-control)] border disabled:opacity-40 ${
                selected
                  ? "border-[var(--blue)] bg-[var(--blue)] text-white"
                  : "border-[var(--line)] text-[var(--text-body)] hover:bg-[var(--blue-soft)]"
              }`}
            >
              {o.label}
            </button>
          );
        })}
      </div>
      {pair.wrongSide && (
        <span className="inline-block rounded-[var(--radius-pill)] bg-[var(--coral-soft)] px-2 py-0.5 text-xs text-[var(--coral)]">
          {WRONG_SIDE_LABEL[pair.wrongSide]}
        </span>
      )}
    </div>
  );
}

/** 불일치 쌍 — 판사와 시스템이 다르게 본 쌍을 두 알림 원문과 함께 보여 주고, 쌍마다 사람 라벨을 매긴다. */
export function DisagreementsSection({ from, to }: { from: string; to: string }) {
  const [kind, setKind] = useState<DisagreementKind | null>(null);
  const [unlabeledOnly, setUnlabeledOnly] = useState(false);
  const [page, setPage] = useState(0);
  const { data, isLoading, error, isPlaceholderData } = useClusteringDisagreements({ from, to, kind, page, size: 20, unlabeledOnly });
  const save = useSaveDisagreementLabel();
  // 저장은 한 번에 하나만 보낸다(저장 중에는 모든 줄의 라벨 버튼을 막음). mutate 호출마다 넘긴 콜백은 마지막 호출 것만
  // 실행돼, 동시에 여러 쌍을 보내면 앞 쌍의 실패가 사라진다. 실패 이유는 그 쌍 아래에만 보인다
  const [failed, setFailed] = useState<{ key: string; message: string } | null>(null);

  // 마지막 페이지의 마지막 쌍에 라벨을 매겨 "라벨 없는 쌍만" 목록이 줄면, 없는 페이지에 머물지 않게 마지막 페이지로 당긴다.
  // 다른 경우는 저장 뒤 지금 페이지를 다시 불러오는 것(invalidate)만으로 당겨진 쌍이 보여 건너뛰는 쌍이 없다
  useEffect(() => {
    if (data && !isPlaceholderData && data.totalPages > 0 && page >= data.totalPages) {
      setPage(data.totalPages - 1);
    }
  }, [data, isPlaceholderData, page]);

  const changeKind = (value: string) => {
    setKind(value === "" ? null : (value as DisagreementKind));
    setPage(0);
  };

  const pairKey = (p: ClusteringDisagreementPair) => `${p.date}-${p.alertA.id}-${p.alertB.id}`;

  const saveLabel = (p: ClusteringDisagreementPair, label: HumanLabel) => {
    const key = pairKey(p);
    setFailed(null);
    save.mutate(
      { alertA: p.alertA.id, alertB: p.alertB.id, label },
      {
        onError: (e) => {
          const message = isAxiosError(e) ? (e.response?.data as { message?: string } | undefined)?.message : undefined;
          setFailed({ key, message: message ?? "저장하지 못했습니다." });
        },
      },
    );
  };

  return (
    <Section title="불일치 쌍" description="과병합 = 시스템은 묶었는데 판사는 다른 사건, 과소병합 = 판사는 같은 사건인데 시스템이 안 묶음.">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <label className="text-[var(--text-muted)]" htmlFor="disagreement-kind">종류</label>
        <select
          id="disagreement-kind"
          value={kind ?? ""}
          onChange={(e) => changeKind(e.target.value)}
          className="border border-[var(--line)] rounded-[var(--radius-control)] px-2 py-1 bg-[var(--surface)] text-[var(--text-body)]"
        >
          <option value="">전체</option>
          <option value="OVER_MERGED">과병합</option>
          <option value="MISSED">과소병합</option>
        </select>
        <label className="flex items-center gap-1 text-[var(--text-body)]">
          <input
            type="checkbox"
            checked={unlabeledOnly}
            onChange={(e) => { setUnlabeledOnly(e.target.checked); setPage(0); }}
          />
          라벨 없는 쌍만
        </label>
        {data && <span className="text-[var(--text-muted)]">총 {formatNumber(data.totalElements)}쌍</span>}
      </div>
      <QueryState isLoading={isLoading} error={error} />
      {data && data.pairs.length === 0 && (
        <p className="text-sm text-[var(--text-muted)]">{unlabeledOnly ? "이 범위에 라벨 없는 불일치 쌍이 없습니다." : "이 범위에 불일치 쌍이 없습니다."}</p>
      )}
      {data && data.pairs.length > 0 && (
        <div className="overflow-x-auto">
          <table className={TABLE}>
            <thead>
              <tr>
                <th className={TH}>날짜</th>
                <th className={TH}>종류</th>
                <th className={TH}>판사</th>
                <th className={TH}>알림 A</th>
                <th className={TH}>알림 B</th>
                <th className={TH}>당시 이벤트 (A / B)</th>
                <th className={TH}>사람 라벨</th>
              </tr>
            </thead>
            <tbody>
              {data.pairs.map((p) => (
                <tr key={pairKey(p)}>
                  <td className={TD}>{p.date}</td>
                  <td className={TD}>{KIND_LABEL[p.kind]}</td>
                  <td className={TD}>{p.judgeVerdict}</td>
                  <td className={TD}><AlertCell alert={p.alertA} /></td>
                  <td className={TD}><AlertCell alert={p.alertB} /></td>
                  <td className={TD}>{p.eventA ?? "미배정"} / {p.eventB ?? "미배정"}</td>
                  <td className={TD}>
                    <LabelButtons pair={p} saving={save.isPending} onSave={(label) => saveLabel(p, label)} />
                    {failed?.key === pairKey(p) && <p className="mt-1 text-xs text-[var(--coral)] whitespace-normal">{failed.message}</p>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && data.totalPages > 1 && (
        <div className="flex items-center gap-2 text-sm">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="px-3 py-1 border border-[var(--line)] rounded-[var(--radius-control)] disabled:opacity-40"
          >
            이전
          </button>
          <span className="text-[var(--text-muted)]">{page + 1} / {data.totalPages}</span>
          <button
            onClick={() => setPage((p) => p + 1)}
            // 이전 응답(placeholder)의 totalPages 로 넘어가면 새 조건에 없는 빈 페이지를 요청하게 된다
            disabled={isPlaceholderData || page + 1 >= data.totalPages}
            className="px-3 py-1 border border-[var(--line)] rounded-[var(--radius-control)] disabled:opacity-40"
          >
            다음
          </button>
        </div>
      )}
    </Section>
  );
}

/** 구조 검사(매일 00:30) — 미배정·임베딩 없음이 있는 날을 강조한다. */
export function DailyChecksSection({ from, to }: { from: string; to: string }) {
  const { data, isLoading, error } = useClusteringDailyChecks(from, to);
  // 펼친 날짜 하나. 강조된(문제 있는) 날만 펼칠 수 있고, 다시 누르면 접힌다
  const [openDate, setOpenDate] = useState<string | null>(null);
  return (
    <Section title="구조 검사 (매일 00:30)" description="미배정이나 임베딩 없음이 있는 날은 강조되고, 누르면 그날의 문제 알림이 아래에 펼쳐집니다. 검사가 돌지 않은 날은 '기록 없음'입니다.">
      <QueryState isLoading={isLoading} error={error} />
      {data && (
        <div className="overflow-x-auto">
          <table className={TABLE}>
            <thead>
              <tr>
                <th className={TH}>날짜</th>
                <th className={TH}>사건 알림</th>
                <th className={TH}>미배정</th>
                <th className={TH}>임베딩 없음</th>
              </tr>
            </thead>
            <tbody>
              {data.days.map((d) => {
                const expandable = d.recorded && !!d.attention;
                const open = expandable && openDate === d.date;
                return (
                  <Fragment key={d.date}>
                    <tr
                      className={`${!d.recorded ? "text-[var(--text-muted)]" : d.attention ? "bg-[var(--coral-soft)] font-medium" : ""} ${expandable ? "cursor-pointer hover:brightness-95" : ""}`}
                      onClick={expandable ? () => setOpenDate(open ? null : d.date) : undefined}
                    >
                      <td className={TD}>
                        {expandable ? (
                          // 키보드·화면 읽기 프로그램은 이 버튼으로 펼친다(행 클릭은 마우스 편의). 클릭이 행으로 번져 두 번 토글되지 않게 막는다
                          <button
                            type="button"
                            aria-expanded={open}
                            aria-controls={`daily-check-alerts-${d.date}`}
                            onClick={(e) => { e.stopPropagation(); setOpenDate(open ? null : d.date); }}
                            className="font-medium hover:underline"
                          >
                            <span aria-hidden="true">{open ? "▾ " : "▸ "}</span>
                            {d.date}
                          </button>
                        ) : (
                          d.date
                        )}
                      </td>
                      {d.recorded ? (
                        <>
                          <td className={TD}>{formatNumber(d.incidentCount)}</td>
                          <td className={TD}>{formatNumber(d.unassignedCount)}</td>
                          <td className={TD}>{formatNumber(d.noEmbeddingCount)}</td>
                        </>
                      ) : (
                        <td className={TD} colSpan={3}>기록 없음</td>
                      )}
                    </tr>
                    {open && (
                      <tr id={`daily-check-alerts-${d.date}`}>
                        <td colSpan={4} className="px-3 py-3 border-b border-[var(--line)] bg-[var(--canvas)]">
                          <DailyCheckAlerts date={d.date} />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </Section>
  );
}

// 구조 검사 행 아래 펼침 — 그날의 문제 알림만(미배정·임베딩 없음). 이벤트 번호는 사건 상세를 새 탭으로 연다
function DailyCheckAlerts({ date }: { date: string }) {
  const { data, isLoading, error } = useClusteringDailyCheckAlerts(date);
  return (
    <div className="space-y-2 whitespace-normal">
      <p className="text-xs text-[var(--text-muted)]">
        지금 조회한 목록입니다. 위 숫자는 검사 당시 값이라, 그 뒤 배정되거나 임베딩이 채워진 알림은 여기에 없습니다.
      </p>
      <QueryState isLoading={isLoading} error={error} />
      {data && data.alerts.length === 0 && (
        <p className="text-sm text-[var(--text-muted)]">지금은 문제 알림이 없습니다.</p>
      )}
      {data && data.alerts.length > 0 && (
        <ul className="space-y-2">
          {data.alerts.map((a) => (
            <li key={a.id} className="rounded-[var(--radius-compact)] border border-[var(--line)] bg-[var(--surface)] p-3 space-y-1">
              <div className="flex flex-wrap items-center gap-2 text-xs text-[var(--text-muted)]">
                <span>#{a.id}</span>
                <span>{formatDateTime(a.sentAt)}</span>
                <span>{a.type ?? "-"}</span>
                {a.unassigned && <span className="rounded-[var(--radius-pill)] bg-[var(--coral-soft)] px-2 py-0.5 text-[var(--coral)]">미배정</span>}
                {a.noEmbedding && <span className="rounded-[var(--radius-pill)] bg-[var(--blue-soft)] px-2 py-0.5 text-[var(--blue)]">임베딩 없음</span>}
                {a.eventId !== null ? (
                  <a href={`/events/${a.eventId}`} target="_blank" rel="noopener noreferrer" className="text-[var(--blue)] hover:underline">
                    사건 #{a.eventId}
                  </a>
                ) : (
                  <span>사건 없음</span>
                )}
                {a.mergeMethod && <span>묶인 방식 {a.mergeMethod}</span>}
              </div>
              {a.message === null ? (
                <div className="text-sm text-[var(--text-muted)]">원문 없음</div>
              ) : (
                // 공백 없는 긴 URL 이 칸 최소 폭을 늘려 표 전체가 옆으로 밀리지 않게 끊는다
                <div className="text-sm text-[var(--text-body)] [overflow-wrap:anywhere]">{a.message}</div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** 어려운 쌍 판사 성적 — 불일치 쌍에 매긴 사람 라벨로 판사·시스템 중 누가 맞았는지(판사 버전별). */
export function HardPairGradesSection() {
  const { data, isLoading, error } = useHardPairGrades();
  return (
    <Section
      title="어려운 쌍 판사 성적 (불일치 쌍 사람 라벨 기준)"
      description="판사와 시스템이 갈린 어려운 쌍만 모은 값이라 위 272쌍 일치율과 다른 숫자입니다. 모르겠음은 분모에서 뺍니다."
    >
      <QueryState isLoading={isLoading} error={error} />
      {data && data.labeledPairs === 0 && <p className="text-sm text-[var(--text-muted)]">아직 라벨 없음</p>}
      {data && data.labeledPairs > 0 && (
        <div className="space-y-2">
          <p className="text-sm text-[var(--text-muted)]">판정 {formatNumber(data.labeledPairs)}건(같은 쌍이 여러 날 불일치면 날짜마다 셉니다)</p>
          <div className="overflow-x-auto">
            <table className={TABLE}>
              <thead>
                <tr>
                  <th className={TH}>판사 버전</th>
                  <th className={TH}>모델</th>
                  <th className={TH}>판사 맞음</th>
                  <th className={TH}>시스템 맞음</th>
                  <th className={TH}>모르겠음</th>
                </tr>
              </thead>
              <tbody>
                {data.versions.map((v) => (
                  <tr key={v.version}>
                    <td className={TD}>{v.version}</td>
                    <td className={TD}>{v.model ?? "-"}</td>
                    <td className={TD}>{formatRatio(v.judgeRight)}</td>
                    <td className={TD}>{formatRatio(v.systemRight)}</td>
                    <td className={TD}>{formatNumber(v.unsureCount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </Section>
  );
}

/** 판사 버전 이력 — 버전마다 모델·272쌍 일치율과 그 버전으로 채점한 기간. */
export function JudgeVersionsSection() {
  const { data, isLoading, error } = useClusteringJudgeVersions();
  return (
    <Section title="판사 버전 이력" description="일치율은 사람 라벨 272쌍과 판사 판정을 견준 값입니다.">
      <QueryState isLoading={isLoading} error={error} />
      {data && (
        <div className="overflow-x-auto">
          <table className={TABLE}>
            <thead>
              <tr>
                <th className={TH}>버전</th>
                <th className={TH}>모델</th>
                <th className={TH}>측정일</th>
                <th className={TH}>같은 사건 일치율</th>
                <th className={TH}>다른 사건 일치율</th>
                <th className={TH}>채점 기간</th>
              </tr>
            </thead>
            <tbody>
              {data.versions.map((v) => (
                <tr key={v.version}>
                  <td className={TD}>{v.version}</td>
                  <td className={TD}>{v.model ?? "-"}</td>
                  <td className={TD}>{v.measuredOn ?? "-"}</td>
                  <td className={TD}>{formatRatio(v.sameAgreement)}</td>
                  <td className={TD}>{formatRatio(v.diffAgreement)}</td>
                  <td className={TD}>
                    {v.firstScoredDate
                      ? `${v.firstScoredDate} ~ ${v.lastScoredDate} (${v.scoredDays}일)`
                      : <span className="text-[var(--text-muted)]">채점 기록 없음</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Section>
  );
}
