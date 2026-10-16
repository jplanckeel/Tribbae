import { useMemo, useState, type FormEvent } from "react";
import * as api from "../lib/api";
import { CATEGORIES, categoryMeta } from "../lib/categories";
import { useMyData } from "../lib/data";
import { safeImage } from "../lib/format";
import { canEditFolder, tagCounts } from "../lib/links";
import { useSession } from "../lib/session";
import type { CategoryKey, LinkInput } from "../lib/types";
import Icon from "./Icon";
import Sheet from "./Sheet";
import { Button, ErrorNote, Field, Spinner, TextArea, Toggle } from "./ui";

/** À monter uniquement quand la feuille est ouverte : l'état part de `initial`. */
export interface LinkFormProps {
  onClose: () => void;
  /** Valeurs initiales (édition ou création pré-remplie). */
  initial: LinkInput;
  editing: boolean;
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

export default function LinkFormSheet({ onClose, initial, editing, onSubmit }: LinkFormProps) {
  const { folders, links } = useMyData();
  const session = useSession();
  const [form, setForm] = useState<LinkInput>(initial);
  const [tagDraft, setTagDraft] = useState("");
  const [ingredients, setIngredients] = useState((initial.ingredients ?? []).join("\n"));
  // Les champs secondaires sont dépliés s'ils sont déjà renseignés
  const [more, setMore] = useState(!!(initial.ageRange || initial.price || initial.location || initial.eventDate || initial.rating));
  const [saving, setSaving] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [error, setError] = useState("");

  const set = <K extends keyof LinkInput>(k: K, v: LinkInput[K]) => setForm((f) => ({ ...f, [k]: v }));
  const cat = categoryMeta(form.category);
  const knownTags = useMemo(() => tagCounts(links).map(([t]) => t), [links]);
  const isRecipe = form.category === "LINK_CATEGORY_RECETTE";
  const isDated = DATED.includes(form.category) || !!form.eventDate;

  const fetchPreview = async () => {
    const url = form.url?.trim();
    if (!url || form.title.trim()) return;
    setPreviewing(true);
    try {
      const p = await api.links.preview(url);
      setForm((f) => ({
        ...f,
        title: f.title || p.title || "",
        description: f.description || p.description || "",
        imageUrl: f.imageUrl || p.image || "",
      }));
    } catch {
      // Aperçu facultatif : l'utilisateur complète à la main
    } finally {
      setPreviewing(false);
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

  const editableFolders = folders.filter((f) => canEditFolder(f, session?.userId) || f.id === initial.folderId);
  const img = safeImage(form.imageUrl);

  return (
    <Sheet
      open
      onClose={onClose}
      title={editing ? "Modifier l'idée" : `Nouvelle ${cat.label.toLowerCase()}`}
      wide
      footer={
        <div className="flex gap-2.5">
          <Button type="button" variant="ghost" onClick={onClose}>Annuler</Button>
          <Button type="submit" form="link-form" block loading={saving}>
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
                  onClick={() => set("category", c.key)}
                  className={`flex h-[38px] shrink-0 items-center gap-1.5 rounded-full px-3.5 text-sm font-semibold transition-colors ${active ? "bg-ecorce text-sable" : `${c.bg} ${c.ink}`}`}
                >
                  <Icon name={c.icon} size={16} />
                  {c.label}
                </button>
              );
            })}
          </div>
        </fieldset>

        <Field
          label="Lien (facultatif)"
          icon="link"
          type="url"
          inputMode="url"
          placeholder="https://…"
          value={form.url ?? ""}
          onChange={(e) => set("url", e.target.value)}
          onBlur={fetchPreview}
          hint={previewing ? "Récupération du titre et de l'image…" : undefined}
        />

        <div className="flex gap-3">
          <div className="min-w-0 flex-1">
            <Field
              label="Titre"
              required
              maxLength={300}
              placeholder="Fête de la courge à la ferme"
              value={form.title}
              onChange={(e) => set("title", e.target.value)}
            />
          </div>
          {previewing && <Spinner size={22} className="mt-10 text-terracotta" />}
        </div>

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
              value={form.folderId ?? ""}
              onChange={(e) => set("folderId", e.target.value)}
              className="h-[52px] w-full appearance-none rounded-m border-[1.5px] border-line-2 bg-white px-4 pr-10 text-[15px] font-medium text-ecorce outline-none focus:border-terracotta"
            >
              <option value="">Non rangée</option>
              {editableFolders.map((f) => (
                <option key={f.id} value={f.id}>{f.name}</option>
              ))}
            </select>
            <Icon name="chevronDown" size={18} className="pointer-events-none absolute top-1/2 right-4 -translate-y-1/2 text-muted" />
          </div>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="f-tags" className="text-[13px] font-bold text-sec">Tags</label>
          <div className="flex min-h-[52px] flex-wrap items-center gap-1.5 rounded-m border-[1.5px] border-line-2 bg-white px-3 py-2 focus-within:border-terracotta">
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

        <button
          type="button"
          onClick={() => setMore((m) => !m)}
          aria-expanded={more}
          className="flex items-center gap-2 self-start text-sm font-bold text-terracotta"
        >
          <Icon name="chevronDown" size={18} className={`transition-transform ${more ? "rotate-180" : ""}`} />
          {more ? "Moins de détails" : "Âge, prix, lieu, date…"}
        </button>

        {(more || isDated) && (
          <div className="grid gap-4 sm:grid-cols-2">
            {more && (
              <>
                <Field label="Âge" icon="person" placeholder="3–10 ans" maxLength={200} value={form.ageRange ?? ""} onChange={(e) => set("ageRange", e.target.value)} />
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
              <label className="flex items-center gap-3 self-end rounded-m bg-soleil px-3.5 py-3">
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
                      className="flex h-10 w-10 items-center justify-center rounded-s text-braise hover:bg-sable-2"
                    >
                      <Icon name="star" size={24} filled={(form.rating ?? 0) >= n} />
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {img && (
          <img src={img} alt="Aperçu de l'image" referrerPolicy="no-referrer" className="h-36 w-full rounded-l object-cover" />
        )}

        <label className="flex items-center gap-3 rounded-m bg-white px-4 py-3.5 shadow-card">
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

        {error && <ErrorNote>{error}</ErrorNote>}
      </form>
    </Sheet>
  );
}
