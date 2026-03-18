"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { projectApi } from "@/lib/api";
import StatusBadge from "@/components/common/StatusBadge";

export default function ProjectsPage() {
  const [projects, setProjects] = useState<any[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ name: "", client_name: "", business_name: "" });

  const load = () => projectApi.list().then((d) => setProjects(d.items || []));
  useEffect(() => { load(); }, []);

  const handleCreate = async () => {
    if (!form.name || !form.client_name || !form.business_name) return;
    await projectApi.create(form);
    setForm({ name: "", client_name: "", business_name: "" });
    setShowForm(false);
    load();
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-xl font-bold">프로젝트</h1>
        <button onClick={() => setShowForm(!showForm)} className="px-4 py-2 bg-blue-600 text-white rounded text-sm">
          새 프로젝트
        </button>
      </div>

      {showForm && (
        <div className="bg-white p-4 rounded border mb-4 flex gap-3 items-end">
          <div>
            <label className="block text-xs text-gray-500 mb-1">프로젝트명</label>
            <input className="border px-3 py-1.5 rounded text-sm" value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">발주처</label>
            <input className="border px-3 py-1.5 rounded text-sm" value={form.client_name}
              onChange={(e) => setForm({ ...form, client_name: e.target.value })} />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">사업명</label>
            <input className="border px-3 py-1.5 rounded text-sm" value={form.business_name}
              onChange={(e) => setForm({ ...form, business_name: e.target.value })} />
          </div>
          <button onClick={handleCreate} className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm">생성</button>
        </div>
      )}

      <table className="w-full bg-white rounded border text-sm">
        <thead className="bg-gray-50">
          <tr>
            <th className="text-left px-4 py-2">프로젝트명</th>
            <th className="text-left px-4 py-2">발주처</th>
            <th className="text-left px-4 py-2">사업명</th>
            <th className="text-left px-4 py-2">상태</th>
            <th className="text-left px-4 py-2">생성일</th>
          </tr>
        </thead>
        <tbody>
          {projects.map((p) => (
            <tr key={p.id} className="border-t hover:bg-gray-50">
              <td className="px-4 py-2">
                <Link href={`/projects/${p.id}`} className="text-blue-600 hover:underline">{p.name}</Link>
              </td>
              <td className="px-4 py-2">{p.client_name}</td>
              <td className="px-4 py-2">{p.business_name}</td>
              <td className="px-4 py-2"><StatusBadge value={p.status} /></td>
              <td className="px-4 py-2 text-gray-500">{p.created_at?.[0]}-{String(p.created_at?.[1]).padStart(2,"0")}-{String(p.created_at?.[2]).padStart(2,"0")}</td>
            </tr>
          ))}
          {projects.length === 0 && (
            <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-400">프로젝트가 없습니다</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
