import { useEffect, useState } from "react";
import { folders as foldersApi, links as linksApi } from "../api";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faPlus, faShareAlt, faArrowLeft, faTrash, faUsers, faUserPlus,
  faTimes, faEye, faPen, faGlobe, faLock,
} from "@fortawesome/free-solid-svg-icons";
import LinkCard from "../components/LinkCard";
import { useNavigate } from "react-router-dom";

export default function Folders() {
  const [folderList, setFolderList] = useState<any[]>([]);
  const [selectedFolder, setSelectedFolder] = useState<any | null>(null);
  const [folderLinks, setFolderLinks] = useState<any[]>([]);
  const [showCreate, setShowCreate] = useState(false);
  const [newName, setNewName] = useState("");
  const [newVisibility, setNewVisibility] = useState("VISIBILITY_PRIVATE");
  const [newBannerUrl, setNewBannerUrl] = useState("");
  const [showCollabModal, setShowCollabModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [editName, setEditName] = useState("");
  const [editVisibility, setEditVisibility] = useState("");
  const [editBannerUrl, setEditBannerUrl] = useState("");
  const [collabEmail, setCollabEmail] = useState("");
  const [collabRole, setCollabRole] = useState("COLLABORATOR_ROLE_EDITOR");
  const [collabError, setCollabError] = useState("");
  const navigate = useNavigate();

  const fetchFolders = async () => {
    const res = await foldersApi.list();
    setFolderList(res.folders || []);
  };

  useEffect(() => { fetchFolders(); }, []);

  const openFolder = async (folder: any) => {
    setSelectedFolder(folder);
    const res = await linksApi.list(folder.id);
    setFolderLinks(res.links || []);
  };

  const openEditModal = () => {
    setEditName(selectedFolder.name);
    setEditVisibility(selectedFolder.visibility);
    setEditBannerUrl(selectedFolder.bannerUrl || "");
    setShowEditModal(true);
  };

  const updateFolder = async () => {
    if (!editName.trim() || !selectedFolder) return;
    const res: any = await foldersApi.update(selectedFolder.id, {
      name: editName.trim(),
      icon: "FOLDER",
      color: "BLUE",
      visibility: editVisibility,
      bannerUrl: editBannerUrl.trim() || undefined,
    });
    setSelectedFolder(res.folder || selectedFolder);
    setShowEditModal(false);
    fetchFolders();
  };

  const createFolder = async () => {
    if (!newName.trim()) return;
    await foldersApi.create({
      name: newName.trim(), icon: "FOLDER", color: "BLUE",
      visibility: newVisibility, bannerUrl: newBannerUrl.trim() || undefined,
    });
    setNewName("");
    setNewVisibility("VISIBILITY_PRIVATE");
    setNewBannerUrl("");
    setShowCreate(false);
    fetchFolders();
  };

  const shareFolder = async (id: string) => {
    const res = await foldersApi.share(id);
    navigator.clipboard.writeText(res.shareUrl);
    alert("Lien copié : " + res.shareUrl);
  };

  const deleteFolder = async (id: string) => {
    if (!confirm("Supprimer cette liste ?")) return;
    await foldersApi.delete(id);
    setSelectedFolder(null);
    fetchFolders();
  };

  const addCollaborator = async () => {
    if (!collabEmail.trim() || !selectedFolder) return;
    setCollabError("");
    try {
      const res: any = await foldersApi.addCollaborator(
        selectedFolder.id, collabEmail.trim(), collabRole
      );
      setSelectedFolder(res.folder || selectedFolder);
      setCollabEmail("");
      fetchFolders();
    } catch (err: any) {
      setCollabError(err.message);
    }
  };

  const removeCollaborator = async (userId: string) => {
    if (!selectedFolder) return;
    const res: any = await foldersApi.removeCollaborator(selectedFolder.id, userId);
    setSelectedFolder(res.folder || selectedFolder);
    fetchFolders();
  };

  const visIcon = (v: string) => {
    if (v === "VISIBILITY_PUBLIC") return <FontAwesomeIcon icon={faGlobe} className="w-3.5 h-3.5 text-green-500" />;
    if (v === "VISIBILITY_SHARED") return <FontAwesomeIcon icon={faUsers} className="w-3.5 h-3.5 text-blue-500" />;
    return <FontAwesomeIcon icon={faLock} className="w-3.5 h-3.5 text-gray-400" />;
  };

  const visLabel = (v: string) => {
    if (v === "VISIBILITY_PUBLIC") return "Communautaire";
    if (v === "VISIBILITY_SHARED") return "Partagée";
    return "Privée";
  };

  // --- Vue détail d'un dossier ---
  if (selectedFolder) {
    const collabs = selectedFolder.collaborators || [];
    return (
      <div className="max-w-5xl mx-auto px-4 py-6">
        {/* Bannière */}
        {selectedFolder.bannerUrl && (
          <div className="h-40 rounded-2xl overflow-hidden mb-4">
            <img src={selectedFolder.bannerUrl} alt="" className="w-full h-full object-cover" />
          </div>
        )}
        <div className="flex items-center gap-3 mb-4">
          <button onClick={() => setSelectedFolder(null)} className="text-orange-500">
            <FontAwesomeIcon icon={faArrowLeft} className="w-5 h-5" />
          </button>
          <div className="flex-1">
            <h2 className="text-xl font-bold text-gray-800">{selectedFolder.name}</h2>
            <div className="flex items-center gap-1 text-xs text-gray-400">
              {visIcon(selectedFolder.visibility)} {visLabel(selectedFolder.visibility)}
              {selectedFolder.ownerDisplayName && (
                <span className="ml-2">par {selectedFolder.ownerDisplayName}</span>
              )}
            </div>
          </div>
          <button onClick={() => setShowCollabModal(true)} className="text-gray-400 hover:text-blue-500" title="Collaborateurs">
            <FontAwesomeIcon icon={faUserPlus} className="w-4 h-4" />
          </button>
          <button onClick={openEditModal} className="text-gray-400 hover:text-orange-500" title="Modifier">
            <FontAwesomeIcon icon={faPen} className="w-4 h-4" />
          </button>
          <button onClick={() => shareFolder(selectedFolder.id)} className="text-gray-400 hover:text-green-500" title="Lien de partage">
            <FontAwesomeIcon icon={faShareAlt} className="w-4 h-4" />
          </button>
          <button onClick={() => deleteFolder(selectedFolder.id)} className="text-gray-400 hover:text-red-500" title="Supprimer">
            <FontAwesomeIcon icon={faTrash} className="w-4 h-4" />
          </button>
        </div>

        {/* Collaborateurs inline */}
        {collabs.length > 0 && (
          <div className="flex flex-wrap gap-2 mb-4">
            {collabs.map((c: any) => (
              <span key={c.userId} className="flex items-center gap-1 text-xs px-2 py-1 rounded-full bg-blue-50 text-blue-600">
                {c.role === "COLLABORATOR_ROLE_EDITOR" ? <FontAwesomeIcon icon={faPen} className="w-2.5 h-2.5" /> : <FontAwesomeIcon icon={faEye} className="w-2.5 h-2.5" />}
                {c.displayName || c.email}
                <button onClick={() => removeCollaborator(c.userId)} className="ml-1 hover:text-red-500">
                  <FontAwesomeIcon icon={faTimes} className="w-2.5 h-2.5" />
                </button>
              </span>
            ))}
          </div>
        )}

        {folderLinks.length === 0 ? (
          <p className="text-center text-gray-400 py-12">Liste vide</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {folderLinks.map((link) => (
              <LinkCard key={link.id} link={link} onClick={() => navigate(`/links/${link.id}`)} />
            ))}
          </div>
        )}

        {/* Modal ajout collaborateur */}
        {showCollabModal && (
          <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center">
            <div className="bg-white rounded-2xl p-6 w-full max-w-sm">
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-bold text-gray-800">Ajouter un collaborateur</h3>
                <button onClick={() => { setShowCollabModal(false); setCollabError(""); }} className="text-gray-400">
                  <FontAwesomeIcon icon={faTimes} className="w-5 h-5" />
                </button>
              </div>
              {collabError && (
                <div className="bg-red-50 text-red-600 text-sm rounded-xl px-4 py-2 mb-3">{collabError}</div>
              )}
              <input
                type="email" placeholder="Email de l'utilisateur"
                value={collabEmail} onChange={(e) => setCollabEmail(e.target.value)}
                className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm mb-3"
              />
              <div className="flex gap-2 mb-4">
                <button
                  onClick={() => setCollabRole("COLLABORATOR_ROLE_EDITOR")}
                  className={`flex-1 flex items-center justify-center gap-1 py-2 rounded-xl text-sm font-medium ${
                    collabRole === "COLLABORATOR_ROLE_EDITOR"
                      ? "bg-orange-500 text-white" : "bg-gray-100 text-gray-600"
                  }`}
                >
                  <FontAwesomeIcon icon={faPen} className="w-3.5 h-3.5" /> Éditeur
                </button>
                <button
                  onClick={() => setCollabRole("COLLABORATOR_ROLE_VIEWER")}
                  className={`flex-1 flex items-center justify-center gap-1 py-2 rounded-xl text-sm font-medium ${
                    collabRole === "COLLABORATOR_ROLE_VIEWER"
                      ? "bg-orange-500 text-white" : "bg-gray-100 text-gray-600"
                  }`}
                >
                  <FontAwesomeIcon icon={faEye} className="w-3.5 h-3.5" /> Lecteur
                </button>
              </div>
              <button
                onClick={addCollaborator}
                className="w-full py-2.5 rounded-xl bg-orange-500 text-white font-semibold hover:bg-orange-600"
              >
                Inviter
              </button>
            </div>
          </div>
        )}

        {/* Modal édition */}
        {showEditModal && (
          <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center">
            <div className="bg-white rounded-2xl p-6 w-full max-w-md">
              <div className="flex items-center justify-between mb-4">
                <h3 className="font-bold text-gray-800">Modifier la liste</h3>
                <button onClick={() => setShowEditModal(false)} className="text-gray-400">
                  <FontAwesomeIcon icon={faTimes} className="w-5 h-5" />
                </button>
              </div>
              <div className="space-y-3">
                <input
                  type="text" placeholder="Nom de la liste" value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm"
                />
                <input
                  type="url" placeholder="URL de la bannière (optionnel)" value={editBannerUrl}
                  onChange={(e) => setEditBannerUrl(e.target.value)}
                  className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm"
                />
                {editBannerUrl && (
                  <div className="h-24 rounded-xl overflow-hidden bg-gray-100">
                    <img src={editBannerUrl} alt="Aperçu" className="w-full h-full object-cover" onError={(e) => (e.currentTarget.style.display = "none")} />
                  </div>
                )}
                <div className="flex gap-2">
                  {[
                    { val: "VISIBILITY_PRIVATE", label: "Privée", icon: <FontAwesomeIcon icon={faLock} className="w-3.5 h-3.5" /> },
                    { val: "VISIBILITY_SHARED", label: "Partagée", icon: <FontAwesomeIcon icon={faUsers} className="w-3.5 h-3.5" /> },
                    { val: "VISIBILITY_PUBLIC", label: "Communautaire", icon: <FontAwesomeIcon icon={faGlobe} className="w-3.5 h-3.5" /> },
                  ].map((opt) => (
                    <button
                      key={opt.val}
                      onClick={() => setEditVisibility(opt.val)}
                      className={`flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-medium ${
                        editVisibility === opt.val
                          ? "bg-orange-500 text-white" : "bg-gray-100 text-gray-600"
                      }`}
                    >
                      {opt.icon} {opt.label}
                    </button>
                  ))}
                </div>
                <button
                  onClick={updateFolder}
                  className="w-full py-2.5 rounded-xl bg-orange-500 text-white font-semibold hover:bg-orange-600"
                >
                  Enregistrer
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }

  // --- Vue liste des dossiers ---
  return (
    <div className="max-w-5xl mx-auto px-4 py-6">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-xl font-bold text-gray-800">Listes</h2>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-1 px-4 py-2 rounded-xl bg-orange-500 text-white text-sm font-medium hover:bg-orange-600"
        >
          <FontAwesomeIcon icon={faPlus} className="w-4 h-4" /> Nouvelle
        </button>
      </div>

      {showCreate && (
        <div className="bg-white rounded-xl p-4 mb-4 shadow-sm space-y-3">
          <input
            type="text" placeholder="Nom de la liste" value={newName}
            onChange={(e) => setNewName(e.target.value)}
            className="w-full px-4 py-2 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm"
            onKeyDown={(e) => e.key === "Enter" && createFolder()}
          />
          <input
            type="url" placeholder="URL de la bannière (optionnel)" value={newBannerUrl}
            onChange={(e) => setNewBannerUrl(e.target.value)}
            className="w-full px-4 py-2 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm"
          />
          {newBannerUrl && (
            <div className="h-24 rounded-xl overflow-hidden bg-gray-100">
              <img src={newBannerUrl} alt="Aperçu" className="w-full h-full object-cover" onError={(e) => (e.currentTarget.style.display = "none")} />
            </div>
          )}
          <div className="flex gap-2">
            {[
              { val: "VISIBILITY_PRIVATE", label: "Privée", icon: <FontAwesomeIcon icon={faLock} className="w-3.5 h-3.5" /> },
              { val: "VISIBILITY_SHARED", label: "Partagée", icon: <FontAwesomeIcon icon={faUsers} className="w-3.5 h-3.5" /> },
              { val: "VISIBILITY_PUBLIC", label: "Communautaire", icon: <FontAwesomeIcon icon={faGlobe} className="w-3.5 h-3.5" /> },
            ].map((opt) => (
              <button
                key={opt.val}
                onClick={() => setNewVisibility(opt.val)}
                className={`flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-medium ${
                  newVisibility === opt.val
                    ? "bg-orange-500 text-white" : "bg-gray-100 text-gray-600"
                }`}
              >
                {opt.icon} {opt.label}
              </button>
            ))}
          </div>
          <button onClick={createFolder} className="px-4 py-2 rounded-xl bg-orange-500 text-white text-sm">
            Créer
          </button>
        </div>
      )}

      {folderList.length === 0 ? (
        <p className="text-center text-gray-400 py-12">Aucune liste</p>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {folderList.map((folder) => (
            <div
              key={folder.id}
              onClick={() => openFolder(folder)}
              className="bg-white rounded-2xl shadow-sm border border-gray-100 cursor-pointer hover:shadow-md transition-shadow overflow-hidden"
            >
              <div className="h-28 bg-gradient-to-br from-amber-100 to-orange-50 relative">
                {folder.bannerUrl ? (
                  <img src={folder.bannerUrl} alt="" className="w-full h-full object-cover" />
                ) : (
                  <div className="w-full h-full flex items-center justify-center text-4xl opacity-50">📁</div>
                )}
                <div className="absolute top-2 right-2">
                  {visIcon(folder.visibility)}
                </div>
              </div>
              <div className="p-3">
                <p className="font-semibold text-gray-800 line-clamp-1">{folder.name}</p>
                <div className="flex items-center gap-2 text-xs text-gray-400 mt-0.5">
                  <span>{visLabel(folder.visibility)}</span>
                  {(folder.collaborators?.length > 0) && (
                    <span className="flex items-center gap-0.5">
                      <FontAwesomeIcon icon={faUsers} className="w-2.5 h-2.5" /> {folder.collaborators.length}
                    </span>
                  )}
                  {folder.linkCount > 0 && (
                    <span>{folder.linkCount} idée{folder.linkCount > 1 ? "s" : ""}</span>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
