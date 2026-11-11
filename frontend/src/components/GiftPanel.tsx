import { useState } from "react";
import * as api from "../lib/api";
import { GIFT_TONE, giftState, type GiftStatus } from "../lib/gifts";
import { Claimed } from "./GiftList";
import { useFeedback } from "./feedback";
import Icon from "./Icon";
import Sheet from "./Sheet";
import { Button } from "./ui";

/**
 * Fiche d'une idée Cadeau d'une liste de cadeaux, dans l'app. La famille de la
 * liste voit seulement « Réservé » ou « Offert » ; les autres proches peuvent
 * dire « Je l'offre », « C'est acheté » ou libérer le cadeau.
 */
export default function GiftPanel({ linkId, title, status, canOffer, onStatus }: {
  linkId: string; title: string; status: GiftStatus; canOffer: boolean; onStatus: (s: GiftStatus) => void;
}) {
  const { toast, confirm } = useFeedback();
  const [busy, setBusy] = useState(false);
  const [claimed, setClaimed] = useState(false);
  const s = giftState(status);

  const act = async (action: "reserve" | "bought" | "unbought" | "release") => {
    if (action === "release" && !(await confirm({
      title: "Libérer ce cadeau ?",
      text: `« ${title} » redeviendra libre et les autres offreurs de la liste en seront prévenus. Personne ne saura que c'était vous.`,
      confirmLabel: "Libérer",
    }))) return;
    setBusy(true);
    try {
      const g = await api.gifts.mine(linkId, action);
      const next: GiftStatus = g.status === "bought" ? "mine_bought" : g.status === "reserved" ? "mine" : "";
      onStatus(next);
      if (action === "reserve") setClaimed(true);
      else toast(action === "release" ? "Cadeau libéré" : action === "bought" ? "Noté : c'est acheté" : "C'est noté");
    } catch (e) {
      toast((e as Error).message, "error");
    } finally {
      setBusy(false);
    }
  };

  return (
    <section aria-label="Offrir cette idée" className="flex flex-col gap-2.5">
      <div role="status" className={`flex items-center gap-3 rounded-2xl px-3.5 py-3 ${GIFT_TONE[s.tone]}`}>
        <Icon name={s.icon} size={22} />
        <span className="flex flex-1 flex-col gap-px">
          <span className="text-[15px] font-bold">{s.long}</span>
          <span className="text-[13px] opacity-85">{canOffer ? s.sub : "Liste de cadeaux : personne ne voit qui offre quoi."}</span>
        </span>
      </div>
      {canOffer && s.free && <Button icon="cadeau" loading={busy} onClick={() => act("reserve")} block>Je l'offre</Button>}
      {canOffer && status === "mine" && (
        <div className="flex flex-col gap-1">
          <Button variant="dark" icon="check" loading={busy} onClick={() => act("bought")} block>C'est acheté</Button>
          <button type="button" onClick={() => act("release")} className="h-10 text-sm font-semibold text-sortie-ink">Libérer ce cadeau</button>
        </div>
      )}
      {canOffer && status === "mine_bought" && (
        <button type="button" onClick={() => act("unbought")} className="h-10 text-sm font-semibold text-link">Finalement, pas encore acheté</button>
      )}
      {claimed && (
        <Sheet open onClose={() => setClaimed(false)} title="Cadeau réservé">
          <Claimed title={title} onClose={() => setClaimed(false)} />
        </Sheet>
      )}
    </section>
  );
}
