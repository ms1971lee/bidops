"use client";

import { useParams } from "next/navigation";
import { useEffect, useState, useRef } from "react";
import { documentApi, analysisJobApi } from "@/lib/api";
import StatusBadge from "@/components/common/StatusBadge";

export default function DocumentsPage() {
  const { id } = useParams() as { id: string };
  const [docs, setDocs] = useState<any[]>([]);
  const fileRef = useRef<HTMLInputElement>(null);

  const load = () => documentApi.list(id).then((d) => setDocs(d.items || [])).catch(() => {});
  useEffect(() => { load(); }, [id]);

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".pdf")) {
      alert("PDF 파일만 업로드할 수 있습니다.\nHWP/HWPX는 PDF로 변환 후 업로드해 주세요.");
      return;
    }
    await documentApi.upload(id, file, "RFP");
    load();
  };

  const handleAnalysis = async (docId: string) => {
    await analysisJobApi.create(id, { document_id: docId, job_type: "RFP_PARSE" });
    alert("분석 Job 생성 완료");
  };

  return (
    <div>
      <div className="flex justify-between items-center mb-4">
        <h2 className="font-semibold">문서 목록</h2>
        <div>
          <input type="file" ref={fileRef} accept=".pdf" onChange={handleUpload} className="hidden" />
          <button onClick={() => fileRef.current?.click()} className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm">
            PDF 업로드
          </button>
        </div>
      </div>

      <div className="bg-yellow-50 border border-yellow-200 rounded px-4 py-2 mb-4 text-sm text-yellow-800">
        PDF 파일만 업로드할 수 있습니다. HWP/HWPX 문서는 PDF로 변환 후 업로드해 주세요.
      </div>

      <table className="w-full bg-white rounded border text-sm">
        <thead className="bg-gray-50">
          <tr>
            <th className="text-left px-4 py-2">파일명</th>
            <th className="text-left px-4 py-2">유형</th>
            <th className="text-left px-4 py-2">파싱 상태</th>
            <th className="text-left px-4 py-2">버전</th>
            <th className="text-left px-4 py-2">액션</th>
          </tr>
        </thead>
        <tbody>
          {docs.map((d) => (
            <tr key={d.id} className="border-t hover:bg-gray-50">
              <td className="px-4 py-2">{d.file_name}</td>
              <td className="px-4 py-2"><StatusBadge value={d.type} /></td>
              <td className="px-4 py-2"><StatusBadge value={d.parse_status} /></td>
              <td className="px-4 py-2">v{d.version}</td>
              <td className="px-4 py-2">
                <button onClick={() => handleAnalysis(d.id)}
                  className="text-xs text-blue-600 hover:underline">분석 시작</button>
              </td>
            </tr>
          ))}
          {docs.length === 0 && (
            <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-400">업로드된 문서가 없습니다</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
