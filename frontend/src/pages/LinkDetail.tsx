import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { links as linksApi, folders as foldersApi } from "../api";
import { CATEGORIES, CATEGORY_COLORS, normalizeCategory } from "../types";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faArrowLeft, faExternalLinkAlt, faMapMarkerAlt, faStar,
  faCalendar, faTrash, faPen, faHeart, faFolderOpen,
  faTimes, faCheck, faCopy, faEuroSign, faBaby,
} from "@fortawesome/free-solid-svg-icons";
import { faHeart as faHeartRegular, faBookmark } from "@fortawesome/free-regular-svg-icons";
import AdminBadge from "../components/AdminBadge";

function catLabel(v: string) { return CATEGORIES.find((c) => c.value === v)?.label ?? "Idée"; }
function catIcon(v: string) { return CATEGORIES.find((c) => c.value === v)?.icon ?? "💡"; }

const inputCls = "w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm";

function InfoPill({ icon, label, value, accent }: { icon: any; label: string; value: string; accent: string }) {
  return (
    <div className="flex items-center gap-3 p-3.5 rounded-2xl" style={{ backgroundColor: accent + "0D" }}>
      <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
        style={{ backgroundColor: accent + "20", color: accent }}>
        <FontAwesomeIcon icon={icon} className="w-4 h-4" />
      </div>
      <div className="min-w-0">
        <p className="text-[10px] font-semibold uppercase tracking-wide text-gray-400">{label}</p>
        <p className="text-sm font-semibold text-gray-800 truncate">{value}</p>
      </div>
    </div>
  );
}

