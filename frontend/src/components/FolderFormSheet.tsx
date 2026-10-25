import { useMemo, useState, type FormEvent, type ReactNode } from "react";
import { FOLDER_COLORS, FOLDER_ICONS, folderColor } from "../lib/categories";
import { useMyData } from "../lib/data";
import {
  COVERS, ILLUSTRATIONS, KEY_DATE_TYPES, SORTS, VIEW_MODES,
  autoIllustration, childColors, countdown, folderInput, resolvedCover, type CoverKey, type ViewMode,
} from "../lib/folderStyle";
import { safeImage } from "../lib/format";
import type { Folder, FolderInput, Visibility } from "../lib/types";
import { CoverArt, FolderGlyph, IllustrationArt } from "./FolderCover";
import Icon, { type IconName } from "./Icon";
import Sheet from "./Sheet";
import { Avatar, Button, ErrorNote, Field, Toggle } from "./ui";

const VISIBILITIES: { key: Visibility; label: string; hint: string; icon: IconName }[] = [
  { key: "VISIBILITY_PRIVATE", label: "Privé", hint: "Vous et les membres invités", icon: "lock" },
  { key: "VISIBILITY_PUBLIC", label: "Public", hint: "Vos idées du dossier visibles par tous, dans l'Explorer", icon: "globe" },
];

const TABS = [
  { key: "style", label: "Style" },
  { key: "infos", label: "Infos" },
  { key: "view", label: "Affichage" },
] as const;
type Tab = (typeof TABS)[number]["key"];

const empty: FolderInput = {
  name: "", icon: "CAMPING", color: "ORANGE", visibility: "VISIBILITY_PRIVATE",
  description: "", cover: "", illustration: "", keyDate: 0, keyDateType: "", childIds: [], viewMode: "", sort: "", pinned: false,
};

// <input type="date"> ↔ timestamp ms (minuit local)
const toDateInput = (ms: number) => {
  if (!ms) return "";
  const d = new Date(ms);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};
const fromDateInput = (v: string) => {
  const [y, m, d] = v.split("-").map(Number);
  return y && m && d ? new Date(y, m - 1, d).getTime() : 0;
};
const inDays = (n: number) => {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n).getTime();
};

/**
 * « Personnaliser » un dossier (maquette du même nom) : aperçu en direct de la
 * carte, puis Style (couleur, couverture, icône), Infos (nom, petit mot, date
 * clé, pour qui, visibilité) et Affichage (vue, tri, épinglage).
 * À monter uniquement quand la feuille est ouverte.
 */
