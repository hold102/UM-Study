"""Generate Spectrum_Login_Authorization.docx describing how a UM Spectrum
credential becomes an authorized session in the UM Study System."""
from docx import Document
from docx.shared import Pt, Inches, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH

OUT = "/Users/LYYY/Downloads/Spectrum Info Extractor/Spectrum_Login_Authorization.docx"

doc = Document()

styles = doc.styles
styles["Normal"].font.name = "Calibri"
styles["Normal"].font.size = Pt(11)


def H(text, level=1):
    h = doc.add_heading(text, level=level)
    for run in h.runs:
        run.font.color.rgb = RGBColor(0x10, 0x10, 0x10)


def P(text, bold=False, italic=False, mono=False):
    p = doc.add_paragraph()
    r = p.add_run(text)
    r.bold = bold
    r.italic = italic
    if mono:
        r.font.name = "Menlo"
        r.font.size = Pt(10)
    return p


def CODE(text):
    p = doc.add_paragraph()
    r = p.add_run(text)
    r.font.name = "Menlo"
    r.font.size = Pt(10)
    r.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
    return p


def BULLET(text, mono_first=False):
    p = doc.add_paragraph(style="List Bullet")
    if mono_first and ":" in text:
        head, tail = text.split(":", 1)
        r = p.add_run(head + ":")
        r.font.name = "Menlo"
        r.font.size = Pt(10)
        r.bold = True
        p.add_run(tail)
    else:
        p.add_run(text)


# Title
title = doc.add_heading("UM Study — Spectrum Login Authorization", level=0)
title.alignment = WD_ALIGN_PARAGRAPH.LEFT

meta = doc.add_paragraph()
meta.add_run("How a Spectrum (UM Moodle) username and password becomes an "
             "authorized session that can read the user’s courses.").italic = True

# 1. Overview
H("1. Overview", 1)
P("The app uses Spectrum (which is Moodle under the hood) as the single source "
  "of truth for identity. There is no separate account system: anyone who can "
  "log in to Spectrum can log in to this app. The backend exchanges the user’s "
  "username + password for a Moodle Web Service token (wstoken), persists an "
  "encrypted copy of that token against an app_user row, and from then on "
  "calls Moodle on the user’s behalf for course/section/file data.")

P("Authorization is implemented with a server-side session cookie. After a "
  "successful login the backend writes the app_user.id into HttpSession under "
  "the attribute name 'userId'. A custom servlet filter "
  "(SessionAuthFilter) promotes that attribute into a Spring Security "
  "Authentication so the standard authorizeHttpRequests() rules can guard "
  "the rest of the /api/** surface.")

# 2. Actors
H("2. Actors and ports", 1)
BULLET("Browser: runs the React SPA at http://localhost:5173/.")
BULLET("Vite dev server (port 5173): serves the SPA bundle. Proxies /api, "
       "/oauth2, /login/oauth2 to the backend so cookies stay first-party.")
BULLET("Spring Boot backend (port 8080): owns the session cookie, calls "
       "Moodle, persists the user row.")
BULLET("UM Spectrum / Moodle (https://spectrum.um.edu.my): the real "
       "identity provider and data store.")
BULLET("H2 (dev) or Supabase Postgres (prod): holds the app_user row.")

# 3. Sequence
H("3. The full login sequence", 1)
P("Step by step, from the user pressing “Sign in” to landing on /courses:", italic=True)

