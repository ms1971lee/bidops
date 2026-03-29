"use client";

import { useState, useRef, useEffect } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth";

export default function SignupPage() {
  const { signup } = useAuth();
  const [organizationName, setOrganizationName] = useState("");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const nameRef = useRef<HTMLInputElement>(null);

  useEffect(() => { nameRef.current?.focus(); }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!organizationName.trim()) { setError("조직명을 입력하세요."); return; }
    if (!name.trim()) { setError("이름을 입력하세요."); return; }
    if (!email.trim()) { setError("이메일을 입력하세요."); return; }
    if (password.length < 8) { setError("비밀번호는 8자 이상이어야 합니다."); return; }
    if (password !== passwordConfirm) { setError("비밀번호가 일치하지 않습니다."); return; }
    setLoading(true);
    try {
      await signup(name.trim(), email.trim(), password, organizationName.trim());
    } catch (err: any) {
      setError(err.message || "회원가입 실패");
    } finally { setLoading(false); }
  };

  const handleSocial = (provider: string) => {
    alert(`${provider} 회원가입은 준비 중입니다.`);
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
            지금 시작하세요
          </h2>
          <p className="text-blue-200/80 leading-relaxed">
            무료로 가입하고 첫 RFP를 분석해 보세요.<br />
            팀을 초대하여 함께 검토할 수 있습니다.
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

          <h1 className="text-2xl font-bold text-gray-900 mb-1">회원가입</h1>
          <p className="text-sm text-gray-500 mb-8">BidOps 계정을 만들어 시작하세요</p>

          {/* Social */}
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
            <div className="relative flex justify-center"><span className="bg-gray-50 px-3 text-xs text-gray-400">또는 이메일로 가입</span></div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="su-org" className="block text-sm font-medium text-gray-700 mb-1.5">조직명</label>
              <input id="su-org" ref={nameRef} type="text" maxLength={200}
                className="w-full h-11 border border-gray-200 px-4 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                value={organizationName} onChange={(e) => setOrganizationName(e.target.value)} placeholder="회사명 또는 팀명" />
            </div>
            <div>
              <label htmlFor="su-name" className="block text-sm font-medium text-gray-700 mb-1.5">이름</label>
              <input id="su-name" type="text" autoComplete="name" maxLength={50}
                className="w-full h-11 border border-gray-200 px-4 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                value={name} onChange={(e) => setName(e.target.value)} placeholder="홍길동" />
            </div>
            <div>
              <label htmlFor="su-email" className="block text-sm font-medium text-gray-700 mb-1.5">이메일</label>
              <input id="su-email" type="email" autoComplete="email"
                className="w-full h-11 border border-gray-200 px-4 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@company.com" />
            </div>
            <div>
              <label htmlFor="su-pw" className="block text-sm font-medium text-gray-700 mb-1.5">비밀번호</label>
              <div className="relative">
                <input id="su-pw" type={showPw ? "text" : "password"} autoComplete="new-password" minLength={8}
                  className="w-full h-11 border border-gray-200 px-4 pr-16 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  value={password} onChange={(e) => setPassword(e.target.value)} placeholder="8자 이상" />
                <button type="button" onClick={() => setShowPw(!showPw)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-gray-400 hover:text-gray-600"
                  tabIndex={-1}>{showPw ? "숨기기" : "보기"}</button>
              </div>
            </div>
            <div>
              <label htmlFor="su-pw2" className="block text-sm font-medium text-gray-700 mb-1.5">비밀번호 확인</label>
              <input id="su-pw2" type="password" autoComplete="new-password" minLength={8}
                className={`w-full h-11 border px-4 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent ${
                  passwordConfirm && password !== passwordConfirm ? "border-red-300" : "border-gray-200"
                }`}
                value={passwordConfirm} onChange={(e) => setPasswordConfirm(e.target.value)} placeholder="비밀번호 재입력" />
              {passwordConfirm && password !== passwordConfirm && (
                <p className="text-xs text-red-500 mt-1">비밀번호가 일치하지 않습니다</p>
              )}
            </div>

            {error && (
              <div className="text-sm text-red-600 bg-red-50 border border-red-100 px-4 py-2.5 rounded-lg" role="alert">{error}</div>
            )}

            <button type="submit" disabled={loading}
              className="w-full h-11 bg-blue-600 text-white rounded-lg text-sm font-semibold hover:bg-blue-700 disabled:opacity-50 transition-all shadow-sm hover:shadow-md">
              {loading ? "가입 중..." : "회원가입"}
            </button>
          </form>

          <p className="text-sm text-gray-500 text-center mt-6">
            이미 계정이 있으신가요?{" "}
            <Link href="/login" className="text-blue-600 hover:text-blue-700 font-semibold">로그인</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
