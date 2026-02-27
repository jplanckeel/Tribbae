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

interface IdeaWithEdits extends Idea {
  editedCategory?: string;
}

interface Props {
  onClose: () => void;
  onCreated: () => void;
  initialFolderId?: string;
}

const ALL_PROMPTS = [
  // Anniversaires (15)
  "Anniversaire pirate pour un enfant de 2 ans",
  "Anniversaire princesse pour une fille de 4 ans",
  "Anniversaire dinosaures pour un garçon de 3 ans",
  "Anniversaire super-héros pour un enfant de 6 ans",
  "Anniversaire licorne pour une fille de 5 ans",
  "Anniversaire spatial pour un enfant de 7 ans",
  "Anniversaire sirène pour une fille de 6 ans",
  "Anniversaire Pokémon pour un enfant de 8 ans",
  "Anniversaire Harry Potter pour un enfant de 10 ans",
  "Anniversaire Reine des Neiges pour une fille de 4 ans",
  "Anniversaire football pour un garçon de 9 ans",
  "Anniversaire danse pour une fille de 8 ans",
  "Anniversaire scientifique pour un enfant de 11 ans",
  "Anniversaire jungle pour un enfant de 3 ans",
  "Anniversaire cirque pour un enfant de 5 ans",
  
  // Cadeaux (20)
  "Cadeaux de Noël pour une petite fille de 5 ans",
  "Cadeaux de Noël pour un garçon de 8 ans",
  "Idées cadeaux pour un bébé de 1 an",
  "Cadeaux d'anniversaire pour une adolescente de 13 ans",
  "Cadeaux éducatifs pour un enfant de 4 ans",
  "Cadeaux créatifs pour un enfant de 6 ans",
  "Cadeaux sportifs pour un garçon de 10 ans",
  "Cadeaux musicaux pour un enfant de 7 ans",
  "Cadeaux de naissance originaux",
  "Cadeaux pour enfant passionné de lecture",
  "Cadeaux écologiques pour enfants",
  "Cadeaux technologiques pour ado de 14 ans",
  "Cadeaux pour enfant qui aime cuisiner",
  "Cadeaux pour enfant qui aime les animaux",
  "Cadeaux pour enfant qui aime dessiner",
  "Cadeaux pour enfant qui aime la nature",
  "Cadeaux pour enfant qui aime les sciences",
  "Cadeaux pour enfant qui aime construire",
  "Cadeaux pour enfant qui aime la magie",
  "Cadeaux pour enfant qui aime les puzzles",
  
  // Activités intérieures (15)
  "Activités en famille pour un week-end pluvieux",
  "Activités créatives pour enfants de 3 à 6 ans",
  "Jeux de société pour toute la famille",
  "Activités manuelles pour enfants de 5 ans",
  "Bricolages de Noël avec les enfants",
  "Activités Montessori pour enfants de 2 ans",
  "Expériences scientifiques à faire à la maison",
  "Activités de peinture pour enfants",
  "Jeux d'intérieur pour anniversaire",
  "Activités de lecture pour enfants",
  "Ateliers cuisine avec les enfants",
  "Activités de yoga pour enfants",
  "Jeux de construction pour enfants",
  "Activités de musique pour enfants",
  "Théâtre et spectacles pour enfants à la maison",
  
  // Activités extérieures (15)
  "Sorties en famille autour de Lyon",
  "Activités nature pour enfants en été",
  "Parcs d'attractions en France pour familles",
  "Randonnées faciles avec enfants en bas âge",
  "Activités à la plage avec des enfants",
  "Sorties culturelles pour enfants à Paris",
  "Fermes pédagogiques près de chez moi",
  "Activités sportives en famille",
  "Balades à vélo avec enfants",
  "Zoos et aquariums à visiter en famille",
  "Châteaux à visiter avec des enfants",
  "Parcs et jardins pour pique-nique en famille",
  "Activités nautiques pour enfants",
  "Accrobranche et parcours aventure pour enfants",
  "Musées interactifs pour enfants",
  
  // Recettes (15)
  "Recettes faciles pour goûter d'anniversaire",
  "Recettes de gâteaux sans gluten pour enfants",
  "Idées de repas rapides pour toute la famille",
  "Recettes de smoothies pour les enfants",
  "Recettes de biscuits à faire avec les enfants",
  "Recettes de crêpes originales",
  "Recettes de légumes pour enfants difficiles",
  "Recettes de goûters sains pour enfants",
  "Recettes de petit-déjeuner équilibré",
  "Recettes de desserts sans sucre ajouté",
  "Recettes végétariennes pour enfants",
  "Recettes de pain maison avec les enfants",
  "Recettes de pizzas maison pour enfants",
  "Recettes de compotes et purées de fruits",
  "Recettes de snacks pour la boîte à lunch",
  
  // Voyages et vacances (15)
  "Idées de voyage en Europe avec des enfants",
  "Destinations vacances famille en France",
  "Activités à faire à Paris avec des enfants",
  "Vacances à la mer avec des enfants en bas âge",
  "Road trip en famille en Bretagne",
  "Week-end en famille en Normandie",
  "Vacances à la montagne en été avec enfants",
  "Destinations ski pour familles débutantes",
  "Camping en famille avec jeunes enfants",
  "Croisières adaptées aux familles",
  "Parcs d'attractions en Europe",
  "Vacances en Espagne avec enfants",
  "Séjour en Italie en famille",
  "Vacances nature en famille",
  "City trip avec enfants en Europe",
  
  // Événements et fêtes (10)
  "Organisation d'une fête de fin d'année scolaire",
  "Idées pour un pique-nique en famille",
  "Activités pour les vacances de Pâques",
  "Idées pour Halloween avec des enfants",
  "Activités pour la fête des mères",
  "Idées pour la fête des pères",
  "Organisation d'une chasse aux œufs de Pâques",
  "Activités pour le carnaval avec enfants",
  "Idées pour fêter le Nouvel An en famille",
  "Organisation d'une fête d'été dans le jardin",
  
  // Éducation et apprentissage (10)
  "Activités pour apprendre l'alphabet en s'amusant",
  "Jeux pour apprendre les chiffres aux enfants",
  "Activités pour développer la motricité fine",
  "Livres pour apprendre l'anglais aux enfants",
  "Applications éducatives pour enfants de 5 ans",
  "Activités pour apprendre les couleurs",
  "Jeux de mémoire pour enfants",
  "Activités pour apprendre à lire",
  "Jeux pour développer la logique",
  "Activités pour apprendre les saisons",
];

