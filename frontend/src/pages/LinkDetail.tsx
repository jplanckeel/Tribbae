import { useEffect, useEffectEvent, useRef, useState, type FormEvent } from "react";
import { Link as RouterLink, useLocation, useNavigate, useParams } from "react-router";
import { LikeHeart } from "../components/cards";
import { useComposer } from "../components/Composer";
import { useFeedback } from "../components/feedback";
import { FolderGlyph } from "../components/FolderCover";
import Icon, { type IconName } from "../components/Icon";
import { TipiMark } from "../components/Logo";
import Sheet from "../components/Sheet";
import { Avatar, Button, EmptyState, MenuItem, PageSpinner, Spinner, Toggle } from "../components/ui";
import * as api from "../lib/api";
import { AVATAR_COLORS, categoryMeta, folderColor } from "../lib/categories";
import { useMyData } from "../lib/data";
import { likeFeedback } from "../lib/feedback";
import { eventDateMs, formatShortDate, formatTime, hostname, safeHref, safeImage } from "../lib/format";
import { canEditFolder, copyInput, toInput } from "../lib/links";
import { ideaSeoDescription, ideaSeoTitle } from "../lib/seo";
import { useSession } from "../lib/session";
import { shareOrCopy } from "../lib/share";
import type { Comment, Link } from "../lib/types";
import { pushLink, useIdeaMorphTarget } from "../lib/transitions";
import { useAsync } from "../lib/useAsync";
import Seo from "../components/Seo";

export default function LinkDetail() {
  const { id = "" } = useParams();
  const res = useAsync(() => api.links.get(id), [id]);
  const { links } = useMyData();
  const state = useLocation().state as { preview?: Link } | null;
  // Idée déjà connue (cache, carte d'origine) : affichée tout de suite pendant le
  // chargement, pour que la carte puisse devenir la fiche sans écran d'attente
  const known = links.find((l) => l.id === id) ?? (state?.preview?.id === id ? state.preview : undefined);
  if (res.loading) return known ? <Detail link={known} onChange={() => {}} /> : <PageSpinner />;
  if (res.error || !res.data) {
    return (
      <div className="mx-auto max-w-md px-5 pt-16">
        <EmptyState
          icon="tipi"
          title="Idée introuvable"
          text={res.error?.message ?? "Elle a peut-être été supprimée ou n'est pas partagée avec vous."}
          action={<RouterLink to="/explorer" className="font-bold text-terracotta">Explorer les idées</RouterLink>}
        />
      </div>
    );
  }
  return <Detail link={res.data} onChange={(l) => res.setData(l)} />;
}