CODE(
    "Browser                Frontend           Backend                Moodle\n"
    "   |                       |                  |                     |\n"
    "   |  type creds, click    |                  |                     |\n"
    "   |---------------------> |                  |                     |\n"
    "   |                       | POST /api/auth/  |                     |\n"
    "   |                       | spectrum-login   |                     |\n"
    "   |                       | {username,pwd}   |                     |\n"
    "   |                       |----------------->|                     |\n"
    "   |                       |                  | POST /login/        |\n"
    "   |                       |                  | token.php           |\n"
    "   |                       |                  | service=            |\n"
    "   |                       |                  | moodle_mobile_app   |\n"
    "   |                       |                  |-------------------->|\n"
    "   |                       |                  | { token, ... }      |\n"
    "   |                       |                  |<--------------------|\n"
    "   |                       |                  | GET core_webservice |\n"
    "   |                       |                  | _get_site_info      |\n"
    "   |                       |                  | wstoken=...         |\n"
    "   |                       |                  |-------------------->|\n"
    "   |                       |                  | { userid, useremail,|\n"
    "   |                       |                  |   fullname, ... }   |\n"
    "   |                       |                  |<--------------------|\n"
    "   |                       |                  | domain check        |\n"
    "   |                       |                  | (*.um.edu.my)       |\n"
    "   |                       |                  | upsert app_user,    |\n"
    "   |                       |                  | encrypt wstoken,    |\n"
    "   |                       |                  | session.userId=...  |\n"
    "   |                       | 200 { id, email, |                     |\n"
    "   |                       |   displayName }  |                     |\n"
    "   |                       | Set-Cookie: UM_  |                     |\n"
    "   |                       |  STUDY_SESSION   |                     |\n"
    "   |                       |<-----------------|                     |\n"
    "   |  navigate /courses    |                  |                     |\n"
    "   |<----------------------|                  |                     |\n"
    "   |  GET /api/courses     |                  |                     |\n"
    "   |  (cookie attached)    |                  |                     |\n"
    "   |---------------------->|----------------->|                     |\n"
    "   |                       |                  | SessionAuthFilter   |\n"
    "   |                       |                  | promotes userId ->  |\n"
    "   |                       |                  | SecurityContext     |\n"
    "   |                       |                  | decrypt wstoken     |\n"
    "   |                       |                  | core_enrol_get_     |\n"
    "   |                       |                  | users_courses       |\n"
    "   |                       |                  |-------------------->|\n"
    "   |                       |                  | [ {id, shortname,...|\n"
    "   |                       |                  |<--------------------|\n"
    "   |                       |  200 [ Course ]  |                     |\n"
    "   |                       |<-----------------|                     |\n"
)

# 4. Frontend
H("4. Frontend — what the form does", 1)
P("frontend/src/pages/Login.tsx renders a username + password form. On submit "
  "it calls api.spectrumLogin(...) from frontend/src/api/client.ts, which "
  "wraps fetch('/api/auth/spectrum-login', { method: 'POST', credentials: "
  "'include', body: JSON.stringify({username,password}) }).")
P("Important behaviors:", italic=True)
BULLET("credentials: 'include' is what makes the browser send/receive the "
       "session cookie cross-origin (Vite dev → Spring Boot).")
BULLET("On 401 to /api/* the client used to globally redirect to /login. "
       "That created a render race when the failing endpoint was the login "
       "itself: the page would silently reload instead of showing 'Wrong "
       "username or password.' The current client.ts skips the redirect when "
       "path starts with /api/auth/, so the login form can display the error.")
BULLET("USE_MOCK now defaults to false. Set VITE_USE_MOCK=true to bypass the "
       "backend with the mock api defined in src/api/mock.ts.")

# 5. Backend
H("5. Backend — the authorization machinery", 1)
P("All code is under backend/src/main/java/my/edu/um/study/.")

H("5.1 AuthController", 2)
CODE("POST /api/auth/spectrum-login   {username, password}\n"
     "GET  /api/auth/logout\n"
     "auth/AuthController.java")
P("The endpoint is whitelisted in SecurityConfig (permitAll for POST "
  "/api/auth/spectrum-login). The handler:")
BULLET("Calls SpectrumAuthenticator.authenticate(username, password) — "
       "throws InvalidSpectrumCredentialsException → 401 if Moodle refuses.")
BULLET("Checks the returned email ends with .um.edu.my or @um.edu.my. "
       "Throws DomainNotAllowedException → 403 if not.")
BULLET("Calls UserService.upsertFromSpectrum(identity) which finds-or-creates "
       "the app_user row, updates display_name + spectrum_user_id, and "
       "stores TokenCipher.encrypt(wstoken) in spectrum_token_enc.")
BULLET("Writes user.id (UUID) into HttpSession under attribute 'userId'.")
BULLET("Returns 200 with { id, email, displayName }.")

H("5.2 SpectrumAuthenticator (Moodle)", 2)
CODE("spectrum/MoodleSpectrumAuthenticator.java")
P("Two HTTP calls against Spectrum:")
BULLET("POST {base}/login/token.php — form body "
       "username=…&password=…&service=moodle_mobile_app. Response is JSON: "
       "{ token, privatetoken } on success, { error, errorcode } on failure.")
BULLET("GET {base}/webservice/rest/server.php?wstoken=…&wsfunction="
       "core_webservice_get_site_info&moodlewsrestformat=json. Returns "
       "{ userid, username, fullname, useremail, … }.")
P("The base URL comes from app.spectrum.base-url (default "
  "https://spectrum.um.edu.my).")

