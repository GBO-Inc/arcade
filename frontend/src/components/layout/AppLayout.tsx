import { NavLink, Outlet } from 'react-router-dom';

export function AppLayout() {
  const linkStyle = ({ isActive }: { isActive: boolean }) =>
    `px-3 py-1.5 rounded text-sm font-medium ${
      isActive ? 'bg-slate-800 text-white' : 'text-slate-400 hover:text-white'
    }`;

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      <header className="border-b border-slate-800 bg-slate-900">
        <div className="max-w-5xl mx-auto px-4 h-14 flex items-center justify-between">
          <div className="flex items-center gap-6">
            <NavLink to="/lobby" className="text-lg font-bold text-white">
              Arcade
            </NavLink>
            <nav className="flex items-center gap-1">
              <NavLink to="/lobby" className={linkStyle}>
                Lobby
              </NavLink>
              <NavLink to="/leaderboard" className={linkStyle}>
                Leaderboard
              </NavLink>
              <NavLink to="/history" className={linkStyle}>
                History
              </NavLink>
            </nav>
          </div>

          <div className="flex items-center gap-2">
            <NavLink to="/profile" className={linkStyle}>
              Profile
            </NavLink>
            <NavLink to="/login" className={linkStyle}>
              Login
            </NavLink>
            <NavLink to="/register" className={linkStyle}>
              Register
            </NavLink>
          </div>
        </div>
      </header>

      <main className="flex-1 max-w-5xl w-full mx-auto px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
