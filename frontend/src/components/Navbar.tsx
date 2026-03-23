import { useState, useRef, useEffect } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faHome, faCompass, faPlus, faBookmark, faUser,
  faFolderOpen, faTag, faSignOutAlt, faBaby,
  faChevronDown, faSignInAlt, faCrown,
} from "@fortawesome/free-solid-svg-icons";
import AddLinkModal from "./AddLinkModal";

export default function Navbar() {
  const location = useLocation();
  const navigate = useNavigate();
  const [showProfileMenu, setShowProfileMenu] = useState(false);
  const [showAdd, setShowAdd] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);
  const isLoggedIn = !!localStorage.getItem("token");
  const isAdmin = localStorage.getItem("isAdmin") === "true";
  const displayName = localStorage.getItem("displayName");

  const isActive = (path: string) => location.pathname === path;

  const logout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("displayName");
    localStorage.removeItem("isAdmin");
    window.location.href = "/login";
  };

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (profileRef.current && !profileRef.current.contains(e.target as Node)) {
        setShowProfileMenu(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  return (
    <>
      {/* ── Desktop navbar ── */}
      <nav className="bg-white/95 backdrop-blur-sm shadow-sm sticky top-0 z-50 hidden sm:block">
        <div className="max-w-5xl mx-auto px-4 flex items-center justify-between h-16">
          <Link to="/" className="flex items-center gap-2.5">
            <img src="/tribbae.jpg" alt="Tribbae" className="h-10 w-10 rounded-2xl object-cover shadow-md" />
            <span className="text-xl font-bold text-orange-500 tracking-tight">Tribbae</span>
          </Link>

          <div className="flex items-center gap-1">
            <NavLink to="/" icon={faHome} label="Accueil" active={isActive("/")} />
            <NavLink to="/discover" icon={faCompass} label="Explorer" active={isActive("/discover")} />

            {isLoggedIn ? (
              <>
                <NavLink to="/folders" icon={faBookmark} label="Mes idées" active={isActive("/folders")} />

                <button
                  onClick={() => setShowAdd(true)}
                  className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-sm font-medium bg-orange-500 text-white hover:bg-orange-600 transition-colors mx-1"
                >
                  <FontAwesomeIcon icon={faPlus} className="w-4 h-4" /> Ajouter
                </button>

                <div ref={profileRef} className="relative">
                  <button
                    onClick={() => setShowProfileMenu(!showProfileMenu)}
                    className={`flex items-center gap-1.5 px-3 py-2 rounded-xl text-sm font-medium transition-colors ${
                      showProfileMenu ? "bg-orange-50 text-orange-500" : "text-gray-500 hover:bg-gray-50 hover:text-gray-700"
                    }`}
                  >
                    <div className="w-7 h-7 rounded-full bg-gradient-to-br from-orange-400 to-orange-600 flex items-center justify-center text-white text-xs font-bold">
                      {displayName?.charAt(0).toUpperCase() || "?"}
                    </div>
                    <span className="max-w-[80px] truncate">{displayName || "Profil"}</span>
                    <FontAwesomeIcon icon={faChevronDown} className={`w-3 h-3 transition-transform ${showProfileMenu ? "rotate-180" : ""}`} />
                  </button>
                  {showProfileMenu && (
                    <div className="absolute right-0 top-12 bg-white rounded-2xl shadow-xl border border-gray-100 py-2 w-48 z-50">
                      <div className="px-4 py-2 border-b border-gray-50 mb-1">
                        <p className="text-xs text-gray-400">Connecté en tant que</p>
                        <p className="text-sm font-semibold text-gray-800 truncate">{displayName}</p>
                      </div>
                      <DropdownItem to="/tags" icon={faTag} label="Tags" onClick={() => setShowProfileMenu(false)} />
                      <DropdownItem to="/children" icon={faBaby} label="Mes enfants" onClick={() => setShowProfileMenu(false)} />
                      {isAdmin && (
                        <DropdownItem to="/admin" icon={faCrown} label="Administration" onClick={() => setShowProfileMenu(false)} />
                      )}
                      <hr className="my-1 border-gray-100" />
                      <button
                        onClick={logout}
                        className="w-full flex items-center gap-2.5 px-4 py-2.5 text-sm text-gray-500 hover:bg-red-50 hover:text-red-500 transition-colors"
                      >
                        <FontAwesomeIcon icon={faSignOutAlt} className="w-4 h-4" /> Déconnexion
                      </button>
                    </div>
                  )}
                </div>
              </>
            ) : (
              <Link
                to="/login"
                className="flex items-center gap-1.5 px-4 py-2 rounded-full bg-orange-500 text-white text-sm font-medium hover:bg-orange-600 transition-colors"
              >
                <FontAwesomeIcon icon={faSignInAlt} className="w-3.5 h-3.5" /> Se connecter
              </Link>
            )}
          </div>
        </div>
      </nav>

      {/* ── Mobile top bar ── */}
      <div className="sm:hidden bg-white/95 backdrop-blur-sm shadow-sm sticky top-0 z-50 px-4 h-14 flex items-center justify-between">
        <Link to="/" className="flex items-center gap-2">
          <img src="/tribbae.jpg" alt="Tribbae" className="h-8 w-8 rounded-xl object-cover" />
          <span className="text-lg font-bold text-orange-500">Tribbae</span>
        </Link>
        {isLoggedIn ? (
          <button
            onClick={() => navigate("/children")}
            className="w-8 h-8 rounded-full bg-gradient-to-br from-orange-400 to-orange-600 flex items-center justify-center text-white text-sm font-bold"
          >
            {displayName?.charAt(0).toUpperCase() || "?"}
          </button>
        ) : (
          <Link to="/login" className="text-sm font-medium text-orange-500">
            Connexion
          </Link>
        )}
      </div>

      {/* ── Mobile bottom nav — 5 onglets comme le mobile ── */}
      <nav className="sm:hidden fixed bottom-0 left-0 right-0 z-50 bg-white border-t border-gray-100 safe-area-pb">
        <div className="flex items-center justify-around h-16">
          <MobileNavItem to="/" icon={faHome} label="Accueil" active={isActive("/")} />
          <MobileNavItem to="/discover" icon={faCompass} label="Explorer" active={isActive("/discover")} />

          {/* Bouton + central */}
          {isLoggedIn ? (
            <button
              onClick={() => setShowAdd(true)}
              className="flex flex-col items-center justify-center w-14 h-14 -mt-5 rounded-full bg-orange-500 shadow-lg shadow-orange-200"
            >
              <FontAwesomeIcon icon={faPlus} className="w-6 h-6 text-white" />
            </button>
          ) : (
            <Link to="/login" className="flex flex-col items-center justify-center w-14 h-14 -mt-5 rounded-full bg-orange-500 shadow-lg shadow-orange-200">
              <FontAwesomeIcon icon={faPlus} className="w-6 h-6 text-white" />
            </Link>
          )}

          {isLoggedIn ? (
            <>
              <MobileNavItem to="/folders" icon={faBookmark} label="Mes idées" active={isActive("/folders")} />
              <MobileNavItem to="/children" icon={faUser} label="Profil" active={isActive("/children")} />
            </>
          ) : (
            <>
              <MobileNavItem to="/tags" icon={faFolderOpen} label="Listes" active={isActive("/tags")} />
              <MobileNavItem to="/login" icon={faSignInAlt} label="Connexion" active={isActive("/login")} />
            </>
          )}
        </div>
      </nav>

      {showAdd && (
        <AddLinkModal
          onClose={() => setShowAdd(false)}
          onCreated={() => setShowAdd(false)}
        />
      )}
    </>
  );
}

function NavLink({ to, icon, label, active }: { to: string; icon: any; label: string; active: boolean }) {
  return (
    <Link
      to={to}
      className={`flex items-center gap-1.5 px-3 py-2 rounded-xl text-sm font-medium transition-colors ${
        active ? "bg-orange-50 text-orange-500" : "text-gray-500 hover:bg-gray-50 hover:text-gray-700"
      }`}
    >
      <FontAwesomeIcon icon={icon} className="w-4 h-4" />
      {label}
    </Link>
  );
}

function DropdownItem({ to, icon, label, onClick }: { to: string; icon: any; label: string; onClick: () => void }) {
  return (
    <Link
      to={to}
      onClick={onClick}
      className="flex items-center gap-2.5 px-4 py-2.5 text-sm text-gray-700 hover:bg-orange-50 hover:text-orange-500 transition-colors"
    >
      <FontAwesomeIcon icon={icon} className="w-4 h-4" /> {label}
    </Link>
  );
}

function MobileNavItem({ to, icon, label, active }: { to: string; icon: any; label: string; active: boolean }) {
  return (
    <Link to={to} className="flex flex-col items-center gap-0.5 px-3 py-1">
      <FontAwesomeIcon
        icon={icon}
        className={`w-5 h-5 transition-colors ${active ? "text-orange-500" : "text-gray-400"}`}
      />
      <span className={`text-[10px] font-medium transition-colors ${active ? "text-orange-500" : "text-gray-400"}`}>
        {label}
      </span>
    </Link>
  );
}
