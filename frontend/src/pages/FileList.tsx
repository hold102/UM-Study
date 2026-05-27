import { useEffect, useMemo, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { Announcement, api, Course, FileItem, Section, Submission } from "../api/client";
import { Shell } from "../components/Shell";
import { Lightbox } from "../components/Lightbox";
import { useReadAnnouncements } from "../utils/readAnnouncements";

const TYPE_CHIPS = [
  { value: "slides", label: "Slides" },
  { value: "tutorial", label: "Tutorial" },
  { value: "announcement", label: "Announcement" },
  { value: "submission", label: "Submission" },
];

const BUCKET_LABEL: Record<string, string> = {
  project: "Project",
  pastyear: "Past Year Papers",
};

const BUCKET_SUBHEAD: Record<string, string> = {
  project: "Briefs, rubrics, assignments, and reference materials for the project.",
  pastyear: "Previous semesters' exam papers — useful for revision.",
};

const PROJECT_GROUPS: Array<{ key: string; label: string; pattern: RegExp }> = [
  { key: "briefs", label: "Briefs & Instructions", pattern: /brief|outline|guideline|instruction|overview/i },
  { key: "rubrics", label: "Rubrics & Marking", pattern: /rubric|marking|grading|criteri/i },
  { key: "templates", label: "Templates & Samples", pattern: /template|sample/i },
];

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

function extractTopic(title: string): string | null {
  if (!title) return null;
  // Strip leading bracketed tags like "[F2F - ONLINE]"
  const stripped = title.replace(/^\s*\[[^\]]*\]\s*/, "").trim();
  // "Week N: Topic" or "Week N - Topic" or "Week N – Topic"
  const m = stripped.match(/^\s*week\s*0*\d+\s*[:\-–—]\s*(.+?)\s*$/i);
  if (m) return m[1];
  // If the remaining title is just "Week N" with no topic, no topic to show.
  if (/^\s*week\s*0*\d+\s*$/i.test(stripped)) return null;
  // Otherwise the section title itself is the topic (e.g., "Lab Materials").
  return stripped || null;
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function formatDateTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleString(undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

export default function FileList() {
  const { courseId, week, bucket } = useParams();
  const [course, setCourse] = useState<Course | null>(null);
  const [sections, setSections] = useState<Section[] | null>(null);
  const [files, setFiles] = useState<FileItem[] | null>(null);
  const [announcements, setAnnouncements] = useState<Announcement[] | null>(null);
  const [submissions, setSubmissions] = useState<Submission[] | null>(null);
  const [type, setType] = useState<string | null>(week ? "slides" : null);
  const [lightboxSrc, setLightboxSrc] = useState<string | null>(null);
  const [openFolders, setOpenFolders] = useState<Set<string>>(new Set());
  const { readIds, toggle: toggleRead } = useReadAnnouncements(courseId);

  const showAnnouncements = type === "announcement";
  const showSubmissions = type === "submission";

  useEffect(() => {
    if (!courseId) return;
    api.courses().then(list => setCourse(list.find(c => c.id === courseId) ?? null));
    api.sections(courseId).then(setSections).catch(() => setSections([]));
  }, [courseId]);

  // When the URL switches to a different week or to a bucket view, reset the type filter
  // (week view defaults to "slides", bucket view defaults to "all").
  useEffect(() => {
    setType(week ? "slides" : null);
  }, [week, bucket]);

  useEffect(() => {
    if (!courseId) return;
    if (showAnnouncements) {
      setAnnouncements(null);
      api.announcements(courseId, { week: week ? Number(week) : undefined }).then(setAnnouncements);
    } else if (showSubmissions) {
      setSubmissions(null);
      api.submissions(courseId, { week: week ? Number(week) : undefined }).then(setSubmissions);
    } else {
      setFiles(null);
      api.files(courseId, {
        week: week ? Number(week) : undefined,
        bucket: bucket || undefined,
        type: type ?? undefined,
      }).then(setFiles);
    }
  }, [courseId, week, bucket, type, showAnnouncements, showSubmissions]);

  const weekNum = week ? Number(week) : null;
  const matchedSection = weekNum != null && sections
    ? sections.find(s => s.week === weekNum)
      ?? sections.find(s => new RegExp(`\\bweek\\s*0*${weekNum}\\b`, "i").test(s.title))
    : null;
  const weekTopic = matchedSection ? extractTopic(matchedSection.title) : null;

  const heading = week
    ? weekTopic ? `Week ${week} (${weekTopic})` : `Week ${week}`
    : bucket
      ? BUCKET_LABEL[bucket] ?? bucket
      : "Files";

  const grouped = useMemo(() => {
    const loose: FileItem[] = [];
    const folders = new Map<string, FileItem[]>();
    for (const f of files ?? []) {
      if (f.folder) {
        const arr = folders.get(f.folder) ?? [];
        arr.push(f);
        folders.set(f.folder, arr);
      } else {
        loose.push(f);
      }
    }
    return { loose, folders };
  }, [files]);

  const toggleFolder = (name: string) => {
    setOpenFolders(prev => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name); else next.add(name);
      return next;
    });
  };

  const baseSubhead = showAnnouncements
    ? "Announcements from your lecturer."
    : showSubmissions
      ? "Assignments and quizzes — what's due and what you've turned in."
      : bucket && BUCKET_SUBHEAD[bucket]
        ? BUCKET_SUBHEAD[bucket]
        : bucket
          ? "Materials for this section."
          : "Only the files you need right now.";
  const itemSuffix = bucket && files && files.length > 0
    ? ` · ${files.length} item${files.length === 1 ? "" : "s"}`
    : "";
  const subhead = baseSubhead + itemSuffix;

  const projectGroups = useMemo(() => {
    if (bucket !== "project" || type !== null) return null;
    const buckets: Record<string, FileItem[]> = { briefs: [], rubrics: [], templates: [], other: [] };
    for (const f of grouped.loose) {
      const match = PROJECT_GROUPS.find(g => g.pattern.test(f.name));
      buckets[match ? match.key : "other"].push(f);
    }
    return buckets;
  }, [grouped.loose, bucket, type]);

  const renderAnnouncement = (a: Announcement) => {
    const isRead = readIds.has(a.id);
    return (
    <article key={a.id} className="announcement" style={{ opacity: isRead ? 0.65 : 1 }}>
      <header>
        <h2>
          {!isRead && <span aria-hidden style={{ color: "var(--accent, #c2410c)", marginRight: 8 }}>●</span>}
          {a.title}
        </h2>
        <span className="meta">
          {a.author ? `${a.author} · ` : ""}{formatDate(a.postedAt)}
          {" · "}
          <button type="button"
                  onClick={() => toggleRead(a.id)}
                  style={{ background: "none", border: "none", padding: 0, cursor: "pointer", color: "var(--muted)", textDecoration: "underline", font: "inherit" }}>
            {isRead ? "Mark as unread" : "Mark as read"}
          </button>
        </span>
      </header>
      {a.body && (
        <details style={{ marginTop: 8 }}>
          <summary style={{ cursor: "pointer", color: "var(--muted)", fontSize: 13, userSelect: "none" }}>
            Description
          </summary>
          <p style={{ whiteSpace: "pre-wrap", margin: "6px 0 0", lineHeight: 1.5 }}>
            {a.body}
          </p>
        </details>
      )}
      {a.links && a.links.length > 0 && (
        <div className="announcement-links">
          {a.links.map((l, idx) => (
            <a key={idx} className="announcement-link" href={l.url}
               target="_blank" rel="noopener noreferrer" title={l.url}>
              <span className="link-icon" aria-hidden>↗</span>
              <span className="link-label">{l.label}</span>
            </a>
          ))}
        </div>
      )}
      {a.images && a.images.length > 0 && (
        <div className="announcement-images">
          {a.images.map((src, idx) => (
            <button key={idx} type="button" className="image-thumb"
                    onClick={() => setLightboxSrc(src)}
                    aria-label="View image">
              <img src={src} alt="" loading="lazy" />
            </button>
          ))}
        </div>
      )}
    </article>
    );
  };

  const renderFileRow = (f: FileItem, inFolder = false) => {
    const isLink = f.fileType === "link";
    // In the "All" view of a week, dim files already covered by the Slides/Tutorial chip
    // so the user can spot what's not yet categorized.
    const dimmed = !!week && type === null && f.category != null;
    return (
      <div key={f.id} className={`file-row ${isLink ? "is-link" : ""} ${inFolder ? "in-folder" : ""}`}
           style={dimmed ? { opacity: 0.5 } : undefined}
           title={dimmed ? `Also appears under ${f.category === "slides" ? "Slides" : "Tutorial"}` : undefined}>
        <div className="file-row-main">
          <div className="name">
            {isLink && <span className="link-icon" aria-hidden>↗</span>}
            {f.name}
          </div>
          {isLink ? (
            <a className="meta link-url" href={f.downloadUrl} target="_blank" rel="noopener noreferrer">
              {f.downloadUrl}
            </a>
          ) : (
            <div className="meta">
              {f.fileType ?? "file"}{f.sizeBytes ? ` · ${formatSize(f.sizeBytes)}` : ""}
            </div>
          )}
          {f.description && (
            <details style={{ marginTop: 8 }}>
              <summary style={{ cursor: "pointer", color: "var(--muted)", fontSize: 13, userSelect: "none" }}>
                Description
              </summary>
              <p style={{ color: "var(--muted)", fontSize: 13, whiteSpace: "pre-wrap", margin: "6px 0 0", lineHeight: 1.5 }}>
                {f.description}
              </p>
            </details>
          )}
        </div>
        {isLink ? (
          <a className="btn-ghost" href={f.downloadUrl} target="_blank" rel="noopener noreferrer">Open</a>
        ) : (
          <a className="btn-ghost" href={f.downloadUrl} download>Download</a>
        )}
      </div>
    );
  };

  return (
    <Shell>
      <main className="page wide">
        <Link to={`/courses/${courseId}`} className="back-link">← Back to weeks</Link>
        <h1 className="page-title">
          {heading}
          {course && (
            <span style={{ color: "var(--muted)", fontWeight: 400, fontSize: 16 }}>
              {" · "}{course.code}
            </span>
          )}
        </h1>
        <p className="page-sub">{subhead}</p>

        <div className="pills" style={{ marginBottom: 28 }}>
          <button className={`pill ${type === null ? "active" : ""}`}
                  onClick={() => setType(null)}>All</button>
          {TYPE_CHIPS
            .filter(t => !bucket || (t.value !== "announcement" && t.value !== "submission"))
            .map(t => (
              <button key={t.value} className={`pill ${type === t.value ? "active" : ""}`}
                      onClick={() => setType(t.value)}>
                {t.label}
              </button>
          ))}
        </div>

        {showAnnouncements ? (
          announcements === null ? (
            <div className="skeleton-grid">
              {Array.from({ length: 2 }).map((_, i) => <div key={i} className="skeleton" />)}
            </div>
          ) : announcements.length === 0 ? (
            <div className="empty">No announcements this week.</div>
          ) : (() => {
            const general = announcements.filter(a => a.week == null);
            const weekly = announcements.filter(a => a.week != null);
            return (
              <>
                {general.length > 0 && (
                  <>
                    <p className="page-sub" style={{ marginTop: 4, marginBottom: 12 }}>General</p>
                    <div className="file-list" style={{ marginBottom: 28 }}>
                      {general.map(renderAnnouncement)}
                    </div>
                  </>
                )}
                {weekly.length > 0 && (
                  <>
                    <p className="page-sub" style={{ marginTop: 4, marginBottom: 12 }}>
                      {week ? `Week ${week}` : "By week"}
                    </p>
                    <div className="file-list">
                      {weekly.map(renderAnnouncement)}
                    </div>
                  </>
                )}
              </>
            );
          })()
        ) : showSubmissions ? (
          submissions === null ? (
            <div className="skeleton-grid">
              {Array.from({ length: 3 }).map((_, i) => <div key={i} className="skeleton" />)}
            </div>
          ) : submissions.length === 0 ? (
            <div className="empty">No assignments or quizzes this week.</div>
          ) : (
            <div className="file-list">
              {submissions.map(s => (
                <div key={s.id} className={`submission-card status-${s.status}`}>
                  <div className="submission-head">
                    <div className="file-row-main">
                      <div className="name">
                        <span className={`submission-kind kind-${s.kind}`}>{s.kind}</span>
                        {s.title}
                      </div>
                      <div className="meta">
                        {s.dueAt ? `Due ${formatDateTime(s.dueAt)}` : "No due date"}
                        {s.submittedAt && ` · Turned in ${formatDate(s.submittedAt)}`}
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
              ))}
            </div>
          )
        ) : files === null ? (
          <div className="skeleton-grid">
            {Array.from({ length: 3 }).map((_, i) => <div key={i} className="skeleton" />)}
          </div>
        ) : files.length === 0 ? (
          <div className="empty">
            {bucket && type === null
              ? `Your lecturer hasn't uploaded anything to ${BUCKET_LABEL[bucket] ?? bucket} yet.`
              : "Nothing for this filter."}
          </div>
        ) : (
          <>
            {projectGroups ? (
              <>
                {PROJECT_GROUPS.map(g => projectGroups[g.key].length > 0 && (
                  <div key={g.key}>
                    <p className="page-sub" style={{ marginTop: 4, marginBottom: 12 }}>{g.label}</p>
                    <div className="file-list" style={{ marginBottom: 28 }}>
                      {projectGroups[g.key].map(f => renderFileRow(f))}
                    </div>
                  </div>
                ))}
                {projectGroups.other.length > 0 && (
                  <div>
                    <p className="page-sub" style={{ marginTop: 4, marginBottom: 12 }}>Other</p>
                    <div className="file-list" style={{ marginBottom: 28 }}>
                      {projectGroups.other.map(f => renderFileRow(f))}
                    </div>
                  </div>
                )}
              </>
            ) : (
              <div className="file-list">
                {grouped.loose.map(f => renderFileRow(f))}
              </div>
            )}
            {grouped.folders.size > 0 && (
              <div className="file-list">
                {Array.from(grouped.folders.entries()).map(([folderName, items]) => {
                  const isOpen = openFolders.has(folderName);
                  const allCategorized = !!week && type === null && items.length > 0
                      && items.every(f => f.category != null);
                  return (
                    <div key={folderName} className={`folder-card ${isOpen ? "open" : ""}`}
                         style={allCategorized ? { opacity: 0.5 } : undefined}
                         title={allCategorized ? "Every file in this folder already appears under Slides or Tutorial" : undefined}>
                      <button type="button" className="folder-head"
                              aria-expanded={isOpen}
                              onClick={() => toggleFolder(folderName)}>
                        <span className="folder-icon" aria-hidden>{isOpen ? "📂" : "📁"}</span>
                        <span className="folder-name">{folderName}</span>
                        <span className="folder-count">{items.length} {items.length === 1 ? "file" : "files"}</span>
                        <span className={`folder-chevron ${isOpen ? "open" : ""}`} aria-hidden>›</span>
                      </button>
                      {isOpen && (
                        <div className="folder-body">
                          {items.map(f => renderFileRow(f, true))}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </>
        )}
      </main>
      {lightboxSrc && <Lightbox src={lightboxSrc} onClose={() => setLightboxSrc(null)} />}
    </Shell>
  );
}
