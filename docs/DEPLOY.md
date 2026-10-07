# Deploying UM Study

Target: **Netlify** or **Vercel** (frontend) + **Render** (backend) + **Supabase** (Postgres).

> The repo ships both `netlify.toml` (builds `frontend/` and proxies `/api/*` to Render) and `frontend/vercel.json`. The steps below use Vercel; on Netlify, import the repo and the config is picked up automatically.

This guide assumes you start from this local repo with no remotes.

---

## 0. One-time prep — push the code to GitHub

You need a GitHub remote because both Vercel and Render deploy from a git provider.

1. From this folder, initialize git and commit:

   ```bash
   cd UM-Study
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   ```

2. Create an empty repo on github.com (no README/license/.gitignore — repo must be empty).
3. Add it as your remote and push:

   ```bash
   git remote add origin https://github.com/<your-username>/<repo-name>.git
   git push -u origin main
   ```

---

## 1. Supabase — get the Postgres connection string

You already have a `supabase/` folder, so I assume the project exists. From the Supabase dashboard:

1. **Project Settings → Database → Connection string → JDBC**.
2. Copy the JDBC URL. It looks like:
   ```
   jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?user=postgres.<project-ref>&password=<password>
   ```
   - Use the **pooler** URL (port 6543) for serverless-friendly connections.
   - Keep this for the Render env var `SUPABASE_DB_URL`.
3. Run the migrations in `supabase/migrations/` (via the Supabase SQL Editor or the Supabase CLI) so the schema exists before the backend starts. The backend uses `ddl-auto: validate` in prod and will fail to start if the tables aren't there.

---

## 2. Render — deploy the backend

1. Sign in at <https://render.com> and click **New → Web Service**.
2. Connect your GitHub account; select the repo you just pushed.
3. Settings:
   - **Name**: e.g. `um-study-backend` (whatever you pick becomes `<name>.onrender.com`)
   - **Region**: pick the closest to Malaysia (Singapore if available)
   - **Branch**: `main`
   - **Root Directory**: `backend`
   - **Runtime**: **Docker** (Render auto-detects the `Dockerfile`)
   - **Instance Type**: Free
4. **Environment Variables** (Advanced):
   ```
   SPRING_PROFILES_ACTIVE   prod
   FRONTEND_BASE_URL        https://<your-vercel-project>.vercel.app
   SUPABASE_DB_URL          <jdbc URL from step 1>
   SUPABASE_DB_USER         postgres.<project-ref>
   SUPABASE_DB_PASSWORD     <password>
   APP_SECRET_KEY           <a long random string — generate one>
   SPECTRUM_CLIENT          moodle
   SPECTRUM_BASE_URL        https://spectrum.um.edu.my
   SEMESTER_START_DATE      2026-03-09
   SEMESTER_BREAK_START_DATE 2026-04-27
   ```
   - For `APP_SECRET_KEY`: `openssl rand -hex 32` produces a good value.
   - You'll only know `FRONTEND_BASE_URL` after step 3 — leave a placeholder and update once Vercel gives you a URL.
5. Click **Create Web Service**. The first build is ~4–6 minutes (Docker, Maven download, Spring Boot startup).
6. When it's live, note the URL: `https://um-study-backend.onrender.com` (or whatever you chose).
7. Test: `curl https://<your-render-url>/api/auth/me` should return `401` (unauthenticated — that's expected).

> **Free tier note**: Render spins down the service after 15 min of inactivity. The first request after sleep takes ~1 min to wake up.

---

## 3. Vercel — deploy the frontend

1. Open [vercel.json](frontend/vercel.json) and replace `REPLACE-ME-RENDER-SERVICE.onrender.com` with your actual Render URL host (no `https://`). Commit and push.
2. Sign in at <https://vercel.com> and click **Add New… → Project**.
3. Import the GitHub repo.
4. Settings:
   - **Framework Preset**: Vite (auto-detected)
   - **Root Directory**: `frontend`
   - **Build Command**: `npm run build` (default)
   - **Output Directory**: `dist` (default)
5. **Environment Variables**:
   ```
   VITE_USE_MOCK   false
   ```
6. Click **Deploy**. First build ~1 minute.
7. When it's live, note the URL: `https://<your-project>.vercel.app`.

---

## 4. Tie them together

1. Go back to Render → your service → **Environment**, and set `FRONTEND_BASE_URL` to the Vercel URL from step 3 (e.g. `https://um-study.vercel.app`).
2. Save — Render will redeploy automatically (~30s for env-only change).
3. Open the Vercel URL. Log in by pasting a Spectrum web service token; you should be redirected to the courses page.

---

## How it all fits together

```
Browser ──HTTPS──▶ <project>.vercel.app
                       │
                       ├─ /          → static React app (Vercel)
                       └─ /api/*     → rewrites to <service>.onrender.com/api/*  (via vercel.json)
                                                  │
                                                  └─ Spring Boot ─▶ Supabase Postgres
```

- Browser only ever talks to the Vercel domain → cookies set by Spring stay same-origin from the browser's view, so `SameSite=Lax` works fine.
- The frontend bundle does relative `fetch("/api/...")` calls; the rewrite handles routing to Render.
- `forward-headers-strategy: framework` tells Spring to trust `X-Forwarded-Proto` from Vercel/Render so URL building and Secure-cookie logic see HTTPS.

---

## Updating

After step 0, every `git push` to `main` triggers both Vercel and Render to redeploy automatically. No further commands needed.

---

## Troubleshooting

- **Render build fails on `mvn package`** → check the build log; usually a Java/Spring dependency download timeout. Re-deploy retries it.
- **`CORS error` in browser console** → `FRONTEND_BASE_URL` on Render doesn't exactly match the Vercel URL (no trailing slash; protocol must be `https://`).
- **`401` on every API call after login** → cookies aren't being sent. Confirm `vercel.json` rewrite points at the right Render host (you replaced the placeholder).
- **Session lost after Render wakes from sleep** → expected first request takes a minute; the frontend's existing 401 → `/login` redirect will fire and the user can re-paste their token.
- **`Schema validation: missing table …`** on backend boot → migrations haven't been applied to Supabase. Run them in the Supabase SQL Editor.
