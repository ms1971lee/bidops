"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import { useEffect, useState, useCallback, useRef } from "react";
import { artifactApi } from "@/lib/api";
import { useProjectRole } from "@/lib/useProjectRole";
import StatusBadge from "@/components/common/StatusBadge";
import DataTable, { type Column } from "@/components/common/DataTable";
import DetailPanel, { PanelField } from "@/components/common/DetailPanel";

const ASSET_TYPE_LABELS: Record<string, string> = {
  PROPOSAL: "제안서", DESIGN: "설계서", PLAN: "계획서",
  REPORT: "보고서", EVIDENCE: "증빙", PRESENTATION: "발표자료", ETC: "기타",
};

const STATUS_OPTIONS = ["DRAFT", "IN_PROGRESS", "REVIEW", "APPROVED", "SUBMITTED"];

export default function ArtifactsPage() {
  const { id } = useParams() as { id: string };
  const { canEdit } = useProjectRole(id);

  const [items, setItems] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<any>(null);
  const [versions, setVersions] = useState<any[]>([]);
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ title: "", asset_type: "PROPOSAL", description: "", linked_requirement_id: "" });

  const load = useCallback(() => {
    setLoading(true);
    artifactApi.list(id).then(setItems).catch(() => setItems([])).finally(() => setLoading(false));
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const selectArtifact = async (artifactId: string) => {
    const [art, vers] = await Promise.all([
      artifactApi.get(id, artifactId),
      artifactApi.versions(id, artifactId).catch(() => []),
    ]);
    setSelected(art);
    setVersions(vers);
  };

  const handleCreate = async () => {
    if (!form.title) return;
    const body: any = { ...form };
    if (!body.linked_requirement_id) delete body.linked_requirement_id;
    await artifactApi.create(id, body);
    setForm({ title: "", asset_type: "PROPOSAL", description: "", linked_requirement_id: "" });
    setShowCreate(false);
    load();
  };

  const handleStatusChange = async (artifactId: string, status: string) => {
    await artifactApi.changeStatus(id, artifactId, status);
    load();
    if (selected?.id === artifactId) selectArtifact(artifactId);
  };

  const columns: Column[] = [
    { key: "title", label: "제목", render: (r) => (
      <div>
        <div>{r.title}</div>
        {r.linked_requirement_id && <span className="text-[10px] text-blue-500">REQ 연결</span>}
      </div>
    )},
    { key: "asset_type", label: "유형", width: "w-20", render: (r) => (
      <span className="text-xs">{ASSET_TYPE_LABELS[r.asset_type] || r.asset_type}</span>
    )},
    { key: "status", label: "상태", width: "w-20", render: (r) => <StatusBadge value={r.status} /> },
    { key: "created_at", label: "생성일", width: "w-24", render: (r) => (
      <span className="text-xs text-gray-400">{r.created_at ? new Date(r.created_at).toLocaleDateString("ko-KR") : "-"}</span>
    )},
  ];

  return (
    <div className="flex gap-4 h-[calc(100vh-220px)]">
      <div className={`${selected ? "w-[450px]" : "flex-1"} flex flex-col shrink-0`}>
        <div className="flex items-center justify-between mb-3">
          <span className="text-sm font-semibold">산출물 ({items.length})</span>
          {canEdit && (
            <button onClick={() => setShowCreate(!showCreate)}
              className="px-3 py-1 bg-blue-600 text-white rounded text-xs">
              {showCreate ? "취소" : "+ 산출물 추가"}
            </button>
          )}
        </div>

        {showCreate && (
          <div className="bg-blue-50 border border-blue-100 rounded p-3 mb-3 space-y-2">
            <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })}
              className="w-full border rounded px-2 py-1.5 text-sm" placeholder="산출물 제목 *" aria-label="산출물 제목" />
            <div className="flex gap-2">
              <select value={form.asset_type} onChange={(e) => setForm({ ...form, asset_type: e.target.value })}
                className="border rounded px-2 py-1 text-xs" aria-label="산출물 유형">
                {Object.entries(ASSET_TYPE_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
              <input value={form.linked_requirement_id}
                onChange={(e) => setForm({ ...form, linked_requirement_id: e.target.value })}
                className="border rounded px-2 py-1 text-xs flex-1" placeholder="연결 요구사항 ID (선택)" aria-label="연결 요구사항" />
            </div>
            <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })}
              className="w-full border rounded px-2 py-1 text-sm" rows={2} placeholder="설명 (선택)" aria-label="설명" />
            <button onClick={handleCreate} className="px-4 py-1 bg-blue-600 text-white rounded text-xs">생성</button>
          </div>
        )}

        <div className="flex-1 overflow-auto">
          <DataTable columns={columns} data={items} loading={loading}
            emptyMessage="산출물이 없습니다"
            rowKey={(r) => r.id} activeRowKey={selected?.id}
            onRowClick={(r) => selectArtifact(r.id)} />
        </div>
      </div>

      {selected && (
        <DetailPanel title={selected.title}
          badges={[{ value: selected.status }, { value: selected.asset_type, label: ASSET_TYPE_LABELS[selected.asset_type] }]}
          onClose={() => setSelected(null)}
          actions={canEdit ? (
            <button onClick={async () => {
              if (!confirm(`"${selected.title}" 산출물을 삭제하시겠습니까?`)) return;
              await artifactApi.delete(id, selected.id);
              setSelected(null); load();
            }} className="text-xs text-red-500 hover:underline">삭제</button>
          ) : undefined}>

          {selected.description && (
            <PanelField label="설명">
              <div className="bg-gray-50 p-3 rounded">{selected.description}</div>
            </PanelField>
          )}

          <div className="flex gap-3 flex-wrap">
            {selected.linked_requirement_id && (
              <Link href={`/projects/${id}/requirements/${selected.linked_requirement_id}`}
                className="text-xs text-blue-600 hover:underline">연결 요구사항 보기</Link>
            )}
          </div>

          {canEdit && (
            <PanelField label="상태 변경">
              <div className="flex gap-1 flex-wrap">
                {STATUS_OPTIONS.map((s) => (
                  <button key={s} onClick={() => handleStatusChange(selected.id, s)}
                    aria-pressed={selected.status === s}
                    className={`px-2.5 py-1 text-xs rounded border ${
                      selected.status === s ? "bg-blue-600 text-white border-blue-600" : "hover:bg-gray-50"
                    }`}>
                    <StatusBadge value={s} />
                  </button>
                ))}
              </div>
            </PanelField>
          )}

          <VersionSection artifactId={selected.id} projectId={id}
            versions={versions} canEdit={canEdit}
            onUploaded={() => selectArtifact(selected.id)} />

          <div className="text-xs text-gray-400 border-t pt-3">
            <div>작성자: {selected.created_by || "-"}</div>
            <div>생성: {selected.created_at ? new Date(selected.created_at).toLocaleString("ko-KR") : "-"}</div>
          </div>
        </DetailPanel>
      )}

      {!selected && items.length > 0 && (
        <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">
          산출물을 선택하면 상세를 확인할 수 있습니다
        </div>
      )}
    </div>
  );
}

