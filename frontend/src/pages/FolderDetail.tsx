import { useMemo, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router";
import { AvatarStack, GridCard, LikeHeart, MosaicTile, RowCard } from "../components/cards";
import { likeFeedback } from "../lib/feedback";
import { useComposer } from "../components/Composer";
import { useFeedback } from "../components/feedback";
import { FolderCover, FolderGlyph } from "../components/FolderCover";
import Icon from "../components/Icon";
import ShareFolderSheet from "../components/ShareFolderSheet";
import Sheet from "../components/Sheet";
import { Avatar, Button, EmptyState, ErrorNote, MenuItem, PageSpinner } from "../components/ui";
import * as api from "../lib/api";
import { CATEGORIES, folderColor } from "../lib/categories";
import { countdown, folderInput, keyDateOf, sortFolderLinks, useFolderChildren } from "../lib/folderStyle";
import { useMyData } from "../lib/data";
import { plural } from "../lib/format";
import { byRecent, canEditFolder } from "../lib/links";
import { useSession } from "../lib/session";
import type { Folder, Link } from "../lib/types";
import { useAsync } from "../lib/useAsync";
import Seo from "../components/Seo";

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
      links={links}
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
  const { upsertFolder, removeFolder, links: mine, peers } = useMyData();
  const { toast, confirm } = useFeedback();
  const [shareOpen, setShareOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [liked, setLiked] = useState(!!folder.likedByMe);
  const [likes, setLikes] = useState(folder.likeCount ?? 0);

  const isOwner = !virtual && !!session && folder.ownerId === session.userId;
  const canEdit = virtual || canEditFolder(folder, session?.userId, peers);
  const color = folderColor(folder.color);
  // Ouvert depuis la fiche d'une idée : elle est surlignée pour garder le fil
  const from = (useLocation().state as { from?: string } | null)?.from;
  const mark = (id: string) => (id === from ? color.solid : undefined);
  const cd = countdown(keyDateOf(folder), folder.keyDateType);
  const kids = useFolderChildren(folder);
  // Les idées créées depuis la feuille d'ajout arrivent dans le cache global : on les fusionne
  const shown = useMemo(() => {
    if (virtual) return links;
    const ids = new Set(links.map((l) => l.id));
    const extra = mine.filter((l) => l.folderId === folder.id && !ids.has(l.id));
    const fresh = new Map(mine.map((l) => [l.id, l]));
    const merged = [...extra, ...links.map((l) => fresh.get(l.id) ?? l)].filter((l) => !fresh.has(l.id) || fresh.get(l.id)!.folderId === folder.id);
    return sortFolderLinks(merged, folder.sort, CATEGORIES.map((c) => c.key));
  }, [links, mine, folder.id, folder.sort, virtual]);

  const setFolder = (f: Folder) => {
    onFolder?.(f);
    if (isOwner || canEdit) upsertFolder(f);
  };

  const like = async () => {
    if (!session) return navigate(`/bienvenue?next=/dossiers/${folder.id}`);
    const next = !liked;
    if (next) likeFeedback();
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

  const togglePin = async () => {
    setMenuOpen(false);
    try {
      const saved = await api.folders.update(folder.id, { ...folderInput(folder), pinned: !folder.pinned });
      setFolder(saved);
      toast(saved.pinned ? "Épinglé en haut de Mes idées" : "Dossier désépinglé");
    } catch (e) {
      toast((e as Error).message, "error");
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
      <Seo title={`${folder.name} · dossier d'idées · Tribbae`} description={`${folder.name} : un dossier d'idées sur Tribbae.`} path={`/dossiers/${folder.id}`} />
      <header className="relative">
        <div className="relative h-[184px] overflow-hidden md:h-[240px]" style={{ background: color.bg }}>
          {virtual ? (
            <div aria-hidden="true" className="absolute -right-24 -bottom-40 h-80 w-80 rounded-full bg-white/45" />
          ) : (
            <FolderCover folder={folder} links={shown} />
          )}
          <div className="relative mx-auto flex max-w-[1200px] items-center justify-between px-5 pt-5 md:px-8">
            <button
              type="button"
              onClick={() => (window.history.length > 1 ? navigate(-1) : navigate("/mes-idees"))}
              aria-label="Retour"
              className="flex h-11 w-11 items-center justify-center rounded-full bg-white/90 hover:bg-card"
            >
              <Icon name="chevronLeft" size={22} />
            </button>
            <div className="flex gap-2">
              {isOwner && (
                <button type="button" onClick={() => setShareOpen(true)} aria-label="Inviter et partager" className="flex h-11 w-11 items-center justify-center rounded-full bg-white/90 hover:bg-card">
                  <Icon name="personPlus" size={21} />
                </button>
              )}
              {!virtual && !isOwner && folder.visibility === "VISIBILITY_PUBLIC" && (
                <button
                  type="button"
                  onClick={like}
                  aria-pressed={liked}
                  aria-label={`J'aime ce dossier (${likes})`}
                  className="flex h-11 items-center gap-1.5 rounded-full bg-white/90 px-3.5 font-bold text-terracotta hover:bg-card"
                >
                  <LikeHeart liked={liked} size={20} />
                  {likes}
                </button>
              )}
              {isOwner && (
                <button type="button" onClick={() => setMenuOpen(true)} aria-label="Plus d'actions" className="flex h-11 w-11 items-center justify-center rounded-full bg-white/90 hover:bg-card">
                  <Icon name="dots" size={21} />
                </button>
              )}
            </div>
          </div>
          {cd && (
            <span
              className="absolute right-5 bottom-10 rounded-[10px] px-2.5 py-[5px] text-xs font-extrabold md:right-[max(2rem,calc((100%-1200px)/2+2rem))]"
              style={folder.keyDateType === "vacances" ? { background: "var(--color-ecorce)", color: "var(--color-sable)" } : { background: color.solid, color: "var(--color-on-solid)" }}
            >
              {cd.label}
            </span>
          )}
        </div>

        <div className="relative mx-auto -mt-7 flex max-w-[1200px] flex-col gap-3 rounded-t-xl bg-sable px-5 pt-0 md:px-8">
          <div className="flex items-end gap-4">
            <span className="-mt-5 flex h-16 w-16 shrink-0 items-center justify-center rounded-[20px] border-4 border-sable text-on-solid" style={{ background: virtual ? "var(--color-sec)" : color.solid }}>
              {virtual ? <Icon name="bookmark" size={30} /> : <FolderGlyph icon={folder.icon} size={30} />}
            </span>
            {kids.length > 0 && (
              <span className="mb-1 flex items-center gap-1.5 text-xs text-sec">
                Pour
                <span className="flex">
                  {kids.map(({ child, colors: [bg, ink] }, i) => (
                    <Avatar key={child.id} name={child.name} color={bg} ink={ink} size={26} className={`border-2 border-sable ${i ? "-ml-2" : ""}`} />
                  ))}
                </span>
                {kids.map((k) => k.child.name).join(", ")}
              </span>
            )}
          </div>
          <div className="flex min-w-0 flex-col gap-1">
            <h1 className="font-display text-[28px] leading-[1.05] font-extrabold tracking-[-0.6px] md:text-[36px]">{folder.name}</h1>
            {folder.description && <p className="text-[15px] text-ecorce-2">{folder.description}</p>}
            <span className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-sec">
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

      <div className="mx-auto flex max-w-[1200px] flex-col gap-5 px-5 pt-5 md:px-8">
        {canEdit && (
          <div className="flex flex-wrap gap-2">
            <Button icon="plus" size="sm" onClick={() => (virtual ? newLink() : openAdd({ folderId: folder.id }))}>Ajouter une idée</Button>
            {!virtual && (
              <Button variant="ai" icon="sparkles" size="sm" onClick={() => openAi({ folderId: folder.id, prompt: folder.name })}>
                Compléter avec l'IA
              </Button>
            )}
            {!virtual && (
              <Button variant="ghost" icon="pencil" size="sm" onClick={() => editFolder(folder, setFolder)}>
                Personnaliser
              </Button>
            )}
          </div>
        )}
        {shown.length ? (
          folder.viewMode === "list" ? (
            <div className="grid gap-2.5 md:grid-cols-2">
              {shown.map((l) => <RowCard key={l.id} link={l} highlight={mark(l.id)} />)}
            </div>
          ) : folder.viewMode === "mosaic" ? (
            <div className="grid auto-rows-[132px] grid-flow-dense grid-cols-2 gap-3 md:auto-rows-[160px] md:grid-cols-3 lg:grid-cols-4">
              {shown.map((l, i) => <MosaicTile key={l.id} link={l} large={i % 3 === 0} highlight={mark(l.id)} />)}
            </div>
          ) : (
            <div className="columns-2 gap-3 md:columns-3 lg:columns-4">
              {shown.map((l, i) => <GridCard key={l.id} link={l} index={i} highlight={mark(l.id)} />)}
            </div>
          )
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
              <MenuItem icon="pencil" label="Personnaliser" onClick={() => { setMenuOpen(false); editFolder(folder, setFolder); }} />
              <MenuItem icon="pin" label={folder.pinned ? "Désépingler de Mes idées" : "Épingler en haut de Mes idées"} onClick={togglePin} />
              <MenuItem icon="personPlus" label="Inviter et partager" onClick={() => { setMenuOpen(false); setShareOpen(true); }} />
              <MenuItem icon="trash" label="Supprimer le dossier" danger onClick={remove} />
            </div>
          </Sheet>
        </>
      )}
    </div>
  );
}
