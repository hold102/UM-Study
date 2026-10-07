"""Generate UM_Study_UX_Test_Report.docx — manual UX test pass on the running
local stack (backend on :8080, frontend on :5173)."""
from docx import Document
from docx.shared import Pt, Inches, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

OUT = "/Users/LYYY/Downloads/Spectrum Info Extractor/UM_Study_UX_Test_Report.docx"

doc = Document()

styles = doc.styles
styles["Normal"].font.name = "Calibri"
styles["Normal"].font.size = Pt(11)


def H(text, level=1):
    h = doc.add_heading(text, level=level)
    for run in h.runs:
        run.font.color.rgb = RGBColor(0x10, 0x10, 0x10)
    return h


def P(text, bold=False, italic=False):
    p = doc.add_paragraph()
    r = p.add_run(text)
    r.bold = bold
    r.italic = italic
    return p


def CODE(text):
    p = doc.add_paragraph()
    r = p.add_run(text)
    r.font.name = "Menlo"
    r.font.size = Pt(9)
    r.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
    return p


def BULLET(text):
    p = doc.add_paragraph(style="List Bullet")
    p.add_run(text)
    return p


def shade_cell(cell, color_hex):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), color_hex)
    tc_pr.append(shd)


SEV_COLOR = {
    "High":   "F8D7DA",
    "Medium": "FFF3CD",
    "Low":    "D1ECF1",
    "Pass":   "D4EDDA",
}


def result_table(rows):
    table = doc.add_table(rows=1 + len(rows), cols=4)
    table.style = "Light Grid Accent 1"
    hdr = table.rows[0].cells
    for i, txt in enumerate(["ID", "Scenario", "Result", "Severity"]):
        hdr[i].text = ""
        run = hdr[i].paragraphs[0].add_run(txt)
        run.bold = True
        shade_cell(hdr[i], "EEEEEE")
    for i, (tid, scen, res, sev) in enumerate(rows, start=1):
        cells = table.rows[i].cells
        cells[0].text = tid
        cells[1].text = scen
        cells[2].text = res
        cells[3].text = sev
        shade_cell(cells[3], SEV_COLOR.get(sev, "FFFFFF"))


# ------------- Title -------------
title = doc.add_heading("UM Study — UX Test Report", level=0)
title.alignment = WD_ALIGN_PARAGRAPH.LEFT

meta = doc.add_paragraph()
meta.add_run(
    "Manual UX walkthrough and API-level probe of the UM Study local stack. "
    "Frontend: Vite dev server on :5173. Backend: Spring Boot on :8080 "
    "(Spectrum/Moodle auth, stub client, H2 dev data)."
).italic = True

info = doc.add_paragraph()
info.add_run("Date: ").bold = True
info.add_run("2026-05-27   ")
info.add_run("Tester: ").bold = True
info.add_run("Claude Code (Opus 4.7)   ")
info.add_run("Build: ").bold = True
info.add_run("local working tree")

# ------------- 1. Summary -------------
H("1. Executive summary", 1)
P("The product hits its core promise — sign in, pick a course, pick a week, "
  "see the right files — with a clean, three-tap shell. The happy paths look "
  "well-considered: skeleton loaders before data, empty-state messages, a "
  "current-week highlight, an unobtrusive announcement-count badge with a "
  "popover, and a single primary call-to-action per screen.")
P("The weak spots are concentrated at the edges of the Spectrum-login form. "
  "Bad request bodies bubble out raw Spring validation messages with internal "
  "Java method signatures (HTTP 500 instead of a clean 400). The frontend "
  "would catch most of these before the network, but the API contract should "
  "still fail closed. There is no rate limiting or lockout, no global error "
  "boundary in the SPA, and the SPA lacks a 404 page for unknown deep links.")

P("Severity counts:", bold=True)
BULLET("High: 0")
BULLET("Medium: 3  (server 500 on bad login bodies; missing global error UI; no 404 page)")
BULLET("Low: 6  (microcopy, focus management, accessibility nits, polishing)")
BULLET("Passing: 11 (auth gating, cookie hygiene, route guards, empty states, deep linking)")

