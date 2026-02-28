import { useState } from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faHeart as faHeartSolid, faStar } from "@fortawesome/free-solid-svg-icons";
import { faHeart as faHeartOutline } from "@fortawesome/free-regular-svg-icons";
import { community as communityApi } from "../api";

interface Props {
  folders: any[];
  onRefresh: () => void;
}

export default function TopFolders({ folders, onRefresh }: Props) {
  const [liking, setLiking] = useState<string | null>(null);

  const handleLike = async (folderId: string, isLiked: boolean) => {
    if (liking) return;
    setLiking(folderId);
    try {
      if (isLiked) {
        await communityApi.unlike(folderId);
      } else {
        await communityApi.like(folderId);
      }
      onRefresh();
    } catch { /* silencieux */ }
    setLiking(null);
  };

  return (
    <div className="mb-6">
      <div className="flex items-center gap-2 mb-3">
        <FontAwesomeIcon icon={faStar} className="text-yellow-500 w-4 h-4" />
        <h2 className="text-sm font-semibold text-gray-600">Listes populaires</h2>
      </div>
      <div className="flex gap-3 overflow-x-auto pb-2 -mx-1 px-1">
        {folders.map((f) => (
          <div
            key={f.id}
            className="flex-shrink-0 w-52 bg-white rounded-2xl p-3 shadow-sm border-2 border-gray-100 hover:border-orange-400 hover:shadow-md transition-all duration-300 cursor-pointer group"
          >
            <div className="flex items-start justify-between mb-1">
              <span className="text-sm font-medium text-gray-800 line-clamp-2 flex-1">{f.name}</span>
              {f.aiGenerated && <span className="text-xs ml-1 flex-shrink-0">✨</span>}
            </div>
            <p className="text-xs text-gray-400 mb-2">
              par {f.ownerDisplayName || "Anonyme"} · {f.linkCount || 0} idées
            </p>
            <div className="flex items-center justify-between">
              <button
                onClick={() => handleLike(f.id, f.likedByMe)}
                disabled={liking === f.id}
                className="flex items-center gap-1 text-xs transition-colors"
              >
                <FontAwesomeIcon
                  icon={f.likedByMe ? faHeartSolid : faHeartOutline}
                  className={`w-3.5 h-3.5 ${f.likedByMe ? "text-red-500" : "text-gray-400"}`}
                />
                <span className={f.likedByMe ? "text-red-500 font-medium" : "text-gray-400"}>
                  {f.likeCount || 0}
                </span>
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
