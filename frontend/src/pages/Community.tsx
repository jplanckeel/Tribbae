import { useEffect, useState } from "react";
import { community, links as linksApi } from "../api";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSearch, faGlobe, faArrowLeft, faTimes } from "@fortawesome/free-solid-svg-icons";
import LinkCard from "../components/LinkCard";
import { useNavigate } from "react-router-dom";

export default function Community() {
  const [folders, setFolders] = useState<any[]>([]);
  const [search, setSearch] = useState("");
  const [nextToken, setNextToken] = useState("");
  const [loading, setLoading] = useState(false);
  const [selectedFolder, setSelectedFolder] = useState<any | null>(null);
  const [folderLinks, setFolderLinks] = useState<any[]>([]);
  const navigate = useNavigate();

  const fetchFolders = async (append = false) => {
    setLoading(true);
    try {
      const res = await community.list(search || undefined, 20, append ? nextToken : undefined);
      setFolders(append ? [...folders, ...(res.folders || [])] : (res.folders || []));
      setNextToken(res.nextPageToken || "");
    } catch {
      // ignore
    }
    setLoading(false);
  };

  useEffect(() => { fetchFolders(); }, []);

  const handleSearch = () => {
    setNextToken("");
    fetchFolders();
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

  if (selectedFolder) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-6">
        <button onClick={() => setSelectedFolder(null)} className="flex items-center gap-1 text-orange-500 mb-4">
          <FontAwesomeIcon icon={faArrowLeft} className="w-4 h-4" /> Communauté
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
              <LinkCard key={link.id} link={link} onClick={() => navigate(`/links/${link.id}`)} />
            ))}
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-6">
      <div className="flex items-center gap-2 mb-6">
        <FontAwesomeIcon icon={faGlobe} className="w-6 h-6 text-green-500" />
        <h2 className="text-xl font-bold text-gray-800">Communauté</h2>
      </div>

      {/* Barre de recherche */}
      <div className="relative mb-6">
        <FontAwesomeIcon icon={faSearch} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 w-4 h-4" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && handleSearch()}
          placeholder="Rechercher une liste communautaire..."
          className="w-full pl-10 pr-10 py-2.5 rounded-full border border-gray-200 focus:border-green-400 focus:outline-none bg-white text-sm"
        />
        {search && (
          <button onClick={() => { setSearch(""); setTimeout(fetchFolders, 0); }} className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400">
            <FontAwesomeIcon icon={faTimes} className="w-3.5 h-3.5" />
          </button>
        )}
      </div>

      {folders.length === 0 && !loading ? (
        <div className="text-center py-20 text-gray-400">
          <FontAwesomeIcon icon={faGlobe} className="mx-auto mb-4 text-gray-300 w-12 h-12" />
          <p>Aucune liste communautaire trouvée</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {folders.map((folder) => (
            <div
              key={folder.id}
              onClick={() => openFolder(folder)}
              className="bg-white rounded-2xl shadow-sm border border-gray-100 cursor-pointer hover:shadow-md transition-shadow overflow-hidden"
            >
              <div className="h-28 bg-gradient-to-br from-green-100 to-teal-50 relative">
                {folder.bannerUrl ? (
                  <img src={folder.bannerUrl} alt="" className="w-full h-full object-cover" />
                ) : (
                  <div className="w-full h-full flex items-center justify-center text-4xl opacity-50">
                    {folder.aiGenerated ? "✨" : "🌍"}
                  </div>
                )}
              </div>
              <div className="p-3">
                <p className="font-semibold text-gray-800 line-clamp-1">{folder.name}</p>
                <div className="flex items-center gap-2 text-xs text-gray-400 mt-0.5">
                  {folder.ownerDisplayName && <span>par {folder.ownerDisplayName}</span>}
                  {folder.linkCount > 0 && (
                    <span>· {folder.linkCount} idée{folder.linkCount > 1 ? "s" : ""}</span>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {nextToken && (
        <div className="text-center mt-6">
          <button
            onClick={() => fetchFolders(true)}
            disabled={loading}
            className="px-6 py-2 rounded-full bg-green-500 text-white text-sm font-medium hover:bg-green-600 disabled:opacity-50"
          >
            {loading ? "Chargement..." : "Voir plus"}
          </button>
        </div>
      )}
    </div>
  );
}
