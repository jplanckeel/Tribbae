import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { Navigate, NavLink, Outlet, ScrollRestoration, useLocation, useNavigate } from "react-router";
import { clearSession, useSession } from "../lib/session";
import { tabLink, trackLocation } from "../lib/transitions";
import { ComposerProvider, useComposer } from "./Composer";
import Icon, { type IconName } from "./Icon";
import Logo from "./Logo";
import VerifyEmailBanner from "./VerifyEmailBanner";

const NAV: { to: string; label: string; icon: IconName; end?: boolean }[] = [
  { to: "/", label: "Accueil", icon: "home", end: true },
  { to: "/explorer", label: "Explorer", icon: "compass" },
  { to: "/mes-idees", label: "Mes idées", icon: "tipi" },
  { to: "/tribu", label: "Ma tribu", icon: "tribe" },
];

/**
 * Masque la barre quand on fait défiler vers le bas (lecture) et la ramène dès
 * qu'on remonte, ou en haut de page. L'état est lié à la page : changer
 * d'écran fait réapparaître la barre.
 */
function useHiddenOnScroll(pathname: string) {
  const [state, setState] = useState({ hidden: false, path: pathname });
  useEffect(() => {
    let last = window.scrollY;
    let frame = 0;
    const onScroll = () => {
      if (frame) return;
      frame = requestAnimationFrame(() => {
        frame = 0;
        const y = window.scrollY;
        const delta = y - last;
        if (Math.abs(delta) < 8 && y > 80) return;
        last = y;
        const hidden = y > 80 && delta > 0;
        setState((s) => (s.hidden === hidden && s.path === window.location.pathname ? s : { hidden, path: window.location.pathname }));
      });
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      window.removeEventListener("scroll", onScroll);
      cancelAnimationFrame(frame);
    };
  }, []);
  const show = () => setState((s) => (s.hidden ? { ...s, hidden: false } : s));
  return { hidden: state.hidden && state.path === pathname, show };
}

/**
 * Barre flottante du bas (mobile) : pilule écorce + losange « + » central.
 * Chaque onglet a une place fixe (icône + libellé toujours visibles) : changer
 * d'onglet ne décale plus les autres.
 */
function BottomNav() {
  const { openAdd } = useComposer();
  const { pathname } = useLocation();
  const { hidden, show } = useHiddenOnScroll(pathname);
  // La fiche d'une idée a sa propre barre d'actions en bas
  if (pathname.startsWith("/links/")) return null;
  const current = tabOf(pathname);
  const item = (n: (typeof NAV)[number]) => (
    <NavLink
      key={n.to}
      to={n.to}
      end={n.end}
      viewTransition={tabLink(NAV.indexOf(n), current).viewTransition}
      // Toucher l'onglet déjà actif remonte en haut de la page
      onClick={(e) => {
        if (e.currentTarget.getAttribute("aria-current") === "page") {
          e.preventDefault();
          window.scrollTo({ top: 0, behavior: "smooth" });
          return;
        }
        tabLink(NAV.indexOf(n), current).onClick();
      }}
      className="group flex h-full min-w-0 flex-1 flex-col items-center justify-center gap-1 rounded-[20px] outline-offset-[-4px]"
    >
      {({ isActive }) => (
        <>
          <span
            className={`flex h-[30px] w-12 items-center justify-center rounded-full transition-colors duration-200 ${
              isActive ? "bg-sable text-terracotta" : "text-bark-mute group-hover:text-sable"
            }`}
          >
            <Icon name={n.icon} size={21} />
          </span>
          <span className={`truncate text-[11px] leading-none font-bold transition-colors duration-200 ${isActive ? "text-sable" : "text-bark-mute"}`}>
            {n.label}
          </span>
        </>
      )}
    </NavLink>
  );
  return (
    <div
      onFocusCapture={show}
      className={`pointer-events-none fixed inset-x-0 bottom-0 z-50 pt-6 pb-[max(20px,env(safe-area-inset-bottom))] transition-transform duration-300 ease-out motion-reduce:transition-none md:hidden ${
        hidden ? "translate-y-[calc(100%+12px)]" : ""
      }`}
    >
      {/* Fondu sable : le contenu qui défile dessous s'efface au lieu d'être coupé net */}
      <div aria-hidden="true" className="absolute inset-0 bg-gradient-to-b from-sable/0 to-sable/90" />
      <nav
        aria-label="Navigation principale"
        style={{ viewTransitionName: "bottom-nav" }}
        className="nav-bar pointer-events-auto relative mx-4 flex h-[68px] items-center rounded-[26px] bg-ecorce px-1 shadow-nav"
      >
        {item(NAV[0])}
        {item(NAV[1])}
        <div className="flex flex-1 justify-center">
          <button
            type="button"
            onClick={() => openAdd()}
            aria-label="Ajouter une idée"
            className="nav-plus -mt-[30px] flex h-14 w-14 rotate-45 items-center justify-center rounded-[20px] border-4 border-sable bg-terracotta text-white transition-transform active:scale-95"
          >
            <Icon name="plus" size={24} strokeWidth={2.6} className="-rotate-45" />
          </button>
        </div>
        {item(NAV[2])}
        {item(NAV[3])}
      </nav>
    </div>
  );
}

