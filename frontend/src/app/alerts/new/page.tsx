"use client";

import React, { useState } from "react";
import { useCreateUserAlert } from "@/lib/mutations/useCreateUserAlert";
import { LEVEL_OPTIONS } from "@/ui/level";
import { useRouter } from "next/navigation";

const DISASTER_TYPES = [
  "호우",
  "태풍",
  "지진",
  "화재",
  "폭염",
  "한파",
  "감염병",
  "대설",
  "강풍",
  "해일",
];

type JusoItem = {
  jibunAddr?: string;
  roadAddr?: string;
  siNm?: string;
  sggNm?: string;
  emdNm?: string;
  admCd: string;
};

export default function RegisterAlertsPage() {
  const router = useRouter();
  const { mutate, isPending, isError, error } = useCreateUserAlert();

  const [title, setTitle] = useState("");
  const [message, setMessage] = useState("");
  const [disasterType, setDisasterType] = useState("");
  const [disasterLevel, setDisasterLevel] = useState("");
  const [occurDate, setOccurDate] = useState(""); // YYYY-MM-DD
  const [occurTime, setOccurTime] = useState(""); // HH:mm (24h)
  const [selectedRegionName, setSelectedRegionName] = useState("");
  const [selectedRegionCode, setSelectedRegionCode] = useState("");

  // 주소 검색 모달 상태
  const [isAddrModalOpen, setIsAddrModalOpen] = useState(false);
  const [addrQuery, setAddrQuery] = useState("");
  const [addrResults, setAddrResults] = useState<Array<{ name: string; code: string }>>([]);
  const [addrLoading, setAddrLoading] = useState(false);
  const [addrError, setAddrError] = useState<string | null>(null);

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!occurDate || !occurTime) {
      alert("발생 일자와 시간을 입력하세요.");
      return;
    }
    if (!selectedRegionCode || !selectedRegionName) {
      alert("주소 검색에서 지역을 선택하세요.");
      return;
    }

    mutate(
      {
        title,
        message,
        disasterType: disasterType || null,
        disasterLevel: disasterLevel || null,
        occurredAt: `${occurDate}T${occurTime}`,
        regionCodes: [selectedRegionCode],
        regionNames: [selectedRegionName],
      },
      {
        onSuccess: () => {
          alert("제보가 등록되었습니다.");
          router.back();
        },
      }
    );
  };

  const searchJuso = async () => {
    const key = process.env.NEXT_PUBLIC_JUSO_API_KEY;
    if (!key) {
      setAddrError("주소 API 키가 없습니다. NEXT_PUBLIC_JUSO_API_KEY를 설정하세요.");
      return;
    }
    if (!addrQuery || addrQuery.trim().length < 2) {
      setAddrError("주소를 두 글자 이상 입력하세요.");
      return;
    }
    try {
      setAddrError(null);
      setAddrLoading(true);

      // 1) JSON 응답 우선 시도
      const jsonParams = new URLSearchParams({
        confmKey: key,
        currentPage: "1",
        countPerPage: "20",
        keyword: addrQuery.trim(),
        resultType: "json",
      });
      const jsonRes = await fetch(`https://www.juso.go.kr/addrlink/addrLinkApi.do?${jsonParams.toString()}`);
      let items: Array<{ name: string; code: string }> = [];
      if (jsonRes.ok) {
        const json = await jsonRes.json().catch(() => null);
        const juso = json?.results?.juso || [];
        items = juso.map((it: JusoItem) => ({
          name: it.jibunAddr || it.roadAddr || `${it.siNm ?? ""} ${it.sggNm ?? ""} ${it.emdNm ?? ""}`.trim(),
          code: it.admCd, // 법정동코드(10자리)
        }));
      }

      // 2) JSON 결과가 없거나 실패 시 JSONP(XML) 폴백 파싱
      if (!items.length) {
        const jsonpParams = new URLSearchParams({
          confmKey: key,
          currentPage: "1",
          countPerPage: "20",
          keyword: addrQuery.trim(),
        });
        const txt = await fetch(`https://business.juso.go.kr/addrlink/addrLinkApiJsonp.do?${jsonpParams.toString()}`, {
          method: "GET",
        }).then(r => r.text());

        // 응답 예: ({'returnXml':'<results>...</results>'}) 형태 → returnXml 추출
        const startToken = "{'returnXml':'";
        const startIdx = txt.indexOf(startToken);
        if (startIdx !== -1) {
          const after = txt.substring(startIdx + startToken.length);
          const endIdx = after.lastIndexOf("'}") >= 0 ? after.lastIndexOf("'}") : after.lastIndexOf("'})");
          const xmlStr = after.substring(0, endIdx).replace(/\\n/g, "\n");

          // XML 파싱
          const parser = new DOMParser();
          const xml = parser.parseFromString(xmlStr, "text/xml");
          const jusoNodes = Array.from(xml.getElementsByTagName("juso"));
          items = jusoNodes.map((node) => {
            const get = (tag: string) => node.getElementsByTagName(tag)[0]?.textContent || "";
            const name = get("jibunAddr") || get("roadAddr") || `${get("siNm")} ${get("sggNm")} ${get("emdNm")}`.trim();
            const code = get("admCd");
            return { name, code };
          });
        }
      }

      setAddrResults(items);
    } catch (e) {
      setAddrError("주소 검색 중 오류가 발생했습니다. " + String(e));
    } finally {
      setAddrLoading(false);
    }
  };

  return (
    <main className="p-6 space-y-6">
      <div>
        <h1 className="text-xl font-semibold">재난 제보 등록</h1>
        <p className="text-sm text-gray-500">필수 정보 입력 후 제출하세요.</p>
      </div>

      <form onSubmit={onSubmit} className="bg-white rounded-xl shadow p-4 grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="md:col-span-2">
          <label className="block text-sm font-medium mb-1">제목</label>
          <input className="input w-full" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="제목을 입력하세요" />
        </div>

        <div className="md:col-span-2">
          <label className="block text-sm font-medium mb-1">메시지</label>
          <textarea className="input w-full min-h-28" value={message} onChange={(e) => setMessage(e.target.value)} placeholder="전문/요지를 입력하세요" />
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">DISASTER TYPE</label>
          <select className="input w-full" value={disasterType} onChange={(e) => setDisasterType(e.target.value)}>
            <option value="">선택 안 함</option>
            {DISASTER_TYPES.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">DISASTER LEVEL</label>
          <select className="input w-full" value={disasterLevel} onChange={(e) => setDisasterLevel(e.target.value)}>
            <option value="">선택 안 함</option>
            {LEVEL_OPTIONS.map((o) => (
              <option key={o.code} value={o.code}>{o.text}</option>
            ))}
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">발생 일자</label>
          <input className="input w-full" type="date" value={occurDate} onChange={(e) => setOccurDate(e.target.value)} />
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">발생 시간 (24시간)</label>
          <input className="input w-full" type="time" step={60} value={occurTime} onChange={(e) => setOccurTime(e.target.value)} />
        </div>

        <div className="md:col-span-2">
          <label className="block text-sm font-medium mb-1">지역 선택 (주소 검색)</label>
          <div className="flex gap-2">
            <input
              className="input w-full"
              readOnly
              value={selectedRegionName ? `${selectedRegionName} (${selectedRegionCode})` : "주소를 선택하세요"}
              onClick={() => setIsAddrModalOpen(true)}
            />
            <button type="button" className="px-3 py-2 rounded bg-gray-100" onClick={() => setIsAddrModalOpen(true)}>검색</button>
            {selectedRegionName && (
              <button type="button" className="px-3 py-2 rounded bg-gray-100" onClick={() => { setSelectedRegionName(""); setSelectedRegionCode(""); }}>초기화</button>
            )}
          </div>
          <p className="text-xs text-gray-500 mt-1">클릭하여 주소 검색 창을 열고, 한 개의 주소를 선택하세요.</p>
        </div>

        <div className="md:col-span-2 flex justify-end gap-2">
          <button type="submit" className="px-4 py-2 rounded bg-blue-600 text-white disabled:opacity-60" disabled={isPending}>등록</button>
        </div>
      </form>

      {isPending && <div>제출 중...</div>}
      {isError && <pre className="text-red-600">{String(error)}</pre>}

      {/* 주소 검색 모달 */}
      <Modal open={isAddrModalOpen} onClose={() => setIsAddrModalOpen(false)}>
        <div className="space-y-3">
          <h3 className="text-lg font-semibold">주소 검색</h3>
          <div className="flex gap-2">
            <input
              className="input w-full"
              value={addrQuery}
              onChange={(e) => setAddrQuery(e.target.value)}
              placeholder="예: 서울특별시 종로구 신교동"
            />
            <button type="button" className="px-3 py-2 rounded bg-blue-600 text-white" onClick={searchJuso}>
              검색
            </button>
          </div>

          {addrLoading && <div className="text-sm text-gray-500">검색 중...</div>}
          {addrError && <div className="text-sm text-red-600">{addrError}</div>}

          {!addrLoading && addrResults.length > 0 && (
            <ul className="max-h-72 overflow-auto divide-y">
              {addrResults.map((r) => (
                <li key={`${r.code}-${r.name}`} className="py-2 flex items-center justify-between gap-3">
                  <div>
                    <div className="text-sm text-gray-800">{r.name}</div>
                    <div className="text-xs text-gray-500">법정동코드: {r.code}</div>
                  </div>
                  <button
                    type="button"
                    className="px-3 py-1 rounded bg-gray-100 hover:bg-gray-200"
                    onClick={() => {
                      setSelectedRegionName(r.name);
                      setSelectedRegionCode(r.code);
                      setIsAddrModalOpen(false);
                    }}
                  >
                    선택
                  </button>
                </li>
              ))}
            </ul>
          )}
          {!addrLoading && addrResults.length === 0 && !addrError && (
            <div className="text-sm text-gray-500">검색 결과가 없습니다.</div>
          )}
        </div>
      </Modal>
    </main>
  );
}

// 간단한 모달 컴포넌트
function Modal({ open, onClose, children }: { open: boolean; onClose: () => void; children: React.ReactNode }) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-xl shadow w-full max-w-2xl p-4">
        <div className="flex justify-end">
          <button className="px-2 py-1 text-sm" onClick={onClose}>닫기</button>
        </div>
        {children}
      </div>
    </div>
  );
}