H("5.3 TokenCipher (encrypt the wstoken at rest)", 2)
CODE("security/TokenCipher.java")
P("AES/GCM/NoPadding. The 256-bit key is SHA-256(app.secret-key). Each "
  "encryption generates a random 12-byte IV and concatenates iv || ciphertext"
  " || tag, then base64-encodes it. The plaintext wstoken is never written to "
  "disk — only the encrypted form lands in app_user.spectrum_token_enc. In "
  "dev the secret defaults to 'dev-only-do-not-use-in-prod-please'; in prod "
  "you must set APP_SECRET_KEY in the environment.")

H("5.4 Session and SessionAuthFilter", 2)
CODE("config/SessionAuthFilter.java\n"
     "config/SecurityConfig.java")
P("Session storage is Spring Session backed by JDBC (the SPRING_SESSION "
  "table is created automatically). The cookie is named UM_STUDY_SESSION, "
  "HttpOnly, SameSite=Lax, 30-day max-age (see application.yml → "
  "server.servlet.session).")
P("SessionAuthFilter runs once per request before the Spring Security filter "
  "chain. If HttpSession has attribute 'userId' it builds a "
  "PreAuthenticatedAuthenticationToken with that UUID as principal and sets "
  "it on the SecurityContext. That lets the authorizeHttpRequests() rule "
  "'.requestMatchers(\"/api/**\").authenticated()' protect everything else.")

H("5.5 What 'authorized' means at the controller", 2)
P("In any protected controller (e.g. CourseController), the request hits "
  "the controller only if the SessionAuthFilter found a userId. The "
  "controller reads userId out of HttpSession directly, loads the User, "
  "decrypts spectrum_token_enc with TokenCipher.decrypt, and calls Moodle "
  "with that wstoken. The session cookie itself never holds the token — "
  "only the database row does.")

# 6. The app_user row
H("6. The app_user table", 1)
P("Schema (supabase/migrations/001_init.sql):")
CODE("id                  uuid primary key default gen_random_uuid()\n"
     "email               text unique not null\n"
     "                    CHECK (email like '%@um.edu.my')\n"
     "display_name        text\n"
     "created_at          timestamptz default now()\n"
     "last_login_at       timestamptz\n"
     "spectrum_token_enc  text     -- base64(iv || ct || tag)\n"
     "spectrum_user_id    bigint   -- Moodle userid")
P("Heads-up: the CHECK constraint as written in 001_init.sql does NOT accept "
  "student emails of the form 24066631@siswa.um.edu.my. Before pointing the "
  "app at Supabase, relax the constraint to "
  "(email like '%.um.edu.my' or email like '%@um.edu.my'), or it will reject "
  "every student account.")

# 7. Failure modes
H("7. Status codes and what they mean", 1)
BULLET("200 OK + JSON Me + Set-Cookie — successful login. Cookie is the new "
       "session. Frontend navigates to /courses.")
BULLET("401 {\"error\":\"invalid_credentials\"} — Moodle rejected the "
       "username/password (errorcode is usually 'invalidlogin'). Frontend "
       "shows 'Wrong username or password.'")
BULLET("403 {\"error\":\"domain\"} — Moodle accepted the credentials, but "
       "the returned email does not end with .um.edu.my / @um.edu.my. "
       "Frontend shows 'Use your UM account.'")
BULLET("500 {\"error\":\"server_error\",\"detail\":\"…\"} — unhandled "
       "exception (e.g. Moodle is unreachable, a JSON shape changed, the DB "
       "is down). Backend logs the full stack trace under "
       "my.edu.um.study.auth.AuthController.")

# 8. Diagnostic checklist
H("8. When login does not work — diagnostic checklist", 1)
P("Run through these in order. Each one isolates a specific layer.")

H("8.1 Is the request even leaving the browser?", 2)
BULLET("Open DevTools → Network. Submit the form. You should see a row for "
       "POST /api/auth/spectrum-login.")
BULLET("If you do not: the form’s onSubmit is not firing. Likely causes are "
       "a JS error earlier on the page, the Sign-in button being disabled "
       "because username/password is empty, or the wrong tab being open.")

H("8.2 Is the request reaching the backend?", 2)
BULLET("In the backend log (backend.log) you should see "
       "'spectrum-login attempt: username=…'")
BULLET("If not: the request is hitting a different process. Common cause is "
       "a duplicate Vite dev server on another port (5173 vs 5174). Make "
       "sure only one Vite is running, and that the browser tab is pointing "
       "at the right port.")
