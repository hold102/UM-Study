import { ReactNode, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, isMock, Me } from "../api/client";
import { Header } from "./Header";

export function Shell({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null);
  const nav = useNavigate();

  useEffect(() => {
    api.me().then(setUser).catch(() => nav("/login"));
  }, [nav]);

  function signOut() {
    if (isMock) { nav("/login"); return; }
    window.location.href = "/api/auth/logout";
  }

  return (
    <div className="app-shell">
      {isMock && <div className="mock-banner">Preview mode — using mock data. Set VITE_USE_MOCK=false to hit the backend.</div>}
      <Header user={user} onSignOut={signOut} />
      {children}
    </div>
  );
}
