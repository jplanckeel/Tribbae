import { useEffect, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { links as linksApi, folders as foldersApi, children as childrenApi, community as communityApi } from "../api";
import LinkCard from "../components/LinkCard";
import FilterBar from "../components/FilterBar";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faPlus, faWandMagicSparkles, faGlobe, faLightbulb, faHeart as faHeartSolid, faArrowLeft, faStar, faClock, faSearch, faXmark } from "@fortawesome/free-solid-svg-icons";
import { faHeart as faHeartOutline } from "@fortawesome/free-regular-svg-icons";
import AddLinkModal from "../components/AddLinkModal";
import AiGenerateModal from "../components/AiGenerateModal";

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

function HorizontalLinkRow({ title, items, onLike, likingLink, navigate, viewAllPath }: any) {
  if (!items || items.length === 0) return null;
  return (
    <div className="mb-8">
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-lg font-bold text-gray-800 flex items-center gap-2">{title}</h2>
        {viewAllPath && (
          <Link to={viewAllPath} className="text-sm text-orange-500 hover:text-orange-600 font-medium">
            Voir tout →
          </Link>
        )}
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {items.slice(0, 4).map((link: any) => (
          <LinkCard key={link.id} link={link} onClick={() => navigate(`/links/${link.id}`)} onLike={onLike} liking={likingLink} />
        ))}
      </div>
    </div>
  );
}

function parseAgeMonths(ageRange: string): number {
  const lower = ageRange.toLowerCase();
  const isMois = lower.includes("mois");
  const nums = [...lower.matchAll(/\d+/g)].map((m) => parseInt(m[0]));
  const max = Math.max(...nums);
  if (!isFinite(max)) return Infinity;
  return isMois ? max : max * 12;
}

type Tab = "discover" | "my-ideas";