BULLET("Confirm with: lsof -nP -iTCP:5173 -sTCP:LISTEN and "
       "lsof -nP -iTCP:8080 -sTCP:LISTEN.")

H("8.3 Did Moodle accept the credentials?", 2)
BULLET("If the next log line says 'spectrum-login invalid credentials: "
       "login.token.php failed: invalidlogin', Spectrum rejected the "
       "username/password. Verify by logging into spectrum.um.edu.my "
       "manually in another tab.")
BULLET("If errorcode is something else (e.g. 'enablewsdescription', "
       "'webservicesnotenabled'), the Moodle Web Service for the mobile app "
       "is disabled for this user. That is a Spectrum admin issue.")

H("8.4 Did the domain check pass?", 2)
BULLET("The backend logs the email it received: 'spectrum-login ok: "
       "username=…, spectrumUserId=…, email=…'.")
BULLET("If that email does not end with .um.edu.my, the controller throws "
       "DomainNotAllowedException and the frontend shows 'Use your UM "
       "account.' Adjust app.allowed-email-domain or the endsWith check.")

H("8.5 Did the upsert succeed?", 2)
BULLET("If you see a 500 with a stack trace referencing User / "
       "UserRepository, the app_user row failed to persist. In prod the "
       "most likely cause is the email CHECK constraint described in "
       "section 6.")
BULLET("In dev (H2) the table is created by Hibernate from the JPA entity, "
       "so the constraint does not apply. Inspect the H2 file at "
       "backend/data/umstudy with a JDBC tool if you suspect corruption.")

H("8.6 Was the session cookie set?", 2)
BULLET("DevTools → Application → Cookies → http://localhost:5173. Look for "
       "UM_STUDY_SESSION. Without it, every subsequent /api/courses request "
       "will be 401.")
BULLET("If the cookie is missing despite a 200 response, it is almost "
       "certainly a CORS / SameSite issue from a misconfigured proxy. "
       "Confirm the request went through Vite (which proxies to 8080) and "
       "not directly to 8080 from a 5173 origin.")

# 9. File map
H("9. File map (relative to project root)", 1)
BULLET("frontend/src/pages/Login.tsx — the form")
BULLET("frontend/src/api/client.ts — fetch wrapper and the per-call mock toggle")
BULLET("backend/src/main/java/my/edu/um/study/auth/AuthController.java — "
       "endpoint, domain check, exception handlers")
BULLET("backend/src/main/java/my/edu/um/study/spectrum/MoodleSpectrumAuthenticator.java — "
       "Moodle calls")
BULLET("backend/src/main/java/my/edu/um/study/spectrum/MoodleSpectrumClient.java — "
       "core_enrol_get_users_courses")
BULLET("backend/src/main/java/my/edu/um/study/user/UserService.java — upsert")
BULLET("backend/src/main/java/my/edu/um/study/user/User.java — JPA entity")
BULLET("backend/src/main/java/my/edu/um/study/security/TokenCipher.java — "
       "AES-GCM")
BULLET("backend/src/main/java/my/edu/um/study/config/SecurityConfig.java — "
       "permitAll list, 401 entry point, filter wiring")
BULLET("backend/src/main/java/my/edu/um/study/config/SessionAuthFilter.java — "
       "session → SecurityContext bridge")
BULLET("backend/src/main/java/my/edu/um/study/config/AppProperties.java — "
       "@ConfigurationProperties('app')")
BULLET("backend/src/main/resources/application.yml — profiles, session "
       "cookie, app props")
BULLET("supabase/migrations/001_init.sql — app_user schema and the "
       "@um.edu.my CHECK constraint")

# 10. Known gaps
H("10. Known gaps and TODOs", 1)
BULLET("The supabase email CHECK constraint rejects @siswa.um.edu.my. Must "
       "be relaxed before pointing at Supabase.")
BULLET("Logout endpoint invalidates the HttpSession but the frontend does "
       "not call it explicitly anywhere yet.")
BULLET("Token rotation: if Moodle invalidates the wstoken (e.g. password "
       "change on Spectrum), the next /api/courses call will fail with a "
       "Moodle 'invalidtoken' error which is not specifically mapped. The "
       "user will see a generic error; the cleanup is to clear "
       "spectrum_token_enc and force re-login.")
BULLET("No CSRF token. CSRF is disabled because every API call is JSON and "
       "SameSite=Lax on the cookie. Accept the risk or add a token if you "
       "ever expose forms to other origins.")

doc.save(OUT)
print("wrote", OUT)
