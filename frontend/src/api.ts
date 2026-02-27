const BASE = "/v1";

function getToken(): string | null {
  return localStorage.getItem("token");
}

class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, opts: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(opts.headers as Record<string, string>),
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  const res = await fetch(`${BASE}${path}`, { ...opts, headers });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(body.message || res.statusText, res.status);
  }
  return res.json();
}

// Auth
export const auth = {
  register: (email: string, password: string, displayName: string) =>
    request("/auth/register", {
      method: "POST",
      body: JSON.stringify({ email, password, displayName }),
    }),
  login: (email: string, password: string) =>
    request<{ userId: string; token: string; displayName: string }>(
      "/auth/login",
      { method: "POST", body: JSON.stringify({ email, password }) }
    ),
};

// Folders
export const folders = {
  list: () => request<{ folders: any[] }>("/folders"),
  create: (data: { name: string; icon: string; color: string; visibility: string; bannerUrl?: string; tags?: string[] }) =>
    request("/folders", { method: "POST", body: JSON.stringify(data) }),
  get: (id: string) => request<{ folder: any }>(`/folders/${id}`),
  update: (id: string, data: any) =>
    request(`/folders/${id}`, { method: "PUT", body: JSON.stringify({ folderId: id, ...data }) }),
  delete: (id: string) =>
    request(`/folders/${id}`, { method: "DELETE" }),
  share: (id: string) =>
    request<{ shareToken: string; shareUrl: string }>(`/folders/${id}/share`, { method: "POST" }),
  addCollaborator: (folderId: string, email: string, role: string) =>
    request(`/folders/${folderId}/collaborators`, {
      method: "POST",
      body: JSON.stringify({ email, role }),
    }),
  removeCollaborator: (folderId: string, userId: string) =>
    request(`/folders/${folderId}/collaborators/${userId}`, { method: "DELETE" }),
};

// Links
export const links = {
  list: (folderId?: string) =>
    request<{ links: any[] }>(`/links${folderId ? `?folderId=${folderId}` : ""}`),
  create: (data: any) =>
    request("/links", { method: "POST", body: JSON.stringify(data) }),
  get: (id: string) => request<{ link: any }>(`/links/${id}`),
  update: (id: string, data: any) =>
    request(`/links/${id}`, { method: "PUT", body: JSON.stringify({ linkId: id, ...data }) }),
  delete: (id: string) =>
    request(`/links/${id}`, { method: "DELETE" }),
  preview: (url: string) =>
    request<{ title: string; description: string; image: string }>(
      `/links/preview?url=${encodeURIComponent(url)}`
    ),
};

// Shared
export const shared = {
  get: (token: string) =>
    request<{ folder: any; links: any[] }>(`/share/${token}`),
};

// Community
export const community = {
  list: (search?: string, pageSize?: number, pageToken?: string) => {
    const params = new URLSearchParams();
    if (search) params.set("search", search);
    if (pageSize) params.set("pageSize", String(pageSize));
    if (pageToken) params.set("pageToken", pageToken);
    const qs = params.toString();
    return request<{ folders: any[]; nextPageToken: string }>(
      `/community/folders${qs ? `?${qs}` : ""}`
    );
  },
  top: (limit?: number) =>
    request<{ folders: any[] }>(`/community/top${limit ? `?limit=${limit}` : ""}`),
  like: (folderId: string) =>
    request<{ likeCount: number }>(`/folders/${folderId}/like`, { method: "POST", body: JSON.stringify({}) }),
  unlike: (folderId: string) =>
    request<{ likeCount: number }>(`/folders/${folderId}/like`, { method: "DELETE" }),
};

// Children
export const children = {
  list: () => request<{ children: any[] }>("/children"),
  create: (name: string, birthDate: number) =>
    request("/children", { method: "POST", body: JSON.stringify({ name, birthDate }) }),
  update: (childId: string, name: string, birthDate: number) =>
    request(`/children/${childId}`, { method: "PUT", body: JSON.stringify({ childId, name, birthDate }) }),
  delete: (childId: string) =>
    request(`/children/${childId}`, { method: "DELETE" }),
};

// AI
export const ai = {
  generate: (prompt: string, model?: string) =>
    request<{ ideas: Array<{
      title: string;
      description: string;
      url?: string;
      imageUrl?: string;
      category: string;
      tags: string[];
      ageRange?: string;
      price?: string;
      location?: string;
      ingredients?: string[];
    }> }>("/ai/generate", {
      method: "POST",
      body: JSON.stringify({ prompt, model }),
    }),
};
