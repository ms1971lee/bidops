const BASE = "/api/v1";

function getToken(): string | null {
  if (typeof window === "undefined") return null;
  try { return localStorage.getItem("bidops_token"); } catch { return null; }
}

export interface ApiError {
  status: number;
  code: string;
  message: string;
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = "Bearer " + token;
  if (options?.headers) Object.assign(headers, options.headers);

  const res = await fetch(path, { ...options, headers });
  if (res.status === 401) {
    if (typeof window !== "undefined") {
      try { localStorage.removeItem("bidops_token"); } catch {}
      window.location.href = "/login";
    }
    throw { status: 401, code: "UNAUTHORIZED", message: "인증이 만료되었습니다." } as ApiError;
  }
  const json = await res.json();
  if (!json.success) {
    const err: ApiError = {
      status: res.status,
      code: json.error?.code || "ERROR",
      message: res.status === 403
        ? "접근 권한이 없습니다."
        : (json.error?.message || "API error"),
    };
    throw err;
  }
  return json.data;
}

// ── Auth ─────────────────────────────────────────────────────────
export type AuthResponse = { token: string; userId: string; email: string; name: string; organization_id: string; organization_name: string };
export type MeResponse = { userId: string; email: string; name: string; organization_id: string; organization_name: string };

export const authApi = {
  login: async (email: string, password: string): Promise<AuthResponse> => {
    const res = await fetch(BASE + "/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    const json = await res.json();
    if (!json.success) throw { status: res.status, code: json.error?.code, message: json.error?.message || "Login failed" } as ApiError;
    return json.data;
  },
  signup: async (name: string, email: string, password: string, organizationName: string): Promise<AuthResponse> => {
    const res = await fetch(BASE + "/auth/signup", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name, email, password, organization_name: organizationName }),
    });
    const json = await res.json();
    if (!json.success) throw { status: res.status, code: json.error?.code, message: json.error?.message || "Signup failed" } as ApiError;
    return json.data;
  },
  me: () => request<MeResponse>(BASE + "/auth/me"),
};

// ── Projects ────────────────────────────────────────────────────────
export const projectApi = {
  list: () => request<{ items: any[]; total_count: number }>(`${BASE}/projects`),
  get: (id: string) => request<any>(`${BASE}/projects/${id}`),
  create: (body: any) => request<any>(`${BASE}/projects`, { method: "POST", body: JSON.stringify(body) }),
};

// ── Audit Logs (감사로그) ────────────────────────────────────────────
export const activityApi = {
  list: (projectId: string, params?: string) =>
    request<{ items: any[]; total_count: number }>(`${BASE}/projects/${projectId}/audit-logs${params ? `?${params}` : ""}`),
};

// ── Project Members ─────────────────────────────────────────────────
export type ProjectMemberDto = {
  id: string;
  user_id: string;
  project_role: "OWNER" | "ADMIN" | "EDITOR" | "REVIEWER" | "VIEWER";
  user_email: string;
  user_name: string;
  joined_at: string;
};

export const memberApi = {
  list: (projectId: string) =>
    request<ProjectMemberDto[]>(`${BASE}/projects/${projectId}/members`),
  add: (projectId: string, body: { email: string; project_role?: string }) =>
    request<ProjectMemberDto>(`${BASE}/projects/${projectId}/members`, {
      method: "POST", body: JSON.stringify(body),
    }),
  remove: (projectId: string, targetUserId: string) =>
    request<void>(`${BASE}/projects/${projectId}/members/${targetUserId}`, { method: "DELETE" }),
  changeRole: (projectId: string, memberId: string, role: string) =>
    request<ProjectMemberDto>(`${BASE}/projects/${projectId}/members/${memberId}/role`, {
      method: "PATCH", body: JSON.stringify({ role }),
    }),
};

// ── Documents ───────────────────────────────────────────────────────
export const documentApi = {
  list: (projectId: string) =>
    request<{ items: any[]; total_count: number }>(`${BASE}/projects/${projectId}/documents`),
  get: (projectId: string, docId: string) =>
    request<any>(`${BASE}/projects/${projectId}/documents/${docId}`),
  upload: async (projectId: string, file: File, type: string) => {
    const token = getToken();
    const form = new FormData();
    form.append("file", file);
    form.append("type", type);
    const headers: Record<string, string> = {};
    if (token) headers["Authorization"] = "Bearer " + token;
    const res = await fetch(`${BASE}/projects/${projectId}/documents`, {
      method: "POST",
      headers,
      body: form,
    });
    if (res.status === 401) {
      localStorage.removeItem("bidops_token");
      window.location.href = "/login";
      throw new Error("Unauthorized");
    }
    const json = await res.json();
    if (!json.success) throw { status: res.status, code: json.error?.code, message: json.error?.message } as ApiError;
    return json.data;
  },
  delete: (projectId: string, docId: string) =>
    request<void>(`${BASE}/projects/${projectId}/documents/${docId}`, { method: "DELETE" }),
  versions: (projectId: string, docId: string) =>
    request<{ items: any[]; total_count: number }>(`${BASE}/projects/${projectId}/documents/${docId}/versions`),
};

