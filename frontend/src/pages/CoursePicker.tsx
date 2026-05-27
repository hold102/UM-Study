import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, Course } from "../api/client";
import { Shell } from "../components/Shell";

export default function CoursePicker() {
  const [courses, setCourses] = useState<Course[] | null>(null);

  useEffect(() => { api.courses().then(setCourses); }, []);

  return (
    <Shell>
      <main className="page wide">
        <h1 className="page-title">Your courses</h1>
        <p className="page-sub">
          {courses && courses.length > 0
            ? `${courses.length} course${courses.length === 1 ? "" : "s"} this semester. Pick one to jump to this week's files.`
            : "Pick a course to jump to this week's files."}
        </p>

        {courses === null ? (
          <div className="skeleton-grid">
            {Array.from({ length: 6 }).map((_, i) => <div key={i} className="skeleton" />)}
          </div>
        ) : courses.length === 0 ? (
          <div className="empty">No courses found for your account yet.</div>
        ) : (
          <div className="grid">
            {courses.map(c => (
              <Link key={c.id} to={`/courses/${c.id}`} className="card">
                <div className="code">{c.code}</div>
                <div className="name">{c.name}</div>
              </Link>
            ))}
          </div>
        )}
      </main>
    </Shell>
  );
}
