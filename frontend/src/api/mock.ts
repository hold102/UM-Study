import { Announcement, CalendarInfo, Course, FileItem, Me, Section, Submission } from "./client";

const me: Me = {
  id: "demo-user",
  email: "tan.liang@um.edu.my",
  displayName: "Tan Liang",
};

const courses: Course[] = [
  { id: "c-wia2005", code: "WIA2005", name: "Algorithm Design and Analysis" },
  { id: "c-wia2006", code: "WIA2006", name: "Operating Systems" },
  { id: "c-wix1003", code: "WIX1003", name: "Fundamentals of Computing" },
  { id: "c-wid2001", code: "WID2001", name: "Database Systems" },
  { id: "c-wxes1116", code: "WXES1116", name: "Software Engineering" },
];

const sectionsByCourse: Record<string, Section[]> = {};
for (const c of courses) {
  const rows: Section[] = [];
  for (let w = 1; w <= 14; w++) {
    rows.push({ id: `${c.id}-w${w}`, title: `Lecture Week ${w}`, week: w, bucket: null });
  }
  rows.push({ id: `${c.id}-proj`, title: "Project Folder", week: null, bucket: "project" });
  rows.push({ id: `${c.id}-pyq`, title: "Past Year Papers", week: null, bucket: "pastyear" });
  rows.push({ id: `${c.id}-asg`, title: "Assignments", week: null, bucket: "project" });
  sectionsByCourse[c.id] = rows;
}

function filesForSection(courseId: string, sectionId: string): FileItem[] {
  const m = /-w(\d+)$/.exec(sectionId);
  if (m) {
    const w = Number(m[1]);
    return [
      { id: `${sectionId}-slides`, name: `W${w}_Slides.pdf`, fileType: "slides",
        downloadUrl: "#", sizeBytes: 1_200_000, week: w, folder: null, description: null, category: "slides" },
      { id: `${sectionId}-tut`, name: `W${w}_Tutorial.pdf`, fileType: "tutorial",
        downloadUrl: "#", sizeBytes: 240_000, week: w, folder: null, category: "tutorial",
        description: `Dear Students,\n\nThis is the tutorial for Week ${w}. Please complete questions 1–5 before the next class.\n\nThank you.` },
      { id: `${sectionId}-reading`, name: `Reading list (external)`, fileType: "link",
        downloadUrl: "https://example.com/reading", sizeBytes: null, week: w, folder: null, description: null, category: null },
      { id: `${sectionId}-ref1`, name: `Reference_A.pdf`, fileType: "pdf",
        downloadUrl: "#", sizeBytes: 320_000, week: w, folder: "Reference Materials", description: null, category: null },
      { id: `${sectionId}-ref2`, name: `Reference_B.pdf`, fileType: "pdf",
        downloadUrl: "#", sizeBytes: 410_000, week: w, folder: "Reference Materials", description: null, category: null },
      { id: `${sectionId}-code1`, name: `example_${w}.zip`, fileType: "zip",
        downloadUrl: "#", sizeBytes: 88_000, week: w, folder: "Code Samples", description: null, category: null },
    ];
  }
  if (sectionId.endsWith("-proj")) {
    return [
      { id: `${sectionId}-brief`, name: "Project_Brief.pdf", fileType: "notes",
        downloadUrl: "#", sizeBytes: 540_000, week: null, folder: null, description: null, category: null },
      { id: `${sectionId}-rubric`, name: "Project_Rubric.pdf", fileType: "notes",
        downloadUrl: "#", sizeBytes: 220_000, week: null, folder: null, description: null, category: null },
    ];
  }
  if (sectionId.endsWith("-pyq")) {
    return [
      { id: `${sectionId}-2024`, name: "2024_Final.pdf", fileType: "other",
        downloadUrl: "#", sizeBytes: 480_000, week: null, folder: null, description: null, category: null },
      { id: `${sectionId}-2023`, name: "2023_Final.pdf", fileType: "other",
        downloadUrl: "#", sizeBytes: 460_000, week: null, folder: null, description: null, category: null },
    ];
  }
  if (sectionId.endsWith("-asg")) {
    return [
      { id: `${sectionId}-a1`, name: "Assignment_1.pdf", fileType: "other",
        downloadUrl: "#", sizeBytes: 180_000, week: null, folder: null, description: null, category: null },
    ];
  }
  return [];
}

