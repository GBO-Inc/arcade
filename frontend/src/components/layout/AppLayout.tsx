import { NavLink, Outlet } from 'react-router-dom';

export function AppLayout() {
    const navLinkClass = ({ isActive }: { isActive: boolean }) =>
        `px-3 py-2 rounded-md text-sm font-medium transition-colors ${
            isActive
                ? 'bg-indigo-600 text-white'
                : 'text-slate-300 hover:bg-slate-800 hover:text-white'
        }`;

    return (
        <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
            <header className="border-b border-slate-800 bg-slate-900">
                <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between">
                    <div className="flex items-center gap-8">
                        <NavLink to="/lobby" className="text-xl font-bold tracking-tight text-white">
                            Arcade
                        </NavLink>
                        <nav className="flex items-center gap-2" aria-label="Main Navigation">
                            <NavLink to="/lobby" className={navLinkClass}>
                                Lobby
                            </NavLink>
                            <NavLink to="/leaderboard" className={navLinkClass}>
                                Leaderboard
                            </NavLink>
                            <NavLink to="/history" className={navLinkClass}>
                                History
                            </NavLink>
                        </nav>
                    </div>
                    <div className="flex items-center gap-3">
                        <NavLink
                            to="/match/DEMO12"
                            className="text-xs bg-slate-800 hover:bg-slate-700 text-slate-300 px-2.5 py-1.5 rounded border border-slate-700"
                        >
                            Demo Room
                        </NavLink>
                        <NavLink to="/profile" className={navLinkClass}>
                            Profile
                        </NavLink>
                        <NavLink
                            to="/login"
                            className="text-sm font-medium text-slate-400 hover:text-white ml-2"
                        >
                            Sign in
                        </NavLink>
                    </div>
                </div>
            </header>

            <main className="flex-1 max-w-6xl w-full mx-auto px-4 py-8">
                <Outlet />
            </main>
        </div>
    );
}