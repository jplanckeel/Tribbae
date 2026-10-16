import type { ReactNode } from "react";
import { Navigate, NavLink, Outlet, ScrollRestoration, useLocation } from "react-router";
import { useSession } from "../lib/session";
import { ComposerProvider, useComposer } from "./Composer";
import Icon, { type IconName } from "./Icon";
import Logo from "./Logo";
import { Avatar } from "./ui";

const NAV: { to: string; label: string; icon: IconName; end?: boolean }[] = [
  { to: "/", label: "Accueil", icon: "home", end: true },
  { to: "/explorer", label: "Explorer", icon: "compass" },
  { to: "/mes-idees", label: "Mes idées", icon: "tipi" },
  { to: "/tribu", label: "Ma tribu", icon: "tribe" },
];

/** Barre flottante du bas (mobile) : pilule écorce + losange « + » central. */
function BottomNav() {
  const { openAdd } = useComposer();
  const { pathname } = useLocation();
  // La fiche d'une idée a sa propre barre d'actions en bas
  if (pathname.startsWith("/links/")) return null;
  const item = (n: (typeof NAV)[number]) => (
    <NavLink
      key={n.to}
      to={n.to}
      end={n.end}
      aria-label={n.label}
      className={({ isActive }) =>
        `flex h-11 items-center justify-center gap-1.5 rounded-2xl text-[13px] font-bold whitespace-nowrap transition-all ${
          isActive ? "bg-sable px-3.5 text-ecorce" : "w-11 text-bark-mute hover:text-sable"
        }`
      }
    >
      {({ isActive }) => (
        <>
          <Icon name={n.icon} size={isActive ? 20 : 22} className={isActive ? "text-terracotta" : ""} />
          {isActive && <span>{n.label}</span>}
        </>
      )}
    </NavLink>
  );
  return (
    <nav
      aria-label="Navigation principale"
      className="fixed inset-x-4 bottom-[max(20px,env(safe-area-inset-bottom))] z-50 flex h-[68px] items-center justify-around rounded-[26px] bg-ecorce px-1.5 shadow-nav md:hidden"
    >
      {item(NAV[0])}
      {item(NAV[1])}
      <button
        type="button"
        onClick={() => openAdd()}
        aria-label="Ajouter une idée"
        className="-mt-[30px] flex h-14 w-14 rotate-45 items-center justify-center rounded-[20px] border-4 border-sable bg-terracotta text-white transition-transform active:scale-95"
      >
        <Icon name="plus" size={24} strokeWidth={2.6} className="-rotate-45" />
      </button>
      {item(NAV[2])}
      {item(NAV[3])}
    </nav>
  );
}

/** En-tête collant pour les grands écrans. */
function TopBar() {
  const session = useSession();
  const { openAdd, openAi } = useComposer();
  return (
    <header className="sticky top-0 z-40 hidden border-b border-line/70 bg-sable/90 backdrop-blur-md md:block">
      <div className="mx-auto flex h-[72px] max-w-6xl items-center gap-6 px-8">
        <NavLink to="/" aria-label="Tribbae, accueil">
          <Logo />
        </NavLink>
        <nav aria-label="Navigation principale" className="flex flex-1 items-center gap-1">
          {NAV.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              end={n.end}
              className={({ isActive }) =>
                `flex h-10 items-center gap-2 rounded-s px-3.5 text-sm font-bold transition-colors ${
                  isActive ? "bg-ecorce text-sable" : "text-sec hover:bg-sable-2 hover:text-ecorce"
                }`
              }
            >
              {n.label}
            </NavLink>
          ))}
        </nav>
        <button
          type="button"
          onClick={() => openAi()}
          className="flex h-11 items-center gap-2 rounded-s bg-prune px-4 text-sm font-bold text-sable transition-colors hover:bg-[#4d3848]"
        >
          <Icon name="sparkles" size={18} className="text-soleil" />
          IA
        </button>
        <button
          type="button"
          onClick={() => openAdd()}
          className="flex h-11 items-center gap-2 rounded-s bg-terracotta px-4 text-sm font-bold text-white transition-colors hover:bg-terracotta-dark"
        >
          <Icon name="plus" size={18} strokeWidth={2.6} />
          Ajouter
        </button>
        {session ? (
          <NavLink to="/tribu" aria-label="Ma tribu">
            <Avatar name={session.displayName} size={40} rounded="rounded-[14px]" />
          </NavLink>
        ) : (
          <NavLink to="/connexion" className="text-sm font-bold text-terracotta hover:text-terracotta-dark">
            Se connecter
          </NavLink>
        )}
      </div>
    </header>
  );
}

/** Mise en page des écrans principaux. */
export function AppLayout() {
  return (
    <ComposerProvider>
      <a
        href="#contenu"
        className="sr-only z-[80] rounded-s bg-ecorce px-4 py-2 font-bold text-sable focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Aller au contenu
      </a>
      <TopBar />
      <main id="contenu" className="pb-32 md:pb-16">
        <Outlet />
      </main>
      <BottomNav />
      <ScrollRestoration />
    </ComposerProvider>
  );
}

/** Mise en page plein écran (bienvenue, connexion, partage). */
export function BareLayout() {
  return (
    <>
      <Outlet />
      <ScrollRestoration />
    </>
  );
}

/** Conteneur de page standard. */
export function Page({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <div className={`mx-auto flex max-w-6xl flex-col gap-5 px-5 pt-5 md:px-8 md:pt-8 ${className}`}>{children}</div>;
}

/** Redirige vers l'écran de bienvenue si aucune session. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const session = useSession();
  const location = useLocation();
  if (!session) {
    return <Navigate to={`/bienvenue?next=${encodeURIComponent(location.pathname + location.search)}`} replace />;
  }
  return <>{children}</>;
}
