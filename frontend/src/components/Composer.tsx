import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { useLocation, useNavigate } from "react-router";
import * as api from "../lib/api";
import { useMyData } from "../lib/data";
import { toInput } from "../lib/links";
import { useSession } from "../lib/session";
import type { Folder, FolderInput, Link, LinkInput } from "../lib/types";
import AddFlow from "./AddFlow";
import AiSheet from "./AiSheet";
import { useFeedback } from "./feedback";
import FolderFormSheet from "./FolderFormSheet";
import LinkFormSheet from "./LinkFormSheet";

// Point d'entrée unique pour créer / modifier : parcours « Ajouter une idée »
// (AddFlow), formulaire complet d'idée, formulaire de dossier et génération IA.

interface ComposerApi {
  openAdd: (opts?: { folderId?: string }) => void;
  newLink: (defaults?: Partial<LinkInput>) => void;
  editLink: (link: Link, onSaved?: (l: Link) => void) => void;
  newFolder: () => void;
  editFolder: (folder: Folder, onSaved?: (f: Folder) => void) => void;
  openAi: (opts?: { folderId?: string; prompt?: string; childId?: string }) => void;
}

const ComposerContext = createContext<ComposerApi | null>(null);

type LinkState = { initial: LinkInput; link?: Link; onSaved?: (l: Link) => void };
type FolderState = { folder?: Folder; onSaved?: (f: Folder) => void };

const blank = (d: Partial<LinkInput> = {}): LinkInput => ({
  title: "", url: "", description: "", category: "LINK_CATEGORY_IDEE", tags: [], visibility: "private", ...d,
});

export function ComposerProvider({ children }: { children: ReactNode }) {
  const session = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const { upsertLink, upsertFolder } = useMyData();
  const { toast } = useFeedback();

  const [add, setAdd] = useState<{ folderId?: string } | null>(null);
  const [linkForm, setLinkForm] = useState<LinkState | null>(null);
  const [folderForm, setFolderForm] = useState<FolderState | null>(null);
  const [ai, setAi] = useState<{ folderId?: string; prompt?: string; childId?: string } | null>(null);

  // Les actions de création demandent un compte
  const guard = useCallback(
    (fn: () => void) => () => {
      if (!session) {
        navigate(`/bienvenue?next=${encodeURIComponent(location.pathname + location.search)}`);
        return;
      }
      fn();
    },
    [session, navigate, location],
  );

  const api_ = useMemo<ComposerApi>(
    () => ({
      openAdd: (opts) => guard(() => setAdd(opts ?? {}))(),
      newLink: (d) => guard(() => setLinkForm({ initial: blank(d) }))(),
      editLink: (link, onSaved) => setLinkForm({ initial: toInput(link), link, onSaved }),
      newFolder: () => guard(() => setFolderForm({}))(),
      editFolder: (folder, onSaved) => setFolderForm({ folder, onSaved }),
      openAi: (opts) => guard(() => setAi(opts ?? {}))(),
    }),
    [guard],
  );

  const submitLink = async (data: LinkInput) => {
    if (!linkForm) return;
    if (linkForm.link) {
      const saved = await api.links.update(linkForm.link.id, data);
      upsertLink(saved);
      linkForm.onSaved?.(saved);
      toast("Idée mise à jour");
      setLinkForm(null);
    } else {
      const created = await api.links.create(data);
      upsertLink(created);
      toast("Idée ajoutée à la tribu");
      setLinkForm(null);
      navigate(`/links/${created.id}`);
    }
  };

  const submitFolder = async (data: FolderInput) => {
    if (!folderForm) return;
    if (folderForm.folder) {
      const saved = await api.folders.update(folderForm.folder.id, data);
      upsertFolder(saved);
      folderForm.onSaved?.(saved);
      toast("Dossier mis à jour");
      setFolderForm(null);
    } else {
      const created = await api.folders.create(data);
      upsertFolder(created);
      toast("Dossier créé");
      setFolderForm(null);
      navigate(`/dossiers/${created.id}`);
    }
  };

  return (
    <ComposerContext.Provider value={api_}>
      {children}
      {add && (
        <AddFlow
          folderId={add.folderId}
          onClose={() => setAdd(null)}
          onAi={() => {
            setAi({ folderId: add.folderId });
            setAdd(null);
          }}
          onMore={(draft) => {
            setLinkForm({ initial: blank(draft) });
            setAdd(null);
          }}
          onNewFolder={() => {
            setFolderForm({});
            setAdd(null);
          }}
        />
      )}
      {linkForm && (
        <LinkFormSheet
          onClose={() => setLinkForm(null)}
          initial={linkForm.initial}
          editing={!!linkForm.link}
          authorName={linkForm.link && linkForm.link.ownerId !== session?.userId ? linkForm.link.ownerDisplayName ?? "" : undefined}
          onSubmit={submitLink}
        />
      )}
      {folderForm && <FolderFormSheet onClose={() => setFolderForm(null)} folder={folderForm.folder} onSubmit={submitFolder} />}
      {ai && (
        <AiSheet
          onClose={() => setAi(null)}
          folderId={ai.folderId}
          prompt={ai.prompt}
          childId={ai.childId}
          onSaved={(fid) => {
            setAi(null);
            const to = fid ? `/dossiers/${fid}` : "/mes-idees";
            if (location.pathname !== to) navigate(to);
          }}
        />
      )}
    </ComposerContext.Provider>
  );
}

export function useComposer(): ComposerApi {
  const ctx = useContext(ComposerContext);
  if (!ctx) throw new Error("useComposer must be used inside ComposerProvider");
  return ctx;
}
