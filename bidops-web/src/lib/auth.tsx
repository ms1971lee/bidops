"use client";

import { createContext, useContext, useEffect, useState } from "react";
import { authApi } from "./api";

interface User {
  userId: string;
  email: string;
  name: string;
  organizationId: string | null;
  organizationName: string | null;
}

interface AuthCtx {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  signup: (name: string, email: string, password: string, organizationName: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthCtx>({
  user: null,
  loading: true,
  login: async () => {},
  signup: async () => {},
  logout: () => {},
});

export function useAuth() {
  return useContext(AuthContext);
}

const PUBLIC_PATHS = ["/login", "/signup", "/forgot-password"];

function getPathname() {
  return typeof window !== "undefined" ? window.location.pathname : "/";
}

function navigate(path: string) {
  if (typeof window !== "undefined") window.location.href = path;
}

function getToken(): string | null {
  try { return localStorage.getItem("bidops_token"); } catch { return null; }
}

function setToken(token: string) {
  try { localStorage.setItem("bidops_token", token); } catch {}
}

function removeToken() {
  try { localStorage.removeItem("bidops_token"); } catch {}
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const pathname = getPathname();
    const token = getToken();
    if (!token) {
      setLoading(false);
      if (!PUBLIC_PATHS.includes(pathname)) navigate("/login");
      return;
    }
    authApi.me()
      .then((data) => setUser({
        userId: data.userId, email: data.email, name: data.name,
        organizationId: data.organization_id, organizationName: data.organization_name,
      }))
      .catch(() => {
        removeToken();
        if (!PUBLIC_PATHS.includes(pathname)) navigate("/login");
      })
      .finally(() => setLoading(false));
  }, []);

  const login = async (email: string, password: string) => {
    const data = await authApi.login(email, password);
    setToken(data.token);
    setUser({ userId: data.userId, email: data.email, name: data.name,
      organizationId: data.organization_id, organizationName: data.organization_name });
    navigate("/dashboard");
  };

  const signup = async (name: string, email: string, password: string, organizationName: string) => {
    const data = await authApi.signup(name, email, password, organizationName);
    setToken(data.token);
    setUser({ userId: data.userId, email: data.email, name: data.name,
      organizationId: data.organization_id, organizationName: data.organization_name });
    navigate("/dashboard");
  };

  const logout = () => {
    removeToken();
    setUser(null);
    navigate("/login");
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, signup, logout }}>
      {loading ? (
        <div className="flex items-center justify-center min-h-screen text-gray-400">로딩 중...</div>
      ) : (
        children
      )}
    </AuthContext.Provider>
  );
}
