import { useEffect, useRef, useState, type CSSProperties } from "react";
import {
  BLANK_COLORS, HINTS, blankLabel, defaultRequest, folderNameFor, inspirations, optionsFor, promptOf,
  resultSubtitle, resultTitle, toggleWho, waitingMessages, type AiRequest, type BlankKey,
} from "../lib/aiRequest";
import * as api from "../lib/api";
import { categoryMeta, folderColor, normalizeCategory } from "../lib/categories";
import { useMyData } from "../lib/data";
import { canEditFolder } from "../lib/links";
import { useSession } from "../lib/session";
import type { AiIdea, CategoryKey, Folder, Link } from "../lib/types";
import { useFeedback } from "./feedback";
import { FolderGlyph } from "./FolderCover";
import FolderPicker from "./FolderPicker";
import Icon from "./Icon";
import Sheet from "./Sheet";
import { ErrorNote, Spinner } from "./ui";

// « Idées avec l'IA » (maquette « Idées avec l'IA v2 ») : une phrase à
// compléter → le tipi s'illumine pendant la rédaction → les idées arrivent une
// à une, chacune avec son « pourquoi », on garde ou on écarte (une autre
// arrive) → les idées gardées s'envolent dans le dossier.
// Même parcours côté Android : ui/AiGenerateScreen.kt.

type Step = "compose" | "gen" | "res" | "done";
type Card = AiIdea & { id: number; cat: CategoryKey; kept: boolean; gone: boolean };

/** Idées demandées, et combien en redemander quand on en écarte. */
const FIRST = 5;
const REFILL = 3;
const NEW_FOLDER = "__new__";
const BLANKS: BlankKey[] = ["what", "who", "when", "where", "budget"];

const SPARKLE = "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z";

/** À monter uniquement quand la feuille est ouverte. */
interface Props {
  onClose: () => void;
  folderId?: string;
  /** Texte libre de départ (recherche, nom de dossier) : devient le détail. */
  prompt?: string;
  /** Enfant pour qui chercher (fiche enfant de « Ma tribu »). */
  childId?: string;
  onSaved: (folderId: string | undefined, links: Link[]) => void;
}

