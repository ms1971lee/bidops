"use client";

import { useParams } from "next/navigation";
import { useEffect, useState, useCallback } from "react";
import { memberApi, type ProjectMemberDto } from "@/lib/api";
import { useProjectRole } from "@/lib/useProjectRole";
import { useAuth } from "@/lib/auth";
import StatusBadge from "@/components/common/StatusBadge";

const ROLE_LABELS: Record<string, string> = {
  OWNER: "소유자",
  ADMIN: "관리자",
  EDITOR: "편집자",
  REVIEWER: "검토자",
  VIEWER: "뷰어",
};

const ROLE_DESC: Record<string, string> = {
  OWNER: "전체 권한 (멤버 관리 포함)",
  ADMIN: "프로젝트 수정, 분석 실행, 검토/승인 (멤버 관리 제외)",
  EDITOR: "문서 업로드, 요구사항/체크리스트/질의 수정, 검토/승인",
  REVIEWER: "조회 + 요구사항 검토 상태 변경 (승인/보류/수정필요)",
  VIEWER: "조회만 가능",
};

export default function MembersPage() {
  const { id } = useParams() as { id: string };
  const { isOwner } = useProjectRole(id);
  const { user } = useAuth();

  const [members, setMembers] = useState<ProjectMemberDto[]>([]);
  const [loading, setLoading] = useState(true);

  // add form
  const [showAdd, setShowAdd] = useState(false);
  const [addEmail, setAddEmail] = useState("");
  const [addRole, setAddRole] = useState<string>("EDITOR");
  const [addError, setAddError] = useState("");
  const [adding, setAdding] = useState(false);

  // role change in progress
  const [changingId, setChangingId] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    memberApi.list(id)
      .then(setMembers)
      .catch(() => setMembers([]))
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const handleAdd = async () => {
    if (!addEmail.trim()) return;
    setAddError("");
    setAdding(true);
    try {
      await memberApi.add(id, { email: addEmail.trim(), project_role: addRole });
      setAddEmail("");
      setShowAdd(false);
      load();
    } catch (err: any) {
      setAddError(err.message || "멤버 추가 실패");
    } finally {
      setAdding(false);
    }
  };

  const handleRemove = async (member: ProjectMemberDto) => {
    if (!confirm(`"${member.user_name || member.user_email}" 멤버를 제거하시겠습니까?`)) return;
    try {
      await memberApi.remove(id, member.user_id);
      if (selected?.user_id === member.user_id) setSelected(null);
      load();
    } catch (err: any) {
      alert(err.message || "멤버 제거 실패");
    }
  };

  const handleRoleChange = async (member: ProjectMemberDto, newRole: string) => {
    if (member.project_role === newRole) return;
    setChangingId(member.id);
    try {
      await memberApi.changeRole(id, member.id, newRole);
      load();
    } catch (err: any) {
      alert(err.message || "역할 변경 실패");
    } finally {
      setChangingId(null);
    }
  };

  // selected member for detail
  const [selected, setSelected] = useState<ProjectMemberDto | null>(null);

  const isMe = (m: ProjectMemberDto) => m.user_id === user?.userId;
  const ownerCount = members.filter((m) => m.project_role === "OWNER").length;

  return (
    <div>
      {/* 헤더 */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="font-semibold">프로젝트 멤버</h2>
          <p className="text-xs text-gray-500 mt-0.5">{members.length}명</p>
        </div>
        {isOwner && (
          <button onClick={() => setShowAdd(!showAdd)}
            className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm">
            {showAdd ? "취소" : "+ 멤버 초대"}
          </button>
        )}
      </div>

      {/* 멤버 초대 폼 */}
      {showAdd && (
        <div className="bg-blue-50 border border-blue-100 rounded-lg p-4 mb-4 space-y-3">
          <div className="text-sm font-medium">멤버 초대</div>
          <div className="flex gap-2 items-end flex-wrap">
            <div className="flex-1 min-w-[200px]">
              <label className="block text-xs text-gray-500 mb-1">이메일</label>
              <input value={addEmail} onChange={(e) => setAddEmail(e.target.value)}
                className="w-full border rounded px-3 py-1.5 text-sm"
                placeholder="user@example.com" type="email"
                onKeyDown={(e) => e.key === "Enter" && handleAdd()} />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">역할</label>
              <select value={addRole} onChange={(e) => setAddRole(e.target.value)}
                className="border rounded px-3 py-1.5 text-sm">
                <option value="EDITOR">편집자 (EDITOR)</option>
                <option value="REVIEWER">검토자 (REVIEWER)</option>
                <option value="VIEWER">뷰어 (VIEWER)</option>
                <option value="ADMIN">관리자 (ADMIN)</option>
                <option value="OWNER">소유자 (OWNER)</option>
              </select>
            </div>
            <button onClick={handleAdd} disabled={adding}
              className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm disabled:opacity-50">
              {adding ? "추가 중..." : "초대"}
            </button>
          </div>
          {addError && (
            <div className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{addError}</div>
          )}
          <div className="text-xs text-gray-400">
            등록된 사용자의 이메일을 입력하세요. 가입되지 않은 이메일은 초대할 수 없습니다.
          </div>
        </div>
      )}

      {/* 역할 안내 */}
      <div className="bg-gray-50 border rounded-lg p-3 mb-4">
        <div className="grid grid-cols-3 gap-3 text-xs">
          {Object.entries(ROLE_DESC).map(([role, desc]) => (
            <div key={role} className="flex gap-2">
              <StatusBadge value={role} />
              <span className="text-gray-500">{desc}</span>
            </div>
          ))}
        </div>
      </div>

      {/* 멤버 목록 */}
      {loading ? (
        <div className="text-center text-gray-400 py-12 text-sm">로딩 중...</div>
      ) : (
        <div className="bg-white rounded border overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="text-left px-4 py-2.5">멤버</th>
                <th className="text-left px-4 py-2.5 w-40">역할</th>
                <th className="text-left px-4 py-2.5 w-40">참여일</th>
                {isOwner && <th className="text-right px-4 py-2.5 w-24">관리</th>}
              </tr>
            </thead>
            <tbody>
              {members.map((m) => (
                <tr key={m.user_id}
                  className={`border-t hover:bg-gray-50 transition-colors ${isMe(m) ? "bg-blue-50/30" : ""}`}>
                  {/* 멤버 정보 */}
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-gray-200 flex items-center justify-center text-xs font-bold text-gray-600">
                        {(m.user_name || m.user_email || "?").charAt(0).toUpperCase()}
                      </div>
                      <div>
                        <div className="font-medium flex items-center gap-1.5">
                          {m.user_name || "-"}
                          {isMe(m) && (
                            <span className="text-[10px] bg-blue-100 text-blue-700 px-1.5 py-0.5 rounded">나</span>
                          )}
                        </div>
                        <div className="text-xs text-gray-500">{m.user_email}</div>
                      </div>
                    </div>
                  </td>

                  {/* 역할 */}
                  <td className="px-4 py-3">
                    {isOwner && !isMe(m) ? (
                      <select value={m.project_role}
                        disabled={changingId === m.id}
                        onChange={(e) => handleRoleChange(m, e.target.value)}
                        className={`border rounded px-2 py-1 text-xs ${
                          changingId === m.id ? "opacity-50" : ""
                        }`}>
                        <option value="OWNER">소유자 (OWNER)</option>
                        <option value="ADMIN">관리자 (ADMIN)</option>
                        <option value="EDITOR">편집자 (EDITOR)</option>
                        <option value="REVIEWER">검토자 (REVIEWER)</option>
                        <option value="VIEWER">뷰어 (VIEWER)</option>
                      </select>
                    ) : (
                      <div className="flex items-center gap-1.5">
                        <StatusBadge value={m.project_role} />
                        <span className="text-xs text-gray-400">{ROLE_LABELS[m.project_role]}</span>
                      </div>
                    )}
                  </td>

                  {/* 참여일 */}
                  <td className="px-4 py-3 text-xs text-gray-500">
                    {m.joined_at ? new Date(m.joined_at).toLocaleDateString("ko-KR") : "-"}
                  </td>

                  {/* 관리 */}
                  {isOwner && (
                    <td className="px-4 py-3 text-right">
                      {!isMe(m) && (
                        <button onClick={() => handleRemove(m)}
                          className="text-xs text-red-500 hover:text-red-700 hover:underline">
                          제거
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
              {members.length === 0 && (
                <tr><td colSpan={isOwner ? 4 : 3} className="px-4 py-12 text-center text-gray-400">
                  멤버가 없습니다
                </td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* VIEWER/EDITOR 안내 */}
      {!isOwner && (
        <div className="mt-4 text-xs text-gray-400 bg-gray-50 p-3 rounded border">
          멤버 추가/제거/역할 변경은 프로젝트 OWNER만 가능합니다.
        </div>
      )}
    </div>
  );
}
