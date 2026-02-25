import { MapPin, Star, ExternalLink } from "lucide-react";
import { CATEGORIES, CATEGORY_COLORS } from "../types";

interface Props {
  link: any;
  onClick?: () => void;
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

export default function LinkCard({ link, onClick }: Props) {
  const color = CATEGORY_COLORS[link.category] || "#FF8C00";
  const hasImage = link.imageUrl && link.imageUrl.length > 0;

  return (
    <div
      onClick={onClick}
      className="bg-white rounded-2xl shadow-sm hover:shadow-md transition-shadow cursor-pointer overflow-hidden"
    >
      {hasImage ? (
        <div className="relative h-36">
          <img
            src={link.imageUrl}
            alt={link.title}
            className="w-full h-full object-cover"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent" />
          <span
            className="absolute top-2 right-2 text-white text-xs font-medium px-2 py-1 rounded-lg"
            style={{ backgroundColor: color }}
          >
            {catIcon(link.category)} {catLabel(link.category)}
          </span>
        </div>
      ) : (
        <div
          className="h-24 flex items-center justify-center relative overflow-hidden"
          style={{ backgroundColor: color + "18" }}
        >
          {/* Pattern d'icônes */}
          <div className="absolute inset-0 flex flex-col justify-evenly opacity-20">
            {[0, 1, 2].map((row) => (
              <div
                key={row}
                className="flex justify-evenly"
                style={{ marginLeft: row % 2 === 1 ? 16 : 0 }}
              >
                {Array.from({ length: 10 }).map((_, i) => (
                  <span key={i} className="text-lg rotate-45">
                    {catIcon(link.category)}
                  </span>
                ))}
              </div>
            ))}
          </div>
          <span
            className="absolute top-2 right-2 text-white text-xs font-medium px-2 py-1 rounded-lg z-10"
            style={{ backgroundColor: color }}
          >
            {catIcon(link.category)} {catLabel(link.category)}
          </span>
        </div>
      )}

      <div className="p-4">
        <h3 className="font-semibold text-gray-800 truncate">{link.title}</h3>
        {link.description && (
          <p className="text-gray-500 text-sm mt-1 line-clamp-2">
            {link.description}
          </p>
        )}
        <div className="flex flex-wrap items-center gap-3 mt-2 text-xs text-gray-400">
          {link.location && (
            <span className="flex items-center gap-1">
              <MapPin size={12} /> {extractCity(link.location)}
            </span>
          )}
          {link.price && <span>💰 {link.price}</span>}
          {link.rating > 0 && (
            <span className="flex items-center gap-0.5">
              {Array.from({ length: link.rating }).map((_, i) => (
                <Star key={i} size={12} fill="#FFD700" stroke="#FFD700" />
              ))}
            </span>
          )}
          {link.url && (
            <a
              href={link.url}
              target="_blank"
              rel="noopener noreferrer"
              onClick={(e) => e.stopPropagation()}
              className="flex items-center gap-1 text-orange-500 hover:underline"
            >
              <ExternalLink size={12} /> Lien
            </a>
          )}
        </div>
        {link.tags?.length > 0 && (
          <div className="flex gap-1 mt-2 flex-wrap">
            {link.tags.slice(0, 4).map((tag: string) => (
              <span
                key={tag}
                className="text-xs px-2 py-0.5 rounded-full"
                style={{ backgroundColor: color + "20", color }}
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
