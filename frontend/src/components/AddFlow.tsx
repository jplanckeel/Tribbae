import { useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import { Link as RouterLink, useNavigate } from "react-router";
import { agoShort, bandsToAge, selectedBands, suggestFolders, whoSees } from "../lib/addFlow";
import { AGE_BANDS } from "../lib/age";
import * as api from "../lib/api";
import { AVATAR_COLORS, CATEGORIES, categoryMeta, folderColor } from "../lib/categories";
import { useMyData } from "../lib/data";
import { eventDateMs, formatShortDate, formatTime, hostname, safeImage } from "../lib/format";
import { canEditFolder, extractUrl, toInput } from "../lib/links";
import { usePhotoUpload } from "../lib/photo";
import { useSession } from "../lib/session";
import type { Folder, Link, LinkInput, LinkPreview } from "../lib/types";
import { FolderGlyph } from "./FolderCover";
import FolderPicker from "./FolderPicker";
import Icon, { type IconName } from "./Icon";
import { TipiMark } from "./Logo";
import { PhotoInput, PhotoPreview } from "./PhotoUpload";
import Sheet from "./Sheet";
import { Avatar, ErrorNote, Field, Toggle } from "./ui";

// Parcours « Ajouter une idée » (maquette « Ajouter une idée v2 ») :
// choisir un point de départ → (lire le lien) → ranger → c'est rangé !
// Même parcours côté Android : ui/AddFlowScreen.kt.

type Step = "choose" | "link" | "sort" | "done";
type Mode = "link" | "write" | "photo";
type PreviewState = { status: "idle" | "loading" | "done" | "failed"; data?: LinkPreview };

const STEPS: { label: string; at: Step }[] = [
  { label: "Trouver", at: "link" },
  { label: "Ranger", at: "sort" },
  { label: "Rangé", at: "done" },
];
const ORDER: Step[] = ["choose", "link", "sort", "done"];

const blank = (folderId?: string): LinkInput => ({
  title: "", url: "", description: "", category: "LINK_CATEGORY_IDEE", tags: [], visibility: "private", folderId,
});

/** À monter uniquement quand la feuille est ouverte. */
export default function AddFlow({ onClose, folderId: preset, onAi, onMore, onNewFolder }: {
  onClose: () => void;
  /** Dossier ouvert à l'écran : proposé en premier. */
  folderId?: string;
  onAi: () => void;
  /** Ouvre le formulaire complet (date, prix, lieu, tags…) avec ce qui est déjà saisi. */
  onMore: (draft: LinkInput) => void;
  onNewFolder: () => void;
}) {
  const session = useSession();
  const navigate = useNavigate();
  const { links, folders, peers, upsertLink, upsertFolder } = useMyData();

  const [step, setStep] = useState<Step>("choose");
  const [mode, setMode] = useState<Mode>("link");
  const [draft, setDraft] = useState<LinkInput>(() => blank(preset));
  const [catChosen, setCatChosen] = useState(false);
  const [folderId, setFolderId] = useState<string | undefined>(preset);
  const [preview, setPreview] = useState<PreviewState>({ status: "idle" });
  const [clip, setClip] = useState<string>();
  const [picker, setPicker] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [created, setCreated] = useState<Link>();
  const request = useRef(0);
  const set = (patch: Partial<LinkInput>) => setDraft((d) => ({ ...d, ...patch }));
  const { photo, send: sendPhoto, reset: resetPhoto } = usePhotoUpload();
  const photoInput = useRef<HTMLInputElement>(null);
  const pickPhoto = () => photoInput.current?.click();
  const choosePhoto = (file: Blob) => {
    set({ imageUrl: undefined });
    sendPhoto(file, (imageUrl) => set({ imageUrl }));
    if (step === "choose") start("photo");
  };

  // « Lien copié à l'instant » : seulement si le navigateur l'autorise déjà (pas de demande de permission)
  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const perm = await navigator.permissions?.query({ name: "clipboard-read" as PermissionName });
        if (perm?.state !== "granted") return;
        const found = extractUrl(await navigator.clipboard.readText());
        if (alive && found) setClip(found);
      } catch {
        // Presse-papiers indisponible : la tuile « Un lien » suffit
      }
    })();
    return () => {
      alive = false;
    };
  }, []);

  const editable = useMemo(() => folders.filter((f) => canEditFolder(f, session?.userId, peers)), [folders, session?.userId, peers]);
  const suggestions = useMemo(() => suggestFolders(draft.category, editable, links, preset), [draft.category, editable, links, preset]);
  const folder = folders.find((f) => f.id === folderId);
  const cat = categoryMeta(draft.category);
  const recent = useMemo(
    () => links.filter((l) => l.ownerId && l.ownerId !== session?.userId && peers.has(l.ownerId)).sort((a, b) => (b.createdAt ?? "").localeCompare(a.createdAt ?? ""))[0],
    [links, peers, session?.userId],
  );

  const readLink = (raw: string) => {
    const url = extractUrl(raw);
    if (!url) {
      setPreview({ status: "idle" });
      return;
    }
    set({ url });
    setPreview({ status: "loading" });
    const id = ++request.current;
    api.links.preview(url).then(
      (p) => {
        if (id !== request.current) return;
        setPreview({ status: "done", data: p });
        setDraft((d) => ({
          ...d,
          url,
          title: p.title || d.title,
          description: p.description || d.description,
          imageUrl: p.image || d.imageUrl,
          price: p.price || d.price,
          location: p.location || d.location,
          eventDate: p.eventDate || d.eventDate,
          ingredients: p.ingredients?.length ? p.ingredients : d.ingredients,
          category: !catChosen && p.category ? p.category : d.category,
        }));
      },
      () => id === request.current && setPreview({ status: "failed" }),
    );
  };

  const start = (m: Mode, url?: string) => {
    setMode(m);
    setError("");
    if (m === "link") {
      setStep("link");
      if (url) readLink(url);
    } else {
      toSort();
    }
  };

  const toSort = () => {
    // Premier passage : le dossier le mieux placé est présélectionné
    if (folderId === undefined) setFolderId(suggestions[0]?.folder.id ?? "");
    setStep("sort");
  };

  const back = () => setStep(step === "sort" && mode === "link" ? "link" : "choose");

  const submit = async () => {
    const title = (draft.title || (draft.url ? hostname(draft.url) : "")).trim();
    if (!title) return setError("Donnez un titre à votre idée.");
    if (mode === "photo" && photo?.status === "failed") return setError("La photo n'est pas partie : réessayez ou changez-la.");
    setSaving(true);
    setError("");
    try {
      const link = await api.links.create({ ...draft, title, folderId: folderId || undefined });
      upsertLink(link);
      setCreated(link);
      setStep("done");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const again = () => {
    setDraft(blank(preset));
    setCatChosen(false);
    setFolderId(preset);
    setPreview({ status: "idle" });
    setCreated(undefined);
    setError("");
    resetPhoto();
    setStep("choose");
  };

  const toggleReminder = async () => {
    if (!created) return;
    try {
      const saved = await api.links.update(created.id, { ...toInput(created), reminderEnabled: !created.reminderEnabled });
      upsertLink(saved);
      setCreated(saved);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const createFolder = async (name: string) => {
    const f = await api.folders.create({ name: name.slice(0, 100), icon: "FOLDER", color: "ORANGE", visibility: "VISIBILITY_PRIVATE" });
    upsertFolder(f);
    setFolderId(f.id);
    setPicker(false);
  };

  const here = ORDER.indexOf(step);
  const header = (
    <div className="flex h-12 items-center justify-between px-5 pt-2.5">
      {step === "link" || step === "sort" ? (
        <button type="button" onClick={back} aria-label="Étape précédente" className="flex h-11 w-11 items-center justify-center rounded-full bg-sable-2 hover:bg-line">
          <Icon name="chevronLeft" size={20} />
        </button>
      ) : (
        <span className="w-11" />
      )}
      <div aria-label={`Étape ${Math.max(here, 1)} sur 3`} className="flex items-end gap-2.5">
        {STEPS.map((d) => {
          const at = ORDER.indexOf(d.at);
          const on = here === at;
          const done = here > at || (d.at === "link" && mode !== "link" && here >= 2);
          return (
            <span key={d.label} className="flex flex-col items-center gap-[3px]">
              <svg
                width={22}
                height={20}
                viewBox="0 0 22 20"
                aria-hidden="true"
                className="transition-transform duration-[450ms] ease-[cubic-bezier(.34,1.56,.64,1)]"
                style={{ transform: `scale(${on ? 1.25 : 1})` }}
              >
                <path d="M11 2L20 18H2Z" fill={on || done ? "#D45A43" : "transparent"} stroke={on || done ? "var(--color-ecorce)" : "#CDB8AA"} strokeWidth={2} strokeLinejoin="round" />
              </svg>
              <span className={`text-[10px] font-bold transition-colors ${on ? "text-ecorce" : "text-muted"}`}>{d.label}</span>
            </span>
          );
        })}
      </div>
      <button type="button" data-close onClick={onClose} aria-label="Fermer" className="flex h-11 w-11 items-center justify-center rounded-full bg-sable-2 hover:bg-line">
        <Icon name="close" size={20} />
      </button>
    </div>
  );

  const footer =
    step === "link" ? (
      <Cta onClick={toSort} disabled={!draft.url} label="Ranger cette idée" icon="arrowRight" />
    ) : step === "sort" ? (
      <Cta onClick={submit} loading={saving || (mode === "photo" && photo?.status === "uploading")} label="Ajouter à la tribu" icon="check" />
    ) : step === "done" && created ? (
      <div className="flex gap-2.5">
        <button type="button" onClick={again} className="h-14 rounded-[18px] border-2 border-ecorce px-4 text-[15px] font-bold transition-transform active:scale-[0.97]">
          Une autre
        </button>
        <Cta
          dark
          onClick={() => {
            onClose();
            navigate(`/links/${created.id}`);
          }}
          label="Voir l'idée"
          icon="arrowRight"
        />
      </div>
    ) : undefined;

  return (
    <Sheet open onClose={onClose} title="Ajouter une idée" header={header} footer={footer} tall>
      <PhotoInput inputRef={photoInput} onFile={choosePhoto} />
      {step === "choose" && (
        <div className="flex flex-col gap-3.5">
          <h2 className="add-up font-display text-[28px] leading-[1.1] font-extrabold tracking-[-0.6px]">
            Une nouvelle idée
            <br />
            pour la tribu ? <span className="add-wave">✨</span>
          </h2>

          {clip && (
            <button
              type="button"
              onClick={() => start("link", clip)}
              className="add-up flex items-center gap-3 rounded-[20px] border-2 border-braise bg-peach p-3.5 text-left transition-transform active:scale-[0.97]"
              style={{ animationDelay: "80ms" }}
            >
              <span className="flex h-[46px] w-[46px] shrink-0 items-center justify-center rounded-[15px] bg-braise"><Icon name="clipboard" size={22} /></span>
              <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="text-xs font-bold text-[var(--color-cadeau-ink)]">Lien copié à l'instant</span>
                <span className="truncate text-[15px] font-bold">{clip.replace(/^https?:\/\/(www\.)?/, "")}</span>
              </span>
              <span className="rounded-xl bg-ecorce px-3 py-2 text-[13px] font-bold text-sable">Utiliser</span>
            </button>
          )}

          <span className="add-up mt-1 text-[13px] font-bold text-sec" style={{ animationDelay: "140ms" }}>Ou partir de…</span>
          <div className="grid grid-cols-3 gap-2.5">
            <Tile delay={180} icon="link" bg="var(--color-activite-bg)" ink="var(--color-activite-ink)" label="Un lien" onClick={() => start("link")} />
            <Tile delay={230} icon="pencil" bg="var(--color-idee-bg)" ink="var(--color-idee-ink)" label="Je l'écris" onClick={() => start("write")} />
            <Tile delay={280} icon="camera" bg="var(--color-recette-bg)" ink="var(--color-recette-ink)" label="Une photo" onClick={pickPhoto} />
          </div>

          <button
            type="button"
            onClick={onAi}
            className="add-up flex items-center gap-3 rounded-[22px] bg-prune p-4 text-left text-sable transition-transform active:scale-[0.97]"
            style={{ animationDelay: "340ms" }}
          >
            <span className="flex h-[46px] w-[46px] shrink-0 items-center justify-center rounded-[15px] bg-soleil text-prune"><Icon name="sparkle" size={22} /></span>
            <span className="flex flex-1 flex-col gap-0.5">
              <span className="font-display text-[17px] font-bold">Pas d'idée ? On en trouve ensemble</span>
              <span className="text-[13px] text-prune-soft">« Un samedi pluvieux avec un enfant de 6 ans »</span>
            </span>
          </button>

          {recent && (
            <div className="add-up mt-1 flex flex-col gap-2" style={{ animationDelay: "400ms" }}>
              <span className="text-[13px] font-bold text-sec">Ajoutée récemment par la tribu</span>
              <RouterLink to={`/links/${recent.id}`} onClick={onClose} className="flex items-center gap-2.5 rounded-2xl bg-card px-3 py-2.5 hover:bg-sable-2">
                <Avatar name={recent.ownerDisplayName} color={AVATAR_COLORS[3][0]} ink={AVATAR_COLORS[3][1]} size={32} rounded="rounded-[11px]" />
                <span className="min-w-0 flex-1 truncate text-[13px]"><strong>{recent.ownerDisplayName || "La tribu"}</strong> · {recent.title}</span>
                <span className="text-xs text-sec">{agoShort(recent.createdAt)}</span>
              </RouterLink>
            </div>
          )}

          <button type="button" onClick={onNewFolder} className="add-up self-start text-sm font-bold text-terracotta" style={{ animationDelay: "450ms" }}>
            Ou créer un dossier
          </button>
        </div>
      )}

      {step === "link" && (
        <div className="add-enter flex flex-col gap-3.5">
          <h2 className="font-display text-[26px] font-extrabold tracking-[-0.5px]">{preview.status === "idle" ? "Collez le lien" : "On regarde ce lien…"}</h2>
          <label className="flex h-[50px] items-center gap-2.5 rounded-2xl border-2 border-terracotta bg-card px-3.5 text-terracotta">
            <Icon name="link" size={18} />
            <span className="sr-only">Adresse de la page</span>
            <input
              autoFocus={!draft.url}
              type="text"
              inputMode="url"
              autoComplete="off"
              autoCapitalize="off"
              spellCheck={false}
              enterKeyHint="go"
              defaultValue={draft.url?.replace(/^https?:\/\//, "")}
              placeholder="Coller l'adresse d'une page"
              onPaste={(e) => {
                const found = extractUrl(e.clipboardData.getData("text"));
                if (found) {
                  e.preventDefault();
                  e.currentTarget.value = found.replace(/^https?:\/\//, "");
                  readLink(found);
                }
              }}
              onBlur={(e) => e.target.value.trim() && extractUrl(e.target.value) !== draft.url && readLink(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && readLink(e.currentTarget.value)}
              className="min-w-0 flex-1 bg-transparent text-sm text-ecorce-2 outline-none placeholder:text-muted"
            />
          </label>

          {preview.status === "loading" && (
            <div className="flex gap-3 rounded-[22px] bg-card p-3.5 shadow-card" aria-label="Lecture de la page…">
              <span className="add-shim h-11 w-11 rounded-xl" />
              <span className="flex flex-1 flex-col gap-2 pt-1">
                <span className="add-shim h-3 w-4/5 rounded-md" />
                <span className="add-shim h-3 w-1/2 rounded-md" />
              </span>
            </div>
          )}

          {(preview.status === "done" || preview.status === "failed") && (
            <>
              <div className="add-fill overflow-hidden rounded-[22px] bg-card shadow-[0_4px_14px_rgba(58,33,27,0.08)]">
                <div className={`relative h-[120px] overflow-hidden ${cat.bg}`}>
                  {safeImage(draft.imageUrl) ? (
                    <img src={safeImage(draft.imageUrl)} alt="" referrerPolicy="no-referrer" className="absolute inset-0 h-full w-full object-cover" />
                  ) : (
                    <>
                      <span aria-hidden="true" className="absolute -bottom-[90px] left-1/2 h-[200px] w-[200px] -translate-x-1/2 rounded-full bg-soleil" />
                      <span className="absolute bottom-2.5 left-1/2 -translate-x-1/2"><TipiMark size={70} /></span>
                    </>
                  )}
                </div>
                <div className="flex flex-col gap-2 px-3.5 pt-3 pb-3.5">
                  {preview.status === "failed" ? (
                    <span className="text-[13px] text-sec">Impossible de lire cette page : on complète ensemble à l'étape suivante.</span>
                  ) : (
                    <>
                      <span className="add-fill font-display text-lg font-bold" style={{ animationDelay: "100ms" }}>{draft.title || hostname(draft.url)}</span>
                      {draft.description && (
                        <span className="add-fill line-clamp-3 text-[13px] leading-[1.4] text-sec" style={{ animationDelay: "170ms" }}>{draft.description}</span>
                      )}
                    </>
                  )}
                </div>
              </div>
              {preview.status === "done" && <Found draft={draft} guessed={!!preview.data?.category} />}
            </>
          )}
        </div>
      )}

      {step === "sort" && (
        <div className="add-enter flex flex-col gap-4">
          <h2 className="font-display text-[26px] font-extrabold tracking-[-0.5px]">Où on la range ?</h2>

          {mode === "photo" && <PhotoPreview photo={photo} onPick={pickPhoto} onRetry={() => photo && choosePhoto(photo.file)} />}

          {(mode !== "link" || !draft.title) && (
            <div className="flex flex-col gap-3">
              <Field label="Le titre" autoFocus={mode !== "link"} maxLength={200} placeholder="Cabane dans les bois" value={draft.title} onChange={(e) => { set({ title: e.target.value }); setError(""); }} />
            </div>
          )}

          <div className="flex flex-col gap-2">
            <div className="flex items-baseline justify-between">
              <span className="text-[13px] font-bold text-sec">Dans quel dossier</span>
              <span className="flex items-center gap-1 text-xs text-muted"><Icon name="sparkle" size={12} className="text-terracotta" />suggérés pour {indefinite(cat.label)}</span>
            </div>
            {[...(folder && !suggestions.some((s) => s.folder.id === folder.id) ? [{ folder, reason: "Choisi dans la liste" }] : []), ...suggestions].slice(0, 3).map((s, i) => (
              <FolderChoice key={s.folder.id} folder={s.folder} reason={s.reason} on={folderId === s.folder.id} delay={i * 60} onClick={() => setFolderId(s.folder.id)} />
            ))}
            <button
              type="button"
              onClick={() => setPicker(true)}
              className="add-up flex h-12 items-center gap-2.5 rounded-2xl border-[1.5px] border-dashed border-dash px-3.5 text-sm font-semibold transition-transform active:scale-[0.97]"
              style={{ animationDelay: "200ms" }}
            >
              <Icon name="search" size={18} className="text-sec" />
              {folderId === "" ? "Sans dossier · " : ""}Chercher parmi mes {folders.length} dossiers
              <Icon name="chevronRight" size={18} className="ml-auto text-muted" />
            </button>
            <WhoSees folder={folder} isPublic={draft.visibility === "public"} />
          </div>

          <div className="flex flex-col gap-2">
            <span className="text-[13px] font-bold text-sec">C'est plutôt…</span>
            <div className="flex flex-wrap gap-2">
              {CATEGORIES.map((c) => {
                const on = draft.category === c.key;
                return (
                  <button
                    key={c.key}
                    type="button"
                    aria-pressed={on}
                    onClick={() => { set({ category: c.key }); setCatChosen(true); }}
                    className={`flex h-10 items-center gap-1.5 rounded-[13px] px-3.5 text-sm font-bold transition-all duration-[450ms] ease-[cubic-bezier(.34,1.56,.64,1)] ${on ? "scale-105 bg-ecorce text-sable" : `${c.bg} ${c.ink}`}`}
                  >
                    <Icon name={c.icon} size={16} />
                    {c.label}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <span className="text-[13px] font-bold text-sec">Pour quels âges</span>
            <div className="flex gap-2">
              {AGE_BANDS.map((b) => {
                const sel = selectedBands(draft.ageRange);
                const on = sel.includes(b.key);
                return (
                  <button
                    key={b.key}
                    type="button"
                    aria-pressed={on}
                    onClick={() => set({ ageRange: bandsToAge(on ? sel.filter((k) => k !== b.key) : [...sel, b.key]) })}
                    className={`h-11 flex-1 rounded-[13px] border-2 text-sm font-bold transition-colors ${on ? "border-terracotta bg-blush text-terracotta-dark" : "border-line-2"}`}
                  >
                    {b.label}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex items-center gap-3 rounded-2xl bg-card px-3.5 py-3">
            <Icon name="globe" size={20} className="text-activite-ink" />
            <span className="flex flex-1 flex-col">
              <span className="text-sm font-bold">Inspirer d'autres familles</span>
              <span className="text-xs text-sec">Visible aussi dans Explorer</span>
            </span>
            <Toggle label="Inspirer d'autres familles" checked={draft.visibility === "public"} onChange={(v) => set({ visibility: v ? "public" : "private" })} />
          </div>

          <button type="button" onClick={() => onMore({ ...draft, folderId: folderId || undefined })} className="self-start text-sm font-bold text-terracotta">
            Plus de détails (date, prix, lieu, tags…)
          </button>
          {error && <ErrorNote>{error}</ErrorNote>}
        </div>
      )}

      {step === "done" && created && (
        <Done link={created} folder={folders.find((f) => f.id === created.folderId)} onReminder={toggleReminder} />
      )}
      {error && step !== "sort" && <ErrorNote>{error}</ErrorNote>}

      {picker && (
        <FolderPicker
          folders={folders}
          links={links}
          current={folderId}
          userId={session?.userId}
          peers={peers}
          onClose={() => setPicker(false)}
          onPick={(id) => { setFolderId(id); setPicker(false); }}
          onCreate={createFolder}
        />
      )}
    </Sheet>
  );
}

const indefinite = (label: string) => (/^[aeéiouy]/i.test(label) ? "une " : ["Cadeau", "Livre"].includes(label) ? "un " : "une ") + label.toLowerCase();

function Cta({ label, icon, onClick, disabled, loading, dark }: { label: string; icon: IconName; onClick: () => void; disabled?: boolean; loading?: boolean; dark?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled || loading}
      className={`flex h-14 flex-1 items-center justify-center gap-2 rounded-[18px] text-base font-bold text-white transition-transform active:scale-[0.97] disabled:opacity-55 ${dark ? "bg-ecorce" : "w-full bg-terracotta shadow-[0_8px_20px_rgba(200,73,44,0.28)]"}`}
    >
      {loading ? "Un instant…" : label}
      <Icon name={icon} size={18} strokeWidth={2.4} />
    </button>
  );
}

function Tile({ icon, bg, ink, label, delay, onClick }: { icon: IconName; bg: string; ink: string; label: string; delay: number; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="add-up flex h-28 flex-col items-center justify-center gap-2 rounded-[20px] bg-card text-[13px] font-bold shadow-[0_3px_10px_rgba(58,33,27,0.06)] transition-transform active:scale-[0.97]"
      style={{ animationDelay: `${delay}ms` }}
    >
      <span className="flex h-11 w-11 items-center justify-center rounded-[14px]" style={{ background: bg, color: ink }}><Icon name={icon} size={22} /></span>
      {label}
    </button>
  );
}

/** « Tribbae a trouvé tout seul » : catégorie, date, prix, lieu lus sur la page. */
function Found({ draft, guessed }: { draft: LinkInput; guessed: boolean }) {
  const cat = categoryMeta(draft.category);
  const at = eventDateMs(draft.eventDate);
  const chips: { key: string; node: React.ReactNode; style?: CSSProperties; cls?: string }[] = [];
  if (guessed) chips.push({ key: "cat", node: cat.label, cls: `add-guess ${cat.bg} ${cat.ink}` });
  if (at) chips.push({ key: "date", node: <><Icon name="calendar" size={14} />{formatShortDate(at)}{new Date(at).getHours() ? ` · ${formatTime(at)}` : ""}</> });
  if (draft.price) chips.push({ key: "price", node: draft.price });
  if (draft.location) chips.push({ key: "loc", node: draft.location });
  if (draft.ingredients?.length) chips.push({ key: "ing", node: `${draft.ingredients.length} ingrédients` });
  if (!chips.length) return null;
  return (
    <div className="add-fill flex flex-col gap-2" style={{ animationDelay: "250ms" }}>
      <span className="flex items-center gap-1.5 text-[13px] font-bold text-sec"><Icon name="sparkle" size={14} className="text-terracotta" />Tribbae a trouvé tout seul</span>
      <div className="flex flex-wrap gap-2">
        {chips.map((c, i) => (
          <span key={c.key} className={`flex items-center gap-1.5 rounded-xl px-3 py-2 text-[13px] font-bold ${c.cls ?? "add-pop bg-card"}`} style={{ animationDelay: `${330 + i * 80}ms` }}>
            {c.node}
          </span>
        ))}
      </div>
    </div>
  );
}

function FolderChoice({ folder, reason, on, delay, onClick }: { folder: Folder; reason: string; on: boolean; delay: number; onClick: () => void }) {
  const c = folderColor(folder.color);
  return (
    <button
      type="button"
      aria-pressed={on}
      onClick={onClick}
      className={`add-up flex items-center gap-3 rounded-[18px] border-2 bg-card px-3 py-2.5 text-left transition-all duration-[450ms] ease-[cubic-bezier(.34,1.56,.64,1)] ${on ? "scale-[1.02] border-ecorce shadow-[0_8px_18px_rgba(58,33,27,0.14)]" : "border-transparent shadow-[0_2px_8px_rgba(58,33,27,0.05)]"}`}
      style={{ animationDelay: `${delay}ms` }}
    >
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[13px]" style={{ background: c.bg, color: c.solid }}><FolderGlyph icon={folder.icon} size={20} /></span>
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="truncate text-[15px] font-bold">{folder.name}</span>
        <span className="truncate text-xs text-sec">{reason}</span>
      </span>
      <span className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 text-sable transition-colors ${on ? "border-ecorce bg-ecorce" : "border-dash"}`}>
        {on && <Icon name="check" size={13} strokeWidth={3.2} className="add-pop" />}
      </span>
    </button>
  );
}

function WhoSees({ folder, isPublic }: { folder?: Folder; isPublic: boolean }) {
  const names = (folder?.collaborators ?? []).map((c) => c.displayName || c.email || "?");
  return (
    <div className={`flex items-center gap-2.5 rounded-[14px] px-3 py-2.5 transition-colors ${names.length ? "bg-peach" : "bg-sable-2"}`}>
      {names.length > 0 && (
        <span className="mr-1 flex">
          {names.slice(0, 3).map((n, i) => {
            const [bg, ink] = AVATAR_COLORS[(i + 1) % AVATAR_COLORS.length];
            return <Avatar key={i} name={n} color={bg} ink={ink} size={26} className={`add-pop border-2 border-[#FFF4EA] ${i ? "-ml-2" : ""}`} />;
          })}
        </span>
      )}
      <span className="flex-1 text-[13px] font-semibold">{whoSees(folder, isPublic)}</span>
    </div>
  );
}

// Éclats de la célébration : décalage, couleur, forme
const SPARKS: [number, number, string, string?][] = [
  [0, -50, "#F28C38"], [38, -34, "#FFC857", "2px"], [48, 4, "#C8492C"], [-38, -34, "#5E4558", "tri"], [-48, 2, "#F28C38", "2px"], [24, -54, "#5E4558"],
];

/** « C'est rangé ! » : la carte s'envole dans son dossier, avec quelques éclats. */
function Done({ link, folder, onReminder }: { link: Link; folder?: Folder; onReminder: () => void }) {
  const cat = categoryMeta(link.category);
  const ink = folder ? folderColor(folder.color).solid : "var(--color-sec)";
  const at = eventDateMs(link.eventDate);
  return (
    <div className="flex flex-col gap-4">
      <div className="relative h-[230px] overflow-hidden rounded-[26px] bg-sky">
        <span aria-hidden="true" className="add-sun absolute top-[34px] right-[58px] h-[54px] w-[54px] rounded-full bg-[#FFC857] shadow-[0_0_0_10px_rgba(255,200,87,0.25)]" />
        <span aria-hidden="true" className="absolute inset-x-0 bottom-0 h-[52px] border-t-[3px] border-ecorce bg-ground" />
        <div aria-hidden="true" className="add-fly absolute top-[70px] left-[calc(50%-125px)] w-[100px] overflow-hidden rounded-[14px] bg-card shadow-[0_8px_18px_rgba(58,33,27,0.2)]">
          <div className={`h-[46px] ${cat.bg}`}>
            {safeImage(link.imageUrl) && <img src={safeImage(link.imageUrl)} alt="" referrerPolicy="no-referrer" className="h-full w-full object-cover" />}
          </div>
          <div className="truncate px-2 py-1.5 text-[11px] font-bold">{link.title}</div>
        </div>
        <div aria-hidden="true" className="add-bump absolute bottom-10 left-[calc(50%+20px)] h-[66px] w-[76px]">
          <span className="absolute top-0 left-0 h-3.5 w-[34px] rounded-t-[7px] rounded-tr-[9px]" style={{ background: ink }} />
          <span className="absolute inset-x-0 top-2.5 bottom-0 flex items-center justify-center rounded-[16px] rounded-tl-none text-white" style={{ background: ink }}>
            {folder ? <FolderGlyph icon={folder.icon} size={28} /> : <Icon name="bookmark" size={28} />}
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
        <h2 className="font-display text-[28px] font-extrabold tracking-[-0.6px]">C'est rangé !</h2>
        <span className="text-[15px] text-ecorce-2">
          « {link.title} » est dans <strong>{folder?.name ?? "vos idées non rangées"}</strong>
        </span>
      </div>
      {at && (
        <button
          type="button"
          aria-pressed={!!link.reminderEnabled}
          onClick={onReminder}
          className={`add-later flex items-center gap-3 rounded-2xl border-2 px-3.5 py-3 text-left transition-colors ${link.reminderEnabled ? "border-terracotta bg-blush" : "border-transparent bg-card"}`}
        >
          <Icon name="bell" size={20} className={`text-terracotta ${link.reminderEnabled ? "add-pop" : ""}`} />
          <span className="flex-1 text-sm font-bold">{link.reminderEnabled ? `Rappel la veille, le ${formatShortDate(at - 86_400_000).toLowerCase()}, c'est noté` : "Me le rappeler la veille"}</span>
        </button>
      )}
    </div>
  );
}
