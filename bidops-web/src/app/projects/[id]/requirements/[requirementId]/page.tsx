"use client";

import { useParams, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useEffect, useState, useCallback, useRef } from "react";
import { requirementApi, documentApi, checklistApi, activityApi, memberApi, type ProjectMemberDto } from "@/lib/api";
import { useProjectRole } from "@/lib/useProjectRole";
import StatusBadge from "@/components/common/StatusBadge";
import PdfViewer, { parseBboxJson, excerptTypeStyle } from "@/components/common/PdfViewer";
import type { BboxHighlight, SourceBlock } from "@/components/common/PdfViewer";

const CATEGORY_LABELS: Record<string, string> = {
  BUSINESS_OVERVIEW: "사업개요", BACKGROUND: "배경", OBJECTIVE: "목표", SCOPE: "범위",
  FUNCTIONAL: "기능", NON_FUNCTIONAL: "비기능", PERFORMANCE: "성능", SECURITY: "보안",
  QUALITY: "품질", TESTING: "시험", DATA_INTEGRATION: "데이터/연계", UI_UX: "UI/UX",
  INFRASTRUCTURE: "인프라", PERSONNEL: "인력", TRACK_RECORD: "실적", SCHEDULE: "일정",
  DELIVERABLE: "산출물", SUBMISSION: "제출", PROPOSAL_GUIDE: "제안안내", EVALUATION: "평가",
  PRESENTATION: "발표", MAINTENANCE: "유지보수", TRAINING: "교육", LEGAL: "법률", ETC: "기타",
};

const ALL_CATEGORIES = Object.keys(CATEGORY_LABELS);

type CenterTab = "basic" | "analysis" | "checklist" | "history";