/** En-tête des grands écrans (maquette « Web · Accueil »). */
function TopBar() {
  const session = useSession();
  const current = tabOf(useLocation().pathname);
  const { openAdd } = useComposer();
  const initial = (session?.displayName?.trim() || "?").charAt(0).toUpperCase();
  return (
    <header className="relative z-40 hidden md:block">
      <div className="mx-auto flex max-w-[1200px] flex-wrap items-center gap-x-7 gap-y-3 px-6 py-[18px]">
        <NavLink to="/" aria-label="Tribbae, accueil" className="text-ecorce">
          <Logo size={40} />
        </NavLink>
        <nav aria-label="Navigation principale" className="flex flex-1 flex-wrap gap-1">
          {NAV.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              end={n.end}
              {...tabLink(NAV.indexOf(n), current)}
              className={({ isActive }) =>
                `rounded-[14px] px-4 py-2.5 text-[15px] transition-colors ${
                  isActive ? "bg-ecorce font-bold text-sable" : "font-semibold text-ecorce hover:bg-sable-2"
                }`
              }
            >
              {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="flex items-center gap-2.5">
          <button
            type="button"
            onClick={() => openAdd()}
            className="flex h-[46px] items-center gap-2 rounded-[16px] bg-terracotta px-[18px] text-[15px] font-bold text-white transition-colors hover:bg-terracotta-dark"
          >
            <Icon name="plus" size={18} strokeWidth={2.6} />
            Ajouter une idée
          </button>
          {session ? (
            <AccountMenu initial={initial} name={session.displayName} />
          ) : (
            <NavLink
              to="/connexion"
              className="flex h-[46px] items-center rounded-[16px] border-2 border-ecorce px-4 text-[15px] font-bold text-ecorce transition-colors hover:bg-ecorce hover:text-sable"
            >
              Se connecter
            </NavLink>
          )}
        </div>
      </div>
    </header>
  );
}

/**
 * Menu du compte (en-tête bureau) : Ma tribu, paramètres, applications
 * connectées et déconnexion. Remplace le rouage de « Ma tribu » sur grand écran.
 */
function AccountMenu({ initial, name }: { initial: string; name: string }) {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (e: Event) => {
      if (e instanceof KeyboardEvent ? e.key === "Escape" : !ref.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", close);
    document.addEventListener("keydown", close);
    return () => {
      document.removeEventListener("pointerdown", close);
      document.removeEventListener("keydown", close);
    };
  }, [open]);

  const go = (to: string) => {
    setOpen(false);
    navigate(to);
  };
  const logout = () => {
    setOpen(false);
    clearSession();
    navigate("/bienvenue", { replace: true });
  };
  const item = "flex w-full items-center gap-3 rounded-[12px] px-3 py-2.5 text-left text-[15px] font-semibold text-ecorce hover:bg-sable-2";

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Mon compte (${name || "Ma tribu"})`}
        className="flex h-[46px] w-[46px] items-center justify-center rounded-[16px] bg-braise font-display text-lg font-extrabold text-ecorce transition-transform hover:-translate-y-0.5"
      >
        {initial}
      </button>
      {open && (
        <div role="menu" aria-label="Mon compte" className="surface-raised absolute top-[54px] right-0 z-50 flex w-64 flex-col gap-0.5 rounded-[18px] border border-line bg-sable p-2 shadow-soft">
          <p className="truncate px-3 pt-1.5 pb-2 text-sm font-bold text-sec">{name || "Mon compte"}</p>
          <button type="button" role="menuitem" className={item} onClick={() => go("/tribu")}>
            <Icon name="tribe" size={18} className="text-terracotta" />Ma tribu
          </button>
          <button type="button" role="menuitem" className={item} onClick={() => go("/tribu?ouvrir=parametres")}>
            <Icon name="settings" size={18} className="text-sec" />Paramètres
          </button>
          <button type="button" role="menuitem" className={item} onClick={() => go("/tribu?ouvrir=applications")}>
            <Icon name="sparkles" size={18} className="text-prune" />Applications connectées
          </button>
          <div className="my-1 h-px bg-line" />
          <button type="button" role="menuitem" className={`${item} text-sortie-ink`} onClick={logout}>
            <Icon name="logout" size={18} />Se déconnecter
          </button>
        </div>
      )}
    </div>
  );
}

/** Grand soleil de l'accueil bureau, qui passe derrière l'en-tête (maquette « Web · Accueil »). */
function DesktopSun() {
  const { pathname } = useLocation();
  if (pathname !== "/") return null;
  return (
    <div aria-hidden="true" className="pointer-events-none absolute -top-[220px] -right-[160px] hidden h-[620px] w-[620px] rounded-full bg-soleil md:block" />
  );
}

/** Pied de page des grands écrans. */
function Footer() {
  return (
    <footer className="hidden border-t border-line px-6 py-6 text-center text-sm text-sec md:block">
      Tribbae — les idées de la tribu, au même endroit ·{" "}
      <NavLink to="/a-propos" className="font-semibold text-terracotta hover:text-terracotta-dark">À propos</NavLink> ·{" "}
      <NavLink to="/aide" className="font-semibold text-terracotta hover:text-terracotta-dark">Aide</NavLink> ·{" "}
      <NavLink to="/confidentialite" className="font-semibold text-terracotta hover:text-terracotta-dark">Confidentialité</NavLink>
    </footer>
  );
}

/** Onglet de la barre (0 à 3) d'un chemin, ou -1 hors des onglets. */
function tabOf(pathname: string): number {
  if (pathname === "/") return 0;
  if (pathname.startsWith("/explorer") || pathname.startsWith("/category/")) return 1;
  if (pathname.startsWith("/mes-idees")) return 2;
  if (pathname.startsWith("/tribu")) return 3;
  return -1;
}

/**
 * Suit les changements d'écran pour les transitions (lib/transitions.ts) :
 * ouvrir une idée, creuser vers un dossier et changer d'onglet passent tous par
 * les View Transitions, animées hors du rendu de la page (plus de saccades).
 */
function ScreenTransitions({ children }: { children: ReactNode }) {
  const location = useLocation();
  useLayoutEffect(() => trackLocation(location.state), [location.key, location.state]);
  return <>{children}</>;
}

/** Mise en page des écrans principaux. */
export function AppLayout() {
  return (
    <ComposerProvider>
      {/* Colonne pleine hauteur : le pied de page reste en bas sur les pages courtes */}
      <div className="flex min-h-dvh flex-col">
        <a
          href="#contenu"
          className="sr-only z-[80] rounded-small bg-ecorce px-4 py-2 font-bold text-sable focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
        >
          Aller au contenu
        </a>
        <div className="relative flex-1 overflow-x-clip">
          <DesktopSun />
          <TopBar />
          <VerifyEmailBanner />
          <main id="contenu" className="relative pb-[calc(124px+env(safe-area-inset-bottom))] md:pb-16">
            <ScreenTransitions>
              <Outlet />
            </ScreenTransitions>
          </main>
        </div>
        <Footer />
      </div>
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
  return <div className={`mx-auto flex max-w-[1200px] flex-col gap-5 px-5 pt-5 md:px-6 md:pt-4 ${className}`}>{children}</div>;
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
