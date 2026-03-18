"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV = [
  { href: "/projects", label: "프로젝트" },
];

export default function Sidebar() {
  const pathname = usePathname();
  return (
    <aside className="w-56 bg-gray-900 text-gray-200 min-h-screen p-4 flex flex-col gap-1">
      <Link href="/projects" className="text-lg font-bold text-white mb-6 block">
        BidOps
      </Link>
      {NAV.map((n) => (
        <Link
          key={n.href}
          href={n.href}
          className={`px-3 py-2 rounded text-sm ${
            pathname.startsWith(n.href) ? "bg-gray-700 text-white" : "hover:bg-gray-800"
          }`}
        >
          {n.label}
        </Link>
      ))}
    </aside>
  );
}
