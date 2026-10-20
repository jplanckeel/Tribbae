import { useState } from "react";
import { Link as RouterLink } from "react-router";
import * as api from "../lib/api";
import { AVATAR_COLORS, categoryMeta, folderColor, folderIcon, type CategoryMeta } from "../lib/categories";
import { dayBadge, eventDateMs, metaLine, plural, safeImage } from "../lib/format";
import { useSession } from "../lib/session";
import type { Folder, Link } from "../lib/types";
import Icon from "./Icon";
import { Avatar } from "./ui";

/** « par Camille » pour l'idée d'un autre compte (tribu, communauté), sinon "". */
function useAuthor(link: Link): string {
  const session = useSession();
  return link.ownerDisplayName && link.ownerId !== session?.userId ? `par ${link.ownerDisplayName}` : "";
}

/** Visuel d'une idée : photo si disponible, sinon fond pastel + icône de catégorie. */
export function IdeaVisual({ link, height, iconSize = 46, className = "" }: {
  link: Link; height: number; iconSize?: number; className?: string;
}) {
  const cat = categoryMeta(link.category);
  const img = safeImage(link.imageUrl);
  const [broken, setBroken] = useState(false);
  return (
    <div className={`relative flex items-center justify-center overflow-hidden ${cat.bg} ${cat.ic} ${className}`} style={{ height }}>
      {img && !broken ? (
        <img
          src={img}
          alt=""
          loading="lazy"
          referrerPolicy="no-referrer"
          onError={() => setBroken(true)}
          className="absolute inset-0 h-full w-full object-cover"
        />
      ) : (
        <Icon name={cat.icon} size={iconSize} className="opacity-55" />
      )}
    </div>
  );
}

function CategoryBadge({ cat }: { cat: CategoryMeta }) {
  return (
    <span className={`absolute top-2.5 left-2.5 rounded-full bg-white px-2.5 py-1 text-xs font-semibold ${cat.ic}`}>
      {cat.label}
    </span>
  );
}

/** Bouton « J'aime » posé sur une carte. Gère l'état optimiste. */
export function LikeButton({ link, className = "", onRequireAuth }: {
  link: Link; className?: string; onRequireAuth?: () => void;
}) {
  const session = useSession();
  const [liked, setLiked] = useState(!!link.likedByMe);
  const [count, setCount] = useState(link.likeCount ?? 0);
  const [busy, setBusy] = useState(false);

  const toggle = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!session) return onRequireAuth?.();
    if (busy) return;
    setBusy(true);
    const next = !liked;
    setLiked(next);
    setCount((c) => c + (next ? 1 : -1));
    try {
      const r = next ? await api.links.like(link.id) : await api.links.unlike(link.id);
      if (typeof r?.likeCount === "number") setCount(r.likeCount);
    } catch {
      setLiked(!next);
      setCount((c) => c + (next ? -1 : 1));
    } finally {
      setBusy(false);
    }
  };

  return (
    <button
      type="button"
      onClick={toggle}
      aria-pressed={liked}
      aria-label={liked ? `Je n'aime plus (${count})` : `J'aime (${count})`}
      className={`flex h-[34px] w-[34px] items-center justify-center rounded-full bg-white/90 text-terracotta backdrop-blur-sm transition-transform active:scale-90 ${className}`}
    >
      <Icon name="heart" size={16} filled={liked} />
    </button>
  );
}

/** Carte « rail » de l'accueil (210 px). */
export function RailCard({ link }: { link: Link }) {
  const cat = categoryMeta(link.category);
  const author = useAuthor(link);
  const meta = [metaLine(link), author].filter(Boolean).join(" · ");
  return (
    <RouterLink
      to={`/links/${link.id}`}
      className="w-[210px] shrink-0 snap-start overflow-hidden rounded-large bg-white shadow-card transition-transform hover:-translate-y-0.5"
    >
      <div className="relative">
        <IdeaVisual link={link} height={120} />
        <CategoryBadge cat={cat} />
      </div>
      <div className="flex flex-col gap-1 px-3.5 pt-3 pb-3.5">
        <span className="line-clamp-2 font-display text-base leading-tight font-bold">{link.title}</span>
        {meta && <span className="text-[13px] text-sec">{meta}</span>}
      </div>
    </RouterLink>
  );
}

// Hauteurs variées pour l'effet mosaïque de l'Explorer
const MOSAIC = [150, 110, 100, 120, 140, 96];

/** Carte de la mosaïque (Explorer, favoris, dossiers). */
export function GridCard({ link, index = 0, onRequireAuth }: { link: Link; index?: number; onRequireAuth?: () => void }) {
  const cat = categoryMeta(link.category);
  const meta = metaLine(link) || cat.label;
  const author = useAuthor(link);
  return (
    <RouterLink
      to={`/links/${link.id}`}
      className="mb-3 block break-inside-avoid overflow-hidden rounded-[20px] bg-white shadow-card transition-transform hover:-translate-y-0.5"
    >
      <div className="relative">
        <IdeaVisual link={link} height={MOSAIC[index % MOSAIC.length]} iconSize={40} />
        <LikeButton link={link} onRequireAuth={onRequireAuth} className="absolute top-2 right-2" />
      </div>
      <div className="flex flex-col gap-1 px-3 pt-2.5 pb-3">
        <span className="line-clamp-3 font-display text-[15px] leading-tight font-bold">{link.title}</span>
        <span className="text-xs text-sec">{meta}</span>
        {author && <span className="truncate text-xs text-sec">{author}</span>}
        {(link.likeCount ?? 0) > 0 && (
          <span className="flex items-center gap-1 text-xs text-sec">
            <Icon name="heart" size={13} />
            {link.likeCount}
          </span>
        )}
      </div>
    </RouterLink>
  );
}

