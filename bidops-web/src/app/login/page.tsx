"use client";

import { useState } from "react";
import { useAuth } from "@/lib/auth";

export default function LoginPage() {
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await login(email, password);
    } catch (err: any) {
      setError(err.message || "로그인 실패");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-100">
      <div className="w-full max-w-sm">
        <div className="bg-white rounded-lg shadow p-8">
          <h1 className="text-xl font-bold text-center mb-1">BidOps</h1>
          <p className="text-sm text-gray-500 text-center mb-6">근거 기반 RFP 분석 시스템</p>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs text-gray-500 mb-1">이메일</label>
              <input type="email" required
                className="w-full border px-3 py-2 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                value={email} onChange={(e) => setEmail(e.target.value)}
                placeholder="admin@bidops.io" />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">비밀번호</label>
              <input type="password" required
                className="w-full border px-3 py-2 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                value={password} onChange={(e) => setPassword(e.target.value)}
                placeholder="bidops123" />
            </div>

            {error && (
              <div className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{error}</div>
            )}

            <button type="submit" disabled={loading}
              className="w-full py-2 bg-blue-600 text-white rounded text-sm font-medium hover:bg-blue-700 disabled:opacity-50">
              {loading ? "로그인 중..." : "로그인"}
            </button>
          </form>

          <p className="text-[11px] text-gray-400 text-center mt-4">
            MVP: admin@bidops.io / bidops123
          </p>
        </div>
      </div>
    </div>
  );
}
