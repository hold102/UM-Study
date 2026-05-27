import { mockApi } from "./mock";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(path, { credentials: "include", ...init });
  const isAuthCall = path.startsWith("/api/auth/");
  if (res.status === 401 && !isAuthCall) {
    window.location.href = "/login";
    throw new Error("unauthorized");
  }
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return res.json() as Promise<T>;
}

export type Me = { id: string; email: string; displayName: string };
export type Course = { id: string; code: string; name: string };
export type Section = { id: string; title: string; week: number | null; bucket: string | null };
export type FileItem = { id: string; name: string; fileType: string | null; downloadUrl: string; sizeBytes: number | null; week: number | null; folder: string | null; description: string | null; category: string | null };
export type AnnouncementLink = { url: string; label: string };
export type Announcement = { id: string; title: string; body: string; postedAt: string; author: string | null; week: number | null; images: string[]; links: AnnouncementLink[] };
export type CalendarInfo = { semesterStartDate: string | null; semesterBreakStartDate: string | null; currentWeek: number | null; totalWeeks: number };
export type SubmissionStatus = "submitted" | "pending" | "overdue" | "not-open-yet";
export type SubmissionMaterial = {
  name: string;
  fileType: string | null;
  downloadUrl: string;
  sizeBytes: number | null;
};
export type Submission = {
  id: string;
  title: string;
  kind: "assignment" | "quiz";
  week: number | null;
  dueAt: string | null;
  openAt: string | null;
  status: SubmissionStatus;
  submittedAt: string | null;
  description: string | null;
  materials: SubmissionMaterial[];
};

const realApi = {
  me: () => request<Me>("/api/auth/me"),
  spectrumLogin: (token: string) =>
    request<Me>("/api/auth/spectrum-login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token }),
    }),
  courses: () => request<Course[]>("/api/courses"),
  sections: (courseId: string) => request<Section[]>(`/api/courses/${courseId}/sections`),
  files: (courseId: string, params: { week?: number; bucket?: string; type?: string }) => {
    const qs = new URLSearchParams();
    if (params.week != null) qs.set("week", String(params.week));
    if (params.bucket) qs.set("bucket", params.bucket);
    if (params.type) qs.set("type", params.type);
    return request<FileItem[]>(`/api/courses/${courseId}/files?${qs}`);
  },
  announcements: (courseId: string, params: { week?: number }) => {
    const qs = new URLSearchParams();
    if (params.week != null) qs.set("week", String(params.week));
    return request<Announcement[]>(`/api/courses/${courseId}/announcements?${qs}`);
  },
  submissions: (courseId: string, params: { week?: number }) => {
    const qs = new URLSearchParams();
    if (params.week != null) qs.set("week", String(params.week));
    return request<Submission[]>(`/api/courses/${courseId}/submissions?${qs}`);
  },
  calendar: () => request<CalendarInfo>("/api/calendar"),
  tokenStatus: () => request<{ linked: boolean }>("/api/me/spectrum-token"),
  saveToken: (token: string) => request<{ linked: boolean }>("/api/me/spectrum-token", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token }),
  }),
  clearToken: () => request<{ linked: boolean }>("/api/me/spectrum-token", { method: "DELETE" }),
};

export const api = USE_MOCK ? mockApi : realApi;
export const isMock = USE_MOCK;
