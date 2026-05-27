# UM Study System

A week-aware companion to UM Spectrum. From login to the right file in three taps:
**sign in → pick course → pick week → done.**

See `UM_Study_System_DevDoc.pdf` for the full spec and `UM_Study_Architecture.docx` for a plain-English tour of how it's built.

## Architecture

```
frontend/   React + Vite + TypeScript           →  http://localhost:5173
backend/    Spring Boot 3.3 (Java 21+)          →  http://localhost:8080
supabase/   Postgres schema + migrations
```

The frontend dev server proxies `/api`, `/oauth2`, and `/login/oauth2` to the
backend so the OAuth flow and the session cookie work on a single origin.

## Quick start (no setup)

The backend has a **dev profile** that uses an in-memory H2 database and a
fake-login endpoint, so you can run the whole stack with no Supabase project
and no Google OAuth credentials.

```bash
# Terminal 1 — backend
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 2 — frontend (talking to real backend, not mock data)
cd frontend
npm install
VITE_USE_MOCK=false npm run dev
```

Open <http://localhost:5173> and click "Continue with Google". In dev profile
this is aliased to `/api/dev/login` which signs you in as `tan.liang@um.edu.my`.

The frontend also has a **mock mode** (the default, no backend needed):

```bash
cd frontend && npm install && npm run dev
```

## Production setup

### 1. Supabase

Create a project, then run the SQL in `supabase/migrations/001_init.sql`
(SQL editor, or `supabase db push` if using the CLI).

### 2. Backend

```bash
cd backend
export SPRING_PROFILES_ACTIVE=prod
export SUPABASE_DB_URL='jdbc:postgresql://<host>:5432/postgres'
export SUPABASE_DB_USER='postgres'
export SUPABASE_DB_PASSWORD='...'
export MICROSOFT_CLIENT_ID='...'
export MICROSOFT_CLIENT_SECRET='...'
export MICROSOFT_TENANT_ID='...'   # UM's Azure AD tenant id; or "common" for any
mvn spring-boot:run
```

In Azure portal → App registrations → New registration:
- **Supported account types**: "Accounts in this organizational directory only" (single-tenant, UM)
- **Redirect URI**: `Web` → `http://localhost:8080/login/oauth2/code/microsoft`
- After creation, get the Application (client) ID and Directory (tenant) ID
- Certificates & secrets → New client secret → copy the *Value*

### 3. Frontend

```bash
cd frontend
VITE_USE_MOCK=false npm run build
```

## Connecting to the real Spectrum

The backend ships with two `SpectrumClient` implementations, picked at startup:

| `SPECTRUM_CLIENT` | What runs | Use for |
|---|---|---|
| `stub` (default) | `StubSpectrumClient` returning canned data | local dev, demos |
| `moodle` | `MoodleSpectrumClient` hitting `/webservice/rest/server.php` | production (Spectrum is Moodle) |

### How a student connects their account

1. They open Spectrum and go to **Profile → Preferences → Security keys**.
2. They create a key for the *Moodle mobile web service* (this is the one with
   `core_enrol_get_users_courses` etc.) and copy the token.
3. In the app: **Settings → paste the token → Save**. The backend AES-GCM-encrypts
   it with `APP_SECRET_KEY` before storing it on `app_user.spectrum_token_enc`.
4. From then on the backend calls Spectrum on the student's behalf, passing
   `wstoken=…` in each request.

### Switching the backend to Moodle mode

```bash
export SPECTRUM_CLIENT=moodle
export SPECTRUM_BASE_URL=https://spectrum.um.edu.my
export APP_SECRET_KEY="$(openssl rand -base64 32)"   # required in prod
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### If Spectrum is not Moodle

The `SpectrumClient` interface in `backend/.../spectrum/SpectrumClient.java` is
the seam. Write a new `@Component` implementing it (e.g. `OpenLmsSpectrumClient`,
`BlackboardSpectrumClient`, or an HTML-scraping `JsoupSpectrumClient`), annotate
it with `@ConditionalOnProperty(name = "app.spectrum.client", havingValue = "yourname")`,
and toggle via `SPECTRUM_CLIENT=yourname`.

## API

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/auth/me` | Current user or 401 |
| `GET` | `/api/auth/logout` | Clears session |
| `GET` | `/oauth2/authorization/google` | Starts Google OAuth (prod) / dev login (dev) |
| `GET` | `/api/dev/login?email=...&name=...` | Dev only — fake sign-in |
| `GET` | `/api/courses` | Student's courses (sync from Spectrum stub) |
| `GET` | `/api/courses/{id}/sections` | Sections, with classified `week`/`bucket` |
| `GET` | `/api/courses/{id}/files?week=N&type=slides` | Filtered files |
| `GET` | `/api/courses/{id}/announcements?week=N` | Text announcements |
| `GET`/`PUT` | `/api/courses/{id}/mappings` | Per-course section overrides |
| `GET`/`PUT`/`DELETE` | `/api/me/spectrum-token` | Personal Spectrum token (encrypted at rest) |

## Build plan (from the dev doc)

- **M1** — Auth shell. ✅ Google login, `@um.edu.my` check, session cookie. Plus dev login.
- **M2** — Spectrum integration. Interface in place, `StubSpectrumClient` returning canned data. Real client TBD.
- **M3** — Course picker UI. ✅
- **M4** — Manual section mapping + week picker. ✅
- **M5** — Filtered file list (slides/announcement/tutorial). ✅ Announcements rendered as text, not files.
- **M6** — Polish + caching. Partly done (DB acts as cache, skeletons + empty states in UI).
- **M7** — Autonomous section scanning. `WeekClassifier` is in place; needs a scheduled job.
