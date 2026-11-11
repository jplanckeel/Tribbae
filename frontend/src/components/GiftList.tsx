import { useState } from "react";
import { Link as RouterLink, useLocation } from "react-router";
import * as api from "../lib/api";
import { categoryMeta } from "../lib/categories";
import { safeHref } from "../lib/format";
import {
  GIFT_FILTERS, GIFT_TONE, giftProgress, giftState, matchesGiftFilter, readPendingGifts, rememberPendingGift, sharedGiftStatus,
  type GiftFilter, type GiftStatus,
} from "../lib/gifts";
import { useSession } from "../lib/session";
import type { Folder, Link } from "../lib/types";
import { IdeaVisual } from "./cards";
import { useFeedback } from "./feedback";
import Icon from "./Icon";
import Sheet from "./Sheet";
import { Button, ErrorNote, Field } from "./ui";

/**
 * « Offrir une idée » sur la page d'un lien partagé : chacun dit « Je l'offre »
 * pour éviter les doublons, sans que personne sache qui offre quoi.
 */
export default function GiftList({ token, folder, links, onChanged }: {
  token: string; folder: Folder; links: Link[]; onChanged: () => void;
}) {
  const session = useSession();
  const [filter, setFilter] = useState<GiftFilter>("all");
  const [pending, setPending] = useState(readPendingGifts);
  const [open, setOpen] = useState<string | null>(null);
  const statusOf = (l: Link): GiftStatus => sharedGiftStatus(l.giftStatus, !!pending[l.id]);
  const progress = giftProgress(links.map(statusOf));
  const shown = links.filter((l) => matchesGiftFilter(statusOf(l), filter));
  const current = links.find((l) => l.id === open);
  const owner = folder.ownerDisplayName || "la personne qui a fait la liste";

  return (
    <div className="flex flex-col gap-3.5">
      <p className="flex items-start gap-2.5 rounded-[18px] bg-prune px-3.5 py-3 text-[13px] leading-[18px] text-soleil">
        <Icon name="sparkles" size={20} className="mt-px shrink-0 text-[#FFC857]" />
        <span><b className="text-white">La surprise est gardée.</b> Personne ne voit qui offre quoi, pas même {owner}.</span>
      </p>

      <div className="flex flex-col gap-1.5">
        <div className="flex justify-between text-[13px]"><span className="font-bold">{progress.label}</span><span className="text-sec">{progress.freeLabel}</span></div>
        <div className="flex h-2 overflow-hidden rounded bg-sable-2" aria-hidden="true">
          <span className="h-full bg-recette-ink transition-[width] duration-700" style={{ width: `${progress.pDone}%` }} />
          <span className="h-full bg-prune transition-[width] duration-700" style={{ width: `${progress.pTaken}%` }} />
        </div>
      </div>

      <div role="radiogroup" aria-label="Filtrer les cadeaux" className="flex gap-2">
        {GIFT_FILTERS.map((f) => {
          const on = filter === f.key;
          return (
            <button key={f.key} type="button" role="radio" aria-checked={on} onClick={() => setFilter(f.key)}
              className={`h-9 rounded-full px-3.5 text-sm font-semibold transition-colors ${on ? "bg-ecorce text-sable" : "bg-card text-ecorce shadow-[inset_0_0_0_1.5px_var(--color-line-2)]"}`}>
              {f.label}
            </button>
          );
        })}
      </div>

      <ul className="flex flex-col gap-2.5">
        {shown.map((l) => <GiftRow key={l.id} link={l} status={statusOf(l)} onOpen={() => setOpen(l.id)} />)}
      </ul>
      {shown.length === 0 && (
        <div className="flex flex-col gap-1.5 rounded-[22px] px-5 py-7 text-center shadow-[inset_0_0_0_2px_var(--color-dash)]">
          <span className="font-display text-[17px] font-bold">{filter === "mine" ? "Vous n'offrez rien pour l'instant" : "Tout a trouvé preneur"}</span>
          <span className="text-sm text-sec">{filter === "mine" ? "Choisissez une idée libre : personne d'autre ne pourra la prendre." : "Merci pour eux !"}</span>
        </div>
      )}

      {current && (
        <GiftSheet
          token={token}
          link={current}
          owner={owner}
          status={statusOf(current)}
          signedIn={!!session}
          onClose={() => setOpen(null)}
          onPending={() => { rememberPendingGift(current.id); setPending(readPendingGifts()); }}
          onChanged={onChanged}
        />
      )}
    </div>
  );
}