/** Ligne compacte (listes, résultats). */
export function RowCard({ link, trailing }: { link: Link; trailing?: React.ReactNode }) {
  const cat = categoryMeta(link.category);
  const meta = metaLine(link);
  const author = useAuthor(link);
  return (
    <RouterLink to={`/links/${link.id}`} className="flex items-center gap-3 rounded-[18px] bg-white p-2.5 pr-3.5 shadow-card">
      <IdeaVisual link={link} height={56} iconSize={24} className="w-14 shrink-0 rounded-[14px]" />
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="truncate text-[15px] font-bold">{link.title}</span>
        <span className="truncate text-[13px] text-sec">{[cat.label, meta, author].filter(Boolean).join(" · ")}</span>
      </span>
      {trailing ?? <Icon name="chevronRight" size={18} className="text-muted" />}
    </RouterLink>
  );
}

/** Tuile de catégorie arrondie (accueil). */
export function CategoryTile({ cat, count, to }: { cat: CategoryMeta; count?: number; to: string }) {
  return (
    <RouterLink to={to} className="group flex w-[76px] shrink-0 snap-start flex-col items-center gap-1.5">
      <span className={`flex h-16 w-16 items-center justify-center rounded-large transition-transform group-hover:-translate-y-0.5 ${cat.bg} ${cat.ic}`}>
        <Icon name={cat.icon} size={28} />
      </span>
      <span className="text-[13px] font-semibold">{cat.plural}</span>
      {count !== undefined && <span className="-mt-1.5 text-xs text-muted">{count}</span>}
    </RouterLink>
  );
}

/** Pastilles d'avatars des collaborateurs d'un dossier. */
export function AvatarStack({ names, label }: { names: string[]; label?: string }) {
  return (
    <span className="flex items-center">
      {names.slice(0, 3).map((n, i) => {
        const [bg, ink] = AVATAR_COLORS[i % AVATAR_COLORS.length];
        return <Avatar key={i} name={n} color={bg} ink={ink} className={`border-2 border-white ${i ? "-ml-2" : ""}`} />;
      })}
      {label && <span className="ml-1.5 text-xs text-sec">{label}</span>}
    </span>
  );
}

/** Carte dossier (Mes idées, communauté). */
export function FolderCard({ folder, count, showOwner }: { folder: Folder; count?: number; showOwner?: boolean }) {
  const color = folderColor(folder.color);
  const n = count ?? folder.linkCount ?? 0;
  const collaborators = folder.collaborators ?? [];
  const vis = folder.visibility;
  return (
    <RouterLink
      to={`/dossiers/${folder.id}`}
      className="relative flex flex-col gap-3.5 overflow-hidden rounded-large bg-white p-3.5 shadow-card transition-transform hover:-translate-y-0.5"
    >
      <span aria-hidden="true" className="absolute -top-3.5 -right-3.5 h-16 w-16 rounded-full" style={{ background: color.bg }} />
      <span className="relative flex h-11 w-11 items-center justify-center rounded-[14px] text-white" style={{ background: color.solid }}>
        <Icon name={folderIcon(folder.icon)} size={22} />
      </span>
      <span className="flex min-w-0 flex-col gap-0.5">
        <span className="line-clamp-2 font-display text-base leading-tight font-bold">{folder.name}</span>
        <span className="text-[13px] text-sec">
          {plural(n, "idée", "idées")}
          {showOwner && folder.ownerDisplayName ? ` · ${folder.ownerDisplayName}` : ""}
        </span>
      </span>
      <span className="mt-auto flex flex-wrap items-center gap-1.5">
        {collaborators.length > 0 ? (
          <AvatarStack names={[folder.ownerDisplayName || "Moi", ...collaborators.map((c) => c.displayName || c.email || "?")]} label="partagé" />
        ) : folder.aiGenerated ? (
          <span className="flex items-center gap-1 rounded-lg bg-prune px-2 py-1 text-xs font-semibold text-sable">
            <Icon name="sparkle" size={12} />IA
          </span>
        ) : vis === "VISIBILITY_PUBLIC" ? (
          <span className="flex items-center gap-1 rounded-lg bg-activite-bg px-2 py-1 text-xs font-semibold text-activite-ink">
            <Icon name="globe" size={12} />Public
          </span>
        ) : showOwner ? (
          (folder.likeCount ?? 0) > 0 && (
            <span className="flex items-center gap-1 text-xs text-sec"><Icon name="heart" size={13} />{folder.likeCount}</span>
          )
        ) : (
          <span className="rounded-lg bg-sable-2 px-2 py-1 text-xs font-semibold text-sec">Privé</span>
        )}
      </span>
    </RouterLink>
  );
}

