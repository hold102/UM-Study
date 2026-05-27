import { Link } from "react-router-dom";
import { Me } from "../api/client";

export function Header({ user, onSignOut }: { user: Me | null; onSignOut?: () => void }) {
  const initials = user?.displayName
    ? user.displayName.split(" ").map(s => s[0]).slice(0, 2).join("").toUpperCase()
    : "?";

  return (
    <header className="app-header">
      <Link to="/courses" className="brand">
        UM Study<span className="dot">.</span>
      </Link>
      {user && (
        <div className="user-chip">
          <span className="avatar">{initials}</span>
          <span>{user.email}</span>
          <Link to="/settings" style={{ color: "var(--muted)" }}>Settings</Link>
          <button onClick={onSignOut} aria-label="Sign out">Sign out</button>
        </div>
      )}
    </header>
  );
}
