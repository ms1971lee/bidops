"use client";

import { useState, useRef, useEffect } from "react";
import Link from "next/link";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const emailRef = useRef<HTMLInputElement>(null);

  useEffect(() => { emailRef.current?.focus(); }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) { setError("이메일을 입력하세요."); return; }
    setError("");
    setLoading(true);
    try {
      // TODO: 백엔드 비밀번호 재설정 API 연동
      // await authApi.requestPasswordReset(email.trim());
      await new Promise((r) => setTimeout(r, 1000));
      setSent(true);
    } catch (err: any) {
      setError(err.message || "요청에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    } finally { setLoading(false); }
  };

  return (
    <div className="flex min-h-screen">
      {/* Left branding */}
      <div className="hidden lg:flex lg:w-[480px] xl:w-[560px] bg-gradient-to-br from-slate-900 via-blue-950 to-slate-900 text-white flex-col justify-between p-12">
        <div>
          <div className="flex items-center gap-3 mb-16">
            <div className="w-10 h-10 bg-blue-500 rounded-xl flex items-center justify-center">
              <span className="font-bold text-lg">B</span>
            </div>
            <span className="text-xl font-bold tracking-tight">BidOps</span>
          </div>
          <h2 className="text-3xl font-bold leading-snug mb-4">
            비밀번호를<br />잊으셨나요?
          </h2>
          <p className="text-blue-200/80 leading-relaxed">
            가입 시 사용한 이메일을 입력하시면<br />
            비밀번호 재설정 링크를 보내드립니다.
          </p>
        </div>
        <p className="text-xs text-blue-300/50">&copy; 2026 BidOps. All rights reserved.</p>
      </div>

      {/* Right form */}
      <div className="flex-1 flex items-center justify-center bg-gray-50 px-6">
        <div className="w-full max-w-[400px]">
          <div className="flex items-center gap-2 mb-8 lg:hidden">
            <div className="w-9 h-9 bg-blue-600 rounded-xl flex items-center justify-center">
              <span className="text-white font-bold">B</span>
            </div>
            <span className="text-lg font-bold">BidOps</span>
          </div>

          {sent ? (
            /* Success state */
            <div className="text-center">
              <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-6">
                <svg className="w-8 h-8 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                </svg>
              </div>
              <h1 className="text-2xl font-bold text-gray-900 mb-2">메일을 확인하세요</h1>
              <p className="text-sm text-gray-500 mb-2">
                <span className="font-medium text-gray-700">{email}</span> 으로
              </p>
              <p className="text-sm text-gray-500 mb-8">비밀번호 재설정 링크를 발송했습니다.</p>

              <div className="bg-amber-50 border border-amber-100 rounded-lg px-4 py-3 text-sm text-amber-700 mb-6">
                메일이 도착하지 않았다면 스팸 폴더를 확인해 주세요.
              </div>

              <div className="space-y-3">
                <button onClick={() => { setSent(false); setEmail(""); }}
                  className="w-full h-11 border border-gray-200 rounded-lg text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                  다른 이메일로 다시 시도
                </button>
                <Link href="/login"
                  className="block w-full h-11 leading-[44px] bg-blue-600 text-white rounded-lg text-sm font-semibold text-center hover:bg-blue-700 transition-colors">
                  로그인으로 돌아가기
                </Link>
              </div>
            </div>
          ) : (
            /* Form state */
            <>
              <h1 className="text-2xl font-bold text-gray-900 mb-1">비밀번호 찾기</h1>
              <p className="text-sm text-gray-500 mb-8">가입 시 사용한 이메일을 입력해 주세요</p>

              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label htmlFor="fp-email" className="block text-sm font-medium text-gray-700 mb-1.5">이메일</label>
                  <input id="fp-email" ref={emailRef} type="email" autoComplete="email"
                    className="w-full h-11 border border-gray-200 px-4 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    value={email} onChange={(e) => setEmail(e.target.value)}
                    placeholder="name@company.com" />
                </div>

                {error && (
                  <div className="text-sm text-red-600 bg-red-50 border border-red-100 px-4 py-2.5 rounded-lg" role="alert">{error}</div>
                )}

                <button type="submit" disabled={loading}
                  className="w-full h-11 bg-blue-600 text-white rounded-lg text-sm font-semibold hover:bg-blue-700 disabled:opacity-50 transition-all shadow-sm hover:shadow-md">
                  {loading ? "발송 중..." : "재설정 링크 보내기"}
                </button>
              </form>

              <p className="text-sm text-gray-500 text-center mt-6">
                비밀번호가 기억나셨나요?{" "}
                <Link href="/login" className="text-blue-600 hover:text-blue-700 font-semibold">로그인</Link>
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
