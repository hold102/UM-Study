# Connecting to Spectrum

Spectrum is Moodle, so the backend talks to it through the Moodle web service (`/webservice/rest/server.php`). There is no scraping and no password handling: each student signs in with their own mobile-service token.

## How a student signs in

1. Open Spectrum and go to **Profile → Preferences → Security keys**.
2. Copy the key for **Moodle mobile web service**.
3. Paste it on the UM Study sign-in screen.

The backend checks the token with `core_webservice_get_site_info`, rejects accounts outside `@um.edu.my`, then encrypts the token (AES-GCM, keyed from `APP_SECRET_KEY`) before storing it. Every later request to Spectrum passes it as `wstoken`.

## Moodle functions used

| Function | Used for |
|---|---|
| `core_webservice_get_site_info` | Validating the token and identifying the student |
| `core_enrol_get_users_courses` | Course list |
| `core_course_get_contents` | Sections and files, classified into weeks by `WeekClassifier` |
| `mod_forum_get_forum_discussions` | Announcements |
| `mod_assign_get_assignments`, `mod_assign_get_submission_status` | Assignments and their status |
| `mod_quiz_get_quizzes_by_courses`, `mod_quiz_get_user_attempts` | Quizzes and attempts |

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `SPECTRUM_BASE_URL` | `https://spectrum.um.edu.my` | |
| `APP_SECRET_KEY` | dev-only value | **Required in production.** Generate with `openssl rand -base64 32` |
| `SEMESTER_START_DATE` | `2026-03-09` | Monday of Week 1 |
| `SEMESTER_BREAK_START_DATE` | `2026-04-27` | Monday of the mid-semester break (that week is skipped) |

## Adapting to another LMS

`backend/src/main/java/my/edu/um/study/spectrum/SpectrumClient.java` is the seam. Implement it for a different LMS, register it as the active `@Component`, and the rest of the app (week classification, filtering, UI) works unchanged.
