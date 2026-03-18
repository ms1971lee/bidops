const BASE = "/api/v1";

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { "Content-Type": "application/json", ...options?.headers },
    ...options,
  });
  const json = await res.json();
  if (!json.success) throw new Error(json.error?.message || "API error");
  return json.data;
}

// ── Projects ────────────────────────────────────────────────────────
export const projectApi = {
  list: () => request<{ items: any[]; total_count: number }>(`${BASE}/projects`),
  get: (id: string) => request<any>(`${BASE}/projects/${id}`),
  create: (body: any) => request<any>(`${BASE}/projects`, { method: "POST", body: JSON.stringify(body) }),
};

// ── Documents ───────────────────────────────────────────────────────
export const documentApi = {
  list: (projectId: string) =>
    request<{ items: any[]; total_count: number }>(`${BASE}/projects/${projectId}/documents`),
  get: (projectId: string, docId: string) =>
    request<any>(`${BASE}/projects/${projectId}/documents/${docId}`),
  upload: async (projectId: string, file: File, type: string) => {
    const form = new FormData();
    form.append("file", file);
    form.append("type", type);
    const res = await fetch(`${BASE}/projects/${projectId}/documents`, { method: "POST", body: form });
    const json = await res.json();
    if (!json.success) throw new Error(json.error?.message);
    return json.data;
  },
};

// ── Requirements ────────────────────────────────────────────────────
export const requirementApi = {
  list: (projectId: string, params?: string) =>
    request<{ items: any[]; total_count: number }>(`${BASE}/projects/${projectId}/requirements${params ? `?${params}` : ""}`),
  get: (projectId: string, id: string) =>
    request<any>(`${BASE}/projects/${projectId}/requirements/${id}`),
  sources: (projectId: string, id: string) =>
    request<any>(`${BASE}/projects/${projectId}/requirements/${id}/sources`),
  changeReviewStatus: (projectId: string, id: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/requirements/${id}/review-status`, {
      method: "POST", body: JSON.stringify(body),
    }),
};

// ── Analysis Jobs ───────────────────────────────────────────────────
export const analysisJobApi = {
  list: (projectId: string) =>
    request<{ items: any[]; total_count: number }>(`${BASE}/projects/${projectId}/analysis-jobs`),
  create: (projectId: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/analysis-jobs`, { method: "POST", body: JSON.stringify(body) }),
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
  changeItemStatus: (projectId: string, checklistId: string, itemId: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/checklists/${checklistId}/items/${itemId}/status`, {
      method: "POST", body: JSON.stringify(body),
    }),
};

// ── Inquiries ───────────────────────────────────────────────────────
export const inquiryApi = {
  list: (projectId: string, params?: string) =>
    request<any[]>(`${BASE}/projects/${projectId}/inquiries${params ? `?${params}` : ""}`),
  create: (projectId: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/inquiries`, { method: "POST", body: JSON.stringify(body) }),
  get: (projectId: string, id: string) =>
    request<any>(`${BASE}/projects/${projectId}/inquiries/${id}`),
  changeStatus: (projectId: string, id: string, body: any) =>
    request<any>(`${BASE}/projects/${projectId}/inquiries/${id}/status`, {
      method: "POST", body: JSON.stringify(body),
    }),
};
