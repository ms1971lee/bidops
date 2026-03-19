"use client";

import { usePathname } from "next/navigation";
import { AuthProvider, useAuth } from "@/lib/auth";
import Sidebar from "./Sidebar";

function Shell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const isLoginPage = pathname === "/login";

  if (isLoginPage) {
    return <>{children}</>;
  }

  return (
    <div className="flex min-h-screen">
      <Sidebar />
      <div className="flex-1 flex flex-col">
        {user && (
          <header className="flex justify-end items-center px-6 py-2 border-b bg-white text-sm">
            <span className="text-gray-500 mr-3">{user.name} ({user.email})</span>
            <button onClick={logout} className="text-xs text-gray-400 hover:text-red-500">로그아웃</button>
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
