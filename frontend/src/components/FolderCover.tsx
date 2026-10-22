import { folderColor, folderIconPath, type FolderColorMeta } from "../lib/categories";
import { coverPhoto, resolvedCover, type CoverKey } from "../lib/folderStyle";
import type { Folder, Link } from "../lib/types";
import { ILLUSTRATION_ART, type IllustrationKey } from "./FolderBannerArt";

/** Icône d'un dossier (jeu FOLDER_ICONS, trait viewBox 24). */
export function FolderGlyph({ icon, size = 22, className = "" }: { icon?: string; size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" focusable="false" className={`icon shrink-0 ${className}`}>
      <path d={folderIconPath(icon)} />
    </svg>
  );
}

// Tipis semés sur la couverture « Tipis » : x, y, taille, rotation (repère 350×110)
const TIPIS = [[18, 14, 26, -8], [78, 54, 20, 6], [140, 10, 30, 0], [206, 50, 22, -5], [262, 16, 26, 8], [320, 58, 20, 0], [40, 84, 18, 4], [300, 92, 16, -6]];

/** Dessin d'une bannière seul (vignettes du sélecteur). */
export function IllustrationArt({ illustration, color, className = "" }: { illustration: IllustrationKey; color: FolderColorMeta; className?: string }) {
  const it = ILLUSTRATION_ART[illustration];
  return (
    <svg viewBox="0 0 350 110" preserveAspectRatio="xMidYMid slice" aria-hidden="true" className={`block h-full w-full ${className}`}>
      {it.art(color)}
    </svg>
  );
}

/**
 * Fond d'une couverture de dossier, qui remplit son conteneur : dessin, uni,
 * tipis, pois, rayures ou photo (sans photo disponible : tipis).
 */
export function CoverArt({ cover, illustration, color, photo }: {
  cover: CoverKey;
  illustration: IllustrationKey | null;
  color: FolderColorMeta;
  photo?: string;
}) {
  if (cover === "illu" && illustration) return <IllustrationArt illustration={illustration} color={color} className="absolute inset-0" />;
  if (cover === "photo" && photo) {
    return <img src={photo} alt="" loading="lazy" referrerPolicy="no-referrer" className="absolute inset-0 h-full w-full object-cover" />;
  }
  const pattern =
    cover === "pois" ? { backgroundImage: `radial-gradient(${color.mid} 2.6px, transparent 3.2px)`, backgroundSize: "16px 16px" }
    : cover === "rayures" ? { backgroundImage: `repeating-linear-gradient(135deg, ${color.mid} 0 7px, transparent 7px 16px)` }
    : {};
  return (
    <div aria-hidden="true" className="absolute inset-0 transition-colors duration-500" style={{ backgroundColor: color.bg, ...pattern }}>
      {(cover === "tipis" || cover === "photo") && (
        <svg viewBox="0 0 350 110" preserveAspectRatio="xMidYMid slice" className="block h-full w-full">
          {TIPIS.map(([x, y, s, r], i) => (
            <g key={i} transform={`translate(${x} ${y}) rotate(${r} ${s / 2} ${s / 2}) scale(${s / 24})`}>
              <path d="M12 2L22 20H2Z" fill={color.mid} />
              <path d="M12 11l4.5 9h-9z" fill={color.bg} />
            </g>
          ))}
        </svg>
      )}
    </div>
  );
}

/** Couverture effective d'un dossier (choisie, ou dessin auto selon le nom / la date clé). */
export function FolderCover({ folder, links = [] }: { folder: Folder; links?: Link[] }) {
  const { cover, illustration } = resolvedCover(folder);
  return <CoverArt cover={cover} illustration={illustration} color={folderColor(folder.color)} photo={cover === "photo" ? coverPhoto(folder, links) : undefined} />;
}
