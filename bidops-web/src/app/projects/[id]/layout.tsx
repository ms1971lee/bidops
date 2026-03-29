"use client";

import Link from "next/link";
import { usePathname, useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { projectApi } from "@/lib/api";
import StatusBadge from "@/components/common/StatusBadge";

const TABS = [
  { key: "", label: "분석 대시보드" },
  { key: "/documents", label: "문서" },
  { key: "/requirements", label: "요구사항" },
  { key: "/checklists", label: "체크리스트" },
  { key: "/inquiries", label: "질의응답" },
  { key: "/artifacts", label: "산출물" },
  { key: "/search", label: "유사검색" },
  { key: "/history", label: "이력" },
  { key: "/settings/members", label: "멤버" },
];

export default function ProjectLayout({ children }: { children: React.ReactNode }) {
  const params = useParams();
  const pathname = usePathname();
  const projectId = params.id as string;
  const [project, setProject] = useState<any>(null);

  useEffect(() => { projectApi.get(projectId).then(setProject).catch(() => {}); }, [projectId]);

  const base = `/projects/${projectId}`;

  return (
    <div>
      <div className="mb-4">
        <Link href="/projects" className="text-sm text-gray-500 hover:underline">&larr; 프로젝트 목록</Link>
      </div>

      {project && (
        <div className="mb-4">
          <h1 className="text-xl font-bold">{project.name}</h1>
          <div className="flex gap-3 text-sm text-gray-500 mt-1">
            <span>{project.client_name}</span>
            <span>{project.business_name}</span>
            <StatusBadge value={project.status} />
          </div>
        </div>
      )}

      <nav className="flex gap-1 border-b mb-4">
        {TABS.map((t) => {
          const href = base + t.key;
          const active = t.key === "" ? pathname === base : pathname.startsWith(href);
          return (
            <Link key={t.key} href={href}
              className={`px-4 py-2 text-sm border-b-2 ${active ? "border-blue-600 text-blue-600 font-medium" : "border-transparent text-gray-500 hover:text-gray-700"}`}>
              {t.label}
            </Link>
          );
        })}
      </nav>

      {children}
    </div>
  );
}