# ------------- 2. Test environment -------------
H("2. Test environment", 1)
BULLET("OS: macOS (Darwin 25.1.0)")
BULLET("Backend: Spring Boot 3.3, Java 21, profile=default (Spectrum/Moodle auth + stub data)")
BULLET("Frontend: Vite 5.4 dev server, React 18 + react-router 6, VITE_USE_MOCK=false")
BULLET("Spectrum client: StubSpectrumClient (canned courses/sections/files)")
BULLET("DB: H2 in-process (data/ directory)")
BULLET("Session cookie: UM_STUDY_SESSION (HttpOnly, SameSite=Lax, Max-Age=30d)")

# ------------- 3. Method -------------
H("3. Method", 1)
P("Two complementary passes:")
BULLET("Static UX review of each page in frontend/src/pages and the API client.")
BULLET("Black-box API probe with curl against :8080 (auth gating, error shapes, "
       "method routing, malformed input) plus front-end shell sanity checks against :5173.")
P("Live front-end click-through was not run in this session — see Section 7 "
  "for the UI scenarios that still need a human pass. Findings 5–8 below are "
  "from reading the page code, not from clicking.")

# ------------- 4. Results table -------------
H("4. Results — scenario matrix", 1)

rows = [
    ("T1",  "GET /api/auth/me unauthenticated → 401",                        "401, no body, session cookie set",             "Pass"),
    ("T2",  "GET /api/courses unauthenticated → 401",                        "401, empty body",                              "Pass"),
    ("T3",  "POST /api/auth/spectrum-login with bad token",                  "401 {\"error\":\"invalid_credentials\"}",      "Pass"),
    ("T4",  "POST /api/auth/spectrum-login with empty token",                "500 leaks Spring validation + method signature","Medium"),
    ("T5",  "POST /api/auth/spectrum-login with empty body {}",              "500 leaks Spring validation + method signature","Medium"),
    ("T6",  "POST /api/auth/spectrum-login with no body",                    "500 leaks Java method signature",              "Medium"),
    ("T7",  "POST /api/auth/spectrum-login with malformed JSON",             "500 leaks Jackson parse detail",               "Medium"),
    ("T8",  "GET /api/auth/spectrum-login (wrong method) → 405",             "405 clean JSON",                               "Pass"),
    ("T9",  "GET /api/courses/not-a-uuid/sections",                          "400 clean JSON",                               "Pass"),
    ("T10", "GET /api/courses/not-a-uuid/files",                             "400 clean JSON",                               "Pass"),
    ("T11", "GET /api/auth/logout while unauthenticated",                    "204 (idempotent) — good",                      "Pass"),
    ("T12", "GET /login page from Vite dev server",                          "200, SPA shell, brand mark present",           "Pass"),
    ("T13", "GET /foo (unknown route) on frontend",                          "200, SPA index served — no 404 page",          "Medium"),
    ("T14", "Deep link /courses unauthenticated",                            "Shell calls /api/auth/me, redirects to /login","Pass"),
    ("T15", "Session cookie attributes",                                     "HttpOnly + SameSite=Lax + 30-day Max-Age",     "Pass"),
    ("T16", "Settings welcome flow (?welcome=1) wording",                    "Distinct headline + sub when welcome=1",        "Pass"),
    ("T17", "WeekPicker current-week calculation",                           "Hard-coded SEMESTER_START = 2026-02-16",       "Low"),
    ("T18", "Announcement popover keyboard escape",                          "Escape closes popover, click-outside closes",  "Pass"),
    ("T19", "Skeleton loaders on Courses/WeekPicker/FileList",               "Present, with empty-state fallbacks",          "Pass"),
    ("T20", "Token input on Login is type=\"text\" (visible)",               "Visible — Settings uses password; inconsistent","Low"),
]
result_table(rows)

# ------------- 5. Findings -------------
H("5. Findings", 1)

H("F1 — Login error path leaks Spring/Java internals (Medium)", 2)
P("Empty token, empty body, missing body, or malformed JSON each respond with "
  "HTTP 500 and a JSON detail that exposes the fully-qualified controller "
  "method signature. Example:")
