import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import { Shell } from "../components/Shell";

export default function Settings() {
  const [linked, setLinked] = useState<boolean | null>(null);
  const [token, setToken] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [params] = useSearchParams();
  const welcome = params.get("welcome") === "1";

  useEffect(() => { api.tokenStatus().then(s => setLinked(s.linked)); }, []);

  async function save() {
    setBusy(true); setError(null);
    try {
      const s = await api.saveToken(token);
      setLinked(s.linked);
      setToken("");
    } catch (e) {
      setError("Could not save token. Check it's correct and try again.");
    } finally { setBusy(false); }
  }

  async function clear() {
    setBusy(true); setError(null);
    try { const s = await api.clearToken(); setLinked(s.linked); }
    finally { setBusy(false); }
  }

  return (
    <Shell>
      <main className="page">
        <Link to="/courses" className="back-link">← Courses</Link>
        <h1 className="page-title">
          {welcome ? "Almost there" : "Settings"}
        </h1>
        <p className="page-sub">
          {welcome
            ? "You're signed in. One last step: connect your Spectrum account so we can read your courses."
            : "Connect your Spectrum account so we can read your courses."}
        </p>

        <section className="card" style={{ display: "block" }}>
          <h2 style={{ fontFamily: "var(--font-display)", fontSize: 17, fontWeight: 500, margin: "0 0 8px" }}>
            Spectrum personal token
          </h2>
          <p className="file-meta" style={{ marginBottom: 16 }}>
            In Spectrum: <strong>Profile → Preferences → Security keys</strong>. Create a
            key for the <em>Moodle mobile web service</em> and paste it here. We store it
            encrypted; you can revoke it any time on Spectrum.
          </p>

          {linked === null ? null : linked ? (
            <div>
              <p style={{ color: "var(--accent)", marginBottom: 16 }}>
                ✓ Token is linked. You're all set.
              </p>
              <button className="btn-ghost" onClick={clear} disabled={busy}>
                Disconnect
              </button>
            </div>
          ) : (
            <div>
              <input
                type="password"
                value={token}
                onChange={e => setToken(e.target.value)}
                placeholder="paste your Spectrum token"
                style={{
                  width: "100%", padding: "12px 14px", marginBottom: 14,
                  border: "1px solid var(--line)", borderRadius: 10,
                  background: "var(--bg)", fontFamily: "monospace", fontSize: 13,
                }}
              />
              <button className="btn-primary" onClick={save} disabled={busy || !token.trim()}>
                {busy ? "Saving…" : "Save token"}
              </button>
              {error && <div style={{ marginTop: 14, color: "var(--accent)", fontSize: 13 }}>{error}</div>}
            </div>
          )}
        </section>
      </main>
    </Shell>
  );
}
