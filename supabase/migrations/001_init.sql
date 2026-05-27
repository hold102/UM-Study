-- UM Study System — initial schema
-- Supabase Postgres. Run via `supabase db push` or the SQL editor.

create extension if not exists "pgcrypto";

create table if not exists app_user (
    id                  uuid primary key default gen_random_uuid(),
    email               text not null unique,
    display_name        text,
    created_at          timestamptz not null default now(),
    last_login_at       timestamptz,
    spectrum_token_enc  text,
    spectrum_user_id    bigint,
    constraint email_is_um check (email like '%@um.edu.my')
);

create table if not exists course (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null references app_user(id) on delete cascade,
    spectrum_id     text not null,
    code            text not null,
    name            text not null,
    created_at      timestamptz not null default now(),
    unique (user_id, spectrum_id)
);

create index if not exists course_user_idx on course(user_id);

-- A section is a Spectrum content bucket (e.g. "Lecture Week 3", "Project").
-- week is null for non-week sections (project / past year / assignment / etc).
create table if not exists section (
    id              uuid primary key default gen_random_uuid(),
    course_id       uuid not null references course(id) on delete cascade,
    spectrum_id     text not null,
    title           text not null,
    week            int check (week between 1 and 14),
    bucket          text,
    confidence      numeric(3,2),
    unique (course_id, spectrum_id)
);

create index if not exists section_course_week_idx on section(course_id, week);

-- Per-course override map: raw section title -> normalized week or bucket.
-- Phase-1 mapping lives here; phase-2 classifier writes here too.
create table if not exists section_mapping (
    id              uuid primary key default gen_random_uuid(),
    course_id       uuid not null references course(id) on delete cascade,
    raw_title       text not null,
    week            int check (week between 1 and 14),
    bucket          text,
    source          text not null default 'manual',
    updated_at      timestamptz not null default now(),
    unique (course_id, raw_title)
);

create table if not exists file_item (
    id              uuid primary key default gen_random_uuid(),
    section_id      uuid not null references section(id) on delete cascade,
    spectrum_id     text not null,
    name            text not null,
    file_type       text,
    download_url    text not null,
    size_bytes      bigint,
    uploaded_at     timestamptz,
    week_override   int check (week_override between 1 and 14),
    unique (section_id, spectrum_id)
);

create index if not exists file_section_idx on file_item(section_id);

-- Announcements are text posts from the lecturer, not files. They live at the
-- course level (Spectrum doesn't always tie them to a section), with an
-- optional week derived by the classifier so they show up in the right week's filter.
create table if not exists announcement (
    id              uuid primary key default gen_random_uuid(),
    course_id       uuid not null references course(id) on delete cascade,
    spectrum_id     text not null,
    title           text not null,
    body            text not null,
    author          text,
    posted_at       timestamptz not null default now(),
    week            int check (week between 1 and 14),
    unique (course_id, spectrum_id)
);

create index if not exists announcement_course_week_idx on announcement(course_id, week, posted_at desc);
