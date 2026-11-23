"use client";

import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { isAxiosError } from "axios";
import type { EvalLabelPair, EvalLabelRelation, HumanLabel } from "@/api/clusteringAdminApi";
import { useUpdateEvalLabel } from "@/lib/mutations/useUpdateEvalLabel";
import { useEvalLabelDatasets, useEvalLabelHistory, useEvalLabels } from "@/lib/queries/useClusteringAdmin";
import { formatDateTime, formatNumber } from "./_format";
import { AlertCell, HUMAN_LABEL_OPTIONS, QueryState, Section } from "./_sections";

// 평가 셋 라벨 — 클러스터링 평가의 정답(사람 라벨)을 셋별로 보고 고친다. 고칠 때는 사유를 받아 이력에 남긴다.
// 평가 도구(--eval.dataset)가 이 값을 읽으므로, 여기서 고친 라벨이 다음 평가의 정답이 된다.

const SELECT = "border border-[var(--line)] rounded-[var(--radius-control)] px-2 py-1 bg-[var(--surface)] text-[var(--text-body)]";
const PAGE_SIZE = 20;

// 이 표는 좌우 스크롤 없이 화면 너비에 다 담는다 — 칸 비율을 고정(table-fixed)하고, 모든 칸이 칸 안에서 줄바꿈한다.
// 검증 화면의 표(TABLE·TH·TD)는 줄바꿈을 막아 두어서 여기서는 따로 둔다
const TABLE = "w-full table-fixed text-sm border-collapse";
const TH = "px-2 py-2 text-left font-medium text-[var(--text-muted)] border-b border-[var(--line)] align-bottom";
const TD = "px-2 py-2 border-b border-[var(--line)] align-top [overflow-wrap:anywhere]";
// 짧은 칸은 고정 너비 + 줄바꿈 금지로 두고(좁은 화면에서 "1/0"·"보/기"처럼 글자가 쪼개지지 않게),
// 남는 너비는 너비를 정하지 않은 두 알림 칸이 반씩 나눠 갖는다. 순서: 순번·관계/간격·알림 A·알림 B·라벨·기준/수정·이력
const COLUMNS: (string | undefined)[] = ["3rem", "7rem", undefined, undefined, "7.5rem", "7rem", "4rem"];
const NOWRAP = "whitespace-nowrap [overflow-wrap:normal]";

const RELATION_LABEL: Record<EvalLabelRelation, string> = {
  SAME_SIGUNGU: "같은 시군구",
  ADJACENT_SIGUNGU: "이웃 시군구",
};

const LABEL_TEXT: Record<HumanLabel, string> = Object.fromEntries(
  HUMAN_LABEL_OPTIONS.map((o) => [o.value, o.label]),
) as Record<HumanLabel, string>;

/**
 * 두 알림의 발송 간격. 같은 사건인지 판단할 때 가장 먼저 보는 단서라 따로 보인다.
 * 24시간 미만은 "N시간 M분", 이상은 "N일 M시간". 한쪽 시각이 없으면 "-".
 */
export function formatGap(a: string | null, b: string | null): string {
  if (!a || !b) return "-";
  const minutes = Math.round(Math.abs(new Date(b).getTime() - new Date(a).getTime()) / 60000);
  if (Number.isNaN(minutes)) return "-";
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  if (days > 0) return `${days}일 ${hours}시간`;
  return `${hours}시간 ${minutes % 60}분`;
}

function errorMessage(e: unknown, fallback: string): string {
  const message = isAxiosError(e) ? (e.response?.data as { message?: string } | undefined)?.message : undefined;
  return message ?? fallback;
}

