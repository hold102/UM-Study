import { useCallback, useEffect, useState } from "react";

const KEY_PREFIX = "read-anns:";

function load(courseId: string | undefined): Set<string> {
  if (!courseId) return new Set();
  try {
    const raw = localStorage.getItem(KEY_PREFIX + courseId);
    return raw ? new Set(JSON.parse(raw)) : new Set();
  } catch {
    return new Set();
  }
}

function save(courseId: string | undefined, ids: Set<string>) {
  if (!courseId) return;
  try {
    localStorage.setItem(KEY_PREFIX + courseId, JSON.stringify(Array.from(ids)));
  } catch {
    // localStorage may be unavailable (private mode, quota) — fail silently
  }
}

export function useReadAnnouncements(courseId: string | undefined) {
  const [readIds, setReadIds] = useState<Set<string>>(() => load(courseId));

  useEffect(() => {
    setReadIds(load(courseId));
  }, [courseId]);

  const toggle = useCallback((id: string) => {
    setReadIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      save(courseId, next);
      return next;
    });
  }, [courseId]);

  const markRead = useCallback((ids: string[]) => {
    setReadIds(prev => {
      let changed = false;
      const next = new Set(prev);
      for (const id of ids) if (!next.has(id)) { next.add(id); changed = true; }
      if (changed) save(courseId, next);
      return changed ? next : prev;
    });
  }, [courseId]);

  const markUnread = useCallback((ids: string[]) => {
    setReadIds(prev => {
      let changed = false;
      const next = new Set(prev);
      for (const id of ids) if (next.delete(id)) changed = true;
      if (changed) save(courseId, next);
      return changed ? next : prev;
    });
  }, [courseId]);

  return { readIds, toggle, markRead, markUnread };
}