function GiftRow({ link, status, onOpen }: { link: Link; status: GiftStatus; onOpen: () => void }) {
  const s = giftState(status);
  const meta = [link.price, link.location].filter(Boolean).join(" · ");
  return (
    <li>
      <button type="button" onClick={onOpen} aria-label={`${link.title}, ${s.label}`}
        className={`relative flex w-full items-center gap-3 overflow-hidden rounded-[22px] bg-card p-2.5 text-left shadow-card transition-transform active:scale-[.97] ${s.dim ? (s.strike ? "opacity-60" : "opacity-75") : ""}`}>
        {status === "mine" && <span aria-hidden="true" className="absolute top-2.5 -right-[26px] h-[18px] w-24 rotate-[38deg] bg-prune" />}
        <span className="relative shrink-0">
          <IdeaVisual link={{ ...link, giftStatus: undefined }} height={64} iconSize={30} className="w-16 rounded-2xl" />
          {(status === "bought" || status === "mine_bought") && (
            <span className="absolute -right-1 -bottom-1 grid size-6 place-items-center rounded-full border-2 border-card bg-recette-ink text-white"><Icon name="check" size={13} /></span>
          )}
        </span>
        <span className="flex min-w-0 flex-1 flex-col gap-1">
          <span className={`font-display text-[15px] leading-tight font-bold ${s.strike ? "line-through" : ""}`}>{link.title}</span>
          {meta && <span className="text-xs text-sec">{meta}</span>}
          <span className={`inline-flex items-center gap-1.5 self-start rounded-lg px-2 py-[3px] text-xs font-bold ${GIFT_TONE[s.tone]}`}>
            <Icon name={s.icon} size={12} />{s.label}
          </span>
        </span>
        <Icon name="chevronRight" size={18} className="relative text-muted" />
      </button>
    </li>
  );
}

type Step = "gift" | "who" | "check-mail" | "claimed" | "release";

