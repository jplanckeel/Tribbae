import { useEffect, useState, useRef } from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faTimes, faStar, faSpinner } from "@fortawesome/free-solid-svg-icons";
import { links as linksApi, folders as foldersApi } from "../api";
import { CATEGORIES, CATEGORY_COLORS } from "../types";

interface Props {
  onClose: () => void;
  onCreated: () => void;
  initialFolderId?: string;
}

export default function AddLinkModal({ onClose, onCreated, initialFolderId }: Props) {
  const [title, setTitle] = useState("");
  const [url, setUrl] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState<string>(CATEGORIES[0].value);
  const [tags, setTags] = useState("");
  const [location, setLocation] = useState("");
  const [price, setPrice] = useState("");
  const [ageRange, setAgeRange] = useState("");
  const [folderId, setFolderId] = useState(initialFolderId || "");
  const [rating, setRating] = useState(0);
  const [ingredients, setIngredients] = useState("");
  const [imageUrl, setImageUrl] = useState("");
  const [folderList, setFolderList] = useState<any[]>([]);
  const [error, setError] = useState("");
  const [loadingPreview, setLoadingPreview] = useState(false);
  const lastFetchedUrl = useRef("");

  useEffect(() => {
    foldersApi.list().then((res) => setFolderList(res.folders || []));
  }, []);

  const fetchPreview = async (rawUrl: string) => {
    const trimmed = rawUrl.trim();
    if (!trimmed || trimmed === lastFetchedUrl.current) return;
    if (!/^https?:\/\/.+/.test(trimmed)) return;
    lastFetchedUrl.current = trimmed;
    setLoadingPreview(true);
    try {
      const meta = await linksApi.preview(trimmed);
      if (!title && meta.title) setTitle(meta.title);
      if (!description && meta.description) setDescription(meta.description);
      if (meta.image) setImageUrl(meta.image);
    } catch {
      // silencieux
    }
    setLoadingPreview(false);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    try {
      await linksApi.create({
        title: title.trim(),
        url: url.trim(),
        description: description.trim(),
        category,
        tags: tags.split(",").map((t) => t.trim()).filter(Boolean),
        location: location.trim(),
        price: price.trim(),
        ageRange: ageRange.trim(),
        folderId: folderId || undefined,
        rating,
        imageUrl: imageUrl.trim(),
        ingredients: category === "LINK_CATEGORY_RECETTE"
          ? ingredients.split("\n").map((s) => s.trim()).filter(Boolean)
          : [],
      });
      onCreated();
    } catch (err: any) {
      setError(err.message);
    }
  };

  const inputCls = "w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm";

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-end sm:items-center justify-center">
      <div className="bg-white rounded-t-3xl sm:rounded-3xl w-full max-w-lg max-h-[92vh] overflow-y-auto p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-gray-800">Nouvelle idée</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <FontAwesomeIcon icon={faTimes} className="w-5 h-5" />
          </button>
        </div>

        {error && <div className="bg-red-50 text-red-600 text-sm rounded-xl px-4 py-2 mb-4">{error}</div>}

        <form onSubmit={handleSubmit} className="space-y-3">
          {/* URL avec preview auto */}
          <div className="relative">
            <input
              type="url" placeholder="URL (optionnel)"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              onBlur={(e) => fetchPreview(e.target.value)}
              className={inputCls}
            />
            {loadingPreview && (
              <FontAwesomeIcon icon={faSpinner} className="absolute right-3 top-1/2 -translate-y-1/2 text-orange-400 w-4 h-4 animate-spin" />
            )}
          </div>

          {/* Preview image */}
          {imageUrl && (
            <div className="relative rounded-xl overflow-hidden h-32">
              <img src={imageUrl} alt="preview" className="w-full h-full object-cover" />
              <button
                type="button"
                onClick={() => setImageUrl("")}
                className="absolute top-2 right-2 bg-black/50 text-white rounded-full w-6 h-6 flex items-center justify-center hover:bg-black/70"
              >
                <FontAwesomeIcon icon={faTimes} className="w-3 h-3" />
              </button>
            </div>
          )}

          <input type="text" placeholder="Titre *" value={title} onChange={(e) => setTitle(e.target.value)} required className={inputCls} />
          <textarea placeholder="Description" value={description} onChange={(e) => setDescription(e.target.value)} rows={2} className={`${inputCls} resize-none`} />

          {/* Catégorie */}
          <div className="flex gap-2 flex-wrap">
            {CATEGORIES.map((cat) => {
              const active = category === cat.value;
              const color = CATEGORY_COLORS[cat.value];
              return (
                <button key={cat.value} type="button" onClick={() => setCategory(cat.value)}
                  className="px-3 py-1.5 rounded-full text-xs font-medium transition-colors"
                  style={{ backgroundColor: active ? color : color + "18", color: active ? "white" : color }}
                >
                  {cat.icon} {cat.label}
                </button>
              );
            })}
          </div>

          {/* Liste */}
          <select value={folderId} onChange={(e) => setFolderId(e.target.value)}
            className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm bg-white"
          >
            <option value="">Aucune liste</option>
            {folderList.map((f) => (
              <option key={f.id} value={f.id}>{f.name}</option>
            ))}
          </select>

          <div className="grid grid-cols-2 gap-3">
            {category !== "LINK_CATEGORY_RECETTE" && (
              <input type="text" placeholder="Lieu" value={location} onChange={(e) => setLocation(e.target.value)} className={inputCls} />
            )}
            <input type="text" placeholder="Prix" value={price} onChange={(e) => setPrice(e.target.value)} className={inputCls} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <input type="text" placeholder="Âge (ex: 3 ans)" value={ageRange} onChange={(e) => setAgeRange(e.target.value)} className={inputCls} />
            <input type="text" placeholder="Tags (virgule)" value={tags} onChange={(e) => setTags(e.target.value)} className={inputCls} />
          </div>

          {category === "LINK_CATEGORY_RECETTE" && (
            <textarea
              placeholder="Ingrédients (un par ligne)"
              value={ingredients} onChange={(e) => setIngredients(e.target.value)}
              rows={3} className={`${inputCls} resize-none`}
            />
          )}

          {/* Note */}
          <div className="flex items-center gap-2">
            <span className="text-sm text-gray-500">Note :</span>
            {[1, 2, 3, 4, 5].map((n) => (
              <button key={n} type="button" onClick={() => setRating(rating === n ? 0 : n)}>
                <FontAwesomeIcon icon={faStar} className="w-5 h-5" style={{ color: n <= rating ? "#FFD700" : "#e5e7eb" }} />
              </button>
            ))}
          </div>

          <button type="submit" className="w-full py-2.5 rounded-xl bg-orange-500 text-white font-semibold hover:bg-orange-600 transition-colors">
            Ajouter
          </button>
        </form>
      </div>
    </div>
  );
}