function Detail({ link, onChange }: { link: Link; onChange: (l: Link) => void }) {
  const morph = useIdeaMorphTarget(link.id);
  const session = useSession();
  const navigate = useNavigate();
  const { editLink } = useComposer();
  const { folders, upsertLink, removeLink, peers, isTribePeer } = useMyData();
  const { toast, confirm } = useFeedback();
  const [menuOpen, setMenuOpen] = useState(false);
  const [liked, setLiked] = useState(!!link.likedByMe);
  const [likes, setLikes] = useState(link.likeCount ?? 0);
  // Adresse de l'image qui n'a pas chargé : une nouvelle image l'efface d'elle-même
  const [brokenSrc, setBrokenSrc] = useState<string>();
  const [saving, setSaving] = useState(false);
  const commentsRef = useRef<HTMLElement>(null);

  const cat = categoryMeta(link.category);
  const isOwner = !!session && link.ownerId === session.userId;
  // Idée de l'utilisateur ou d'un membre de sa tribu : il la gère comme la sienne
  const ourIdea = isOwner || isTribePeer(link.ownerId);
  const folder = folders.find((f) => f.id === link.folderId);
  const canEdit = ourIdea || (!!folder && canEditFolder(folder, session?.userId, peers));
  const at = eventDateMs(link.eventDate);
  const href = safeHref(link.url);
  const img = safeImage(link.imageUrl);
  const imgBroken = !!img && brokenSrc === img;
  const comments = useAsync(() => api.comments.list(link.id), [link.id]);

  const requireAuth = () => navigate(`/bienvenue?next=/links/${link.id}`);

  const apply = (l: Link) => {
    onChange(l);
    if (canEdit) upsertLink(l);
  };

  // Idée sans image, ou dont l'image ne charge plus : on cherche celle de la
  // page et on l'enregistre (une tentative par cas et par visite)
  const imageTries = useRef(new Set<string>());
  const findImage = useEffectEvent(async () => {
    const key = `${link.id}:${imgBroken ? link.imageUrl : ""}`;
    if (!link.url || imageTries.current.has(key)) return;
    imageTries.current.add(key);
    try {
      const p = await api.links.preview(link.url);
      const next = [...(p.images ?? []), p.image].find((u) => u && u !== link.imageUrl);
      if (!next) return;
      apply(await api.links.update(link.id, { ...toInput(link), imageUrl: next }));
    } catch {
      // Page injoignable : le serveur réessaiera de son côté
    }
  });
  useEffect(() => {
    if (canEdit && href && (!img || imgBroken)) void findImage();
  }, [link.id, img, imgBroken, canEdit, href]);

  const like = async () => {
    if (!session) return requireAuth();
    const next = !liked;
    if (next) likeFeedback();
    setLiked(next);
    setLikes((n) => n + (next ? 1 : -1));
    try {
      const r = next ? await api.links.like(link.id) : await api.links.unlike(link.id);
      if (typeof r?.likeCount === "number") setLikes(r.likeCount);
    } catch {
      setLiked(!next);
      setLikes((n) => n + (next ? -1 : 1));
    }
  };

  const toggleReminder = async (v: boolean) => {
    const prev = link;
    apply({ ...link, reminderEnabled: v });
    try {
      apply(await api.links.update(link.id, { ...toInput(link), reminderEnabled: v }));
      toast(v ? "Rappel activé la veille" : "Rappel désactivé");
    } catch (e) {
      apply(prev);
      toast((e as Error).message, "error");
    }
  };

  const toggleFavorite = async () => {
    setMenuOpen(false);
    try {
      const r = await api.links.toggleFavorite(link.id);
      apply({ ...link, favorite: !!r.favorite });
      toast(r.favorite ? "Ajoutée aux favoris" : "Retirée des favoris");
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };

  const saveCopy = async () => {
    if (!session) return requireAuth();
    setSaving(true);
    try {
      const copy = await api.links.create(copyInput(link));
      upsertLink(copy);
      toast("Enregistrée dans vos idées");
      navigate(`/links/${copy.id}`);
    } catch (e) {
      toast((e as Error).message, "error");
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    setMenuOpen(false);
    if (!(await confirm({ title: "Supprimer cette idée ?", text: `« ${link.title} » sera définitivement supprimée.`, confirmLabel: "Supprimer", danger: true }))) return;
    try {
      await api.links.delete(link.id);
      removeLink(link.id);
      toast("Idée supprimée");
      navigate(-1);
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };

  const share = async () => {
    const r = await shareOrCopy(window.location.href, link.title);
    if (r === "copied") toast(link.visibility === "public" ? "Lien copié" : "Lien copié — visible par les membres du dossier");
  };

  const infos: { icon: IconName; label: string; value: string }[] = [];
  if (at) infos.push({ icon: "calendar", label: "Quand", value: [formatShortDate(at), formatTime(at)].filter(Boolean).join(" · ") });
  if (link.ageRange) infos.push({ icon: "person", label: "Âge", value: link.ageRange });
  if (link.location) infos.push({ icon: "pin", label: "Où", value: link.location });
  if (link.price) infos.push({ icon: "euro", label: "Prix", value: link.price });

  return (
    <article className="mx-auto max-w-3xl md:px-8 md:pt-8">
      <Seo
        title={ideaSeoTitle(link)}
        description={ideaSeoDescription(link, cat)}
        path={`/links/${link.id}`}
      />

      {/* Visuel */}
      <div className={`relative h-[300px] overflow-hidden md:h-[360px] md:rounded-xl ${cat.bg}`} style={morph.visualStyle}>
        {img && !imgBroken ? (
          <img src={img} alt="" referrerPolicy="no-referrer" onError={() => setBrokenSrc(img)} className="absolute inset-0 h-full w-full object-cover" />
        ) : (
          <>
            <div aria-hidden="true" className="absolute -bottom-[120px] left-1/2 h-80 w-80 -translate-x-1/2 rounded-full bg-soleil" />
            <span className="absolute bottom-6 left-1/2 -translate-x-1/2"><TipiMark size={140} /></span>
          </>
        )}
        <div className="vt-pop absolute inset-x-4 top-4 flex justify-between">
          <button
            type="button"
            onClick={() => (window.history.length > 1 ? navigate(-1) : navigate("/"))}
            aria-label="Retour"
            className="flex h-11 w-11 items-center justify-center rounded-full bg-white/92 hover:bg-white"
          >
            <Icon name="chevronLeft" size={22} />
          </button>
          <div className="flex gap-2">
            <button type="button" onClick={share} aria-label="Partager" className="flex h-11 w-11 items-center justify-center rounded-full bg-white/92 hover:bg-white">
              <Icon name="share" size={21} />
            </button>
            {session && (
              <button type="button" onClick={() => setMenuOpen(true)} aria-label="Plus d'actions" className="flex h-11 w-11 items-center justify-center rounded-full bg-white/92 hover:bg-white">
                <Icon name="dots" size={21} />
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Contenu */}
      <div className="vt-rise relative -mt-7 flex flex-col gap-4 rounded-t-xl bg-sable px-5 pt-[22px] pb-32 md:px-0 md:pb-12">
        <div className="flex flex-wrap items-center gap-2">
          <span className={`rounded-full px-3 py-1 text-[13px] font-bold ${cat.bg} ${cat.ink}`}>{cat.label}</span>
          <span className="text-[13px] text-sec">
            {/* Toujours le nom du compte qui a ajouté l'idée, même au sein d'une tribu */}
            {link.ownerDisplayName ? `par ${link.ownerDisplayName}${isOwner ? " (vous)" : ""}` : isOwner ? "par vous" : ""}
            {folder ? <> · dossier <RouterLink to={`/dossiers/${folder.id}`} {...pushLink(link.id)} className="font-semibold text-ecorce underline-offset-2 hover:underline">{folder.name}</RouterLink></> : ""}
          </span>
          {link.favorite && <Icon name="star" size={16} filled className="text-braise" />}
          {link.visibility === "public" && (
            <span className="flex items-center gap-1 text-xs text-sec"><Icon name="globe" size={14} />public</span>
          )}
        </div>

        <h1 className="self-start font-display text-[28px] leading-[1.1] font-extrabold tracking-[-0.6px] md:text-[36px]" style={morph.titleStyle}>{link.title}</h1>

        {!ourIdea && link.ownerId && session && <FollowAuthor userId={link.ownerId} name={link.ownerDisplayName} />}

        {infos.length > 0 && (
          <dl className="grid grid-cols-2 gap-2.5 md:grid-cols-4">
            {infos.map((i) => (
              <div key={i.label} className="flex items-center gap-2.5 rounded-2xl bg-white p-3">
                <Icon name={i.icon} size={20} className={cat.ic} />
                <div className="flex min-w-0 flex-col">
                  <dt className="text-xs text-sec">{i.label}</dt>
                  <dd className="truncate text-sm font-bold" title={i.value}>{i.value}</dd>
                </div>
              </div>
            ))}
          </dl>
        )}

        {at && canEdit && (
          <label className="flex items-center gap-3 rounded-2xl bg-soleil px-3.5 py-3">
            <Icon name="bell" size={20} />
            <span className="flex-1 text-sm font-semibold">Me le rappeler la veille</span>
            <Toggle label="Rappel la veille" checked={!!link.reminderEnabled} onChange={toggleReminder} />
          </label>
        )}

        {(link.rating ?? 0) > 0 && (
          <div className="flex items-center gap-1 text-braise" aria-label={`Note : ${link.rating} sur 5`}>
            {[1, 2, 3, 4, 5].map((n) => <Icon key={n} name="star" size={20} filled={(link.rating ?? 0) >= n} />)}
          </div>
        )}

        {link.description && <p className="text-[15px] leading-relaxed whitespace-pre-line text-ecorce-2">{link.description}</p>}

        {folder && (
          <RouterLink to={`/dossiers/${folder.id}`} {...pushLink(link.id)} className="flex items-center gap-3 rounded-[18px] bg-white px-3.5 py-3 transition-transform active:scale-[0.97]">
            <span className="flex h-10 w-10 items-center justify-center rounded-[13px]" style={{ background: folderColor(folder.color).bg, color: folderColor(folder.color).solid }}>
              <FolderGlyph icon={folder.icon} size={20} />
            </span>
            <span className="flex flex-1 flex-col">
              <span className="text-xs text-sec">Rangée dans</span>
              <span className="text-[15px] font-bold">{folder.name}</span>
            </span>
            <Icon name="chevronRight" size={20} className="text-muted" />
          </RouterLink>
        )}

        {!!link.ingredients?.length && (
          <section className="flex flex-col gap-2.5 rounded-large bg-white p-4">
            <h2 className="flex items-center gap-2 text-lg font-bold"><Icon name="cart" size={20} className="text-recette-ic" />Ingrédients</h2>
            <ul className="flex flex-col gap-1.5">
              {link.ingredients.map((ing, i) => (
                <li key={i} className="flex items-start gap-2.5 text-[15px] text-ecorce-2">
                  <span aria-hidden="true" className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-recette-ic" />
                  {ing}
                </li>
              ))}
            </ul>
            <RouterLink to="/courses" className="self-start text-sm font-bold text-terracotta">Préparer la liste de courses</RouterLink>
          </section>
        )}

        {!!link.tags?.length && (
          <div className="flex flex-wrap gap-1.5">
            {link.tags.map((t) => (
              <RouterLink key={t} to={`/mes-idees?vue=tags&tag=${encodeURIComponent(t)}`} className="rounded-[10px] bg-sable-2 px-2.5 py-1 text-[13px] text-sec hover:bg-line">
                #{t}
              </RouterLink>
            ))}
          </div>
        )}

        {href && <span className="text-xs text-muted">Source : {hostname(href)}</span>}

        {!canEdit && session && (
          <Button variant="secondary" icon="bookmark" loading={saving} onClick={saveCopy} className="self-start">
            Enregistrer dans mes idées
          </Button>
        )}

        <section ref={commentsRef} id="commentaires" className="mt-2 flex scroll-mt-6 flex-col gap-3">
          <h2 className="text-xl font-bold">Commentaires{comments.data?.length ? ` (${comments.data.length})` : ""}</h2>
          <Comments
            linkId={link.id}
            list={comments.data ?? []}
            loading={comments.loading}
            onChange={(c) => comments.setData(c)}
            canModerate={ourIdea}
            onRequireAuth={requireAuth}
          />
        </section>
      </div>

      {/* Barre d'actions */}
      <div className="vt-cta fixed inset-x-0 bottom-0 z-40 border-t border-line bg-sable/95 px-5 pt-3.5 pb-[max(26px,env(safe-area-inset-bottom))] backdrop-blur-md md:sticky md:mt-0 md:rounded-t-xl md:px-0 md:pb-4">
        <div className="mx-auto flex max-w-3xl gap-2.5">
          <button
            type="button"
            onClick={like}
            aria-pressed={liked}
            aria-label={`J'aime, ${likes}`}
            className="flex h-[54px] items-center gap-1.5 rounded-medium border-[1.5px] border-line-2 bg-white px-4 font-bold text-terracotta transition-colors hover:bg-sable-2"
          >
            <LikeHeart liked={liked} size={20} />
            {likes}
          </button>
          <button
            type="button"
            onClick={() => commentsRef.current?.scrollIntoView({ behavior: "smooth" })}
            aria-label={`Commentaires, ${comments.data?.length ?? 0}`}
            className="flex h-[54px] items-center gap-1.5 rounded-medium border-[1.5px] border-line-2 bg-white px-4 font-bold transition-colors hover:bg-sable-2"
          >
            <Icon name="comment" size={20} />
            {comments.data?.length ?? 0}
          </button>
          {href ? (
            <a
              href={href}
              target="_blank"
              rel="noopener noreferrer nofollow"
              className="flex h-[54px] flex-1 items-center justify-center gap-2 rounded-medium bg-terracotta font-bold text-white transition-colors hover:bg-terracotta-dark"
            >
              Voir le site
              <Icon name="external" size={20} />
            </a>
          ) : canEdit ? (
            <Button block icon="pencil" className="!h-[54px]" onClick={() => editLink(link, onChange)}>Modifier</Button>
          ) : !session ? (
            <Button block onClick={requireAuth}>Rejoindre Tribbae</Button>
          ) : null}
        </div>
      </div>

      {session && (
        <Sheet open={menuOpen} onClose={() => setMenuOpen(false)} title="Idée">
          <div className="flex flex-col gap-2">
            {canEdit && <MenuItem icon="pencil" label="Modifier" onClick={() => { setMenuOpen(false); editLink(link, onChange); }} />}
            {ourIdea && <MenuItem icon="star" label={link.favorite ? "Retirer des favoris" : "Ajouter aux favoris"} onClick={toggleFavorite} />}
            {!canEdit && <MenuItem icon="bookmark" label="Enregistrer dans mes idées" onClick={() => { setMenuOpen(false); saveCopy(); }} />}
            <MenuItem icon="share" label="Partager le lien" onClick={() => { setMenuOpen(false); share(); }} />
            {ourIdea && <MenuItem icon="trash" label="Supprimer" danger onClick={remove} />}
          </div>
        </Sheet>
      )}
    </article>
  );
}

function FollowAuthor({ userId, name }: { userId: string; name?: string }) {
  const [following, setFollowing] = useState<boolean>();
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let alive = true;
    api.follows.isFollowing(userId).then((v) => alive && setFollowing(v)).catch(() => {});
    return () => {
      alive = false;
    };
  }, [userId]);
  if (following === undefined) return null;
  const toggle = async () => {
    setBusy(true);
    try {
      if (following) await api.follows.unfollow(userId);
      else await api.follows.follow(userId);
      setFollowing(!following);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="flex items-center gap-3 rounded-2xl bg-white p-2.5 pr-3">
      <Avatar name={name} size={36} rounded="rounded-small" />
      <span className="flex-1 text-sm"><strong>{name || "Une tribu"}</strong><span className="text-sec"> partage ses idées</span></span>
      <Button size="sm" variant={following ? "ghost" : "dark"} loading={busy} onClick={toggle}>
        {following ? "Suivie" : "Suivre"}
      </Button>
    </div>
  );
}

const timeFmt = new Intl.DateTimeFormat("fr-FR", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

function Comments({ linkId, list, loading, onChange, canModerate, onRequireAuth }: {
  linkId: string; list: Comment[]; loading: boolean; onChange: (c: Comment[]) => void; canModerate: boolean; onRequireAuth: () => void;
}) {
  const session = useSession();
  const { toast, confirm } = useFeedback();
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);

  const send = async (e: FormEvent) => {
    e.preventDefault();
    if (!text.trim()) return;
    setBusy(true);
    try {
      const c = await api.comments.create(linkId, text.trim());
      onChange([...list, c]);
      setText("");
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  };

  const remove = async (c: Comment) => {
    if (!(await confirm({ title: "Supprimer ce commentaire ?", confirmLabel: "Supprimer", danger: true }))) return;
    try {
      await api.comments.delete(c.id);
      onChange(list.filter((x) => x.id !== c.id));
    } catch (err) {
      toast((err as Error).message, "error");
    }
  };

  return (
    <div className="flex flex-col gap-3">
      {loading ? (
        <Spinner className="text-terracotta" />
      ) : list.length === 0 ? (
        <p className="text-sm text-sec">Pas encore de commentaire. Une astuce, un retour d'expérience ?</p>
      ) : (
        <ul className="flex flex-col gap-2.5">
          {list.map((c, i) => {
            const [bg, ink] = AVATAR_COLORS[i % AVATAR_COLORS.length];
            const mine = c.userId === session?.userId;
            return (
              <li key={c.id} className="flex gap-2.5">
                <Avatar name={c.userDisplayName} color={bg} ink={ink} size={34} rounded="rounded-small" />
                <div className="flex min-w-0 flex-1 flex-col gap-0.5 rounded-2xl rounded-tl-md bg-white px-3.5 py-2.5">
                  <span className="flex items-baseline gap-2 text-[13px]">
                    <strong>{c.userDisplayName || "Membre"}</strong>
                    {c.createdAt && <span className="text-xs text-muted">{timeFmt.format(new Date(c.createdAt))}</span>}
                    {(mine || canModerate) && (
                      <button type="button" onClick={() => remove(c)} aria-label="Supprimer le commentaire" className="ml-auto text-muted hover:text-sortie-ink">
                        <Icon name="trash" size={15} />
                      </button>
                    )}
                  </span>
                  <p className="text-sm break-words whitespace-pre-line text-ecorce-2">{c.text}</p>
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {session ? (
        <form onSubmit={send} className="flex items-end gap-2">
          <label className="sr-only" htmlFor="comment">Votre commentaire</label>
          <textarea
            id="comment"
            rows={1}
            maxLength={1000}
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Ajouter un commentaire…"
            className="max-h-40 min-h-[48px] flex-1 resize-y rounded-medium border-[1.5px] border-line-2 bg-white px-4 py-3 text-[15px] outline-none placeholder:text-muted focus:border-terracotta"
          />
          <button
            type="submit"
            disabled={busy || !text.trim()}
            aria-label="Envoyer"
            className="flex h-12 w-12 shrink-0 items-center justify-center rounded-medium bg-ecorce text-sable disabled:opacity-50"
          >
            {busy ? <Spinner size={18} /> : <Icon name="send" size={20} />}
          </button>
        </form>
      ) : (
        <button type="button" onClick={onRequireAuth} className="self-start text-sm font-bold text-terracotta">
          Connectez-vous pour commenter
        </button>
      )}
    </div>
  );
}
