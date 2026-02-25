import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { links as linksApi, folders as foldersApi, children as childrenApi } from "../api";
import LinkCard from "../components/LinkCard";
import FilterBar from "../components/FilterBar";
import { Plus } from "lucide-react";
import AddLinkModal from "../components/AddLinkModal";

function parseAgeMonths(ageRange: string): number {
  const lower = ageRange.toLowerCase();
  const isMois = lower.includes("mois");
  const nums = [...lower.matchAll(/\d+/g)].map((m) => parseInt(m[0]));
  const max = Math.max(...nums);
  if (!isFinite(max)) return Infinity;
  return isMois ? max : max * 12;
}

export default function Home() {
  const [allLinks, setAllLinks] = useState<any[]>([]);
  const [folderList, setFolderList] = useState<any[]>([]);
  const [childList, setChildList] = useState<any[]>([]);
  const [search, setSearch] = useState("");
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null);
  const [selectedChildId, setSelectedChildId] = useState<string | null>(null);
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const [showAdd, setShowAdd] = useState(false);
  const navigate = useNavigate();

  const fetchLinks = async () => {
    try {
      const res = await linksApi.list();
      setAllLinks(res.links || []);
    } catch {
      navigate("/login");
    }
  };

  useEffect(() => {
    fetchLinks();
    foldersApi.list().then((r) => setFolderList(r.folders || []));
    childrenApi.list().then((r) => setChildList(r.children || []));
  }, []);

  // Calcul de l'âge max en mois pour le filtre enfant
  const childAgeMonths = selectedChildId
    ? (() => {
        const child = childList.find((c) => c.id === selectedChildId);
        if (!child) return null;
        return Math.floor((Date.now() - child.birthDate) / (1000 * 60 * 60 * 24 * 30.44));
      })()
    : null;

  const filtered = allLinks.filter((l) => {
    if (selectedCategory && l.category !== selectedCategory) return false;
    if (selectedFolderId && l.folderId !== selectedFolderId) return false;
    if (favoritesOnly && !l.favorite) return false;
    if (childAgeMonths !== null && l.ageRange) {
      const maxAge = parseAgeMonths(l.ageRange);
      if (maxAge < childAgeMonths) return false;
    }
    if (search) {
      const q = search.toLowerCase();
      return (
        l.title?.toLowerCase().includes(q) ||
        l.description?.toLowerCase().includes(q) ||
        l.location?.toLowerCase().includes(q) ||
        l.tags?.some((t: string) => t.toLowerCase().includes(q))
      );
    }
    return true;
  });

  return (
    <div className="max-w-5xl mx-auto px-4 py-6">
      <FilterBar
        search={search} onSearchChange={setSearch}
        selectedCategory={selectedCategory} onCategoryChange={setSelectedCategory}
        favoritesOnly={favoritesOnly} onFavoritesToggle={() => setFavoritesOnly(!favoritesOnly)}
        folders={folderList} selectedFolderId={selectedFolderId} onFolderChange={setSelectedFolderId}
        children={childList} selectedChildId={selectedChildId} onChildChange={setSelectedChildId}
      />

      {filtered.length === 0 ? (
        <div className="text-center py-20 text-gray-400">
          <p className="text-5xl mb-4">💡</p>
          <p>Ajoutez votre première idée</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mt-4">
          {filtered.map((link) => (
            <LinkCard key={link.id} link={link} onClick={() => navigate(`/links/${link.id}`)} />
          ))}
        </div>
      )}

      <button
        onClick={() => setShowAdd(true)}
        className="fixed bottom-6 right-6 w-14 h-14 rounded-full bg-orange-500 text-white shadow-lg hover:bg-orange-600 flex items-center justify-center transition-colors"
      >
        <Plus size={28} />
      </button>

      {showAdd && (
        <AddLinkModal
          onClose={() => setShowAdd(false)}
          onCreated={() => { setShowAdd(false); fetchLinks(); }}
        />
      )}
    </div>
  );
}
