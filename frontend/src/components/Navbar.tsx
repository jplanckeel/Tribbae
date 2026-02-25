import { useState, useRef, useEffect } from "react";
import { Link, useLocation } from "react-router-dom";
import { Home, FolderOpen, Tag, Globe, LogOut, Baby, ChevronDown } from "lucide-react";

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
            <Home size={18} /> Accueil
          </Link>
          <Link to="/folders" className={`flex items-center gap-1 pb-1 text-sm font-medium ${active("/folders")}`}>
            <FolderOpen size={18} /> Listes
          </Link>
          <Link to="/tags" className={`flex items-center gap-1 pb-1 text-sm font-medium ${active("/tags")}`}>
            <Tag size={18} /> Tags
          </Link>
          <Link to="/community" className={`flex items-center gap-1 pb-1 text-sm font-medium ${active("/community")}`}>
            <Globe size={18} /> Communauté
          </Link>

          {/* Menu Plus */}
          <div ref={moreRef} className="relative">
            <button
              onClick={() => setShowMore(!showMore)}
              className={`flex items-center gap-1 pb-1 text-sm font-medium text-gray-500 hover:text-orange-400 ${
                location.pathname === "/children" ? "text-orange-500 border-b-2 border-orange-500" : ""
              }`}
            >
              {displayName || "Plus"} <ChevronDown size={14} />
            </button>
            {showMore && (
              <div className="absolute right-0 top-8 bg-white rounded-xl shadow-lg border border-gray-100 py-1 w-44 z-50">
                <Link
                  to="/children"
                  onClick={() => setShowMore(false)}
                  className="flex items-center gap-2 px-4 py-2.5 text-sm text-gray-700 hover:bg-orange-50 hover:text-orange-500"
                >
                  <Baby size={16} /> Mes enfants
                </Link>
                <hr className="my-1 border-gray-100" />
                <button
                  onClick={logout}
                  className="w-full flex items-center gap-2 px-4 py-2.5 text-sm text-gray-500 hover:bg-red-50 hover:text-red-500"
                >
                  <LogOut size={16} /> Déconnexion
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}
