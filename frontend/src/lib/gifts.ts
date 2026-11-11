import type { IconName } from "../components/Icon";

/**
 * « Offrir une idée » : liste de cadeaux partagée. Personne ne voit qui offre
 * quoi ; chacun voit si un cadeau est libre, réservé ou offert, et les siens.
 * Mêmes libellés et mêmes cas que data/Gifts.kt (GiftsTest).
 */
export type GiftStatus = "" | "reserved" | "bought" | "mine" | "mine_bought" | "pending";

export type GiftTone = "free" | "mine" | "taken" | "done";

/** Couleurs d'un état (design system « GiftReservation », maquette « Offrir une idée »). */
export const GIFT_TONE: Record<GiftTone, string> = {
  free: "bg-sable-2 text-sec",
  mine: "bg-prune text-soleil",
  taken: "bg-sable-2 text-sec",
  done: "bg-recette-bg text-recette-ink",
};

export interface GiftState {
  /** Pastille de la ligne */
  label: string;
  /** Bloc d'état de la fiche */
  long: string;
  sub: string;
  tone: GiftTone;
  icon: IconName;
  /** Peut encore être réservé */
  free: boolean;
  /** Réservé par moi (compte ou ce navigateur) */
  mine: boolean;
  /** Ligne estompée, titre barré pour un cadeau déjà offert par un autre */
  dim: boolean;
  strike: boolean;
}

export function giftState(status: GiftStatus | undefined): GiftState {
  switch (status) {
    case "mine":
      return { label: "Vous l'offrez", long: "Vous l'offrez", sub: "Pas encore acheté", tone: "mine", icon: "cadeau", free: false, mine: true, dim: false, strike: false };
    case "mine_bought":
      return { label: "Acheté par vous", long: "Acheté, il n'y a plus qu'à l'emballer", sub: "Les autres le voient comme offert", tone: "done", icon: "check", free: false, mine: true, dim: false, strike: false };
    case "pending":
      return { label: "À confirmer", long: "Confirmez dans votre e-mail", sub: "Sans confirmation sous 2 h, il redevient libre.", tone: "mine", icon: "mail", free: false, mine: true, dim: false, strike: false };
    case "reserved":
      return { label: "Réservé", long: "Quelqu'un s'en occupe", sub: "Personne ne sait qui, pas même la personne qui a fait la liste.", tone: "taken", icon: "lock", free: false, mine: false, dim: true, strike: false };
    case "bought":
      return { label: "Offert", long: "Déjà acheté", sub: "Rien à faire de ce côté", tone: "done", icon: "check", free: false, mine: false, dim: true, strike: true };
    default:
      return { label: "Libre", long: "Personne ne l'offre encore", sub: "Réservez-le pour qu'il ne soit pas offert deux fois.", tone: "free", icon: "plus", free: true, mine: false, dim: false, strike: false };
  }
}

export interface GiftProgress {
  done: number;
  taken: number;
  free: number;
  /** Largeurs de la barre, en % */
  pDone: number;
  pTaken: number;
  label: string;
  freeLabel: string;
}

const plural = (n: number, one: string, many: string) => `${n} ${n > 1 ? many : one}`;

/** Avancement de la liste : offerts (achetés), réservés, encore libres. */
export function giftProgress(statuses: GiftStatus[]): GiftProgress {
  const total = statuses.length;
  const done = statuses.filter((s) => s === "bought" || s === "mine_bought").length;
  const taken = statuses.filter((s) => s === "reserved" || s === "mine" || s === "pending").length;
  const free = total - done - taken;
  const pct = (n: number) => (total ? Math.round((n / total) * 100) : 0);
  return {
    done, taken, free, pDone: pct(done), pTaken: pct(taken),
    label: `${plural(done, "offert", "offerts")} · ${plural(taken, "réservé", "réservés")}`,
    freeLabel: plural(free, "encore libre", "encore libres"),
  };
}

export type GiftFilter = "all" | "free" | "mine";

export const GIFT_FILTERS: { key: GiftFilter; label: string }[] = [
  { key: "all", label: "Tout" },
  { key: "free", label: "À offrir" },
  { key: "mine", label: "Mes cadeaux" },
];

export function matchesGiftFilter(status: GiftStatus | undefined, filter: GiftFilter): boolean {
  const s = giftState(status);
  return filter === "all" || (filter === "free" ? s.free : s.mine);
}

/** Créatrice de la liste : seulement le nombre d'idées qui ont trouvé preneur. */
export function surpriseLabel(statuses: GiftStatus[]): string {
  const total = statuses.length;
  const taken = statuses.filter((s) => s !== "" && s !== undefined).length;
  if (!taken) return "Aucune idée n'a encore trouvé preneur.";
  return `${taken} ${taken > 1 ? "idées" : "idée"} sur ${total} ${taken > 1 ? "ont" : "a"} trouvé preneur.`;
}

/** Réservations faites sans compte depuis ce navigateur (en attente de l'e-mail). */
const PENDING_KEY = "tribbae.gifts.pending";

export function readPendingGifts(): Record<string, number> {
  try {
    const raw = JSON.parse(localStorage.getItem(PENDING_KEY) ?? "{}") as Record<string, number>;
    const now = Date.now();
    return Object.fromEntries(Object.entries(raw).filter(([, until]) => typeof until === "number" && until > now));
  } catch {
    return {};
  }
}

export function rememberPendingGift(linkId: string) {
  try {
    const all = readPendingGifts();
    all[linkId] = Date.now() + 2 * 60 * 60 * 1000;
    localStorage.setItem(PENDING_KEY, JSON.stringify(all));
  } catch {
    /* stockage indisponible : on montre « À confirmer » le temps de la page */
  }
}

/** Statut affiché sur la page du lien : celui du serveur, ou « À confirmer » pour ce navigateur. */
export function sharedGiftStatus(serverStatus: GiftStatus | undefined, pendingHere: boolean): GiftStatus {
  if (pendingHere && (serverStatus === "reserved" || !serverStatus)) return "pending";
  return serverStatus ?? "";
}