/** 평가 셋 라벨 영역. 셋을 고르면 그 셋의 라벨 쌍을 넣은 순서대로 보여 주고, 쌍마다 라벨을 사유와 함께 고친다. */
export function EvalLabelsSection() {
  const datasets = useEvalLabelDatasets();
  const [picked, setPicked] = useState<string | null>(null);
  const [label, setLabel] = useState<HumanLabel | null>(null);
  const [page, setPage] = useState(0);
  // 고른 셋이 없으면 첫 셋을 쓴다 — 셋이 하나뿐인 지금은 고르지 않아도 바로 보인다
  const dataset = picked ?? datasets.data?.datasets[0]?.name ?? null;
  const summary = datasets.data?.datasets.find((d) => d.name === dataset);
  const list = useEvalLabels(dataset ? { dataset, label, page, size: PAGE_SIZE } : null);

  // 필터 때문에 마지막 페이지가 비면(예: 모르겠음만 보다가 마지막 쌍을 고침) 있는 마지막 페이지로 당긴다
  useEffect(() => {
    if (list.data && !list.isPlaceholderData && list.data.totalPages > 0 && page >= list.data.totalPages) {
      setPage(list.data.totalPages - 1);
    }
  }, [list.data, list.isPlaceholderData, page]);

  const changeDataset = (value: string) => {
    setPicked(value);
    setPage(0);
  };
  const changeLabel = (value: string) => {
    setLabel(value === "" ? null : (value as HumanLabel));
    setPage(0);
  };

  return (
    <Section
      title="평가 셋 라벨"
      description="클러스터링 평가의 정답(사람 라벨)입니다. 라벨을 바꾸려면 사유를 적어야 하고, 바꿀 때마다 이력이 남습니다. 평가 도구(--eval.dataset)가 이 값을 읽습니다."
    >
      <QueryState isLoading={datasets.isLoading} error={datasets.error} />
      {datasets.data && datasets.data.datasets.length === 0 && (
        <p className="text-sm text-[var(--text-muted)]">
          평가 셋 라벨이 없습니다. backend/evaluation/datasets/pair_label_data_*.sql 을 이 DB 에 넣어 주세요.
        </p>
      )}
      {datasets.data && dataset && (
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <label className="text-[var(--text-muted)]" htmlFor="eval-dataset">평가 셋</label>
          <select id="eval-dataset" value={dataset} onChange={(e) => changeDataset(e.target.value)} className={SELECT}>
            {datasets.data.datasets.map((d) => (
              <option key={d.name} value={d.name}>
                {d.name} ({formatNumber(d.pairs)}쌍)
              </option>
            ))}
          </select>
          <label className="text-[var(--text-muted)]" htmlFor="eval-label-filter">라벨</label>
          <select id="eval-label-filter" value={label ?? ""} onChange={(e) => changeLabel(e.target.value)} className={SELECT}>
            <option value="">전체</option>
            {HUMAN_LABEL_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
          {summary && (
            <span className="text-[var(--text-muted)]">
              같은 사건 {formatNumber(summary.same)} · 다른 사건 {formatNumber(summary.diff)} · 모르겠음 {formatNumber(summary.unsure)}
              {" · "}마지막 수정 {summary.lastUpdatedAt ? formatDateTime(summary.lastUpdatedAt) : "없음"}
            </span>
          )}
        </div>
      )}
      {dataset && <QueryState isLoading={list.isLoading} error={list.error} />}
      {list.data && list.data.pairs.length === 0 && (
        <p className="text-sm text-[var(--text-muted)]">이 조건에 맞는 라벨 쌍이 없습니다.</p>
      )}
      {list.data && list.data.pairs.length > 0 && (
        <EvalLabelTable pairs={list.data.pairs} startIndex={list.data.page * list.data.size} />
      )}
      {list.data && list.data.totalPages > 1 && (
        <Pagination
          page={page}
          totalPages={list.data.totalPages}
          totalElements={list.data.totalElements}
          // 이전 응답(placeholder)의 totalPages 로 넘어가면 새 조건에 없는 빈 페이지를 요청하게 된다 — 불러오는 동안 이동을 막는다
          busy={list.isPlaceholderData}
          onChange={setPage}
        />
      )}
    </Section>
  );
}

const PAGE_BLOCK = 10;

/**
 * 가운데 정렬된 페이지 넘김: 이전 · 번호 10개 묶음 · 다음. 번호는 지금 페이지가 든 10개 묶음(1~10, 11~20 …)을 보인다.
 * 이전·다음은 한 페이지씩 움직이고, 묶음 끝에서 넘어가면 다음 묶음 번호가 보인다. page 는 0부터, 화면 번호는 1부터.
 */
function Pagination({ page, totalPages, totalElements, busy, onChange }: {
  page: number;
  totalPages: number;
  totalElements: number;
  busy: boolean;
  onChange: (page: number) => void;
}) {
  const blockStart = Math.floor(page / PAGE_BLOCK) * PAGE_BLOCK;
  const pages = Array.from({ length: Math.min(PAGE_BLOCK, totalPages - blockStart) }, (_, i) => blockStart + i);
  // 이전·다음은 테두리 있는 버튼(화살표 + 글자), 번호는 테두리 없는 칸(지금 페이지만 채움)으로 모양을 나눈다
  const step = "inline-flex items-center gap-1 px-3 py-1 text-sm rounded-[var(--radius-control)] border border-[var(--line)] "
    + "text-[var(--text-body)] hover:bg-[var(--blue-soft)] disabled:opacity-40 disabled:hover:bg-transparent";
  const number = "min-w-[2rem] h-8 px-1.5 text-sm rounded-[var(--radius-control)] disabled:opacity-40";
  return (
    <nav className="flex flex-col items-center gap-1" aria-label="평가 셋 라벨 페이지">
      <div className="flex flex-wrap items-center justify-center gap-2">
        <button type="button" onClick={() => onChange(page - 1)} disabled={busy || page === 0} className={step}>
          <span aria-hidden="true">‹</span> 이전
        </button>
        <div className="flex flex-wrap items-center justify-center gap-0.5">
          {pages.map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => onChange(p)}
              disabled={busy}
              aria-current={p === page ? "page" : undefined}
              className={`${number} ${
                p === page
                  ? "bg-[var(--blue)] text-white font-semibold"
                  : "text-[var(--text-muted)] hover:bg-[var(--blue-soft)] hover:text-[var(--blue)]"
              }`}
            >
              {p + 1}
            </button>
          ))}
        </div>
        <button type="button" onClick={() => onChange(page + 1)} disabled={busy || page + 1 >= totalPages} className={step}>
          다음 <span aria-hidden="true">›</span>
        </button>
      </div>
      <span className="text-xs text-[var(--text-muted)]">
        {page + 1} / {totalPages} 페이지 · 총 {formatNumber(totalElements)}쌍
      </span>
    </nav>
  );
}

