import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { links as linksApi, community as communityApi } from "../api";
import { CATEGORY_COLORS } from "../types";
import LinkCard from "../components/LinkCard";
import SEOHead from "../components/SEOHead";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faArrowLeft, faSearch, faTimes, faFire, faClock } from "@fortawesome/free-solid-svg-icons";

const CATEGORY_INFO: Record<string, { name: string; color: string; emoji: string; catKey: string; seoDesc: string }> = {
  RECETTE:    { name: "Recettes",    color: "#81C784", emoji: "🍳", catKey: "LINK_CATEGORY_RECETTE",    seoDesc: "Découvrez les meilleures recettes de famille partagées par la communauté Tribbae." },
  CADEAU:     { name: "Cadeaux",     color: "#FF8C00", emoji: "🎁", catKey: "LINK_CATEGORY_CADEAU",     seoDesc: "Trouvez l'inspiration pour vos cadeaux en famille." },
  ACTIVITE:   { name: "Activités",   color: "#4FC3F7", emoji: "🏃", catKey: "LINK_CATEGORY_ACTIVITE",   seoDesc: "Idées d'activités en famille : sorties, loisirs créatifs, jeux, sport." },
  EVENEMENT:  { name: "Événements",  color: "#FF7043", emoji: "📅", catKey: "LINK_CATEGORY_EVENEMENT",  seoDesc: "Organisez vos événements familiaux : anniversaires, sorties, fêtes." },
  IDEE:       { name: "Idées",       color: "#FFD700", emoji: "💡", catKey: "LINK_CATEGORY_IDEE",       seoDesc: "Explorez des idées originales partagées par les familles." },
  LIVRE:      { name: "Livres",      color: "#9C27B0", emoji: "📚", catKey: "LINK_CATEGORY_LIVRE",      seoDesc: "Découvrez les livres recommandés par les familles Tribbae." },
  DECORATION: { name: "Décorations", color: "#E91E63", emoji: "🎨", catKey: "LINK_CATEGORY_DECORATION", seoDesc: "Idées de décoration partagées par la communauté Tribbae." },
};

export default function Category() {
  const { category } = useParams<{ category: string }>();
  const navigate = useNavigate();
  const [allLinks, setAllLinks] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [likingLink, setLikingLink] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [activeTab, setActiveTab] = useState<"top" | "new">("top");
  const isLoggedIn = !!localStorage.getItem("token");

  const categoryKey = category?.toUpperCase() || "IDEE";
  const info = CATEGORY_INFO[categoryKey] || CATEGORY_INFO.IDEE;
  const color = CATEGORY_COLORS[info.catKey] || info.color;

  useEffect(() => {
    setLoading(true);
    communityApi.links(info.catKey, 100)
      .then((res) => setAllLinks(res.links || []))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [category]);

  const handleLinkLike = async (linkId: string, isLiked: boolean) => {
    if (!isLoggedIn) { navigate("/login"); return; }
    if (likingLink) return;
    setLikingLink(linkId);
    try {
      const result = isLiked ? await linksApi.unlike(linkId) : await linksApi.like(linkId);
      setAllLinks((prev) => prev.map((l) => l.id === linkId ? { ...l, likedByMe: !isLiked, likeCount: result.likeCount } : l));
    } catch { /* silencieux */ }
    setLikingLink(null);
  };

  const filtered = allLinks.filter((l) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return l.title?.toLowerCase().includes(q) || l.description?.toLowerCase().includes(q) || l.tags?.some((t: string) => t.toLowerCase().includes(q));
  });

  const topLinks = [...filtered].sort((a, b) => (b.likeCount || 0) - (a.likeCount || 0));
  const newLinks = [...filtered].sort((a, b) => (b.createdAt || "").localeCompare(a.createdAt || ""));
  const displayLinks = activeTab === "top" ? topLinks : newLinks;

  return (
    <div className="min-h-screen bg-gray-50 pb-20 sm:pb-8">
      <SEOHead
        title={`${info.emoji} ${info.name} en famille — Idées et inspiration`}
        description={info.seoDesc}
      />

      {/* Hero */}
      <div className="relative h-48 sm:h-56" style={{ background: `linear-gradient(135deg, ${color}, ${color}CC)` }}>
        <div className="absolute inset-0 flex flex-col justify-evenly opacity-10">
          {[0, 1, 2].map((row) => (
            <div key={row} className="flex justify-evenly" style={{ marginLeft: row % 2 === 1 ? 20 : 0 }}>
              {Array.from({ length: 14 }).map((_, i) => (
                <span key={i} className="text-2xl">{info.emoji}</span>
              ))}
            </div>
          ))}
        </div>

        <button
          onClick={() => navigate(-1)}
          className="absolute top-12 left-4 w-10 h-10 rounded-full bg-black/20 backdrop-blur-sm flex items-center justify-center"
        >
          <FontAwesomeIcon icon={faArrowLeft} className="w-4 h-4 text-white" />
        </button>

        <div className="absolute bottom-6 left-5">
          <p className="text-4xl mb-1">{info.emoji}</p>
          <h1 className="text-2xl font-bold text-white">{info.name}</h1>
          <p className="text-white/80 text-sm mt-0.5">{allLinks.length} idée{allLinks.length > 1 ? "s" : ""}</p>
        </div>
      </div>

      {/* White card */}
      <div className="bg-white rounded-t-3xl -mt-5 relative z-10 px-4 pt-5 pb-4">

        {/* Search */}
        <div className="relative mb-4">
          <FontAwesomeIcon icon={faSearch} className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={`Rechercher dans ${info.name}...`}
            className="w-full pl-10 pr-10 py-2.5 rounded-2xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm bg-gray-50 focus:bg-white transition-colors"
          />
          {search && (
            <button onClick={() => setSearch("")} className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400">
              <FontAwesomeIcon icon={faTimes} className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        {/* Tabs */}
        <div className="flex gap-2 mb-4">
          <button
            onClick={() => setActiveTab("top")}
            className={`flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-medium transition-colors ${
              activeTab === "top" ? "text-white" : "bg-gray-100 text-gray-500"
            }`}
            style={activeTab === "top" ? { backgroundColor: color } : {}}
          >
            <FontAwesomeIcon icon={faFire} className="w-3.5 h-3.5" /> Top
          </button>
          <button
            onClick={() => setActiveTab("new")}
            className={`flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-medium transition-colors ${
              activeTab === "new" ? "text-white" : "bg-gray-100 text-gray-500"
            }`}
            style={activeTab === "new" ? { backgroundColor: color } : {}}
          >
            <FontAwesomeIcon icon={faClock} className="w-3.5 h-3.5" /> Nouveautés
          </button>
          <span className="ml-auto text-sm text-gray-400 self-center">
            {filtered.length} résultat{filtered.length > 1 ? "s" : ""}
          </span>
        </div>
      </div>

      {/* Content */}
      <div className="px-4 pb-4">
        {loading ? (
          <div className="flex justify-center py-20">
            <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-orange-500" />
          </div>
        ) : displayLinks.length === 0 ? (
          <div className="text-center py-20">
            <p className="text-5xl mb-3">{search ? "🔍" : info.emoji}</p>
            <p className="text-gray-500 text-sm">
              {search ? `Aucun résultat pour "${search}"` : "Aucune idée dans cette catégorie"}
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {displayLinks.map((link) => (
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
    </div>
  );
}
