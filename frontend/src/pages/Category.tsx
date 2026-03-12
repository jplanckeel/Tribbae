import { useEffect, useState } from "react";
import { useParams, useNavigate, Link } from "react-router-dom";
import { links as linksApi, community as communityApi } from "../api";
import LinkCard from "../components/LinkCard";
import SEOHead from "../components/SEOHead";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faArrowLeft, faUtensils, faGift, faRunning, faCalendar, faLightbulb } from "@fortawesome/free-solid-svg-icons";

const CATEGORY_INFO: Record<string, { name: string; icon: any; color: string; emoji: string; seoDesc: string }> = {
  RECETTE: { name: "Recettes", icon: faUtensils, color: "bg-green-500", emoji: "🍳", seoDesc: "Découvrez les meilleures recettes de famille partagées par la communauté Tribbae. Recettes faciles, rapides et gourmandes pour toute la famille." },
  CADEAU: { name: "Cadeaux", icon: faGift, color: "bg-orange-500", emoji: "🎁", seoDesc: "Trouvez l'inspiration pour vos cadeaux en famille. Idées cadeaux pour enfants, anniversaires, Noël et toutes les occasions." },
  ACTIVITE: { name: "Activités", icon: faRunning, color: "bg-blue-500", emoji: "🏃", seoDesc: "Idées d'activités en famille : sorties, loisirs créatifs, jeux, sport. Des idées pour tous les âges partagées par la communauté." },
  EVENEMENT: { name: "Événements", icon: faCalendar, color: "bg-red-500", emoji: "📅", seoDesc: "Organisez vos événements familiaux : anniversaires, sorties, fêtes. Trouvez l'inspiration et planifiez en famille." },
  IDEE: { name: "Idées", icon: faLightbulb, color: "bg-yellow-500", emoji: "💡", seoDesc: "Explorez des idées originales partagées par les familles. Inspiration pour le quotidien et les moments spéciaux." },
};

export default function Category() {
  const { category } = useParams<{ category: string }>();
  const navigate = useNavigate();
  const [allLinks, setAllLinks] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [likingLink, setLikingLink] = useState<string | null>(null);
  const isLoggedIn = !!localStorage.getItem("token");

  const categoryKey = category?.toUpperCase() || "IDEE";
  const categoryInfo = CATEGORY_INFO[categoryKey] || CATEGORY_INFO.IDEE;

  useEffect(() => {
    const fetchLinks = async () => {
      try {
        setLoading(true);
        // Endpoint public avec filtre catégorie côté backend
        const res = await communityApi.links(`LINK_CATEGORY_${categoryKey}`, 100);
        setAllLinks(res.links || []);
      } catch (err) {
        console.error("Error fetching links:", err);
      } finally {
        setLoading(false);
      }
    };

    fetchLinks();
  }, [category, categoryKey]);

  // Les liens sont déjà filtrés par catégorie côté backend
  const filteredLinks = allLinks;

  // Trier par likes décroissants pour le top
  const topLinks = [...filteredLinks].sort((a, b) => (b.likeCount || 0) - (a.likeCount || 0));

  // Nouveautés (les plus récents)
  const newLinks = [...filteredLinks].reverse().slice(0, 12);

  const handleLinkLike = async (linkId: string, isLiked: boolean) => {
    if (!isLoggedIn) { navigate("/login"); return; }
    if (likingLink) return;
    setLikingLink(linkId);
    try {
      const result = isLiked
        ? await linksApi.unlike(linkId)
        : await linksApi.like(linkId);

      // Mettre à jour l'état local
      setAllLinks(prev => prev.map(l =>
        l.id === linkId
          ? { ...l, likedByMe: !isLiked, likeCount: result.likeCount }
          : l
      ));
    } catch (err) {
      console.error("Error liking link:", err);
    }
    setLikingLink(null);
  };

  return (
    <main className="max-w-5xl mx-auto px-4 py-6">
      <SEOHead
        title={`${categoryInfo.emoji} ${categoryInfo.name} en famille — Idées et inspiration`}
        description={categoryInfo.seoDesc}
      />
      {/* BreadcrumbList JSON-LD */}
      <script type="application/ld+json" dangerouslySetInnerHTML={{
        __html: JSON.stringify({
          "@context": "https://schema.org",
          "@type": "BreadcrumbList",
          "itemListElement": [
            { "@type": "ListItem", "position": 1, "name": "Accueil", "item": "https://tribbae.bananaops.cloud/" },
            { "@type": "ListItem", "position": 2, "name": categoryInfo.name, "item": `https://tribbae.bananaops.cloud/category/${category}` }
          ]
        })
      }} />
      {/* Header */}
      <div className="mb-6">
        <Link to="/" className="inline-flex items-center gap-2 text-orange-500 hover:text-orange-600 mb-4 text-sm font-medium">
          <FontAwesomeIcon icon={faArrowLeft} className="w-4 h-4" />
          Retour à l'accueil
        </Link>

        <div className="flex items-center gap-4">
          <div className={`w-16 h-16 rounded-2xl ${categoryInfo.color} flex items-center justify-center text-3xl`}>
            {categoryInfo.emoji}
          </div>
          <div>
            <h1 className="text-3xl font-bold text-gray-800">{categoryInfo.name}</h1>
            <p className="text-gray-500 mt-1">{filteredLinks.length} idée{filteredLinks.length > 1 ? "s" : ""} partagée{filteredLinks.length > 1 ? "s" : ""}</p>
          </div>
        </div>
      </div>

      {loading ? (
        <div className="text-center py-20">
          <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-orange-500"></div>
          <p className="text-gray-500 mt-4">Chargement...</p>
        </div>
      ) : filteredLinks.length === 0 ? (
        <div className="text-center py-20">
          <p className="text-5xl mb-4">{categoryInfo.emoji}</p>
          <p className="text-gray-500">Aucune idée dans cette catégorie pour le moment</p>
        </div>
      ) : (
        <>
          {/* Top idées */}
          {topLinks.length > 0 && (
            <div className="mb-8">
              <h2 className="text-xl font-bold text-gray-800 mb-4">🔥 Top {categoryInfo.name}</h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {topLinks.slice(0, 6).map((link) => (
                  <LinkCard
                    key={link.id}
                    link={link}
                    onClick={() => navigate(`/links/${link.id}`)}
                    onLike={handleLinkLike}
                    liking={likingLink}
                  />
                ))}
              </div>
            </div>
          )}

          {/* Nouveautés */}
          {newLinks.length > 0 && (
            <div className="mb-8">
              <h2 className="text-xl font-bold text-gray-800 mb-4">✨ Nouveautés</h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {newLinks.map((link) => (
                  <LinkCard
                    key={link.id}
                    link={link}
                    onClick={() => navigate(`/links/${link.id}`)}
                    onLike={handleLinkLike}
                    liking={likingLink}
                  />
                ))}
              </div>
            </div>
          )}

          {/* Toutes les idées */}
          {filteredLinks.length > 18 && (
            <div>
              <h2 className="text-xl font-bold text-gray-800 mb-4">📋 Toutes les idées</h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {filteredLinks.map((link) => (
                  <LinkCard
                    key={link.id}
                    link={link}
                    onClick={() => navigate(`/links/${link.id}`)}
                    onLike={handleLinkLike}
                    liking={likingLink}
                  />
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </main>
  );
}