const ANNOUNCEMENT_TEMPLATES: Array<Omit<Announcement, "id" | "postedAt" | "week" | "images" | "links">> = [
  { title: "Tutorial slot moved to Friday", author: "Dr. Lim",
    body: "This week's tutorial will be held on Friday 2pm in DK-3 instead of Thursday. Please bring the worked example from the lecture." },
  { title: "Submission deadline extended", author: "Dr. Lim",
    body: "Given the public holiday, the assignment deadline is pushed back by 48 hours. New due date: Monday 23:59." },
  { title: "Reading for next week",
    body: "Please skim Chapter 4 (Greedy Algorithms) before the next lecture. Focus on the activity-selection proof.", author: "Dr. Lim" },
  { title: "Office hours this week",
    body: "I will be available Wednesday 4–6pm, room A2-09. Drop in if anything from the lecture is unclear.", author: "Dr. Lim" },
];

const GENERAL_ANNOUNCEMENT_TEMPLATES: Array<Omit<Announcement, "id" | "postedAt" | "week" | "images" | "links">> = [
  { title: "Course outline & grading scheme", author: "Dr. Lim",
    body: "Welcome to the course. The full syllabus, grading scheme, and weekly schedule are attached. Please read through before our first tutorial." },
  { title: "Communication channel",
    body: "All course discussions will happen on the class Telegram group. Link in the materials below.", author: "Dr. Lim" },
];

function generalAnnouncements(courseId: string): Announcement[] {
  const base = new Date();
  base.setDate(base.getDate() - 30);
  return GENERAL_ANNOUNCEMENT_TEMPLATES.map((t, i) => {
    const posted = new Date(base);
    posted.setDate(posted.getDate() + i);
    return {
      id: `${courseId}-general-${i}`,
      title: t.title,
      body: t.body,
      author: t.author,
      postedAt: posted.toISOString(),
      week: null,
      images: [],
      links: i === 0 ? [
        { url: "https://docs.google.com/document/d/syllabus-example", label: "Course syllabus" },
      ] : [
        { url: "https://t.me/example-class", label: "Telegram group" },
      ],
    };
  });
}

function announcementsForWeek(courseId: string, week: number): Announcement[] {
  const seed = (courseId.length + week) % ANNOUNCEMENT_TEMPLATES.length;
  const count = ((week % 3) + 1);
  const out: Announcement[] = [];
  for (let i = 0; i < count; i++) {
    const t = ANNOUNCEMENT_TEMPLATES[(seed + i) % ANNOUNCEMENT_TEMPLATES.length];
    const posted = new Date();
    posted.setDate(posted.getDate() - i);
    out.push({
      id: `${courseId}-w${week}-ann${i}`,
      title: t.title,
      body: t.body,
      author: t.author,
      postedAt: posted.toISOString(),
      week,
      images: i === 0 && week % 2 === 0
        ? [`https://picsum.photos/seed/ann-${courseId}-w${week}/640/360`]
        : [],
      links: i === 0 ? [
        { url: `https://docs.google.com/document/d/example-${week}`, label: "Reading material (Google Doc)" },
        { url: `https://meet.google.com/example`, label: "Meet link" },
      ] : [],
    });
  }
  return out;
}

function sleep(ms: number) {
  return new Promise(r => setTimeout(r, ms));
}

