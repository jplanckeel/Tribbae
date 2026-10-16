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
import type { Child, Folder, Tribe as TribeData, TribeMember } from "../lib/types";
import { useAsync } from "../lib/useAsync";

interface Member {
  key: string;
  name: string;
  sub?: string;
}

export default function Tribe() {
  const session = useSession()!;
  const navigate = useNavigate();
  const { links, folders, children, upsertFolder, tribe, setTribe } = useMyData();
  const [nameOpen, setNameOpen] = useState(false);
  const [membersOpen, setMembersOpen] = useState(false);
  const { newFolder } = useComposer();
  const [kidsOpen, setKidsOpen] = useState(false);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [shareFolder, setShareFolder] = useState<Folder>();
  const [followingOpen, setFollowingOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const following = useAsync(() => api.follows.following(session.userId), [session.userId]);

  const mine = links.filter((l) => l.ownerId === session.userId);
  const myFolders = folders.filter((f) => f.ownerId === session.userId);
  const tribeName = tribe?.name?.trim() || session.displayName;
  const accounts = useMemo<TribeMember[]>(
    () => tribe?.members ?? [{ userId: session.userId, displayName: session.displayName, role: "TRIBE_ROLE_OWNER", sharing: true }],
    [tribe, session],
  );
  const me = accounts.find((m) => m.userId === session.userId);
  const likesReceived = mine.reduce((n, l) => n + (l.likeCount ?? 0), 0) + myFolders.reduce((n, f) => n + (f.likeCount ?? 0), 0);

  // La tribu : ses comptes (moi d'abord), puis les enfants
  const members = useMemo<Member[]>(() => {
    const out: Member[] = [...accounts]
      .sort((a, b) => (a.userId === session.userId ? -1 : b.userId === session.userId ? 1 : 0))
      .map((m) => ({
        key: m.userId,
        name: m.userId === session.userId ? `${m.displayName || session.displayName || "Moi"} (vous)` : m.displayName || m.email || "Membre",
        sub: m.sharing ? undefined : "en attente",
      }));
    for (const c of children) out.push({ key: `child-${c.id}`, name: c.name, sub: formatAge(c.birthDate) });
    return out;
  }, [accounts, children, session]);

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
          <span className="flex items-center justify-center gap-1.5">
            <span className="font-display text-2xl font-extrabold">La tribu {tribeName}</span>
            <button
              type="button"
              onClick={() => setNameOpen(true)}
              aria-label="Renommer la tribu"
              className="flex h-8 w-8 items-center justify-center rounded-full text-sec hover:bg-sable-2 hover:text-ecorce"
            >
              <Icon name="pencil" size={16} />
            </button>
          </span>
          <span className="text-sm text-sec">
            {plural(members.length, "membre", "membres")}
            {session.isAdmin && " · administrateur"}
          </span>
        </div>

        <div className="grid grid-cols-3 gap-2">
          <Stat value={links.length} label={links.length > 1 ? "idées" : "idée"} />
          <Stat value={folders.length} label={folders.length > 1 ? "dossiers" : "dossier"} />
          <Stat value={likesReceived} label="j'aime reçus" />
        </div>

        <section className="flex flex-col gap-2.5">
          <SectionTitle
            title="Les membres"
            action={<button type="button" onClick={() => setMembersOpen(true)} className="text-sm font-semibold text-terracotta hover:text-terracotta-dark">Gérer</button>}
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

        {me && !me.sharing && accounts.length > 1 && (
          <SharingBanner tribe={tribe!} counts={{ ideas: mine.length, folders: myFolders.length, children: children.length }} onChange={setTribe} />
        )}

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
            <span className="text-[13px] text-bark-soft">Partagez toute la tribu avec un proche, par email</span>
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
      {nameOpen && <TribeNameSheet current={tribeName} onClose={() => setNameOpen(false)} onSaved={setTribe} />}
      <MembersSheet
        open={membersOpen}
        onClose={() => setMembersOpen(false)}
        accounts={accounts}
        meId={session.userId}
        onChange={setTribe}
        onKids={() => { setMembersOpen(false); setKidsOpen(true); }}
        onInvite={() => { setMembersOpen(false); setInviteOpen(true); }}
      />

      <Sheet open={inviteOpen} onClose={() => setInviteOpen(false)} title="Inviter dans la tribu">
        <InviteForm onDone={(t) => { setTribe(t); setInviteOpen(false); }} />
        <h3 className="mt-6 mb-1 text-[13px] font-bold text-sec">Ou partager un seul dossier</h3>
        <p className="mb-3 text-sm text-sec">
          Vos proches pourront le consulter par lien ou y ajouter leurs idées, sans accéder au reste de la tribu.
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
          <button
            type="button"
            onClick={() => { setSettingsOpen(false); setNameOpen(true); }}
            className="flex items-center gap-3 rounded-m bg-white p-3.5 text-left hover:bg-sable-2"
          >
            <Icon name="tipi" size={20} className="text-terracotta" />
            <span className="flex flex-1 flex-col">
              <span className="text-xs text-sec">Nom de la tribu</span>
              <span className="font-bold">La tribu {tribeName}</span>
            </span>
            <Icon name="pencil" size={17} className="text-sec" />
          </button>
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

// ── Tribu partagée ───────────────────────────────────────────────────

function TribeNameSheet({ current, onClose, onSaved }: { current: string; onClose: () => void; onSaved: (t: TribeData) => void }) {
  const { toast } = useFeedback();
  const [name, setName] = useState(current);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return setError("Donnez un nom à votre tribu.");
    setBusy(true);
    setError("");
    try {
      onSaved(await api.tribe.rename(name.trim()));
      toast("Tribu renommée");
      onClose();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Sheet open onClose={onClose} title="Nom de la tribu">
      <form onSubmit={save} className="flex flex-col gap-3">
        <Field label="La tribu…" value={name} onChange={(e) => setName(e.target.value)} maxLength={60} placeholder="Dupont" autoFocus />
        <p className="text-sm text-sec">
          Affiché dans l'accueil (« La tribu {name.trim() || "…"}, on fait quoi ce week-end ? ») et pour tous les membres.
          Vos idées restent signées de votre nom.
        </p>
        {error && <ErrorNote>{error}</ErrorNote>}
        <Button type="submit" block loading={busy}>Enregistrer</Button>
      </form>
    </Sheet>
  );
}

function InviteForm({ onDone }: { onDone: (t: TribeData) => void }) {
  const { toast } = useFeedback();
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const invite = async (e: FormEvent) => {
    e.preventDefault();
    if (!email.trim()) return setError("Saisissez l'email de son compte Tribbae.");
    setBusy(true);
    setError("");
    try {
      onDone(await api.tribe.addMember(email.trim()));
      toast("Ajouté à la tribu");
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <form onSubmit={invite} className="flex flex-col gap-3">
      <p className="text-[15px] text-ecorce-2">
        Votre proche rejoint la tribu et gère tout avec vous : idées, dossiers, enfants et liste de courses.
      </p>
      <Field label="Email de son compte Tribbae" type="email" icon="send" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="camille@exemple.fr" autoComplete="email" />
      <p className="text-xs text-sec">
        Ses propres idées ne rejoindront la tribu qu'une fois qu'il l'aura confirmé depuis « Ma tribu ».
      </p>
      {error && <ErrorNote>{error}</ErrorNote>}
      <Button type="submit" icon="personPlus" block loading={busy}>Ajouter à la tribu</Button>
    </form>
  );
}

/** Nouveau membre : il voit la tribu, et choisit d'y verser son propre contenu. */
function SharingBanner({ tribe, counts, onChange }: {
  tribe: TribeData; counts: { ideas: number; folders: number; children: number }; onChange: (t: TribeData) => void;
}) {
  const { toast } = useFeedback();
  const [busy, setBusy] = useState(false);
  const what = [
    counts.ideas && plural(counts.ideas, "idée", "idées"),
    counts.folders && plural(counts.folders, "dossier", "dossiers"),
    counts.children && plural(counts.children, "enfant", "enfants"),
  ].filter(Boolean).join(", ");
  const share = async () => {
    setBusy(true);
    try {
      onChange(await api.tribe.setSharing(true));
      toast("Votre contenu est partagé avec la tribu");
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setBusy(false);
    }
  };
  return (
    <section className="flex flex-col gap-3 rounded-[20px] bg-soleil p-4">
      <span className="font-display text-lg font-bold">Bienvenue dans la tribu {tribe.name} !</span>
      <p className="text-sm text-ecorce-2">
        Vous voyez déjà tout ce que la tribu partage. Partagez aussi votre contenu{what ? ` (${what})` : ""} : les
        autres membres pourront le voir et le modifier. Vos idées restent signées de votre nom.
      </p>
      <Button icon="check" loading={busy} onClick={share} className="self-start">Partager avec la tribu</Button>
    </section>
  );
}

function MembersSheet({ open, onClose, accounts, meId, onChange, onKids, onInvite }: {
  open: boolean; onClose: () => void; accounts: TribeMember[]; meId: string;
  onChange: (t: TribeData) => void; onKids: () => void; onInvite: () => void;
}) {
  const navigate = useNavigate();
  const { toast, confirm } = useFeedback();
  const iAmOwner = accounts.find((m) => m.userId === meId)?.role === "TRIBE_ROLE_OWNER";
  const me = accounts.find((m) => m.userId === meId);

  const remove = async (m: TribeMember) => {
    const self = m.userId === meId;
    const ok = await confirm({
      title: self ? "Quitter la tribu ?" : `Retirer ${m.displayName || m.email} ?`,
      text: self
        ? "Vous ne verrez plus le contenu de la tribu, et le vôtre ne sera plus partagé avec elle."
        : "Cette personne ne verra plus le contenu de la tribu, et le sien ne sera plus partagé.",
      confirmLabel: self ? "Quitter" : "Retirer",
      danger: true,
    });
    if (!ok) return;
    try {
      onChange(await api.tribe.removeMember(m.userId));
      toast(self ? "Vous avez quitté la tribu" : "Membre retiré");
      if (self) {
        onClose();
        navigate("/tribu");
      }
    } catch (err) {
      toast((err as Error).message, "error");
    }
  };

  const stopSharing = async () => {
    const ok = await confirm({
      title: "Ne plus partager votre contenu ?",
      text: "Vos idées, dossiers et enfants ne seront plus visibles des autres membres. Vous continuerez à voir ceux de la tribu.",
      confirmLabel: "Ne plus partager",
      danger: true,
    });
    if (!ok) return;
    try {
      onChange(await api.tribe.setSharing(false));
    } catch (err) {
      toast((err as Error).message, "error");
    }
  };

  return (
    <Sheet open={open} onClose={onClose} title="Les membres">
      <ul className="mb-4 flex flex-col divide-y divide-sable-2 overflow-hidden rounded-[18px] bg-white">
        {accounts.map((m, i) => {
          const [bg, ink] = AVATAR_COLORS[i % AVATAR_COLORS.length];
          const self = m.userId === meId;
          const name = m.displayName || m.email || "Membre";
          return (
            <li key={m.userId} className="flex items-center gap-3 px-3.5 py-3">
              <span className="flex h-10 w-10 items-center justify-center rounded-s font-display font-extrabold" style={{ background: bg, color: ink }}>{initial(name)}</span>
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="truncate font-bold">{name}{self && " (vous)"}</span>
                <span className="truncate text-xs text-sec">
                  {[m.role === "TRIBE_ROLE_OWNER" ? "Créateur" : "Membre", !m.sharing && "partage de son contenu en attente", !self && m.email]
                    .filter(Boolean).join(" · ")}
                </span>
              </span>
              {accounts.length > 1 && (self || iAmOwner) && (
                <Button size="sm" variant="ghost" onClick={() => remove(m)}>{self ? "Quitter" : "Retirer"}</Button>
              )}
            </li>
          );
        })}
      </ul>
      <div className="flex flex-col gap-2">
        <Button variant="secondary" icon="personPlus" block onClick={onInvite}>Inviter un proche</Button>
        <Button variant="ghost" icon="baby" block onClick={onKids}>Les enfants</Button>
        {me?.sharing && accounts.length > 1 && (
          <button type="button" onClick={stopSharing} className="mt-2 self-center text-sm font-semibold text-sec hover:text-sortie-ink">
            Ne plus partager mon contenu
          </button>
        )}
      </div>
    </Sheet>
  );
}