/**
 * 라벨 쌍 표. 수정은 한 번에 하나만 보낸다(저장 중에는 모든 줄의 버튼을 막는다).
 * 순번은 지금 조건(셋·라벨 필터)의 목록에서 몇 번째인지다(1부터, 페이지를 넘겨도 이어진다).
 */
function EvalLabelTable({ pairs, startIndex }: { pairs: EvalLabelPair[]; startIndex: number }) {
  const update = useUpdateEvalLabel();
  return (
    <table className={TABLE}>
      <colgroup>
        {COLUMNS.map((w, i) => <col key={i} style={w ? { width: w } : undefined} />)}
      </colgroup>
      <thead>
        <tr>
          <th className={`${TH} ${NOWRAP}`}>순번</th>
          <th className={`${TH} ${NOWRAP}`}>관계<br />간격</th>
          <th className={TH}>알림 A</th>
          <th className={TH}>알림 B</th>
          <th className={`${TH} ${NOWRAP}`}>라벨</th>
          <th className={`${TH} ${NOWRAP}`}>기준 시점<br />마지막 수정</th>
          <th className={`${TH} ${NOWRAP}`}>이력</th>
        </tr>
      </thead>
      <tbody>
        {pairs.map((p, i) => (
          <EvalLabelRow key={p.id} pair={p} index={startIndex + i + 1} saving={update.isPending}
            onSave={(label, reason, callbacks) => update.mutate({ id: p.id, body: { label, reason } }, callbacks)} />
        ))}
      </tbody>
    </table>
  );
}

type SaveCallbacks = { onSuccess: () => void; onError: (e: unknown) => void };

/**
 * 라벨 쌍 한 줄. 지금 라벨과 다른 버튼을 누르면 사유 입력 모달이 뜨고, 사유를 적고 저장해야 바뀐다.
 * 같은 라벨을 누르면 아무것도 하지 않는다(서버도 같은 라벨은 바꾸지 않는다). 이력 "보기"도 모달로 띄운다.
 */
