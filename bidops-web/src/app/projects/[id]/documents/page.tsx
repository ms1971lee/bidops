"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { useEffect, useState, useRef, useCallback } from "react";
import { documentApi, analysisJobApi } from "@/lib/api";
import { useProjectRole } from "@/lib/useProjectRole";
import StatusBadge from "@/components/common/StatusBadge";
import DetailPanel, { PanelField } from "@/components/common/DetailPanel";

export default function DocumentsPage() {
  const { id } = useParams() as { id: string };
  const { canEdit, isOwner } = useProjectRole(id);
  const [docs, setDocs] = useState<any[]>([]);
  const [jobs, setJobs] = useState<any[]>([]);
  const [uploading, setUploading] = useState(false);
  const [analyzingDocIds, setAnalyzingDocIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState("");
  const [uploadType, setUploadType] = useState("RFP");
  const [selected, setSelected] = useState<any>(null);
  const [versions, setVersions] = useState<any[]>([]);
  const [dragging, setDragging] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const dragCounter = useRef(0);
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const load = useCallback(() => {
    setError("");
    Promise.all([
      documentApi.list(id).then((d) => d.items || []),
      analysisJobApi.list(id).then((d) => d.items || []).catch(() => []),
    ]).then(([docItems, jobItems]) => {
      setDocs(docItems);
      setJobs(jobItems);
    }).catch((e) => setError(e.code === "FORBIDDEN" ? "이 프로젝트의 문서를 볼 권한이 없습니다." : (e.message || "문서 목록을 불러올 수 없습니다.")));
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const latestJobByDoc = useCallback((docId: string) => {
    return jobs
      .filter((j: any) => j.document_id === docId)
      .sort((a: any, b: any) => String(b.started_at ?? "").localeCompare(String(a.started_at ?? "")))[0] || null;
  }, [jobs]);

  const hasActiveJobs = jobs.some((j: any) => j.status === "PENDING" || j.status === "RUNNING");

  useEffect(() => {
    if (hasActiveJobs) {
      pollingRef.current = setInterval(() => { load(); }, 2000);
    } else {
      if (pollingRef.current) { clearInterval(pollingRef.current); pollingRef.current = null; }
    }
    return () => { if (pollingRef.current) clearInterval(pollingRef.current); };
  }, [hasActiveJobs, load]);

  const existingFileNames = docs.map((d) => (d.file_name || "").toLowerCase());

  const uploadFile = async (file: File) => {
    if (!file.name.toLowerCase().endsWith(".pdf")) { alert("PDF 파일만 업로드할 수 있습니다."); return; }
    if (existingFileNames.includes(file.name.toLowerCase())) {
      const existing = docs.find((d) => (d.file_name || "").toLowerCase() === file.name.toLowerCase());
      if (existing && !confirm(`"${file.name}"이 이미 존재합니다 (v${existing.version}).\n새 버전으로 업로드됩니다. 계속?`)) return;
    }
    setUploading(true);
    try { await documentApi.upload(id, file, uploadType); load(); }
    catch (err: any) { alert(err.message || "업로드 실패"); }
    finally { setUploading(false); }
  };

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) await uploadFile(file);
    if (fileRef.current) fileRef.current.value = "";
  };

  const handleDragEnter = (e: React.DragEvent) => { e.preventDefault(); e.stopPropagation(); dragCounter.current++; if (e.dataTransfer.types.includes("Files")) setDragging(true); };
  const handleDragLeave = (e: React.DragEvent) => { e.preventDefault(); e.stopPropagation(); dragCounter.current--; if (dragCounter.current === 0) setDragging(false); };
  const handleDragOver = (e: React.DragEvent) => { e.preventDefault(); e.stopPropagation(); };
  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault(); e.stopPropagation(); setDragging(false); dragCounter.current = 0;
    if (!canEdit) return;
    const files = Array.from(e.dataTransfer.files).filter(f => f.name.toLowerCase().endsWith(".pdf"));
    if (files.length === 0) { alert("PDF 파일만 업로드할 수 있습니다."); return; }
    for (const file of files) { await uploadFile(file); }
  };

  const handleDelete = async (docId: string, fileName: string) => {
    if (!confirm(`"${fileName}" 문서를 삭제하시겠습니까?`)) return;
    try { await documentApi.delete(id, docId); if (selected?.id === docId) setSelected(null); load(); }
    catch (err: any) { alert(err.message || "삭제 실패"); }
  };

  const handleAnalysis = async (docId: string) => {
    setAnalyzingDocIds((prev) => new Set(prev).add(docId));
    try { await analysisJobApi.create(id, { document_id: docId, job_type: "RFP_PARSE" }); load(); }
    catch (err: any) { alert(err.message || "분석 Job 생성 실패"); }
    finally { setAnalyzingDocIds((prev) => { const s = new Set(prev); s.delete(docId); return s; }); }
  };

  const selectDoc = async (docId: string) => {
    try {
      const [doc, vers] = await Promise.all([
        documentApi.get(id, docId),
        documentApi.versions(id, docId).then((d) => d.items || []).catch(() => []),
      ]);
      setSelected(doc); setVersions(vers);
    } catch { setSelected(null); setVersions([]); }
  };

  const failedCount = docs.filter((d) => d.parse_status === "FAILED").length;
  const parsingCount = docs.filter((d) => d.parse_status === "PARSING").length;
  const parsedCount = docs.filter((d) => d.parse_status === "PARSED").length;
  const uploadedCount = docs.filter((d) => d.parse_status === "UPLOADED").length;

  const isDocBusy = (d: any) => {
    const job = latestJobByDoc(d.id);
    return d.parse_status === "PARSING" || analyzingDocIds.has(d.id) ||
      (job && (job.status === "PENDING" || job.status === "RUNNING"));
  };

  const selectedJob = selected ? latestJobByDoc(selected.id) : null;

  return (
    <div className="flex flex-col gap-4"
      onDragEnter={handleDragEnter} onDragLeave={handleDragLeave}
      onDragOver={handleDragOver} onDrop={handleDrop}>

      {/* 상단 요약 배지 */}
      {docs.length > 0 && (
        <div className="flex gap-3">
          <SummaryBadge label="전체" count={docs.length} color="bg-gray-100 text-gray-700" />
          {uploadedCount > 0 && <SummaryBadge label="업로드됨" count={uploadedCount} color="bg-blue-50 text-blue-700" />}
          {parsingCount > 0 && <SummaryBadge label="분석중" count={parsingCount} color="bg-yellow-50 text-yellow-700" pulse />}
          {parsedCount > 0 && <SummaryBadge label="분석완료" count={parsedCount} color="bg-green-50 text-green-700" />}
          {failedCount > 0 && <SummaryBadge label="실패" count={failedCount} color="bg-red-50 text-red-700" />}
        </div>
      )}

      {/* 분석 진행 중 알림 */}
      {hasActiveJobs && (() => {
        const activeJob = jobs.find((j: any) => j.status === "PENDING" || j.status === "RUNNING");
        const pct = activeJob?.progress || 0;
        return (
          <div className="bg-blue-50 border border-blue-200 rounded-lg px-4 py-3 space-y-2">
            <div className="flex items-center gap-2">
              <span className="w-2.5 h-2.5 bg-blue-500 rounded-full animate-pulse" />
              <span className="text-sm text-blue-800 font-medium">분석이 진행 중입니다</span>
              <span className="text-xs text-blue-600">{pct > 0 ? `${pct}%` : "준비 중..."}</span>
            </div>
            <div className="w-full bg-blue-200 rounded-full h-1.5">
              <div className="bg-blue-500 h-1.5 rounded-full transition-all duration-500" style={{ width: `${Math.max(pct, 1)}%` }} />
            </div>
          </div>
        );
      })()}

      {/* 헤더 */}
      <div className="flex justify-between items-center">
        <div className="flex items-center gap-2">
          <h2 className="font-semibold">문서 목록</h2>
          <span className="text-xs text-gray-400">{docs.length}건</span>
        </div>
        {canEdit && (
          <div className="flex items-center gap-2">
            <select value={uploadType} onChange={(e) => setUploadType(e.target.value)}
              className="border rounded px-2 py-1 text-xs" aria-label="문서 유형">
              <option value="RFP">RFP</option>
              <option value="ANNEX">별첨</option>
              <option value="FORM">양식</option>
              <option value="QNA">Q&A</option>
              <option value="PROPOSAL_REFERENCE">참고자료</option>
            </select>
            <input type="file" ref={fileRef} accept=".pdf" onChange={handleUpload} className="hidden" aria-label="PDF 파일 선택" />
            <button onClick={() => fileRef.current?.click()} disabled={uploading}
              className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm disabled:opacity-50 hover:bg-blue-700 transition-colors">
              {uploading ? "업로드 중..." : "PDF 업로드"}
            </button>
          </div>
        )}
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 rounded px-4 py-2 text-sm text-red-700 flex items-center justify-between" role="alert">
          <span>{error}</span>
          <button onClick={load} className="text-xs text-red-600 hover:underline font-medium ml-3">다시 시도</button>
        </div>
      )}

      <div className="flex gap-4 relative">
        {/* 드래그 오버레이 */}
        {dragging && (
          <div className="absolute inset-0 z-50 bg-blue-50/80 border-2 border-dashed border-blue-400 rounded-lg flex items-center justify-center pointer-events-none">
            <div className="text-center">
              <div className="text-4xl mb-2">📄</div>
              <div className="text-blue-700 font-semibold">PDF 파일을 여기에 놓으세요</div>
              <div className="text-sm text-blue-500 mt-1">여러 파일을 한 번에 업로드할 수 있습니다</div>
            </div>
          </div>
        )}

        {/* 문서 리스트 */}
        <div className={`${selected ? "w-1/2 xl:w-3/5" : "flex-1"} transition-all`}>
          {docs.length === 0 ? (
            <div className="bg-white rounded-lg border border-dashed border-gray-300 p-12 text-center text-gray-400 text-sm">
              {canEdit ? "PDF 파일을 드래그하거나 업로드 버튼을 클릭하세요" : "업로드된 문서가 없습니다"}
            </div>
          ) : (
            <div className="space-y-2">
              {docs.map((d) => {
                const job = latestJobByDoc(d.id);
                const busy = isDocBusy(d);
                const isSelected = selected?.id === d.id;
                return (
                  <div key={d.id} onClick={() => selectDoc(d.id)}
                    className={`bg-white rounded-lg border cursor-pointer transition-all hover:shadow-sm ${
                      isSelected ? "border-blue-500 ring-2 ring-blue-100" : "border-gray-200 hover:border-gray-300"
                    } ${d.parse_status === "FAILED" ? "border-l-4 border-l-red-400" : ""}`}>

                    <div className="px-4 py-3 space-y-1.5">
                      {/* 1줄: 파일명 */}
                      <div className="text-sm font-medium text-gray-900 line-clamp-2 text-right" title={d.file_name}>
                        {d.file_name}
                      </div>

                      {/* 2줄: 타입 · 페이지 · 버전 · 상태 · 분석 · 액션 */}
                      <div className="flex items-center gap-3 text-xs">
                        <StatusBadge value={d.type} />
                        {d.page_count && <span className="text-gray-400">{d.page_count}p</span>}
                        <span className="text-gray-400">v{d.version}</span>
                        <span className="text-gray-200">|</span>
                        <StatusBadge value={d.parse_status} />
                        {job && (
                          <span className="flex items-center gap-1">
                            {(job.status === "PENDING" || job.status === "RUNNING") &&
                              <span className="w-1.5 h-1.5 bg-blue-500 rounded-full animate-pulse" />}
                            <StatusBadge value={job.status} />
                          </span>
                        )}
                        <span className="flex-1" />
                        {/* 액션 */}
                        <span className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                          {isOwner && !busy && d.parse_status === "UPLOADED" && (
                            <button onClick={() => handleAnalysis(d.id)}
                              className="text-blue-600 hover:text-blue-800 font-medium">분석 시작</button>
                          )}
                          {isOwner && !busy && d.parse_status === "PARSED" && (
                            <>
                              <Link href={`/projects/${id}/requirements`}
                                className="text-green-600 hover:text-green-800 font-medium">결과 보기</Link>
                              <button onClick={() => handleAnalysis(d.id)}
                                className="text-gray-500 hover:text-gray-700">재분석</button>
                            </>
                          )}
                          {isOwner && !busy && d.parse_status === "FAILED" && (
                            <button onClick={() => handleAnalysis(d.id)}
                              className="text-red-600 hover:text-red-800 font-medium">재분석</button>
                          )}
                          {busy && (
                            <span className="text-blue-500 flex items-center gap-1">
                              <span className="w-1.5 h-1.5 bg-blue-500 rounded-full animate-pulse" />분석 중
                            </span>
                          )}
                          {isOwner && !busy && (
                            <button onClick={() => handleDelete(d.id, d.file_name)}
                              className="text-red-400 hover:text-red-600">삭제</button>
                          )}
                        </span>
                      </div>
                    </div>

                    {/* Progress bar (분석 중일 때만) */}
                    {busy && (
                      <div className="px-4 pb-2">
                        <div className="w-full bg-gray-200 rounded-full h-1.5">
                          <div className="bg-blue-500 h-1.5 rounded-full transition-all duration-500" style={{ width: `${Math.max(job?.progress || 0, 1)}%` }} />
                        </div>
                        <div className="text-[10px] text-blue-500 mt-0.5 text-right">
                          {job?.progress ? `${job.progress}%` : "준비 중..."}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* 우측 상세 패널 */}
        {selected && (
          <div className="w-1/2 xl:w-2/5 shrink-0">
            <DetailPanel title={selected.file_name}
              badges={[{ value: selected.type }, { value: selected.parse_status }]}
              meta={<span className="text-xs text-gray-500">v{selected.version}</span>}
              onClose={() => setSelected(null)}>

              {/* 분석 상태 + progress */}
              {selectedJob && (
                <div className={`rounded-lg p-3 space-y-2 ${
                  selectedJob.status === "COMPLETED" ? "bg-green-50 border border-green-200" :
                  selectedJob.status === "FAILED" ? "bg-red-50 border border-red-200" :
                  "bg-blue-50 border border-blue-200"
                }`}>
                  <div className="flex items-center gap-2">
                    {(selectedJob.status === "PENDING" || selectedJob.status === "RUNNING") &&
                      <span className="w-2 h-2 bg-blue-500 rounded-full animate-pulse" />}
                    <span className={`text-sm font-medium ${
                      selectedJob.status === "COMPLETED" ? "text-green-700" :
                      selectedJob.status === "FAILED" ? "text-red-700" : "text-blue-700"
                    }`}>
                      {selectedJob.status === "PENDING" ? "분석 대기 중" :
                       selectedJob.status === "RUNNING" ? "분석 진행 중" :
                       selectedJob.status === "COMPLETED" ? "분석 완료" : "분석 실패"}
                    </span>
                  </div>

                  {/* Progress bar in panel */}
                  {(selectedJob.status === "PENDING" || selectedJob.status === "RUNNING") && (
                    <div>
                      <div className="w-full bg-blue-200 rounded-full h-1.5">
                        <div className="bg-blue-600 h-1.5 rounded-full transition-all duration-500" style={{ width: `${Math.max(selectedJob.progress || 0, 1)}%` }} />
                      </div>
                      <div className="text-[10px] text-blue-500 mt-1">{selectedJob.progress ? `${selectedJob.progress}%` : "준비 중..."}</div>
                    </div>
                  )}

                  {selectedJob.started_at && (
                    <div className="text-xs text-gray-500">시작: {formatDateTime(selectedJob.started_at)}</div>
                  )}
                  {selectedJob.finished_at && (
                    <div className="text-xs text-gray-500">완료: {formatDateTime(selectedJob.finished_at)}</div>
                  )}
                  {selectedJob.status === "COMPLETED" && selectedJob.result_count != null && (
                    <div className="text-xs text-green-700">추출된 요구사항: {selectedJob.result_count}건</div>
                  )}
                  {selectedJob.status === "FAILED" && selectedJob.error_message && (
                    <div className="text-xs text-red-600">{selectedJob.error_message}</div>
                  )}

                  {selectedJob.status === "COMPLETED" && (
                    <Link href={`/projects/${id}/requirements`}
                      className="inline-block mt-1 px-4 py-1.5 bg-green-600 text-white rounded text-xs hover:bg-green-700 transition-colors">
                      분석 결과 보기
                    </Link>
                  )}
                  {selectedJob.status === "FAILED" && isOwner && (
                    <button onClick={() => handleAnalysis(selected.id)}
                      className="mt-1 px-4 py-1.5 bg-red-600 text-white rounded text-xs hover:bg-red-700 transition-colors">
                      재분석 시도
                    </button>
                  )}
                </div>
              )}

              {!selectedJob && (
                <div className="bg-gray-50 border border-gray-200 rounded-lg p-3 text-sm text-gray-500">
                  문서를 업로드한 뒤 분석 시작을 누르면 요구사항 추출을 진행합니다.
                </div>
              )}

              {selected.page_count && (
                <PanelField label="페이지 수">
                  <span className="text-sm font-medium">{selected.page_count}페이지</span>
                </PanelField>
              )}

              {selected.viewer_url && (
                <a href={selected.viewer_url} target="_blank" rel="noopener noreferrer"
                  className="inline-block text-xs text-blue-600 hover:underline bg-blue-50 px-3 py-1.5 rounded">PDF 열기</a>
              )}

              <PanelField label={`버전 이력 (${versions.length})`}>
                {versions.length > 0 ? (
                  <div className="space-y-1">
                    {versions.map((v: any) => (
                      <div key={v.id} className={`flex items-center gap-2 rounded px-3 py-2 text-xs ${v.parse_status === "FAILED" ? "bg-red-50" : "bg-gray-50"}`}>
                        <span className="font-mono font-medium">v{v.version}</span>
                        <span className="text-gray-600 truncate">{v.file_name}</span>
                        <StatusBadge value={v.parse_status} />
                        <span className="ml-auto text-gray-400 shrink-0">{v.created_at ? new Date(v.created_at).toLocaleDateString("ko-KR") : ""}</span>
                      </div>
                    ))}
                  </div>
                ) : <div className="text-xs text-gray-400">단일 버전</div>}
              </PanelField>

              {isOwner && !isDocBusy(selected) && !selectedJob && selected.parse_status === "UPLOADED" && (
                <div className="border-t pt-3">
                  <button onClick={() => handleAnalysis(selected.id)}
                    className="px-4 py-1.5 bg-blue-600 text-white rounded text-xs hover:bg-blue-700 transition-colors">
                    분석 시작
                  </button>
                </div>
              )}
            </DetailPanel>
          </div>
        )}

        {!selected && docs.length > 0 && (
          <div className="hidden md:flex flex-1 items-center justify-center text-gray-400 text-sm">
            문서를 선택하면 상세를 확인할 수 있습니다
          </div>
        )}
      </div>
    </div>
  );
}

/* ── Summary badge ───────────────────────────────────────────────── */
function SummaryBadge({ label, count, color, pulse }: { label: string; count: number; color: string; pulse?: boolean }) {
  return (
    <div className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium ${color}`}>
      {pulse && <span className="w-1.5 h-1.5 bg-current rounded-full animate-pulse" />}
      {label} <span className="font-bold">{count}</span>
    </div>
  );
}

/* ── Date formatter ──────────────────────────────────────────────── */
function formatDateTime(value: any): string {
  if (!value) return "-";
  try {
    if (Array.isArray(value)) {
      const [y, m, d, h = 0, min = 0] = value;
      return `${y}.${String(m).padStart(2, "0")}.${String(d).padStart(2, "0")} ${String(h).padStart(2, "0")}:${String(min).padStart(2, "0")}`;
    }
    return new Date(value).toLocaleString("ko-KR");
  } catch { return String(value); }
}
