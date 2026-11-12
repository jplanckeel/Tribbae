import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import * as api from "../lib/api";
import { AVATAR_COLORS } from "../lib/categories";
import { useMyData } from "../lib/data";
import {
  awaitingConfirmation, celebrationTitle, dayChoices, dayOf, doneLabel, doneToast, doneWords, folderProgress,
  formatDoneDay, inGiftList, isDone, memoryLine, progressHint, progressLabel, promptDay, promptKicker, promptQuestion, starWord,
  type DoneInput, type FolderProgress, type LinkDone,
} from "../lib/done";
import { likeFeedback } from "../lib/feedback";
import { eventDateMs, safeImage } from "../lib/format";
import { canEditFolder } from "../lib/links";
import { usePhotoUpload } from "../lib/photo";
import { useSession } from "../lib/session";
import type { Link } from "../lib/types";
import { useAsync } from "../lib/useAsync";
import { useFeedback } from "./feedback";
import Icon from "./Icon";
import { PhotoInput } from "./PhotoUpload";
import Sheet from "./Sheet";
import { Avatar, Button, ErrorNote, Toggle } from "./ui";
import { DateSheet } from "./WheelPicker";

// « C'est fait » (maquettes « C'est fait · prototype » et « … · états ») :
// un geste pour dire qu'une idée a été vécue, un souvenir facultatif, une
// petite célébration et un toast pour annuler. Même parcours côté Android
// (ui/Done.kt) ; libellés et règles dans lib/done.ts ↔ data/Done.kt.

interface RememberOptions {
  /** Jour proposé d'office (rappel d'une sortie passée) : AAAA-MM-JJ et son libellé */
  day?: string;
  dayLabel?: string;
  /** Souvenir à corriger, au lieu d'en créer un */
  edit?: LinkDone;
}

interface DoneApi {
  /** Le lecteur voit l'état « fait » : sa tribu, ou un dossier dont il est membre. */
  canSee: (l: Link) => boolean;
  /** Il peut la marquer : sa tribu, ou collaborateur éditeur du dossier ; jamais un cadeau d'une liste de cadeaux. */
  canMark: (l: Link) => boolean;
  /** Coche d'une vignette : faite, sans souvenir. */
  quick: (l: Link) => void;
  /** Ouvre la feuille « Comment c'était ? ». */
  remember: (l: Link, opts?: RememberOptions) => void;
  /** « Plus fait » : retire le dernier souvenir. */
  unmark: (l: Link) => Promise<void>;
  /** Retire un souvenir précis. */
  remove: (l: Link, doneId: string) => Promise<void>;
  /** « Pas cette fois ». */
  dismiss: (l: Link) => Promise<void>;
  /** Prévenu quand une idée change (la fiche garde sa propre copie). */
  subscribe: (fn: (l: Link) => void) => () => void;
}

const DoneContext = createContext<DoneApi | null>(null);

export function useDone(): DoneApi {
  const ctx = useContext(DoneContext);
  if (!ctx) throw new Error("useDone must be used inside DoneProvider");
  return ctx;
}

/** Suit les changements d'état « fait » d'une idée. */
export function useDoneChange(linkId: string, onChange: (l: Link) => void) {
  const { subscribe } = useDone();
  const cb = useRef(onChange);
  useEffect(() => {
    cb.current = onChange;
  });
  useEffect(() => subscribe((l) => l.id === linkId && cb.current(l)), [subscribe, linkId]);
}

const reducedMotion = () => typeof window !== "undefined" && window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;

