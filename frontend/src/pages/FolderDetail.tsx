import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { AvatarStack, GridCard } from "../components/cards";
import { useComposer } from "../components/Composer";
import { useFeedback } from "../components/feedback";
import Icon from "../components/Icon";
import ShareFolderSheet from "../components/ShareFolderSheet";
import Sheet from "../components/Sheet";
import { Button, EmptyState, ErrorNote, MenuItem, PageSpinner } from "../components/ui";
import * as api from "../lib/api";
import { folderColor, folderIcon } from "../lib/categories";
import { useMyData } from "../lib/data";
import { plural } from "../lib/format";
import { byRecent, canEditFolder } from "../lib/links";
import { useSession } from "../lib/session";
import type { Folder, Link } from "../lib/types";
import { useAsync } from "../lib/useAsync";

const LOOSE_ID = "non-rangees";

export default function FolderDetail() {
  const { id = "" } = useParams();
  const data = useMyData();
  if (id === LOOSE_ID) {
    const loose = data.links.filter((l) => !l.folderId).sort(byRecent);
    return (
      <FolderView
        folder={{ id, ownerId: "", name: "Non rangées", icon: "BOOKMARK", color: "YELLOW" }}
        links={loose}
        virtual
      />
    );
  }
  return <RemoteFolder id={id} />;
}

function RemoteFolder({ id }: { id: string }) {
  const res = useAsync(() => Promise.all([api.folders.get(id), api.links.list(id)]), [id]);
  if (res.loading) return <PageSpinner />;
  if (res.error || !res.data) {
    return (
      <div className="mx-auto max-w-md px-5 pt-16">
        <EmptyState icon="tipi" title="Dossier introuvable" text={res.error?.message ?? "Ce dossier n'existe plus ou n'est pas partagé avec vous."} />
      </div>
    );
  }
  const [folder, links] = res.data;
  return (
    <FolderView
      folder={folder}
      links={[...links].sort(byRecent)}
      onFolder={(f) => res.setData((prev) => [f, prev?.[1] ?? links])}
    />
  );
}