export default function RequirementDetailPage() {
  const { id: projectId, requirementId } = useParams() as { id: string; requirementId: string };
  const searchParams = useSearchParams();
  const { canEdit, canReview, isOwner } = useProjectRole(projectId);

  const initialSourceExcerptId = searchParams.get("sourceExcerptId");
  const reviewMode = searchParams.get("mode") === "review";
  const deepLinkApplied = useRef(false);

  const [centerTab, setCenterTab] = useState<CenterTab>("analysis");
  const [reviewDone, setReviewDone] = useState(false);

  const [detail, setDetail] = useState<any>(null);
  const [analysis, setAnalysis] = useState<any>(null);
  const [review, setReview] = useState<any>(null);
  const [sources, setSources] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  // 이전/다음 네비게이션용 목록
  const [reqList, setReqList] = useState<any[]>([]);
  const [autoAdvance, setAutoAdvance] = useState(true);

  // PDF viewer
  const [pdfUrl, setPdfUrl] = useState<string | null>(null);
  const [pdfMaxPage, setPdfMaxPage] = useState<number | undefined>(undefined);
  const [pdfPage, setPdfPage] = useState<number | undefined>(undefined);
  const [pdfHighlight, setPdfHighlight] = useState<BboxHighlight | null>(null);
  const [activeBlockId, setActiveBlockId] = useState<string | null>(null);

  // linked checklist items + history
  const [linkedItems, setLinkedItems] = useState<any[]>([]);
  const [activities, setActivities] = useState<any[]>([]);

  // edit states
  const [editing, setEditing] = useState(false);
  const [editForm, setEditForm] = useState<any>({});
  const [analysisEditing, setAnalysisEditing] = useState(false);
  const [analysisForm, setAnalysisForm] = useState<any>({});
  const [reviewComment, setReviewComment] = useState("");
  const [saving, setSaving] = useState(false);

  // 검토 의견 임시저장
  const draftKey = `bidops_review_draft_${requirementId}`;
  useEffect(() => {
    try {
      const saved = localStorage.getItem(draftKey);
      if (saved) setReviewComment(saved);
    } catch {}
  }, [draftKey]);
  useEffect(() => {
    try {
      if (reviewComment.trim()) localStorage.setItem(draftKey, reviewComment);
      else localStorage.removeItem(draftKey);
    } catch {}
  }, [reviewComment, draftKey]);

  // left panel: source list collapsed + filter
  const [sourceListOpen, setSourceListOpen] = useState(true);
  const [sourceFilter, setSourceFilter] = useState("");

  // members (for reviewer name resolution)
  const [members, setMembers] = useState<ProjectMemberDto[]>([]);
  useEffect(() => { memberApi.list(projectId).then(setMembers).catch(() => {}); }, [projectId]);

  // 요구사항 전체 목록 (네비게이션용)
  useEffect(() => {
    requirementApi.list(projectId, "size=200")
      .then((d) => setReqList(d.items || []))
      .catch(() => {});
  }, [projectId]);

  // 키보드 단축키: ←/→ 이전/다음
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
      if (e.altKey && e.key === "ArrowLeft" && prevReq) { e.preventDefault(); navigateTo(prevReq.id); }
      if (e.altKey && e.key === "ArrowRight" && nextReq) { e.preventDefault(); navigateTo(nextReq.id); }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  });
  const memberNameMap = new Map(members.map((m) => [m.user_id, m.user_name || m.user_email]));

  const loadDetail = useCallback(() => {
    setLoading(true);
    Promise.all([
      requirementApi.get(projectId, requirementId),
      requirementApi.getAnalysis(projectId, requirementId).catch(() => null),
      requirementApi.getReview(projectId, requirementId).catch(() => null),
      requirementApi.sources(projectId, requirementId).catch(() => null),
    ]).then(([det, ana, rev, src]) => {
      setDetail(det);
      setAnalysis(ana);
      setReview(rev);
      setSources(src);

      if (det?.requirement?.document_id) {
        documentApi.get(projectId, det.requirement.document_id)
          .then((doc: any) => {
            setPdfUrl(doc.viewer_url || doc.storage_path || null);
            setPdfMaxPage(doc.page_count || undefined);
          })
          .catch(() => setPdfUrl(null));
      }
    }).finally(() => setLoading(false));

    loadLinkedChecklist();
    activityApi.list(projectId, `target_type=requirement&size=20`)
      .then((d) => setActivities(
        (d.items || []).filter((a: any) => a.target_id === requirementId)
      ))
      .catch(() => setActivities([]));
  }, [projectId, requirementId]);

  const loadLinkedChecklist = useCallback(() => {
    checklistApi.list(projectId).then((lists: any[]) => {
      const all: any[] = [];
      return Promise.all(
        (lists || []).map((cl: any) =>
          checklistApi.items(projectId, cl.id, "requirement_id=" + requirementId)
            .then((items: any[]) => items.forEach((i: any) => all.push({ ...i, checklist_title: cl.title, checklist_id: cl.id })))
            .catch(() => {})
        )
      ).then(() => setLinkedItems(all));
    }).catch(() => setLinkedItems([]));
  }, [projectId, requirementId]);

  useEffect(() => { loadDetail(); }, [loadDetail]);

  // deep link: sourceExcerptId in URL → activate block + navigate PDF
  useEffect(() => {
    if (deepLinkApplied.current || !initialSourceExcerptId || !sources) return;
    const blocks = sources.source_text_blocks || [];
    const target = blocks.find((b: any) => b.id === initialSourceExcerptId);
    if (target) {
      deepLinkApplied.current = true;
      handleSourceBlockClick(target);
      setTimeout(() => {
        const el = document.getElementById("source-block-" + target.id);
        el?.scrollIntoView({ behavior: "smooth", block: "center" });
      }, 200);
    }
  }, [sources, initialSourceExcerptId]);

  // ── Source block click → PDF navigation ──────────────────────────
  const handleSourceBlockClick = (block: any) => {
    setActiveBlockId(block.id);
    setPdfPage(block.page_no);

    const bbox = parseBboxJson(block.bbox_json, block.anchor_label);
    if (bbox) {
      // bbox 있으면 정확한 위치 하이라이트
      setPdfHighlight(bbox);
    } else {
      // bbox 없으면 페이지 이동 + anchor fallback
      setPdfHighlight(block.anchor_label
        ? { x: 5, y: 10, w: 90, h: 8, label: `${block.anchor_label} (p.${block.page_no})` }
        : null);
    }
  };

  // page ref button click (no specific block)
  const handlePageRefClick = (pageNo: number) => {
    setPdfPage(pageNo);
    setPdfHighlight(null);
    setActiveBlockId(null);
  };

  // PDF overlay block click → sync source list
  const handlePdfBlockClick = (block: SourceBlock) => {
    setActiveBlockId(block.id);
    setSourceListOpen(true);
    setTimeout(() => {
      const el = document.getElementById("source-block-" + block.id);
      el?.scrollIntoView({ behavior: "smooth", block: "center" });
    }, 100);
  };

  // PDF page navigation
  const handlePdfPageChange = (newPage: number) => {
    setPdfPage(newPage);
    setPdfHighlight(null);
    setActiveBlockId(null);
  };

  // current page blocks for multi-highlight
  const currentPageBlocks: SourceBlock[] = (sources?.source_text_blocks || [])
    .filter((b: any) => b.page_no === pdfPage)
    .map((b: any) => ({
      id: b.id,
      page_no: b.page_no,
      excerpt_type: b.excerpt_type,
      anchor_label: b.anchor_label,
      raw_text: b.raw_text,
      bbox_json: b.bbox_json,
      link_type: b.link_type,
    }));

  // ── Basic info edit ────────────────────────────────────────────
  const startEdit = () => {
    const r = detail.requirement;
    setEditForm({
      title: r.title || "",
      category: r.category || "",
      mandatory_flag: r.mandatory_flag,
      evidence_required_flag: r.evidence_required_flag,
      analysis_status: r.analysis_status || "",
    });
    setEditing(true);
  };

  const saveEdit = async () => {
    setSaving(true);
    try {
      await requirementApi.update(projectId, requirementId, editForm);
      setEditing(false);
      loadDetail();
    } catch (err: any) {
      alert(err.message || "저장 실패");
    } finally { setSaving(false); }
  };

  // ── Analysis edit ──────────────────────────────────────────────
  const startAnalysisEdit = () => {
    setAnalysisForm({
      fact_summary: analysis?.fact_summary || "",
      interpretation_summary: analysis?.interpretation_summary || "",
      intent_summary: analysis?.intent_summary || "",
      proposal_point: analysis?.proposal_point || "",
      implementation_approach: analysis?.implementation_approach || "",
      expected_deliverables: analysis?.expected_deliverables || [],
      differentiation_point: analysis?.differentiation_point || "",
      risk_note: analysis?.risk_note || [],
      query_needed: analysis?.query_needed || false,
      fact_level: analysis?.fact_level || "REVIEW_NEEDED",
    });
    setAnalysisEditing(true);
  };

  const saveAnalysis = async () => {
    setSaving(true);
    try {
      const updated = await requirementApi.updateAnalysis(projectId, requirementId, analysisForm);
      setAnalysis(updated);
      setAnalysisEditing(false);
    } catch (err: any) {
      alert(err.message || "저장 실패");
    } finally { setSaving(false); }
  };

  // ── 이전/다음 네비게이션 ──────────────────────────────────────
  const navList = reviewMode
    ? reqList.filter((r: any) => !r.review_status || r.review_status === "NOT_REVIEWED" || r.id === requirementId)
    : reqList;
  const currentIndex = navList.findIndex((r: any) => r.id === requirementId);
  const prevReq = currentIndex > 0 ? navList[currentIndex - 1] : null;
  const nextReq = currentIndex >= 0 && currentIndex < navList.length - 1 ? navList[currentIndex + 1] : null;

  const navigateTo = (id: string) => {
    if (reviewComment.trim() && !confirm("작성 중인 검토 의견이 있습니다. 이동하시겠습니까?")) return;
    try { localStorage.removeItem(draftKey); } catch {}
    const suffix = reviewMode ? "?mode=review" : "";
    window.location.href = `/projects/${projectId}/requirements/${id}${suffix}`;
  };

  // ── Review status ──────────────────────────────────────────────
  const handleReviewStatus = async (status: string) => {
    setSaving(true);
    try {
      const updated = await requirementApi.changeReviewStatus(projectId, requirementId, {
        review_status: status,
        review_comment: reviewComment || undefined,
      });
      setReview(updated);
      setReviewComment("");
      try { localStorage.removeItem(draftKey); } catch {}
      loadDetail();

      // 자동 이동
      if (autoAdvance) {
        // reviewMode에서는 다음 미검토 항목으로
        const nextTarget = reviewMode
          ? reqList.find((r: any) => r.id !== requirementId && (!r.review_status || r.review_status === "NOT_REVIEWED"))
          : nextReq;

        if (nextTarget) {
          setTimeout(() => navigateTo(nextTarget.id), 300);
        } else {
          setReviewDone(true);
        }
      }
    } catch (err: any) {
      alert(err.message || "상태 변경 실패");
    } finally { setSaving(false); }
  };

  if (loading) {
    return <div className="text-center text-gray-400 py-12">로딩 중...</div>;
  }

  if (!detail) {
    return <div className="text-center text-gray-400 py-12">요구사항을 찾을 수 없습니다</div>;
  }

  const req = detail.requirement;
  const sourceBlocks = sources?.source_text_blocks || [];
  const filteredSourceBlocks = sourceFilter
    ? sourceBlocks.filter((b: any) => {
        const kw = sourceFilter.toLowerCase();
        return (b.raw_text || "").toLowerCase().includes(kw)
          || (b.anchor_label || "").toLowerCase().includes(kw);
      })
    : sourceBlocks;

  const CENTER_TABS: { key: CenterTab; label: string }[] = [
    { key: "basic", label: "원문/기본" },
    { key: "analysis", label: "AI 분석" },
    { key: "checklist", label: `체크 (${linkedItems.length})` },
    { key: "history", label: "이력" },
  ];

  return (
    <div className="flex flex-col gap-2 relative">
      {/* 검토 완료 오버레이 */}
      {reviewDone && (
        <div className="absolute inset-0 z-50 bg-white/90 flex flex-col items-center justify-center gap-4 rounded-lg">
          <div className="text-2xl">✅</div>
          <div className="text-lg font-bold text-green-700">모든 미검토 항목을 처리했습니다</div>
          <div className="text-sm text-gray-500">
            {reqList.filter((r: any) => r.review_status === "APPROVED").length}건 승인 /
            {reqList.filter((r: any) => r.review_status === "HOLD").length}건 보류 /
            {reqList.filter((r: any) => r.review_status === "NEEDS_UPDATE").length}건 수정필요
          </div>
          <Link href={`/projects/${projectId}/requirements`}
            className="px-6 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors text-sm">
            목록으로 돌아가기
          </Link>
        </div>
      )}

      {/* ── Header ─────────────────────────────────────────────── */}
      <div className="flex items-center gap-3 flex-wrap">
        {reviewMode && (
          <span className="text-[10px] bg-blue-100 text-blue-700 px-2 py-0.5 rounded font-medium">검토 모드</span>
        )}
        <Link href={`/projects/${projectId}/requirements`}
          className="text-sm text-gray-500 hover:underline">&larr; 목록</Link>

        {/* 이전/다음 네비게이션 */}
        <div className="flex items-center gap-1 border rounded px-1">
          <button onClick={() => prevReq && navigateTo(prevReq.id)} disabled={!prevReq}
            className="px-1.5 py-0.5 text-xs hover:bg-gray-100 rounded disabled:opacity-30 disabled:cursor-not-allowed"
            title={prevReq ? `이전: ${prevReq.requirement_code}` : ""}>&larr;</button>
          {navList.length > 0 && (
            <span className="text-[10px] text-gray-500 min-w-[50px] text-center">
              {currentIndex + 1}/{navList.length}
              {reviewMode && navList.length < reqList.length && <span className="text-gray-300"> ({reqList.length})</span>}
            </span>
          )}
          <button onClick={() => nextReq && navigateTo(nextReq.id)} disabled={!nextReq}
            className="px-1.5 py-0.5 text-xs hover:bg-gray-100 rounded disabled:opacity-30 disabled:cursor-not-allowed"
            title={nextReq ? `다음: ${nextReq.requirement_code}` : ""}>&rarr;</button>
        </div>

        <span className="font-mono font-bold text-lg">{req.requirement_code}</span>
        <StatusBadge value={req.review_status || "NOT_REVIEWED"} />
        <StatusBadge value={req.fact_level || "REVIEW_NEEDED"} />
        {req.mandatory_flag && <span className="text-red-500 text-xs font-bold bg-red-50 px-1.5 py-0.5 rounded">필수</span>}
        {req.query_needed && <span className="text-orange-500 text-xs font-bold bg-orange-50 px-1.5 py-0.5 rounded">질의필요</span>}
        {req.extraction_status === "MERGED" && (
          <span className="text-purple-700 text-[10px] font-bold bg-purple-50 px-1.5 py-0.5 rounded" title={req.merge_reason || ""}>
            병합 ({req.original_req_nos})
          </span>
        )}
        {req.original_req_nos && req.extraction_status !== "MERGED" && (
          <span className="text-gray-400 text-[10px] font-mono" title="원문 번호">{req.original_req_nos}</span>
        )}
        <div className="ml-auto flex gap-2 items-center">
          <label className="flex items-center gap-1 text-[10px] text-gray-400 cursor-pointer">
            <input type="checkbox" checked={autoAdvance} onChange={(e) => setAutoAdvance(e.target.checked)}
              className="w-3 h-3" />
            검토 후 자동이동
          </label>
          <Link href={`/projects/${projectId}/search?keyword=${encodeURIComponent(req.title || "")}&category=${req.category || ""}`}
            className="text-xs text-purple-600 hover:underline">
            유사 제안 검색
          </Link>
        </div>
      </div>

      {/* ── 3-Panel Layout (responsive: stack on small screens) ─── */}
      <div className="flex flex-col lg:flex-row gap-3" style={{ height: "calc(100vh - 180px)" }}>

        {/* ═══ LEFT: PDF + Sources ═══ */}
        <div className="w-full lg:w-[38%] lg:min-w-[300px] flex flex-col gap-2 lg:shrink-0 min-h-[250px] lg:min-h-0">
          {/* PDF Viewer */}
          <div className="flex-1 min-h-0 bg-gray-50 rounded border relative">
            {pdfUrl ? (
              <>
                <div className="absolute top-1 left-2 z-30 flex items-center gap-1.5 text-[10px]">
                  <span className="bg-white/80 text-gray-600 px-1.5 py-0.5 rounded shadow-sm">
                    {pdfPage ? `p.${pdfPage}` : "PDF"}
                    {pdfMaxPage ? `/${pdfMaxPage}` : ""}
                  </span>
                  {currentPageBlocks.length > 0 && (
                    <span className={`px-1.5 py-0.5 rounded shadow-sm ${
                      currentPageBlocks.some((b) => b.bbox_json)
                        ? "bg-orange-100 text-orange-600"
                        : "bg-blue-100 text-blue-600"
                    }`}>
                      {currentPageBlocks.length}개 근거
                      {!currentPageBlocks.some((b) => b.bbox_json) && " (위치 미확인)"}
                    </span>
                  )}
                </div>
                <PdfViewer url={pdfUrl} page={pdfPage} highlight={pdfHighlight}
                  pageBlocks={currentPageBlocks}
                  activeBlockId={activeBlockId}
                  onBlockClick={handlePdfBlockClick}
                  onPageChange={handlePdfPageChange}
                  maxPage={pdfMaxPage} />
              </>
            ) : loading ? (
              <div className="flex items-center justify-center h-full text-sm text-gray-400">PDF 로딩 중...</div>
            ) : (
              <div className="flex flex-col items-center justify-center h-full text-sm text-gray-400 gap-2">
                <span>PDF 파일을 불러올 수 없습니다</span>
                <span className="text-[10px]">문서가 삭제되었거나 접근 권한이 없을 수 있습니다</span>
              </div>
            )}
          </div>

          {/* Source Blocks List */}
          <div className="bg-white rounded border overflow-hidden flex flex-col"
            style={{ maxHeight: sourceListOpen ? "40%" : "36px", minHeight: "36px", transition: "max-height 0.2s" }}>
            <button onClick={() => setSourceListOpen(!sourceListOpen)}
              className="flex items-center justify-between px-3 py-2 text-xs font-medium text-gray-700 bg-gray-50 hover:bg-gray-100 shrink-0">
              <span>근거 블록 ({sourceBlocks.length}){sourceFilter && ` - ${filteredSourceBlocks.length}건`}</span>
              <span className="text-gray-400">{sourceListOpen ? "▼" : "▶"}</span>
            </button>
            {sourceListOpen && (
              <div className="overflow-auto flex-1 p-2 space-y-1.5">
                {/* Source block search */}
                {sourceBlocks.length > 0 && (
                  <input value={sourceFilter} onChange={(e) => setSourceFilter(e.target.value)}
                    placeholder="근거 검색..."
                    className="w-full border rounded px-2 py-1 text-[11px] mb-1"
                    aria-label="근거 블록 검색" />
                )}

                {/* Page ref buttons */}
                {sources?.page_refs?.length > 0 && (
                  <div className="flex gap-1 flex-wrap mb-2">
                    {sources.page_refs.map((p: number) => (
                      <button key={p} onClick={() => handlePageRefClick(p)}
                        className={`px-2 py-0.5 text-[10px] rounded transition-colors ${
                          pdfPage === p && !activeBlockId
                            ? "bg-blue-600 text-white"
                            : "bg-blue-100 text-blue-700 hover:bg-blue-200"
                        }`}>
                        p.{p}
                      </button>
                    ))}
                  </div>
                )}

                {sourceBlocks.length === 0 && (
                  <div className="text-xs text-gray-400 py-6 text-center space-y-1">
                    <div>원문 근거가 연결되지 않았습니다</div>
                    <div className="text-[10px]">재분석하면 근거가 자동 연결됩니다</div>
                  </div>
                )}
                {sourceBlocks.length > 0 && filteredSourceBlocks.length === 0 && sourceFilter && (
                  <div className="text-xs text-gray-400 py-4 text-center">검색 결과가 없습니다</div>
                )}

                {filteredSourceBlocks.map((block: any) => {
                  const isActive = block.id === activeBlockId;
                  const typeStyle = excerptTypeStyle(block.excerpt_type);
                  return (
                    <div key={block.id}
                      id={"source-block-" + block.id}
                      className={`rounded p-2.5 cursor-pointer transition-all text-xs ${typeStyle} ${
                        isActive ? "ring-2 ring-orange-400 shadow-sm" : "hover:shadow-sm"
                      }`}
                      onClick={() => handleSourceBlockClick(block)}>
                      <div className="flex items-center gap-1.5 mb-1">
                        <span className="font-mono text-blue-700 font-medium">p.{block.page_no}</span>
                        {block.anchor_label && (
                          <span className="bg-gray-200 text-gray-700 px-1 py-0.5 rounded text-[10px] font-medium">
                            {block.anchor_label}
                          </span>
                        )}
                        <ExcerptTypeBadge type={block.excerpt_type} />
                        {block.link_type === "SUPPORTING" && (
                          <span className="text-[10px] bg-gray-100 text-gray-500 px-1 py-0.5 rounded">보조</span>
                        )}
                        {block.bbox_json ? (
                          <span className="text-[10px] bg-orange-100 text-orange-600 px-1 py-0.5 rounded">위치</span>
                        ) : (
                          <span className="text-[10px] bg-gray-100 text-gray-400 px-1 py-0.5 rounded">페이지</span>
                        )}
                      </div>
                      <div className="text-gray-700 leading-relaxed line-clamp-3">
                        {block.raw_text}
                      </div>
                      {isActive && (
                        <div className="mt-1.5 flex items-center gap-2 text-[10px]">
                          <span className="text-orange-600">PDF에서 위치 확인</span>
                          <span className="text-gray-300">→</span>
                          <button onClick={(e) => { e.stopPropagation(); setCenterTab("analysis"); }}
                            className="text-blue-600 hover:underline">AI 해석 보기</button>
                          <span className="text-gray-300">→</span>
                          <span className="text-gray-400">우측에서 검토</span>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* ═══ CENTER: Basic Info + AI Analysis ═══ */}
        <div className="flex-1 min-w-0 lg:min-w-[260px] flex flex-col min-h-0">
          <nav className="flex gap-1 border-b mb-2 shrink-0">
            {CENTER_TABS.map((t) => (
              <button key={t.key} onClick={() => setCenterTab(t.key)}
                className={`px-3 py-1.5 text-xs border-b-2 transition-colors ${
                  centerTab === t.key ? "border-blue-600 text-blue-600 font-medium" : "border-transparent text-gray-500 hover:text-gray-700"
                }`}>
                {t.label}
              </button>
            ))}
          </nav>

          <div className="bg-white rounded border p-4 overflow-auto flex-1 min-h-0">
            {centerTab === "basic" && (
              <BasicTab req={req} editing={editing} editForm={editForm} setEditForm={setEditForm}
                canEdit={canEdit} saving={saving}
                onStartEdit={startEdit} onSave={saveEdit} onCancel={() => setEditing(false)} />
            )}
            {centerTab === "analysis" && (
              <AnalysisTab analysis={analysis} editing={analysisEditing} form={analysisForm}
                setForm={setAnalysisForm} canEdit={canEdit} saving={saving}
                onStartEdit={startAnalysisEdit} onSave={saveAnalysis} onCancel={() => setAnalysisEditing(false)} />
            )}
            {centerTab === "checklist" && (
              <ChecklistTab items={linkedItems} projectId={projectId} canEdit={canEdit}
                onStatusChange={async (checklistId, itemId, status) => {
                  await checklistApi.changeItemStatus(projectId, checklistId, itemId, { status });
                  loadLinkedChecklist();
                }} />
            )}
            {centerTab === "history" && (
              <HistoryTab activities={activities} projectId={projectId} />
            )}
          </div>
        </div>

        {/* ═══ RIGHT: Human Review ═══ */}
        <div className="w-full lg:w-[22%] lg:min-w-[200px] flex flex-col min-h-0 lg:shrink-0">
          <div className="text-xs font-medium text-gray-700 border-b pb-1.5 mb-2 shrink-0 flex items-center gap-2">
            <span>검토</span>
            <StatusBadge value={review?.review_status || "NOT_REVIEWED"} />
          </div>
          <div className="bg-white rounded border p-3 overflow-auto flex-1 min-h-0">
            <ReviewPanel review={review} canReview={canReview} saving={saving}
              comment={reviewComment} setComment={setReviewComment}
              onChangeStatus={handleReviewStatus}
              memberNameMap={memberNameMap} />
          </div>
          {/* 하단 네비게이션 */}
          <div className="flex items-center justify-between mt-2 shrink-0">
            <button onClick={() => prevReq && navigateTo(prevReq.id)} disabled={!prevReq}
              className="text-xs text-gray-500 hover:text-blue-600 disabled:opacity-30 disabled:cursor-not-allowed">
              &larr; 이전
            </button>
            <span className="text-[10px] text-gray-400">Alt+←/→</span>
            <button onClick={() => nextReq && navigateTo(nextReq.id)} disabled={!nextReq}
              className="text-xs text-gray-500 hover:text-blue-600 disabled:opacity-30 disabled:cursor-not-allowed">
              다음 &rarr;
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── Basic Tab ─────────────────────────────────────────────────────────
function BasicTab({ req, editing, editForm, setEditForm, canEdit, saving, onStartEdit, onSave, onCancel }: any) {
  if (editing) {
    return (
      <div className="space-y-3 text-sm">
        <Field label="제목">
          <input value={editForm.title} onChange={(e) => setEditForm({ ...editForm, title: e.target.value })}
            className="w-full border rounded px-2 py-1 text-sm" />
        </Field>
        <Field label="카테고리">
          <select value={editForm.category} onChange={(e) => setEditForm({ ...editForm, category: e.target.value })}
            className="border rounded px-2 py-1 text-sm">
            {ALL_CATEGORIES.map((c) => <option key={c} value={c}>{CATEGORY_LABELS[c]}</option>)}
          </select>
        </Field>
        <div className="flex gap-4">
          <label className="flex items-center gap-1 text-sm">
            <input type="checkbox" checked={editForm.mandatory_flag}
              onChange={(e) => setEditForm({ ...editForm, mandatory_flag: e.target.checked })} />
            필수
          </label>
          <label className="flex items-center gap-1 text-sm">
            <input type="checkbox" checked={editForm.evidence_required_flag}
              onChange={(e) => setEditForm({ ...editForm, evidence_required_flag: e.target.checked })} />
            증빙필요
          </label>
        </div>
        <div className="flex gap-2 pt-2">
          <button onClick={onSave} disabled={saving}
            className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm disabled:opacity-50">
            {saving ? "저장 중..." : "저장"}
          </button>
          <button onClick={onCancel} className="px-4 py-1.5 border rounded text-sm">취소</button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4 text-sm">
      <div className="flex justify-between items-start">
        <h3 className="font-semibold text-base">{req.title}</h3>
        {canEdit && (
          <button onClick={onStartEdit} className="text-xs text-blue-600 hover:underline shrink-0">수정</button>
        )}
      </div>

      <div className="flex gap-2 flex-wrap">
        <StatusBadge value={req.category} />
        {req.mandatory_flag && <span className="text-xs text-red-500 font-bold">필수</span>}
        {req.evidence_required_flag && <span className="text-xs text-purple-500 font-bold">증빙필요</span>}
        <StatusBadge value={req.analysis_status || "EXTRACTED"} />
      </div>

      <Field label="원문">
        <div className="bg-gray-50 p-3 rounded text-sm whitespace-pre-wrap leading-relaxed">
          {req.original_text || "-"}
        </div>
      </Field>

      <div className="grid grid-cols-2 gap-4 text-xs text-gray-500">
        <div>카테고리: <span className="text-gray-700">{CATEGORY_LABELS[req.category] || req.category}</span></div>
        <div>신뢰도: <span className="text-gray-700">{req.confidence_score != null ? `${(req.confidence_score * 100).toFixed(0)}%` : "-"}</span></div>
        <div>근거수준: <StatusBadge value={req.fact_level || "REVIEW_NEEDED"} /></div>
        <div>질의필요: <span className="text-gray-700">{req.query_needed ? "예" : "아니오"}</span></div>
      </div>
    </div>
  );
}

// ── Analysis Tab (AI 분석 -- 사람 검토와 분리) ───────────────────────
function AnalysisTab({ analysis, editing, form, setForm, canEdit, saving, onStartEdit, onSave, onCancel }: any) {
  if (!analysis || !analysis.requirement_id) {
    return <div className="text-sm text-gray-400 py-8 text-center">AI 분석 결과가 없습니다. 문서 탭에서 분석을 실행하세요.</div>;
  }

  const hasContent = analysis.fact_summary || analysis.interpretation_summary
    || analysis.intent_summary || analysis.proposal_point
    || analysis.implementation_approach || analysis.differentiation_point
    || (analysis.expected_deliverables?.length > 0) || (analysis.risk_note?.length > 0);

  if (!hasContent && !editing) {
    return (
      <div className="space-y-3">
        <div className="text-sm text-gray-400 py-8 text-center">
          분석 결과가 아직 채워지지 않았습니다.<br />
          문서 탭에서 재분석을 실행하면 AI 분석 내용이 생성됩니다.
        </div>
        {canEdit && (
          <div className="text-center">
            <button onClick={onStartEdit} className="text-xs text-blue-600 hover:underline">직접 입력</button>
          </div>
        )}
      </div>
    );
  }

  if (editing) {
    return (
      <div className="space-y-3 text-sm">
        <div className="bg-blue-50 border border-blue-100 rounded p-2 text-xs text-blue-700">
          AI 분석 결과를 실무자가 직접 보정합니다. 검토 상태 변경은 우측 [검토] 패널에서 처리하세요.
        </div>
        <Field label="확정 근거 (원문 기반)">
          <textarea value={form.fact_summary} rows={3}
            onChange={(e) => setForm({ ...form, fact_summary: e.target.value })}
            className="w-full border rounded px-2 py-1 text-sm" />
        </Field>
        <Field label="AI 추론">
          <textarea value={form.interpretation_summary} rows={3}
            onChange={(e) => setForm({ ...form, interpretation_summary: e.target.value })}
            className="w-full border rounded px-2 py-1 text-sm" />
        </Field>
        <Field label="발주처 의도">
          <textarea value={form.intent_summary} rows={2}
            onChange={(e) => setForm({ ...form, intent_summary: e.target.value })}
            className="w-full border rounded px-2 py-1 text-sm" />
        </Field>
        <Field label="제안 포인트">
          <textarea value={form.proposal_point} rows={2}
            onChange={(e) => setForm({ ...form, proposal_point: e.target.value })}
            className="w-full border rounded px-2 py-1 text-sm" />
        </Field>
        <Field label="구현 방향">
          <textarea value={form.implementation_approach} rows={2}
            onChange={(e) => setForm({ ...form, implementation_approach: e.target.value })}
            className="w-full border rounded px-2 py-1 text-sm" />
        </Field>
        <Field label="차별화 포인트">
          <textarea value={form.differentiation_point} rows={2}
            onChange={(e) => setForm({ ...form, differentiation_point: e.target.value })}
            className="w-full border rounded px-2 py-1 text-sm" />
        </Field>
        <Field label="근거수준">
          <select value={form.fact_level}
            onChange={(e) => setForm({ ...form, fact_level: e.target.value })}
            className="border rounded px-2 py-1 text-sm">
            <option value="FACT">확정</option>
            <option value="INFERENCE">추론</option>
            <option value="REVIEW_NEEDED">검토필요</option>
          </select>
        </Field>
        <label className="flex items-center gap-1 text-sm">
          <input type="checkbox" checked={form.query_needed}
            onChange={(e) => setForm({ ...form, query_needed: e.target.checked })} />
          질의 필요
        </label>
        <div className="flex gap-2 pt-2">
          <button onClick={onSave} disabled={saving}
            className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm disabled:opacity-50">
            {saving ? "저장 중..." : "저장"}
          </button>
          <button onClick={onCancel} className="px-4 py-1.5 border rounded text-sm">취소</button>
        </div>
      </div>
    );
  }

  const deliverables = analysis.expected_deliverables?.length > 0 ? analysis.expected_deliverables : [];
  const risks = analysis.risk_note?.length > 0 ? analysis.risk_note : [];

  return (
    <div className="space-y-4 text-sm">
      {/* 헤더: 상태 배지들 */}
      <div className="flex justify-between items-center">
        <div className="flex gap-2 items-center flex-wrap">
          <span className="font-semibold">AI 분석 결과</span>
          <StatusBadge value={analysis.fact_level || "REVIEW_NEEDED"} />
          <span className={`text-xs font-bold px-2 py-0.5 rounded ${analysis.query_needed ? "bg-orange-100 text-orange-700" : "bg-gray-100 text-gray-500"}`}>
            질의 {analysis.query_needed ? "필요" : "불필요"}
          </span>
        </div>
        {canEdit && (
          <button onClick={onStartEdit} className="text-xs text-blue-600 hover:underline">수정</button>
        )}
      </div>

      {/* ── 섹션 1: 사실 확인 ── */}
      <AnalysisSection title="사실 확인" color="green">
        <SectionItem label="확정 근거 (원문 기반)" value={analysis.fact_summary} color="green" required />
      </AnalysisSection>

      {/* ── 섹션 2: AI 해석/추론 ── */}
      <AnalysisSection title="AI 해석" color="yellow">
        <SectionItem label="발주처 의도 / 평가 포인트" value={analysis.intent_summary} color="green" required />
        <SectionItem label="AI 해석" value={analysis.interpretation_summary} color="yellow" required />
      </AnalysisSection>

      {/* ── 섹션 3: 제안 전략 ── */}
      <AnalysisSection title="제안 전략" color="blue">
        <SectionItem label="제안 포인트" value={analysis.proposal_point} color="blue" required />
        <SectionItem label="구현/수행 방안" value={analysis.implementation_approach} color="blue" required />
        <SectionItem label="차별화 포인트" value={analysis.differentiation_point} color="purple" />
      </AnalysisSection>

      {/* ── 섹션 4: 산출물 ── */}
      <AnalysisSection title="산출물" color="gray">
        {deliverables.length > 0 ? (
          <div className="flex flex-wrap gap-1.5">
            {deliverables.map((d: string, i: number) => (
              <span key={i} className="text-xs bg-blue-50 text-blue-700 px-2 py-1 rounded">{d}</span>
            ))}
          </div>
        ) : (
          <span className="text-xs text-gray-400">판단불가</span>
        )}
      </AnalysisSection>

      {/* ── 섹션 5: 리스크/주의사항 ── */}
      <AnalysisSection title="리스크 / 주의사항" color="red">
        {risks.length > 0 ? (
          <div className="space-y-1.5">
            {risks.map((r: string, i: number) => (
              <div key={i} className="bg-red-50 border-l-3 border-red-300 px-3 py-2 rounded-r text-red-800 whitespace-pre-wrap">{r}</div>
            ))}
          </div>
        ) : (
          <span className="text-xs text-gray-400">식별된 리스크 없음</span>
        )}
      </AnalysisSection>

      {/* ── 섹션 6: 평가/증빙/질의 ── */}
      {(analysis.evaluation_focus || analysis.required_evidence || analysis.clarification_questions) && (
        <AnalysisSection title="평가 포인트 / 증빙 / 질의" color="gray">
          <SectionItem label="평가 중점 확인 사항" value={analysis.evaluation_focus} color="gray" />
          <SectionItem label="필요 증빙 자료" value={analysis.required_evidence} color="gray" />
          {analysis.clarification_questions && (
            <SectionItem label="발주처 질의 사항" value={analysis.clarification_questions} color="red" required />
          )}
        </AnalysisSection>
      )}

      {/* ── 섹션 7: 제안서 초안 ── */}
      {analysis.draft_proposal_snippet && (
        <AnalysisSection title="제안서 초안 (Draft)" color="blue">
          <div className="bg-blue-50 p-3 rounded text-sm whitespace-pre-wrap leading-relaxed border-l-4 border-blue-300">
            {analysis.draft_proposal_snippet}
          </div>
          <div className="text-[10px] text-gray-400 mt-1">이 초안은 AI가 생성한 것으로 실무자 검토 후 사용하세요.</div>
        </AnalysisSection>
      )}
    </div>
  );
}

function AnalysisSection({ title, color, children }: { title: string; color: string; children: React.ReactNode }) {
  const borderColors: Record<string, string> = {
    green: "border-green-200", yellow: "border-yellow-200", blue: "border-blue-200",
    purple: "border-purple-200", gray: "border-gray-200", red: "border-red-200",
  };
  const headerColors: Record<string, string> = {
    green: "text-green-700", yellow: "text-yellow-700", blue: "text-blue-700",
    purple: "text-purple-700", gray: "text-gray-700", red: "text-red-700",
  };
  return (
    <div className={`border ${borderColors[color] || "border-gray-200"} rounded-lg overflow-hidden`}>
      <div className={`text-xs font-semibold px-3 py-1.5 bg-gray-50 ${headerColors[color] || "text-gray-700"}`}>{title}</div>
      <div className="px-3 py-2 space-y-2">{children}</div>
    </div>
  );
}

function SectionItem({ label, value, color, required }: { label: string; value?: string | null; color: string; required?: boolean }) {
  const bgColors: Record<string, string> = {
    green: "bg-green-50", yellow: "bg-yellow-50", blue: "bg-blue-50",
    purple: "bg-purple-50", gray: "bg-gray-50", red: "bg-red-50",
  };
  return (
    <div>
      <div className="text-[11px] font-medium text-gray-500 mb-0.5 flex items-center gap-1">
        {label}
        {required && !value && <span className="text-[9px] bg-red-100 text-red-600 px-1 py-0.5 rounded">미생성</span>}
      </div>
      {value ? (
        <div className={`${bgColors[color] || "bg-gray-50"} p-2.5 rounded text-sm whitespace-pre-wrap leading-relaxed`}>{value}</div>
      ) : (
        <div className="text-xs text-gray-400 py-1 bg-gray-50 rounded px-2">
          {required ? "분석 재생성 필요" : "해당 없음"}
        </div>
      )}
    </div>
  );
}

// ── Review Panel (사람 검토 -- 우측 고정 패널) ─────────────────────────
function ReviewPanel({ review, canReview, saving, comment, setComment, onChangeStatus, memberNameMap }: any) {
  const status = review?.review_status || "NOT_REVIEWED";
  const STATUSES = [
    { value: "IN_REVIEW", label: "검토중", color: "bg-blue-50 border-blue-200 text-blue-700" },
    { value: "APPROVED", label: "승인", color: "bg-green-50 border-green-200 text-green-700" },
    { value: "HOLD", label: "보류", color: "bg-yellow-50 border-yellow-200 text-yellow-700" },
    { value: "NEEDS_UPDATE", label: "수정필요", color: "bg-red-50 border-red-200 text-red-700" },
  ];

  return (
    <div className="space-y-3 text-sm">
      {/* Current status */}
      <div className="space-y-2">
        <div className="flex items-center gap-2">
          <span className="text-xs text-gray-500">상태:</span>
          <StatusBadge value={status} />
        </div>
        {review?.reviewed_by_user_id && (
          <div className="text-xs text-gray-500">
            검토자: <span className="text-gray-700">{memberNameMap?.get(review.reviewed_by_user_id) || review.reviewed_by_user_id.slice(0, 8)}</span>
          </div>
        )}
        {review?.reviewed_at && (
          <div className="text-xs text-gray-500">
            검토일: <span className="text-gray-700">{new Date(review.reviewed_at).toLocaleString("ko-KR")}</span>
          </div>
        )}
      </div>

      {review?.review_comment && (
        <Field label="검토 의견">
          <div className="bg-gray-50 p-2 rounded whitespace-pre-wrap text-xs">{review.review_comment}</div>
        </Field>
      )}

      {/* Status change buttons */}
      {canReview && (
        <div className="border-t pt-3 space-y-2">
          <textarea value={comment} onChange={(e) => setComment(e.target.value)}
            placeholder="검토 의견 (선택)"
            className="w-full border rounded px-2 py-1 text-xs" rows={2} />
          <div className="grid grid-cols-2 gap-1.5">
            {STATUSES.map((s) => (
              <button key={s.value} onClick={() => onChangeStatus(s.value)} disabled={saving}
                className={`px-2 py-1.5 text-xs rounded border transition-colors disabled:opacity-50 ${
                  status === s.value ? s.color + " font-medium" : "hover:bg-gray-50"
                }`}>
                {s.label}
              </button>
            ))}
          </div>
        </div>
      )}

      {!canReview && (
        <div className="text-xs text-gray-400 bg-gray-50 p-2 rounded">
          REVIEWER 이상 역할이 필요합니다.
        </div>
      )}

      {/* AI vs Human separation notice */}
      <div className="border-t pt-2 mt-2">
        <div className="text-[10px] text-gray-400">
          이 패널은 사람 검토 영역입니다. AI 분석 결과는 중앙 패널에서 확인하세요.
        </div>
      </div>
    </div>
  );
}

// ── Checklist Tab ────────────────────────────────────────────────────
function ChecklistTab({ items, projectId, canEdit, onStatusChange }: {
  items: any[]; projectId: string; canEdit: boolean;
  onStatusChange: (checklistId: string, itemId: string, status: string) => void;
}) {
  if (items.length === 0) {
    return (
      <div className="text-sm text-gray-400 py-8 text-center">
        연결된 체크리스트 항목이 없습니다.
        <br />
        <Link href={`/projects/${projectId}/checklists`}
          className="text-blue-600 hover:underline text-xs mt-1 inline-block">
          체크리스트에서 항목을 연결하세요
        </Link>
      </div>
    );
  }

  const RISK_STYLES: Record<string, string> = {
    HIGH: "border-l-4 border-l-red-500 bg-red-50/40",
    MEDIUM: "border-l-4 border-l-yellow-400",
    BLOCKED: "border-l-4 border-l-red-400 bg-red-50/30",
  };

  return (
    <div className="space-y-4 text-sm">
      <div className="flex items-center gap-2 text-xs text-gray-500">
        <span>{items.length}개 항목</span>
        <span>완료 {items.filter((i: any) => i.current_status === "DONE").length}</span>
        {items.some((i: any) => i.risk_level === "HIGH") && (
          <span className="text-red-600">고위험 {items.filter((i: any) => i.risk_level === "HIGH").length}</span>
        )}
      </div>

      <div className="space-y-1.5">
        {items.map((item: any) => {
          const rowStyle = RISK_STYLES[item.current_status === "BLOCKED" ? "BLOCKED" : item.risk_level] || "";
          return (
            <div key={item.id} className={`flex items-start gap-3 bg-white border rounded px-3 py-2.5 ${rowStyle}`}>
              {canEdit ? (
                <input type="checkbox" checked={item.current_status === "DONE"}
                  onChange={() => onStatusChange(item.checklist_id, item.id, item.current_status === "DONE" ? "TODO" : "DONE")}
                  className="w-4 h-4 mt-0.5 shrink-0 cursor-pointer" />
              ) : (
                <input type="checkbox" checked={item.current_status === "DONE"} disabled
                  className="w-4 h-4 mt-0.5 shrink-0 opacity-50" />
              )}

              <span className="font-mono text-xs text-gray-400 shrink-0 mt-0.5">{item.item_code}</span>

              <div className="flex-1 min-w-0">
                <div className={item.current_status === "DONE" ? "line-through text-gray-400" : ""}>
                  {item.item_text}
                </div>
                <div className="flex gap-2 mt-1 flex-wrap">
                  {item.checklist_title && (
                    <span className="text-[11px] text-gray-400">{item.checklist_title}</span>
                  )}
                  {item.owner_user_id && (
                    <span className="text-[11px] text-blue-600">담당: {item.owner_user_id.slice(0, 8)}</span>
                  )}
                  {item.due_hint && (
                    <span className="text-[11px] text-gray-400">마감: {item.due_hint}</span>
                  )}
                  {item.risk_note && (
                    <span className="text-[11px] text-orange-600">! {item.risk_note}</span>
                  )}
                  {item.source_excerpt_id && (
                    <span className="text-[11px] text-blue-500">근거 연결됨</span>
                  )}
                </div>
              </div>

              <div className="flex items-center gap-1.5 shrink-0">
                {item.mandatory_flag && (
                  <span className="text-xs text-red-600 font-bold bg-red-50 px-1.5 py-0.5 rounded">필수</span>
                )}
                <StatusBadge value={item.risk_level} />
                {canEdit && item.current_status !== "DONE" ? (
                  <select value={item.current_status}
                    onChange={(e) => onStatusChange(item.checklist_id, item.id, e.target.value)}
                    className="border rounded px-1 py-0.5 text-xs bg-white">
                    <option value="TODO">TODO</option>
                    <option value="IN_PROGRESS">진행중</option>
                    <option value="DONE">완료</option>
                    <option value="BLOCKED">차단</option>
                  </select>
                ) : (
                  <StatusBadge value={item.current_status} />
                )}
              </div>
            </div>
          );
        })}
      </div>

      <Link href={`/projects/${projectId}/checklists`}
        className="text-xs text-blue-600 hover:underline">
        체크리스트 전체 보기
      </Link>
    </div>
  );
}

// ── History Tab ──────────────────────────────────────────────────────
function HistoryTab({ activities, projectId }: { activities: any[]; projectId: string }) {
  if (activities.length === 0) {
    return <div className="text-sm text-gray-400 py-8 text-center">이 요구사항에 대한 활동 이력이 없습니다</div>;
  }

  const TYPE_ICONS: Record<string, string> = {
    REQUIREMENT_UPDATED: "M",
    REQUIREMENT_REVIEW_CHANGED: "R",
    REQUIREMENT_INSIGHT_UPDATED: "AI",
    CHECKLIST_ITEM_STATUS_CHANGED: "C",
    CHECKLIST_ITEM_CREATED: "C+",
  };

  return (
    <div className="space-y-2 text-sm">
      {activities.map((a: any) => (
        <div key={a.id} className={`flex items-start gap-3 bg-white border rounded px-3 py-2.5 ${
          a.activity_type?.includes("REVIEW") ? "border-l-4 border-l-blue-300" :
          a.activity_type?.includes("INSIGHT") ? "border-l-4 border-l-purple-300" : ""
        }`}>
          <span className="text-[10px] font-mono bg-gray-100 px-1.5 py-0.5 rounded shrink-0">
            {TYPE_ICONS[a.activity_type] || "-"}
          </span>
          <div className="flex-1 min-w-0">
            <div className="text-sm">{a.summary}</div>
            {a.detail && <div className="text-xs text-gray-500 mt-0.5">{a.detail}</div>}
            <div className="text-xs text-gray-400 mt-1">
              <span className="font-medium text-gray-600">{a.actor_name || "알 수 없음"}</span>
              <span className="mx-1">-</span>
              {a.created_at ? new Date(a.created_at).toLocaleString("ko-KR") : ""}
            </div>
          </div>
        </div>
      ))}
      <Link href={`/projects/${projectId}/history`}
        className="text-xs text-blue-600 hover:underline">
        전체 이력 보기
      </Link>
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

// ── Field component ─────────────────────────────────────────────────
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-xs text-gray-500 mb-1 font-medium">{label}</div>
      {children}
    </div>
  );
}
