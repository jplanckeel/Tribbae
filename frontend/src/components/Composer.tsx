import { createContext, useCallback, useContext, useMemo, useState, type FormEvent, type ReactNode } from "react";
import { useLocation, useNavigate } from "react-router";
import * as api from "../lib/api";
import { CATEGORIES } from "../lib/categories";
import { useMyData } from "../lib/data";
import { toInput } from "../lib/links";
import { useSession } from "../lib/session";
import type { CategoryKey, Folder, FolderInput, Link, LinkInput } from "../lib/types";
import AiSheet from "./AiSheet";
import { useFeedback } from "./feedback";
import FolderFormSheet from "./FolderFormSheet";
import Icon from "./Icon";
import LinkFormSheet from "./LinkFormSheet";
import Sheet from "./Sheet";
import { Spinner } from "./ui";

// Point d'entrée unique pour créer / modifier : feuille « Ajouter à la tribu »,
// formulaire d'idée, formulaire de dossier et génération IA.

interface ComposerApi {
  openAdd: (opts?: { folderId?: string }) => void;
  newLink: (defaults?: Partial<LinkInput>) => void;
  editLink: (link: Link, onSaved?: (l: Link) => void) => void;
  newFolder: () => void;
  editFolder: (folder: Folder, onSaved?: (f: Folder) => void) => void;
  openAi: (opts?: { folderId?: string; prompt?: string }) => void;
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
  const [ai, setAi] = useState<{ folderId?: string; prompt?: string } | null>(null);

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
      <AddSheet
        open={!!add}
        onClose={() => setAdd(null)}
        onPick={(next) => {
          const folderId = add?.folderId;
          setAdd(null);
          if (next.kind === "link") setLinkForm({ initial: blank({ folderId, ...next.defaults }) });
          if (next.kind === "folder") setFolderForm({});
          if (next.kind === "ai") setAi({ folderId });
        }}
      />
      {linkForm && (
        <LinkFormSheet onClose={() => setLinkForm(null)} initial={linkForm.initial} editing={!!linkForm.link} onSubmit={submitLink} />
      )}
      {folderForm && <FolderFormSheet onClose={() => setFolderForm(null)} folder={folderForm.folder} onSubmit={submitFolder} />}
      {ai && (
        <AiSheet
          onClose={() => setAi(null)}
          folderId={ai.folderId}
          prompt={ai.prompt}
          onSaved={(fid) => {
            setAi(null);
            if (fid && location.pathname !== `/dossiers/${fid}`) navigate(`/dossiers/${fid}`);
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

// ── Feuille « Ajouter à la tribu » (maquette) ─────────────────────────

type Pick =
  | { kind: "link"; defaults: Partial<LinkInput> }
  | { kind: "folder" }
  | { kind: "ai" };

function AddSheet({ open, onClose, onPick }: { open: boolean; onClose: () => void; onPick: (p: Pick) => void }) {
  const [url, setUrl] = useState("");
  const [loading, setLoading] = useState(false);
  const canPaste = typeof navigator !== "undefined" && !!navigator.clipboard?.readText;

  const fromUrl = async (raw: string) => {
    const value = raw.trim();
    if (!value) return;
    setLoading(true);
    let defaults: Partial<LinkInput> = { url: value };
    try {
      const p = await api.links.preview(value);
      defaults = { url: value, title: p.title || "", description: p.description || "", imageUrl: p.image || "" };
    } catch {
      // L'aperçu est facultatif : on ouvre quand même le formulaire
    } finally {
      setLoading(false);
    }
    setUrl("");
    onPick({ kind: "link", defaults });
  };

  const paste = async () => {
    try {
      const text = await navigator.clipboard.readText();
      setUrl(text);
      if (/^https?:\/\//i.test(text.trim())) fromUrl(text);
    } catch {
      // Permission refusée : l'utilisateur colle à la main
    }
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    fromUrl(url);
  };

  return (
    <Sheet open={open} onClose={onClose} title="Ajouter à la tribu">
      <div className="flex flex-col gap-[18px]">
        <form onSubmit={submit} className="flex flex-col gap-2">
          <label htmlFor="add-url" className="text-[13px] font-bold text-sec">Un lien repéré ?</label>
          <div className="flex h-14 items-center gap-2.5 rounded-m border-2 border-line-2 bg-white pr-2 pl-4 text-muted focus-within:border-terracotta">
            <Icon name="link" size={20} />
            <input
              id="add-url"
              type="url"
              inputMode="url"
              autoComplete="off"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="Coller l'adresse d'une page"
              className="min-w-0 flex-1 bg-transparent text-[15px] font-medium text-ecorce outline-none placeholder:text-muted"
            />
            {loading ? (
              <Spinner size={22} className="mr-2 text-terracotta" />
            ) : url.trim() ? (
              <button type="submit" className="h-10 rounded-s bg-terracotta px-3 text-[13px] font-bold text-white">Ajouter</button>
            ) : canPaste ? (
              <button type="button" onClick={paste} className="h-10 rounded-s bg-ecorce px-3 text-[13px] font-bold text-sable">Coller</button>
            ) : null}
          </div>
          <span className="text-xs text-sec">Titre, image et description sont récupérés automatiquement.</span>
        </form>

        <div className="flex flex-col gap-2.5">
          <span className="text-[13px] font-bold text-sec">Ou partir d'une catégorie</span>
          <div className="grid grid-cols-4 gap-2">
            {CATEGORIES.map((c) => (
              <button
                key={c.key}
                type="button"
                onClick={() => onPick({ kind: "link", defaults: { category: c.key as CategoryKey } })}
                className={`flex h-[78px] flex-col items-center justify-center gap-1.5 rounded-m text-xs font-semibold transition-transform hover:-translate-y-0.5 ${c.bg} ${c.ink}`}
              >
                <Icon name={c.icon} size={22} className={c.ic} />
                {c.label}
              </button>
            ))}
            <button
              type="button"
              onClick={() => onPick({ kind: "folder" })}
              className="flex h-[78px] flex-col items-center justify-center gap-1.5 rounded-m border-2 border-dashed border-dash text-xs font-semibold text-sec transition-colors hover:bg-sable-2"
            >
              <Icon name="tipi" size={22} className="text-terracotta" />
              Dossier
            </button>
          </div>
        </div>

        <button
          type="button"
          onClick={() => onPick({ kind: "ai" })}
          className="flex items-center gap-3.5 rounded-l bg-prune p-4 text-left text-sable transition-colors hover:bg-[#4d3848]"
        >
          <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-m bg-soleil text-prune">
            <Icon name="sparkles" size={24} />
          </span>
          <span className="flex flex-1 flex-col gap-0.5">
            <span className="font-display text-[17px] font-bold">Demander à l'IA</span>
            <span className="text-[13px] text-prune-soft">« 10 activités d'intérieur pour un jour de pluie »</span>
          </span>
          <Icon name="chevronRight" size={20} />
        </button>
      </div>
    </Sheet>
  );
}
