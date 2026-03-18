"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { inquiryApi } from "@/lib/api";
import StatusBadge from "@/components/common/StatusBadge";

export default function InquiriesPage() {
  const { id } = useParams() as { id: string };
  const [items, setItems] = useState<any[]>([]);
  const [filter, setFilter] = useState("");
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ title: "", question_text: "", priority: "MEDIUM" });
  const [selected, setSelected] = useState<any>(null);

  const load = () => inquiryApi.list(id, filter).then(setItems).catch(() => {});
  useEffect(() => { load(); }, [id, filter]);

  const handleCreate = async () => {
    if (!form.title || !form.question_text) return;
    await inquiryApi.create(id, form);
    setForm({ title: "", question_text: "", priority: "MEDIUM" });
    setShowForm(false);
    load();
  };

  const handleStatus = async (inqId: string, status: string, answerText?: string) => {
    await inquiryApi.changeStatus(id, inqId, { status, answer_text: answerText });
    if (selected?.id === inqId) inquiryApi.get(id, inqId).then(setSelected);
    load();
  };

  return (
    <div className="flex gap-4">
      <div className="flex-1">
        <div className="flex justify-between items-center mb-3">
          <div className="flex gap-2">
            {["", "status=DRAFT", "status=SUBMITTED", "status=ANSWERED", "priority=HIGH"].map((f) => (
              <button key={f} onClick={() => setFilter(f)}
                className={`px-3 py-1 text-xs rounded border ${filter === f ? "bg-blue-600 text-white border-blue-600" : "bg-white"}`}>
                {f === "" ? "전체" : f.split("=")[1]}
              </button>
            ))}
          </div>
          <button onClick={() => setShowForm(!showForm)} className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm">
            새 질의
          </button>
        </div>

        {showForm && (
          <div className="bg-white p-4 rounded border mb-3 space-y-2">
            <input className="border px-3 py-1.5 rounded text-sm w-full" placeholder="질의 제목"
              value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
            <textarea className="border px-3 py-1.5 rounded text-sm w-full h-20" placeholder="질의 내용"
              value={form.question_text} onChange={(e) => setForm({ ...form, question_text: e.target.value })} />
            <div className="flex gap-2">
              <select className="border px-2 py-1 rounded text-sm" value={form.priority}
                onChange={(e) => setForm({ ...form, priority: e.target.value })}>
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
                <option value="CRITICAL">CRITICAL</option>
              </select>
              <button onClick={handleCreate} className="px-4 py-1 bg-blue-600 text-white rounded text-sm">생성</button>
            </div>
          </div>
        )}

        <table className="w-full bg-white rounded border text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="text-left px-3 py-2">코드</th>
              <th className="text-left px-3 py-2">제목</th>
              <th className="text-left px-3 py-2">우선도</th>
              <th className="text-left px-3 py-2">상태</th>
            </tr>
          </thead>
          <tbody>
            {items.map((q) => (
              <tr key={q.id} className={`border-t cursor-pointer hover:bg-gray-50 ${selected?.id === q.id ? "bg-blue-50" : ""}`}
                onClick={() => inquiryApi.get(id, q.id).then(setSelected)}>
                <td className="px-3 py-2 font-mono text-xs">{q.inquiry_code}</td>
                <td className="px-3 py-2">{q.title}</td>
                <td className="px-3 py-2"><StatusBadge value={q.priority} /></td>
                <td className="px-3 py-2"><StatusBadge value={q.status} /></td>
              </tr>
            ))}
            {items.length === 0 && (
              <tr><td colSpan={4} className="px-4 py-8 text-center text-gray-400">질의가 없습니다</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {/* 상세 패널 */}
      {selected && (
        <div className="w-96 bg-white rounded border p-4 text-sm space-y-3 max-h-[80vh] overflow-auto">
          <div className="flex justify-between items-center">
            <span className="font-mono font-bold">{selected.inquiry_code}</span>
            <button onClick={() => setSelected(null)} className="text-gray-400 hover:text-gray-600">✕</button>
          </div>
          <h3 className="font-medium">{selected.title}</h3>
          <div className="flex gap-2">
            <StatusBadge value={selected.status} />
            <StatusBadge value={selected.priority} />
          </div>

          <div>
            <div className="text-xs text-gray-500 mb-1">질의 내용</div>
            <div className="bg-gray-50 p-2 rounded text-xs whitespace-pre-wrap">{selected.question_text}</div>
          </div>

          {selected.reason_note && (
            <div>
              <div className="text-xs text-gray-500 mb-1">질의 사유</div>
              <div className="bg-yellow-50 p-2 rounded text-xs">{selected.reason_note}</div>
            </div>
          )}

          {selected.answer_text && (
            <div>
              <div className="text-xs text-gray-500 mb-1">발주처 답변</div>
              <div className="bg-green-50 p-2 rounded text-xs">{selected.answer_text}</div>
            </div>
          )}

          <div>
            <div className="text-xs text-gray-500 mb-1">상태 변경</div>
            <div className="flex gap-1 flex-wrap">
              {["SUBMITTED", "ANSWERED", "CLOSED"].map((s) => (
                <button key={s} onClick={() => {
                  if (s === "ANSWERED") {
                    const ans = prompt("답변 내용:");
                    if (ans) handleStatus(selected.id, s, ans);
                  } else {
                    handleStatus(selected.id, s);
                  }
                }}
                  className={`px-2 py-1 text-xs rounded border ${
                    selected.status === s ? "bg-blue-600 text-white" : "hover:bg-gray-100"
                  }`}>
                  {s}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
