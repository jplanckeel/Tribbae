import { useEffect, useState } from "react";
import { children as childrenApi } from "../api";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faPlus, faPen, faTrash, faTimes, faCheck, faBaby } from "@fortawesome/free-solid-svg-icons";

function calcAge(birthDateMs: number): string {
  const now = Date.now();
  const diffMs = now - birthDateMs;
  const months = Math.floor(diffMs / (1000 * 60 * 60 * 24 * 30.44));
  if (months < 24) return `${months} mois`;
  const years = Math.floor(months / 12);
  const rem = months % 12;
  return rem > 0 ? `${years} ans ${rem} mois` : `${years} ans`;
}

function formatDate(ms: number): string {
  return new Date(ms).toLocaleDateString("fr-FR");
}

const AVATAR_COLORS = [
  "#4FC3F7", "#FF8C00", "#81C784", "#BA68C8", "#FF7043", "#FFD700",
];

export default function Children() {
  const [list, setList] = useState<any[]>([]);
  const [showAdd, setShowAdd] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [birthDate, setBirthDate] = useState("");
  const [error, setError] = useState("");

  const fetch = async () => {
    const res = await childrenApi.list();
    setList(res.children || []);
  };

  useEffect(() => { fetch(); }, []);

  const resetForm = () => { setName(""); setBirthDate(""); setError(""); };

  const handleAdd = async () => {
    if (!name.trim() || !birthDate) { setError("Prénom et date requis"); return; }
    try {
      await childrenApi.create(name.trim(), new Date(birthDate).getTime());
      resetForm(); setShowAdd(false); fetch();
    } catch (e: any) { setError(e.message); }
  };

  const handleEdit = async (id: string) => {
    if (!name.trim() || !birthDate) { setError("Prénom et date requis"); return; }
    try {
      await childrenApi.update(id, name.trim(), new Date(birthDate).getTime());
      resetForm(); setEditingId(null); fetch();
    } catch (e: any) { setError(e.message); }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Supprimer cet enfant ?")) return;
    await childrenApi.delete(id);
    fetch();
  };

  const startEdit = (child: any) => {
    setEditingId(child.id);
    setName(child.name);
    setBirthDate(new Date(child.birthDate).toISOString().split("T")[0]);
    setError("");
  };

  return (
    <div className="max-w-2xl mx-auto px-4 py-6">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-2">
          <FontAwesomeIcon icon={faBaby} className="w-6 h-6 text-blue-400" />
          <h2 className="text-xl font-bold text-gray-800">Mes enfants</h2>
        </div>
        <button
          onClick={() => { resetForm(); setShowAdd(true); setEditingId(null); }}
          className="flex items-center gap-1 px-4 py-2 rounded-xl bg-orange-500 text-white text-sm font-medium hover:bg-orange-600"
        >
          <FontAwesomeIcon icon={faPlus} className="w-4 h-4" /> Ajouter
        </button>
      </div>

      <p className="text-sm text-gray-400 mb-4">
        Sélectionnez un enfant sur l'accueil pour filtrer les idées par âge.
      </p>

      {/* Formulaire ajout */}
      {showAdd && (
        <div className="bg-white rounded-2xl shadow-sm p-4 mb-4 space-y-3">
          <h3 className="font-semibold text-gray-700">Nouvel enfant</h3>
          {error && <p className="text-red-500 text-sm">{error}</p>}
          <input
            type="text" placeholder="Prénom" value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm"
          />
          <div>
            <label className="text-xs text-gray-400 mb-1 block">Date de naissance</label>
            <input
              type="date" value={birthDate}
              onChange={(e) => setBirthDate(e.target.value)}
              max={new Date().toISOString().split("T")[0]}
              className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm"
            />
          </div>
          <div className="flex gap-2">
            <button onClick={handleAdd} className="flex-1 py-2 rounded-xl bg-orange-500 text-white text-sm font-medium hover:bg-orange-600">
              Ajouter
            </button>
            <button onClick={() => { setShowAdd(false); resetForm(); }} className="px-4 py-2 rounded-xl bg-gray-100 text-gray-600 text-sm">
              Annuler
            </button>
          </div>
        </div>
      )}

      {list.length === 0 && !showAdd ? (
        <div className="text-center py-16 text-gray-400">
          <FontAwesomeIcon icon={faBaby} className="mx-auto mb-4 text-gray-300 w-12 h-12" />
          <p>Aucun enfant ajouté</p>
        </div>
      ) : (
        <div className="space-y-3">
          {list.map((child, i) => {
            const color = AVATAR_COLORS[i % AVATAR_COLORS.length];
            const isEditing = editingId === child.id;
            return (
              <div key={child.id} className="bg-white rounded-2xl shadow-sm p-4">
                {isEditing ? (
                  <div className="space-y-3">
                    {error && <p className="text-red-500 text-sm">{error}</p>}
                    <input
                      type="text" value={name} onChange={(e) => setName(e.target.value)}
                      className="w-full px-4 py-2 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm"
                    />
                    <input
                      type="date" value={birthDate} onChange={(e) => setBirthDate(e.target.value)}
                      max={new Date().toISOString().split("T")[0]}
                      className="w-full px-4 py-2 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm"
                    />
                    <div className="flex gap-2">
                      <button onClick={() => handleEdit(child.id)} className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-orange-500 text-white text-sm">
                        <FontAwesomeIcon icon={faCheck} className="w-3.5 h-3.5" /> Enregistrer
                      </button>
                      <button onClick={() => { setEditingId(null); resetForm(); }} className="px-3 py-1.5 rounded-xl bg-gray-100 text-gray-600 text-sm">
                        <FontAwesomeIcon icon={faTimes} className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="flex items-center gap-3">
                    <div
                      className="w-12 h-12 rounded-full flex items-center justify-center text-white font-bold text-lg flex-shrink-0"
                      style={{ backgroundColor: color }}
                    >
                      {child.name.charAt(0).toUpperCase()}
                    </div>
                    <div className="flex-1">
                      <p className="font-semibold text-gray-800">{child.name}</p>
                      <p className="text-sm text-gray-400">
                        {calcAge(child.birthDate)} · né le {formatDate(child.birthDate)}
                      </p>
                    </div>
                    <button onClick={() => startEdit(child)} className="text-gray-400 hover:text-orange-500 p-1">
                      <FontAwesomeIcon icon={faPen} className="w-4 h-4" />
                    </button>
                    <button onClick={() => handleDelete(child.id)} className="text-gray-400 hover:text-red-500 p-1">
                      <FontAwesomeIcon icon={faTrash} className="w-4 h-4" />
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