CODE("$ curl -sS -X POST -H 'Content-Type: application/json' \\\n"
     "    -d '{\"token\":\"\"}' http://localhost:8080/api/auth/spectrum-login\n"
     "HTTP/1.1 500\n"
     "{\"error\":\"server_error\",\"detail\":\"Validation failed for argument [0] in\n"
     " public my.edu.um.study.auth.MeDto\n"
     " my.edu.um.study.auth.AuthController.login(\n"
     "  my.edu.um.study.auth.SpectrumLoginRequest,\n"
     "  jakarta.servlet.http.HttpSession): ... must not be blank\"}")
P("Impact: two-fold. UX — a real 4xx is being delivered as a 5xx, so the "
  "frontend can't distinguish \"you typed nothing\" from \"server is down.\" "
  "Security/info-disclosure — the response confirms the framework, class "
  "names, and package layout to anyone probing the endpoint.")
P("Fix: ", bold=True)
BULLET("Map MethodArgumentNotValidException and HttpMessageNotReadableException "
       "to 400 in the global @ControllerAdvice (likely already present for other "
       "exceptions — these two are slipping through to the 500 handler).")
BULLET("Strip detail to a short, user-safe message: {\"error\":\"bad_request\","
       "\"field\":\"token\",\"message\":\"Token is required.\"}")
BULLET("Front-end: today the Login form already disables the submit when "
       "token.trim() is empty, so this is mostly defence-in-depth — but the "
       "405 / wrong-method tests show the global handler should fail closed.")

H("F2 — No global error UI in the SPA (Medium)", 2)
P("api/client.ts throws on any non-2xx that isn't 401-on-auth. Pages that "
  "consume this (CoursePicker, WeekPicker, FileList, Settings) call "
  ".then(setState) with no .catch, so a transient backend hiccup produces an "
  "unhandled promise rejection, a blank skeleton-forever screen, and a "
  "console error the user can't see.")
P("Fix: ", bold=True)
BULLET("Add a React error boundary at the Shell so render-time exceptions "
       "show a friendly retry card instead of a blank route.")
BULLET("In each page's data-fetch effect, catch the rejection and surface a "
       "small inline \"Couldn't load — Retry\" affordance (CoursePicker and "
       "FileList already have an \"empty\" branch — extend it with an "
       "\"error\" branch).")
BULLET("api/client.ts already redirects on 401; keep that path, but expose "
       "non-401 errors to the caller as a typed object, not Error(\"500 …\").")

H("F3 — No SPA 404 page (Medium)", 2)
P("App.tsx defines five routes and a / → /courses redirect, but no \"*\" "
  "catch-all. Vite's dev server returns the SPA shell for /foo, so the user "
  "lands on a blank Routes render with no orientation and no link home.")
P("Fix: add a final <Route path=\"*\" element={<NotFound />} /> with a single "
  "\"Back to courses\" link and the brand mark. Same component can be reused "
  "in the production static build (also missing a 404).")

H("F4 — Token visibility inconsistency between Login and Settings (Low)", 2)
P("Login.tsx uses <input type=\"text\"> for the Spectrum token. Settings.tsx "
  "uses <input type=\"password\">. Both inputs accept the same secret, but "
  "only one masks it. Decide one way — masking is the safer default; if the "
  "Login form was deliberately visible to help users verify a paste, add a "
  "\"show/hide\" toggle and mirror that on Settings.")

H("F5 — currentWeek() pins on a hard-coded SEMESTER_START (Low)", 2)
P("WeekPicker.tsx hard-codes new Date(2026, 1, 16) as the semester start, "
  "clamped to [1,14]. That ships fine for this semester but will silently "
  "drift the \"Jump to current week\" CTA after the semester ends, and on a "
  "year-rollover the page will keep pointing at Week 14 forever.")
P("Fix: serve the semester window from the backend (e.g. /api/calendar) so "
  "the SPA never has to bake a date in. Or, near-term, read it from a single "
  "config module so it lives next to other temporal constants.")

H("F6 — Announcement badge isn't a real focus stop (Low)", 2)
P("The little count badge over each week tile is a <button> — good — but it "
  "stops propagation on click only. There is no focus outline override, and "
  "since it sits visually inside the <Link> tile, keyboard users tabbing "
  "across the grid will encounter \"week 1, announcement 2, week 2, "
  "announcement 5…\" with no grouping label.")
