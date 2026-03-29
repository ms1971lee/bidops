"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { useEffect, useState, useCallback } from "react";
import { inquiryApi } from "@/lib/api";
import { useProjectRole } from "@/lib/useProjectRole";
import StatusBadge from "@/components/common/StatusBadge";
import DataTable, { type Column } from "@/components/common/DataTable";
import FilterBar from "@/components/common/FilterBar";
import DetailPanel, { PanelField } from "@/components/common/DetailPanel";

const STATUS_FILTERS = [
  { value: "", label: "전체" }, { value: "DRAFT", label: "초안" },
  { value: "SUBMITTED", label: "제출" }, { value: "ANSWERED", label: "답변완료" },
  { value: "CLOSED", label: "종료" },
];
const PRIORITY_FILTERS = [
  { value: "", label: "우선도 전체" }, { value: "CRITICAL", label: "긴급" },
  { value: "HIGH", label: "높음" }, { value: "MEDIUM", label: "중간" }, { value: "LOW", label: "낮음" },
];
const PRIORITY_ROW: Record<string, string> = {
  CRITICAL: "border-l-4 border-l-red-600 bg-red-50/40", HIGH: "border-l-4 border-l-orange-400",
};

export default function InquiriesPage() {
  const { id } = useParams() as { id: string };
  const { canEdit, canReview } = useProjectRole(id);

  const [items, setItems] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState("");
  const [priorityFilter, setPriorityFilter] = useState("");
  const [selected, setSelected] = useState<any>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [createForm, setCreateForm] = useState({ title: "", question_text: "", reason_note: "", priority: "MEDIUM", requirement_id: "" });
  const [editing, setEditing] = useState(false);
  const [editForm, setEditForm] = useState<any>({});
  const [answerText, setAnswerText] = useState("");
  const [generating, setGenerating] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    const p = new URLSearchParams();
    if (statusFilter) p.set("status", statusFilter);
    if (priorityFilter) p.set("priority", priorityFilter);
    inquiryApi.list(id, p.toString() || undefined)
      .then(setItems).catch(() => setItems([])).finally(() => setLoading(false));
  }, [id, statusFilter, priorityFilter]);

  useEffect(() => { load(); }, [load]);

  const selectItem = (inqId: string) => {
    inquiryApi.get(id, inqId).then((d) => { setSelected(d); setEditing(false); setAnswerText(""); });
  };

  const handleCreate = async () => {
    if (!createForm.title || !createForm.question_text) return;
    const body: any = { ...createForm };
    if (!body.requirement_id) delete body.requirement_id;
    if (!body.reason_note) delete body.reason_note;
    await inquiryApi.create(id, body);
    setCreateForm({ title: "", question_text: "", reason_note: "", priority: "MEDIUM", requirement_id: "" });
    setShowCreate(false); load();
  };

  const startEdit = () => {
    setEditForm({ title: selected.title || "", question_text: selected.question_text || "",
      reason_note: selected.reason_note || "", priority: selected.priority || "MEDIUM",
      requirement_id: selected.requirement_id || "" });
    setEditing(true);
  };

  const saveEdit = async () => {
    const body: any = { ...editForm };
    if (!body.requirement_id) delete body.requirement_id;
    if (!body.reason_note) delete body.reason_note;
    const updated = await inquiryApi.update(id, selected.id, body);
    setSelected(updated); setEditing(false); load();
  };

  const handleStatus = async (status: string, answer?: string) => {
    const body: any = { status };
    if (answer) body.answer_text = answer;
    const updated = await inquiryApi.changeStatus(id, selected.id, body);
    setSelected(updated); setAnswerText(""); load();
  };

  const handleGenerate = async () => {
    if (!confirm("질의 필요 요구사항에서 질의 초안을 자동 생성합니다. 계속?")) return;
    setGenerating(true);
    try {
      const r = await inquiryApi.generate(id);
      alert(`질의 초안 ${r.created_count}건 생성 (건너뜀 ${r.skipped_count}건)`);
      load();
    } catch (err: any) { alert(err.message || "생성 실패"); }
    finally { setGenerating(false); }
  };

  const columns: Column[] = [
    { key: "inquiry_code", label: "코드", width: "w-20", render: (q) => <span className="font-mono text-xs text-gray-500">{q.inquiry_code}</span> },
    { key: "title", label: "제목", render: (q) => <span className="truncate max-w-[200px] block">{q.title}</span> },
    { key: "priority", label: "우선도", width: "w-16", render: (q) => <StatusBadge value={q.priority} /> },
    { key: "status", label: "상태", width: "w-20", render: (q) => <StatusBadge value={q.status} /> },
    { key: "requirement_id", label: "요구사항", width: "w-16", render: (q) =>
      q.requirement_id ? <Link href={`/projects/${id}/requirements/${q.requirement_id}`}
        className="text-[10px] text-blue-600 hover:underline" onClick={(e) => e.stopPropagation()}>REQ</Link>
        : <span className="text-[10px] text-gray-300">-</span>
    },
    { key: "updated_at", label: "수정일", width: "w-20", render: (q) =>
      <span className="text-xs text-gray-400">{q.updated_at ? new Date(q.updated_at).toLocaleDateString("ko-KR") : "-"}</span>
    },
  ];

  return (
    <div className="flex gap-4 h-[calc(100vh-220px)]">
      <div className={`${selected ? "w-[420px]" : "flex-1"} flex flex-col shrink-0`}>
        <div className="flex items-center gap-2 mb-3 flex-wrap">
          <span className="text-sm font-semibold">질의응답</span>
          <span className="text-xs text-gray-400">{items.length}건</span>
          {canEdit && (
            <div className="ml-auto flex gap-1">
              <button onClick={handleGenerate} disabled={generating}
                className="px-3 py-1 bg-green-600 text-white rounded text-xs disabled:opacity-50">
                {generating ? "생성 중..." : "초안 자동 생성"}
              </button>
              <button onClick={() => setShowCreate(!showCreate)}
                className="px-3 py-1 bg-blue-600 text-white rounded text-xs">
                {showCreate ? "취소" : "+ 새 질의"}
              </button>
            </div>
          )}
        </div>

        <FilterBar
          toggleFilters={[{ label: "상태", options: STATUS_FILTERS, value: statusFilter, onChange: setStatusFilter }]}
          selectFilters={[{ label: "우선도", options: PRIORITY_FILTERS, value: priorityFilter, onChange: setPriorityFilter }]}
        />

        {showCreate && (
          <div className="bg-blue-50 border border-blue-100 rounded p-3 mb-3 space-y-2">
            <input value={createForm.title} onChange={(e) => setCreateForm({ ...createForm, title: e.target.value })}
              className="w-full border rounded px-2 py-1.5 text-sm" placeholder="질의 제목 *" aria-label="질의 제목" />
            <textarea value={createForm.question_text} rows={3}
              onChange={(e) => setCreateForm({ ...createForm, question_text: e.target.value })}
              className="w-full border rounded px-2 py-1.5 text-sm" placeholder="질의 내용 *" aria-label="질의 내용" />
            <div className="flex gap-2 items-center">
              <select value={createForm.priority} onChange={(e) => setCreateForm({ ...createForm, priority: e.target.value })}
                className="border rounded px-2 py-1 text-xs" aria-label="우선도">
                <option value="LOW">낮음</option><option value="MEDIUM">중간</option>
                <option value="HIGH">높음</option><option value="CRITICAL">긴급</option>
              </select>
              <button onClick={handleCreate} className="px-4 py-1 bg-blue-600 text-white rounded text-xs ml-auto">생성</button>
            </div>
          </div>
        )}

        <div className="flex-1 overflow-auto">
          <DataTable columns={columns} data={items} loading={loading}
            emptyMessage="질의가 없습니다" rowKey={(q) => q.id}
            activeRowKey={selected?.id} onRowClick={(q) => selectItem(q.id)}
            rowClassName={(q) => PRIORITY_ROW[q.priority] || ""} />
        </div>
      </div>

      {selected && (
        <DetailPanel title={selected.title || selected.inquiry_code}
          badges={[{ value: selected.status }, { value: selected.priority }]}
          meta={<span className="font-mono text-xs text-gray-500">{selected.inquiry_code}</span>}
          onClose={() => { setSelected(null); setEditing(false); }}
          actions={canEdit && !editing && selected.status === "DRAFT" ? (
            <button onClick={startEdit} className="text-xs text-blue-600 hover:underline">수정</button>
          ) : undefined}>

          {editing ? (
            <div className="space-y-3 border-t pt-3">
              <PanelField label="제목"><input value={editForm.title} onChange={(e) => setEditForm({ ...editForm, title: e.target.value })} className="w-full border rounded px-2 py-1.5 text-sm" aria-label="제목" /></PanelField>
              <PanelField label="질의 내용"><textarea value={editForm.question_text} rows={4} onChange={(e) => setEditForm({ ...editForm, question_text: e.target.value })} className="w-full border rounded px-2 py-1.5 text-sm" aria-label="질의 내용" /></PanelField>
              <div className="flex gap-2 pt-1">
                <button onClick={saveEdit} className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm">저장</button>
                <button onClick={() => setEditing(false)} className="px-4 py-1.5 border rounded text-sm">취소</button>
              </div>
            </div>
          ) : (
            <>
              {selected.requirement_id && (
                <div className="flex items-center gap-2 bg-blue-50 rounded px-3 py-2">
                  <span className="text-xs text-gray-500">연결 요구사항:</span>
                  <Link href={`/projects/${id}/requirements/${selected.requirement_id}`}
                    className="text-xs text-blue-600 hover:underline ml-auto">상세 보기 &rarr;</Link>
                </div>
              )}
              <PanelField label="질의 내용"><div className="bg-gray-50 p-3 rounded whitespace-pre-wrap">{selected.question_text}</div></PanelField>
              {selected.reason_note && <PanelField label="질의 사유"><div className="bg-yellow-50 p-3 rounded whitespace-pre-wrap">{selected.reason_note}</div></PanelField>}
              {selected.answer_text && <PanelField label="발주처 답변"><div className="bg-green-50 border border-green-100 p-3 rounded whitespace-pre-wrap">{selected.answer_text}</div></PanelField>}

              {canReview && (
                <div className="border-t pt-4 space-y-3">
                  <PanelField label="상태 변경">
                    {selected.status === "SUBMITTED" && (
                      <div className="space-y-2 mb-2">
                        <textarea value={answerText} rows={3} onChange={(e) => setAnswerText(e.target.value)}
                          className="w-full border rounded px-2 py-1.5 text-sm" placeholder="발주처 답변 내용..." aria-label="답변 내용" />
                        <button onClick={() => { if (!answerText.trim()) { alert("답변을 입력하세요."); return; } handleStatus("ANSWERED", answerText); }}
                          className="px-3 py-1.5 bg-green-600 text-white rounded text-xs">답변 완료</button>
                      </div>
                    )}
                    <div className="flex gap-2 flex-wrap">
                      {selected.status === "DRAFT" && <button onClick={() => handleStatus("SUBMITTED")} className="px-3 py-1.5 bg-blue-600 text-white rounded text-xs">제출</button>}
                      {selected.status === "ANSWERED" && <button onClick={() => handleStatus("CLOSED")} className="px-3 py-1.5 bg-gray-600 text-white rounded text-xs">종료</button>}
                      {selected.status !== "DRAFT" && selected.status !== "CLOSED" && <button onClick={() => handleStatus("DRAFT")} className="px-3 py-1.5 border rounded text-xs">초안으로</button>}
                    </div>
                  </PanelField>
                </div>
              )}
              {!canReview && <div className="text-xs text-gray-400 bg-gray-50 p-3 rounded border-t mt-3">상태 변경은 REVIEWER 이상만 가능합니다.</div>}
            </>
          )}
        </DetailPanel>
      )}

      {!selected && items.length > 0 && (
        <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">질의를 선택하면 상세를 확인할 수 있습니다</div>
      )}
    </div>
  );
}