export const mockApi = {
  async me() { await sleep(80); return me; },
  async spectrumLogin(_token: string) {
    await sleep(200);
    return me;
  },
  async courses() { await sleep(120); return courses; },
  async sections(courseId: string) {
    await sleep(120);
    return sectionsByCourse[courseId] ?? [];
  },
  async files(courseId: string, params: { week?: number; bucket?: string; type?: string }) {
    await sleep(120);
    const sections = sectionsByCourse[courseId] ?? [];
    let matched: Section[];
    if (params.week != null) matched = sections.filter(s => s.week === params.week);
    else if (params.bucket) matched = sections.filter(s => s.bucket === params.bucket);
    else matched = sections;

    let files = matched.flatMap(s => filesForSection(courseId, s.id));
    if (params.type) files = files.filter(f => f.fileType === params.type);
    return files;
  },
  async submissions(courseId: string, params: { week?: number }): Promise<Submission[]> {
    await sleep(140);
    const now = Date.now();
    const dayMs = 86400000;
    const items: Submission[] = [];
    for (let w = 1; w <= 14; w++) {
      const dueOffsetDays = (w - 11) * 7 + 3;
      const due = new Date(now + dueOffsetDays * dayMs);
      const isPast = due.getTime() < now;
      const submitted = w <= 9 && (w % 2 === 0);
      const status: Submission["status"] =
        submitted ? "submitted" : isPast ? "overdue" : "pending";
      items.push({
        id: `${courseId}-w${w}-assign`,
        title: `Assignment ${w}`,
        kind: "assignment",
        week: w,
        dueAt: due.toISOString(),
        openAt: null,
        status,
        submittedAt: submitted ? new Date(due.getTime() - dayMs).toISOString() : null,
        description: `Implement the algorithm from this week's lecture and submit a short report.\n\nMarking is based on correctness, complexity analysis, and code style. Pair work allowed.`,
        materials: w % 2 === 0 ? [
          { name: `A${w}_Brief.pdf`, fileType: "pdf", downloadUrl: "#", sizeBytes: 320_000 },
          { name: `A${w}_Rubric.pdf`, fileType: "pdf", downloadUrl: "#", sizeBytes: 110_000 },
        ] : [],
      });
      if (w % 3 === 0) {
        items.push({
          id: `${courseId}-w${w}-quiz`,
          title: `Quiz ${w / 3}`,
          kind: "quiz",
          week: w,
          dueAt: new Date(due.getTime() - dayMs).toISOString(),
          openAt: new Date(due.getTime() - 4 * dayMs).toISOString(),
          status: w <= 9 ? "submitted" : isPast ? "overdue" : "pending",
          submittedAt: w <= 9 ? new Date(due.getTime() - 2 * dayMs).toISOString() : null,
          description: null,
          materials: [],
        });
      }
    }
    return params.week != null ? items.filter(s => s.week === params.week) : items;
  },
  async announcements(courseId: string, params: { week?: number }) {
    await sleep(120);
    const general = generalAnnouncements(courseId);
    if (params.week != null) {
      return [...general, ...announcementsForWeek(courseId, params.week)];
    }
    const all: Announcement[] = [...general];
    for (let w = 1; w <= 14; w++) all.push(...announcementsForWeek(courseId, w));
    return all;
  },
  async calendar(): Promise<CalendarInfo> {
    await sleep(60);
    const start = new Date(2026, 2, 9); // Mar 9
    const breakStart = new Date(2026, 3, 27); // Apr 27
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const dayMs = 86400000;
    const dayOffset = Math.floor((today.getTime() - start.getTime()) / dayMs);
    const breakOffset = Math.floor((breakStart.getTime() - start.getTime()) / dayMs);
    let week: number | null = dayOffset < 0 ? null : Math.floor(dayOffset / 7) + 1;
    const breakCalWeek = Math.floor(breakOffset / 7) + 1;
    if (week != null) {
      if (week === breakCalWeek) week = null;
      else if (week > breakCalWeek) week -= 1;
      if (week != null && week > 14) week = null;
    }
    return {
      semesterStartDate: "2026-03-09",
      semesterBreakStartDate: "2026-04-27",
      currentWeek: week,
      totalWeeks: 14,
    };
  },
  async tokenStatus() { await sleep(60); return { linked: false }; },
  async saveToken(_token: string) { await sleep(120); return { linked: true }; },
  async clearToken() { await sleep(60); return { linked: false }; },
};
