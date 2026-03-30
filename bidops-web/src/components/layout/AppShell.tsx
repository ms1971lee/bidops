"use client";

import { usePathname } from "next/navigation";
import { AuthProvider, useAuth } from "@/lib/auth";
import Sidebar from "./Sidebar";

const PUBLIC_PATHS = ["/login", "/signup", "/forgot-password"];

function Shell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const isPublicPage = PUBLIC_PATHS.includes(pathname);

  if (isPublicPage) {
    return <>{children}</>;
  }

  return (
    <div className="flex min-h-screen bg-gray-50/50">
      <Sidebar />
      <div className="flex-1 flex flex-col min-w-0">
        {user && (
          <header className="flex justify-end items-center px-6 py-2.5 border-b border-gray-100 bg-white/80 backdrop-blur-sm">
            <span className="text-[11px] text-gray-400 mr-3">{user.name} <span className="text-gray-300">({user.email})</span></span>
            <button onClick={logout} className="text-[11px] text-gray-400 hover:text-rose-500 transition-colors">
              로그아웃
            </button>
          </header>
        )}
        <main className="flex-1 p-6 overflow-auto">{children}</main>
      </div>
    </div>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      <Shell>{children}</Shell>
    </AuthProvider>
  );
}