function FolderView({ folder, links, virtual, onFolder }: {
  folder: Folder;
  links: Link[];
  virtual?: boolean;
  onFolder?: (f: Folder) => void;
}) {
  const session = useSession();
  const navigate = useNavigate();
  const { openAdd, openAi, editFolder, newLink } = useComposer();
  const { upsertFolder, removeFolder, links: mine } = useMyData();
  const { toast, confirm } = useFeedback();
  const [shareOpen, setShareOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [liked, setLiked] = useState(!!folder.likedByMe);
  const [likes, setLikes] = useState(folder.likeCount ?? 0);

  const isOwner = !virtual && !!session && folder.ownerId === session.userId;
  const canEdit = virtual || canEditFolder(folder, session?.userId);
  const color = folderColor(folder.color);
  // Les idées créées depuis la feuille d'ajout arrivent dans le cache global : on les fusionne
  const shown = useMemo(() => {
    if (virtual) return links;
    const ids = new Set(links.map((l) => l.id));
    const extra = mine.filter((l) => l.folderId === folder.id && !ids.has(l.id));
    const fresh = new Map(mine.map((l) => [l.id, l]));
    return [...extra, ...links.map((l) => fresh.get(l.id) ?? l)].filter((l) => !fresh.has(l.id) || fresh.get(l.id)!.folderId === folder.id);
  }, [links, mine, folder.id, virtual]);

  const setFolder = (f: Folder) => {
    onFolder?.(f);
    if (isOwner || canEdit) upsertFolder(f);
  };

  const like = async () => {
    if (!session) return navigate(`/bienvenue?next=/dossiers/${folder.id}`);
    const next = !liked;
    setLiked(next);
    setLikes((n) => n + (next ? 1 : -1));
    try {
      const r = next ? await api.folders.like(folder.id) : await api.folders.unlike(folder.id);
      if (typeof r?.likeCount === "number") setLikes(r.likeCount);
    } catch {
      setLiked(!next);
      setLikes((n) => n + (next ? -1 : 1));
    }
  };

  const remove = async () => {
    setMenuOpen(false);
    const ok = await confirm({
      title: `Supprimer « ${folder.name} » ?`,
      text: "Le dossier disparaît, ses idées sont conservées dans « Non rangées ».",
      confirmLabel: "Supprimer",
      danger: true,
    });
    if (!ok) return;
    try {
      await api.folders.delete(folder.id);
      removeFolder(folder.id);
      toast("Dossier supprimé");
      navigate("/mes-idees", { replace: true });
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };

  const collaborators = folder.collaborators ?? [];
  const visibilityLabel = virtual
    ? "Vos idées sans dossier"
    : folder.visibility === "VISIBILITY_PUBLIC"
      ? "Public"
      : collaborators.length
        ? "Partagé"
        : "Privé";

  return (
    <div>
      <title>{`${folder.name} · Tribbae`}</title>
      <header className="relative overflow-hidden px-5 pt-5 pb-8 md:px-8" style={{ background: color.bg }}>
        <div aria-hidden="true" className="absolute -right-24 -bottom-40 h-80 w-80 rounded-full bg-white/45" />
        <div className="relative mx-auto flex max-w-6xl flex-col gap-5">
          <div className="flex items-center justify-between">
            <button
              type="button"
              onClick={() => (window.history.length > 1 ? navigate(-1) : navigate("/mes-idees"))}
              aria-label="Retour"
              className="flex h-11 w-11 items-center justify-center rounded-full bg-white/90 hover:bg-white"
            >
              <Icon name="chevronLeft" size={22} />
            </button>
            <div className="flex gap-2">
              {isOwner && (
                <button type="button" onClick={() => setShareOpen(true)} aria-label="Inviter et partager" className="flex h-11 w-11 items-center justify-center rounded-full bg-white/90 hover:bg-white">
                  <Icon name="personPlus" size={21} />
                </button>
              )}
              {!virtual && !isOwner && folder.visibility === "VISIBILITY_PUBLIC" && (
                <button
                  type="button"
                  onClick={like}
                  aria-pressed={liked}
                  aria-label={`J'aime ce dossier (${likes})`}
                  className="flex h-11 items-center gap-1.5 rounded-full bg-white/90 px-3.5 font-bold text-terracotta hover:bg-white"
                >
                  <Icon name="heart" size={20} filled={liked} />
                  {likes}
                </button>
              )}
              {isOwner && (
                <button type="button" onClick={() => setMenuOpen(true)} aria-label="Plus d'actions" className="flex h-11 w-11 items-center justify-center rounded-full bg-white/90 hover:bg-white">
                  <Icon name="dots" size={21} />
                </button>
              )}
            </div>
          </div>
          <div className="flex items-end gap-4">
            <span className="flex h-16 w-16 shrink-0 items-center justify-center rounded-l text-white shadow-soft" style={{ background: color.solid }}>
              <Icon name={folderIcon(folder.icon)} size={30} />
            </span>
            <div className="flex min-w-0 flex-col gap-1">
              <h1 className="font-display text-[28px] leading-[1.05] font-extrabold tracking-[-0.6px] md:text-[36px]">{folder.name}</h1>
              <span className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-ecorce-2">
                <span>{plural(shown.length, "idée", "idées")}</span>
                <span aria-hidden="true">·</span>
                <span>{visibilityLabel}</span>
                {!isOwner && folder.ownerDisplayName && (
                  <>
                    <span aria-hidden="true">·</span>
                    <span>par {folder.ownerDisplayName}</span>
                  </>
                )}
                {folder.aiGenerated && (
                  <span className="flex items-center gap-1 rounded-lg bg-prune px-2 py-0.5 text-xs font-semibold text-sable"><Icon name="sparkle" size={12} />IA</span>
                )}
              </span>
            </div>
          </div>
          {collaborators.length > 0 && (
            <button type="button" onClick={() => isOwner && setShareOpen(true)} className="self-start" disabled={!isOwner}>
              <AvatarStack
                names={[folder.ownerDisplayName || session?.displayName || "?", ...collaborators.map((c) => c.displayName || c.email || "?")]}
                label={plural(collaborators.length + 1, "membre", "membres")}
              />
            </button>
          )}
        </div>
      </header>

      <div className="relative mx-auto -mt-4 flex max-w-6xl flex-col gap-5 rounded-t-xl bg-sable px-5 pt-5 md:px-8">
        {canEdit && (
          <div className="flex flex-wrap gap-2">
            <Button icon="plus" size="sm" onClick={() => (virtual ? newLink() : openAdd({ folderId: folder.id }))}>Ajouter une idée</Button>
            {!virtual && (
              <Button variant="ai" icon="sparkles" size="sm" onClick={() => openAi({ folderId: folder.id, prompt: folder.name })}>
                Compléter avec l'IA
              </Button>
            )}
          </div>
        )}
        {shown.length ? (
          <div className="columns-2 gap-3 md:columns-3 lg:columns-4">
            {shown.map((l, i) => <GridCard key={l.id} link={l} index={i} />)}
          </div>
        ) : (
          <EmptyState icon="tipi" title="Ce dossier attend ses premières idées" text={canEdit ? "Ajoutez un lien, une recette, une sortie… ou laissez l'IA le remplir." : undefined} />
        )}
        {!session && (
          <ErrorNote>
            Connectez-vous pour enregistrer ces idées dans votre tribu.
          </ErrorNote>
        )}
      </div>

      {isOwner && (
        <>
          {shareOpen && <ShareFolderSheet onClose={() => setShareOpen(false)} folder={folder} onChange={setFolder} />}
          <Sheet open={menuOpen} onClose={() => setMenuOpen(false)} title="Dossier">
            <div className="flex flex-col gap-2">
              <MenuItem icon="pencil" label="Modifier le nom, la couleur…" onClick={() => { setMenuOpen(false); editFolder(folder, setFolder); }} />
              <MenuItem icon="personPlus" label="Inviter et partager" onClick={() => { setMenuOpen(false); setShareOpen(true); }} />
              <MenuItem icon="trash" label="Supprimer le dossier" danger onClick={remove} />
            </div>
          </Sheet>
        </>
      )}
    </div>
  );
}
