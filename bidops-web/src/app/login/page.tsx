"use client";

import { useState, useRef, useEffect } from "react";
import { useAuth } from "@/lib/auth";
import Link from "next/link";

export default function LoginPage() {
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [remember, setRemember] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const emailRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    try {
      const saved = localStorage.getItem("bidops_remember");
      if (saved) {
        const { email: savedEmail, password: savedPw } = JSON.parse(saved);
        if (savedEmail) setEmail(savedEmail);
        if (savedPw) setPassword(savedPw);
        setRemember(true);
      }
    } catch {}
    emailRef.current?.focus();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) { setError("이메일을 입력하세요."); return; }
    if (!password) { setError("비밀번호를 입력하세요."); return; }
    setError("");
    setLoading(true);
    try {
      if (remember) {
        try { localStorage.setItem("bidops_remember", JSON.stringify({ email: email.trim(), password })); } catch {}
      } else {
        try { localStorage.removeItem("bidops_remember"); } catch {}
      }
      await login(email.trim(), password);
    } catch (err: any) {
      setError(
        err.code === "INVALID_CREDENTIALS" ? "이메일 또는 비밀번호가 올바르지 않습니다." :
        err.code === "USER_NOT_FOUND" ? "등록되지 않은 이메일입니다." :
        err.message || "로그인에 실패했습니다. 잠시 후 다시 시도해 주세요."
      );
    } finally { setLoading(false); }
  };

  // TODO: 백엔드 OAuth2 연동 후 실제 URL로 교체
  const handleSocial = (provider: string) => {
    alert(`${provider} 로그인은 준비 중입니다.`);
  };

  return (
    <div className="flex min-h-screen">
      {/* Left - branding panel */}
      <div className="hidden lg:flex lg:w-[480px] xl:w-[560px] bg-gradient-to-br from-gray-950 via-indigo-950 to-gray-950 text-white flex-col justify-between p-12">
        <div>
          <div className="flex items-center gap-3 mb-16">
            <div className="w-10 h-10 bg-indigo-600 rounded-xl flex items-center justify-center">
              <span className="font-bold text-lg">B</span>
            </div>
            <span className="text-xl font-bold tracking-tight">BidOps</span>
          </div>

          <h2 className="text-3xl font-bold leading-snug mb-4">
            근거 기반으로<br />입찰을 분석합니다
          </h2>
          <p className="text-indigo-200/70 leading-relaxed">
            RFP 요구사항을 구조적으로 분석하고,<br />
            누락을 방지하며, 수주 운영을 지원합니다.
          </p>

          <div className="mt-12 space-y-4">
            <Feature icon="01" text="PDF 업로드만으로 요구사항 자동 추출" />
            <Feature icon="02" text="원문 근거 기반 추적 가능한 분석" />
            <Feature icon="03" text="체크리스트 + 질의 + 산출물 통합 관리" />
          </div>
        </div>

        <p className="text-xs text-indigo-300/40">
          &copy; 2026 BidOps. All rights reserved.
        </p>
      </div>

      {/* Right - login form */}
      <div className="flex-1 flex items-center justify-center bg-gray-50 px-6">
        <div className="w-full max-w-[400px]">
          {/* Mobile logo */}
          <div className="flex items-center gap-2 mb-8 lg:hidden">
            <div className="w-9 h-9 bg-indigo-600 rounded-xl flex items-center justify-center">
              <span className="text-white font-bold">B</span>
            </div>
            <span className="text-lg font-bold">BidOps</span>
          </div>

          <h1 className="text-2xl font-bold text-gray-900 mb-1">로그인</h1>
          <p className="text-sm text-gray-500 mb-8">계정에 로그인하여 프로젝트를 관리하세요</p>

          {/* Social login buttons */}
          <div className="grid grid-cols-3 gap-3 mb-6">
            <button onClick={() => handleSocial("Google")}
              className="flex items-center justify-center gap-2 h-11 border border-gray-200 rounded-lg text-sm text-gray-700 bg-white hover:bg-gray-50 transition-colors">
              <svg className="w-5 h-5" viewBox="0 0 24 24"><path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/><path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>
              Google
            </button>
            <button onClick={() => handleSocial("Naver")}
              className="flex items-center justify-center gap-2 h-11 rounded-lg text-sm text-white bg-[#03C75A] hover:bg-[#02b351] transition-colors">
              <svg className="w-5 h-5" viewBox="0 0 24 24"><path fill="#fff" d="M16.27 12.27 7.4 3H3v18h4.73V12.73L16.6 21H21V3h-4.73z"/></svg>
              Naver
            </button>
            <button onClick={() => handleSocial("Kakao")}
              className="flex items-center justify-center gap-2 h-11 rounded-lg text-sm text-[#391B1B] bg-[#FEE500] hover:bg-[#FADA0A] transition-colors">
              <svg className="w-5 h-5" viewBox="0 0 24 24"><path fill="#391B1B" d="M12 3C6.48 3 2 6.36 2 10.43c0 2.62 1.75 4.93 4.38 6.25l-1.12 4.13c-.1.36.31.65.62.44l4.84-3.19c.42.04.84.06 1.28.06 5.52 0 10-3.36 10-7.5S17.52 3 12 3z"/></svg>
              Kakao
            </button>
          </div>

          <div className="relative mb-6">
            <div className="absolute inset-0 flex items-center"><div className="w-full border-t border-gray-200" /></div>
            <div className="relative flex justify-center"><span className="bg-gray-50 px-3 text-xs text-gray-400">또는 이메일로 계속</span></div>
          </div>

          {/* Email login form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="login-email" className="block text-sm font-medium text-gray-700 mb-1.5">이메일</label>
              <input id="login-email" ref={emailRef} type="email" autoComplete="email"
                className="w-full h-11 border border-gray-200 px-4 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:border-transparent transition-shadow"
                value={email} onChange={(e) => setEmail(e.target.value)}
                placeholder="name@company.com" />
            </div>
            <div>
              <label htmlFor="login-pw" className="block text-sm font-medium text-gray-700 mb-1.5">비밀번호</label>
              <div className="relative">
                <input id="login-pw" type={showPw ? "text" : "password"} autoComplete="current-password"
                  className="w-full h-11 border border-gray-200 px-4 pr-16 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:border-transparent transition-shadow"
                  value={password} onChange={(e) => setPassword(e.target.value)}
                  placeholder="8자 이상" />
                <button type="button" onClick={() => setShowPw(!showPw)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-gray-400 hover:text-gray-600 transition-colors"
                  tabIndex={-1} aria-label={showPw ? "비밀번호 숨기기" : "비밀번호 보기"}>
                  {showPw ? "숨기기" : "보기"}
                </button>
              </div>
            </div>

            <label className="flex items-center gap-2 cursor-pointer">
              <input type="checkbox" checked={remember}
                onChange={(e) => {
                  setRemember(e.target.checked);
                  if (!e.target.checked) {
                    try { localStorage.removeItem("bidops_remember"); } catch {}
                  }
                }}
                className="w-4 h-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500" />
              <span className="text-sm text-gray-600">로그인 정보 기억하기</span>
            </label>

            {error && (
              <div className="text-sm text-rose-600 bg-rose-50/50 border border-rose-100 px-4 py-2.5 rounded-xl" role="alert">{error}</div>
            )}

            <button type="submit" disabled={loading}
              className="w-full h-11 bg-indigo-600 text-white rounded-lg text-sm font-semibold hover:bg-indigo-700 disabled:opacity-50 transition-all shadow-sm hover:shadow-md">
              {loading ? "로그인 중..." : "로그인"}
            </button>
          </form>

          <div className="flex items-center justify-center gap-3 mt-6 text-sm">
            <Link href="/signup" className="text-indigo-600 hover:text-indigo-700 font-semibold">회원가입</Link>
            <span className="text-gray-300">|</span>
            <Link href="/forgot-password" className="text-gray-500 hover:text-gray-700">비밀번호 찾기</Link>
          </div>
        </div>
      </div>
    </div>
  );
}

function Feature({ icon, text }: { icon: string; text: string }) {
  return (
    <div className="flex items-start gap-3">
      <span className="w-8 h-8 bg-indigo-500/20 rounded-lg flex items-center justify-center text-xs font-bold text-indigo-300 shrink-0">{icon}</span>
      <span className="text-sm text-indigo-100/80 mt-1">{text}</span>
    </div>
  );
}