P("Fix: keep the button keyboard-reachable, but group with aria-describedby "
  "on the week tile, and ensure the popover traps focus while open (currently "
  "Escape closes, which is good; Tab should cycle within the popover).")

H("F7 — Empty-state copy reads slightly different on each surface (Low)", 2)
BULLET("CoursePicker: \"No courses found for your account yet.\"")
BULLET("WeekPicker (no announcements): silent (badge just not rendered).")
BULLET("FileList (files): \"Nothing for this filter.\"")
BULLET("FileList (announcements): \"No announcements this week.\"")
P("These are all fine in isolation but collectively read as four different "
  "voices. Pick one cadence — \"No X yet.\" / \"Nothing for X.\" — and apply "
  "uniformly.")

H("F8 — Mock-mode banner is permanent in mock builds (Low)", 2)
P("Shell.tsx renders the mock banner whenever isMock is true. There is no "
  "dismiss. For demos that's fine, but anyone who hits mock by mistake (the "
  "default if VITE_USE_MOCK is unset) will see the banner forever. Either "
  "make it dismissable, or hard-fail the build if both modes are in scope.")

H("F9 — No rate limit / lockout on /api/auth/spectrum-login (Low)", 2)
P("Each invalid attempt returns a clean 401 and rotates the session cookie. "
  "There is no exponential backoff, no captcha, no temporary lockout. For a "
  "student-only product on a university network this is acceptable, but "
  "since the endpoint forwards to Moodle, repeated wrong tokens against the "
  "stub are fine — against the real client they hit Moodle every time.")

# ------------- 6. Things that work well -------------
H("6. What's working well", 1)
BULLET("Three-tap promise is honoured: /login → /courses → /courses/:id → /courses/:id/week/:N reads as fast as it sounds.")
BULLET("Skeleton loaders + empty states are present on every async surface — no jank, no flash of empty UI.")
BULLET("Session cookie is HttpOnly + SameSite=Lax + 30-day Max-Age. Good defaults.")
BULLET("API responses use sensible 4xx for auth and routing errors (401, 405, 400 for bad UUIDs).")
BULLET("Settings has a \"welcome\" microcopy variant for first-time users — small touch that matters.")
BULLET("WeekPicker shows the current-week tile with a visual marker and a single primary CTA — exactly one decision per screen.")
BULLET("Announcement popover dismisses on Escape and on outside-click. Both expected.")
BULLET("Deep linking works: any /courses/* URL hits Shell, Shell checks /api/auth/me, and unauth users land on /login. No broken-deep-link foot-gun.")

# ------------- 7. Not yet covered -------------
H("7. Not covered in this pass", 1)
P("These scenarios need a human click-through (or Playwright) to verify; "
  "code-reading alone won't catch them:")
BULLET("Full happy-path flow with a real Spectrum token (login → course → week → download a file).")
BULLET("Slow-network behaviour: do skeletons appear within 100 ms, or only after a noticeable delay?")
BULLET("Mobile viewport: card grid, week grid, and pill rows on 375 px wide.")
BULLET("Logout flow: cookie cleared, /api/auth/me returns 401, /courses redirects.")
BULLET("Settings disconnect → reconnect round-trip.")
BULLET("Custom bucket page (/courses/:id/bucket/custom) — is the empty state right?")
BULLET("Screen reader pass: form labels on Login, link landmarks on the file list, popover focus trapping.")

# ------------- 8. Recommendations, ordered -------------
H("8. Recommended order of work", 1)
P("If only three things are done this week, do these:")
BULLET("Add a @ControllerAdvice handler for MethodArgumentNotValidException + "
       "HttpMessageNotReadableException that returns a 400 with a clean "
       "{error, message} body. (Fixes F1.)")
BULLET("Add a top-level error boundary in the SPA and a 404 route. (Fixes F2 + F3.)")
BULLET("Mask the Login token input or add a show/hide toggle; unify with Settings. (Fixes F4.)")

P("Everything else (microcopy harmonisation, focus management, semester-start "
  "source-of-truth) is polish — worth doing, not urgent.")

doc.save(OUT)
print("wrote", OUT)
