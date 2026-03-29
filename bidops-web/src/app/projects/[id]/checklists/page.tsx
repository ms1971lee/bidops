"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { useEffect, useState, useCallback } from "react";
import { checklistApi, memberApi, documentApi, sourceExcerptApi, type ProjectMemberDto } from "@/lib/api";
import { useProjectRole } from "@/lib/useProjectRole";
import StatusBadge from "@/components/common/StatusBadge";
import FilterBar from "@/components/common/FilterBar";
import PdfViewer, { parseBboxJson, excerptTypeStyle } from "@/components/common/PdfViewer";
import type { BboxHighlight, SourceBlock } from "@/components/common/PdfViewer";

const STATUS_OPTIONS = [
  { value: "", label: "전체" }, { value: "TODO", label: "TODO" },
  { value: "IN_PROGRESS", label: "진행중" }, { value: "DONE", label: "완료" },
  { value: "BLOCKED", label: "차단" },
];
const RISK_OPTIONS = [
  { value: "", label: "위험도 전체" }, { value: "HIGH", label: "높음" },
  { value: "MEDIUM", label: "중간" }, { value: "LOW", label: "낮음" }, { value: "NONE", label: "없음" },
];
const MANDATORY_OPTIONS = [
  { value: "", label: "필수여부 전체" }, { value: "true", label: "필수" }, { value: "false", label: "선택" },
];
const RISK_ROW_STYLES: Record<string, string> = {
  HIGH: "border-l-4 border-l-red-500 bg-red-50/40",
  MEDIUM: "border-l-4 border-l-yellow-400",
  BLOCKED: "bg-red-50/30",
};
const CHECKLIST_TYPES: Record<string, string> = { SUBMISSION: "제출물", EVALUATION: "평가항목", EVIDENCE: "증빙" };

