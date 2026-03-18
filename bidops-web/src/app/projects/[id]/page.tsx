"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { documentApi, analysisJobApi, requirementApi, checklistApi, inquiryApi } from "@/lib/api";

export default function ProjectOverview() {
  const { id } = useParams() as { id: string };
  const [stats, setStats] = useState({ docs: 0, reqs: 0, checklists: 0, inquiries: 0, jobs: 0 });

  useEffect(() => {
    Promise.all([
      documentApi.list(id).then((d) => d.items?.length || 0).catch(() => 0),
      requirementApi.list(id).then((d) => d.total_count || 0).catch(() => 0),
      checklistApi.list(id).then((d) => d?.length || 0).catch(() => 0),
      inquiryApi.list(id).then((d) => d?.length || 0).catch(() => 0),
      analysisJobApi.list(id).then((d) => d.items?.length || 0).catch(() => 0),
    ]).then(([docs, reqs, checklists, inquiries, jobs]) =>
      setStats({ docs, reqs, checklists, inquiries, jobs })
    );
  }, [id]);

  const cards = [
    { label: "문서", value: stats.docs },
    { label: "요구사항", value: stats.reqs },
    { label: "체크리스트", value: stats.checklists },
    { label: "질의", value: stats.inquiries },
    { label: "분석 Job", value: stats.jobs },
  ];

  return (
    <div className="grid grid-cols-5 gap-4">
      {cards.map((c) => (
        <div key={c.label} className="bg-white rounded border p-4 text-center">
          <div className="text-2xl font-bold">{c.value}</div>
          <div className="text-sm text-gray-500 mt-1">{c.label}</div>
        </div>
      ))}
    </div>
  );
}
