import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";

export default function Login() {
  const nav = useNavigate();

  const [token, setToken] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null);
    try {
      console.log("[login] submitting token, len=", token.trim().length);
      const me = await api.spectrumLogin(token.trim());
      console.log("[login] success, me=", me);
      nav("/courses");
      console.log("[login] nav('/courses') called");
    } catch (err: any) {
      console.error("[login] failed:", err);
      const msg = String(err?.message ?? "");
      if (msg.startsWith("401")) setError("Invalid Spectrum token.");
      else if (msg.startsWith("403")) setError("Use your UM account.");
      else setError("Login failed. Please try again.");
    } finally { setBusy(false); }
  }

  return (
    <div className="login-shell">
      <div className="login-card">
        <div className="brand-mark">UM Study<span className="dot">.</span></div>
        <h1>Sign in to continue</h1>
        <p>Paste your Spectrum mobile-service token.</p>

        <form onSubmit={onSubmit} style={{ marginTop: 18, textAlign: "left" }}>
          <div style={{ fontSize: 12, color: "var(--muted)", marginBottom: 6 }}>
            Spectrum token
          </div>
          <input
            type="text" autoComplete="off" required spellCheck={false}
            value={token} onChange={e => setToken(e.target.value)}
            placeholder="e.g. 533134282157ceaddbba4dd70d609912"
            style={{ width: "100%", padding: "11px 14px", marginBottom: 14,
                     border: "1px solid var(--line)", borderRadius: 10,
                     background: "var(--bg)", fontSize: 14,
                     fontFamily: "Menlo, monospace" }}
          />
          <button type="submit" className="btn-primary"
                  disabled={busy || !token.trim()}
                  style={{ width: "100%" }}>
            {busy ? "Signing in…" : "Sign in"}
          </button>
        </form>

        <details style={{ marginTop: 18, fontSize: 12, color: "var(--muted)" }}>
          <summary style={{ cursor: "pointer" }}>How to get a token</summary>
          <ol style={{ paddingLeft: 18, marginTop: 8, lineHeight: 1.6 }}>
            <li>Open Spectrum in a browser and sign in (Microsoft 365).</li>
            <li>Go to <b>Profile → Preferences → Security keys</b>.</li>
            <li>Create a key for <b>Moodle mobile web service</b>.</li>
            <li>Copy the token and paste it above.</li>
          </ol>
        </details>

        {error && <div className="error">{error}</div>}
      </div>
    </div>
  );
}
