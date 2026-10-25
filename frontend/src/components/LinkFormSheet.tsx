import { useEffect, useEffectEvent, useMemo, useRef, useState, type FormEvent, type SyntheticEvent } from "react";
import * as api from "../lib/api";
import { CATEGORIES, categoryMeta } from "../lib/categories";
import { useMyData } from "../lib/data";
import { hostname, safeImage } from "../lib/format";
import { canEditFolder, extractUrl, tagCounts } from "../lib/links";
import { usePhotoUpload, type PhotoUpload } from "../lib/photo";
import { useSession } from "../lib/session";
import type { CategoryKey, LinkInput, LinkPreview } from "../lib/types";
import AgePicker from "./AgePicker";
import Icon from "./Icon";
import { PhotoInput, PhotoPreview } from "./PhotoUpload";
import Sheet from "./Sheet";
import { Button, ErrorNote, Field, Spinner, TextArea, Toggle } from "./ui";

/** À monter uniquement quand la feuille est ouverte : l'état part de `initial`. */
export interface LinkFormProps {
  onClose: () => void;
  /** Valeurs initiales (édition ou création pré-remplie). */
  initial: LinkInput;
  editing: boolean;
  /**
   * Nom de l'auteur quand l'idée appartient à un autre membre de la tribu :
   * on peut la modifier, mais seul son auteur décide de la publier.
   */
  authorName?: string;
  onSubmit: (data: LinkInput) => Promise<void>;
}

// datetime-local ↔ timestamp ms (heure locale)
const toLocalInput = (ms?: number) => {
  if (!ms) return "";
  const d = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
};
const fromLocalInput = (v: string) => (v ? new Date(v).getTime() : 0);

const DATED: CategoryKey[] = ["LINK_CATEGORY_EVENEMENT", "LINK_CATEGORY_ACTIVITE"];

type PreviewState = {
  status: "idle" | "loading" | "done" | "failed";
  /** Resynchronisation d'une idée existante : les infos de la page remplacent les siennes. */
  resync?: boolean;
  /** Adresse dont l'aperçu est en cours ou affiché. */
  url?: string;
  images: string[];
  site?: string;
};

/** Valeurs remplies par le dernier aperçu : on peut les remplacer si le lien change. */
type AutoFilled = Partial<Pick<LinkInput, "title" | "description" | "imageUrl" | "price" | "location" | "eventDate">> & { ingredients?: string };

// Remplace la valeur si elle est vide ou si elle venait de l'aperçu précédent
function fillEmpty<T>(current: T | undefined, previousAuto: T | undefined, next: T | undefined): T | undefined {
  if (!next) return current;
  return !current || current === previousAuto ? next : current;
}

// Resynchronisation : ce que la page donne remplace la valeur, le reste est gardé
const replace = <T,>(current: T | undefined, _previousAuto: T | undefined, next: T | undefined): T | undefined => next || current;

const NEW_FOLDER = "__new__";