function VersionSection({ artifactId, projectId, versions, canEdit, onUploaded }: {
  artifactId: string; projectId: string; versions: any[]; canEdit: boolean; onUploaded: () => void;
}) {
  const [uploading, setUploading] = useState(false);
  const [versionNote, setVersionNote] = useState("");
  const fileRef = useRef<HTMLInputElement>(null);

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    try {
      await artifactApi.uploadVersion(projectId, artifactId, file, versionNote || undefined);
      setVersionNote("");
      onUploaded();
    } catch (err: any) { alert(err.message || "업로드 실패"); }
    finally { setUploading(false); if (fileRef.current) fileRef.current.value = ""; }
  };

  return (
    <PanelField label={`버전 (${versions.length})`}>
      {canEdit && (
        <div className="flex items-center gap-1 mb-2">
          <input value={versionNote} onChange={(e) => setVersionNote(e.target.value)}
            className="border rounded px-2 py-0.5 text-xs w-32" placeholder="버전 메모" aria-label="버전 메모" />
          <input type="file" ref={fileRef} onChange={handleUpload} className="hidden" aria-label="파일 선택" />
          <button onClick={() => fileRef.current?.click()} disabled={uploading}
            className="px-2 py-0.5 bg-blue-600 text-white rounded text-xs disabled:opacity-50">
            {uploading ? "업로드 중..." : "파일 업로드"}
          </button>
        </div>
      )}
      {versions.length > 0 ? (
        <div className="space-y-1">
          {versions.map((v: any) => (
            <div key={v.id} className="flex items-center gap-2 bg-gray-50 rounded px-3 py-2 text-xs">
              <span className="font-mono font-medium">v{v.version}</span>
              <span className="text-gray-600 truncate">{v.file_name}</span>
              {v.version_note && <span className="text-gray-400 truncate">{v.version_note}</span>}
              <span className="ml-auto flex items-center gap-2 shrink-0">
                {v.viewer_url && <a href={v.viewer_url} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline">열기</a>}
                <span className="text-gray-400">{v.created_at ? new Date(v.created_at).toLocaleDateString("ko-KR") : ""}</span>
              </span>
            </div>
          ))}
        </div>
      ) : (
        <div className="text-xs text-gray-400">{canEdit ? "파일을 업로드하여 첫 번째 버전을 생성하세요" : "버전 없음"}</div>
      )}
    </PanelField>
  );
}