function GiftSheet({ token, link, owner, status, signedIn, onClose, onPending, onChanged }: {
  token: string; link: Link; owner: string; status: GiftStatus; signedIn: boolean;
  onClose: () => void; onPending: () => void; onChanged: () => void;
}) {
  const { toast } = useFeedback();
  const location = useLocation();
  const [step, setStep] = useState<Step>("gift");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const s = giftState(status);
  const cat = categoryMeta(link.category);
  const href = safeHref(link.url);

  const run = async (fn: () => Promise<void>) => {
    setBusy(true);
    setError("");
    try {
      await fn();
    } catch (e) {
      setError((e as Error).message);
      onChanged();
    } finally {
      setBusy(false);
    }
  };
  const reserve = () => run(async () => {
    if (!signedIn) return setStep("who");
    await api.shared.reserveGift(token, link.id);
    onChanged();
    setStep("claimed");
  });
  const reserveByEmail = () => run(async () => {
    await api.shared.reserveGift(token, link.id, { name: name.trim(), email: email.trim() });
    onPending();
    onChanged();
    setStep("check-mail");
  });
  const act = (action: "bought" | "unbought" | "release", message: string) => run(async () => {
    await api.gifts.mine(link.id, action);
    onChanged();
    toast(message);
    onClose();
  });

  const title = { gift: link.title, who: `Vous offrez ${link.title}`, "check-mail": "Vérifiez vos e-mails", claimed: "Cadeau réservé", release: "Libérer ce cadeau ?" }[step];

  return (
    <Sheet open onClose={onClose} title={title}>
      {step === "gift" && (
        <div className="flex flex-col gap-3.5">
          <div className="relative">
            <IdeaVisual link={{ ...link, giftStatus: undefined }} height={150} iconSize={64} className="rounded-[22px]" />
            <span className={`absolute top-3 left-3 rounded-full bg-card px-2.5 py-1 text-xs font-semibold ${cat.ink}`}>{cat.label}</span>
          </div>
          <div className="flex flex-col gap-1">
            <span className="text-sm text-sec">
              {[link.price, link.location].filter(Boolean).join(" · ")}
              {href && <>{(link.price || link.location) && " · "}<a href={href} target="_blank" rel="noopener noreferrer nofollow" className="font-semibold text-link">Voir le site ↗</a></>}
            </span>
          </div>
          {link.description && (
            <div className="flex items-start gap-2.5 rounded-2xl bg-card px-3.5 py-3">
              <span className="grid size-8 shrink-0 place-items-center rounded-full bg-prune font-display text-[13px] font-extrabold text-soleil">{owner.slice(0, 1).toUpperCase()}</span>
              <span className="flex flex-col gap-0.5 text-sm leading-[19px]"><b>Le mot de {owner}</b><span className="text-ecorce-2">{link.description}</span></span>
            </div>
          )}
          <div role="status" className={`flex items-center gap-3 rounded-2xl px-3.5 py-3 ${GIFT_TONE[s.tone]}`}>
            <Icon name={s.icon} size={22} />
            <span className="flex flex-1 flex-col gap-px"><span className="text-[15px] font-bold">{s.long}</span><span className="text-[13px] opacity-85">{s.sub}</span></span>
          </div>
          {error && <ErrorNote>{error}</ErrorNote>}
          {s.free && <Button icon="cadeau" loading={busy} onClick={reserve} block>Je l'offre</Button>}
          {status === "mine" && (
            <div className="flex flex-col gap-2">
              <Button variant="dark" icon="check" loading={busy} onClick={() => act("bought", "Noté : c'est acheté")} block>C'est acheté</Button>
              <button type="button" onClick={() => setStep("release")} className="h-10 text-sm font-semibold text-sortie-ink">Libérer ce cadeau</button>
            </div>
          )}
          {status === "mine_bought" && (
            <button type="button" onClick={() => act("unbought", "C'est noté")} className="h-10 text-sm font-semibold text-link">Finalement, pas encore acheté</button>
          )}
          {status === "pending" && <p className="text-center text-[13px] text-sec">Ouvrez le lien reçu par e-mail pour confirmer. Le même lien sert ensuite à dire « C'est acheté » ou à le libérer.</p>}
          {(status === "reserved" || status === "bought") && <p className="text-center text-[13px] text-sec">S'il se libère, les personnes qui offrent un cadeau de cette liste sont prévenues.</p>}
        </div>
      )}

      {step === "who" && (
        <form className="flex flex-col gap-3" onSubmit={(e) => { e.preventDefault(); reserveByEmail(); }}>
          <p className="text-[13px] text-ecorce-2">Pour que les autres sachent qu'il est pris. Votre prénom ne sert qu'à vous écrire : personne ne le verra sur la liste.</p>
          <Field label="Votre prénom" value={name} onChange={(e) => setName(e.target.value)} autoComplete="given-name" maxLength={40} required />
          <Field label="Votre e-mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required
            hint="Pour confirmer, puis pour dire « C'est acheté » ou libérer le cadeau." />
          {error && <ErrorNote>{error}</ErrorNote>}
          <Button type="submit" icon="cadeau" loading={busy} disabled={!name.trim() || !email.trim()} block>Réserver ce cadeau</Button>
          <span className="text-center text-[13px] text-sec">
            Déjà sur Tribbae ? <RouterLink to={`/connexion?next=${encodeURIComponent(location.pathname)}`} className="font-bold text-link">Se connecter</RouterLink>
          </span>
        </form>
      )}

      {step === "check-mail" && (
        <div className="flex flex-col items-center gap-3 text-center">
          <span className="grid size-16 place-items-center rounded-full bg-cadeau-bg text-cadeau-ink"><Icon name="mail" size={30} /></span>
          <p className="text-sm leading-5 text-ecorce-2">Nous avons écrit à <b>{email.trim()}</b>. Ouvrez le lien pour confirmer que vous offrez « {link.title} ». Sans confirmation sous 2 heures, il redevient libre.</p>
          <Button onClick={onClose} block>Compris</Button>
        </div>
      )}

      {step === "claimed" && <Claimed title={link.title} onClose={onClose} />}

      {step === "release" && (
        <div className="flex flex-col gap-3.5">
          <p className="text-[15px] leading-[22px] text-ecorce-2">« {link.title} » redeviendra libre et les autres offreurs de la liste en seront prévenus. Personne ne saura que c'était vous.</p>
          {error && <ErrorNote>{error}</ErrorNote>}
          <div className="flex gap-2.5">
            <Button variant="ghost" onClick={() => setStep("gift")} className="flex-1">Je le garde</Button>
            <Button loading={busy} onClick={() => act("release", "Cadeau libéré")} className="flex-1">Libérer</Button>
          </div>
        </div>
      )}
    </Sheet>
  );
}

/** « C'est vous qui l'offrez ! » : le paquet s'ouvre, quelques confettis. */
export function Claimed({ title, onClose }: { title: string; onClose: () => void }) {
  const sparks: [string, number, number, number][] = [
    ["#F28C38", -110, -40, 0], ["#FFC857", 100, -54, 60], ["#9FD3E6", -80, 46, 120],
    ["#F28C38", 116, 30, 40], ["#FFC857", -18, -86, 90], ["#9FD3E6", 30, 74, 150],
  ];
  return (
    <div className="relative flex flex-col items-center gap-3 pt-1.5 text-center">
      {sparks.map(([c, dx, dy, delay], i) => (
        <span key={i} aria-hidden="true" className="gift-spark" style={{ background: c, ["--dx" as string]: `${dx}px`, ["--dy" as string]: `${dy}px`, animationDelay: `${delay}ms` }} />
      ))}
      <svg width="120" height="112" viewBox="0 0 120 112" aria-hidden="true">
        <path d="M22 54h76v54H22z" fill="#C24F78" stroke="#3A211B" strokeWidth="3" strokeLinejoin="round" />
        <path d="M60 54v54" stroke="#FFC857" strokeWidth="10" />
        <g className="gift-lid">
          <path d="M16 38h88v18H16z" fill="#9C3A5C" stroke="#3A211B" strokeWidth="3" strokeLinejoin="round" />
          <path d="M60 38v18" stroke="#FFC857" strokeWidth="10" />
          <g className="gift-bow"><path d="M60 38c-12-26-40-24-30-5 5 8 19 7 30 5zm0 0c12-26 40-24 30-5-5 8-19 7-30 5z" fill="#FFC857" stroke="#3A211B" strokeWidth="3" strokeLinejoin="round" /></g>
        </g>
      </svg>
      <h2 className="font-display text-[28px] font-extrabold tracking-[-0.6px]">C'est vous qui l'offrez !</h2>
      <p className="text-sm leading-5 text-ecorce-2">« {title} » est réservé pour vous. Les autres voient seulement qu'il est pris ; personne ne sait qui l'offre.</p>
      <Button onClick={onClose} block>Parfait</Button>
    </div>
  );
}