export default function LinkDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [link, setLink] = useState<any>(null);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<any>({});
  const [folderList, setFolderList] = useState<any[]>([]);
  const [showSaveToList, setShowSaveToList] = useState(false);
  const [saveToFolderId, setSaveToFolderId] = useState("");
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!id) return;
    linksApi.get(id).then((res) => { setLink(res.link); setForm(res.link); });
    foldersApi.list().then((res) => setFolderList(res.folders || []));
  }, [id]);

  if (!link) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-orange-500" />
      </div>
    );
  }

  const category = normalizeCategory(link.category);
  const color = CATEGORY_COLORS[category] || "#FF8C00";
  const hasImage = link.imageUrl && link.imageUrl.length > 0;
  const folderName = folderList.find((f: any) => f.id === link.folderId)?.name;
  const isLoggedIn = !!localStorage.getItem("token");
  const currentUserId = localStorage.getItem("userId");
  const isOwnLink = currentUserId && link.ownerId && link.ownerId === currentUserId;

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
      imageUrl: form.imageUrl || "",
    });
    setLink({ ...form });
    setEditing(false);
  };

  const toggleLike = async () => {
    try {
      if (link.likedByMe) {
        const res = await linksApi.unlike(link.id);
        setLink({ ...link, likedByMe: false, likeCount: res.likeCount });
      } else {
        const res = await linksApi.like(link.id);
        setLink({ ...link, likedByMe: true, likeCount: res.likeCount });
      }
    } catch { /* silencieux */ }
  };

  const handleCopyUrl = () => {
    if (link.url) {
      navigator.clipboard.writeText(link.url).catch(() => {});
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleSaveToMyList = async () => {
    setSaving(true);
    try {
      await linksApi.create({
        title: link.title, url: link.url || "", description: link.description || "",
        category: link.category, tags: link.tags || [], location: link.location || "",
        price: link.price || "", ageRange: link.ageRange || "", rating: link.rating || 0,
        ingredients: link.ingredients || [], folderId: saveToFolderId || "",
        imageUrl: link.imageUrl || "",
      });
      setSaved(true);
      setShowSaveToList(false);
    } catch { /* silencieux */ }
    setSaving(false);
  };

  // ── Edit view ──
  if (editing) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-6 pb-24 sm:pb-6">
        <button onClick={() => setEditing(false)} className="flex items-center gap-1.5 text-orange-500 mb-4 text-sm font-medium">
          <FontAwesomeIcon icon={faArrowLeft} className="w-4 h-4" /> Retour
        </button>
        <div className="bg-white rounded-2xl shadow-sm p-6 space-y-3">
          <input value={form.title || ""} onChange={(e) => setForm({ ...form, title: e.target.value })} className={`${inputCls} font-semibold`} placeholder="Titre" />
          <input value={form.url || ""} onChange={(e) => setForm({ ...form, url: e.target.value })} className={inputCls} placeholder="URL" />
          <textarea value={form.description || ""} onChange={(e) => setForm({ ...form, description: e.target.value })} rows={3} className={`${inputCls} resize-none`} placeholder="Description" />
          <div className="space-y-2">
            <input value={form.imageUrl || ""} onChange={(e) => setForm({ ...form, imageUrl: e.target.value })} className={inputCls} placeholder="URL de l'image" />
            {form.imageUrl && (
              <div className="relative rounded-xl overflow-hidden h-32">
                <img src={form.imageUrl} alt="aperçu" className="w-full h-full object-cover" />
                <button type="button" onClick={() => setForm({ ...form, imageUrl: "" })}
                  className="absolute top-2 right-2 bg-black/50 text-white rounded-full w-6 h-6 flex items-center justify-center">
                  <FontAwesomeIcon icon={faTimes} className="w-3 h-3" />
                </button>
              </div>
            )}
          </div>
          <div className="flex gap-2 flex-wrap">
            {CATEGORIES.map((cat) => {
              const active = normalizeCategory(form.category) === cat.value;
              const c = CATEGORY_COLORS[cat.value];
              return (
                <button key={cat.value} type="button" onClick={() => setForm({ ...form, category: cat.value })}
                  className="px-3 py-1.5 rounded-full text-xs font-medium transition-colors"
                  style={{ backgroundColor: active ? c : c + "18", color: active ? "white" : c }}>
                  {cat.icon} {cat.label}
                </button>
              );
            })}
          </div>
          <select value={form.folderId || ""} onChange={(e) => setForm({ ...form, folderId: e.target.value })}
            className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm bg-white">
            <option value="">Aucune liste</option>
            {folderList.map((f: any) => <option key={f.id} value={f.id}>{f.name}</option>)}
          </select>
          <div className="grid grid-cols-2 gap-3">
            <input value={form.location || ""} onChange={(e) => setForm({ ...form, location: e.target.value })} className={inputCls} placeholder="Lieu" />
            <input value={form.price || ""} onChange={(e) => setForm({ ...form, price: e.target.value })} className={inputCls} placeholder="Prix" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <input value={form.ageRange || ""} onChange={(e) => setForm({ ...form, ageRange: e.target.value })} className={inputCls} placeholder="Âge" />
            <input value={(form.tags || []).join(", ")} onChange={(e) => setForm({ ...form, tags: e.target.value.split(",").map((t: string) => t.trim()).filter(Boolean) })} className={inputCls} placeholder="Tags (virgule)" />
          </div>
          {normalizeCategory(form.category) === "LINK_CATEGORY_RECETTE" && (
            <textarea value={(form.ingredients || []).join("\n")} onChange={(e) => setForm({ ...form, ingredients: e.target.value.split("\n").filter(Boolean) })}
              rows={4} className={`${inputCls} resize-none`} placeholder="Ingrédients (un par ligne)" />
          )}
          <div className="flex items-center gap-2">
            <span className="text-sm text-gray-500">Note :</span>
            {[1, 2, 3, 4, 5].map((n) => (
              <button key={n} type="button" onClick={() => setForm({ ...form, rating: form.rating === n ? 0 : n })}>
                <FontAwesomeIcon icon={faStar} className="w-5 h-5" style={{ color: n <= (form.rating || 0) ? "#FFD700" : "#e5e7eb" }} />
              </button>
            ))}
          </div>
          <button onClick={handleSave} className="w-full py-2.5 rounded-xl bg-orange-500 text-white font-semibold hover:bg-orange-600 transition-colors">
            Enregistrer
          </button>
        </div>
      </div>
    );
  }

  // ── Detail view ──
  return (
    <div className="min-h-screen bg-gray-50 pb-24 sm:pb-8">
      <div className="max-w-2xl mx-auto">

        {/* Hero */}
        <div className="relative h-72 sm:h-80">
          {hasImage ? (
            <img src={link.imageUrl} alt={link.title} className="w-full h-full object-cover" />
          ) : (
            <div className="w-full h-full overflow-hidden" style={{ backgroundColor: color + "25" }}>
              <div className="absolute inset-0 flex flex-col justify-evenly opacity-15">
                {[0, 1, 2, 3].map((row) => (
                  <div key={row} className="flex justify-evenly" style={{ marginLeft: row % 2 === 1 ? 20 : 0 }}>
                    {Array.from({ length: 12 }).map((_, i) => (
                      <span key={i} className="text-2xl">{catIcon(category)}</span>
                    ))}
                  </div>
                ))}
              </div>
            </div>
          )}
          <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/20 to-black/10" />

          <button onClick={() => navigate(-1)}
            className="absolute top-12 left-4 w-10 h-10 rounded-full bg-black/30 backdrop-blur-sm flex items-center justify-center">
            <FontAwesomeIcon icon={faArrowLeft} className="w-4 h-4 text-white" />
          </button>

          <div className="absolute top-12 right-4 flex gap-2">
            {isLoggedIn && !isOwnLink && (
              <button onClick={() => { if (!saved) setShowSaveToList(!showSaveToList); }}
                className="w-10 h-10 rounded-full bg-black/30 backdrop-blur-sm flex items-center justify-center">
                <FontAwesomeIcon icon={faBookmark} className={`w-4 h-4 ${saved ? "text-orange-400" : "text-white"}`} />
              </button>
            )}
            {isOwnLink && (
              <>
                <button onClick={() => setEditing(true)} className="w-10 h-10 rounded-full bg-black/30 backdrop-blur-sm flex items-center justify-center">
                  <FontAwesomeIcon icon={faPen} className="w-4 h-4 text-white" />
                </button>
                <button onClick={handleDelete} className="w-10 h-10 rounded-full bg-red-500/80 backdrop-blur-sm flex items-center justify-center">
                  <FontAwesomeIcon icon={faTrash} className="w-4 h-4 text-white" />
                </button>
              </>
            )}
          </div>

          <div className="absolute bottom-5 left-4 right-4">
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-white text-[11px] font-semibold mb-2" style={{ backgroundColor: color }}>
              {catIcon(category)} {catLabel(category)}
            </span>
            <h1 className="text-white text-xl font-bold leading-snug">{link.title}</h1>
          </div>
        </div>

        {/* White card */}
        <div className="bg-white rounded-t-3xl -mt-5 relative z-10 px-4 sm:px-6">

          {/* Stats */}
          <div className="flex items-center justify-around py-4 border-b border-gray-100">
            {link.rating > 0 && (
              <div className="flex flex-col items-center gap-0.5">
                <div className="flex items-center gap-1">
                  <FontAwesomeIcon icon={faStar} className="w-3.5 h-3.5 text-yellow-400" />
                  <span className="text-base font-bold text-gray-800">{link.rating}</span>
                </div>
                <span className="text-[10px] text-gray-400">Note</span>
              </div>
            )}
            <div className="flex flex-col items-center gap-0.5">
              <span className="text-base font-bold text-gray-800">{link.likeCount || 0}</span>
              <span className="text-[10px] text-gray-400">J'aime</span>
            </div>
            {link.ownerDisplayName && (
              <div className="flex flex-col items-center gap-0.5 max-w-[100px]">
                <span className="text-sm font-semibold text-gray-800 truncate">{link.ownerDisplayName}</span>
                <span className="text-[10px] text-gray-400">Auteur</span>
              </div>
            )}
          </div>

          {/* Author */}
          <div className="flex items-center gap-3 py-3.5 border-b border-gray-100">
            <div className="w-10 h-10 rounded-full flex items-center justify-center text-white font-bold text-sm flex-shrink-0"
              style={{ background: `linear-gradient(135deg, ${color}, ${color}CC)` }}>
              {link.ownerDisplayName?.charAt(0).toUpperCase() || "?"}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-1.5">
                <p className="text-sm font-semibold text-gray-800">{link.ownerDisplayName || "Anonyme"}</p>
                {link.ownerIsAdmin && <AdminBadge />}
              </div>
              {link.createdAt && (
                <p className="text-xs text-gray-400">
                  Partagé le {new Date(link.createdAt).toLocaleDateString("fr-FR", { day: "numeric", month: "long", year: "numeric" })}
                </p>
              )}
            </div>
          </div>

          {/* Save panel */}
          {showSaveToList && (
            <div className="my-4 bg-orange-50 rounded-2xl p-4 space-y-3">
              <p className="text-sm font-semibold text-gray-700">📥 Ajouter à mes listes</p>
              <select value={saveToFolderId} onChange={(e) => setSaveToFolderId(e.target.value)}
                className="w-full px-3 py-2 rounded-xl border border-orange-200 focus:border-orange-400 focus:outline-none text-sm bg-white">
                <option value="">Sans liste (mes idées)</option>
                {folderList.map((f: any) => <option key={f.id} value={f.id}>{f.name}</option>)}
              </select>
              <div className="flex gap-2">
                <button onClick={handleSaveToMyList} disabled={saving}
                  className="flex-1 py-2 rounded-xl bg-orange-500 text-white text-sm font-semibold hover:bg-orange-600 disabled:opacity-50 transition-colors">
                  {saving ? "Ajout..." : "Ajouter"}
                </button>
                <button onClick={() => setShowSaveToList(false)}
                  className="px-4 py-2 rounded-xl bg-gray-100 text-gray-600 text-sm hover:bg-gray-200 transition-colors">
                  Annuler
                </button>
              </div>
            </div>
          )}

          {saved && !showSaveToList && (
            <div className="my-3 flex items-center gap-2 text-sm text-green-600 bg-green-50 rounded-xl px-3 py-2">
              <FontAwesomeIcon icon={faCheck} className="w-3.5 h-3.5" /> Idée ajoutée à vos listes
            </div>
          )}

          {/* URL */}
          {link.url && (
            <div className="py-4">
              <div className="rounded-2xl overflow-hidden" style={{ border: `1.5px solid ${color}40` }}>
                <div className="flex items-center gap-2 px-4 py-2.5" style={{ backgroundColor: color + "15" }}>
                  <FontAwesomeIcon icon={faExternalLinkAlt} className="w-3 h-3" style={{ color }} />
                  <span className="text-[11px] font-bold uppercase tracking-wide" style={{ color }}>Lien de l'idée</span>
                </div>
                <div className="flex items-center gap-2 px-4 py-3 bg-white">
                  <a href={link.url} target="_blank" rel="noopener noreferrer" onClick={(e) => e.stopPropagation()}
                    className="flex-1 text-sm font-medium truncate" style={{ color }}>
                    {link.url}
                  </a>
                  <button onClick={handleCopyUrl}
                    className="w-8 h-8 rounded-xl flex items-center justify-center flex-shrink-0 transition-colors"
                    style={{ backgroundColor: copied ? "#DCFCE7" : "#F3F4F6" }}>
                    <FontAwesomeIcon icon={copied ? faCheck : faCopy} className="w-3.5 h-3.5"
                      style={{ color: copied ? "#16A34A" : "#9CA3AF" }} />
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* Description */}
          {link.description && (
            <div className="py-3">
              <p className="text-[11px] font-bold uppercase tracking-wide text-gray-400 mb-2">Description</p>
              <p className="text-sm text-gray-700 leading-relaxed">{link.description}</p>
            </div>
          )}

          {/* Info pills */}
          {(link.price || link.ageRange || link.location || folderName) && (
            <div className="py-3">
              <p className="text-[11px] font-bold uppercase tracking-wide text-gray-400 mb-3">Infos pratiques</p>
              <div className="grid grid-cols-2 gap-2.5">
                {link.price && <InfoPill icon={faEuroSign} label="Budget" value={link.price} accent="#10B981" />}
                {link.ageRange && <InfoPill icon={faBaby} label="Âge conseillé" value={link.ageRange} accent="#8B5CF6" />}
                {link.location && (
                  <button className="text-left" onClick={() => window.open(`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(link.location)}`, "_blank")}>
                    <InfoPill icon={faMapMarkerAlt} label="Lieu" value={link.location} accent="#3B82F6" />
                  </button>
                )}
                {folderName && <InfoPill icon={faFolderOpen} label="Dossier" value={folderName} accent={color} />}
              </div>
            </div>
          )}

          {/* Tags */}
          {link.tags?.length > 0 && (
            <div className="py-3">
              <p className="text-[11px] font-bold uppercase tracking-wide text-gray-400 mb-2">Tags</p>
              <div className="flex flex-wrap gap-2">
                {link.tags.map((tag: string) => (
                  <span key={tag} className="px-3 py-1 rounded-full text-xs font-semibold"
                    style={{ backgroundColor: color + "18", color }}>
                    #{tag}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Event date */}
          {link.eventDate && (
            <div className="py-3">
              <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-orange-50 text-orange-600 text-sm">
                <FontAwesomeIcon icon={faCalendar} className="w-3.5 h-3.5" />
                {new Date(Number(link.eventDate) * 1000).toLocaleDateString("fr-FR", { day: "numeric", month: "long", year: "numeric" })}
              </span>
            </div>
          )}

          {/* Ingredients */}
          {link.ingredients?.length > 0 && (
            <div className="py-3">
              <p className="text-[11px] font-bold uppercase tracking-wide text-gray-400 mb-2">Ingrédients</p>
              <ul className="space-y-1.5">
                {link.ingredients.map((ing: string, i: number) => (
                  <li key={i} className="text-sm text-gray-600 flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full flex-shrink-0" style={{ backgroundColor: color }} />
                    {ing}
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className="h-4" />
        </div>
      </div>

      {/* Bottom CTA */}
      <div className="fixed bottom-0 left-0 right-0 sm:relative sm:bottom-auto bg-white border-t border-gray-100 px-4 pt-3 pb-6 sm:pb-3 sm:border-0 sm:bg-transparent sm:max-w-2xl sm:mx-auto sm:mt-2">
        <div className="flex gap-3">
          {isLoggedIn && (
            <button onClick={toggleLike}
              className="w-12 h-12 rounded-2xl flex items-center justify-center border-2 transition-all flex-shrink-0"
              style={{ borderColor: link.likedByMe ? "#EF4444" : "#E5E7EB", backgroundColor: link.likedByMe ? "#FEF2F2" : "white" }}>
              <FontAwesomeIcon icon={link.likedByMe ? faHeart : faHeartRegular}
                className={`w-5 h-5 ${link.likedByMe ? "text-red-500" : "text-gray-400"}`} />
            </button>
          )}
          {link.url && (
            <a href={link.url} target="_blank" rel="noopener noreferrer"
              className="flex-1 h-12 rounded-2xl flex items-center justify-center gap-2 text-white font-semibold text-sm"
              style={{ background: `linear-gradient(135deg, ${color}, ${color}CC)` }}>
              <FontAwesomeIcon icon={faExternalLinkAlt} className="w-4 h-4" />
              Ouvrir le lien
            </a>
          )}
          {isLoggedIn && !isOwnLink && !link.url && (
            <button onClick={() => { if (!saved) setShowSaveToList(!showSaveToList); }}
              className="flex-1 h-12 rounded-2xl flex items-center justify-center gap-2 text-white font-semibold text-sm"
              style={{ background: saved ? "linear-gradient(135deg, #9CA3AF, #6B7280)" : `linear-gradient(135deg, ${color}, ${color}CC)` }}>
              <FontAwesomeIcon icon={faBookmark} className="w-4 h-4" />
              {saved ? "Sauvegardé ✓" : "Sauvegarder"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
