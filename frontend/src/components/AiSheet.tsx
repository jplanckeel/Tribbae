import { useState, type FormEvent } from "react";
import * as api from "../lib/api";
import { CATEGORIES, categoryMeta, normalizeCategory } from "../lib/categories";
import { useMyData } from "../lib/data";
import { canEditFolder } from "../lib/links";
import { useSession } from "../lib/session";
import type { AiIdea, CategoryKey, Link } from "../lib/types";
import { useFeedback } from "./feedback";
import Icon from "./Icon";
import Sheet from "./Sheet";
import { Button, Spinner } from "./ui";

const SUGGESTIONS = [
  "10 activités d'intérieur pour un jour de pluie",
  "Sorties nature autour de Lyon pour des 3–8 ans",
  "Goûters sans sucre ajouté",
  "Cadeaux de Noël pour une ado de 13 ans",
];

const NEW_FOLDER = "__new__";

/** À monter uniquement quand la feuille est ouverte. */
interface Props {
  onClose: () => void;
  folderId?: string;
  prompt?: string;
  onSaved: (folderId: string | undefined, links: Link[]) => void;
}

export default function AiSheet({ onClose, folderId, prompt: initialPrompt, onSaved }: Props) {
  const session = useSession();
  const { folders, upsertFolder, upsertLink, peers } = useMyData();
  const { toast, confirm } = useFeedback();
  const [prompt, setPrompt] = useState(initialPrompt ?? "");
  const [ideas, setIdeas] = useState<(AiIdea & { cat: CategoryKey })[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [target, setTarget] = useState<string>(folderId ?? NEW_FOLDER);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const generate = async (e?: FormEvent) => {
    e?.preventDefault();
    const p = prompt.trim();
    if (!p || loading) return;
    setLoading(true);
    setError("");
    setIdeas([]);
    try {
      const res = await api.ai.generate(p);
      setIdeas(res.map((i) => ({ ...i, cat: normalizeCategory(i.category) })));
      setSelected(new Set(res.map((_, i) => i)));
      if (res.length === 0) setError("Aucune idée trouvée, reformulez votre demande.");
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const toggle = (i: number) =>
    setSelected((s) => {
      const n = new Set(s);
      if (n.has(i)) n.delete(i);
      else n.add(i);
      return n;
    });

  const save = async () => {
    const chosen = ideas.filter((_, i) => selected.has(i));
    if (!chosen.length) return;
    setSaving(true);
    setError("");
    try {
      let fid = target === NEW_FOLDER ? undefined : target;
      if (!fid) {
        const folder = await api.folders.create({
          name: prompt.trim().slice(0, 80) || "Idées de l'IA",
          icon: "MAGIC",
          color: "PURPLE",
          visibility: "VISIBILITY_PRIVATE",
        });
        upsertFolder(folder);
        fid = folder.id;
      }
      const created = await Promise.all(
        chosen.map((i) =>
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
            folderId: fid,
            visibility: "private",
          }),
        ),
      );
      created.forEach(upsertLink);
      toast(`${created.length} idée${created.length > 1 ? "s" : ""} rangée${created.length > 1 ? "s" : ""}`);
      setIdeas([]);
      onSaved(fid, created);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const close = async () => {
    if (ideas.length && !(await confirm({ title: "Abandonner ces idées ?", text: "Les idées générées ne seront pas enregistrées.", confirmLabel: "Abandonner", danger: true }))) return;
    onClose();
  };

  const writable = folders.filter((f) => canEditFolder(f, session?.userId, peers));

  return (
    <Sheet
      open
      onClose={close}
      title="Demander à l'IA"
      tone="prune"
      wide
      footer={
        ideas.length > 0 ? (
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <label className="flex flex-1 items-center gap-2 text-sm">
              <span className="shrink-0 text-prune-soft">Ranger dans</span>
              <select
                value={target}
                onChange={(e) => setTarget(e.target.value)}
                className="h-11 min-w-0 flex-1 rounded-s border-0 bg-white/10 px-3 font-semibold text-sable outline-none focus:ring-2 focus:ring-soleil"
              >
                <option value={NEW_FOLDER} className="text-ecorce">Nouveau dossier « {prompt.trim().slice(0, 30)} »</option>
                {writable.map((f) => <option key={f.id} value={f.id} className="text-ecorce">{f.name}</option>)}
              </select>
            </label>
            <button
              type="button"
              onClick={save}
              disabled={saving || selected.size === 0}
              className="flex h-[52px] items-center justify-center gap-2 rounded-m bg-soleil px-5 font-bold text-ecorce transition-colors hover:bg-white disabled:opacity-55"
            >
              {saving ? <Spinner size={18} /> : <Icon name="check" size={18} />}
              Garder {selected.size} idée{selected.size > 1 ? "s" : ""}
            </button>
          </div>
        ) : undefined
      }
    >
      <form onSubmit={generate} className="flex flex-col gap-3">
        <label htmlFor="ai-prompt" className="text-sm text-prune-soft">
          Décrivez ce que vous cherchez : l'IA prépare des idées prêtes à ranger.
        </label>
        <div className="flex gap-2">
          <textarea
            id="ai-prompt"
            rows={2}
            maxLength={500}
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) generate(e);
            }}
            placeholder="« 10 activités d'intérieur pour un jour de pluie »"
            className="min-h-[56px] flex-1 resize-none rounded-m bg-white/10 px-4 py-3 text-[15px] text-sable outline-none placeholder:text-white/45 focus:ring-2 focus:ring-soleil"
          />
          <button
            type="submit"
            disabled={!prompt.trim() || loading}
            aria-label="Générer des idées"
            className="flex w-14 shrink-0 items-center justify-center rounded-m bg-soleil text-prune transition-colors hover:bg-white disabled:opacity-50"
          >
            {loading ? <Spinner size={22} /> : <Icon name="sparkles" size={24} />}
          </button>
        </div>
        {!ideas.length && !loading && (
          <div className="flex flex-wrap gap-2">
            {SUGGESTIONS.map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => setPrompt(s)}
                className="rounded-full border border-white/20 px-3 py-1.5 text-[13px] text-prune-soft transition-colors hover:bg-white/10"
              >
                {s}
              </button>
            ))}
          </div>
        )}
      </form>

      {loading && (
        <div role="status" className="mt-6 flex flex-col items-center gap-3 rounded-l bg-white/5 px-6 py-10 text-center">
          <Icon name="sparkles" size={32} className="animate-pulse text-soleil" />
          <p className="font-display text-lg font-bold">L'IA prépare vos idées…</p>
          <p className="text-sm text-prune-soft">Rédaction en cours, comptez 20 à 60 secondes.</p>
        </div>
      )}

      {error && <p role="alert" className="mt-4 rounded-s bg-white/10 px-4 py-3 text-sm font-medium text-soleil">{error}</p>}

      {ideas.length > 0 && (
        <ul className="mt-5 flex flex-col gap-2.5">
          {ideas.map((idea, i) => {
            const cat = categoryMeta(idea.cat);
            const on = selected.has(i);
            return (
              <li key={i} className={`rounded-l p-3.5 transition-colors ${on ? "bg-white text-ecorce" : "bg-white/5 text-sable"}`}>
                <div className="flex items-start gap-3">
                  <button
                    type="button"
                    role="checkbox"
                    aria-checked={on}
                    aria-label={`Garder « ${idea.title} »`}
                    onClick={() => toggle(i)}
                    className={`mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-lg border-2 ${on ? "border-terracotta bg-terracotta text-white" : "border-white/40"}`}
                  >
                    {on && <Icon name="check" size={14} strokeWidth={3} />}
                  </button>
                  <div className="flex min-w-0 flex-1 flex-col gap-1.5">
                    <span className="font-display text-base leading-tight font-bold">{idea.title}</span>
                    {idea.description && <span className={`line-clamp-3 text-[13px] ${on ? "text-sec" : "text-prune-soft"}`}>{idea.description}</span>}
                    <div className="flex flex-wrap items-center gap-1.5">
                      <select
                        aria-label="Catégorie"
                        value={idea.cat}
                        onChange={(e) => setIdeas((list) => list.map((x, j) => (j === i ? { ...x, cat: e.target.value as CategoryKey } : x)))}
                        className={`h-7 rounded-full border-0 px-2.5 text-xs font-semibold outline-none ${cat.bg} ${cat.ink}`}
                      >
                        {CATEGORIES.map((c) => <option key={c.key} value={c.key}>{c.label}</option>)}
                      </select>
                      {[idea.ageRange].filter(Boolean).map((m) => (
                        <span key={m} className={`text-xs ${on ? "text-sec" : "text-prune-soft"}`}>· {m}</span>
                      ))}
                    </div>
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {ideas.length > 0 && (
        <Button type="button" variant="ghost" size="sm" className="mt-4" onClick={() => generate()} icon="sparkles">
          Relancer
        </Button>
      )}
    </Sheet>
  );
}