export default function LinkFormSheet({ onClose, initial, editing, authorName, onSubmit }: LinkFormProps) {
  const { folders, links, peers, upsertFolder, children } = useMyData();
  const session = useSession();
  const [form, setForm] = useState<LinkInput>(initial);
  const [tagDraft, setTagDraft] = useState("");
  // Nom du dossier en cours de création depuis le choix du dossier (null : fermé)
  const [newFolder, setNewFolder] = useState<string | null>(null);
  const [creatingFolder, setCreatingFolder] = useState(false);
  const [ingredients, setIngredients] = useState((initial.ingredients ?? []).join("\n"));
  // Les champs secondaires sont dépliés s'ils sont déjà renseignés
  const [more, setMore] = useState(!!(initial.price || initial.location || initial.eventDate || initial.rating));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  // Photo de l'appareil, envoyée au serveur : elle devient l'image de l'idée
  const { photo, send: sendPhoto } = usePhotoUpload();
  const photoInput = useRef<HTMLInputElement>(null);
  const choosePhoto = (file: Blob) => sendPhoto(file, (url) => set("imageUrl", url));

  // Aperçu du lien : lancé dès l'ouverture si l'idée arrive avec une adresse
  const initialUrl = editing ? undefined : extractUrl(initial.url);
  const [preview, setPreview] = useState<PreviewState>(() =>
    initialUrl ? { status: "loading", url: initialUrl, images: [] } : { status: "idle", images: [] },
  );
  // La catégorie suggérée par la page ne remplace pas un choix explicite
  const [categoryChosen, setCategoryChosen] = useState(editing || initial.category !== "LINK_CATEGORY_IDEE");
  const autoFilled = useRef<AutoFilled>({});
  const requestId = useRef(0);

  const set = <K extends keyof LinkInput>(k: K, v: LinkInput[K]) => setForm((f) => ({ ...f, [k]: v }));
  const cat = categoryMeta(form.category);
  const knownTags = useMemo(() => tagCounts(links).map(([t]) => t), [links]);
  const isRecipe = form.category === "LINK_CATEGORY_RECETTE";
  const isDated = DATED.includes(form.category) || !!form.eventDate;

  const applyPreview = (p: LinkPreview, url: string, resync = false) => {
    const prev = autoFilled.current;
    const ingredientsText = (p.ingredients ?? []).join("\n");
    const fill = resync ? replace : fillEmpty;
    setForm((f) => ({
      ...f,
      title: fill(f.title, prev.title, p.title) ?? "",
      description: fill(f.description, prev.description, p.description),
      imageUrl: fill(f.imageUrl, prev.imageUrl, p.image),
      price: fill(f.price, prev.price, p.price),
      location: fill(f.location, prev.location, p.location),
      eventDate: fill(f.eventDate, prev.eventDate, p.eventDate),
      category: !categoryChosen && p.category ? p.category : f.category,
    }));
    setIngredients((cur) => fill(cur, prev.ingredients, ingredientsText) ?? "");
    autoFilled.current = {
      title: p.title, description: p.description, imageUrl: p.image, price: p.price,
      location: p.location, eventDate: p.eventDate, ingredients: ingredientsText,
    };
    if (p.price || p.location) setMore(true);
    setPreview({
      status: "done",
      resync,
      url,
      images: p.images?.length ? p.images : p.image ? [p.image] : [],
      site: p.siteName || hostname(url),
    });
  };

  // Seules les réponses de la dernière requête comptent (l'adresse a pu changer)
  const loadPreview = async (url: string, resync = false) => {
    const id = ++requestId.current;
    try {
      const p = await api.links.preview(url);
      if (id === requestId.current) applyPreview(p, url, resync);
    } catch {
      if (id === requestId.current) setPreview((s) => ({ ...s, status: "failed" }));
    }
  };

  const startPreview = (raw: string | undefined, force = false) => {
    const url = extractUrl(raw);
    if (!url) return;
    if (url !== form.url) set("url", url);
    if (!force && preview.url === url && preview.status !== "failed") return;
    setPreview({ status: "loading", url, images: [] });
    void loadPreview(url);
  };

  // Relit la page d'une idée existante et remplace ses infos par celles du lien
  const resync = () => {
    const url = extractUrl(form.url);
    if (!url) return;
    if (url !== form.url) set("url", url);
    setPreview({ status: "loading", resync: true, url, images: [] });
    void loadPreview(url, true);
  };

  const loadInitialPreview = useEffectEvent(() => {
    if (initialUrl) void loadPreview(initialUrl);
  });
  useEffect(() => loadInitialPreview(), []);

  const createFolder = async () => {
    const name = newFolder?.trim();
    if (!name || creatingFolder) return;
    setCreatingFolder(true);
    setError("");
    try {
      const folder = await api.folders.create({ name: name.slice(0, 80), icon: "FOLDER", color: "ORANGE", visibility: "VISIBILITY_PRIVATE" });
      upsertFolder(folder);
      set("folderId", folder.id);
      setNewFolder(null);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setCreatingFolder(false);
    }
  };

  const addTag = (raw: string) => {
    const t = raw.trim().replace(/^#/, "").toLowerCase();
    if (!t) return;
    setForm((f) => ((f.tags ?? []).includes(t) ? f : { ...f, tags: [...(f.tags ?? []), t].slice(0, 30) }));
    setTagDraft("");
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!form.title.trim()) return setError("Donnez un titre à votre idée.");
    setSaving(true);
    setError("");
    try {
      const pending = tagDraft.trim() ? [...(form.tags ?? []), tagDraft.trim().replace(/^#/, "").toLowerCase()] : form.tags;
      await onSubmit({
        ...form,
        title: form.title.trim(),
        tags: [...new Set(pending)],
        ingredients: isRecipe ? ingredients.split("\n").map((s) => s.trim()).filter(Boolean) : form.ingredients,
        reminderEnabled: form.eventDate ? form.reminderEnabled : false,
      });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const editableFolders = folders.filter((f) => canEditFolder(f, session?.userId, peers) || f.id === initial.folderId);
  // Dans son propre dossier public, une idée est visible de tous même sans être partagée à la communauté
  const chosenFolder = folders.find((f) => f.id === form.folderId);
  const inMyPublicFolder = chosenFolder?.visibility === "VISIBILITY_PUBLIC" && chosenFolder.ownerId === session?.userId;

  return (
    <Sheet
      open
      onClose={onClose}
      title={editing ? "Modifier l'idée" : `Nouvelle ${cat.label.toLowerCase()}`}
      wide
      footer={
        <div className="flex gap-2.5">
          <Button type="button" variant="ghost" onClick={onClose}>Annuler</Button>
          <Button type="submit" form="link-form" block loading={saving || photo?.status === "uploading"}>
            {editing ? "Enregistrer" : "Ajouter à la tribu"}
          </Button>
        </div>
      }
    >
      <form id="link-form" onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-[13px] font-bold text-sec">Catégorie</legend>
          <div className="no-scrollbar -mx-5 flex gap-2 overflow-x-auto px-5 sm:-mx-6 sm:px-6">
            {CATEGORIES.map((c) => {
              const active = c.key === form.category;
              return (
                <button
                  key={c.key}
                  type="button"
                  aria-pressed={active}
                  onClick={() => { set("category", c.key); setCategoryChosen(true); }}
                  className={`flex h-[38px] shrink-0 items-center gap-1.5 rounded-full px-3.5 text-sm font-semibold transition-colors ${active ? "bg-ecorce text-sable" : `${c.bg} ${c.ink}`}`}
                >
                  <Icon name={c.icon} size={16} />
                  {c.label}
                </button>
              );
            })}
          </div>
        </fieldset>

        <div className="flex flex-col gap-1.5">
          <Field
            label="Lien (facultatif)"
            icon="link"
            type="url"
            inputMode="url"
            autoCapitalize="off"
            spellCheck={false}
            placeholder="https://…"
            value={form.url ?? ""}
            onChange={(e) => set("url", e.target.value)}
            onBlur={() => startPreview(form.url)}
            onPaste={(e) => {
              const pasted = e.clipboardData.getData("text");
              if (extractUrl(pasted)) {
                e.preventDefault();
                startPreview(pasted);
              }
            }}
          />
          <PreviewStatus preview={preview} onRetry={() => (preview.resync ? resync() : startPreview(form.url, true))} />
          {editing && extractUrl(form.url) && preview.status !== "loading" && (
            <button
              type="button"
              onClick={resync}
              className="flex items-center gap-1.5 self-start text-[13px] font-bold text-terracotta hover:underline"
            >
              <Icon name="refresh" size={16} />
              Resynchroniser depuis le lien
            </button>
          )}
        </div>

        <PhotoInput inputRef={photoInput} onFile={choosePhoto} />
        <ImageChooser
          loading={preview.status === "loading"}
          candidates={preview.images}
          value={form.imageUrl}
          onChange={(u) => set("imageUrl", u)}
          photo={photo}
          onPhoto={() => photoInput.current?.click()}
          onRetry={() => photo && choosePhoto(photo.file)}
        />

        <Field
          label="Titre"
          required
          maxLength={300}
          placeholder={preview.status === "loading" ? "Récupération du titre…" : "Fête de la courge à la ferme"}
          value={form.title}
          onChange={(e) => set("title", e.target.value)}
        />

        <TextArea
          label="Description"
          maxLength={5000}
          placeholder="Ce qu'il faut savoir, ce qu'il faut emporter…"
          value={form.description ?? ""}
          onChange={(e) => set("description", e.target.value)}
        />

        {isRecipe && (
          <TextArea
            label="Ingrédients (un par ligne)"
            placeholder={"250 g de farine\n2 œufs\n50 cl de lait"}
            value={ingredients}
            onChange={(e) => setIngredients(e.target.value)}
          />
        )}

        <div className="flex flex-col gap-1.5">
          <label htmlFor="f-folder" className="text-[13px] font-bold text-sec">Dossier</label>
          <div className="relative">
            <select
              id="f-folder"
              value={newFolder !== null ? NEW_FOLDER : (form.folderId ?? "")}
              onChange={(e) => {
                if (e.target.value === NEW_FOLDER) return setNewFolder("");
                setNewFolder(null);
                set("folderId", e.target.value);
              }}
              className="h-[52px] w-full appearance-none rounded-medium border-[1.5px] border-line-2 bg-card px-4 pr-10 text-[15px] font-medium text-ecorce outline-none focus:border-terracotta"
            >
              <option value="">Non rangée</option>
              {editableFolders.map((f) => (
                <option key={f.id} value={f.id}>{f.name}</option>
              ))}
              <option value={NEW_FOLDER}>+ Nouveau dossier…</option>
            </select>
            <Icon name="chevronDown" size={18} className="pointer-events-none absolute top-1/2 right-4 -translate-y-1/2 text-muted" />
          </div>
          {newFolder !== null && (
            <div className="mt-1 flex flex-col gap-2 rounded-medium bg-card p-3">
              <div className="flex gap-2">
                <input
                  autoFocus
                  aria-label="Nom du nouveau dossier"
                  value={newFolder}
                  maxLength={80}
                  onChange={(e) => setNewFolder(e.target.value)}
                  onKeyDown={(e) => {
                    // Entrée crée le dossier, sans enregistrer l'idée
                    if (e.key === "Enter") {
                      e.preventDefault();
                      void createFolder();
                    } else if (e.key === "Escape") {
                      e.stopPropagation();
                      setNewFolder(null);
                    }
                  }}
                  placeholder="Vacances d'été, Noël…"
                  className="h-11 min-w-0 flex-1 rounded-small border-[1.5px] border-line-2 px-3 text-[15px] font-medium text-ecorce outline-none placeholder:text-muted focus:border-terracotta"
                />
                <Button type="button" size="sm" className="h-11" loading={creatingFolder} disabled={!newFolder.trim()} onClick={() => void createFolder()}>
                  Créer
                </Button>
                <Button type="button" size="sm" variant="ghost" className="h-11" onClick={() => setNewFolder(null)}>
                  Annuler
                </Button>
              </div>
              <p className="text-xs text-sec">Dossier privé : visible seulement par votre tribu.</p>
            </div>
          )}
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="f-tags" className="text-[13px] font-bold text-sec">Tags</label>
          <div className="flex min-h-[52px] flex-wrap items-center gap-1.5 rounded-medium border-[1.5px] border-line-2 bg-card px-3 py-2 focus-within:border-terracotta">
            {(form.tags ?? []).map((t) => (
              <span key={t} className="flex items-center gap-1 rounded-[10px] bg-sable-2 py-1 pr-1 pl-2.5 text-[13px] text-sec">
                #{t}
                <button
                  type="button"
                  aria-label={`Retirer le tag ${t}`}
                  onClick={() => set("tags", (form.tags ?? []).filter((x) => x !== t))}
                  className="flex h-5 w-5 items-center justify-center rounded-full hover:bg-line"
                >
                  <Icon name="close" size={12} />
                </button>
              </span>
            ))}
            <input
              id="f-tags"
              list="known-tags"
              value={tagDraft}
              onChange={(e) => setTagDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === ",") {
                  e.preventDefault();
                  addTag(tagDraft);
                } else if (e.key === "Backspace" && !tagDraft && form.tags?.length) {
                  set("tags", form.tags.slice(0, -1));
                }
              }}
              onBlur={() => addTag(tagDraft)}
              placeholder={form.tags?.length ? "" : "automne, plein-air…"}
              className="min-w-[120px] flex-1 bg-transparent px-1 text-[15px] outline-none placeholder:text-muted"
            />
            <datalist id="known-tags">
              {knownTags.map((t) => <option key={t} value={t} />)}
            </datalist>
          </div>
        </div>

        <AgePicker value={form.ageRange ?? ""} onChange={(v) => set("ageRange", v)} kids={children} />

        <button
          type="button"
          onClick={() => setMore((m) => !m)}
          aria-expanded={more}
          className="flex items-center gap-2 self-start text-sm font-bold text-terracotta"
        >
          <Icon name="chevronDown" size={18} className={`transition-transform ${more ? "rotate-180" : ""}`} />
          {more ? "Moins de détails" : "Prix, lieu, date…"}
        </button>

        {(more || isDated) && (
          <div className="grid gap-4 sm:grid-cols-2">
            {more && (
              <>
                <Field label="Prix" icon="euro" placeholder="Gratuit, 6 € / enfant…" maxLength={200} value={form.price ?? ""} onChange={(e) => set("price", e.target.value)} />
                <Field label="Lieu" icon="pin" placeholder="Ferme des Saules" maxLength={200} value={form.location ?? ""} onChange={(e) => set("location", e.target.value)} />
                <Field label="Image (adresse web)" icon="landscape" type="url" placeholder="https://…" value={form.imageUrl ?? ""} onChange={(e) => set("imageUrl", e.target.value)} />
              </>
            )}
            <Field
              label="Date"
              icon="calendar"
              type="datetime-local"
              value={toLocalInput(form.eventDate)}
              onChange={(e) => set("eventDate", fromLocalInput(e.target.value))}
            />
            {!!form.eventDate && (
              <label className="flex items-center gap-3 self-end rounded-medium bg-soleil px-3.5 py-3">
                <Icon name="bell" size={20} />
                <span className="flex-1 text-sm font-semibold">Me le rappeler la veille</span>
                <Toggle label="Rappel la veille" checked={!!form.reminderEnabled} onChange={(v) => set("reminderEnabled", v)} />
              </label>
            )}
            {more && (
              <div className="flex flex-col gap-1.5 sm:col-span-2">
                <span className="text-[13px] font-bold text-sec">Note de la tribu</span>
                <div className="flex gap-1" role="radiogroup" aria-label="Note">
                  {[1, 2, 3, 4, 5].map((n) => (
                    <button
                      key={n}
                      type="button"
                      role="radio"
                      aria-checked={form.rating === n}
                      aria-label={`${n} sur 5`}
                      onClick={() => set("rating", form.rating === n ? 0 : n)}
                      className="flex h-10 w-10 items-center justify-center rounded-small text-braise hover:bg-sable-2"
                    >
                      <Icon name="star" size={24} filled={(form.rating ?? 0) >= n} />
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {authorName === undefined ? (
          <label className="flex items-center gap-3 rounded-medium bg-card px-4 py-3.5 shadow-card">
            <Icon name="globe" size={20} className="text-activite-ic" />
            <span className="flex flex-1 flex-col">
              <span className="text-sm font-bold">Partager avec la communauté</span>
              <span className="text-xs text-sec">Visible dans l'Explorer par toutes les tribus</span>
            </span>
            <Toggle
              label="Partager avec la communauté"
              checked={form.visibility === "public"}
              onChange={(v) => set("visibility", v ? "public" : "private")}
            />
          </label>
        ) : (
          <p role="note" className="flex items-start gap-2 rounded-medium bg-card px-4 py-3 text-xs text-sec">
            <Icon name="lock" size={15} className="mt-px shrink-0" />
            Idée de {authorName || "un membre de la tribu"} : seul son auteur peut la partager avec la communauté.
          </p>
        )}
        {inMyPublicFolder && form.visibility !== "public" && (
          <p role="note" className="-mt-2 flex items-start gap-2 px-1 text-xs text-sec">
            <Icon name="eye" size={15} className="mt-px shrink-0" />
            Le dossier « {chosenFolder.name} » est public : cette idée y sera visible par tous ses visiteurs.
          </p>
        )}

        {error && <ErrorNote>{error}</ErrorNote>}
      </form>
    </Sheet>
  );
}

/** État de la lecture du lien, sous le champ « Lien ». */
function PreviewStatus({ preview, onRetry }: { preview: PreviewState; onRetry: () => void }) {
  if (preview.status === "loading") {
    return (
      <span role="status" className="flex items-center gap-1.5 text-xs text-sec">
        <Spinner size={14} className="text-terracotta" />
        {preview.resync ? "Resynchronisation depuis la page…" : "Lecture de la page…"}
      </span>
    );
  }
  if (preview.status === "failed") {
    return (
      <span role="status" className="flex flex-wrap items-center gap-x-2 text-xs text-sec">
        Impossible de lire cette page : complétez à la main.
        <button type="button" onClick={onRetry} className="font-bold text-terracotta hover:underline">Réessayer</button>
      </span>
    );
  }
  if (preview.status === "done") {
    return (
      <span role="status" className="flex items-center gap-1.5 text-xs text-sec">
        <Icon name="check" size={14} className="text-activite-ic" />
        {preview.resync
          ? `Infos mises à jour${preview.site ? ` depuis ${preview.site}` : ""} : vérifiez puis enregistrez`
          : `Infos récupérées${preview.site ? ` depuis ${preview.site}` : ""}`}
      </span>
    );
  }
  return null;
}

/**
 * Image de l'idée : grande vignette de l'image choisie et, s'il y en a
 * plusieurs, les autres images trouvées sur la page. Les images qui ne
 * chargent pas (liens morts, hotlinking refusé) ou trop petites (icônes,
 * pixels) sont écartées d'office.
 */
function ImageChooser({ loading, candidates, value, onChange, photo, onPhoto, onRetry }: {
  loading: boolean;
  candidates: string[];
  value?: string;
  onChange: (url: string) => void;
  /** Photo de l'appareil en cours d'envoi (son adresse arrive ensuite dans value). */
  photo?: PhotoUpload;
  onPhoto: () => void;
  onRetry: () => void;
}) {
  const [broken, setBroken] = useState<ReadonlySet<string>>(() => new Set());
  const list = [...new Set([...(safeImage(value) && value ? [value] : []), ...candidates])].filter((u) => !broken.has(u));
  const selected = value && list.includes(value) ? value : undefined;

  const discard = (u: string) => {
    setBroken((b) => new Set(b).add(u));
    if (u === value) {
      const next = list.find((c) => c !== u);
      if (next || candidates.includes(u)) onChange(next ?? "");
    }
  };
  const checkSize = (u: string) => (e: SyntheticEvent<HTMLImageElement>) => {
    const { naturalWidth: w, naturalHeight: h } = e.currentTarget;
    if (w > 0 && (w < 120 || h < 80)) discard(u);
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-baseline justify-between gap-2">
        <span className="text-[13px] font-bold text-sec">Image</span>
        {list.length > 1 && <span className="text-xs text-sec">{list.length} trouvées · touchez pour choisir</span>}
        <button type="button" onClick={onPhoto} className="ml-auto flex items-center gap-1 text-[13px] font-bold text-link hover:underline">
          <Icon name="camera" size={15} />
          {photo || selected ? "Changer de photo" : "Mettre une photo"}
        </button>
      </div>
      {photo && photo.status !== "done" ? (
        <PhotoPreview photo={photo} onPick={onPhoto} onRetry={onRetry} />
      ) : selected ? (
        <img
          key={selected}
          src={selected}
          alt="Image de l'idée"
          referrerPolicy="no-referrer"
          onError={() => discard(selected)}
          onLoad={checkSize(selected)}
          className="h-44 w-full animate-fade-in rounded-large bg-sable-2 object-cover"
        />
      ) : loading ? (
        <div aria-hidden="true" className="h-44 w-full animate-pulse rounded-large bg-sable-2" />
      ) : null}
      {(list.length > 1 || (list.length === 1 && !selected)) && (
        <div role="radiogroup" aria-label="Choisir l'image" className="no-scrollbar -mx-5 flex gap-2 overflow-x-auto px-5 py-1 sm:-mx-6 sm:px-6">
          {list.map((u, i) => (
            <button
              key={u}
              type="button"
              role="radio"
              aria-checked={u === value}
              aria-label={`Image ${i + 1} sur ${list.length}`}
              onClick={() => onChange(u)}
              className={`h-16 w-16 shrink-0 overflow-hidden rounded-small bg-sable-2 transition ${u === value ? "ring-[3px] ring-terracotta ring-offset-2 ring-offset-sable" : "opacity-75 hover:opacity-100"}`}
            >
              <img
                src={u}
                alt=""
                loading="lazy"
                referrerPolicy="no-referrer"
                onError={() => discard(u)}
                onLoad={checkSize(u)}
                className="h-full w-full object-cover"
              />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