function getRandomPrompts(count = 5): string[] {
  const shuffled = [...ALL_PROMPTS].sort(() => Math.random() - 0.5);
  return shuffled.slice(0, count);
}

export default function AiGenerateModal({ onClose, onCreated, initialFolderId }: Props) {
  const [prompt, setPrompt] = useState("");
  const [ideas, setIdeas] = useState<IdeaWithEdits[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [examplePrompts] = useState(() => getRandomPrompts(5));
  const [editingCategory, setEditingCategory] = useState<number | null>(null);

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

  const updateCategory = (index: number, newCategory: string) => {
    setIdeas((prev) =>
      prev.map((idea, i) => (i === index ? { ...idea, editedCategory: newCategory } : idea))
    );
    setEditingCategory(null);
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
            category: idea.editedCategory ?? idea.category,
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
        onClose();
      }, 800);
    } catch (err: any) {
      setError(err.message || "Erreur lors de la sauvegarde");
    } finally {
      setSaving(false);
    }
  };

  const handleClose = () => {
    if (ideas.length > 0 && !saved) {
      if (confirm("Fermer sans sauvegarder les idées générées ?")) {
        onClose();
      }
    } else {
      onClose();
    }
  };

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-end sm:items-center justify-center">
      <div className="bg-white rounded-t-3xl sm:rounded-3xl w-full max-w-2xl max-h-[92vh] overflow-y-auto p-6">
        {/* Header */}
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <span className="text-2xl">✨</span>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold text-gray-800">Générer avec l'IA</h2>
                <span className="text-xs px-2 py-0.5 rounded-full bg-orange-100 text-orange-600 font-medium">
                  Expérimental
                </span>
              </div>
            </div>
          </div>
          <button onClick={handleClose} className="text-gray-400 hover:text-gray-600">
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
            {examplePrompts.map((ex) => (
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
                const displayCategory = idea.editedCategory ?? idea.category;
                const color = CATEGORY_COLORS[displayCategory] ?? "#FFD700";
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
                          {getCategoryIcon(displayCategory)}
                        </div>
                      )}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-medium text-sm text-gray-800">{idea.title}</span>
                          {editingCategory === i ? (
                            <select
                              value={displayCategory}
                              onChange={(e) => {
                                e.stopPropagation();
                                updateCategory(i, e.target.value);
                              }}
                              onClick={(e) => e.stopPropagation()}
                              className="text-xs px-2 py-0.5 rounded-full font-medium border-2 border-orange-400"
                              style={{ backgroundColor: color + "22", color }}
                              autoFocus
                            >
                              {CATEGORIES.map((cat) => (
                                <option key={cat.value} value={cat.value}>
                                  {cat.icon} {cat.label}
                                </option>
                              ))}
                            </select>
                          ) : (
                            <span
                              onClick={(e) => {
                                e.stopPropagation();
                                setEditingCategory(i);
                              }}
                              className="text-xs px-2 py-0.5 rounded-full font-medium cursor-pointer hover:ring-2 hover:ring-orange-300"
                              style={{ backgroundColor: color + "22", color }}
                              title="Cliquer pour modifier"
                            >
                              {getCategoryLabel(displayCategory)}
                            </span>
                          )}
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
