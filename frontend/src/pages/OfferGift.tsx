import { useState } from "react";
import { Link as RouterLink } from "react-router";
import { useFeedback } from "../components/feedback";
import Icon from "../components/Icon";
import Logo from "../components/Logo";
import Seo from "../components/Seo";
import { Button, EmptyState, ErrorNote, PageSpinner } from "../components/ui";
import * as api from "../lib/api";
import { GIFT_TONE, giftState, type GiftStatus } from "../lib/gifts";
import type { GiftReservation } from "../lib/types";
import { useAsync } from "../lib/useAsync";

/** Jeton du lien reçu par e-mail, après le # : jamais envoyé aux statistiques. */
function readToken(): string {
  return decodeURIComponent(window.location.hash.replace(/^#/, ""));
}

// Ouvrir le lien confirme la réservation, une fois par chargement (StrictMode rejoue l'effet)
const confirmations = new Map<string, Promise<GiftReservation>>();
function confirmOnce(token: string) {
  let p = confirmations.get(token);
  if (!p) {
    p = api.gifts.manage(token, "confirm");
    confirmations.set(token, p);
  }
  return p;
}

const asStatus = (s?: string): GiftStatus => (s === "bought" ? "mine_bought" : s === "reserved" ? "mine" : s === "pending" ? "pending" : "");

/** « Vous offrez… » : page ouverte depuis l'e-mail d'un proche qui a réservé sans compte. */
export default function OfferGift() {
  const { toast } = useFeedback();
  const [token] = useState(readToken);
  const first = useAsync(() => (token ? confirmOnce(token) : Promise.reject(new Error("no token"))), [token]);
  const [gift, setGift] = useState<GiftReservation | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [releasing, setReleasing] = useState(false);
  const current = gift ?? first.data;

  if (first.loading) return <PageSpinner />;
  if (!current) {
    return (
      <div className="mx-auto max-w-md px-5 pt-16">
        <Seo title="Offrir une idée · Tribbae" noindex />
        <EmptyState icon="cadeau" title="Ce lien n'est plus valable"
          text="La réservation a été libérée, ou n'a pas été confirmée à temps. Rouvrez la liste pour choisir un cadeau."
          action={<RouterLink to="/" className="font-bold text-terracotta">Découvrir Tribbae</RouterLink>} />
      </div>
    );
  }

  const status = asStatus(current.status);
  const s = giftState(status);
  const released = !current.status;
  const act = async (action: "bought" | "unbought" | "release", message: string) => {
    setBusy(true);
    setError("");
    try {
      setGift(await api.gifts.manage(token, action));
      setReleasing(false);
      toast(message);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col gap-4 px-5 pt-5 pb-12">
      <Seo title="Offrir une idée · Tribbae" noindex />
      <RouterLink to="/" aria-label="Tribbae"><Logo /></RouterLink>
      <span className="mt-2 text-xs font-bold tracking-[.6px] text-deco-ink uppercase">{current.folderName ? `Liste « ${current.folderName} »` : "Liste de cadeaux"}</span>
      <h1 className="font-display text-[28px] leading-[1.05] font-extrabold tracking-[-0.6px]">
        {released ? "Cadeau libéré" : `Vous offrez « ${current.title} »`}
      </h1>
      {released ? (
        <p className="text-[15px] text-ecorce-2">« {current.title} » est de nouveau libre. Les autres offreurs de la liste sont prévenus, sans savoir que c'était vous.</p>
      ) : (
        <>
          <div role="status" className={`flex items-center gap-3 rounded-2xl px-3.5 py-3 ${GIFT_TONE[s.tone]}`}>
            <Icon name={s.icon} size={22} />
            <span className="flex flex-1 flex-col gap-px"><span className="text-[15px] font-bold">{s.long}</span><span className="text-[13px] opacity-85">Personne ne voit votre nom, ni sur la liste ni ailleurs.</span></span>
          </div>
          {error && <ErrorNote>{error}</ErrorNote>}
          {status === "mine" && !releasing && (
            <div className="flex flex-col gap-2">
              <Button variant="dark" icon="check" loading={busy} onClick={() => act("bought", "Noté : c'est acheté")} block>C'est acheté</Button>
              <button type="button" onClick={() => setReleasing(true)} className="h-10 text-sm font-semibold text-sortie-ink">Libérer ce cadeau</button>
            </div>
          )}
          {status === "mine_bought" && (
            <button type="button" onClick={() => act("unbought", "C'est noté")} className="h-10 text-sm font-semibold text-link">Finalement, pas encore acheté</button>
          )}
          {releasing && (
            <div className="flex flex-col gap-3 rounded-2xl bg-card p-4">
              <span className="font-display text-xl font-extrabold">Libérer ce cadeau ?</span>
              <p className="text-sm text-ecorce-2">Il redeviendra libre et les autres offreurs de la liste en seront prévenus.</p>
              <div className="flex gap-2.5">
                <Button variant="ghost" onClick={() => setReleasing(false)} className="flex-1">Je le garde</Button>
                <Button loading={busy} onClick={() => act("release", "Cadeau libéré")} className="flex-1">Libérer</Button>
              </div>
            </div>
          )}
          <p className="text-[13px] text-sec">Gardez l'e-mail reçu : ce lien vous ramène ici pour changer d'avis.</p>
        </>
      )}
      {current.shareToken && (
        <RouterLink to={`/share/${current.shareToken}`} className="mt-2 flex h-12 items-center justify-center rounded-medium bg-card font-bold text-ecorce shadow-card">
          Voir toute la liste
        </RouterLink>
      )}
    </div>
  );
}
