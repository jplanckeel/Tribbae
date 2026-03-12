import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { links as linksApi } from "../api";
import LinkCard from "../components/LinkCard";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faTag, faArrowLeft } from "@fortawesome/free-solid-svg-icons";

export default function Tags() {
  const [allLinks, setAllLinks] = useState<any[]>([]);
  const [selectedTag, setSelectedTag] = useState<string | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    linksApi.list().then((res) => setAllLinks(res.links || []));
  }, []);

  // Extraire tous les tags uniques avec leur count
  const tagCounts: Record<string, number> = {};
  allLinks.forEach((l) =>
    l.tags?.forEach((t: string) => {
      tagCounts[t] = (tagCounts[t] || 0) + 1;
    })
  );
  const sortedTags = Object.entries(tagCounts).sort((a, b) => b[1] - a[1]);

  const filteredLinks = selectedTag
    ? allLinks.filter((l) => l.tags?.includes(selectedTag))
    : [];

  if (selectedTag) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-6">
        <button
          onClick={() => setSelectedTag(null)}
          className="flex items-center gap-1 text-orange-500 mb-4"
        >
          <FontAwesomeIcon icon={faArrowLeft} className="w-4 h-4" /> Tous les tags
        </button>
        <h2 className="text-xl font-bold text-gray-800 mb-4">
          #{selectedTag}
          <span className="text-sm font-normal text-gray-400 ml-2">
            {filteredLinks.length} idée{filteredLinks.length > 1 ? "s" : ""}
          </span>
        </h2>
        {filteredLinks.length === 0 ? (
          <p className="text-center text-gray-400 py-12">Aucun résultat</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredLinks.map((link) => (
              <LinkCard
                key={link.id}
                link={link}
                onClick={() => navigate(`/links/${link.id}`)}
              />
            ))}
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-6">
      <h2 className="text-xl font-bold text-gray-800 mb-4">Tags</h2>
      {sortedTags.length === 0 ? (
        <p className="text-center text-gray-400 py-12">Aucun tag</p>
      ) : (
        <div className="flex flex-wrap gap-2">
          {sortedTags.map(([tag, count]) => (
            <button
              key={tag}
              onClick={() => setSelectedTag(tag)}
              className="flex items-center gap-1.5 px-4 py-2 rounded-full bg-white shadow-sm hover:shadow text-sm font-medium text-gray-700 hover:text-orange-500 transition-colors"
            >
              <FontAwesomeIcon icon={faTag} className="w-3.5 h-3.5 text-orange-400" />
              {tag}
              <span className="text-xs text-gray-400 ml-1">({count})</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