function EvalLabelRow({ pair, index, saving, onSave }: {
  pair: EvalLabelPair;
  index: number;
  saving: boolean;
  onSave: (label: HumanLabel, reason: string, callbacks: SaveCallbacks) => void;
}) {
  const [draft, setDraft] = useState<HumanLabel | null>(null);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);

  const choose = (value: HumanLabel) => {
    setError(null);
    setDraft(value === pair.label ? null : value);
  };
  const cancel = () => {
    setDraft(null);
    setReason("");
    setError(null);
  };
  const save = () => {
    if (!draft || reason.trim() === "") return;
    onSave(draft, reason.trim(), {
      onSuccess: cancel,
      onError: (e) => setError(errorMessage(e, "저장하지 못했습니다.")),
    });
  };

  return (
    <>
      <tr>
        <td className={`${TD} ${NOWRAP}`}>{index}</td>
        <td className={`${TD} ${NOWRAP}`}>
          <div>{pair.relation ? RELATION_LABEL[pair.relation] : "-"}</div>
          <div className="text-xs text-[var(--text-muted)]">{formatGap(pair.alertA.sentAt, pair.alertB.sentAt)}</div>
        </td>
        <td className={TD}><AlertCell alert={pair.alertA} fluid /></td>
        <td className={TD}><AlertCell alert={pair.alertB} fluid /></td>
        <td className={TD}>
          <div className="flex flex-wrap gap-1" role="group" aria-label={`라벨 쌍 #${pair.id} 라벨`}>
            {HUMAN_LABEL_OPTIONS.map((o) => {
              const current = pair.label === o.value;
              const chosen = draft === o.value;
              return (
                <button
                  key={o.value}
                  type="button"
                  aria-pressed={current}
                  disabled={saving}
                  onClick={() => choose(o.value)}
                  className={`px-2 py-0.5 text-xs rounded-[var(--radius-control)] border disabled:opacity-40 ${NOWRAP} ${
                    current
                      ? "border-[var(--blue)] bg-[var(--blue)] text-white"
                      : chosen
                        ? "border-[var(--blue)] text-[var(--blue)] bg-[var(--blue-soft)]"
                        : "border-[var(--line)] text-[var(--text-body)] hover:bg-[var(--blue-soft)]"
                  }`}
                >
                  {o.label}
                </button>
              );
            })}
          </div>
        </td>
        <td className={`${TD} ${NOWRAP}`}>
          <div>{pair.criteriaDate ?? "-"}</div>
          <div className="text-xs text-[var(--text-muted)]">{pair.updatedAt ? formatDateTime(pair.updatedAt) : "고친 적 없음"}</div>
        </td>
        <td className={`${TD} ${NOWRAP}`}>
          <button
            type="button"
            aria-haspopup="dialog"
            onClick={() => setHistoryOpen(true)}
            className="px-2 py-0.5 text-xs border border-[var(--line)] rounded-[var(--radius-control)] hover:bg-[var(--blue-soft)]"
          >
            보기
          </button>
        </td>
      </tr>
      {draft && (
        <ReasonModal
          index={index}
          pair={pair}
          from={pair.label}
          to={draft}
          reason={reason}
          saving={saving}
          error={error}
          onReasonChange={setReason}
          onSave={save}
          onCancel={cancel}
        />
      )}
      {historyOpen && <HistoryModal index={index} pair={pair} onClose={() => setHistoryOpen(false)} />}
    </>
  );
}

/**
 * 이 화면의 모달 공통 틀: 어두운 배경 + 가운데 상자, Esc·바깥 클릭으로 닫기(closable 이 false 면 닫지 않음).
 * body 아래로 그린다(createPortal) — 표의 tbody 안에 div 를 둘 수 없고, 표의 overflow·z-index 에 갇히지 않게.
 */
function ModalShell({ titleId, closable, onClose, children }: {
  titleId: string;
  closable: boolean;
  onClose: () => void;
  children: React.ReactNode;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && closable) onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [closable, onClose]);

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={() => closable && onClose()}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-2xl max-h-[90vh] overflow-y-auto space-y-4 rounded-[var(--radius-panel-card)] bg-[var(--surface)] p-6 shadow-[0_10px_30px_rgba(28,39,60,0.12)]"
      >
        {children}
      </div>
    </div>,
    document.body,
  );
}

/** 모달 위쪽의 두 알림 원문(나란히, 좁은 화면에서는 위아래). 표로 돌아가지 않고 판단을 다시 확인하게 한다. */
function PairAlerts({ pair }: { pair: EvalLabelPair }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 text-sm">
      {[{ name: "알림 A", alert: pair.alertA }, { name: "알림 B", alert: pair.alertB }].map(({ name, alert }) => (
        <div key={name} className="rounded-[var(--radius-control)] border border-[var(--line)] p-3">
          <div className="mb-1 text-xs font-medium text-[var(--text-muted)]">{name}</div>
          <AlertCell alert={alert} fluid />
        </div>
      ))}
    </div>
  );
}

function pairFacts(pair: EvalLabelPair): string {
  return `${pair.relation ? RELATION_LABEL[pair.relation] : "관계 없음"} · 간격 ${formatGap(pair.alertA.sentAt, pair.alertB.sentAt)}`;
}

