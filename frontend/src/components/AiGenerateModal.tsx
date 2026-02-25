import { useState } from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faTimes, faWandMagicSparkles, faSpinner, faPlus, faCheck } from "@fortawesome/free-solid-svg-icons";
import { ai as aiApi, links as linksApi } from "../api";
import { CATEGORY_COLORS, CATEGORIES } from "../types";

interface Idea {
  title: string;
  description: string;
  url?: string;
  imageUrl?: string;
  category: string;
  tags: string[];
  ageRange?: string;
  price?: string;
  location?: string;
  ingredients?: string[];
}

interface Props {
  onClose: () => void;
  onCreated: () => void;
  initialFolderId?: string;
}

const EXAMPLE_PROMPTS = [
  "Anniversaire pirate pour un enfant de 2 ans",
  "Cadeaux de Noël pour une petite fille de 5 ans",
  "Activités en famille pour un week-end pluvieux",
  "Recettes faciles pour goûter d'anniversaire",
  "Idées de voyage en Europe avec des enfants",
];

export default function AiGenerateModal({ onClose, onCreated, initialFolderId }: Props) {
  const [prompt, setPrompt] = useState("");
  const [ideas, setIdeas] = useState<Idea[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  const getCategoryLabel = (value: string) =>
    CATEGORIES.find((c) => c.value === value)?.label ?? value;
  const getCategoryIcon = (value: string) =>
    CATEGORIES.find((c) => c.value === value)?.icon ?? "💡";

  const handleGenerate = async () => {
    if (!prompt.trim()) return;
    setLoading(true);
    setError("");
    setIdeas([]);
    setSelected(new Set());
    setSaved(false);
    try {
      const res = await aiApi.generate(prompt.trim());
      setIdeas(res.ideas || []);
      // Tout sélectionner par défaut
      setSelected(new Set(res.ideas.map((_, i) => i)));
    } catch (err: any) {
      setError(err.message || "Erreur lors de la génération");
    } finally {
      setLoading(false);
    }
  };

  const toggleSelect = (i: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      next.has(i) ? next.delete(i) : next.add(i);
      return next;
    });
  };

  const handleSave = async () => {
    const toSave = ideas.filter((_, i) => selected.has(i));
    if (toSave.length === 0) return;
    setSaving(true);
    try {
      await Promise.all(
        toSave.map((idea) =>
          linksApi.create({
            title: idea.title,
            url: idea.url ?? "",
            description: idea.description,
            category: idea.category,
            tags: idea.tags ?? [],
            ageRange: idea.ageRange ?? "",
            price: idea.price ?? "",
            location: idea.location ?? "",
            imageUrl: idea.imageUrl ?? "",
            ingredients: idea.ingredients ?? [],
            folderId: initialFolderId || undefined,
          })
        )
      );
      setSaved(true);
      setTimeout(() => {
        onCreated();
      }, 800);
    } catch (err: any) {
      setError(err.message || "Erreur lors de la sauvegarde");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-end sm:items-center justify-center">
      <div className="bg-white rounded-t-3xl sm:rounded-3xl w-full max-w-2xl max-h-[92vh] overflow-y-auto p-6">
        {/* Header */}
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <span className="text-2xl">✨</span>
            <h2 className="text-lg font-bold text-gray-800">Générer avec l'IA</h2>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <FontAwesomeIcon icon={faTimes} className="w-5 h-5" />
          </button>
        </div>

        {/* Prompt */}
        <div className="mb-3">
          <textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); handleGenerate(); } }}
            placeholder="Décris ta liste... ex: anniversaire pirate pour un enfant de 2 ans"
            rows={2}
            className="w-full px-4 py-3 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm resize-none"
          />
        </div>

        {/* Exemples */}
        {ideas.length === 0 && !loading && (
          <div className="flex flex-wrap gap-2 mb-4">
            {EXAMPLE_PROMPTS.map((ex) => (
              <button
                key={ex}
                onClick={() => setPrompt(ex)}
                className="text-xs px-3 py-1.5 rounded-full bg-orange-50 text-orange-600 hover:bg-orange-100 transition-colors"
              >
                {ex}
              </button>
            ))}
          </div>
        )}

        <button
          onClick={handleGenerate}
          disabled={loading || !prompt.trim()}
          className="w-full py-2.5 rounded-xl bg-orange-500 text-white font-semibold hover:bg-orange-600 transition-colors disabled:opacity-50 flex items-center justify-center gap-2 mb-4"
        >
          {loading ? (
            <><FontAwesomeIcon icon={faSpinner} className="animate-spin" /> Génération en cours...</>
          ) : (
            <><FontAwesomeIcon icon={faWandMagicSparkles} /> Générer des idées</>
          )}
        </button>

        {error && (
          <div className="bg-red-50 text-red-600 text-sm rounded-xl px-4 py-2 mb-4">
            {error}
            {error.includes("unreachable") && (
              <p className="mt-1 text-xs text-red-400">Assure-toi qu'Ollama tourne : <code>ollama serve</code></p>
            )}
          </div>
        )}

        {/* Résultats */}
        {ideas.length > 0 && (
          <>
            <div className="flex items-center justify-between mb-3">
              <p className="text-sm text-gray-500">{ideas.length} idées générées — {selected.size} sélectionnées</p>
              <button
                onClick={() => setSelected(selected.size === ideas.length ? new Set() : new Set(ideas.map((_, i) => i)))}
                className="text-xs text-orange-500 hover:underline"
              >
                {selected.size === ideas.length ? "Tout désélectionner" : "Tout sélectionner"}
              </button>
            </div>

            <div className="space-y-2 mb-4">
              {ideas.map((idea, i) => {
                const color = CATEGORY_COLORS[idea.category] ?? "#FFD700";
                const isSelected = selected.has(i);
                return (
                  <button
                    key={i}
                    onClick={() => toggleSelect(i)}
                    className={`w-full text-left p-3 rounded-xl border-2 transition-all ${
                      isSelected ? "border-orange-400 bg-orange-50" : "border-gray-100 bg-gray-50 opacity-60"
                    }`}
                  >
                    <div className="flex items-start gap-3">
                      {idea.imageUrl ? (
                        <img
                          src={idea.imageUrl}
                          alt=""
                          className="w-16 h-16 rounded-lg object-cover flex-shrink-0"
                          onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }}
                        />
                      ) : (
                        <div
                          className="w-10 h-10 rounded-lg flex items-center justify-center text-sm flex-shrink-0 mt-0.5"
                          style={{ backgroundColor: color + "22" }}
                        >
                          {getCategoryIcon(idea.category)}
                        </div>
                      )}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-medium text-sm text-gray-800">{idea.title}</span>
                          <span
                            className="text-xs px-2 py-0.5 rounded-full font-medium"
                            style={{ backgroundColor: color + "22", color }}
                          >
                            {getCategoryLabel(idea.category)}
                          </span>
                        </div>
                        {idea.description && (
                          <p className="text-xs text-gray-500 mt-0.5 line-clamp-2">{idea.description}</p>
                        )}
                        <div className="flex flex-wrap gap-1 mt-1">
                          {idea.url && (
                            <a
                              href={idea.url}
                              target="_blank"
                              rel="noopener noreferrer"
                              onClick={(e) => e.stopPropagation()}
                              className="text-xs text-blue-500 hover:underline truncate max-w-[200px]"
                              title={idea.url}
                            >🔗 voir la source</a>
                          )}
                          {idea.ageRange && <span className="text-xs text-gray-400">👶 {idea.ageRange}</span>}
                          {idea.price && <span className="text-xs text-gray-400">💰 {idea.price}</span>}
                          {idea.location && <span className="text-xs text-gray-400">📍 {idea.location}</span>}
                          {idea.tags?.slice(0, 3).map((t) => (
                            <span key={t} className="text-xs bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded-full">#{t}</span>
                          ))}
                        </div>
                      </div>
                      <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0 mt-0.5 ${
                        isSelected ? "border-orange-400 bg-orange-400" : "border-gray-300"
                      }`}>
                        {isSelected && <FontAwesomeIcon icon={faCheck} className="text-white w-2.5 h-2.5" />}
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>

            <button
              onClick={handleSave}
              disabled={saving || selected.size === 0 || saved}
              className="w-full py-2.5 rounded-xl bg-green-500 text-white font-semibold hover:bg-green-600 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {saved ? (
                <><FontAwesomeIcon icon={faCheck} /> Ajouté avec succès !</>
              ) : saving ? (
                <><FontAwesomeIcon icon={faSpinner} className="animate-spin" /> Sauvegarde...</>
              ) : (
                <><FontAwesomeIcon icon={faPlus} /> Ajouter {selected.size} idée{selected.size > 1 ? "s" : ""}</>
              )}
            </button>
          </>
        )}
      </div>
    </div>
  );
}