export default function AiSheet({ onClose, folderId, prompt: initialDetail, childId, onSaved }: Props) {
  const session = useSession();
  const { folders, links, children, upsertFolder, upsertLink, peers } = useMyData();
  const { confirm, toast } = useFeedback();
  const [step, setStep] = useState<Step>("compose");
  const [req, setReq] = useState<AiRequest>(() => defaultRequest(children, childId, initialDetail ?? ""));
  const [edit, setEdit] = useState<BlankKey | null>(null);
  const [cascade, setCascade] = useState(0);
  const [inspiration, setInspiration] = useState<string>();
  const [cards, setCards] = useState<Card[]>([]);
  const [reserve, setReserve] = useState<AiIdea[]>([]);
  const [pending, setPending] = useState(0);
  const [error, setError] = useState("");
  const [target, setTarget] = useState(folderId ?? "");
  const [picking, setPicking] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState<{ links: Link[]; folder?: Folder }>();
  const [now] = useState(() => Date.now());
  const abort = useRef<AbortController | null>(null);
  const refilling = useRef(false);
  // Places en attente d'une idée (miroir de `pending`, lu à la fin d'une requête)
  const waiting = useRef(0);
  const nextId = useRef(0);
  const seen = useRef<string[]>([]);

  const writable = folders.filter((f) => canEditFolder(f, session?.userId, peers));
  const newName = folderNameFor(req, children, inspiration);
  // Par défaut : le dossier d'où l'on vient, sinon un dossier existant du même nom, sinon un nouveau
  const folderTarget = target || writable.find((f) => f.name.toLowerCase() === newName.toLowerCase())?.id || NEW_FOLDER;
  const targetFolder = writable.find((f) => f.id === folderTarget);
  const kept = cards.filter((c) => c.kept && !c.gone);
  const shown = cards.filter((c) => !c.gone);

  useEffect(() => () => abort.current?.abort(), []);

  const toCards = (ideas: AiIdea[]): Card[] =>
    ideas.map((i) => ({ ...i, id: nextId.current++, cat: normalizeCategory(i.category), kept: false, gone: false }));
  const remember = (ideas: AiIdea[]) => { seen.current = [...seen.current, ...ideas.map((i) => i.title)]; };

  const update = (patch: Partial<AiRequest>, fromInspiration?: string) => {
    setReq((r) => ({ ...r, ...patch }));
    setInspiration(fromInspiration);
  };

  /** Demande des idées : garde celles déjà gardées et n'en repropose aucune. */
  const generate = async () => {
    abort.current?.abort();
    const ctrl = new AbortController();
    abort.current = ctrl;
    setStep("gen");
    setEdit(null);
    setError("");
    try {
      const ideas = await api.ai.generate(promptOf(req, children), { count: FIRST, exclude: seen.current, signal: ctrl.signal });
      if (ctrl.signal.aborted) return;
      remember(ideas);
      setCards((list) => [...list.filter((c) => c.kept && !c.gone), ...toCards(ideas)]);
      setReserve([]);
      waiting.current = 0;
      setPending(0);
      setStep(ideas.length || kept.length ? "res" : "compose");
      if (!ideas.length) setError("Aucune idée trouvée : changez un mot de la phrase et réessayez.");
    } catch (err) {
      if (ctrl.signal.aborted) return;
      setError((err as Error).message);
      setStep(kept.length ? "res" : "compose");
    }
  };

  const cancel = () => {
    abort.current?.abort();
    setStep("compose");
  };

  /** Va chercher quelques idées de plus et les fait entrer dans les places libres. */
  const refill = async () => {
    if (refilling.current) return;
    refilling.current = true;
    try {
      const ideas = await api.ai.generate(promptOf(req, children), { count: REFILL, exclude: seen.current });
      remember(ideas);
      const fresh = toCards(ideas);
      const take = Math.min(waiting.current, fresh.length);
      setCards((list) => [...list, ...fresh.slice(0, take)]);
      setReserve((r) => [...r, ...fresh.slice(take)]);
      if (!fresh.length) toast("Pas d'autre idée pour cette demande.");
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      waiting.current = 0;
      setPending(0);
      refilling.current = false;
    }
  };

  /** « Pas pour nous » : la carte s'en va, une autre arrive. */
  const drop = (id: number) => {
    setCards((list) => list.map((c) => (c.id === id ? { ...c, gone: true, kept: false } : c)));
    if (reserve.length) {
      const [next, ...rest] = reserve;
      setReserve(rest);
      setCards((list) => [...list, ...toCards([next])]);
    } else {
      waiting.current += 1;
      setPending(waiting.current);
      void refill();
    }
  };

  const toggleKeep = (id: number) => setCards((list) => list.map((c) => (c.id === id ? { ...c, kept: !c.kept } : c)));

  const save = async () => {
    if (!kept.length || saving) return;
    setSaving(true);
    setError("");
    try {
      let folder = targetFolder;
      if (folderTarget === NEW_FOLDER) {
        folder = await api.folders.create({ name: newName, icon: "MAGIC", color: "PURPLE", visibility: "VISIBILITY_PRIVATE" });
        upsertFolder(folder);
      }
      const created = await Promise.all(
        kept.map((i) =>
          api.links.create({
            title: i.title,
            description: i.description,
            url: "",
            imageUrl: "",
            category: i.cat,
            tags: i.tags ?? [],
            ageRange: i.ageRange ?? "",
            price: "",
            location: "",
            ingredients: [],
            folderId: folder?.id,
            visibility: "private",
          }),
        ),
      );
      created.forEach(upsertLink);
      setSaved({ links: created, folder });
      setStep("done");
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const restart = () => {
    setCards([]);
    setReserve([]);
    waiting.current = 0;
    setPending(0);
    seen.current = [];
    setSaved(undefined);
    setError("");
    setStep("compose");
  };

  const close = async () => {
    if (step !== "done" && kept.length && !(await confirm({
      title: "Abandonner ces idées ?", text: "Les idées gardées ne seront pas rangées.", confirmLabel: "Abandonner", danger: true,
    }))) return;
    abort.current?.abort();
    onClose();
  };

  const gen = step === "gen";
  const header = (
    <div className="flex items-center justify-between px-5 pt-3 pb-1.5 sm:px-6 sm:pt-5">
      <button
        type="button"
        data-close
        onClick={close}
        aria-label="Fermer"
        className={`flex h-11 w-11 items-center justify-center rounded-full transition-colors ${gen ? "bg-white/15 text-sable" : "bg-sable-2 text-ecorce hover:bg-line"}`}
      >
        <Icon name="close" size={20} />
      </button>
      <span className="flex items-center gap-1.5 text-[15px] font-bold">
        <Icon name="sparkles" size={18} className={`ai-twinkle ${gen ? "text-[#FFC857]" : "text-prune"}`} />
        Idées avec l'IA
      </span>
      <span className="w-11" />
    </div>
  );

  const footer =
    step === "compose" ? (
      <button
        type="button"
        onClick={() => void generate()}
        className="flex h-[58px] w-full items-center justify-center gap-2.5 rounded-[20px] bg-prune text-[17px] font-bold text-sable shadow-[0_10px_24px_rgba(94,69,88,0.35)] transition-transform active:scale-[.97]"
      >
        <Icon name="sparkles" size={20} className="ai-twinkle text-soleil" />
        Trouver {FIRST} idées
      </button>
    ) : step === "res" ? (
      <div className="flex flex-col gap-2">
        <span className="flex flex-wrap items-center gap-1.5 text-[13px] text-sec">
          Ranger dans <strong className="text-ecorce">{targetFolder?.name ?? (folderTarget === NEW_FOLDER ? `« ${newName} » (nouveau)` : "vos idées non rangées")}</strong> ·
          <button type="button" onClick={() => setPicking(true)} className="font-bold text-terracotta hover:underline">changer</button>
        </span>
        <button
          type="button"
          onClick={() => void save()}
          aria-disabled={!kept.length}
          className={`flex h-14 w-full items-center justify-center gap-2 rounded-medium text-base font-bold text-white transition-colors active:scale-[.97] ${kept.length ? "bg-terracotta" : "cursor-not-allowed bg-bark-mute"}`}
        >
          {saving && <Spinner size={18} />}
          {kept.length ? `Ranger ${kept.length} idée${kept.length > 1 ? "s" : ""}` : "Gardez au moins une idée"}
        </button>
      </div>
    ) : undefined;

  return (
    <Sheet open onClose={close} title="Idées avec l'IA" tone={gen ? "prune" : "sable"} header={header} footer={footer} tall>
      {step === "compose" && (
        <div className="flex animate-fade-in flex-col gap-[18px]">
          <span className="text-[15px] text-sec">Racontez-moi votre envie, je m'occupe du reste.</span>
          {error && <ErrorNote>{error}</ErrorNote>}

          <div className="flex flex-wrap items-center gap-x-1.5 gap-y-2 rounded-[26px] bg-white px-[18px] py-5 font-display text-[23px] leading-tight font-bold shadow-[0_4px_16px_rgba(58,33,27,0.07)]">
            <span>Je cherche</span>
            {blank("what")}
            <span>pour</span>
            {blank("who")}
            {blank("when")}
            <span className="-ml-1.5">,</span>
            {blank("where")}
            <span className="-ml-1.5">,</span>
            <span>budget</span>
            {blank("budget")}
            <span className="-ml-1.5">.</span>
            {edit && (
              <div key={edit} className="ai-opts mt-2 flex basis-full flex-wrap gap-2 border-t-[1.5px] border-dashed border-line pt-3 font-sans">
                <span className="basis-full text-xs font-bold text-sec">{HINTS[edit]}</span>
                {optionsFor(edit, children).map((o) => {
                  const on = edit === "who" ? req.who.includes(o.key) : req[edit] === o.key;
                  const [bg, fg] = BLANK_COLORS[edit];
                  return (
                    <button
                      key={o.key}
                      type="button"
                      aria-pressed={on}
                      onClick={() => {
                        if (edit === "who") update({ who: toggleWho(req.who, o.key) });
                        else {
                          update({ [edit]: o.key });
                          setEdit(null);
                        }
                      }}
                      className={`h-[38px] rounded-small border-2 px-3 text-sm font-bold transition-transform duration-[450ms] ease-[cubic-bezier(.34,1.56,.64,1)] ${on ? "scale-[1.04] border-ecorce" : "border-line bg-white text-ecorce"}`}
                      style={on ? { background: bg, color: fg } : undefined}
                    >
                      {edit === "who" ? o.label : o.label.charAt(0).toUpperCase() + o.label.slice(1)}
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          <label className="flex flex-col gap-1.5">
            <span className="text-[13px] font-bold text-sec">Un détail en plus ? <span className="font-medium">(facultatif)</span></span>
            <span className="flex h-[50px] items-center gap-2.5 rounded-2xl border-[1.5px] border-line bg-white px-3.5 focus-within:border-terracotta">
              <Icon name="pencil" size={18} className="text-muted" />
              <input
                value={req.detail}
                maxLength={200}
                onChange={(e) => setReq((r) => ({ ...r, detail: e.target.value }))}
                placeholder={`« ${children[0]?.name ?? "Il"} adore l'espace en ce moment »`}
                className="min-w-0 flex-1 bg-transparent text-[15px] font-medium text-ecorce outline-none placeholder:text-muted"
              />
            </span>
          </label>

          <div className="flex flex-col gap-2">
            <span className="text-[13px] font-bold text-sec">Ou partir d'une inspiration</span>
            <div className="no-scrollbar -mr-5 flex gap-2 overflow-x-auto pr-5 sm:-mr-6">
              {inspirations(children, now).map((i) => (
                <button
                  key={i.label}
                  type="button"
                  onClick={() => {
                    update({ ...i.set, who: i.set.who ?? req.who }, i.label);
                    setEdit(null);
                    setCascade((c) => c + 1);
                  }}
                  className="flex h-11 shrink-0 items-center gap-2 rounded-[14px] px-3.5 text-sm font-bold transition-transform active:scale-[.97]"
                  style={{ background: i.colors[0], color: i.colors[1] }}
                >
                  <svg viewBox="0 0 24 24" width={17} height={17} aria-hidden="true" className="icon"><path d={i.d} /></svg>
                  {i.label}
                </button>
              ))}
            </div>
          </div>
        </div>
      )}

      {gen && <Waiting messages={waitingMessages(req, children)} onCancel={cancel} />}

      {step === "res" && (
        <div className="flex flex-col gap-3">
          <div className="flex animate-fade-in items-end justify-between gap-2.5">
            <div className="flex flex-col gap-0.5">
              <h2 className="font-display text-[25px] leading-[1.1] font-extrabold tracking-[-0.5px]">{resultTitle(shown.length + pending, req, children)}</h2>
              <button type="button" onClick={() => setStep("compose")} className="self-start py-1 text-[13px] font-semibold text-sec underline">
                {resultSubtitle(req)} — modifier
              </button>
            </div>
            <button
              type="button"
              onClick={() => void generate()}
              aria-label="Proposer d'autres idées"
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[14px] bg-livre-bg text-prune"
            >
              <Icon name="refresh" size={20} className="ai-spin" />
            </button>
          </div>
          {error && <ErrorNote>{error}</ErrorNote>}
          {cards.map((c, i) => (
            <IdeaCard key={c.id} card={c} delay={c.gone ? 0 : i < FIRST ? i * 220 : 80} onKeep={() => toggleKeep(c.id)} onDrop={() => drop(c.id)} />
          ))}
          {Array.from({ length: pending }, (_, i) => (
            <div key={`p${i}`} role="status" className="add-shim flex h-[120px] items-center justify-center gap-2 rounded-[22px] text-sm font-semibold text-sec">
              <Icon name="sparkles" size={16} className="ai-twinkle text-prune" />
              Une autre idée arrive…
            </div>
          ))}
        </div>
      )}

      {step === "done" && saved && (
        <Done
          links={saved.links}
          folder={saved.folder}
          onRestart={restart}
          onOpen={() => onSaved(saved.folder?.id, saved.links)}
        />
      )}

      {picking && (
        <FolderPicker
          folders={writable}
          links={links}
          current={folderTarget === NEW_FOLDER ? undefined : targetFolder ? folderTarget : ""}
          userId={session?.userId}
          peers={peers}
          onClose={() => setPicking(false)}
          onPick={(id) => {
            setTarget(id || "none");
            setPicking(false);
          }}
          onCreate={async (name) => {
            const f = await api.folders.create({ name, icon: "MAGIC", color: "PURPLE", visibility: "VISIBILITY_PRIVATE" });
            upsertFolder(f);
            setTarget(f.id);
            setPicking(false);
          }}
        />
      )}
    </Sheet>
  );

  /** Un mot de la phrase à toucher pour le changer. */
  function blank(k: BlankKey) {
    const on = edit === k;
    const [bg, fg] = BLANK_COLORS[k];
    return (
      <button
        key={`${k}-${cascade}`}
        type="button"
        aria-expanded={on}
        onClick={() => setEdit(on ? null : k)}
        className={`rounded-small border-2 px-2.5 py-1 font-display text-[22px] font-bold transition-colors ${cascade > 0 ? "ai-blank" : ""} ${on ? "border-ecorce" : "border-transparent"}`}
        style={{ background: bg, color: fg, animationDelay: `${BLANKS.indexOf(k) * 90}ms` }}
      >
        {blankLabel(k, req, children) || "…"}
      </button>
    );
  }
}

/** Le tipi s'illumine, des messages rassurants défilent. */
function Waiting({ messages, onCancel }: { messages: string[]; onCancel: () => void }) {
  const [i, setI] = useState(0);
  useEffect(() => {
    const t = setInterval(() => setI((n) => Math.min(n + 1, messages.length - 1)), 2700);
    return () => clearInterval(t);
  }, [messages.length]);
  return (
    <div role="status" className="flex min-h-[420px] animate-fade-in flex-col items-center justify-center gap-[30px] px-2.5 text-sable">
      <div className="relative flex h-[200px] w-[200px] items-center justify-center">
        <span aria-hidden="true" className="ai-glow absolute inset-5 rounded-full bg-[radial-gradient(circle,rgba(255,200,87,0.45),rgba(255,200,87,0)_70%)]" />
        <div aria-hidden="true" className="ai-orbit absolute inset-0">
          <svg viewBox="0 0 24 24" width={22} height={22} className="icon absolute top-0 left-[89px] text-[#FFC857]"><path d={SPARKLE} /></svg>
          <svg viewBox="0 0 24 24" width={16} height={16} className="icon absolute top-[120px] right-2 text-braise"><path d={SPARKLE} /></svg>
          <svg viewBox="0 0 24 24" width={18} height={18} className="icon absolute top-[130px] left-2.5 text-soleil"><path d={SPARKLE} /></svg>
        </div>
        <svg width="130" height="113" viewBox="0 0 76 66" aria-hidden="true" className="relative">
          <path d="M31 4L44 22M45 4L32 22" stroke="#FBF3E7" strokeWidth="2.6" strokeLinecap="round" />
          <path d="M38 12L70 62H6Z" fill="#D45A43" stroke="#FBF3E7" strokeWidth="2.6" strokeLinejoin="round" />
          <path d="M38 34L52 62H24Z" fill="#FFC857" className="ai-glow" />
          <path d="M38 34L38 62H24Z" fill="#3A211B" stroke="#FBF3E7" strokeWidth="1.8" strokeLinejoin="round" className="ai-flap-l" />
          <path d="M38 34L52 62H38Z" fill="#F28C38" stroke="#FBF3E7" strokeWidth="1.8" strokeLinejoin="round" className="ai-flap-r" />
        </svg>
      </div>
      <p key={i} aria-live="polite" className="ai-msg h-[50px] text-center font-display text-xl font-bold">{messages[i]}</p>
      <div className="h-1.5 w-[70%] overflow-hidden rounded-full bg-white/20"><span className="ai-bar block h-full rounded-full bg-[#FFC857]" /></div>
      <button type="button" onClick={onCancel} className="h-11 rounded-[14px] border-[1.5px] border-sable/40 px-[18px] text-sm font-bold text-sable hover:bg-white/10">
        Modifier ma demande
      </button>
    </div>
  );
}

function IdeaCard({ card, delay, onKeep, onDrop }: { card: Card; delay: number; onKeep: () => void; onDrop: () => void }) {
  const cat = categoryMeta(card.cat);
  return (
    <div
      aria-hidden={card.gone}
      className={`flex flex-col gap-2.5 rounded-[22px] border-2 bg-white p-3.5 shadow-[0_3px_12px_rgba(58,33,27,0.06)] transition-colors ${card.gone ? "ai-out" : "add-up"} ${card.kept ? "border-prune" : "border-transparent"}`}
      style={{ animationDelay: `${delay}ms` }}
    >
      <div className="flex items-start gap-3">
        <span className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-[15px] ${cat.bg} ${cat.ink}`}>
          <Icon name={cat.icon} size={24} />
        </span>
        <span className="flex min-w-0 flex-1 flex-col gap-[3px]">
          <span className="font-display text-[17px] leading-[1.2] font-bold">{card.title}</span>
          <span className="text-[13px] text-sec">{[cat.label, card.ageRange].filter(Boolean).join(" · ")}</span>
          {card.description && <span className="text-[13px] text-ecorce-2">{card.description}</span>}
        </span>
      </div>
      {card.why && (
        <span className="flex items-center gap-1.5 text-[13px] font-semibold text-prune">
          <Icon name="sparkles" size={14} className="shrink-0" />
          {card.why}
        </span>
      )}
      <div className="flex gap-2">
        <button
          type="button"
          aria-pressed={card.kept}
          disabled={card.gone}
          onClick={onKeep}
          className={`flex h-11 flex-1 items-center justify-center gap-1.5 rounded-[14px] text-sm font-bold transition-colors active:scale-[.97] ${card.kept ? "bg-prune text-sable" : "bg-sable-2 text-ecorce"}`}
        >
          {card.kept && <Icon name="check" size={17} strokeWidth={2.8} className="add-pop" />}
          {card.kept ? "Gardée" : "Je garde"}
        </button>
        <button
          type="button"
          disabled={card.gone}
          onClick={onDrop}
          aria-label={`Pas pour nous : écarter « ${card.title} » et proposer autre chose`}
          className="flex h-11 w-11 items-center justify-center rounded-[14px] border-[1.5px] border-line-2 bg-sable text-sec active:scale-[.97]"
        >
          <Icon name="close" size={18} />
        </button>
      </div>
    </div>
  );
}

const SPARKS: [number, number, string, string?][] = [
  [0, -52, "#F28C38"], [40, -34, "#FFC857", "2px"], [50, 4, "#C8492C"], [-40, -34, "#3A211B", "tri"], [-50, 2, "#F28C38", "2px"],
];

/** Les idées gardées s'envolent dans le dossier. */
function Done({ links, folder, onRestart, onOpen }: { links: Link[]; folder?: Folder; onRestart: () => void; onOpen: () => void }) {
  const ink = folder ? folderColor(folder.color).solid : "#5E4558";
  const n = links.length;
  return (
    <div className="flex flex-col gap-[18px]">
      <div className="relative h-[260px] overflow-hidden rounded-[26px] bg-livre-bg">
        <span aria-hidden="true" className="absolute -bottom-[120px] left-1/2 -ml-[150px] h-[300px] w-[300px] rounded-full bg-soleil" />
        {links.slice(0, 3).map((l, j) => (
          <div
            key={l.id}
            aria-hidden="true"
            className="add-fly absolute w-[92px] overflow-hidden rounded-xl bg-white shadow-[0_8px_18px_rgba(58,33,27,0.2)]"
            style={{ left: `calc(50% - ${155 - j * 22}px)`, top: 50 + j * 26, animationDelay: `${250 + j * 160}ms` }}
          >
            <div className={`h-9 ${categoryMeta(l.category).bg}`} />
            <div className="truncate px-[7px] py-[5px] text-[10px] font-bold">{l.title}</div>
          </div>
        ))}
        <div aria-hidden="true" className="add-bump absolute bottom-[46px] left-[calc(50%+30px)] h-[68px] w-[78px]">
          <span className="absolute top-0 left-0 h-3.5 w-[34px] rounded-t-[7px] rounded-tr-[9px]" style={{ background: ink }} />
          <span className="absolute inset-x-0 top-2.5 bottom-0 flex items-center justify-center rounded-[16px] rounded-tl-none text-white" style={{ background: ink }}>
            {folder ? <FolderGlyph icon={folder.icon} size={28} /> : <Icon name="idee" size={28} />}
          </span>
          {SPARKS.map(([dx, dy, bg, shape], i) => (
            <span
              key={i}
              className="add-spark"
              style={{
                "--dx": `${dx}px`, "--dy": `${dy}px`, background: bg,
                borderRadius: shape === "tri" ? 0 : shape ?? "50%",
                clipPath: shape === "tri" ? "polygon(50% 0, 100% 100%, 0 100%)" : undefined,
              } as CSSProperties}
            />
          ))}
        </div>
      </div>
      <div className="add-late flex flex-col gap-1 text-center">
        <h2 className="font-display text-[28px] font-extrabold tracking-[-0.6px]">{n} idée{n > 1 ? "s rangées" : " rangée"} !</h2>
        <span className="text-[15px] text-ecorce-2">
          dans <strong>{folder?.name ?? "vos idées non rangées"}</strong>. Bonne aventure à la tribu !
        </span>
      </div>
      <div className="add-late flex gap-2.5">
        <button type="button" onClick={onRestart} className="h-14 rounded-medium border-2 border-ecorce px-4 text-[15px] font-bold active:scale-[.97]">
          Nouvelle demande
        </button>
        <button type="button" onClick={onOpen} className="h-14 flex-1 rounded-medium bg-ecorce text-[15px] font-bold text-sable active:scale-[.97]">
          {folder ? "Voir le dossier" : "Voir mes idées"}
        </button>
      </div>
    </div>
  );
}
