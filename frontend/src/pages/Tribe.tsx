import { useMemo, useState, type FormEvent } from "react";
import { Link as RouterLink, useNavigate } from "react-router";
import { useComposer } from "../components/Composer";
import { useFeedback } from "../components/feedback";
import Icon, { type IconName } from "../components/Icon";
import ShareFolderSheet from "../components/ShareFolderSheet";
import Sheet from "../components/Sheet";
import { Button, EmptyState, ErrorNote, Field, SectionTitle, Spinner } from "../components/ui";
import * as api from "../lib/api";
import { AVATAR_COLORS, folderColor, folderIcon } from "../lib/categories";
import { useMyData } from "../lib/data";
import { formatAge, initial, plural } from "../lib/format";
import { clearSession, useSession } from "../lib/session";
import type { Child, Folder } from "../lib/types";
import { useAsync } from "../lib/useAsync";

interface Member {
  key: string;
  name: string;
  sub?: string;
}

export default function Tribe() {
  const session = useSession()!;
  const navigate = useNavigate();
  const { links, folders, children, upsertFolder } = useMyData();
  const { newFolder } = useComposer();
  const [kidsOpen, setKidsOpen] = useState(false);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [shareFolder, setShareFolder] = useState<Folder>();
  const [followingOpen, setFollowingOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const following = useAsync(() => api.follows.following(session.userId), [session.userId]);

  const mine = links.filter((l) => l.ownerId === session.userId);
  const myFolders = folders.filter((f) => f.ownerId === session.userId);
  const likesReceived = mine.reduce((n, l) => n + (l.likeCount ?? 0), 0) + myFolders.reduce((n, f) => n + (f.likeCount ?? 0), 0);

  // La tribu : moi, les proches invités dans mes dossiers, puis les enfants
  const members = useMemo<Member[]>(() => {
    const out: Member[] = [{ key: "me", name: session.displayName || "Moi" }];
    const seen = new Set<string>([session.userId]);
    for (const f of folders) {
      for (const c of f.collaborators ?? []) {
        if (seen.has(c.userId)) continue;
        seen.add(c.userId);
        out.push({ key: c.userId, name: c.displayName || c.email || "Membre" });
      }
      if (f.ownerId !== session.userId && !seen.has(f.ownerId)) {
        seen.add(f.ownerId);
        out.push({ key: f.ownerId, name: f.ownerDisplayName || "Membre" });
      }
    }
    for (const c of children) out.push({ key: `child-${c.id}`, name: c.name, sub: formatAge(c.birthDate) });
    return out;
  }, [folders, children, session]);

  const logout = () => {
    clearSession();
    navigate("/bienvenue", { replace: true });
  };

  return (
    <div>
      <title>Ma tribu · Tribbae</title>
      <header className="relative overflow-hidden bg-braise px-5 pt-5 pb-[74px] md:px-8">
        <div aria-hidden="true" className="absolute -bottom-[170px] left-1/2 h-[360px] w-[360px] -translate-x-1/2 rounded-full bg-soleil" />
        <div className="relative mx-auto flex max-w-3xl items-center justify-between">
          <h1 className="font-display text-[26px] font-extrabold text-ecorce">Ma tribu</h1>
          <button
            type="button"
            onClick={() => setSettingsOpen(true)}
            aria-label="Paramètres"
            className="flex h-11 w-11 items-center justify-center rounded-full bg-white/60 text-ecorce hover:bg-white/80"
          >
            <Icon name="settings" size={21} />
          </button>
        </div>
        <svg aria-hidden="true" width="120" height="104" viewBox="0 0 48 42" className="absolute bottom-0 left-1/2 -translate-x-1/2">
          <path d="M20 1l6 9M28 1l-6 9" stroke="#3A211B" strokeWidth="1.8" strokeLinecap="round" />
          <path d="M24 7L44 41H4z" fill="#D45A43" stroke="#3A211B" strokeWidth="1.8" strokeLinejoin="round" />
          <path d="M24 22l11 19H13z" fill="#5E4558" stroke="#3A211B" strokeWidth="1.8" strokeLinejoin="round" />
          <path d="M12 24h12.5L18 37z" fill="#F28C38" stroke="#3A211B" strokeWidth="1.6" strokeLinejoin="round" />
          <path d="M30 18l2-1.5 2 1.5M33 26l2-1.5 2 1.5" stroke="#3A211B" strokeWidth="1.4" fill="none" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </header>

      <div className="mx-auto flex max-w-3xl flex-col gap-[18px] px-5 pt-[18px] md:px-8">
        <div className="flex flex-col gap-0.5 text-center">
          <span className="font-display text-2xl font-extrabold">La tribu {session.displayName}</span>
          <span className="text-sm text-sec">
            {plural(members.length, "membre", "membres")}
            {session.isAdmin && " · administrateur"}
          </span>
        </div>

        <div className="grid grid-cols-3 gap-2">
          <Stat value={mine.length} label={mine.length > 1 ? "idées" : "idée"} />
          <Stat value={folders.length} label={folders.length > 1 ? "dossiers" : "dossier"} />
          <Stat value={likesReceived} label="j'aime reçus" />
        </div>

        <section className="flex flex-col gap-2.5">
          <SectionTitle
            title="Les membres"
            action={<button type="button" onClick={() => setKidsOpen(true)} className="text-sm font-semibold text-terracotta hover:text-terracotta-dark">Gérer</button>}
          />
          <div className="no-scrollbar -mx-5 flex gap-3.5 overflow-x-auto px-5 md:mx-0 md:px-0">
            {members.map((m, i) => {
              const [bg, ink] = AVATAR_COLORS[i % AVATAR_COLORS.length];
              return (
                <div key={m.key} className="flex w-[70px] shrink-0 flex-col items-center gap-1.5 text-center">
                  <span className="flex h-[58px] w-[58px] items-center justify-center rounded-[20px] font-display text-[22px] font-extrabold" style={{ background: bg, color: ink }}>
                    {initial(m.name)}
                  </span>
                  <span className="line-clamp-2 text-[13px] leading-tight font-semibold">
                    {m.name}
                    {m.sub && <span className="font-normal text-sec"> · {m.sub}</span>}
                  </span>
                </div>
              );
            })}
            <button type="button" onClick={() => setKidsOpen(true)} className="flex w-[70px] shrink-0 flex-col items-center gap-1.5">
              <span className="flex h-[58px] w-[58px] items-center justify-center rounded-[20px] border-2 border-dashed border-dash text-terracotta">
                <Icon name="baby" size={24} />
              </span>
              <span className="text-[13px] font-semibold text-sec">Enfant</span>
            </button>
          </div>
        </section>

        <button
          type="button"
          onClick={() => setInviteOpen(true)}
          className="flex items-center gap-3 rounded-[20px] bg-ecorce p-3.5 text-left text-sable transition-transform hover:-translate-y-0.5"
        >
          <span className="flex h-11 w-11 items-center justify-center rounded-[14px] bg-braise text-ecorce">
            <Icon name="personPlus" size={22} />
          </span>
          <span className="flex flex-1 flex-col">
            <span className="text-[15px] font-bold">Inviter dans la tribu</span>
            <span className="text-[13px] text-bark-soft">Partagez dossiers et idées en un lien</span>
          </span>
          <Icon name="chevronRight" size={20} className="text-bark-soft" />
        </button>

        <nav aria-label="Plus" className="overflow-hidden rounded-[20px] bg-white">
          <ListRow icon="heart" label="Tribus suivies" value={following.data ? String(following.data.length) : undefined} onClick={() => setFollowingOpen(true)} />
          <ListRow icon="cart" label="Liste de courses" to="/courses" />
          <ListRow icon="help" label="Aide & FAQ" to="/aide" />
          {session.isAdmin && <ListRow icon="crown" label="Administration" to="/admin" />}
          <ListRow icon="logout" label="Se déconnecter" onClick={logout} last />
        </nav>
      </div>

      <ChildrenSheet open={kidsOpen} onClose={() => setKidsOpen(false)} list={children} />

      <Sheet open={inviteOpen} onClose={() => setInviteOpen(false)} title="Inviter dans la tribu">
        <p className="mb-4 text-[15px] text-ecorce-2">
          Choisissez le dossier à partager : vos proches pourront le consulter par lien ou y ajouter leurs idées.
        </p>
        {myFolders.length ? (
          <ul className="flex flex-col gap-2">
            {myFolders.map((f) => {
              const c = folderColor(f.color);
              return (
                <li key={f.id}>
                  <button
                    type="button"
                    onClick={() => {
                      setInviteOpen(false);
                      setShareFolder(f);
                    }}
                    className="flex w-full items-center gap-3 rounded-m bg-white p-3 text-left hover:bg-sable-2"
                  >
                    <span className="flex h-10 w-10 items-center justify-center rounded-s text-white" style={{ background: c.solid }}>
                      <Icon name={folderIcon(f.icon)} size={20} />
                    </span>
                    <span className="flex-1 font-bold">{f.name}</span>
                    <span className="text-xs text-sec">{plural(f.collaborators?.length ?? 0, "membre", "membres")}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        ) : (
          <EmptyState
            icon="tipi"
            title="Aucun dossier à partager"
            action={<Button icon="plus" onClick={() => { setInviteOpen(false); newFolder(); }}>Créer un dossier</Button>}
          />
        )}
      </Sheet>
      {shareFolder && (
        <ShareFolderSheet
          onClose={() => setShareFolder(undefined)}
          folder={shareFolder}
          onChange={(f) => {
            setShareFolder(f);
            upsertFolder(f);
          }}
        />
      )}

      <Sheet open={followingOpen} onClose={() => setFollowingOpen(false)} title="Tribus suivies">
        {following.loading ? (
          <Spinner className="text-terracotta" />
        ) : following.data?.length ? (
          <ul className="flex flex-col divide-y divide-sable-2 overflow-hidden rounded-[18px] bg-white">
            {following.data.map((u, i) => {
              const [bg, ink] = AVATAR_COLORS[i % AVATAR_COLORS.length];
              return (
                <li key={u.id} className="flex items-center gap-3 px-3.5 py-3">
                  <span className="flex h-10 w-10 items-center justify-center rounded-s font-display font-extrabold" style={{ background: bg, color: ink }}>{initial(u.displayName)}</span>
                  <span className="flex flex-1 flex-col">
                    <span className="font-bold">{u.displayName || "Tribu"}</span>
                    <span className="text-xs text-sec">{plural(u.followerCount ?? 0, "abonné", "abonnés")}</span>
                  </span>
                  <UnfollowButton userId={u.id} onDone={() => following.setData((l) => (l ?? []).filter((x) => x.id !== u.id))} />
                </li>
              );
            })}
          </ul>
        ) : (
          <EmptyState icon="heart" title="Vous ne suivez personne" text="Depuis la fiche d'une idée de la communauté, suivez la tribu qui l'a partagée." />
        )}
      </Sheet>

      <Sheet open={settingsOpen} onClose={() => setSettingsOpen(false)} title="Paramètres">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-3 rounded-m bg-white p-3.5">
            <span className="flex h-12 w-12 items-center justify-center rounded-m bg-braise font-display text-xl font-extrabold">{initial(session.displayName)}</span>
            <span className="flex flex-col">
              <span className="font-bold">{session.displayName}</span>
              <span className="text-xs text-sec">{session.isAdmin ? "Administrateur" : "Membre"}</span>
            </span>
          </div>
          <p className="text-sm text-sec">
            Pour modifier votre mot de passe ou supprimer votre compte, écrivez-nous à{" "}
            <a href="mailto:support@tribbae.com" className="font-semibold text-terracotta">support@tribbae.com</a>.
          </p>
          <Button variant="danger" icon="logout" onClick={logout}>Se déconnecter</Button>
        </div>
      </Sheet>
    </div>
  );
}

function Stat({ value, label }: { value: number; label: string }) {
  return (
    <div className="rounded-2xl bg-white px-2 py-3 text-center">
      <div className="font-display text-[22px] font-extrabold">{value}</div>
      <div className="text-xs text-sec">{label}</div>
    </div>
  );
}

function ListRow({ icon, label, value, to, onClick, last }: {
  icon: IconName; label: string; value?: string; to?: string; onClick?: () => void; last?: boolean;
}) {
  const cls = `flex w-full items-center gap-3 px-4 py-3.5 text-left text-[15px] font-semibold transition-colors hover:bg-sable/60 ${last ? "" : "border-b border-sable-2"}`;
  const content = (
    <>
      <Icon name={icon} size={20} />
      <span className="flex-1">{label}</span>
      {value && <span className="text-[13px] font-normal text-sec">{value}</span>}
      <Icon name="chevronRight" size={18} className="text-muted" />
    </>
  );
  return to ? <RouterLink to={to} className={cls}>{content}</RouterLink> : <button type="button" onClick={onClick} className={cls}>{content}</button>;
}

function UnfollowButton({ userId, onDone }: { userId: string; onDone: () => void }) {
  const [busy, setBusy] = useState(false);
  return (
    <Button
      size="sm"
      variant="ghost"
      loading={busy}
      onClick={async () => {
        setBusy(true);
        try {
          await api.follows.unfollow(userId);
          onDone();
        } finally {
          setBusy(false);
        }
      }}
    >
      Ne plus suivre
    </Button>
  );
}

// ── Enfants ───────────────────────────────────────────────────────────

const toDateInput = (v: Child["birthDate"]) => {
  const d = new Date(Number(v));
  return Number.isNaN(d.getTime()) ? "" : d.toISOString().slice(0, 10);
};

function ChildrenSheet({ open, onClose, list }: { open: boolean; onClose: () => void; list: Child[] }) {
  const { reloadChildren } = useMyData();
  const { toast, confirm } = useFeedback();
  const [editing, setEditing] = useState<Child | "new" | null>(null);
  const [name, setName] = useState("");
  const [birth, setBirth] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const start = (c: Child | "new") => {
    setEditing(c);
    setName(c === "new" ? "" : c.name);
    setBirth(c === "new" ? "" : toDateInput(c.birthDate));
    setError("");
  };

  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !birth) return setError("Le prénom et la date de naissance sont nécessaires.");
    const ms = new Date(`${birth}T12:00:00`).getTime();
    if (ms > Date.now()) return setError("La date de naissance est dans le futur.");
    setBusy(true);
    setError("");
    try {
      if (editing === "new") await api.children.create(name.trim(), ms);
      else if (editing) await api.children.update(editing.id, name.trim(), ms);
      await reloadChildren();
      setEditing(null);
      toast(editing === "new" ? "Enfant ajouté" : "Enfant mis à jour");
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const remove = async (c: Child) => {
    if (!(await confirm({ title: `Retirer ${c.name} ?`, confirmLabel: "Retirer", danger: true }))) return;
    try {
      await api.children.delete(c.id);
      await reloadChildren();
    } catch (err) {
      toast((err as Error).message, "error");
    }
  };

  return (
    <Sheet open={open} onClose={() => { setEditing(null); onClose(); }} title="Les enfants">
      <p className="mb-4 text-sm text-sec">L'âge des enfants sert à filtrer les idées adaptées dans l'Explorer.</p>
      {list.length > 0 && (
        <ul className="mb-4 flex flex-col divide-y divide-sable-2 overflow-hidden rounded-[18px] bg-white">
          {list.map((c, i) => {
            const [bg, ink] = AVATAR_COLORS[(i + 2) % AVATAR_COLORS.length];
            return (
              <li key={c.id} className="flex items-center gap-3 px-3.5 py-3">
                <span className="flex h-10 w-10 items-center justify-center rounded-s font-display font-extrabold" style={{ background: bg, color: ink }}>{initial(c.name)}</span>
                <span className="flex flex-1 flex-col">
                  <span className="font-bold">{c.name}</span>
                  <span className="text-xs text-sec">{formatAge(c.birthDate)}</span>
                </span>
                <button type="button" onClick={() => start(c)} aria-label={`Modifier ${c.name}`} className="flex h-9 w-9 items-center justify-center rounded-full text-sec hover:bg-sable-2">
                  <Icon name="pencil" size={17} />
                </button>
                <button type="button" onClick={() => remove(c)} aria-label={`Retirer ${c.name}`} className="flex h-9 w-9 items-center justify-center rounded-full text-sec hover:bg-sortie-bg hover:text-sortie-ink">
                  <Icon name="trash" size={17} />
                </button>
              </li>
            );
          })}
        </ul>
      )}
      {editing ? (
        <form onSubmit={save} className="flex flex-col gap-3 rounded-l bg-sable-2/60 p-4">
          <Field label="Prénom" value={name} onChange={(e) => setName(e.target.value)} maxLength={60} autoFocus />
          <Field label="Date de naissance" type="date" value={birth} max={new Date().toISOString().slice(0, 10)} onChange={(e) => setBirth(e.target.value)} />
          {error && <ErrorNote>{error}</ErrorNote>}
          <div className="flex gap-2">
            <Button type="button" variant="ghost" onClick={() => setEditing(null)}>Annuler</Button>
            <Button type="submit" block loading={busy}>{editing === "new" ? "Ajouter" : "Enregistrer"}</Button>
          </div>
        </form>
      ) : (
        <Button variant="secondary" icon="plus" block onClick={() => start("new")}>Ajouter un enfant</Button>
      )}
    </Sheet>
  );
}
