import { GIFT_TONE, giftState, type GiftStatus } from "../lib/gifts";
import Icon from "./Icon";

/** Pastille d'état d'un cadeau : jamais qui l'offre. */
export default function GiftTag({ status, className = "" }: { status: GiftStatus | undefined; className?: string }) {
  const s = giftState(status);
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-lg px-2 py-[3px] text-xs font-bold ${GIFT_TONE[s.tone]} ${className}`}>
      <Icon name={s.icon} size={12} />
      {s.label}
    </span>
  );
}
