import { useState, useRef, useEffect } from "react";
import { Link, useLocation } from "react-router-dom";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faHome, faFolderOpen, faTag, faGlobe, faSignOutAlt, faBaby, faChevronDown } from "@fortawesome/free-solid-svg-icons";

export default function Navbar() {
  const location = useLocation();
  const [showMore, setShowMore] = useState(false);
  const moreRef = useRef<HTMLDivElement>(null);

  const active = (path: string) =>
    location.pathname === path
      ? "text-orange-500 border-b-2 border-orange-500"
      : "text-gray-500 hover:text-orange-400";

  const logout = () => {
    localStorage.removeItem("token");
    localStorage.removeItem("displayName");
    window.location.href = "/login";
  };

  // Ferme le menu si clic en dehors
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (moreRef.current && !moreRef.current.contains(e.target as Node)) {
        setShowMore(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const displayName = localStorage.getItem("displayName");

  return (
    <nav className="bg-white shadow-sm sticky top-0 z-50">
      <div className="max-w-5xl mx-auto px-4 flex items-center justify-between h-14">
        <Link to="/" className="text-xl font-bold text-orange-500">
          Tribbae
        </Link>

        <div className="flex items-center gap-5">
          <Link to="/" className={`flex items-center gap-1 pb-1 text-sm font-medium ${active("/")}`}>
            <FontAwesomeIcon icon={faHome} className="w-4 h-4" /> Accueil
          </Link>
          <Link to="/folders" className={`flex items-center gap-1 pb-1 text-sm font-medium ${active("/folders")}`}>
            <FontAwesomeIcon icon={faFolderOpen} className="w-4 h-4" /> Listes
          </Link>
          <Link to="/tags" className={`flex items-center gap-1 pb-1 text-sm font-medium ${active("/tags")}`}>
            <FontAwesomeIcon icon={faTag} className="w-4 h-4" /> Tags
          </Link>
          <Link to="/community" className={`flex items-center gap-1 pb-1 text-sm font-medium ${active("/community")}`}>
            <FontAwesomeIcon icon={faGlobe} className="w-4 h-4" /> Communauté
          </Link>

          {/* Menu Plus */}
          <div ref={moreRef} className="relative">
            <button
              onClick={() => setShowMore(!showMore)}
              className={`flex items-center gap-1 pb-1 text-sm font-medium text-gray-500 hover:text-orange-400 ${
                location.pathname === "/children" ? "text-orange-500 border-b-2 border-orange-500" : ""
              }`}
            >
              {displayName || "Plus"} <FontAwesomeIcon icon={faChevronDown} className="w-3 h-3" />
            </button>
            {showMore && (
              <div className="absolute right-0 top-8 bg-white rounded-xl shadow-lg border border-gray-100 py-1 w-44 z-50">
                <Link
                  to="/children"
                  onClick={() => setShowMore(false)}
                  className="flex items-center gap-2 px-4 py-2.5 text-sm text-gray-700 hover:bg-orange-50 hover:text-orange-500"
                >
                  <FontAwesomeIcon icon={faBaby} className="w-4 h-4" /> Mes enfants
                </Link>
                <hr className="my-1 border-gray-100" />
                <button
                  onClick={logout}
                  className="w-full flex items-center gap-2 px-4 py-2.5 text-sm text-gray-500 hover:bg-red-50 hover:text-red-500"
                >
                  <FontAwesomeIcon icon={faSignOutAlt} className="w-4 h-4" /> Déconnexion
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}