export function DoneProvider({ children }: { children: ReactNode }) {
  const session = useSession();
  const { links, folders, peers, isTribePeer, upsertLink } = useMyData();
  const { toast } = useFeedback();
  const [sheet, setSheet] = useState<{ link: Link; opts: RememberOptions }>();
  const [yay, setYay] = useState<{ title: string; sub: string }>();
  const listeners = useRef(new Set<(l: Link) => void>());
  // Dernière version connue des idées et dossiers, lue après un appel au serveur
  const latest = useRef({ links, folders });
  useEffect(() => {
    latest.current = { links, folders };
  });

  const subscribe = useCallback((fn: (l: Link) => void) => {
    listeners.current.add(fn);
    return () => void listeners.current.delete(fn);
  }, []);

  const publish = useCallback((l: Link) => {
    upsertLink(l);
    listeners.current.forEach((fn) => fn(l));
  }, [upsertLink]);

  const folderOf = useCallback((l: Link) => folders.find((f) => f.id === l.folderId), [folders]);

  const canSee = useCallback((l: Link) => {
    if (!session) return false;
    if (isTribePeer(l.ownerId)) return true;
    const f = folderOf(l);
    return !!f && (isTribePeer(f.ownerId) || (f.collaborators ?? []).some((c) => c.userId === session.userId));
  }, [session, isTribePeer, folderOf]);

  const canMark = useCallback((l: Link) => {
    if (!session) return false;
    const f = folderOf(l);
    if (inGiftList(l, f)) return false;
    return isTribePeer(l.ownerId) || (!!f && canEditFolder(f, session.userId, peers));
  }, [session, isTribePeer, folderOf, peers]);

  /** Avancement du dossier de l'idée, une fois celle-ci à jour. */
  const progressAfter = (l: Link): FolderProgress | null => {
    if (!l.folderId) return null;
    const { links: all, folders: fs } = latest.current;
    const inFolder = all.filter((x) => x.folderId === l.folderId && x.id !== l.id);
    return folderProgress([...inFolder, l], !!fs.find((f) => f.id === l.folderId)?.giftReservations);
  };

  const undo = async (l: Link, doneId: string) => {
    try {
      publish(await api.done.unmark(l.id, doneId));
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };

  /** Célébration, puis toast « 5 sur 8 faites · Annuler ». */
  const celebrate = (before: Link, after: Link, doneId: string) => {
    likeFeedback();
    const p = progressAfter(after);
    setYay({ title: celebrationTitle(after.category, isDone(before)), sub: p ? `${progressLabel(p)} dans ce dossier` : "Un souvenir de plus pour la tribu" });
    setTimeout(() => {
      setYay(undefined);
      toast(doneToast(p), "ok", { duration: 5000, action: { label: "Annuler", onClick: () => void undo(after, doneId) } });
    }, reducedMotion() ? 700 : 1500);
  };

  const mark = async (l: Link, input: DoneInput) => {
    const r = await api.done.mark(l.id, input);
    publish(r.link);
    celebrate(l, r.link, r.done.id);
  };

  const quick = (l: Link) => {
    mark(l, {}).catch((e: Error) => toast(e.message, "error"));
  };

  const remove = async (l: Link, doneId: string) => {
    try {
      const next = await api.done.unmark(l.id, doneId);
      publish(next);
      toast(isDone(next) ? "Souvenir retiré" : "Remis dans « À faire »");
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };

  const unmark = async (l: Link) => {
    try {
      const dones = await api.done.list(l.id);
      if (dones[0]) await remove(l, dones[0].id);
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };

  const dismiss = async (l: Link) => {
    try {
      publish(await api.done.dismissPrompt(l.id));
      toast("D'accord, elle reste à faire");
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };

  const value: DoneApi = {
    canSee, canMark, quick, unmark, remove, dismiss, subscribe,
    remember: (link, opts = {}) => setSheet({ link, opts }),
  };

  return (
    <DoneContext.Provider value={value}>
      {children}
      {sheet && (
        <MemorySheet
          link={sheet.link}
          opts={sheet.opts}
          onClose={() => setSheet(undefined)}
          onMark={(input) => mark(sheet.link, input)}
          onUpdated={publish}
        />
      )}
      {yay && <Celebration title={yay.title} sub={yay.sub} />}
    </DoneContext.Provider>
  );
}

const SPARKS: [number, number, string][] = [
  [-70, -60, "#F28C38"], [70, -64, "#FFC857"], [-88, 10, "#9FD3E6"], [90, 14, "#C8492C"], [-50, 76, "#FFC857"],
  [56, 80, "#4C7A3C"], [0, -96, "#9FD3E6"], [-20, 96, "#F28C38"], [104, -26, "#4C7A3C"], [-104, -28, "#C8492C"],
];

const CHECK = "M5 12.5l4.5 4.5L19 7.5";

function Check({ size, width = 3, className = "" }: { size: number; width?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={width} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className={className}>
      <path d={CHECK} />
    </svg>
  );
}

/** La coche se dessine, quelques confettis aux couleurs de la marque. */
function Celebration({ title, sub }: { title: string; sub: string }) {
  return createPortal(
    <div role="status" aria-live="polite" className="fixed inset-0 z-[75] flex animate-fade-in flex-col items-center justify-center gap-[18px] bg-sable/95">
      <div className="relative h-[132px] w-[132px]">
        {SPARKS.map(([dx, dy, c], i) => (
          <span key={i} aria-hidden="true" className="done-spark" style={{ background: c, ["--dx" as string]: `${dx}px`, ["--dy" as string]: `${dy}px` }} />
        ))}
        <span className="done-stamp absolute inset-0 flex items-center justify-center rounded-full bg-done-solid text-white shadow-[0_0_0_10px_var(--color-done-bg)]">
          <Check size={66} width={2.8} className="done-draw" />
        </span>
      </div>
      <div className="flex animate-fade-up flex-col gap-1 px-6 text-center">
        <span className="font-display text-[26px] font-extrabold tracking-[-0.5px]">{title}</span>
        <span className="text-[15px] text-sec">{sub}</span>
      </div>
    </div>,
    document.body,
  );
}

interface Person {
  id: string;
  name: string;
  child: boolean;
  colors: [string, string];
}

/** Qui peut avoir été là : les comptes de la tribu, puis ses enfants. */
function usePeople(): Person[] {
  const session = useSession();
  const { tribe, children } = useMyData();
  return useMemo(() => {
    const accounts = (tribe?.members ?? []).filter((m) => m.sharing || m.userId === session?.userId)
      .map((m) => ({ id: m.userId, name: m.userId === session?.userId ? session.displayName || m.displayName || "Moi" : m.displayName || m.email || "Membre" }));
    if (session && !accounts.some((a) => a.id === session.userId)) accounts.unshift({ id: session.userId, name: session.displayName || "Moi" });
    return [
      ...accounts.map((a) => ({ ...a, child: false })),
      ...children.map((c) => ({ id: c.id, name: c.name, child: true })),
    ].map((p, i) => ({ ...p, colors: AVATAR_COLORS[i % AVATAR_COLORS.length] }));
  }, [tribe, children, session]);
}

function Stars({ value, size = 15, className = "" }: { value: number; size?: number; className?: string }) {
  return (
    <span className={`flex gap-0.5 text-braise ${className}`} role="img" aria-label={`${value} sur 5`}>
      {[1, 2, 3, 4, 5].map((n) => <Icon key={n} name="star" size={size} filled={value >= n} />)}
    </span>
  );
}

/** Feuille « Comment c'était ? » : tout y est facultatif. */
function MemorySheet({ link, opts, onClose, onMark, onUpdated }: {
  link: Link; opts: RememberOptions; onClose: () => void;
  onMark: (input: DoneInput) => Promise<void>; onUpdated: (l: Link) => void;
}) {
  const edit = opts.edit;
  const people = usePeople();
  const [now] = useState(() => Date.now());
  const words = doneWords(link.category);
  const choices = useMemo(() => {
    const list: { label: string; day: string }[] = dayChoices(now).map((c) => ({ label: c.label, day: c.day }));
    const extra = edit?.doneOn ?? opts.day;
    if (extra && !list.some((c) => c.day === extra)) list.push({ label: (!edit && opts.dayLabel) || formatDoneDay(extra, now), day: extra });
    return list;
  }, [now, edit, opts.day, opts.dayLabel]);
  const [day, setDay] = useState(edit?.doneOn ?? opts.day ?? choices[0].day);
  const [picking, setPicking] = useState(false);
  const [who, setWho] = useState<Set<string>>(() => new Set(edit ? [...(edit.withUserIds ?? []), ...(edit.withChildIds ?? [])] : people.map((p) => p.id)));
  const [rating, setRating] = useState(edit?.rating ?? 0);
  const [note, setNote] = useState(edit?.note ?? "");
  const [redo, setRedo] = useState(!!link.redo);
  const [photoUrl, setPhotoUrl] = useState(edit?.photoUrl ?? "");
  const upload = usePhotoUpload();
  const fileRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState<"save" | "quick">();
  const [error, setError] = useState("");
  const custom = !choices.some((c) => c.day === day);
  const uploading = upload.photo?.status === "uploading";

  const input = (): DoneInput => ({
    doneOn: day,
    withUserIds: people.filter((p) => !p.child && who.has(p.id)).map((p) => p.id),
    withChildIds: people.filter((p) => p.child && who.has(p.id)).map((p) => p.id),
    rating, note: note.trim(), photoUrl, redo,
  });
  const run = async (kind: "save" | "quick", fn: () => Promise<void>) => {
    setBusy(kind);
    setError("");
    try {
      await fn();
      onClose();
    } catch (e) {
      setError((e as Error).message);
      setBusy(undefined);
    }
  };
  const save = () => run("save", async () => {
    if (!edit) return onMark(input());
    onUpdated((await api.done.update(link.id, edit.id, input())).link);
  });
  const toggle = (id: string) => setWho((s) => {
    const next = new Set(s);
    if (!next.delete(id)) next.add(id);
    return next;
  });
  const preview = upload.photo?.local ?? safeImage(photoUrl);
  const chip = (on: boolean) => `h-[38px] rounded-full px-3.5 text-sm font-semibold transition-colors ${on ? "bg-ecorce text-sable" : "bg-card text-ecorce shadow-[inset_0_0_0_1.5px_var(--color-line-2)]"}`;

  return (
    <>
      <Sheet open onClose={onClose} title={edit ? "Notre souvenir" : isDone(link) ? `${words.again} ?` : words.ask}>
        <div className="flex flex-col gap-4">
          <p className="-mt-2 text-sm text-sec">{link.title} · tout est facultatif</p>

          <div className="flex flex-col gap-2">
            <span className="text-[13px] font-bold text-sec">Quand ?</span>
            <div role="radiogroup" aria-label="Quand" className="flex flex-wrap gap-2">
              {choices.map((c) => (
                <button key={c.day} type="button" role="radio" aria-checked={day === c.day} onClick={() => setDay(c.day)} className={chip(day === c.day)}>{c.label}</button>
              ))}
              <button type="button" role="radio" aria-checked={custom} onClick={() => setPicking(true)} className={chip(custom)}>
                {custom ? formatDoneDay(day, now) : "Choisir…"}
              </button>
            </div>
          </div>

          {people.length > 0 && (
            <div className="flex flex-col gap-2">
              <span className="text-[13px] font-bold text-sec">Qui était là ?</span>
              <div className="flex flex-wrap gap-3.5">
                {people.map((p) => {
                  const on = who.has(p.id);
                  return (
                    <button key={p.id} type="button" aria-pressed={on} onClick={() => toggle(p.id)}
                      className={`flex w-14 flex-col items-center gap-1 text-xs font-semibold transition-opacity ${on ? "" : "opacity-55"}`}>
                      <span className={`relative rounded-full ${on ? "shadow-[0_0_0_3px_var(--color-sable),0_0_0_5px_var(--color-done)]" : ""}`}>
                        <Avatar name={p.name} color={p.colors[0]} ink={p.colors[1]} size={48} />
                        {on && (
                          <span className="done-pop absolute -right-1 -bottom-1 grid size-5 place-items-center rounded-full border-2 border-sable bg-done-solid text-white">
                            <Check size={11} width={3.4} />
                          </span>
                        )}
                      </span>
                      <span className="w-full truncate text-center">{p.name}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          <div className="flex flex-col gap-1.5">
            <span className="text-[13px] font-bold text-sec">C'était comment ?</span>
            <div className="flex items-center gap-1">
              {[1, 2, 3, 4, 5].map((n) => (
                <button key={n} type="button" aria-label={`${n} sur 5`} aria-pressed={rating === n} onClick={() => setRating(rating === n ? 0 : n)}
                  className={`p-0.5 transition-transform ${rating >= n ? "text-braise" : "text-dash"} ${rating === n ? "scale-[1.15]" : ""}`}>
                  <Icon name="star" size={34} filled={rating >= n} />
                </button>
              ))}
              <span aria-live="polite" className="ml-1.5 text-sm font-bold text-cadeau-ink">{starWord(rating)}</span>
            </div>
          </div>

          <div className="flex gap-2.5">
            <PhotoInput inputRef={fileRef} onFile={(f) => upload.send(f, setPhotoUrl)} />
            <button type="button" onClick={() => fileRef.current?.click()} aria-label={preview ? "Changer la photo" : "Ajouter une photo"}
              className={`relative flex h-24 w-24 shrink-0 flex-col items-center justify-center gap-1 overflow-hidden rounded-[18px] bg-card text-xs font-semibold text-sec ${preview ? "" : "shadow-[inset_0_0_0_2px_var(--color-dash)]"}`}>
              {preview ? <img src={preview} alt="" className="absolute inset-0 h-full w-full object-cover" /> : <><Icon name="camera" size={24} />Photo</>}
              {uploading && <span role="status" className="absolute inset-0 grid place-items-center bg-black/40 text-[11px] font-bold text-white">Envoi…</span>}
            </button>
            <label className="flex flex-1 flex-col">
              <span className="sr-only">Un petit mot</span>
              <textarea value={note} onChange={(e) => setNote(e.target.value)} maxLength={280} placeholder="Un petit mot pour s'en souvenir…"
                className="min-h-24 flex-1 resize-none rounded-[18px] border-[1.5px] border-line-2 bg-card px-3.5 py-3 text-[15px] leading-[21px] outline-none placeholder:text-muted focus:border-terracotta" />
            </label>
          </div>
          {upload.photo?.status === "failed" && <ErrorNote>{upload.photo.error || "L'envoi de la photo a échoué."}</ErrorNote>}

          <div className="flex items-center gap-3 rounded-2xl bg-card px-3.5 py-3">
            <span className="flex flex-1 flex-col gap-px"><span className="text-[15px] font-bold">À refaire</span><span className="text-[13px] text-sec">On la reproposera l'an prochain</span></span>
            <Toggle label="À refaire" checked={redo} onChange={setRedo} />
          </div>

          <p className="flex items-center gap-2 text-xs text-sec"><Icon name="lock" size={15} />Le souvenir reste dans la tribu, même si l'idée est publique.</p>

          {error && <ErrorNote>{error}</ErrorNote>}
          <Button block loading={busy === "save"} disabled={uploading || !!busy} onClick={save}>{edit ? "Enregistrer" : "Garder ce souvenir"}</Button>
          {!edit && (
            <button type="button" disabled={!!busy} onClick={() => run("quick", () => onMark({}))} className="-mt-1.5 h-10 text-sm font-semibold text-link disabled:opacity-55">
              Juste marquer comme fait
            </button>
          )}
        </div>
      </Sheet>
      {picking && (
        <DateSheet title="Quel jour ?" kind="birth" value={new Date(`${day}T12:00:00`).getTime()} onConfirm={(ms) => setDay(dayOf(ms))} onClose={() => setPicking(false)} />
      )}
    </>
  );
}

/**
 * Coche d'une vignette, en bas à droite du visuel : vide = un toucher marque
 * l'idée comme faite ; verte = elle l'est (le toucher ouvre la fiche) ;
 * jaune « Fait ? » = la sortie datée est passée.
 */
export function DoneTick({ link, className = "", small }: { link: Link; className?: string; small?: boolean }) {
  const done = useDone();
  const [now] = useState(() => Date.now());
  if (!done.canSee(link)) return null;
  const n = link.doneCount ?? 0;
  const size = small ? "h-[22px] min-w-[22px] border-2 border-card" : "h-8 min-w-8";
  if (n > 0) {
    return (
      <span role="img" aria-label={`${doneLabel(link, now)} · voir le souvenir`}
        className={`flex items-center justify-center gap-0.5 rounded-full bg-done-solid text-xs font-bold text-white ${size} ${small ? "" : "m-1.5"} ${n > 1 && !small ? "pr-2 pl-1.5" : ""} ${className}`}>
        <Check size={small ? 12 : 15} />
        {n > 1 && !small && <span>×{n}</span>}
      </span>
    );
  }
  if (!done.canMark(link)) return null;
  const stop = (e: React.MouseEvent, fn: () => void) => {
    e.preventDefault();
    e.stopPropagation();
    fn();
  };
  if (awaitingConfirmation(link, now)) {
    const at = eventDateMs(link.eventDate) ?? now;
    return (
      <button type="button" onClick={(e) => stop(e, () => done.remember(link, { day: dayOf(at), dayLabel: promptDay(link, now) }))}
        className={`flex h-11 items-center ${className}`}>
        <span className="flex h-7 items-center rounded-full bg-[#FFC857] px-2.5 text-xs font-bold text-[#3A211B]">Fait ?</span>
      </button>
    );
  }
  return (
    <button type="button" aria-label="Marquer comme fait" onClick={(e) => stop(e, () => done.quick(link))}
      className={`grid size-11 place-items-center transition-transform active:scale-90 ${className}`}>
      <span className="grid size-8 place-items-center rounded-full bg-card/90 text-muted shadow-[inset_0_0_0_1.5px_var(--color-dash)]"><Check size={15} /></span>
    </button>
  );
}

/** Anneau « 4 sur 8 faites » d'un dossier. */
export function ProgressRing({ progress }: { progress: FolderProgress }) {
  const off = Math.round(132 - (132 * progress.done) / progress.total);
  return (
    <div className="flex items-center gap-3.5 rounded-[20px] bg-card px-3.5 py-3 shadow-card">
      <span className="relative h-[52px] w-[52px] shrink-0">
        <svg width="52" height="52" viewBox="0 0 52 52" aria-hidden="true" className="-rotate-90">
          <circle cx="26" cy="26" r="21" fill="none" stroke="var(--color-done-bg)" strokeWidth="7" />
          <circle className="done-ring" cx="26" cy="26" r="21" fill="none" stroke="var(--color-done)" strokeWidth="7" strokeLinecap="round" strokeDasharray="132" strokeDashoffset={off} />
        </svg>
        <span aria-hidden="true" className="absolute inset-0 grid place-items-center font-display text-[15px] font-extrabold text-done">{progress.done}</span>
      </span>
      <span className="flex flex-col gap-0.5">
        <span className="font-display text-[17px] font-bold">{progressLabel(progress)}</span>
        <span className="text-[13px] text-sec">{progressHint(progress)}</span>
      </span>
    </div>
  );
}

/** Bandeau sombre : la sortie datée est passée, « Vous y êtes allés ? ». */
export function DonePrompt({ link }: { link: Link }) {
  const done = useDone();
  const [now] = useState(() => Date.now());
  const at = eventDateMs(link.eventDate) ?? now;
  return (
    <div className="flex animate-fade-up flex-col gap-3 rounded-[20px] bg-[#3A211B] p-3.5 text-[#FBF3E7]">
      <div className="flex items-center gap-3">
        <span className="grid size-11 shrink-0 place-items-center rounded-[14px] bg-[#F8DCD3] text-[#B2412A]"><Icon name="calendar" size={22} /></span>
        <span className="flex min-w-0 flex-col gap-0.5">
          <span className="text-xs font-bold text-[#FFC857]">{promptKicker(link, now)}</span>
          <span className="font-display text-[17px] leading-[1.2] font-bold">{promptQuestion(link)}</span>
        </span>
      </div>
      <div className="flex gap-2">
        <button type="button" onClick={() => done.remember(link, { day: dayOf(at), dayLabel: promptDay(link, now) })}
          className="h-11 flex-1 rounded-[14px] bg-[#FFC857] text-sm font-bold text-[#3A211B] transition-transform active:scale-[.96]">Oui, on y était !</button>
        <button type="button" onClick={() => void done.dismiss(link)}
          className="h-11 flex-1 rounded-[14px] bg-white/[.12] text-sm font-semibold transition-transform active:scale-[.96]">Pas cette fois</button>
      </div>
    </div>
  );
}

/** Tampon « Fait » posé en biais sur le visuel de la fiche. */
export function DoneStamp({ label, className = "" }: { label: string; className?: string }) {
  return (
    <span className={`done-stamp flex items-center gap-1.5 rounded-xl bg-done-solid px-3 py-1.5 font-display text-sm font-extrabold text-white shadow-[0_6px_16px_rgba(62,102,48,.35)] ${className}`}>
      <Check size={16} />{label}
    </span>
  );
}

/** Pastille verte « Fait le 26 oct. » : remplace le bouton une fois l'idée faite. */
export function DonePill({ link }: { link: Link }) {
  const [now] = useState(() => Date.now());
  return (
    <span className="flex h-10 items-center gap-1.5 self-start rounded-[14px] bg-done-bg px-3 text-sm font-bold text-done">
      <Check size={16} width={2.8} />{doneLabel(link, now)}
    </span>
  );
}

/** Carte « Notre souvenir » de la fiche : le dernier, et les précédents si l'idée a été refaite. */
export function MemoryCard({ link }: { link: Link }) {
  const done = useDone();
  const people = usePeople();
  const [now] = useState(() => Date.now());
  const [all, setAll] = useState(false);
  const res = useAsync(() => api.done.list(link.id), [link.id, link.doneCount, link.lastDoneAt]);
  // Un souvenir corrigé ne change ni le nombre ni la date : on relit quand même
  useDoneChange(link.id, res.reload);
  // Pendant une relecture, la carte garde les souvenirs déjà affichés
  const [dones, setDones] = useState<LinkDone[]>([]);
  if (res.data && res.data !== dones) setDones(res.data);
  const last = dones[0];
  if (!last) return null;
  const present = (d: LinkDone) => people.filter((p) => (p.child ? d.withChildIds : d.withUserIds)?.includes(p.id));
  const photo = safeImage(last.photoUrl);
  const canMark = done.canMark(link);

  return (
    <section aria-label="Notre souvenir" className="done-pop flex flex-col overflow-hidden rounded-[20px] bg-card shadow-card">
      {photo && <img src={photo} alt="Photo souvenir" loading="lazy" className="h-40 w-full object-cover" />}
      <div className="flex flex-col gap-2 px-3.5 pt-3 pb-3.5">
        <div className="flex items-center justify-between">
          <h2 className="text-xs font-bold tracking-[.6px] text-done uppercase">Notre souvenir</h2>
          {(last.rating ?? 0) > 0 && <Stars value={last.rating ?? 0} />}
        </div>
        {last.note && <p className="text-[15px] leading-[21px] text-ecorce-2">« {last.note} »</p>}
        <span className="flex items-center gap-2 text-[13px] text-sec">
          {present(last).length > 0 && (
            <span className="flex">
              {present(last).slice(0, 5).map((p) => <Avatar key={p.id} name={p.name} color={p.colors[0]} ink={p.colors[1]} size={24} className="-mr-1.5 border-2 border-card" />)}
            </span>
          )}
          <span className={present(last).length ? "ml-1.5" : ""}>{memoryLine(present(last).map((p) => p.name), last.doneOn, dones.length, now)}</span>
        </span>
        <div className="flex flex-wrap items-center gap-2">
          {link.redo && (
            <span className="inline-flex items-center gap-1 rounded-lg bg-cadeau-bg px-2 py-[3px] text-xs font-bold text-cadeau-ink"><Icon name="refresh" size={12} />À refaire</span>
          )}
          {canMark && (
            <button type="button" onClick={() => done.remember(link, { edit: last })} className="text-[13px] font-bold text-link">Modifier</button>
          )}
          {dones.length > 1 && (
            <button type="button" aria-expanded={all} onClick={() => setAll(!all)} className="ml-auto text-[13px] font-bold text-link">
              {all ? "Masquer" : `Voir les ${dones.length} souvenirs`}
            </button>
          )}
        </div>
        {all && (
          <ul className="flex flex-col gap-2 border-t border-line pt-2.5">
            {dones.slice(1).map((d) => (
              <li key={d.id} className="flex items-start gap-2 text-[13px]">
                <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="flex items-center gap-2 font-bold">{formatDoneDay(d.doneOn, now)}{(d.rating ?? 0) > 0 && <Stars value={d.rating ?? 0} size={12} />}</span>
                  {d.note && <span className="text-ecorce-2">« {d.note} »</span>}
                  {present(d).length > 0 && <span className="text-sec">Avec {present(d).map((p) => p.name).join(", ")}</span>}
                </span>
                {canMark && (
                  <button type="button" aria-label={`Retirer le souvenir du ${formatDoneDay(d.doneOn, now)}`} onClick={() => void done.remove(link, d.id)}
                    className="-m-1.5 grid size-8 place-items-center rounded-full text-muted hover:text-sortie-ink">
                    <Icon name="trash" size={15} />
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