/**
 * Carte d'idée de la grille bureau (maquette « Web · Explorer ») : grand
 * visuel, pastille catégorie (et date d'une sortie), titre, âge · prix · j'aime.
 */
export function IdeaCard({ link, onRequireAuth }: { link: Link; onRequireAuth?: () => void }) {
  const cat = categoryMeta(link.category);
  const at = eventDateMs(link.eventDate);
  const badge = at ? `${cat.label} · ${dayBadge(at).weekday.toLowerCase()} ${dayBadge(at).day}` : cat.label;
  const likes = link.likeCount ?? 0;
  const author = useAuthor(link);
  const meta = [metaLine(link), author, likes > 0 ? `${likes} j'aime` : ""].filter(Boolean).join(" · ");
  return (
    <RouterLink
      to={`/links/${link.id}`}
      className="group overflow-hidden rounded-[22px] bg-white shadow-card transition-transform hover:-translate-y-0.5"
    >
      <div className="relative">
        <IdeaVisual link={link} height={160} iconSize={56} />
        <span className={`absolute top-3 left-3 rounded-full bg-white px-2.5 py-[5px] text-xs font-bold ${cat.ink}`}>{badge}</span>
        <LikeButton link={link} onRequireAuth={onRequireAuth} className="absolute top-2.5 right-2.5" />
      </div>
      <div className="flex flex-col gap-1.5 px-4 pt-3.5 pb-4">
        <span className="line-clamp-2 font-display text-[17px] leading-[1.2] font-bold">{link.title}</span>
        {meta && <span className="text-sm text-sec">{meta}</span>}
      </div>
    </RouterLink>
  );
}

/**
 * Carte « liste » d'un dossier public (maquette « Les listes qui plaisent aux
 * tribus ») : bandeau coloré (ou image du dossier), nom, auteur · idées · j'aime.
 */
export function ListCard({ folder }: { folder: Folder }) {
  const color = folderColor(folder.color);
  const banner = safeImage(folder.bannerUrl);
  const n = folder.linkCount ?? 0;
  const likes = folder.likeCount ?? 0;
  const meta = [
    folder.ownerDisplayName ? `par ${folder.ownerDisplayName}` : "",
    plural(n, "idée", "idées"),
    likes > 0 ? `${likes} j'aime` : "",
  ].filter(Boolean).join(" · ");
  return (
    <RouterLink
      to={`/dossiers/${folder.id}`}
      className="overflow-hidden rounded-[24px] bg-white shadow-card transition-transform hover:-translate-y-0.5"
    >
      <div className="relative h-[130px] overflow-hidden" style={{ background: color.bg }}>
        {banner ? (
          <img src={banner} alt="" loading="lazy" referrerPolicy="no-referrer" className="absolute inset-0 h-full w-full object-cover" />
        ) : (
          <>
            <span aria-hidden="true" className="absolute -right-[30px] -bottom-[60px] h-40 w-40 rounded-full opacity-20" style={{ background: color.solid }} />
            <span className="absolute bottom-[18px] left-[22px]" style={{ color: color.solid }}>
              <Icon name={folderIcon(folder.icon)} size={54} />
            </span>
          </>
        )}
        {folder.aiGenerated && (
          <span className="absolute top-3 left-3 flex items-center gap-1 rounded-lg bg-prune px-2 py-1 text-xs font-semibold text-sable">
            <Icon name="sparkle" size={12} />IA
          </span>
        )}
      </div>
      <div className="flex flex-col gap-1.5 px-[18px] pt-4 pb-[18px]">
        <span className="line-clamp-2 font-display text-[19px] leading-tight font-bold">{folder.name}</span>
        <span className="text-sm text-sec">{meta}</span>
      </div>
    </RouterLink>
  );
}

/** Carte prune « l'IA prépare une liste » glissée dans les grilles. */
export function AiCard({ title, text, onClick, className = "" }: { title: string; text?: string; onClick: () => void; className?: string }) {
  return (
    <div className={`flex flex-col justify-between gap-3 rounded-[24px] bg-prune p-[22px] text-sable ${className}`}>
      <Icon name="sparkles" size={30} className="text-soleil" />
      <span className="font-display text-[21px] leading-[1.2] font-bold">{title}</span>
      {text && <span className="text-sm text-prune-soft">{text}</span>}
      <button
        type="button"
        onClick={onClick}
        className="self-start rounded-[14px] bg-soleil px-4 py-2.5 text-sm font-bold text-ecorce transition-colors hover:bg-white"
      >
        Essayer
      </button>
    </div>
  );
}

/** Rail horizontal défilant (déborde à droite comme sur la maquette). */
export function Rail({ children }: { children: React.ReactNode }) {
  return (
    <div
      className="no-scrollbar -mx-5 flex snap-x gap-3 overflow-x-auto scroll-px-5 px-5 pb-2 md:-mx-6 md:scroll-px-6 md:px-6"
    >
      {children}
    </div>
  );
}
