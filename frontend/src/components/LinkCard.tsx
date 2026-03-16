import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faMapMarkerAlt, faStar, faHeart as faHeartSolid } from "@fortawesome/free-solid-svg-icons";
import { faHeart as faHeartOutline } from "@fortawesome/free-regular-svg-icons";
import { CATEGORIES, CATEGORY_COLORS, normalizeCategory } from "../types";
import AdminBadge from "./AdminBadge";

interface Props {
  link: any;
  onClick?: () => void;
  onLike?: (linkId: string, isLiked: boolean) => void;
  liking?: string | null;
}

function extractCity(location: string): string {
  const parts = location.split(",").map((s) => s.trim());
  if (parts.length <= 1) return location;
  return /\d/.test(parts[0]) && parts.length > 1 ? parts[1] : parts[0];
}

function catLabel(value: string) {
  return CATEGORIES.find((c) => c.value === value)?.label ?? "Idée";
}
function catIcon(value: string) {
  return CATEGORIES.find((c) => c.value === value)?.icon ?? "💡";
}

export default function LinkCard({ link, onClick, onLike, liking }: Props) {
  const category = normalizeCategory(link.category);
  const color = CATEGORY_COLORS[category] || "#FF8C00";
  const hasImage = link.imageUrl && link.imageUrl.length > 0;
  const isLoggedIn = !!localStorage.getItem("token");

  return (
    <div
      onClick={onClick}
      className="bg-white rounded-2xl shadow-sm overflow-hidden cursor-pointer group hover:shadow-md transition-all duration-200 active:scale-[0.98]"
    >
      {/* Image / Pattern */}
      <div className="relative h-36 overflow-hidden">
        {hasImage ? (
          <img
            src={link.imageUrl}
            alt={link.title}
            className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center" style={{ backgroundColor: color + "18" }}>
            <div className="absolute inset-0 flex flex-col justify-evenly opacity-15">
              {[0, 1, 2].map((row) => (
                <div key={row} className="flex justify-evenly" style={{ marginLeft: row % 2 === 1 ? 16 : 0 }}>
                  {Array.from({ length: 10 }).map((_, i) => (
                    <span key={i} className="text-lg">{catIcon(category)}</span>
                  ))}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Gradient overlay */}
        <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-transparent" />

        {/* Category badge */}
        <div
          className="absolute top-2.5 right-2.5 flex items-center gap-1 px-2 py-1 rounded-full text-white text-[11px] font-semibold"
          style={{ backgroundColor: color }}
        >
          <span>{catIcon(category)}</span>
          <span>{catLabel(category)}</span>
        </div>

        {/* Like button */}
        {isLoggedIn && onLike && (
          <button
            onClick={(e) => { e.stopPropagation(); onLike(link.id, link.likedByMe); }}
            disabled={liking === link.id}
            className="absolute top-2.5 left-2.5 flex items-center gap-1 bg-white/85 backdrop-blur-sm rounded-full px-2 py-1 text-xs transition-all hover:bg-white"
          >
            <FontAwesomeIcon
              icon={link.likedByMe ? faHeartSolid : faHeartOutline}
              className={`w-3 h-3 ${link.likedByMe ? "text-red-500" : "text-gray-400"}`}
            />
            <span className={`font-medium ${link.likedByMe ? "text-red-500" : "text-gray-500"}`}>
              {link.likeCount || 0}
            </span>
          </button>
        )}

        {/* Rating overlay */}
        {link.rating > 0 && (
          <div className="absolute bottom-2 right-2.5 flex items-center gap-0.5 bg-white/90 rounded-full px-1.5 py-0.5">
            <FontAwesomeIcon icon={faStar} className="w-2.5 h-2.5 text-yellow-400" />
            <span className="text-[10px] font-bold text-gray-800">{link.rating}</span>
          </div>
        )}
      </div>

      {/* Content */}
      <div className="p-3">
        <div className="flex items-start gap-1.5 mb-1">
          <h3 className="font-semibold text-gray-800 text-sm leading-snug line-clamp-2 flex-1">{link.title}</h3>
          {link.ownerIsAdmin && <div className="flex-shrink-0 mt-0.5"><AdminBadge /></div>}
        </div>

        {link.description && (
          <p className="text-gray-400 text-xs line-clamp-2 mb-2">{link.description}</p>
        )}

        <div className="flex flex-wrap items-center gap-2 text-xs text-gray-400">
          {link.ownerDisplayName && (
            <span className="truncate max-w-[80px]">👤 {link.ownerDisplayName}</span>
          )}
          {link.location && (
            <span className="flex items-center gap-0.5">
              <FontAwesomeIcon icon={faMapMarkerAlt} className="w-2.5 h-2.5" />
              {extractCity(link.location)}
            </span>
          )}
          {link.price && <span className="text-green-600 font-medium">💰 {link.price}</span>}
        </div>

        {link.tags?.length > 0 && (
          <div className="flex gap-1 mt-2 flex-wrap">
            {link.tags.slice(0, 3).map((tag: string) => (
              <span
                key={tag}
                className="text-[11px] px-2 py-0.5 rounded-full font-medium"
                style={{ backgroundColor: color + "18", color }}
              >
                #{tag}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