export default function ChecklistsPage() {
  const { id } = useParams() as { id: string };
  const { canEdit } = useProjectRole(id);

  const [checklists, setChecklists] = useState<any[]>([]);
  const [selectedCl, setSelectedCl] = useState<string | null>(null);
  const [items, setItems] = useState<any[]>([]);
  const [loadingItems, setLoadingItems] = useState(false);
  const [members, setMembers] = useState<ProjectMemberDto[]>([]);
  const [expandedReviews, setExpandedReviews] = useState<Record<string, any[]>>({});

  const [statusFilter, setStatusFilter] = useState("");
  const [riskFilter, setRiskFilter] = useState("");
  const [mandatoryFilter, setMandatoryFilter] = useState("");
  const [ownerFilter, setOwnerFilter] = useState("");
  const [keyword, setKeyword] = useState("");
  const [appliedKeyword, setAppliedKeyword] = useState("");

  const [showCreateCl, setShowCreateCl] = useState(false);
  const [newClTitle, setNewClTitle] = useState("");
  const [newClType, setNewClType] = useState("SUBMISSION");

  const [showCreateItem, setShowCreateItem] = useState(false);
  const [newItemForm, setNewItemForm] = useState({ item_text: "", mandatory_flag: false, due_hint: "", risk_level: "NONE", risk_note: "", linked_requirement_id: "" });

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<any>({});

  // PDF viewer state
  const [showPdf, setShowPdf] = useState(false);
  const [pdfUrl, setPdfUrl] = useState<string | null>(null);
  const [pdfPage, setPdfPage] = useState<number | undefined>(undefined);
  const [pdfHighlight, setPdfHighlight] = useState<BboxHighlight | null>(null);
  const [pdfPageBlocks, setPdfPageBlocks] = useState<SourceBlock[]>([]);
  const [activeBlockId, setActiveBlockId] = useState<string | null>(null);
  const [pdfSourceItem, setPdfSourceItem] = useState<string | null>(null);
  const [pdfMaxPage, setPdfMaxPage] = useState<number | undefined>(undefined);

  // member name lookup
  const memberNameMap = new Map(members.map((m) => [m.user_id, m.user_name || m.user_email]));

  const loadLists = useCallback(() => { checklistApi.list(id).then(setChecklists).catch(() => {}); }, [id]);
  useEffect(() => { loadLists(); }, [loadLists]);
  useEffect(() => { memberApi.list(id).then(setMembers).catch(() => {}); }, [id]);

  const assignableMembers = members.filter((m) => ["OWNER", "ADMIN", "EDITOR", "REVIEWER"].includes(m.project_role));

  const loadItems = useCallback((clId: string) => {
    setSelectedCl(clId); setLoadingItems(true);
    const p = new URLSearchParams();
    if (statusFilter) p.set("status", statusFilter);
    if (riskFilter) p.set("risk_level", riskFilter);
    if (mandatoryFilter) p.set("mandatory", mandatoryFilter);
    if (ownerFilter) p.set("owner_user_id", ownerFilter);
    if (appliedKeyword) p.set("keyword", appliedKeyword);
    checklistApi.items(id, clId, p.toString() || undefined)
      .then(setItems).catch(() => setItems([])).finally(() => setLoadingItems(false));
  }, [id, statusFilter, riskFilter, mandatoryFilter, ownerFilter, appliedKeyword]);

  useEffect(() => { if (selectedCl) loadItems(selectedCl); }, [statusFilter, riskFilter, mandatoryFilter, ownerFilter, appliedKeyword]);

  const selectedChecklist = checklists.find((c) => c.id === selectedCl);

  // server-side filters now handle keyword + owner
  const filteredItems = items;

  const handleCreateChecklist = async () => {
    if (!newClTitle) return;
    await checklistApi.create(id, { checklist_type: newClType, title: newClTitle });
    setNewClTitle(""); setShowCreateCl(false); loadLists();
  };
  const handleCreateItem = async () => {
    if (!selectedCl || !newItemForm.item_text) return;
    await checklistApi.createItem(id, selectedCl, newItemForm);
    setNewItemForm({ item_text: "", mandatory_flag: false, due_hint: "", risk_level: "NONE", risk_note: "", linked_requirement_id: "" });
    setShowCreateItem(false); loadItems(selectedCl); loadLists();
  };
  const handleStatusChange = async (itemId: string, status: string) => {
    if (!selectedCl) return;
    await checklistApi.changeItemStatus(id, selectedCl, itemId, { status });
    loadItems(selectedCl); loadLists();
  };
  const startEdit = (item: any) => {
    setEditingId(item.id);
    setEditForm({ item_text: item.item_text, mandatory_flag: item.mandatory_flag, due_hint: item.due_hint || "",
      risk_level: item.risk_level || "NONE", risk_note: item.risk_note || "",
      linked_requirement_id: item.linked_requirement_id || "",
      owner_user_id: item.owner_user_id || "", action_comment: item.action_comment || "" });
  };
  const saveEdit = async () => {
    if (!selectedCl || !editingId) return;
    await checklistApi.updateItem(id, selectedCl, editingId, editForm);
    setEditingId(null); loadItems(selectedCl); loadLists();
  };

  // ── PDF source viewing ───────────────────────────────────────────
  const handleViewSource = async (item: any) => {
    if (!item.source_excerpt_id) return;
    setPdfSourceItem(item.id);
    try {
      // direct source excerpt lookup (no requirement sources detour needed)
      const excerpt = await sourceExcerptApi.get(item.source_excerpt_id);
      if (!excerpt) { setPdfSourceItem(null); return; }

      // fetch document for PDF URL + page_count
      const doc = await documentApi.get(id, excerpt.document_id);
      setPdfUrl(doc.viewer_url || doc.storage_path || null);
      setPdfMaxPage(doc.page_count || undefined);

      // set up PDF view
      setPdfPage(excerpt.page_no);
      setPdfHighlight(parseBboxJson(excerpt.bbox_json, excerpt.anchor_label));
      setActiveBlockId(excerpt.id);
      setPdfPageBlocks([{
        id: excerpt.id,
        page_no: excerpt.page_no,
        excerpt_type: excerpt.excerpt_type,
        anchor_label: excerpt.anchor_label,
        raw_text: excerpt.raw_text,
        bbox_json: excerpt.bbox_json,
      }]);
      setShowPdf(true);
    } catch {
      setPdfSourceItem(null);
    }
  };

  const handlePdfPageChange = (newPage: number) => {
    setPdfPage(newPage);
    setPdfHighlight(null);
    setActiveBlockId(null);
    setPdfPageBlocks([]);
  };

  const closePdf = () => {
    setShowPdf(false);
    setPdfSourceItem(null);
    setPdfUrl(null);
    setPdfPage(undefined);
    setPdfHighlight(null);
    setPdfPageBlocks([]);
    setActiveBlockId(null);
    setPdfMaxPage(undefined);
  };

  const mandatoryNotDone = filteredItems.filter((i) => i.mandatory_flag && i.current_status !== "DONE").length;
  const highRiskCount = filteredItems.filter((i) => i.risk_level === "HIGH").length;
  const blockedCount = filteredItems.filter((i) => i.current_status === "BLOCKED").length;

  const hasActiveFilter = !!(statusFilter || riskFilter || mandatoryFilter || ownerFilter || appliedKeyword);
  const resetFilters = () => {
    setStatusFilter(""); setRiskFilter(""); setMandatoryFilter("");
    setOwnerFilter(""); setKeyword(""); setAppliedKeyword("");
  };

  // owner filter options
  const ownerOptions = [
    { value: "", label: "담당자 전체" },
    ...assignableMembers.map((m) => ({ value: m.user_id, label: m.user_name || m.user_email })),
  ];

  return (
    <div className="flex gap-4 h-[calc(100vh-220px)]">
      {/* ── 좌측: 체크리스트 목록 ──────────────────────────────────── */}
      <div className="w-56 flex flex-col shrink-0 lg:w-72">
        <div className="flex items-center justify-between mb-3">
          <span className="text-sm font-semibold">체크리스트</span>
          {canEdit && (
            <button onClick={() => setShowCreateCl(!showCreateCl)} className="text-xs text-blue-600 hover:underline">
              {showCreateCl ? "취소" : "+ 새 체크리스트"}
            </button>
          )}
        </div>
        {showCreateCl && (
          <div className="bg-white border rounded p-3 mb-3 space-y-2">
            <input className="w-full border px-2 py-1 rounded text-sm" placeholder="제목" aria-label="체크리스트 제목"
              value={newClTitle} onChange={(e) => setNewClTitle(e.target.value)} />
            <select className="w-full border px-2 py-1 rounded text-sm" aria-label="유형"
              value={newClType} onChange={(e) => setNewClType(e.target.value)}>
              {Object.entries(CHECKLIST_TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
            <button onClick={handleCreateChecklist} className="w-full px-3 py-1.5 bg-blue-600 text-white rounded text-sm">생성</button>
          </div>
        )}
        <div className="flex-1 overflow-auto space-y-2">
          {checklists.map((c) => {
            const pct = c.total_count > 0 ? Math.round((c.done_count / c.total_count) * 100) : 0;
            return (
              <div key={c.id} role="button" tabIndex={0}
                onKeyDown={(e) => { if (e.key === "Enter") loadItems(c.id); }}
                className={`bg-white border rounded p-3 cursor-pointer text-sm transition-colors ${selectedCl === c.id ? "border-blue-500 ring-1 ring-blue-200" : "hover:bg-gray-50"}`}
                onClick={() => loadItems(c.id)}>
                <div className="font-medium">{c.title}</div>
                <div className="flex items-center gap-2 text-xs text-gray-500 mt-1">
                  <span className="px-1.5 py-0.5 bg-gray-100 rounded">{CHECKLIST_TYPES[c.checklist_type] || c.checklist_type}</span>
                  <span>{c.done_count}/{c.total_count} 완료</span>
                </div>
                {c.total_count > 0 && (
                  <div className="w-full bg-gray-200 rounded h-1.5 mt-2">
                    <div className={`h-1.5 rounded transition-all ${pct === 100 ? "bg-green-500" : "bg-blue-500"}`} style={{ width: `${pct}%` }} />
                  </div>
                )}
              </div>
            );
          })}
          {checklists.length === 0 && <div className="text-center text-gray-400 py-8 text-sm">체크리스트가 없습니다</div>}
        </div>
      </div>

      {/* ── 중앙: 항목 목록 ────────────────────────────────────────── */}
      <div className={`flex-1 flex flex-col min-w-0 ${showPdf ? "max-w-[50%]" : ""}`}>
        {selectedCl ? (
          <>
            <div className="flex items-center gap-3 mb-2 flex-wrap">
              <h2 className="font-semibold text-sm">{selectedChecklist?.title}</h2>
              {mandatoryNotDone > 0 && <span className="text-xs bg-red-100 text-red-700 px-2 py-0.5 rounded" role="status">필수 미완료 {mandatoryNotDone}</span>}
              {highRiskCount > 0 && <span className="text-xs bg-orange-100 text-orange-700 px-2 py-0.5 rounded" role="status">고위험 {highRiskCount}</span>}
              {blockedCount > 0 && <span className="text-xs bg-red-100 text-red-700 px-2 py-0.5 rounded" role="status">차단 {blockedCount}</span>}
              <span className="text-xs text-gray-400">{filteredItems.length}건</span>
              {showPdf && (
                <button onClick={closePdf} className="ml-auto text-xs text-gray-500 hover:text-red-500">PDF 닫기</button>
              )}
            </div>

            <FilterBar
              toggleFilters={[{ label: "상태", options: STATUS_OPTIONS, value: statusFilter, onChange: setStatusFilter }]}
              selectFilters={[
                { label: "위험도", options: RISK_OPTIONS, value: riskFilter, onChange: setRiskFilter },
                { label: "필수여부", options: MANDATORY_OPTIONS, value: mandatoryFilter, onChange: setMandatoryFilter },
                { label: "담당자", options: ownerOptions, value: ownerFilter, onChange: setOwnerFilter },
              ]}
              keyword={{ value: keyword, onChange: setKeyword, onSearch: () => setAppliedKeyword(keyword), placeholder: "항목 검색..." }}
              hasActiveFilter={hasActiveFilter}
              onReset={resetFilters}
              actions={canEdit ? (
                <button onClick={() => setShowCreateItem(!showCreateItem)}
                  className="px-3 py-1 bg-blue-600 text-white rounded text-xs">
                  {showCreateItem ? "취소" : "+ 항목 추가"}
                </button>
              ) : undefined}
            />

            {showCreateItem && (
              <div className="bg-blue-50 border border-blue-100 rounded p-3 mb-3 space-y-2">
                <textarea value={newItemForm.item_text} rows={2}
                  onChange={(e) => setNewItemForm({ ...newItemForm, item_text: e.target.value })}
                  className="w-full border rounded px-2 py-1 text-sm" placeholder="항목 내용 *" aria-label="항목 내용" />
                <div className="flex gap-2 flex-wrap items-center">
                  <label className="flex items-center gap-1 text-xs">
                    <input type="checkbox" checked={newItemForm.mandatory_flag}
                      onChange={(e) => setNewItemForm({ ...newItemForm, mandatory_flag: e.target.checked })} /> 필수
                  </label>
                  <select value={newItemForm.risk_level} onChange={(e) => setNewItemForm({ ...newItemForm, risk_level: e.target.value })}
                    className="border rounded px-2 py-1 text-xs" aria-label="위험도">
                    <option value="NONE">없음</option><option value="LOW">낮음</option>
                    <option value="MEDIUM">중간</option><option value="HIGH">높음</option>
                  </select>
                  <button onClick={handleCreateItem} className="px-3 py-1 bg-blue-600 text-white rounded text-xs ml-auto">추가</button>
                </div>
              </div>
            )}

            <div className="flex-1 overflow-auto space-y-1" role="list" aria-label="체크리스트 항목">
              {loadingItems ? (
                <div className="text-center text-gray-400 py-8 text-sm">로딩 중...</div>
              ) : filteredItems.length === 0 ? (
                <div className="text-center text-gray-400 py-8 text-sm">
                  {items.length > 0 && hasActiveFilter ? "필터 조건에 맞는 항목이 없습니다" : "항목이 없습니다"}
                </div>
              ) : filteredItems.map((item) => (
                <ItemRow key={item.id} item={item} projectId={id} checklistId={selectedCl!}
                  canEdit={canEdit} editing={editingId === item.id}
                  editForm={editForm} setEditForm={setEditForm}
                  members={assignableMembers} reviews={expandedReviews[item.id]}
                  memberNameMap={memberNameMap}
                  pdfActive={pdfSourceItem === item.id}
                  onStartEdit={() => startEdit(item)} onSaveEdit={saveEdit}
                  onCancelEdit={() => setEditingId(null)} onStatusChange={handleStatusChange}
                  onViewSource={() => handleViewSource(item)}
                  onLoadReviews={async (itemId) => {
                    const r = await checklistApi.listReviews(id, selectedCl!, itemId, 3);
                    setExpandedReviews((prev) => ({ ...prev, [itemId]: r }));
                  }}
                  onLoadAllReviews={async (itemId) => {
                    const r = await checklistApi.listReviews(id, selectedCl!, itemId);
                    setExpandedReviews((prev) => ({ ...prev, [itemId]: r }));
                  }} />
              ))}
            </div>
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">좌측에서 체크리스트를 선택하세요</div>
        )}
      </div>

      {/* ── 우측: PDF 뷰어 ────────────────────────────────────────── */}
      {showPdf && (
        <div className="w-[38%] min-w-[320px] flex flex-col shrink-0">
          <div className="flex items-center gap-2 text-xs text-gray-500 mb-1">
            <span>PDF {pdfPage ? `- p.${pdfPage}` : ""}</span>
            {pdfPageBlocks.some((b) => b.bbox_json) && (
              <span className="bg-orange-100 text-orange-600 px-1.5 py-0.5 rounded text-[10px]">
                {pdfPageBlocks.filter((b) => b.bbox_json).length}개 근거 표시 중
              </span>
            )}
            <button onClick={closePdf} className="ml-auto text-gray-400 hover:text-red-500 text-xs">닫기</button>
          </div>
          <div className="flex-1 min-h-0 bg-gray-50 rounded border">
            <PdfViewer url={pdfUrl} page={pdfPage} highlight={pdfHighlight}
              pageBlocks={pdfPageBlocks}
              activeBlockId={activeBlockId}
              onPageChange={handlePdfPageChange}
              maxPage={pdfMaxPage}
              onBlockClick={(block) => setActiveBlockId(block.id)} />
          </div>
          {/* active block raw text preview */}
          {activeBlockId && (() => {
            const block = pdfPageBlocks.find((b) => b.id === activeBlockId);
            if (!block) return null;
            return (
              <div className={`mt-1 p-2 rounded text-xs max-h-24 overflow-auto ${excerptTypeStyle(block.excerpt_type)}`}>
                <div className="flex items-center gap-1.5 mb-1">
                  {block.anchor_label && <span className="font-mono font-medium text-gray-700">{block.anchor_label}</span>}
                  {block.excerpt_type && (
                    <ExcerptTypeBadge type={block.excerpt_type} />
                  )}
                </div>
                <div className="text-gray-700 leading-relaxed line-clamp-3">{block.raw_text}</div>
              </div>
            );
          })()}
        </div>
      )}
    </div>
  );
}

// ── ItemRow (인라인 편집 유지) ────────────────────────────────────────
function ItemRow({ item, projectId, checklistId, canEdit, editing, editForm, setEditForm,
  members, reviews, memberNameMap, pdfActive,
  onStartEdit, onSaveEdit, onCancelEdit, onStatusChange, onViewSource,
  onLoadReviews, onLoadAllReviews }: {
  item: any; projectId: string; checklistId: string; canEdit: boolean;
  editing: boolean; editForm: any; setEditForm: (f: any) => void;
  members: ProjectMemberDto[]; reviews?: any[];
  memberNameMap: Map<string, string>;
  pdfActive: boolean;
  onStartEdit: () => void; onSaveEdit: () => void; onCancelEdit: () => void;
  onStatusChange: (itemId: string, status: string) => void;
  onViewSource: () => void;
  onLoadReviews: (itemId: string) => void;
  onLoadAllReviews: (itemId: string) => void;
}) {
  const rowStyle = RISK_ROW_STYLES[item.current_status === "BLOCKED" ? "BLOCKED" : item.risk_level] || "";
  const ownerName = item.owner_user_id ? (memberNameMap.get(item.owner_user_id) || item.owner_user_id.slice(0, 8)) : null;

  if (editing) {
    return (
      <div className="bg-blue-50 border border-blue-200 rounded p-3 space-y-2" role="listitem">
        <textarea value={editForm.item_text} rows={2} onChange={(e) => setEditForm({ ...editForm, item_text: e.target.value })}
          className="w-full border rounded px-2 py-1 text-sm" aria-label="항목 내용" />
        <div className="flex gap-2 flex-wrap items-center">
          <label className="flex items-center gap-1 text-xs">
            <input type="checkbox" checked={editForm.mandatory_flag}
              onChange={(e) => setEditForm({ ...editForm, mandatory_flag: e.target.checked })} /> 필수
          </label>
          <select value={editForm.risk_level} onChange={(e) => setEditForm({ ...editForm, risk_level: e.target.value })}
            className="border rounded px-2 py-1 text-xs" aria-label="위험도">
            <option value="NONE">없음</option><option value="LOW">낮음</option>
            <option value="MEDIUM">중간</option><option value="HIGH">높음</option>
          </select>
          <select value={editForm.owner_user_id} onChange={(e) => setEditForm({ ...editForm, owner_user_id: e.target.value })}
            className="border rounded px-2 py-1 text-xs" aria-label="담당자">
            <option value="">담당자 미지정</option>
            {members.map((m) => <option key={m.user_id} value={m.user_id}>{m.user_name || m.user_email}</option>)}
          </select>
        </div>
        <div className="flex gap-2 items-center">
          <input value={editForm.action_comment} onChange={(e) => setEditForm({ ...editForm, action_comment: e.target.value })}
            className="border rounded px-2 py-1 text-xs flex-1" placeholder="조치 메모" aria-label="조치 메모" />
          <button onClick={onSaveEdit} className="px-3 py-1 bg-blue-600 text-white rounded text-xs">저장</button>
          <button onClick={onCancelEdit} className="px-3 py-1 border rounded text-xs">취소</button>
        </div>
      </div>
    );
  }

  return (
    <div className={`flex items-start gap-3 bg-white border rounded px-3 py-2.5 text-sm group transition-colors ${rowStyle} ${pdfActive ? "ring-2 ring-blue-300" : ""}`} role="listitem">
      {canEdit ? (
        <input type="checkbox" checked={item.current_status === "DONE"}
          onChange={() => onStatusChange(item.id, item.current_status === "DONE" ? "TODO" : "DONE")}
          className="w-4 h-4 mt-0.5 shrink-0 cursor-pointer" aria-label={`${item.item_code} 완료 토글`} />
      ) : (
        <input type="checkbox" checked={item.current_status === "DONE"} disabled
          className="w-4 h-4 mt-0.5 shrink-0 opacity-50" aria-label={`${item.item_code} 상태`} />
      )}

      <span className="font-mono text-xs text-gray-400 shrink-0 mt-0.5 w-16">{item.item_code}</span>

      <div className="flex-1 min-w-0">
        <div className={item.current_status === "DONE" ? "line-through text-gray-400" : ""}>{item.item_text}</div>
        <div className="flex gap-2 mt-1 flex-wrap items-center">
          {ownerName && <span className="text-[11px] text-blue-600">담당: {ownerName}</span>}
          {item.due_hint && <span className="text-[11px] text-gray-400">마감: {item.due_hint}</span>}
          {item.risk_note && <span className="text-[11px] text-orange-600" aria-label="리스크 메모">! {item.risk_note}</span>}
          {item.updated_at && <span className="text-[11px] text-gray-300">{new Date(item.updated_at).toLocaleDateString("ko-KR")}</span>}
        </div>
        {item.action_comment && <div className="text-[11px] text-gray-500 mt-0.5 bg-gray-50 px-2 py-0.5 rounded">{item.action_comment}</div>}

        {/* Requirement linkage */}
        {item.linked_requirement_id && (
          <div className="flex items-center gap-2 mt-1">
            <Link href={`/projects/${projectId}/requirements/${item.linked_requirement_id}`}
              className="text-[11px] text-blue-600 hover:underline inline-flex items-center gap-0.5"
              onClick={(e) => e.stopPropagation()}>
              <span className="bg-blue-50 px-1.5 py-0.5 rounded">요구사항</span>
            </Link>
            {item.source_excerpt_id && (
              <button onClick={(e) => { e.stopPropagation(); onViewSource(); }}
                className={`text-[11px] inline-flex items-center gap-0.5 hover:underline ${pdfActive ? "text-orange-600 font-medium" : "text-purple-600"}`}>
                <span className={`px-1.5 py-0.5 rounded ${pdfActive ? "bg-orange-50" : "bg-purple-50"}`}>
                  근거 PDF
                </span>
              </button>
            )}
          </div>
        )}

        {reviews === undefined ? (
          <button onClick={() => onLoadReviews(item.id)} className="text-[11px] text-blue-500 hover:underline mt-0.5">이력 보기</button>
        ) : reviews.length > 0 ? (
          <div className="mt-1 space-y-0.5">
            {reviews.map((r: any) => (
              <div key={r.id} className="text-[11px] text-gray-400 flex gap-1">
                <span className={r.change_type === "STATUS_CHANGED" ? "text-blue-500" : r.change_type === "OWNER_CHANGED" ? "text-purple-500" : "text-gray-500"}>
                  {r.change_type === "STATUS_CHANGED" ? "상태" : r.change_type === "OWNER_CHANGED" ? "담당" : "메모"}
                </span>
                {r.before_value && r.after_value && <span>{r.before_value} → {r.after_value}</span>}
                {r.comment && <span className="truncate">{r.comment}</span>}
                <span className="ml-auto shrink-0">{r.created_at ? new Date(r.created_at).toLocaleDateString("ko-KR") : ""}</span>
              </div>
            ))}
            <button onClick={() => onLoadAllReviews(item.id)} className="text-[11px] text-blue-500 hover:underline">전체 이력</button>
          </div>
        ) : <div className="text-[11px] text-gray-300 mt-0.5">이력 없음</div>}
      </div>

      <div className="flex items-center gap-1.5 shrink-0">
        {item.mandatory_flag && <span className="text-xs text-red-600 font-bold bg-red-50 px-1.5 py-0.5 rounded" aria-label="필수">필수</span>}
        <StatusBadge value={item.risk_level} />
        {canEdit && item.current_status !== "DONE" && (
          <select value={item.current_status} onChange={(e) => onStatusChange(item.id, e.target.value)}
            className="border rounded px-1 py-0.5 text-xs bg-white" aria-label="상태 변경">
            <option value="TODO">TODO</option><option value="IN_PROGRESS">진행중</option>
            <option value="DONE">완료</option><option value="BLOCKED">차단</option>
          </select>
        )}
        {item.current_status === "DONE" && <StatusBadge value="DONE" />}
        {canEdit && (
          <button onClick={onStartEdit} aria-label="수정"
            className="text-xs text-gray-400 hover:text-blue-600 opacity-0 group-hover:opacity-100 transition-opacity">수정</button>
        )}
      </div>
    </div>
  );
}

function ExcerptTypeBadge({ type }: { type?: string }) {
  const styles: Record<string, string> = {
    PARAGRAPH: "bg-blue-100 text-blue-600",
    TABLE: "bg-purple-100 text-purple-600",
    LIST: "bg-green-100 text-green-600",
    HEADER: "bg-yellow-100 text-yellow-700",
    FOOTNOTE: "bg-gray-100 text-gray-500",
  };
  const labels: Record<string, string> = {
    PARAGRAPH: "문단", TABLE: "표", LIST: "목록", HEADER: "제목", FOOTNOTE: "각주",
  };
  if (!type) return null;
  return (
    <span className={`text-[10px] px-1 py-0.5 rounded ${styles[type] || "bg-gray-100 text-gray-500"}`}>
      {labels[type] || type}
    </span>
  );
}
