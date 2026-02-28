import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { community as communityApi, links as linksApi } from "../api";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faArrowLeft, faGlobe, faSearch, faXmark, faHeart as faHeartSolid } from "@fortawesome/free-solid-svg-icons";
import { faHeart as faHeartOutline } from "@fortawesome/free-regular-svg-icons";

function FolderCard({ folder, onOpen, onLike, liking }: any) {
  return (
    <div
      onClick={() => onOpen(folder)}
      className="bg-white rounded-2xl shadow-sm border border-gray-100 cursor-pointer hover:shadow-md transition-shadow overflow-hidden"
    >
      <div className="h-32 bg-gradient-to-br from-green-100 to-teal-50 relative">
        {folder.bannerUrl ? (
          <img src={folder.bannerUrl} alt="" className="w-full h-full object-cover" />
        ) : (
          <div className="w-full h-full flex items-center justify-center text-4xl opacity-50">
            {folder.aiGenerated ? "✨" : "🌍"}
          </div>
        )}
        <button
          onClick={(e) => { e.stopPropagation(); onLike(folder.id, folder.likedByMe); }}
          disabled={liking === folder.id}
          className="absolute top-2 right-2 flex items-center gap-1 bg-white/80 backdrop-blur-sm rounded-full px-2 py-1 text-xs"
        >
          <FontAwesomeIcon
            icon={folder.likedByMe ? faHeartSolid : faHeartOutline}
            className={`w-3 h-3 ${folder.likedByMe ? "text-red-500" : "text-gray-400"}`}
          />
          <span className={folder.likedByMe ? "text-red-500 font-medium" : "text-gray-500"}>
            {folder.likeCount || 0}
          </span>
        </button>
      </div>
      <div className="p-3">
        <p className="font-semibold text-gray-800 line-clamp-1">{folder.name}</p>
        <p className="text-xs text-gray-400 mt-0.5">
          {folder.ownerDisplayName && <span>par {folder.ownerDisplayName}</span>}
          {folder.linkCount > 0 && <span> · {folder.linkCount} idée{folder.linkCount > 1 ? "s" : ""}</span>}
        </p>
        {folder.tags && folder.tags.length > 0 && (
          <div className="flex gap-1 mt-1.5 flex-wrap">
            {folder.tags.slice(0, 2).map((tag: string, i: number) => (
              <span key={i} className="text-xs px-2 py-0.5 rounded-full bg-orange-50 text-orange-600">{tag}</span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export default function AllFolders() {
  const navigate = useNavigate();
  const isLoggedIn = !!localStorage.getItem("token");
  const [folders, setFolders] = useState<any[]>([]);
  const [search, setSearch] = useState("");
  const [liking, setLiking] = useState<string | null>(null);
  const [selectedFolder, setSelectedFolder] = useState<any | null>(null);
  const [folderLinks, setFolderLinks] = useState<any[]>([]);
  const [sortBy, setSortBy] = useState<"recent" | "popular">("popular");

  useEffect(() => {
    communityApi.list(undefined, 100).then((r) => setFolders(r.folders || [])).catch(() => {});
  }, []);

  const handleLike = async (folderId: string, isLiked: boolean) => {
    if (!isLoggedIn) { navigate("/login"); return; }
    if (liking) return;
    setLiking(folderId);
    try {
      const result = isLiked ? await communityApi.unlike(folderId) : await communityApi.like(folderId);
      const upd = (f: any) => f.id === folderId ? { ...f, likedByMe: !isLiked, likeCount: result.likeCount } : f;
      setFolders((p) => p.map(upd));
      if (selectedFolder?.id === folderId) {
        setSelectedFolder((prev: any) => ({ ...prev, likedByMe: !isLiked, likeCount: result.likeCount }));
      }
    } catch { /* silencieux */ }
    setLiking(null);
  };

  const openFolder = async (folder: any) => {
    setSelectedFolder(folder);
    try {
      const res = await linksApi.list(folder.id);
      setFolderLinks(res.links || []);
    } catch {
      setFolderLinks([]);
    }
  };

  const filtered = folders.filter((f) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return (
      f.name?.toLowerCase().includes(q) ||
      f.ownerDisplayName?.toLowerCase().includes(q) ||
      f.tags?.some((t: string) => t.toLowerCase().includes(q))
    );
  });

  const sorted = [...filtered].sort((a, b) => {
    if (sortBy === "popular") {
      return (b.likeCount || 0) - (a.likeCount || 0);
    }
    return (b.createdAt || 0) - (a.createdAt || 0);
  });

  // Vue détail d'un dossier
  if (selectedFolder) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-6">
        <button
          onClick={() => setSelectedFolder(null)}
          className="flex items-center gap-1 text-orange-500 mb-4 text-sm font-medium"
        >
          <FontAwesomeIcon icon={faArrowLeft} className="w-4 h-4" /> Retour
        </button>
        {selectedFolder.bannerUrl && (
          <div className="h-40 rounded-2xl overflow-hidden mb-4">
            <img src={selectedFolder.bannerUrl} alt="" className="w-full h-full object-cover" />
          </div>
        )}
        <div className="mb-4">
          <h2 className="text-xl font-bold text-gray-800">{selectedFolder.name}</h2>
          <div className="flex items-center gap-2 text-sm text-gray-400 mt-1">
            <FontAwesomeIcon icon={faGlobe} className="w-3.5 h-3.5 text-green-500" />
            {selectedFolder.ownerDisplayName && (
              <span>par {selectedFolder.ownerDisplayName}</span>
            )}
            {selectedFolder.linkCount > 0 && (
              <span>· {selectedFolder.linkCount} idée{selectedFolder.linkCount > 1 ? "s" : ""}</span>
            )}
          </div>
        </div>
        {folderLinks.length === 0 ? (
          <p className="text-center text-gray-400 py-12">Liste vide</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {folderLinks.map((link) => (
              <div
                key={link.id}
                onClick={() => navigate(`/links/${link.id}`)}
                className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4 cursor-pointer hover:shadow-md transition-shadow"
              >
                <p className="font-semibold text-gray-800 line-clamp-2">{link.title}</p>
                {link.description && (
                  <p className="text-xs text-gray-500 mt-1 line-clamp-2">{link.description}</p>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-6">
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate("/")}
          className="text-gray-400 hover:text-gray-600"
        >
          <FontAwesomeIcon icon={faArrowLeft} className="w-5 h-5" />
        </button>
        <div>
          <h1 className="text-2xl font-bold text-gray-800">🌍 Toutes les listes publiques</h1>
          <p className="text-sm text-gray-500">{folders.length} liste{folders.length > 1 ? "s" : ""} disponible{folders.length > 1 ? "s" : ""}</p>
        </div>
      </div>

      {/* Barre de recherche et tri */}
      <div className="flex gap-3 mb-6">
        <div className="relative flex-1">
          <FontAwesomeIcon icon={faSearch} className="absolute left-3 top-1/2 -translate-y-1/2 text-orange-400 w-4 h-4" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Rechercher une liste..."
            className="w-full pl-10 pr-10 py-2.5 rounded-full border border-orange-200 focus:border-orange-400 focus:outline-none bg-white text-sm"
          />
          {search && (
            <button onClick={() => setSearch("")} className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400">
              <FontAwesomeIcon icon={faXmark} className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
        <select
          value={sortBy}
          onChange={(e) => setSortBy(e.target.value as "recent" | "popular")}
          className="px-4 py-2.5 rounded-full border border-gray-200 focus:border-orange-400 focus:outline-none bg-white text-sm"
        >
          <option value="popular">Plus populaires</option>
          <option value="recent">Plus récentes</option>
        </select>
      </div>

      {/* Grille de listes */}
      {sorted.length === 0 ? (
        <div className="text-center py-20 text-gray-400">
          <FontAwesomeIcon icon={faGlobe} className="mx-auto mb-3 text-gray-300 w-12 h-12" />
          <p>{search ? `Aucune liste trouvée pour "${search}"` : "Aucune liste publique pour le moment"}</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {sorted.map((f) => (
            <FolderCard key={f.id} folder={f} onOpen={openFolder} onLike={handleLike} liking={liking} />
          ))}
        </div>
      )}
    </div>
  );
}

