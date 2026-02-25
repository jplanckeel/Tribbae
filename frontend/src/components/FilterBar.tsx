import { useState } from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSearch, faStar, faTimes, faSlidersH, faBaby, faFolderOpen } from "@fortawesome/free-solid-svg-icons";
import { CATEGORIES, CATEGORY_COLORS } from "../types";

interface Props {
  search: string;
  onSearchChange: (v: string) => void;
  selectedCategory: string | null;
  onCategoryChange: (v: string | null) => void;
  favoritesOnly: boolean;
  onFavoritesToggle: () => void;
  // filtres avancés
  folders?: any[];
  selectedFolderId?: string | null;
  onFolderChange?: (id: string | null) => void;
  children?: any[];
  selectedChildId?: string | null;
  onChildChange?: (id: string | null) => void;
}

function calcAgeShort(birthDateMs: number): string {
  const months = Math.floor((Date.now() - birthDateMs) / (1000 * 60 * 60 * 24 * 30.44));
  if (months < 24) return `${months}m`;
  return `${Math.floor(months / 12)}a`;
}

export default function FilterBar({
  search, onSearchChange,
  selectedCategory, onCategoryChange,
  favoritesOnly, onFavoritesToggle,
  folders = [], selectedFolderId, onFolderChange,
  children = [], selectedChildId, onChildChange,
}: Props) {
  const [showSheet, setShowSheet] = useState(false);

  const activeCount = [
    selectedCategory, selectedFolderId, selectedChildId, favoritesOnly || null,
  ].filter(Boolean).length;

  return (
    <>
      <div className="space-y-3">
        {/* Search */}
        <div className="relative">
          <FontAwesomeIcon icon={faSearch} className="absolute left-3 top-1/2 -translate-y-1/2 text-orange-400 w-4 h-4" />
          <input
            type="text" value={search} onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Rechercher une idée..."
            className="w-full pl-10 pr-10 py-2.5 rounded-full border border-orange-200 focus:border-orange-400 focus:outline-none bg-white text-sm"
          />
          {search && (
            <button onClick={() => onSearchChange("")} className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400">
              <FontAwesomeIcon icon={faTimes} className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        {/* Quick chips */}
        <div className="flex gap-2 overflow-x-auto pb-1 no-scrollbar">
          {CATEGORIES.map((cat) => {
            const active = selectedCategory === cat.value;
            const color = CATEGORY_COLORS[cat.value];
            return (
              <button key={cat.value} onClick={() => onCategoryChange(active ? null : cat.value)}
                className="flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-medium whitespace-nowrap transition-colors"
                style={{ backgroundColor: active ? color : color + "18", color: active ? "white" : color }}
              >
                {cat.icon} {cat.label}
              </button>
            );
          })}
          <button onClick={onFavoritesToggle}
            className={`flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-medium whitespace-nowrap transition-colors ${
              favoritesOnly ? "bg-yellow-400 text-black" : "bg-yellow-50 text-yellow-600"
            }`}
          >
            <FontAwesomeIcon icon={faStar} className="w-3 h-3" /> Favoris
          </button>
          <button onClick={() => setShowSheet(true)}
            className={`flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-medium whitespace-nowrap transition-colors ${
              activeCount > 0 ? "bg-orange-500 text-white" : "bg-orange-50 text-orange-500"
            }`}
          >
            <FontAwesomeIcon icon={faSlidersH} className="w-3 h-3" />
            {activeCount > 0 ? `Filtres (${activeCount})` : "+ Filtres"}
          </button>
        </div>
      </div>

      {/* Bottom sheet */}
      {showSheet && (
        <div className="fixed inset-0 z-50 flex items-end">
          <div className="absolute inset-0 bg-black/40" onClick={() => setShowSheet(false)} />
          <div className="relative bg-white rounded-t-3xl w-full max-h-[80vh] overflow-y-auto p-6 space-y-5">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-bold text-gray-800">Tous les filtres</h3>
              <div className="flex items-center gap-3">
                {activeCount > 0 && (
                  <button onClick={() => {
                    onCategoryChange(null);
                    onFolderChange?.(null);
                    onChildChange?.(null);
                    if (favoritesOnly) onFavoritesToggle();
                  }} className="text-sm text-orange-500 hover:underline">
                    Réinitialiser
                  </button>
                )}
                <button onClick={() => setShowSheet(false)} className="text-gray-400"><FontAwesomeIcon icon={faTimes} className="w-5 h-5" /></button>
              </div>
            </div>

            {/* Catégorie */}
            <div>
              <p className="text-sm font-semibold text-gray-500 mb-2">Catégorie</p>
              <div className="flex flex-wrap gap-2">
                <button onClick={() => onCategoryChange(null)}
                  className={`px-3 py-1.5 rounded-full text-xs font-medium ${!selectedCategory ? "bg-orange-500 text-white" : "bg-gray-100 text-gray-600"}`}
                >
                  Tout
                </button>
                {CATEGORIES.map((cat) => {
                  const active = selectedCategory === cat.value;
                  const color = CATEGORY_COLORS[cat.value];
                  return (
                    <button key={cat.value} onClick={() => onCategoryChange(active ? null : cat.value)}
                      className="px-3 py-1.5 rounded-full text-xs font-medium"
                      style={{ backgroundColor: active ? color : color + "18", color: active ? "white" : color }}
                    >
                      {cat.icon} {cat.label}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Listes */}
            {folders.length > 0 && (
              <div>
                <p className="text-sm font-semibold text-gray-500 mb-2 flex items-center gap-1">
                  <FontAwesomeIcon icon={faFolderOpen} className="w-3.5 h-3.5" /> Liste
                </p>
                <div className="flex flex-wrap gap-2">
                  <button onClick={() => onFolderChange?.(null)}
                    className={`px-3 py-1.5 rounded-full text-xs font-medium ${!selectedFolderId ? "bg-orange-500 text-white" : "bg-gray-100 text-gray-600"}`}
                  >
                    Toutes
                  </button>
                  {folders.map((f) => (
                    <button key={f.id} onClick={() => onFolderChange?.(selectedFolderId === f.id ? null : f.id)}
                      className={`px-3 py-1.5 rounded-full text-xs font-medium ${selectedFolderId === f.id ? "bg-orange-500 text-white" : "bg-gray-100 text-gray-600"}`}
                    >
                      📁 {f.name}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Enfants */}
            {children.length > 0 && (
              <div>
                <p className="text-sm font-semibold text-gray-500 mb-2 flex items-center gap-1">
                  <FontAwesomeIcon icon={faBaby} className="w-3.5 h-3.5" /> Enfant
                </p>
                <div className="flex flex-wrap gap-2">
                  <button onClick={() => onChildChange?.(null)}
                    className={`px-3 py-1.5 rounded-full text-xs font-medium ${!selectedChildId ? "bg-blue-400 text-white" : "bg-gray-100 text-gray-600"}`}
                  >
                    Tous
                  </button>
                  {children.map((c) => (
                    <button key={c.id} onClick={() => onChildChange?.(selectedChildId === c.id ? null : c.id)}
                      className={`px-3 py-1.5 rounded-full text-xs font-medium ${selectedChildId === c.id ? "bg-blue-400 text-white" : "bg-blue-50 text-blue-600"}`}
                    >
                      👶 {c.name} ({calcAgeShort(c.birthDate)})
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Favoris */}
            <div>
              <p className="text-sm font-semibold text-gray-500 mb-2">Autres</p>
              <button onClick={onFavoritesToggle}
                className={`flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-medium ${favoritesOnly ? "bg-yellow-400 text-black" : "bg-yellow-50 text-yellow-600"}`}
              >
                <FontAwesomeIcon icon={faStar} className="w-3 h-3" /> Favoris uniquement ♥
              </button>
            </div>

            <button onClick={() => setShowSheet(false)}
              className="w-full py-2.5 rounded-xl bg-orange-500 text-white font-semibold hover:bg-orange-600"
            >
              Appliquer
            </button>
          </div>
        </div>
      )}
    </>
  );
}