export default function Home() {
  const isLoggedIn = !!localStorage.getItem("token");
  const [tab, setTab] = useState<Tab>("discover");
  const [allLinks, setAllLinks] = useState<any[]>([]);
  const [folderList, setFolderList] = useState<any[]>([]);
  const [childList, setChildList] = useState<any[]>([]);
  const [search, setSearch] = useState("");
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null);
  const [selectedChildId, setSelectedChildId] = useState<string | null>(null);
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const [showAdd, setShowAdd] = useState(false);
  const [showAi, setShowAi] = useState(false);
  const [topFolders, setTopFolders] = useState<any[]>([]);
  const [communityFolders, setCommunityFolders] = useState<any[]>([]);
  const [selectedCommunityFolder, setSelectedCommunityFolder] = useState<any | null>(null);
  const [communityFolderLinks, setCommunityFolderLinks] = useState<any[]>([]);
  const [liking, setLiking] = useState<string | null>(null);
  const [likingLink, setLikingLink] = useState<string | null>(null);
  const [communityLinks, setCommunityLinks] = useState<any[]>([]);
  const [newLinks, setNewLinks] = useState<any[]>([]);
  const [discoverSearch, setDiscoverSearch] = useState("");
  const navigate = useNavigate();

  const fetchLinks = async () => {
    try {
      const res = await linksApi.list();
      setAllLinks(res.links || []);
    } catch (err: any) {
      if (err?.status === 401 || err?.status === 403) navigate("/login");
    }
  };

  useEffect(() => {
    communityApi.top(12).then((r) => setTopFolders(r.folders || [])).catch(() => {});
    communityApi.list(undefined, 30).then((r) => setCommunityFolders(r.folders || [])).catch(() => {});
    communityApi.links().then((r) => setCommunityLinks(r.links || [])).catch(() => {});
    communityApi.newLinks(12).then((r) => setNewLinks(r.links || [])).catch(() => {});
    if (isLoggedIn) {
      fetchLinks();
      foldersApi.list().then((r) => setFolderList(r.folders || [])).catch(() => {});
      childrenApi.list().then((r) => setChildList(r.children || [])).catch(() => {});
    }
  }, []);

  const updateLinkInState = (linkId: string, isLiked: boolean, likeCount: number) => {
    const upd = (l: any) => l.id === linkId ? { ...l, likedByMe: !isLiked, likeCount } : l;
    setCommunityFolderLinks((p) => p.map(upd));
    setAllLinks((p) => p.map(upd));
    setCommunityLinks((p) => p.map(upd));
    setNewLinks((p) => p.map(upd));
  };

  const handleLike = async (folderId: string, isLiked: boolean) => {
    if (!isLoggedIn) { navigate("/login"); return; }
    if (liking) return;
    setLiking(folderId);
    try {
      const result = isLiked ? await communityApi.unlike(folderId) : await communityApi.like(folderId);
      const upd = (f: any) => f.id === folderId ? { ...f, likedByMe: !isLiked, likeCount: result.likeCount } : f;
      setTopFolders((p) => p.map(upd));
      setCommunityFolders((p) => p.map(upd));
    } catch { /* silencieux */ }
    setLiking(null);
  };

  const handleLinkLike = async (linkId: string, isLiked: boolean) => {
    if (!isLoggedIn) { navigate("/login"); return; }
    if (likingLink) return;
    setLikingLink(linkId);
    try {
      const result = isLiked ? await linksApi.unlike(linkId) : await linksApi.like(linkId);
      updateLinkInState(linkId, isLiked, result.likeCount);
    } catch { /* silencieux */ }
    setLikingLink(null);
  };

  const openCommunityFolder = async (folder: any) => {
    setSelectedCommunityFolder(folder);
    const cached = communityLinks.filter((l: any) => l.folderId === folder.id);
    if (cached.length > 0) {
      setCommunityFolderLinks(cached);
    } else {
      try {
        const res = await linksApi.list(folder.id);
        setCommunityFolderLinks(res.links || []);
      } catch {
        setCommunityFolderLinks([]);
      }
    }
  };

  const childAgeMonths = selectedChildId
    ? (() => {
        const child = childList.find((c) => c.id === selectedChildId);
        if (!child) return null;
        return Math.floor((Date.now() - child.birthDate) / (1000 * 60 * 60 * 24 * 30.44));
      })()
    : null;

  const filtered = allLinks.filter((l) => {
    if (selectedCategory && l.category !== `LINK_CATEGORY_${selectedCategory.toUpperCase()}`) return false;
    if (selectedFolderId && l.folderId !== selectedFolderId) return false;
    if (favoritesOnly && !l.favorite) return false;
    if (childAgeMonths !== null && l.ageRange && parseAgeMonths(l.ageRange) < childAgeMonths) return false;
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

  const CATEGORIES = [
    { key: "LINK_CATEGORY_RECETTE", path: "recette", emoji: "🍳", label: "Recettes" },
    { key: "LINK_CATEGORY_CADEAU", path: "cadeau", emoji: "🎁", label: "Cadeaux" },
    { key: "LINK_CATEGORY_ACTIVITE", path: "activite", emoji: "🏃", label: "Activités" },
    { key: "LINK_CATEGORY_EVENEMENT", path: "evenement", emoji: "📅", label: "Événements" },
    { key: "LINK_CATEGORY_IDEE", path: "idee", emoji: "💡", label: "Idées" },
  ];

  // Vue détail d'un dossier public
  if (selectedCommunityFolder) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-6">
        <button
          onClick={() => setSelectedCommunityFolder(null)}
          className="flex items-center gap-1 text-orange-500 mb-4 text-sm font-medium"
        >
          <FontAwesomeIcon icon={faArrowLeft} className="w-4 h-4" /> Retour
        </button>
        {selectedCommunityFolder.bannerUrl && (
          <div className="h-40 rounded-2xl overflow-hidden mb-4">
            <img src={selectedCommunityFolder.bannerUrl} alt="" className="w-full h-full object-cover" />
          </div>
        )}
        <div className="mb-4">
          <h2 className="text-xl font-bold text-gray-800">{selectedCommunityFolder.name}</h2>
          <div className="flex items-center gap-2 text-sm text-gray-400 mt-1">
            <FontAwesomeIcon icon={faGlobe} className="w-3.5 h-3.5 text-green-500" />
            {selectedCommunityFolder.ownerDisplayName && (
              <span>par {selectedCommunityFolder.ownerDisplayName}</span>
            )}
            {selectedCommunityFolder.linkCount > 0 && (
              <span>· {selectedCommunityFolder.linkCount} idée{selectedCommunityFolder.linkCount > 1 ? "s" : ""}</span>
            )}
          </div>
        </div>
        {communityFolderLinks.length === 0 ? (
          <p className="text-center text-gray-400 py-12">Liste vide</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {communityFolderLinks.map((link) => (
              <LinkCard
                key={link.id}
                link={link}
                onClick={() => navigate(`/links/${link.id}`)}
                onLike={handleLinkLike}
                liking={likingLink}
              />
            ))}
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-6">
      {/* Onglets */}
      <div className="flex gap-1 mb-6 bg-gray-100 rounded-full p-1 w-fit">
        <button
          onClick={() => setTab("discover")}
          className={`flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-medium transition-colors ${
            tab === "discover" ? "bg-white text-orange-500 shadow-sm" : "text-gray-500 hover:text-gray-700"
          }`}
        >
          <FontAwesomeIcon icon={faGlobe} className="w-3.5 h-3.5" /> Découvrir
        </button>
        <button
          onClick={() => setTab("my-ideas")}
          className={`flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-medium transition-colors ${
            tab === "my-ideas" ? "bg-white text-orange-500 shadow-sm" : "text-gray-500 hover:text-gray-700"
          }`}
        >
          <FontAwesomeIcon icon={faLightbulb} className="w-3.5 h-3.5" /> Mes idées
        </button>
      </div>

      {tab === "discover" ? (
        <>
          {/* Barre de recherche */}
          <div className="relative mb-6">
            <FontAwesomeIcon icon={faSearch} className="absolute left-3 top-1/2 -translate-y-1/2 text-orange-400 w-4 h-4" />
            <input
              type="text"
              value={discoverSearch}
              onChange={(e) => setDiscoverSearch(e.target.value)}
              placeholder="Rechercher une idée, une liste..."
              className="w-full pl-10 pr-10 py-2.5 rounded-full border border-orange-200 focus:border-orange-400 focus:outline-none bg-white text-sm"
            />
            {discoverSearch && (
              <button onClick={() => setDiscoverSearch("")} className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400">
                <FontAwesomeIcon icon={faXmark} className="w-3.5 h-3.5" />
              </button>
            )}
          </div>

          {/* Résultats de recherche ou contenu normal */}
          {discoverSearch ? (() => {
            const q = discoverSearch.toLowerCase();
            const matchedLinks = communityLinks.filter((l) =>
              l.title?.toLowerCase().includes(q) ||
              l.description?.toLowerCase().includes(q) ||
              l.location?.toLowerCase().includes(q) ||
              l.tags?.some((t: string) => t.toLowerCase().includes(q))
            );
            const matchedFolders = communityFolders.filter((f) =>
              f.name?.toLowerCase().includes(q) ||
              f.tags?.some((t: string) => t.toLowerCase().includes(q))
            );
            return (
              <>
                {matchedLinks.length > 0 && (
                  <div className="mb-8">
                    <h2 className="text-lg font-bold text-gray-800 mb-3">🔍 Idées trouvées ({matchedLinks.length})</h2>
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                      {matchedLinks.map((link) => (
                        <LinkCard key={link.id} link={link} onClick={() => navigate(`/links/${link.id}`)} onLike={handleLinkLike} liking={likingLink} />
                      ))}
                    </div>
                  </div>
                )}
                {matchedFolders.length > 0 && (
                  <div className="mb-8">
                    <h2 className="text-lg font-bold text-gray-800 mb-3">📁 Listes trouvées ({matchedFolders.length})</h2>
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                      {matchedFolders.map((f) => (
                        <FolderCard key={f.id} folder={f} onOpen={openCommunityFolder} onLike={handleLike} liking={liking} />
                      ))}
                    </div>
                  </div>
                )}
                {matchedLinks.length === 0 && matchedFolders.length === 0 && (
                  <div className="text-center py-20 text-gray-400">
                    <p className="text-5xl mb-4">🔍</p>
                    <p>Aucun résultat pour "{discoverSearch}"</p>
                  </div>
                )}
              </>
            );
          })() : (
          <>
          {/* Catégories */}
          <div className="mb-8">
            <h2 className="text-lg font-bold text-gray-800 mb-3">🎯 Explorer par catégorie</h2>
            <div className="grid grid-cols-5 gap-3">
              {CATEGORIES.map(({ path, emoji, label }) => (
                <Link
                  key={path}
                  to={`/category/${path}`}
                  className="bg-white rounded-2xl shadow-sm border border-gray-100 p-4 hover:shadow-md transition-shadow text-center group"
                >
                  <div className="text-3xl mb-1.5 group-hover:scale-110 transition-transform">{emoji}</div>
                  <p className="font-semibold text-gray-800 text-sm">{label}</p>
                </Link>
              ))}
            </div>
          </div>

          {/* Nouveautés */}
          <HorizontalLinkRow
            title={<><FontAwesomeIcon icon={faClock} className="text-blue-400" /> Nouveautés</>}
            items={newLinks}
            onLike={handleLinkLike}
            likingLink={likingLink}
            navigate={navigate}
          />

          {/* Tops par catégorie */}
          {CATEGORIES.map(({ key, emoji, label, path }) => {
            const items = communityLinks
              .filter((l) => l.category === key)
              .sort((a, b) => (b.likeCount || 0) - (a.likeCount || 0))
              .slice(0, 8);
            return (
              <HorizontalLinkRow
                key={key}
                title={<><FontAwesomeIcon icon={faStar} className="text-yellow-400" /> {emoji} {label}</>}
                items={items}
                onLike={handleLinkLike}
                likingLink={likingLink}
                navigate={navigate}
                viewAllPath={`/category/${path}`}
              />
            );
          })}

          {/* Listes populaires */}
          {topFolders.length > 0 && (
            <div className="mb-8">
              <h2 className="text-lg font-bold text-gray-800 mb-3">🔥 Listes populaires</h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                {topFolders.slice(0, 4).map((f) => (
                  <div
                    key={f.id}
                    onClick={() => openCommunityFolder(f)}
                    className="bg-white rounded-2xl shadow-sm border border-gray-100 cursor-pointer hover:shadow-md transition-shadow overflow-hidden"
                  >
                    <div className="h-28 bg-gradient-to-br from-orange-200 to-amber-100 relative">
                      {f.bannerUrl ? (
                        <img src={f.bannerUrl} alt="" className="w-full h-full object-cover" />
                      ) : (
                        <div className="w-full h-full flex items-center justify-center text-4xl opacity-60">
                          {f.aiGenerated ? "✨" : "📋"}
                        </div>
                      )}
                      <button
                        onClick={(e) => { e.stopPropagation(); handleLike(f.id, f.likedByMe); }}
                        disabled={liking === f.id}
                        className="absolute top-2 right-2 flex items-center gap-1 bg-white/80 backdrop-blur-sm rounded-full px-2 py-1 text-xs"
                      >
                        <FontAwesomeIcon
                          icon={f.likedByMe ? faHeartSolid : faHeartOutline}
                          className={`w-3 h-3 ${f.likedByMe ? "text-red-500" : "text-gray-400"}`}
                        />
                        <span className={f.likedByMe ? "text-red-500 font-medium" : "text-gray-500"}>
                          {f.likeCount || 0}
                        </span>
                      </button>
                    </div>
                    <div className="p-3">
                      <p className="text-sm font-semibold text-gray-800 line-clamp-1">{f.name}</p>
                      <p className="text-xs text-gray-400 mt-0.5">
                        par {f.ownerDisplayName || "Anonyme"} · {f.linkCount || 0} idées
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Toutes les listes publiques */}
          <div>
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-lg font-bold text-gray-800">🌍 Toutes les listes publiques</h2>
              {communityFolders.length > 6 && (
                <Link to="/discover" className="text-sm text-orange-500 hover:text-orange-600 font-medium">
                  Voir tout →
                </Link>
              )}
            </div>
            {communityFolders.length === 0 ? (
              <div className="text-center py-12 text-gray-400">
                <FontAwesomeIcon icon={faGlobe} className="mx-auto mb-3 text-gray-300 w-10 h-10" />
                <p>Aucune liste publique pour le moment</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {communityFolders.slice(0, 6).map((f) => (
                  <FolderCard key={f.id} folder={f} onOpen={openCommunityFolder} onLike={handleLike} liking={liking} />
                ))}
              </div>
            )}
          </div>
          </>
          )}
        </>
      ) : (
        <>
          {!isLoggedIn ? (
            <div className="text-center py-20">
              <p className="text-5xl mb-4">🔒</p>
              <p className="text-gray-500 mb-4">Connectez-vous pour voir et gérer vos idées</p>
              <button
                onClick={() => navigate("/login")}
                className="px-6 py-2.5 rounded-full bg-orange-500 text-white font-medium hover:bg-orange-600 transition-colors"
              >
                Se connecter
              </button>
            </div>
          ) : (
            <>
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
                    <LinkCard
                      key={link.id}
                      link={link}
                      onClick={() => navigate(`/links/${link.id}`)}
                      onLike={handleLinkLike}
                      liking={likingLink}
                    />
                  ))}
                </div>
              )}
            </>
          )}
        </>
      )}

      {isLoggedIn && (
        <>
          <button
            onClick={() => setShowAi(true)}
            className="fixed bottom-6 right-24 w-14 h-14 rounded-full bg-purple-500 text-white shadow-lg hover:bg-purple-600 flex items-center justify-center transition-colors group"
            title="Générer avec l'IA (Expérimental)"
          >
            <FontAwesomeIcon icon={faWandMagicSparkles} className="w-6 h-6" />
            <span className="absolute -top-1 -right-1 bg-orange-500 text-white text-[9px] font-bold px-1.5 py-0.5 rounded-full">
              EXP
            </span>
          </button>
          <button
            onClick={() => setShowAdd(true)}
            className="fixed bottom-6 right-6 w-14 h-14 rounded-full bg-orange-500 text-white shadow-lg hover:bg-orange-600 flex items-center justify-center transition-colors"
          >
            <FontAwesomeIcon icon={faPlus} className="w-7 h-7" />
          </button>
        </>
      )}

      {showAdd && (
        <AddLinkModal onClose={() => setShowAdd(false)} onCreated={() => { setShowAdd(false); fetchLinks(); }} />
      )}
      {showAi && (
        <AiGenerateModal onClose={() => setShowAi(false)} onCreated={() => { setShowAi(false); fetchLinks(); }} />
      )}
    </div>
  );
}