export default function FolderFormSheet({ onClose, folder, onSubmit }: {
  onClose: () => void;
  folder?: Folder;
  onSubmit: (data: FolderInput) => Promise<void>;
}) {
  const { children, links } = useMyData();
  const [form, setForm] = useState<FolderInput>(() => (folder ? folderInput(folder) : empty));
  const [tab, setTab] = useState<Tab>(folder ? "style" : "infos");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const set = (patch: Partial<FolderInput>) => setForm((f) => ({ ...f, ...patch }));

  const color = folderColor(form.color);
  const keyDate = form.keyDate ?? 0;
  const draft: Folder = { ...form, id: folder?.id ?? "", ownerId: "", name: form.name || "Mon dossier", keyDate };
  const { cover, illustration } = resolvedCover(draft);
  const suggested = autoIllustration(form.name, keyDate || undefined, form.keyDateType);
  const photos = useMemo(
    () => [...new Set(links.filter((l) => folder && l.folderId === folder.id).map((l) => safeImage(l.imageUrl)).filter((u): u is string => !!u))].slice(0, 12),
    [links, folder],
  );
  const photo = cover === "photo" ? safeImage(form.bannerUrl) ?? photos[0] : undefined;
  const cd = countdown(keyDate || undefined, form.keyDateType);
  const kids = (form.childIds ?? []).map((id) => children.find((c) => c.id === id)).filter((c) => !!c);
  const linkCount = folder ? links.filter((l) => l.folderId === folder.id).length : 0;

  const submit = async (e?: FormEvent) => {
    e?.preventDefault();
    if (!form.name.trim()) {
      setTab("infos");
      return setError("Donnez un nom au dossier.");
    }
    setSaving(true);
    setError("");
    try {
      await onSubmit({ ...form, name: form.name.trim(), description: form.description?.trim() });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const pickCover = (k: CoverKey) => set(k === "illu" && !form.illustration ? { cover: k, illustration: illustration ?? suggested ?? "anniv" } : { cover: k });

  return (
    <Sheet
      open
      onClose={onClose}
      title={folder ? "Personnaliser" : "Nouveau dossier"}
      footer={
        <Button type="submit" form="folder-form" block loading={saving}>
          {folder ? "Enregistrer" : "Créer le dossier"}
        </Button>
      }
    >
      <form id="folder-form" onSubmit={submit} className="flex flex-col" noValidate>
        {/* Aperçu en direct */}
        <div className="sticky -top-3 z-10 -mx-5 bg-sable px-5 pt-1 pb-3 sm:-mx-6 sm:px-6">
          <div className="relative h-[214px]" aria-hidden="true">
            <div key={`${cover}-${illustration}`} className="absolute inset-x-0 top-0 h-32 animate-fade-in overflow-hidden rounded-[24px]">
              <CoverArt cover={cover} illustration={illustration} color={color} photo={photo} />
            </div>
            <div className="absolute inset-x-4 bottom-0 pt-3">
              <span className="absolute top-0 left-0 h-6 w-16 rounded-t-[10px] rounded-tr-[12px] bg-card" />
              <div className="relative flex items-center gap-3 rounded-[22px] rounded-tl-none bg-card p-3.5 shadow-[0_8px_22px_rgba(58,33,27,0.12)]">
                <span key={form.icon} className="flex h-[54px] w-[54px] shrink-0 animate-pop items-center justify-center rounded-[17px] text-on-solid transition-colors duration-500" style={{ background: color.solid }}>
                  <FolderGlyph icon={form.icon} size={27} />
                </span>
                <div className="flex min-w-0 flex-1 flex-col gap-[3px]">
                  <span className="truncate font-display text-[19px] leading-[1.1] font-extrabold">{form.name || "Mon dossier"}</span>
                  <span className="truncate text-[13px] text-sec">{form.description || `${linkCount} idée${linkCount > 1 ? "s" : ""}`}</span>
                  <div className="mt-0.5 flex min-h-6 items-center gap-1.5">
                    {cd && <span className="rounded-lg px-2 py-[3px] text-xs font-extrabold transition-colors duration-500" style={{ background: color.bg, color: color.solid }}>{cd.label}</span>}
                    <span className="flex">
                      {kids.map((k, i) => {
                        const [bg, ink] = childColors(children, k.id);
                        return <Avatar key={k.id} name={k.name} color={bg} ink={ink} size={24} className={`border-2 border-card ${i ? "-ml-[7px]" : ""}`} />;
                      })}
                    </span>
                  </div>
                </div>
                {form.pinned && (
                  <span className="absolute -top-2 right-3 flex h-7 w-7 items-center justify-center rounded-full bg-ecorce text-sable">
                    <svg width={14} height={14} viewBox="0 0 24 24" className="icon"><path d="M9 4h6l-1 6 3 3H7l3-3z M12 13v7" /></svg>
                  </span>
                )}
              </div>
            </div>
          </div>

          <div role="tablist" aria-label="Personnalisation" className="relative mt-3.5 grid h-12 grid-cols-3 rounded-2xl bg-sable-2 p-1">
            <span
              aria-hidden="true"
              className="absolute top-1 bottom-1 left-1 w-[calc((100%-8px)/3)] rounded-xl bg-card shadow-[0_2px_8px_rgba(58,33,27,0.12)] transition-transform duration-300 ease-[cubic-bezier(.34,1.56,.64,1)]"
              style={{ transform: `translateX(${TABS.findIndex((t) => t.key === tab) * 100}%)` }}
            />
            {TABS.map((t) => (
              <button
                key={t.key}
                type="button"
                role="tab"
                id={`folder-tab-${t.key}`}
                aria-selected={tab === t.key}
                aria-controls="folder-tabpanel"
                onClick={() => setTab(t.key)}
                className={`relative text-sm transition-colors ${tab === t.key ? "font-bold text-ecorce" : "font-semibold text-sec"}`}
              >
                {t.label}
              </button>
            ))}
          </div>
        </div>

        <div id="folder-tabpanel" role="tabpanel" aria-labelledby={`folder-tab-${tab}`} key={tab} className="flex animate-fade-in flex-col gap-[18px] pt-1">
          {tab === "style" && (
            <>
              <Group label="Couleur">
                <div className="flex flex-wrap gap-0.5">
                  {FOLDER_COLORS.map((c) => {
                    const on = form.color === c.key;
                    return (
                      <button key={c.key} type="button" aria-label={c.label} aria-pressed={on} onClick={() => set({ color: c.key })} className="flex h-11 w-11 items-center justify-center">
                        <span
                          className="h-8 w-8 rounded-full transition-all duration-300 ease-[cubic-bezier(.34,1.56,.64,1)]"
                          style={{ background: c.solid, boxShadow: on ? `0 0 0 3px #FBF3E7, 0 0 0 5px ${c.solid}` : "none", transform: `scale(${on ? 1 : 0.86})` }}
                        />
                      </button>
                    );
                  })}
                </div>
              </Group>

              <Group label="Couverture" hint={!form.cover ? "· automatique" : undefined}>
                <div className="grid grid-cols-3 gap-2">
                  {COVERS.map((v) => {
                    const on = cover === v.key;
                    return (
                      <button
                        key={v.key}
                        type="button"
                        aria-pressed={!!form.cover && on}
                        onClick={() => pickCover(v.key)}
                        className={`relative flex h-16 min-w-0 items-end justify-center overflow-hidden rounded-2xl border-[2.5px] pb-1 transition-transform ${on ? "scale-[1.04] border-ecorce" : "border-transparent"}`}
                      >
                        <CoverArt
                          cover={v.key}
                          illustration={v.key === "illu" ? illustration ?? suggested ?? "anniv" : null}
                          color={color}
                          photo={v.key === "photo" ? photo ?? photos[0] : undefined}
                        />
                        <span className="relative rounded-[5px] bg-white/85 px-[5px] py-px text-[10px] font-bold" style={{ color: color.solid }}>{v.label}</span>
                      </button>
                    );
                  })}
                </div>
                {cover === "illu" && (
                  <div className="mt-1.5 flex flex-col gap-1.5">
                    <span className="text-xs font-semibold text-sec">Choisir un dessin</span>
                    <div className="no-scrollbar -mr-5 flex gap-2 overflow-x-auto px-0.5 pt-1 pb-1.5 sm:-mr-6">
                      {ILLUSTRATIONS.map((il) => {
                        const on = illustration === il.key;
                        return (
                          <button
                            key={il.key}
                            type="button"
                            aria-label={il.label}
                            aria-pressed={on}
                            onClick={() => set({ cover: "illu", illustration: il.key })}
                            className={`relative h-[68px] w-[108px] shrink-0 overflow-hidden rounded-[14px] border-[3px] transition-transform ${on ? "scale-[1.04] border-ecorce" : "border-transparent"}`}
                          >
                            <IllustrationArt illustration={il.key} color={color} />
                            {suggested === il.key && !on && (
                              <span className="absolute bottom-1 left-1 rounded-md bg-ecorce px-1.5 py-0.5 text-[10px] font-bold text-sable">Suggéré</span>
                            )}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}
                {cover === "photo" && (
                  photos.length ? (
                    <div className="mt-1.5 flex flex-col gap-1.5">
                      <span className="text-xs font-semibold text-sec">Une photo des idées du dossier</span>
                      <div className="no-scrollbar -mr-5 flex gap-2 overflow-x-auto px-0.5 pt-1 pb-1.5 sm:-mr-6">
                        {photos.map((u) => {
                          const on = photo === u;
                          return (
                            <button
                              key={u}
                              type="button"
                              aria-label="Choisir cette photo"
                              aria-pressed={on}
                              onClick={() => set({ cover: "photo", bannerUrl: u })}
                              className={`h-[68px] w-[96px] shrink-0 overflow-hidden rounded-[14px] border-[3px] ${on ? "border-ecorce" : "border-transparent"}`}
                            >
                              <img src={u} alt="" loading="lazy" referrerPolicy="no-referrer" className="h-full w-full object-cover" />
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  ) : (
                    <p className="text-xs text-sec">Ajoutez au dossier des idées avec une photo pour en faire la couverture.</p>
                  )
                )}
              </Group>

              <Group label="Icône">
                <div className="grid grid-cols-6 gap-1.5">
                  {FOLDER_ICONS.map((i) => {
                    const on = form.icon === i.key;
                    return (
                      <button
                        key={i.key}
                        type="button"
                        aria-label={i.label}
                        title={i.label}
                        aria-pressed={on}
                        onClick={() => set({ icon: i.key })}
                        className={`flex h-[50px] items-center justify-center rounded-[14px] transition-all ${on ? "scale-[1.08] text-white" : "bg-card text-ecorce hover:bg-sable-2"}`}
                        style={on ? { background: color.solid } : undefined}
                      >
                        <FolderGlyph icon={i.key} size={22} />
                      </button>
                    );
                  })}
                </div>
              </Group>
            </>
          )}

          {tab === "infos" && (
            <>
              <Field label="Nom" required maxLength={100} placeholder="Vacances Drôme" value={form.name} onChange={(e) => { set({ name: e.target.value }); setError(""); }} />
              <Field
                label="Un petit mot (facultatif)"
                maxLength={140}
                placeholder="Ex : pour ses 10 ans"
                value={form.description ?? ""}
                onChange={(e) => set({ description: e.target.value })}
              />

              <div className="flex flex-col gap-2 rounded-[18px] bg-card p-3.5">
                <div className="flex items-center gap-2.5">
                  <Icon name="calendar" size={20} className="text-terracotta" />
                  <span className="flex flex-1 flex-col">
                    <span className="text-[15px] font-bold">Une date clé</span>
                    <span className="text-xs text-sec">Compte à rebours sur le dossier</span>
                  </span>
                  <Toggle
                    label="Une date clé"
                    checked={!!keyDate}
                    onChange={(on) => set(on ? { keyDate: inDays(14), keyDateType: form.keyDateType || "event" } : { keyDate: 0, keyDateType: "" })}
                  />
                </div>
                {!!keyDate && (
                  <div className="flex animate-fade-in flex-col gap-2.5 pt-1.5">
                    <div className="flex flex-wrap gap-2">
                      {KEY_DATE_TYPES.map((d) => {
                        const on = form.keyDateType === d.key;
                        return (
                          <button
                            key={d.key}
                            type="button"
                            aria-pressed={on}
                            onClick={() => set({ keyDateType: d.key })}
                            className={`h-10 rounded-xl border-2 px-3 text-[13px] font-bold transition-colors ${on ? "border-ecorce" : "border-line-2 bg-card"}`}
                            style={on ? { background: color.bg } : undefined}
                          >
                            {d.label}
                          </button>
                        );
                      })}
                    </div>
                    <label className="flex items-center gap-3 text-[13px] font-bold text-sec">
                      {form.keyDateType === "anniv" ? "Date de l'anniversaire" : "Date"}
                      <input
                        type="date"
                        required
                        value={toDateInput(keyDate)}
                        onChange={(e) => set({ keyDate: fromDateInput(e.target.value) || keyDate })}
                        className="h-10 flex-1 rounded-xl border-[1.5px] border-line-2 bg-card px-3 text-[15px] font-semibold text-ecorce outline-none focus:border-terracotta"
                      />
                    </label>
                  </div>
                )}
              </div>

              <Group label="Pour qui">
                {children.length ? (
                  <div className="flex flex-wrap gap-2">
                    {children.map((c) => {
                      const on = !!form.childIds?.includes(c.id);
                      const [bg, ink] = childColors(children, c.id);
                      return (
                        <button
                          key={c.id}
                          type="button"
                          aria-pressed={on}
                          onClick={() => set({ childIds: on ? form.childIds!.filter((x) => x !== c.id) : [...(form.childIds ?? []), c.id] })}
                          className={`flex h-14 min-w-[30%] flex-1 items-center justify-center gap-2 rounded-2xl border-2 bg-card text-sm font-bold transition-transform ${on ? "scale-[1.03] border-ecorce" : "border-transparent"}`}
                        >
                          <Avatar name={c.name} color={bg} ink={ink} size={28} />
                          {c.name}
                        </button>
                      );
                    })}
                    {children.length > 1 && (
                      <button
                        type="button"
                        aria-pressed={form.childIds?.length === children.length}
                        onClick={() => set({ childIds: form.childIds?.length === children.length ? [] : children.map((c) => c.id) })}
                        className={`flex h-14 min-w-[30%] flex-1 items-center justify-center gap-2 rounded-2xl border-2 bg-card text-sm font-bold ${form.childIds?.length === children.length ? "border-ecorce" : "border-transparent"}`}
                      >
                        <Avatar name="T" color="#F28C38" size={28} />
                        Tous
                      </button>
                    )}
                  </div>
                ) : (
                  <p className="text-xs text-sec">Ajoutez vos enfants dans « Ma tribu » pour indiquer à qui s'adresse le dossier.</p>
                )}
              </Group>

              <Group label="Visibilité">
                <div className="grid grid-cols-2 gap-2">
                  {VISIBILITIES.map((v) => {
                    const active = form.visibility === v.key || (v.key === "VISIBILITY_PRIVATE" && form.visibility === "VISIBILITY_SHARED");
                    return (
                      <button
                        key={v.key}
                        type="button"
                        aria-pressed={active}
                        onClick={() => set({ visibility: v.key })}
                        className={`flex flex-col items-start gap-1 rounded-medium border-2 p-3 text-left transition-colors ${active ? "border-ecorce bg-card" : "border-transparent bg-sable-2"}`}
                      >
                        <span className="flex items-center gap-1.5 text-sm font-bold"><Icon name={v.icon} size={16} />{v.label}</span>
                        <span className="text-xs text-sec">{v.hint}</span>
                      </button>
                    );
                  })}
                </div>
              </Group>
            </>
          )}

          {tab === "view" && (
            <>
              <Group label="Afficher les idées en">
                <div className="grid grid-cols-3 gap-2">
                  {VIEW_MODES.map((w) => {
                    const on = (form.viewMode || "grid") === w.key;
                    return (
                      <button
                        key={w.key}
                        type="button"
                        aria-pressed={on}
                        onClick={() => set({ viewMode: w.key })}
                        className={`flex flex-col items-center gap-2 rounded-2xl border-[2.5px] bg-card p-2.5 text-[13px] font-bold transition-transform ${on ? "scale-[1.03] border-ecorce" : "border-transparent"}`}
                      >
                        <ViewPreview mode={w.key} ink={color.solid} mid={color.mid} />
                        {w.label}
                      </button>
                    );
                  })}
                </div>
              </Group>
              <Group label="Trier par">
                <div className="flex flex-wrap gap-2">
                  {SORTS.map((o) => {
                    const on = (form.sort || "recent") === o.key;
                    return (
                      <button
                        key={o.key}
                        type="button"
                        aria-pressed={on}
                        onClick={() => set({ sort: o.key })}
                        className={`h-10 rounded-xl px-3.5 text-[13px] font-bold transition-colors ${on ? "bg-ecorce text-sable" : "bg-card text-ecorce"}`}
                      >
                        {o.label}
                      </button>
                    );
                  })}
                </div>
              </Group>
              <div className="flex items-center gap-3 rounded-2xl bg-card p-3.5">
                <svg width={20} height={20} viewBox="0 0 24 24" aria-hidden="true" className="icon text-terracotta"><path d="M9 4h6l-1 6 3 3H7l3-3z M12 13v7" /></svg>
                <span className="flex flex-1 flex-col">
                  <span className="text-[15px] font-bold">Épingler en haut de Mes idées</span>
                  <span className="text-xs text-sec">Toujours sous la main</span>
                </span>
                <Toggle label="Épingler en haut de Mes idées" checked={!!form.pinned} onChange={(pinned) => set({ pinned })} />
              </div>
            </>
          )}

          {error && <ErrorNote>{error}</ErrorNote>}
        </div>
      </form>
    </Sheet>
  );
}

function Group({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <fieldset className="flex min-w-0 flex-col gap-2">
      <legend className="mb-2 text-[13px] font-bold text-sec">
        {label}
        {hint && <span className="font-medium"> {hint}</span>}
      </legend>
      {children}
    </fieldset>
  );
}

/** Mini-schéma d'une vue (grille, liste, mosaïque) aux couleurs du dossier. */
function ViewPreview({ mode, ink, mid }: { mode: ViewMode; ink: string; mid: string }) {
  const cells = mode === "list" ? [1, 1, 1] : mode === "mosaic" ? [2, 1, 1] : [1, 1, 1, 1];
  return (
    <span aria-hidden="true" className={`grid h-[52px] w-16 gap-[3px] ${mode === "list" ? "grid-cols-1" : "grid-cols-2"}`}>
      {cells.map((r, i) => (
        <span key={i} className={`rounded transition-colors duration-500 ${r === 2 ? "row-span-2" : ""}`} style={{ background: i === 0 ? ink : mid }} />
      ))}
    </span>
  );
}
