"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { useEffect, useState, useCallback } from "react";
import { activityApi, memberApi, type ProjectMemberDto } from "@/lib/api";
import DataTable, { type Column } from "@/components/common/DataTable";
import FilterBar from "@/components/common/FilterBar";

const TYPE_GROUPS = [
  { label: "전체", value: "" }, { label: "문서", value: "document" },
  { label: "분석", value: "analysis_job" }, { label: "요구사항", value: "requirement" },
  { label: "체크리스트", value: "checklist" }, { label: "질의", value: "inquiry" },
  { label: "멤버", value: "member" },
];

const TYPE_ICONS: Record<string, string> = {
  DOCUMENT_UPLOADED: "📄", DOCUMENT_DELETED: "🗑️", ANALYSIS_STARTED: "🔬",
  ANALYSIS_COMPLETED: "✅", ANALYSIS_FAILED: "❌", REQUIREMENT_UPDATED: "📝",
  REQUIREMENT_REVIEW_CHANGED: "✍️", REQUIREMENT_INSIGHT_UPDATED: "🤖",
  CHECKLIST_CREATED: "☑️", CHECKLIST_ITEM_STATUS_CHANGED: "✓",
  INQUIRY_CREATED: "❓", INQUIRY_STATUS_CHANGED: "💬",
  MEMBER_ADDED: "➕", MEMBER_REMOVED: "➖", MEMBER_ROLE_CHANGED: "🔑",
  PROJECT_CREATED: "📁", PROJECT_UPDATED: "📁",
};

const TYPE_LABELS: Record<string, string> = {
  DOCUMENT_UPLOADED: "문서 업로드", DOCUMENT_DELETED: "문서 삭제",
  ANALYSIS_STARTED: "분석 시작", ANALYSIS_COMPLETED: "분석 완료", ANALYSIS_FAILED: "분석 실패",
  REQUIREMENT_UPDATED: "요구사항 수정", REQUIREMENT_REVIEW_CHANGED: "검토 변경",
  REQUIREMENT_INSIGHT_UPDATED: "AI 분석 수정",
  CHECKLIST_CREATED: "체크리스트 생성", CHECKLIST_ITEM_STATUS_CHANGED: "체크리스트 상태",
  INQUIRY_CREATED: "질의 생성", INQUIRY_STATUS_CHANGED: "질의 상태",
  MEMBER_ADDED: "멤버 추가", MEMBER_REMOVED: "멤버 제거", MEMBER_ROLE_CHANGED: "역할 변경",
  PROJECT_CREATED: "프로젝트 생성", PROJECT_UPDATED: "프로젝트 수정",
};

function isAiActivity(type: string): boolean {
  return type.startsWith("ANALYSIS") || type === "REQUIREMENT_INSIGHT_UPDATED";
}

const TARGET_ROUTES: Record<string, (p: string, t: string) => string> = {
  requirement: (p, t) => `/projects/${p}/requirements/${t}`,
  document: (p) => `/projects/${p}/documents`,
  analysis_job: (p) => `/projects/${p}`,
  checklist: (p) => `/projects/${p}/checklists`,
  inquiry: (p) => `/projects/${p}/inquiries`,
  member: (p) => `/projects/${p}/settings/members`,
};

