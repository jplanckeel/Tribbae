import { useMyData } from "../lib/data";
import type { Link } from "../lib/types";
import { AUDIENCE_LABEL, ideaAudience, type Audience } from "../lib/visibility";
import Icon, { type IconName } from "./Icon";

const STYLES: Record<Audience, { icon: IconName; colors: string }> = {
  private: { icon: "lock", colors: "bg-vis-private-bg text-vis-private-ink" },
  shared: { icon: "people", colors: "bg-vis-shared-bg text-vis-shared-ink" },
  public: { icon: "globe", colors: "bg-vis-public-bg text-vis-public-ink" },
};

/** Badge de visibilité (fiche, dossier épinglé) ou pastille ronde `iconOnly` (cartes). */
export default function VisibilityBadge({ audience, iconOnly, className = "" }: { audience: Audience; iconOnly?: boolean; className?: string }) {
  const { icon, colors } = STYLES[audience];
  const label = AUDIENCE_LABEL[audience];
  return (
    <span className={`inline-flex h-[26px] shrink-0 items-center rounded-full text-xs font-bold whitespace-nowrap ${colors} ${iconOnly ? "w-[26px] justify-center ring-2 ring-card" : "gap-[5px] pr-[9px] pl-[7px]"} ${className}`}>
      <Icon name={icon} size={14} />
      <span className={iconOnly ? "sr-only" : undefined}>{label}</span>
    </span>
  );
}

/** Pastille d'une carte idée : rien pour une idée Privée, pour ne pas charger les listes. */
export function IdeaVisibility({ link, className }: { link: Link; className?: string }) {
  const { folders, peers } = useMyData();
  const audience = ideaAudience(link, folders.find((f) => f.id === link.folderId), peers);
  return audience === "private" ? null : <VisibilityBadge audience={audience} iconOnly className={className} />;
}
