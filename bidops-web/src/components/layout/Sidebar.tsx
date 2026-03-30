"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV = [
  { href: "/dashboard", label: "대시보드" },
  { href: "/projects", label: "프로젝트" },
];

export default function Sidebar() {
  const pathname = usePathname();
  return (
    <aside className="w-56 bg-gray-950 text-gray-400 min-h-screen px-3 py-5 flex flex-col gap-1">
      <Link href="/dashboard" className="flex items-center gap-2.5 px-3 mb-8">
        <div className="w-7 h-7 bg-indigo-600 rounded-lg flex items-center justify-center">
          <span className="text-white font-bold text-xs">B</span>
        </div>
        <span className="text-base font-bold text-white tracking-tight">BidOps</span>
      </Link>
      <div className="text-[10px] text-gray-600 uppercase tracking-wider px-3 mb-1">메뉴</div>
      {NAV.map((n) => (
        <Link
          key={n.href}
          href={n.href}
          className={`px-3 py-2 rounded-lg text-[13px] transition-colors ${
            pathname.startsWith(n.href)
              ? "bg-indigo-600/20 text-white font-medium"
              : "hover:bg-gray-800/50 hover:text-gray-200"
          }`}
        >
          {n.label}
        </Link>
      ))}
    </aside>
  );
}
