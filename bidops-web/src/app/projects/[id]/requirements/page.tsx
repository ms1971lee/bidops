"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { useEffect, useState } from "react";
import { requirementApi, documentApi, checklistApi, inquiryApi } from "@/lib/api";
import StatusBadge from "@/components/common/StatusBadge";
import PdfViewer, { parseBboxJson } from "@/components/common/PdfViewer";
import type { BboxHighlight } from "@/components/common/PdfViewer";

export default function RequirementsPage() {
  const { id } = useParams() as { id: string };
  const [items, setItems] = useState<any[]>([]);
  const [filter, setFilter] = useState("");
  const [selected, setSelected] = useState<any>(null);
  const [sources, setSources] = useState<any>(null);
  const [pdfUrl, setPdfUrl] = useState<string | null>(null);
  const [pdfPage, setPdfPage] = useState<number | undefined>(undefined);
  const [pdfHighlight, setPdfHighlight] = useState<BboxHighlight | null>(null);
  const [showPdf, setShowPdf] = useState(false);
  const [linkedChecklist, setLinkedChecklist] = useState<any[]>([]);
  const [linkedInquiries, setLinkedInquiries] = useState<any[]>([]);

  const load = () => requirementApi.list(id, filter).then((d) => setItems(d.items || [])).catch(() => {});
  useEffect(() => { load(); }, [id, filter]);

  const handleSelect = async (reqId: string) => {
    const [detail, src] = await Promise.all([
      requirementApi.get(id, reqId),
      requirementApi.sources(id, reqId).catch(() => ({ source_text_blocks: [], page_refs: [], clause_refs: [] })),
    ]);
    setSelected(detail);
    setSources(src);
    setPdfPage(undefined);
    setPdfHighlight(null);

    // 연결된 체크리스트 항목 + 질의 로드
    const selectedReqId = detail.requirement.id;
    inquiryApi.list(id, "requirement_id=" + selectedReqId)
      .then(setLinkedInquiries).catch(() => setLinkedInquiries([]));
    checklistApi.list(id).then((lists: any[]) => {
      const allItems: any[] = [];
      return Promise.all(
        (lists || []).map((cl: any) =>
          checklistApi.items(id, cl.id, "requirement_id=" + selectedReqId)
            .then((items: any[]) => items.forEach((i: any) => allItems.push({ ...i, checklist_title: cl.title })))
            .catch(() => {})
        )
      ).then(() => setLinkedChecklist(allItems));
    }).catch(() => setLinkedChecklist([]));

    if (detail.requirement.document_id) {
      documentApi.get(id, detail.requirement.document_id)
        .then((doc: any) => setPdfUrl(doc.viewer_url || doc.storage_path || null))
        .catch(() => setPdfUrl(null));
    }
  };

  const handleReview = async (reqId: string, status: string) => {
    await requirementApi.changeReviewStatus(id, reqId, { review_status: status });
    handleSelect(reqId);
    load();
  };

  const handlePageClick = (pageNo: number, bboxJson?: string, label?: string) => {
    setPdfPage(pageNo);
    setPdfHighlight(parseBboxJson(bboxJson, label));
    setShowPdf(true);
  };

  return (
    <div className="flex gap-4 h-[calc(100vh-200px)]">
      {/* 좌측: 목록 */}
      <div className={`${selected ? "w-72" : "flex-1"} flex flex-col`}>
        <div className="flex gap-1 mb-3 flex-wrap">
          {["", "review_status=NOT_REVIEWED", "review_status=APPROVED", "fact_level=REVIEW_NEEDED", "query_needed=true"].map((f) => (
            <button key={f} onClick={() => setFilter(f)}
              className={`px-2 py-1 text-xs rounded border ${filter === f ? "bg-blue-600 text-white border-blue-600" : "bg-white"}`}>
              {f === "" ? "전체" : f.split("=")[1]}
            </button>
          ))}
        </div>

        <div className="flex-1 overflow-auto bg-white rounded border">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 sticky top-0">
              <tr>
                <th className="text-left px-3 py-2">코드</th>
                <th className="text-left px-3 py-2">제목</th>
                <th className="text-left px-3 py-2">검토</th>
              </tr>
            </thead>
            <tbody>
              {items.map((r) => (
                <tr key={r.id}
                  className={`border-t cursor-pointer hover:bg-gray-50 ${selected?.requirement?.id === r.id ? "bg-blue-50" : ""}`}
                  onClick={() => handleSelect(r.id)}>
                  <td className="px-3 py-2 font-mono text-xs">{r.requirement_code}</td>
                  <td className="px-3 py-2 truncate max-w-[200px]">{r.title}</td>
                  <td className="px-3 py-2"><StatusBadge value={r.review_status} /></td>
                </tr>
              ))}
              {items.length === 0 && (
                <tr><td colSpan={3} className="px-4 py-8 text-center text-gray-400">요구사항이 없습니다</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* 중앙: 상세 패널 */}
      {selected && (
        <div className="w-96 flex flex-col bg-white rounded border overflow-auto">
          <div className="p-4 space-y-3 text-sm">
            <div className="flex justify-between items-center">
              <span className="font-mono font-bold">{selected.requirement.requirement_code}</span>
              <div className="flex gap-2">
                {pdfUrl && (
                  <button onClick={() => setShowPdf(!showPdf)}
                    className="text-xs text-blue-600 hover:underline">
                    {showPdf ? "PDF 닫기" : "PDF 보기"}
                  </button>
                )}
                <button onClick={() => { setSelected(null); setSources(null); setShowPdf(false); }}
                  className="text-gray-400 hover:text-gray-600">✕</button>
              </div>
            </div>

            <div className="flex gap-2 flex-wrap">
              <StatusBadge value={selected.requirement.category} />
              <StatusBadge value={selected.requirement.fact_level} />
              {selected.requirement.query_needed && <StatusBadge value="QUERY_NEEDED" />}
            </div>

            <div>
              <div className="text-xs text-gray-500 mb-1">원문</div>
              <div className="bg-gray-50 p-2 rounded text-xs">{selected.requirement.original_text}</div>
            </div>

            {/* 원문 근거 (SourceExcerpt) */}
            {sources?.source_text_blocks?.length > 0 && (
              <div>
                <div className="text-xs text-gray-500 mb-1">
                  원문 근거 ({sources.source_text_blocks.length}건)
                </div>
                <div className="space-y-1">
                  {sources.source_text_blocks.map((block: any) => (
                    <div key={block.id}
                      className="bg-blue-50 border border-blue-100 rounded p-2 cursor-pointer hover:bg-blue-100 transition-colors"
                      onClick={() => handlePageClick(block.page_no, block.bbox_json, block.anchor_label)}>
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-xs font-mono text-blue-700 font-medium">
                          p.{block.page_no}
                        </span>
                        {block.anchor_label && (
                          <span className="text-xs text-gray-500">{block.anchor_label}</span>
                        )}
                        <span className="text-xs text-gray-400">{block.excerpt_type}</span>
                        {block.bbox_json && (
                          <span className="text-[10px] bg-orange-100 text-orange-600 px-1 rounded">bbox</span>
                        )}
                      </div>
                      <div className="text-xs whitespace-pre-wrap text-gray-700">{block.raw_text}</div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {sources?.page_refs?.length > 0 && (
              <div>
                <div className="text-xs text-gray-500 mb-1">참조 페이지</div>
                <div className="flex gap-1 flex-wrap">
                  {sources.page_refs.map((p: number) => (
                    <button key={p} onClick={() => handlePageClick(p, undefined, undefined)}
                      className="px-2 py-0.5 text-xs bg-blue-100 text-blue-700 rounded hover:bg-blue-200">
                      p.{p}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {selected.insight?.fact_summary && (
              <div>
                <div className="text-xs text-gray-500 mb-1">확정 근거</div>
                <div className="bg-green-50 p-2 rounded text-xs whitespace-pre-wrap">{selected.insight.fact_summary}</div>
              </div>
            )}

            {selected.insight?.interpretation_summary && (
              <div>
                <div className="text-xs text-gray-500 mb-1">AI 추론</div>
                <div className="bg-yellow-50 p-2 rounded text-xs">{selected.insight.interpretation_summary}</div>
              </div>
            )}

            {selected.insight?.risk_note?.length > 0 && (
              <div>
                <div className="text-xs text-gray-500 mb-1">리스크/검토사유</div>
                <div className="bg-red-50 p-2 rounded text-xs">{selected.insight.risk_note.join(", ")}</div>
              </div>
            )}

            {/* 검토 상태 */}
            <div>
              <div className="text-xs text-gray-500 mb-1">검토 상태</div>
              <div className="flex gap-1">
                {["IN_REVIEW", "APPROVED", "HOLD", "NEEDS_UPDATE"].map((s) => (
                  <button key={s} onClick={() => handleReview(selected.requirement.id, s)}
                    className={`px-2 py-1 text-xs rounded border ${
                      selected.review?.review_status === s ? "bg-blue-600 text-white" : "hover:bg-gray-100"
                    }`}>
                    {s}
                  </button>
                ))}
              </div>
            </div>

            {/* 연결된 체크리스트 항목 */}
            <div className="border-t pt-3">
              <div className="flex justify-between items-center mb-2">
                <div className="text-xs text-gray-500">
                  연결 체크리스트 ({linkedChecklist.length}건)
                </div>
                <Link href={`/projects/${id}/checklists`}
                  className="text-xs text-blue-600 hover:underline">전체 보기</Link>
              </div>
              {linkedChecklist.length > 0 ? (
                <div className="space-y-1">
                  {linkedChecklist.map((ci: any) => (
                    <div key={ci.id} className="flex items-center gap-2 bg-gray-50 rounded px-2 py-1.5 text-xs">
                      <span className={ci.current_status === "DONE" ? "line-through text-gray-400" : ""}>
                        {ci.item_code}
                      </span>
                      <span className="truncate flex-1">{ci.item_text}</span>
                      <StatusBadge value={ci.current_status} />
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-xs text-gray-400">연결된 체크리스트 항목이 없습니다</div>
              )}
            </div>

            {/* 연결된 질의 */}
            <div className="border-t pt-3">
              <div className="flex justify-between items-center mb-2">
                <div className="text-xs text-gray-500">
                  연결 질의 ({linkedInquiries.length}건)
                </div>
                <Link href={`/projects/${id}/inquiries`}
                  className="text-xs text-blue-600 hover:underline">전체 보기</Link>
              </div>
              {linkedInquiries.length > 0 ? (
                <div className="space-y-1">
                  {linkedInquiries.map((inq: any) => (
                    <div key={inq.id} className="flex items-center gap-2 bg-gray-50 rounded px-2 py-1.5 text-xs">
                      <span className="font-mono">{inq.inquiry_code}</span>
                      <span className="truncate flex-1">{inq.title}</span>
                      <StatusBadge value={inq.status} />
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-xs text-gray-400">연결된 질의가 없습니다</div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* 우측: PDF 뷰어 */}
      {showPdf && selected && (
        <div className="flex-1 min-w-[400px]">
          <div className="text-xs text-gray-500 mb-1">
            PDF {pdfPage ? `- p.${pdfPage}` : ""}
          </div>
          <div className="h-[calc(100%-20px)]">
            <PdfViewer url={pdfUrl} page={pdfPage} highlight={pdfHighlight} />
          </div>
        </div>
      )}
    </div>
  );
}
