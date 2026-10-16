import { useState, type FormEvent } from "react";
import { FOLDER_COLORS, FOLDER_ICONS, folderColor } from "../lib/categories";
import type { Folder, FolderInput, Visibility } from "../lib/types";
import Icon, { type IconName } from "./Icon";
import Sheet from "./Sheet";
import { Button, ErrorNote, Field } from "./ui";

const VISIBILITIES: { key: Visibility; label: string; hint: string; icon: IconName }[] = [
  { key: "VISIBILITY_PRIVATE", label: "Privé", hint: "Vous et les membres invités", icon: "lock" },
  { key: "VISIBILITY_PUBLIC", label: "Public", hint: "Vos idées du dossier visibles par tous, dans l'Explorer", icon: "globe" },
];

const empty: FolderInput = { name: "", icon: "FOLDER", color: "ORANGE", visibility: "VISIBILITY_PRIVATE" };

/** À monter uniquement quand la feuille est ouverte. */
export default function FolderFormSheet({ onClose, folder, onSubmit }: {
  onClose: () => void;
  folder?: Folder;
  onSubmit: (data: FolderInput) => Promise<void>;
}) {
  const [form, setForm] = useState<FolderInput>(() =>
    folder
      ? {
          name: folder.name,
          icon: folder.icon || "FOLDER",
          color: folder.color || "ORANGE",
          // « Partagé » est un état dérivé des collaborateurs : on le présente comme privé
          visibility: folder.visibility === "VISIBILITY_PUBLIC" ? "VISIBILITY_PUBLIC" : (folder.visibility ?? "VISIBILITY_PRIVATE"),
          bannerUrl: folder.bannerUrl,
          tags: folder.tags,
        }
      : empty,
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const color = folderColor(form.color);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) return setError("Donnez un nom au dossier.");
    setSaving(true);
    setError("");
    try {
      await onSubmit({ ...form, name: form.name.trim() });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Sheet
      open
      onClose={onClose}
      title={folder ? "Modifier le dossier" : "Nouveau dossier"}
      footer={
        <Button type="submit" form="folder-form" block loading={saving}>
          {folder ? "Enregistrer" : "Créer le dossier"}
        </Button>
      }
    >
      <form id="folder-form" onSubmit={submit} className="flex flex-col gap-5" noValidate>
        <div className="flex items-center gap-3">
          <span
            aria-hidden="true"
            className="flex h-14 w-14 shrink-0 items-center justify-center rounded-m text-white transition-colors"
            style={{ background: color.solid }}
          >
            <Icon name={FOLDER_ICONS.find((i) => i.key === form.icon)?.icon ?? "tipi"} size={26} />
          </span>
          <div className="flex-1">
            <Field
              label="Nom"
              required
              maxLength={100}
              placeholder="Vacances Drôme"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
          </div>
        </div>

        <fieldset>
          <legend className="mb-2 text-[13px] font-bold text-sec">Couleur</legend>
          <div className="flex flex-wrap gap-2.5">
            {FOLDER_COLORS.map((c) => (
              <button
                key={c.key}
                type="button"
                aria-label={c.label}
                aria-pressed={form.color === c.key}
                onClick={() => setForm({ ...form, color: c.key })}
                className={`h-10 w-10 rounded-full border-[3px] transition-transform ${form.color === c.key ? "scale-110 border-ecorce" : "border-white"}`}
                style={{ background: c.solid }}
              />
            ))}
          </div>
        </fieldset>

        <fieldset>
          <legend className="mb-2 text-[13px] font-bold text-sec">Icône</legend>
          <div className="grid grid-cols-7 gap-2">
            {FOLDER_ICONS.map((i) => {
              const active = form.icon === i.key;
              return (
                <button
                  key={i.key}
                  type="button"
                  aria-label={i.label}
                  title={i.label}
                  aria-pressed={active}
                  onClick={() => setForm({ ...form, icon: i.key })}
                  className={`flex aspect-square items-center justify-center rounded-s transition-colors ${active ? "text-white" : "bg-white text-ecorce hover:bg-sable-2"}`}
                  style={active ? { background: color.solid } : undefined}
                >
                  <Icon name={i.icon} size={20} />
                </button>
              );
            })}
          </div>
        </fieldset>

        <fieldset>
          <legend className="mb-2 text-[13px] font-bold text-sec">Visibilité</legend>
          <div className="grid grid-cols-2 gap-2">
            {VISIBILITIES.map((v) => {
              const active = form.visibility === v.key || (v.key === "VISIBILITY_PRIVATE" && form.visibility === "VISIBILITY_SHARED");
              return (
                <button
                  key={v.key}
                  type="button"
                  aria-pressed={active}
                  onClick={() => setForm({ ...form, visibility: v.key })}
                  className={`flex flex-col items-start gap-1 rounded-m border-2 p-3 text-left transition-colors ${active ? "border-ecorce bg-white" : "border-transparent bg-sable-2"}`}
                >
                  <span className="flex items-center gap-1.5 text-sm font-bold"><Icon name={v.icon} size={16} />{v.label}</span>
                  <span className="text-xs text-sec">{v.hint}</span>
                </button>
              );
            })}
          </div>
        </fieldset>

        {error && <ErrorNote>{error}</ErrorNote>}
      </form>
    </Sheet>
  );
}
