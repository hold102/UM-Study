import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Announcement, api, CalendarInfo, Course, Section } from "../api/client";
import { Shell } from "../components/Shell";
import { Lightbox } from "../components/Lightbox";
import { useReadAnnouncements } from "../utils/readAnnouncements";

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 86400000;
const TOTAL_WEEKS = 14;

// Mirrors SemesterCalendar.weekOfDate on the backend: maps a post date to the academic week,
// skipping the mid-semester break week. Returns null for dates outside the semester.
function weekFromDate(iso: string, info: CalendarInfo | null): number | null {
  if (!info?.semesterStartDate) return null;
  const start = Date.parse(info.semesterStartDate);
  const d = Date.parse(iso);
  if (Number.isNaN(start) || Number.isNaN(d)) return null;
  const dayOffset = Math.floor((d - start) / DAY_MS);
  if (dayOffset < 0) return null;
  let calWeek = Math.floor(dayOffset / 7) + 1;
  if (info.semesterBreakStartDate) {
    const breakDay = Date.parse(info.semesterBreakStartDate);
    if (!Number.isNaN(breakDay)) {
      const breakOffset = Math.floor((breakDay - start) / DAY_MS);
      const breakCalWeek = Math.floor(breakOffset / 7) + 1;
      if (calWeek === breakCalWeek) return null;
      if (calWeek > breakCalWeek) calWeek -= 1;
    }
  }
  return calWeek > TOTAL_WEEKS ? null : calWeek;
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

const BUCKET_LABEL: Record<string, string> = {
  project: "Project",
  pastyear: "Past Year Papers",
};

export default function WeekPicker() {
  const { courseId } = useParams();
  const [course, setCourse] = useState<Course | null>(null);
  const [sections, setSections] = useState<Section[] | null>(null);
  const [announcements, setAnnouncements] = useState<Announcement[]>([]);
  const [openWeek, setOpenWeek] = useState<number | null>(null);
  const [cw, setCw] = useState<number | null>(null);
  const [calendarInfo, setCalendarInfo] = useState<CalendarInfo | null>(null);
  const [lightboxSrc, setLightboxSrc] = useState<string | null>(null);
  const { readIds, toggle: toggleRead } = useReadAnnouncements(courseId);

  useEffect(() => {
    if (!courseId) return;
    api.courses().then(list => setCourse(list.find(c => c.id === courseId) ?? null));
    api.sections(courseId).then(setSections);
    api.announcements(courseId, {}).then(setAnnouncements).catch(() => setAnnouncements([]));
  }, [courseId]);

  useEffect(() => {
    let cancelled = false;
    const refresh = () => api.calendar()
        .then(info => {
          if (cancelled) return;
          setCw(info.currentWeek);
          setCalendarInfo(info);
        })
        .catch(() => { if (!cancelled) { setCw(null); setCalendarInfo(null); } });
    refresh();
    const id = window.setInterval(refresh, HOUR_MS);
    return () => { cancelled = true; window.clearInterval(id); };
  }, []);

  const buckets = useMemo(
    () => Array.from(new Set((sections ?? []).map(s => s.bucket).filter(Boolean))) as string[],
    [sections]
  );

  // Resolve a week for each announcement: use its own week if tagged (section/label),
  // otherwise (general/forum post) derive it from the post date.
  const weekOfAnnouncement = (a: Announcement): number | null =>
    a.week ?? weekFromDate(a.postedAt, calendarInfo);

  const annByWeek = useMemo(() => {
    const map = new Map<number, Announcement[]>();
    for (const a of announcements) {
      const w = weekOfAnnouncement(a);
      if (w == null) continue;
      const arr = map.get(w) ?? [];
      arr.push(a);
      map.set(w, arr);
    }
    return map;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [announcements, calendarInfo]);

  const unreadCountByWeek = useMemo(() => {
    const map = new Map<number, number>();
    for (const a of announcements) {
      if (readIds.has(a.id)) continue;
      const w = weekOfAnnouncement(a);
      if (w == null) continue;
      map.set(w, (map.get(w) ?? 0) + 1);
    }
    return map;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [announcements, readIds, calendarInfo]);

  useEffect(() => {
    if (openWeek == null) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setOpenWeek(null); };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [openWeek]);

  return (
    <Shell>
      <main className="page wide" onClick={() => setOpenWeek(null)}>
        <Link to="/courses" className="back-link">← Courses</Link>
        <h1 className="page-title">
          {course ? course.code : "Loading…"}
          {course && <span style={{ color: "var(--muted)", fontWeight: 400, fontSize: 16 }}> · {course.name}</span>}
        </h1>
        <p className="page-sub">
          {cw == null
            ? "Pick a week. The semester is on break or hasn't started."
            : `Pick a week. You are currently in Week ${cw}.`}
        </p>

        {cw != null && (
          <Link className="btn-primary" to={`/courses/${courseId}/week/${cw}`}>
            Jump to current week
          </Link>
        )}

        <div className="week-grid" style={{ marginTop: 28 }}>
          {Array.from({ length: 14 }, (_, i) => i + 1).map(w => {
            const anns = annByWeek.get(w) ?? [];
            const hasAnns = anns.length > 0;
            const unread = unreadCountByWeek.get(w) ?? 0;
            const isOpen = openWeek === w;
            return (
              <div key={w} className="week-tile-wrap">
                <Link to={`/courses/${courseId}/week/${w}`}
                      className={`week-tile ${w === cw ? "current" : ""}`}
                      aria-label={`Week ${w}`}>
                  {w}
                </Link>
                {unread > 0 && (
                  <button
                    type="button"
                    className={`ann-badge ${isOpen ? "open" : ""}`}
                    aria-label={`${unread} unread announcement${unread === 1 ? "" : "s"} for week ${w}`}
                    onClick={(e) => {
                      e.stopPropagation();
                      setOpenWeek(isOpen ? null : w);
                    }}>
                    {unread}
                  </button>
                )}
                {isOpen && hasAnns && (
                  <div className="ann-popover" onClick={(e) => e.stopPropagation()}>
                    <div className="ann-popover-head">
                      <strong>Week {w} announcements</strong>
                      <button className="ann-popover-close" aria-label="Close"
                              onClick={() => setOpenWeek(null)}>×</button>
                    </div>
                    <ul className="ann-popover-list">
                      {anns.map(a => {
                        const isRead = readIds.has(a.id);
                        return (
                        <li key={a.id} style={{ opacity: isRead ? 0.6 : 1 }}>
                          <div className="ann-popover-title">
                            {!isRead && <span aria-hidden style={{ color: "var(--accent, #c2410c)", marginRight: 6 }}>●</span>}
                            {a.title}
                          </div>
                          <div className="ann-popover-meta">
                            {a.author ? `${a.author} · ` : ""}{formatDate(a.postedAt)}
                            {" · "}
                            <button type="button"
                                    onClick={(e) => { e.stopPropagation(); toggleRead(a.id); }}
                                    style={{ background: "none", border: "none", padding: 0, cursor: "pointer", color: "var(--muted)", textDecoration: "underline", font: "inherit" }}>
                              {isRead ? "Mark as unread" : "Mark as read"}
                            </button>
                          </div>
                          {a.body && <p className="ann-popover-body">{a.body}</p>}
                          {a.links && a.links.length > 0 && (
                            <div className="ann-popover-links">
                              {a.links.slice(0, 4).map((l, idx) => (
                                <a key={idx} className="ann-popover-link" href={l.url}
                                   target="_blank" rel="noopener noreferrer"
                                   onClick={(e) => e.stopPropagation()}
                                   title={l.url}>
                                  ↗ {l.label}
                                </a>
                              ))}
                            </div>
                          )}
                          {a.images && a.images.length > 0 && (
                            <div className="ann-popover-images">
                              {a.images.slice(0, 3).map((src, idx) => (
                                <button key={idx} type="button" className="image-thumb"
                                        onClick={(e) => { e.stopPropagation(); setLightboxSrc(src); }}
                                        aria-label="View image">
                                  <img src={src} alt="" loading="lazy" />
                                </button>
                              ))}
                            </div>
                          )}
                        </li>
                        );
                      })}
                    </ul>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        <p className="page-sub" style={{ marginBottom: 12 }}>Other sections</p>
        <div className="pills">
          {buckets.map(b => (
            <Link key={b}
                  to={`/courses/${courseId}/bucket/${b}`}
                  className="pill">
              {BUCKET_LABEL[b] ?? b}
            </Link>
          ))}
          <Link to={`/courses/${courseId}/upcoming`} className="pill">
            Upcoming submissions
          </Link>
        </div>
      </main>
      {lightboxSrc && <Lightbox src={lightboxSrc} onClose={() => setLightboxSrc(null)} />}
    </Shell>
  );
}
