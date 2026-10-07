# API reference

All endpoints are served by the Spring Boot backend under `/api`. Everything except sign-in requires the session cookie (`UM_STUDY_SESSION`, HttpOnly, SameSite=Lax).

| Method | Path | Returns |
|---|---|---|
| `POST` | `/api/auth/spectrum-login` | Signs in with `{ "token": "<spectrum token>" }`. 401 for an invalid token, 403 outside `@um.edu.my` |
| `GET` | `/api/auth/me` | Current user, or 401 |
| `GET` | `/api/auth/logout` | Clears the session (204) |
| `GET` | `/api/calendar` | Semester start, break week, current week |
| `GET` | `/api/courses` | The student's enrolled courses |
| `GET` | `/api/courses/{id}/sections` | Sections, each classified into a `week` or a `bucket` (project, past year…) |
| `GET` | `/api/courses/{id}/files?week=N&bucket=B&type=T` | Files filtered by week or bucket, and optionally by type |
| `GET` | `/api/courses/{id}/announcements?week=N` | Announcements as text, with images and links |
| `GET` | `/api/courses/{id}/submissions` | Assignments and quizzes with due dates and status |