/** 라벨을 바꿀 때 사유를 받는 모달. 사유가 비면 저장할 수 없고, 저장 중에는 닫을 수 없다. 실패 이유는 모달 안에 보인다. */
function ReasonModal({ index, pair, from, to, reason, saving, error, onReasonChange, onSave, onCancel }: {
  index: number;
  pair: EvalLabelPair;
  from: HumanLabel;
  to: HumanLabel;
  reason: string;
  saving: boolean;
  error: string | null;
  onReasonChange: (value: string) => void;
  onSave: () => void;
  onCancel: () => void;
}) {
  const titleId = `eval-label-reason-${pair.id}`;
  return (
    <ModalShell titleId={titleId} closable={!saving} onClose={onCancel}>
      <div className="space-y-1">
        <h3 id={titleId} className="text-base font-semibold text-[var(--ink)]">{index}번 쌍 라벨 바꾸기</h3>
        <p className="text-sm text-[var(--text-body)]">
          <span className="text-[var(--text-muted)]">{LABEL_TEXT[from]}</span>
          {" → "}
          <span className="font-semibold text-[var(--blue)]">{LABEL_TEXT[to]}</span>
          <span className="text-[var(--text-muted)]"> · {pairFacts(pair)}</span>
        </p>
      </div>
      <PairAlerts pair={pair} />
      <div className="space-y-1">
        <label htmlFor={`${titleId}-reason`} className="text-sm font-medium text-[var(--ink)]">
          바꾸는 사유 <span className="text-[var(--coral)]">*</span>
        </label>
        <textarea
          id={`${titleId}-reason`}
          value={reason}
          onChange={(e) => onReasonChange(e.target.value)}
          rows={3}
          autoFocus
          placeholder="예: 같은 화재의 발생과 진화 안내라 같은 사건"
          className="w-full rounded-[var(--radius-control)] border border-[var(--line)] bg-[var(--surface)] px-3 py-2 text-sm text-[var(--text-body)]"
        />
        {error && <p className="text-sm text-[var(--coral)]">{error}</p>}
      </div>
      <div className="flex justify-end gap-2">
        <button
          type="button"
          onClick={onCancel}
          disabled={saving}
          className="rounded-[var(--radius-control)] border border-[var(--line)] px-4 py-2 text-sm text-[var(--text-body)] disabled:opacity-40"
        >
          취소
        </button>
        <button
          type="button"
          onClick={onSave}
          disabled={saving || reason.trim() === ""}
          className="rounded-[var(--radius-control)] border border-[var(--blue)] bg-[var(--blue)] px-4 py-2 text-sm text-white disabled:opacity-40"
        >
          {saving ? "저장 중…" : "저장"}
        </button>
      </div>
    </ModalShell>
  );
}

/** 라벨 하나의 변경 이력을 띄우는 모달. 현재 라벨·기준 시점과 두 알림 원문을 함께 보여 준다. */
function HistoryModal({ index, pair, onClose }: { index: number; pair: EvalLabelPair; onClose: () => void }) {
  const titleId = `eval-label-history-${pair.id}`;
  return (
    <ModalShell titleId={titleId} closable onClose={onClose}>
      <div className="space-y-1">
        <h3 id={titleId} className="text-base font-semibold text-[var(--ink)]">{index}번 쌍 라벨 이력</h3>
        <p className="text-sm text-[var(--text-muted)]">
          지금 라벨 <span className="font-semibold text-[var(--ink)]">{LABEL_TEXT[pair.label]}</span>
          {" · "}기준 시점 {pair.criteriaDate ?? "-"} · {pairFacts(pair)}
        </p>
      </div>
      <PairAlerts pair={pair} />
      <EvalLabelHistory id={pair.id} />
      <div className="flex justify-end">
        <button
          type="button"
          onClick={onClose}
          autoFocus
          className="rounded-[var(--radius-control)] border border-[var(--line)] px-4 py-2 text-sm text-[var(--text-body)]"
        >
          닫기
        </button>
      </div>
    </ModalShell>
  );
}

/** 라벨 하나의 변경 이력(최신 먼저). 펼친 줄에서만 그려져 펼치지 않은 줄은 요청하지 않는다. */
function EvalLabelHistory({ id }: { id: number }) {
  const { data, isLoading, error } = useEvalLabelHistory(id);
  if (isLoading || error) return <QueryState isLoading={isLoading} error={error} />;
  if (!data || data.changes.length === 0) {
    return <p className="text-sm text-[var(--text-muted)]">바뀐 적 없는 라벨입니다.</p>;
  }
  return (
    <ul className="space-y-1 text-sm whitespace-normal">
      {data.changes.map((c, i) => (
        <li key={`${c.changedAt}-${i}`} className="text-[var(--text-body)]">
          <span className="text-[var(--text-muted)]">{formatDateTime(c.changedAt)} · 회원 #{c.changedBy}</span>
          {" · "}
          {LABEL_TEXT[c.oldLabel]} → {LABEL_TEXT[c.newLabel]}
          {" · "}
          {c.reason}
        </li>
      ))}
    </ul>
  );
}
