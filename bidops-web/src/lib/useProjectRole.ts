"use client";

import { useEffect, useState } from "react";
import { memberApi, type ProjectMemberDto } from "./api";
import { useAuth } from "./auth";

export type ProjectRole = "OWNER" | "ADMIN" | "EDITOR" | "REVIEWER" | "VIEWER" | null;

const ROLE_RANK: Record<string, number> = {
  OWNER: 0, ADMIN: 1, EDITOR: 2, REVIEWER: 3, VIEWER: 4,
};

export function useProjectRole(projectId: string) {
  const { user } = useAuth();
  const [role, setRole] = useState<ProjectRole>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!user || !projectId) {
      setLoading(false);
      return;
    }
    memberApi.list(projectId)
      .then((members) => {
        const me = members.find((m: ProjectMemberDto) => m.user_id === user.userId);
        setRole(me ? me.project_role : null);
      })
      .catch(() => setRole(null))
      .finally(() => setLoading(false));
  }, [projectId, user?.userId]);

  const rank = role ? ROLE_RANK[role] : 99;
  const canEdit = rank <= ROLE_RANK.EDITOR;        // OWNER, ADMIN, EDITOR
  const canReview = rank <= ROLE_RANK.REVIEWER;     // OWNER, ADMIN, EDITOR, REVIEWER
  const isAdmin = rank <= ROLE_RANK.ADMIN;          // OWNER, ADMIN
  const isOwner = role === "OWNER";

  return { role, loading, canEdit, canReview, isAdmin, isOwner };
}
