import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, Course, Submission } from "../api/client";
import { Shell } from "../components/Shell";

const STATUS_LABEL: Record<string, string> = {
  submitted: "Submitted",
  pending: "Pending",
  overdue: "Overdue",
  "not-open-yet": "Not open yet",
};

function formatSize(bytes: number | null): string {
  if (bytes == null) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

function formatRelative(iso: string): string {
  const now = Date.now();
  const due = new Date(iso).getTime();
  const diffMs = due - now;
  const past = diffMs < 0;
  const abs = Math.abs(diffMs);
  const dayMs = 86400000;
  const hourMs = 3600000;
  const minMs = 60000;
  if (abs >= dayMs) {
    const days = Math.round(abs / dayMs);
    return past ? `${days} day${days === 1 ? "" : "s"} ago` : `in ${days} day${days === 1 ? "" : "s"}`;
  }
  if (abs >= hourMs) {
    const hours = Math.round(abs / hourMs);
    return past ? `${hours} hour${hours === 1 ? "" : "s"} ago` : `in ${hours} hour${hours === 1 ? "" : "s"}`;
  }
  const mins = Math.max(1, Math.round(abs / minMs));
  return past ? `${mins} min${mins === 1 ? "" : "s"} ago` : `in ${mins} min${mins === 1 ? "" : "s"}`;
}

export default function UpcomingSubmissions() {
  const { courseId } = useParams();
  const [course, setCourse] = useState<Course | null>(null);
  const [submissions, setSubmissions] = useState<Submission[] | null>(null);

  useEffect(() => {
    if (!courseId) return;
    api.courses().then(list => setCourse(list.find(c => c.id === courseId) ?? null));
    api.submissions(courseId, {}).then(setSubmissions).catch(() => setSubmissions([]));
  }, [courseId]);

  const upcoming = useMemo(() => {
    if (!submissions) return null;
    const now = Date.now();
    return submissions
      .filter(s => {
        if (s.status === "submitted") return false;
        if (!s.dueAt) return true;
        return new Date(s.dueAt).getTime() >= now;
      })
      .sort((a, b) => {
        const ad = a.dueAt ? new Date(a.dueAt).getTime() : Number.POSITIVE_INFINITY;
        const bd = b.dueAt ? new Date(b.dueAt).getTime() : Number.POSITIVE_INFINITY;
        return ad - bd;
      });
  }, [submissions]);

  const overdue = useMemo(() => {
    if (!submissions) return null;
    const now = Date.now();
    return submissions
      .filter(s => s.status !== "submitted" && s.dueAt && new Date(s.dueAt).getTime() < now)
      .sort((a, b) => new Date(b.dueAt!).getTime() - new Date(a.dueAt!).getTime());
  }, [submissions]);

  const renderCard = (s: Submission) => (
    <div key={s.id} className={`submission-card status-${s.status}`}>
      <div className="submission-head">
        <div className="file-row-main">
          <div className="name">
            <span className={`submission-kind kind-${s.kind}`}>{s.kind}</span>
            {s.title}
            {s.week != null && (
              <span style={{ color: "var(--muted)", fontWeight: 400, fontSize: 13, marginLeft: 8 }}>
                · Week {s.week}
              </span>
            )}
          </div>
          <div className="meta">
            {s.dueAt
              ? <>Due {formatDateTime(s.dueAt)} <span style={{ color: "var(--muted)" }}>({formatRelative(s.dueAt)})</span></>
              : "No due date"}
            {s.submittedAt && ` · Turned in ${formatDateTime(s.submittedAt)}`}
          </div>
        </div>
        <span className={`status-badge status-${s.status}`}>{STATUS_LABEL[s.status]}</span>
      </div>
      {s.description && (
        <details className="submission-description">
          <summary>Description</summary>
          <div className="submission-description-body">{s.description}</div>
        </details>
      )}
      {s.materials && s.materials.length > 0 && (
        <div className="submission-materials">
          {s.materials.map((mat, idx) => (
            <a key={idx} className="material-chip" href={mat.downloadUrl} download
               title={mat.sizeBytes ? `${mat.fileType ?? "file"} · ${formatSize(mat.sizeBytes)}` : (mat.fileType ?? "file")}>
              <span className="material-clip" aria-hidden>📎</span>
              <span className="material-name">{mat.name}</span>
            </a>
          ))}
        </div>
      )}
    </div>
  );

  return (
    <Shell>
      <main className="page wide">
        <Link to={`/courses/${courseId}`} className="back-link">← Back to weeks</Link>
        <h1 className="page-title">
          Upcoming submissions
          {course && (
            <span style={{ color: "var(--muted)", fontWeight: 400, fontSize: 16 }}>
              {" · "}{course.code}
            </span>
          )}
        </h1>
        <p className="page-sub">
          {upcoming === null
            ? "Loading submissions…"
            : upcoming.length === 0 && (overdue?.length ?? 0) === 0
              ? "Nothing pending. You're all caught up."
              : `${upcoming.length} pending · sorted by closest deadline.`}
        </p>

        {upcoming === null ? (
          <div className="skeleton-grid">
            {Array.from({ length: 3 }).map((_, i) => <div key={i} className="skeleton" />)}
          </div>
        ) : (
          <>
            {upcoming.length > 0 && (
              <div className="file-list" style={{ marginBottom: 32 }}>
                {upcoming.map(renderCard)}
              </div>
            )}

            {overdue && overdue.length > 0 && (
              <>
                <p className="page-sub" style={{ marginTop: 4, marginBottom: 12 }}>
                  Overdue ({overdue.length})
                </p>
                <div className="file-list">
                  {overdue.map(renderCard)}
                </div>
              </>
            )}

            {upcoming.length === 0 && (overdue?.length ?? 0) === 0 && (
              <div className="empty">No submissions to track right now.</div>
            )}
          </>
        )}
      </main>
    </Shell>
  );
}
