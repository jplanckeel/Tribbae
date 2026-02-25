import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { links as linksApi, folders as foldersApi } from "../api";
import { CATEGORIES, CATEGORY_COLORS } from "../types";
import {
  ArrowLeft, ExternalLink, MapPin, Star, Tag,
  Calendar, Trash2, Edit3, Heart, FolderOpen,
} from "lucide-react";

function catLabel(v: string) { return CATEGORIES.find((c) => c.value === v)?.label ?? "Idée"; }
function catIcon(v: string) { return CATEGORIES.find((c) => c.value === v)?.icon ?? "💡"; }

const inputCls = "w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm";

export default function LinkDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [link, setLink] = useState<any>(null);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<any>({});
  const [folderList, setFolderList] = useState<any[]>([]);

  useEffect(() => {
    if (!id) return;
    linksApi.get(id).then((res) => { setLink(res.link); setForm(res.link); });
    foldersApi.list().then((res) => setFolderList(res.folders || []));
  }, [id]);

  if (!link) return <div className="flex items-center justify-center py-20 text-gray-400">Chargement...</div>;

  const color = CATEGORY_COLORS[link.category] || "#FF8C00";
  const hasImage = link.imageUrl && link.imageUrl.length > 0;
  const folderName = folderList.find((f) => f.id === link.folderId)?.name;

  const handleDelete = async () => {
    if (!confirm("Supprimer cette idée ?")) return;
    await linksApi.delete(link.id);
    navigate("/");
  };

  const handleSave = async () => {
    await linksApi.update(link.id, {
      title: form.title, url: form.url, description: form.description,
      category: form.category, tags: form.tags, location: form.location,
      price: form.price, ageRange: form.ageRange, rating: form.rating,
      ingredients: form.ingredients, folderId: form.folderId || "",
    });
    setLink({ ...form });
    setEditing(false);
  };

  const toggleFavorite = async () => {
    const updated = { ...link, favorite: !link.favorite };
    await linksApi.update(link.id, { favorite: updated.favorite });
    setLink(updated);
  };

  const openMaps = () => {
    window.open(`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(link.location)}`, "_blank");
  };

  // --- Vue édition ---
  if (editing) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-6">
        <button onClick={() => setEditing(false)} className="flex items-center gap-1 text-orange-500 mb-4">
          <ArrowLeft size={18} /> Retour
        </button>
        <div className="bg-white rounded-2xl shadow-sm p-6 space-y-3">
          <input value={form.title || ""} onChange={(e) => setForm({ ...form, title: e.target.value })} className={`${inputCls} font-semibold`} placeholder="Titre" />
          <input value={form.url || ""} onChange={(e) => setForm({ ...form, url: e.target.value })} className={inputCls} placeholder="URL" />
          <textarea value={form.description || ""} onChange={(e) => setForm({ ...form, description: e.target.value })} rows={3} className={`${inputCls} resize-none`} placeholder="Description" />

          {/* Catégorie */}
          <div className="flex gap-2 flex-wrap">
            {CATEGORIES.map((cat) => {
              const active = form.category === cat.value;
              const c = CATEGORY_COLORS[cat.value];
              return (
                <button key={cat.value} type="button" onClick={() => setForm({ ...form, category: cat.value })}
                  className="px-3 py-1.5 rounded-full text-xs font-medium"
                  style={{ backgroundColor: active ? c : c + "18", color: active ? "white" : c }}
                >
                  {cat.icon} {cat.label}
                </button>
              );
            })}
          </div>

          {/* Liste */}
          <select value={form.folderId || ""} onChange={(e) => setForm({ ...form, folderId: e.target.value })}
            className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm bg-white"
          >
            <option value="">Aucune liste</option>
            {folderList.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
          </select>

          <div className="grid grid-cols-2 gap-3">
            <input value={form.location || ""} onChange={(e) => setForm({ ...form, location: e.target.value })} className={inputCls} placeholder="Lieu" />
            <input value={form.price || ""} onChange={(e) => setForm({ ...form, price: e.target.value })} className={inputCls} placeholder="Prix" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <input value={form.ageRange || ""} onChange={(e) => setForm({ ...form, ageRange: e.target.value })} className={inputCls} placeholder="Âge" />
            <input
              value={(form.tags || []).join(", ")}
              onChange={(e) => setForm({ ...form, tags: e.target.value.split(",").map((t: string) => t.trim()).filter(Boolean) })}
              className={inputCls} placeholder="Tags (virgule)"
            />
          </div>

          {form.category === "LINK_CATEGORY_RECETTE" && (
            <textarea
              value={(form.ingredients || []).join("\n")}
              onChange={(e) => setForm({ ...form, ingredients: e.target.value.split("\n").filter(Boolean) })}
              rows={4} className={`${inputCls} resize-none`} placeholder="Ingrédients (un par ligne)"
            />
          )}

          {/* Note */}
          <div className="flex items-center gap-2">
            <span className="text-sm text-gray-500">Note :</span>
            {[1, 2, 3, 4, 5].map((n) => (
              <button key={n} type="button" onClick={() => setForm({ ...form, rating: form.rating === n ? 0 : n })}>
                <Star size={20} fill={n <= (form.rating || 0) ? "#FFD700" : "none"} stroke="#FFD700" />
              </button>
            ))}
          </div>

          <button onClick={handleSave} className="w-full py-2.5 rounded-xl bg-orange-500 text-white font-semibold hover:bg-orange-600">
            Enregistrer
          </button>
        </div>
      </div>
    );
  }

  // --- Vue détail ---
  return (
    <div className="max-w-2xl mx-auto px-4 py-6">
      <button onClick={() => navigate(-1)} className="flex items-center gap-1 text-orange-500 mb-4">
        <ArrowLeft size={18} /> Retour
      </button>

      <div className="bg-white rounded-2xl shadow-sm overflow-hidden">
        {hasImage ? (
          <div className="relative h-56">
            <img src={link.imageUrl} alt={link.title} className="w-full h-full object-cover" />
            <div className="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent" />
            <span className="absolute top-3 right-3 text-white text-sm font-medium px-3 py-1 rounded-lg" style={{ backgroundColor: color }}>
              {catIcon(link.category)} {catLabel(link.category)}
            </span>
          </div>
        ) : (
          <div className="h-32 relative overflow-hidden" style={{ backgroundColor: color + "18" }}>
            <div className="absolute inset-0 flex flex-col justify-evenly opacity-20">
              {[0, 1, 2, 3].map((row) => (
                <div key={row} className="flex justify-evenly" style={{ marginLeft: row % 2 === 1 ? 16 : 0 }}>
                  {Array.from({ length: 12 }).map((_, i) => (
                    <span key={i} className="text-xl rotate-45">{catIcon(link.category)}</span>
                  ))}
                </div>
              ))}
            </div>
            <span className="absolute top-3 right-3 text-white text-sm font-medium px-3 py-1 rounded-lg z-10" style={{ backgroundColor: color }}>
              {catIcon(link.category)} {catLabel(link.category)}
            </span>
          </div>
        )}

        <div className="p-6 space-y-4">
          <div className="flex items-start justify-between gap-2">
            <h1 className="text-xl font-bold text-gray-800 flex-1">{link.title}</h1>
            <div className="flex gap-2 flex-shrink-0">
              <button onClick={toggleFavorite} className={link.favorite ? "text-red-500" : "text-gray-300 hover:text-red-400"}>
                <Heart size={20} fill={link.favorite ? "currentColor" : "none"} />
              </button>
              <button onClick={() => setEditing(true)} className="text-gray-400 hover:text-orange-500">
                <Edit3 size={18} />
              </button>
              <button onClick={handleDelete} className="text-gray-400 hover:text-red-500">
                <Trash2 size={18} />
              </button>
            </div>
          </div>

          {/* Dossier */}
          {folderName && (
            <span className="inline-flex items-center gap-1 text-xs px-3 py-1 rounded-full bg-orange-50 text-orange-600">
              <FolderOpen size={12} /> {folderName}
            </span>
          )}

          {link.description && <p className="text-gray-600 text-sm">{link.description}</p>}

          {link.url && (
            <a href={link.url} target="_blank" rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 text-orange-500 hover:underline text-sm"
            >
              <ExternalLink size={14} />
              {(() => { try { return new URL(link.url).hostname; } catch { return link.url; } })()}
            </a>
          )}

          <div className="flex flex-wrap gap-3 text-sm">
            {link.location && (
              <button onClick={openMaps} className="flex items-center gap-1 px-3 py-1.5 rounded-full bg-blue-50 text-blue-600 hover:bg-blue-100">
                <MapPin size={14} /> {link.location}
              </button>
            )}
            {link.price && (
              <span className="flex items-center gap-1 px-3 py-1.5 rounded-full bg-green-50 text-green-600">
                💰 {link.price}
              </span>
            )}
            {link.ageRange && (
              <span className="flex items-center gap-1 px-3 py-1.5 rounded-full bg-purple-50 text-purple-600">
                👶 {link.ageRange}
              </span>
            )}
            {link.eventDate && (
              <span className="flex items-center gap-1 px-3 py-1.5 rounded-full bg-orange-50 text-orange-600">
                <Calendar size={14} /> {new Date(Number(link.eventDate) * 1000).toLocaleDateString("fr-FR")}
              </span>
            )}
          </div>

          {link.rating > 0 && (
            <div className="flex items-center gap-1">
              {Array.from({ length: 5 }).map((_, i) => (
                <Star key={i} size={18} fill={i < link.rating ? "#FFD700" : "none"} stroke="#FFD700" />
              ))}
            </div>
          )}

          {link.tags?.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {link.tags.map((tag: string) => (
                <span key={tag} className="flex items-center gap-1 text-xs px-3 py-1 rounded-full"
                  style={{ backgroundColor: color + "20", color }}
                >
                  <Tag size={10} /> {tag}
                </span>
              ))}
            </div>
          )}

          {link.ingredients?.length > 0 && (
            <div>
              <h3 className="text-sm font-semibold text-gray-700 mb-2">🧾 Ingrédients</h3>
              <ul className="space-y-1">
                {link.ingredients.map((ing: string, i: number) => (
                  <li key={i} className="text-sm text-gray-600 flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-orange-400" />
                    {ing}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