export default function HistoryPage() {
  const { id } = useParams() as { id: string };
  const [items, setItems] = useState<any[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [targetTypeFilter, setTargetTypeFilter] = useState("");
  const [actorFilter, setActorFilter] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [members, setMembers] = useState<ProjectMemberDto[]>([]);
  const [viewMode, setViewMode] = useState<"timeline" | "table">("timeline");

  useEffect(() => { memberApi.list(id).then(setMembers).catch(() => {}); }, [id]);

  const load = useCallback(() => {
    setLoading(true);
    const p = new URLSearchParams();
    if (targetTypeFilter) p.set("target_type", targetTypeFilter);
    if (actorFilter) p.set("actor_user_id", actorFilter);
    if (dateFrom) p.set("date_from", dateFrom);
    if (dateTo) p.set("date_to", dateTo);
    p.set("page", String(page)); p.set("size", "30");
    activityApi.list(id, p.toString())
      .then((d) => { setItems(d.items || []); setTotalCount(d.total_count || 0); })
      .catch(() => { setItems([]); setTotalCount(0); })
      .finally(() => setLoading(false));
  }, [id, targetTypeFilter, actorFilter, dateFrom, dateTo, page]);

  useEffect(() => { load(); }, [load]);

  const totalPages = Math.max(1, Math.ceil(totalCount / 30));

  const memberOptions = [{ value: "", label: "사용자 전체" },
    ...members.map((m) => ({ value: m.user_id, label: m.user_name || m.user_email }))];

  const tableColumns: Column[] = [
    { key: "created_at", label: "일시", width: "w-36", render: (r) =>
      <span className="text-xs text-gray-500 whitespace-nowrap">{r.created_at ? new Date(r.created_at).toLocaleString("ko-KR") : "-"}</span> },
    { key: "activity_type", label: "유형", width: "w-24", render: (r) =>
      <span className="text-[10px] px-1.5 py-0.5 rounded bg-gray-100 text-gray-600">{TYPE_LABELS[r.activity_type] || r.activity_type}</span> },
    { key: "summary", label: "내용" },
    { key: "actor_name", label: "수행자", width: "w-20", render: (r) =>
      <span className="text-xs text-gray-600">{r.actor_name || "-"}</span> },
    { key: "detail", label: "상세", width: "w-40", render: (r) =>
      <span className="text-xs text-gray-400 truncate max-w-[160px] block">{r.detail || "-"}</span> },
    { key: "link", label: "", width: "w-16", render: (r) => {
      const route = r.target_type && r.target_id ? TARGET_ROUTES[r.target_type]?.(id, r.target_id) : null;
      return route ? <Link href={route} className="text-xs text-blue-600 hover:underline">보기</Link> : null;
    }},
  ];

  return (
    <div>
      <FilterBar
        toggleFilters={[{ label: "유형", options: TYPE_GROUPS, value: targetTypeFilter, onChange: (v) => { setTargetTypeFilter(v); setPage(1); } }]}
        selectFilters={[{ label: "사용자", options: memberOptions, value: actorFilter, onChange: (v) => { setActorFilter(v); setPage(1); } }]}
        totalCount={totalCount}
        actions={
          <div className="flex items-center gap-2">
            <input type="date" value={dateFrom} onChange={(e) => { setDateFrom(e.target.value); setPage(1); }}
              className="border rounded px-2 py-1 text-xs" aria-label="시작일" />
            <span className="text-xs text-gray-400">~</span>
            <input type="date" value={dateTo} onChange={(e) => { setDateTo(e.target.value); setPage(1); }}
              className="border rounded px-2 py-1 text-xs" aria-label="종료일" />
            <span className="text-gray-300">|</span>
            <div className="flex gap-1">
              <button onClick={() => setViewMode("timeline")} aria-pressed={viewMode === "timeline"}
                className={`px-2 py-1 text-xs rounded border ${viewMode === "timeline" ? "bg-blue-600 text-white border-blue-600" : "bg-white"}`}>타임라인</button>
              <button onClick={() => setViewMode("table")} aria-pressed={viewMode === "table"}
                className={`px-2 py-1 text-xs rounded border ${viewMode === "table" ? "bg-blue-600 text-white border-blue-600" : "bg-white"}`}>테이블</button>
            </div>
          </div>
        }
      />

      {loading ? (
        <div className="text-center text-gray-400 py-12">로딩 중...</div>
      ) : items.length === 0 ? (
        <div className="text-center text-gray-400 py-12 bg-white rounded border">활동 이력이 없습니다</div>
      ) : viewMode === "table" ? (
        <DataTable columns={tableColumns} data={items} rowKey={(r) => r.id} emptyMessage="이력 없음" />
      ) : (
        <div className="space-y-6">
          {groupByDate(items).map(([date, entries]) => (
            <div key={date}>
              <div className="text-xs font-semibold text-gray-500 mb-2 sticky top-0 bg-gray-50 py-1 z-10">{date}</div>
              <div className="space-y-1">
                {(entries as any[]).map((item: any) => {
                  const ai = isAiActivity(item.activity_type);
                  const icon = TYPE_ICONS[item.activity_type] || "•";
                  const route = item.target_type && item.target_id ? TARGET_ROUTES[item.target_type]?.(id, item.target_id) : null;
                  return (
                    <div key={item.id} className={`flex items-start gap-3 bg-white border rounded px-4 py-3 text-sm ${ai ? "border-l-4 border-l-purple-400" : "border-l-4 border-l-blue-300"}`}>
                      <span className="text-base shrink-0 mt-0.5 w-6 text-center" aria-hidden="true">{icon}</span>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-medium text-gray-800">{item.summary}</span>
                          <span className="text-[10px] px-1.5 py-0.5 rounded bg-gray-100 text-gray-500">{TYPE_LABELS[item.activity_type] || item.activity_type}</span>
                          {ai && <span className="text-[10px] px-1.5 py-0.5 rounded bg-purple-100 text-purple-600">AI</span>}
                        </div>
                        {item.detail && <div className="text-xs text-gray-500 mt-0.5">{item.detail}</div>}
                        <div className="flex items-center gap-3 mt-1 text-xs text-gray-400">
                          <span className="font-medium text-gray-600">{item.actor_name || "알 수 없음"}</span>
                          <span>{item.created_at ? new Date(item.created_at).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" }) : ""}</span>
                          {route && <Link href={route} className="text-blue-600 hover:underline">상세 보기</Link>}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex justify-center gap-1 mt-6">
          <button onClick={() => setPage(Math.max(1, page - 1))} disabled={page <= 1} className="px-3 py-1 text-xs border rounded disabled:opacity-30">이전</button>
          {Array.from({ length: Math.min(totalPages, 10) }, (_, i) => {
            const start = Math.max(1, Math.min(page - 4, totalPages - 9));
            const pg = start + i;
            if (pg > totalPages) return null;
            return <button key={pg} onClick={() => setPage(pg)} className={`px-3 py-1 text-xs border rounded ${page === pg ? "bg-blue-600 text-white border-blue-600" : "hover:bg-gray-50"}`}>{pg}</button>;
          })}
          <button onClick={() => setPage(Math.min(totalPages, page + 1))} disabled={page >= totalPages} className="px-3 py-1 text-xs border rounded disabled:opacity-30">다음</button>
        </div>
      )}
    </div>
  );
}

function groupByDate(items: any[]): [string, any[]][] {
  const map = new Map<string, any[]>();
  for (const item of items) {
    const date = item.created_at ? new Date(item.created_at).toLocaleDateString("ko-KR", { year: "numeric", month: "long", day: "numeric", weekday: "short" }) : "날짜 없음";
    if (!map.has(date)) map.set(date, []);
    map.get(date)!.push(item);
  }
  return Array.from(map.entries());
}
