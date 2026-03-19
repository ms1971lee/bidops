"use client";

import { createContext, useContext, useEffect, useState } from "react";
import { useRouter, usePathname } from "next/navigation";
import { authApi } from "./api";

interface User {
  userId: string;
  email: string;
  name: string;
}

interface AuthCtx {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthCtx>({
  user: null,
  loading: true,
  login: async () => {},
  logout: () => {},
});

export function useAuth() {
  return useContext(AuthContext);
}

const PUBLIC_PATHS = ["/login"];

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    const token = localStorage.getItem("bidops_token");
    if (!token) {
      setLoading(false);
      if (!PUBLIC_PATHS.includes(pathname)) router.replace("/login");
      return;
    }
    authApi.me()
      .then(setUser)
      .catch(() => {
        localStorage.removeItem("bidops_token");
        if (!PUBLIC_PATHS.includes(pathname)) router.replace("/login");
      })
      .finally(() => setLoading(false));
  }, []);

  const login = async (email: string, password: string) => {
    const data = await authApi.login(email, password);
    localStorage.setItem("bidops_token", data.token);
    setUser({ userId: data.userId, email: data.email, name: data.name });
    router.push("/dashboard");
  };

  const logout = () => {
    localStorage.removeItem("bidops_token");
    setUser(null);
    router.push("/login");
  };

  // 로딩 중이면 스피너
  if (loading) {
    return <div className="flex items-center justify-center min-h-screen text-gray-400">로딩 중...</div>;
  }

  // 로그인 안 된 상태에서 보호 경로 접근 → /login으로 리다이렉트 (위에서 처리됨)
  // 로그인 된 상태에서 /login 접근 → dashboard로
  if (user && pathname === "/login") {
    router.replace("/dashboard");
    return null;
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}