// ── Requirements ────────────────────────────────────────────────────
export const requirementApi = {
  list: (projectId: string, params?: string) =>
    request<{ items: any[]; total_count: number }>(`${BASE}/projects/${projectId}/requirements${params ? `?${params}` : ""}`),
  get: (projectId: string, id: string) =>
    request<any>(`${BASE}/projects/${projectId}/requirements/${id}`),
  update: (projectId: string, id: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/requirements/${id}`, {
      method: "PATCH", body: JSON.stringify(body),
    }),
  sources: (projectId: string, id: string) =>
    request<any>(`${BASE}/projects/${projectId}/requirements/${id}/sources`),
  getAnalysis: (projectId: string, id: string) =>
    request<any>(`${BASE}/projects/${projectId}/requirements/${id}/analysis`),
  updateAnalysis: (projectId: string, id: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/requirements/${id}/analysis`, {
      method: "PATCH", body: JSON.stringify(body),
    }),
  getReview: (projectId: string, id: string) =>
    request<any>(`${BASE}/projects/${projectId}/requirements/${id}/review`),
  changeReviewStatus: (projectId: string, id: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/requirements/${id}/review-status`, {
      method: "POST", body: JSON.stringify(body),
    }),
};

// ── Analysis Jobs ───────────────────────────────────────────────────
export const analysisJobApi = {
  list: (projectId: string) =>
    request<{ items: any[]; total_count: number }>(`${BASE}/projects/${projectId}/analysis-jobs`),
  get: (projectId: string, jobId: string) =>
    request<any>(`${BASE}/projects/${projectId}/analysis-jobs/${jobId}`),
  create: (projectId: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/analysis-jobs`, { method: "POST", body: JSON.stringify(body) }),
  coverage: (projectId: string, documentId: string) =>
    request<any>(`${BASE}/projects/${projectId}/analysis-jobs/coverage?document_id=${documentId}`),
};

// ── Checklists ──────────────────────────────────────────────────────
export const checklistApi = {
  list: (projectId: string) => request<any[]>(`${BASE}/projects/${projectId}/checklists`),
  create: (projectId: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/checklists`, { method: "POST", body: JSON.stringify(body) }),
  items: (projectId: string, checklistId: string, params?: string) =>
    request<any[]>(`${BASE}/projects/${projectId}/checklists/${checklistId}/items${params ? `?${params}` : ""}`),
  createItem: (projectId: string, checklistId: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/checklists/${checklistId}/items`, {
      method: "POST", body: JSON.stringify(body),
    }),
  updateItem: (projectId: string, checklistId: string, itemId: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/checklists/${checklistId}/items/${itemId}`, {
      method: "PATCH", body: JSON.stringify(body),
    }),
  changeItemStatus: (projectId: string, checklistId: string, itemId: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/checklists/${checklistId}/items/${itemId}/status`, {
      method: "POST", body: JSON.stringify(body),
    }),
  listReviews: (projectId: string, checklistId: string, itemId: string, limit?: number) =>
    request<any[]>(`${BASE}/projects/${projectId}/checklists/${checklistId}/items/${itemId}/reviews${limit ? `?limit=${limit}` : ""}`),
};

// ── Source Excerpts ─────────────────────────────────────────────────
export const sourceExcerptApi = {
  get: (id: string) =>
    request<any>(`${BASE}/source-excerpts/${id}`),
};

// ── Artifacts ───────────────────────────────────────────────────────
export const artifactApi = {
  list: (projectId: string) =>
    request<any[]>(`${BASE}/projects/${projectId}/artifacts`),
  get: (projectId: string, id: string) =>
    request<any>(`${BASE}/projects/${projectId}/artifacts/${id}`),
  create: (projectId: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/artifacts`, { method: "POST", body: JSON.stringify(body) }),
  update: (projectId: string, id: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/artifacts/${id}`, { method: "PATCH", body: JSON.stringify(body) }),
  delete: (projectId: string, id: string) =>
    request<void>(`${BASE}/projects/${projectId}/artifacts/${id}`, { method: "DELETE" }),
  changeStatus: (projectId: string, id: string, status: string) =>
    request<any>(`${BASE}/projects/${projectId}/artifacts/${id}/status`, {
      method: "POST", body: JSON.stringify({ status }),
    }),
  versions: (projectId: string, id: string) =>
    request<any[]>(`${BASE}/projects/${projectId}/artifacts/${id}/versions`),
  uploadVersion: async (projectId: string, id: string, file: File, versionNote?: string) => {
    const token = getToken();
    const form = new FormData();
    form.append("file", file);
    if (versionNote) form.append("version_note", versionNote);
    const headers: Record<string, string> = {};
    if (token) headers["Authorization"] = "Bearer " + token;
    const res = await fetch(`${BASE}/projects/${projectId}/artifacts/${id}/versions`, {
      method: "POST", headers, body: form,
    });
    if (res.status === 401) { localStorage.removeItem("bidops_token"); window.location.href = "/login"; throw new Error("Unauthorized"); }
    const json = await res.json();
    if (!json.success) throw { status: res.status, code: json.error?.code, message: json.error?.message } as ApiError;
    return json.data;
  },
};

// ── Inquiries ───────────────────────────────────────────────────────
export const inquiryApi = {
  list: (projectId: string, params?: string) =>
    request<any[]>(`${BASE}/projects/${projectId}/inquiries${params ? `?${params}` : ""}`),
  create: (projectId: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/inquiries`, { method: "POST", body: JSON.stringify(body) }),
  get: (projectId: string, id: string) =>
    request<any>(`${BASE}/projects/${projectId}/inquiries/${id}`),
  update: (projectId: string, id: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/inquiries/${id}`, {
      method: "PATCH", body: JSON.stringify(body),
    }),
  changeStatus: (projectId: string, id: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/inquiries/${id}/status`, {
      method: "POST", body: JSON.stringify(body),
    }),
  generate: (projectId: string) =>
    request<{ created_count: number; skipped_count: number; created_inquiry_ids: string[] }>(
      `${BASE}/projects/${projectId}/inquiries/generate`, { method: "POST" }),
};
