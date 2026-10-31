import { useMemo, useState, type FormEvent } from "react";
import { Link as RouterLink, useNavigate, useSearchParams } from "react-router";
import { useComposer } from "../components/Composer";
import { useFeedback } from "../components/feedback";
import { FolderGlyph } from "../components/FolderCover";
import Icon, { type IconName } from "../components/Icon";
import ShareFolderSheet from "../components/ShareFolderSheet";
import Sheet from "../components/Sheet";
import { Button, EmptyState, ErrorNote, Field, PageHeader, Spinner, Toggle } from "../components/ui";
import * as api from "../lib/api";
import { AVATAR_COLORS, folderColor } from "../lib/categories";
import { useMyData } from "../lib/data";
import { childColors, countdown } from "../lib/folderStyle";
import { formatAge, formatLongDate, initial, plural } from "../lib/format";
import { scopeLabels } from "../lib/oauth";
import { clearSession, useSession } from "../lib/session";
import { birthdayLabel, circleOf, ideasForChild, monthName, RING_SLOTS, ringPosition, yearInIdeas, type CircleMember } from "../lib/tribe";
import type { Child, CircleInvitation, CollaboratorRole, ConnectedApp, Folder, Tribe as TribeData, TribeInvitation, TribeMember } from "../lib/types";
import { setThemePref, useThemePref, type ThemePref } from "../lib/theme";
import { useAsync } from "../lib/useAsync";
import Seo from "../components/Seo";
import { Page } from "../components/Shell";

const CARD = "rounded-large bg-card shadow-[0_1px_0_rgba(58,33,27,0.06),0_8px_24px_rgba(58,33,27,0.06)]";
const SPRING = "transition-transform duration-[450ms] ease-[cubic-bezier(.34,1.56,.64,1)]";
const PRESS = "transition-transform duration-[120ms] ease-[cubic-bezier(.34,1.56,.64,1)] active:scale-[.96]";

/** Une place autour du feu : un compte de la tribu ou un enfant. */
interface Seat {
  key: string;
  name: string;
  colors: [string, string];
  badge?: string;
  open: () => void;
  aria: string;
}

/**
 * « Ma tribu » (maquette « Ma tribu v2 ») : la tribu autour du feu, le foyer
 * (comptes et enfants) et le cercle (proches qui ont accès à des dossiers),
 * les invitations, l'année en idées et les réglages.
 */
export default function Tribe() {
  const session = useSession()!;
  const navigate = useNavigate();
  const { toast } = useFeedback();
  const { links, folders, children, upsertFolder, tribe, setTribe, isTribePeer } = useMyData();
  const { openAi } = useComposer();
  const [tab, setTab] = useState<"foyer" | "cercle">("foyer");
  const [nameOpen, setNameOpen] = useState(false);
  const [membersOpen, setMembersOpen] = useState(false);
  const [kid, setKid] = useState<Child | "new">();
  const [inviteOpen, setInviteOpen] = useState(false);
  const [shareFolder, setShareFolder] = useState<Folder>();
  const [followingOpen, setFollowingOpen] = useState(false);
  // Paramètres et applications connectées s'ouvrent aussi depuis le menu du
  // compte de l'en-tête bureau (/tribu?ouvrir=parametres) : l'adresse les porte
  const [params, setParams] = useSearchParams();
  const panel = params.get("ouvrir");
  const settingsOpen = panel === "parametres";
  const appsOpen = panel === "applications";
  const openPanel = (name: "parametres" | "applications" | null) =>
    setParams((p) => {
      if (name) p.set("ouvrir", name);
      else p.delete("ouvrir");
      return p;
    }, { replace: true });
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [now] = useState(() => Date.now());
  const following = useAsync(() => api.follows.following(session.userId), [session.userId]);
  // Invitations dans le cercle envoyées par moi, en attente
  const circleInvites = useAsync(() => api.circle.invitations(), [session.userId]);
  const pendingCircle = circleInvites.data ?? [];

  const mine = links.filter((l) => l.ownerId === session.userId);
  const myFolders = folders.filter((f) => f.ownerId === session.userId);
  const tribeName = tribe?.name?.trim() || session.displayName;
  const accounts = useMemo<TribeMember[]>(
    () =>
      [...(tribe?.members ?? [{ userId: session.userId, displayName: session.displayName, role: "TRIBE_ROLE_OWNER", sharing: true }])]
        .sort((a, b) => (a.userId === session.userId ? -1 : b.userId === session.userId ? 1 : 0)),
    [tribe, session],
  );
  const me = accounts.find((m) => m.userId === session.userId);
  const invitations = tribe?.invitations ?? [];
  const circle = useMemo(
    () => circleOf(folders, (id) => id === session.userId || isTribePeer(id) || accounts.some((m) => m.userId === id)),
    [folders, isTribePeer, accounts, session.userId],
  );
  const likesReceived = mine.reduce((n, l) => n + (l.likeCount ?? 0), 0) + myFolders.reduce((n, f) => n + (f.likeCount ?? 0), 0);
  const year = new Date(now).getFullYear();
  const yearStats = useMemo(() => yearInIdeas(links, year), [links, year]);
  const accountName = (m: TribeMember) =>
    m.userId === session.userId ? m.displayName || session.displayName || "Moi" : m.displayName || m.email || "Membre";

  // Autour du feu : les comptes (moi d'abord), puis les enfants
  const seats: Seat[] = [
    ...accounts.map((m, i): Seat => ({
      key: m.userId,
      name: accountName(m),
      colors: AVATAR_COLORS[i % AVATAR_COLORS.length],
      open: () => setMembersOpen(true),
      aria: `${accountName(m)}, gérer les membres`,
    })),
    ...children.map((c): Seat => {
      const cd = countdown(Number(c.birthDate), "anniv", now);
      return {
        key: `child-${c.id}`,
        name: c.name,
        colors: childColors(children, c.id),
        badge: cd && cd.days <= 30 ? (cd.days === 0 ? "Anniv." : `J-${cd.days}`) : undefined,
        open: () => setKid(c),
        aria: `Voir le profil de ${c.name}`,
      };
    }),
  ];
  const shown = seats.length > RING_SLOTS ? seats.slice(0, RING_SLOTS - 1) : seats;
  const extra = seats.length - shown.length;

  const logout = () => {
    clearSession();
    navigate("/bienvenue", { replace: true });
  };
  const exportData = async () => {
    setExporting(true);
    try {
      await api.account.exportData();
      toast("Vos données sont téléchargées");
    } catch (err) {
      toast((err as Error).message, "error");
    } finally {
      setExporting(false);
    }
  };

  const foyerCount = accounts.length + children.length;

  return (
    <div>
      <Seo title="Ma tribu · Tribbae" noindex />

      <Page>
        <PageHeader
          title="Ma tribu"
          subtitle={
            <>
              {foyerCount} au foyer · {circle.length} dans le cercle
              {invitations.length > 0 && ` · ${plural(invitations.length, "invitation en attente", "invitations en attente")}`}
              {session.isAdmin && " · administrateur"}
            </>
          }
          action={
            <button
              type="button"
              onClick={() => openPanel("parametres")}
              aria-label="Paramètres"
              // Sur grand écran, les paramètres sont dans le menu du compte de l'en-tête
              className={`flex h-11 w-11 items-center justify-center rounded-full bg-card text-ecorce shadow-soft hover:bg-sable-2 md:hidden ${PRESS}`}
            >
              <Icon name="settings" size={21} />
            </button>
          }
        />

        {/* La tribu installée autour du feu */}
        <section aria-label={`La tribu ${tribeName}`} className="relative -mx-5 -mt-2 h-[246px] overflow-hidden md:mx-0">
          <div aria-hidden="true" className="absolute top-[42px] left-1/2 -ml-[170px] h-[340px] w-[340px] animate-sun-up rounded-full bg-soleil" />
          <div className="absolute top-0 left-1/2 z-[3]">
            {[...shown, ...(extra > 0 ? [null] : [])].map((s, i, all) => {
              const { dx, dy } = ringPosition(i, all.length);
              const style = { left: dx - 32, top: 151 + dy - 40, animationDelay: `${200 + i * 90}ms` };
              const cls = `absolute flex w-16 animate-rise flex-col items-center gap-1 ${PRESS}`;
              const tile = "relative flex h-14 w-14 items-center justify-center rounded-[20px] border-[3px] border-sable font-display text-[22px] font-extrabold shadow-[0_6px_14px_rgba(58,33,27,0.14)]";
              if (!s) {
                return (
                  <button key="more" type="button" onClick={() => setTab("foyer")} aria-label={`Et ${extra} de plus`} className={cls} style={style}>
                    <span className={`${tile} bg-sable-2 text-sec`}>+{extra}</span>
                    <span className="text-[13px] font-bold">la suite</span>
                  </button>
                );
              }
              return (
                <button key={s.key} type="button" onClick={s.open} aria-label={s.aria} className={cls} style={style}>
                  <span className={tile} style={{ background: s.colors[0], color: s.colors[1] }}>
                    {initial(s.name)}
                    {s.badge && (
                      <span className="absolute -top-2 -right-2 rounded-lg bg-cadeau-ink px-1.5 py-0.5 font-sans text-[10px] font-extrabold whitespace-nowrap text-white">
                        {s.badge}
                      </span>
                    )}
                  </span>
                  <span className="max-w-full truncate text-[13px] font-bold">{s.name}</span>
                </button>
              );
            })}
          </div>

          <svg aria-hidden="true" viewBox="0 0 100 100" className="absolute top-[86px] left-1/2 z-[2] -ml-[52px] h-[104px] w-[104px] animate-fade-up" style={{ animationDelay: "150ms" }}>
            <path d="M50 24L81 80H19Z" fill="#D45A43" stroke="var(--color-ecorce)" strokeWidth="4" strokeLinejoin="round" />
            <path d="M43 13L54 30M57 13L46 30" stroke="var(--color-ecorce)" strokeWidth="4" strokeLinecap="round" />
            <path d="M50 50L64 80H36Z" fill="#5E4558" stroke="var(--color-ecorce)" strokeWidth="4" strokeLinejoin="round" />
            <path d="M34 52H50L42 68Z" fill="#F28C38" stroke="var(--color-ecorce)" strokeWidth="3.5" strokeLinejoin="round" />
            <path d="M49 43l2.7-3 2.7 3 2.6-3M58 62l2.7-3 2.7 3 2.6-3" fill="none" stroke="var(--color-ecorce)" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
            <path d="M6 80H94" stroke="var(--color-ecorce)" strokeWidth="4" strokeLinecap="round" />
          </svg>
          <svg aria-hidden="true" width="26" height="30" viewBox="0 0 26 30" className="absolute top-[152px] left-[calc(50%+57px)] z-[2]">
            <path className="origin-bottom animate-flame" d="M13 2c4 6 9 9 9 16a9 9 0 0 1-18 0c0-4 2-6 4-8 0 3 1 5 3 5 0-5-1-9 2-13z" fill="#F28C38" stroke="var(--color-ecorce)" strokeWidth="2" strokeLinejoin="round" />
            <path d="M3 28h20" stroke="var(--color-ecorce)" strokeWidth="2.4" strokeLinecap="round" />
          </svg>

          <div className="absolute inset-x-0 bottom-3.5 flex animate-fade-up justify-center px-5" style={{ animationDelay: "350ms" }}>
            <span className="flex items-center gap-1">
              <span className="font-display text-2xl font-extrabold tracking-[-0.4px]">La tribu {tribeName}</span>
              <button
                type="button"
                onClick={() => setNameOpen(true)}
                aria-label="Renommer la tribu"
                className="flex h-8 w-8 items-center justify-center rounded-full text-muted hover:bg-sable-2 hover:text-ecorce"
              >
                <Icon name="pencil" size={16} />
              </button>
            </span>
          </div>
        </section>
        <div role="tablist" aria-label="Ma tribu" className="relative grid h-[50px] grid-cols-2 rounded-medium bg-sable-2 p-1">
          <span
            aria-hidden="true"
            className={`absolute inset-y-1 left-1 w-[calc(50%-4px)] rounded-[14px] bg-card shadow-[0_2px_6px_rgba(58,33,27,0.12)] ${SPRING}`}
            style={{ transform: `translateX(${tab === "foyer" ? 0 : 100}%)` }}
          />
          {([["foyer", "Le foyer", foyerCount], ["cercle", "Le cercle", circle.length]] as const).map(([k, label, n]) => (
            <button
              key={k}
              type="button"
              role="tab"
              aria-selected={tab === k}
              onClick={() => setTab(k)}
              className={`relative flex items-center justify-center gap-1.5 text-sm font-bold ${tab === k ? "text-ecorce" : "text-sec"}`}
            >
              {label} <span className="font-semibold text-muted">{n}</span>
            </button>
          ))}
        </div>

        {tab === "foyer" ? (
          <div role="tabpanel" aria-label="Le foyer" className="flex flex-col gap-2.5">
            {me && !me.sharing && accounts.length > 1 && (
              <SharingBanner tribe={tribe!} counts={{ ideas: mine.length, folders: myFolders.length, children: children.length }} onChange={setTribe} />
            )}
            {accounts.map((m, i) => {
              const [bg, ink] = AVATAR_COLORS[i % AVATAR_COLORS.length];
              const self = m.userId === session.userId;
              const count = links.filter((l) => l.ownerId === m.userId).length;
              return (
                <button
                  key={m.userId}
                  type="button"
                  onClick={() => setMembersOpen(true)}
                  className={`${CARD} flex animate-fade-up items-center gap-3 px-3.5 py-3 text-left ${PRESS}`}
                  style={{ animationDelay: `${i * 60}ms` }}
                >
                  <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-[17px] font-display text-xl font-extrabold" style={{ background: bg, color: ink }}>
                    {initial(accountName(m))}
                  </span>
                  <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <span className="truncate text-[15px] font-bold">{accountName(m)}{self && " (vous)"}</span>
                    <span className="truncate text-[13px] text-sec">
                      {m.sharing ? plural(count, "idée ajoutée", "idées ajoutées") : "Partage de son contenu en attente"}
                    </span>
                  </span>
                  <span className="rounded-[10px] bg-sable-2 px-2.5 py-1 text-xs font-bold text-sec">
                    {m.role === "TRIBE_ROLE_OWNER" ? "Créateur" : "Parent"}
                  </span>
                </button>
              );
            })}
            {invitations.map((inv) => (
              <PendingInvitation key={inv.id} invitation={inv} now={now} onChange={setTribe} />
            ))}

            <h2 className="mt-2.5 font-display text-xl font-bold">Les enfants</h2>
            {children.length > 0 ? (
              <div className="grid grid-cols-2 gap-2.5 md:grid-cols-3">
                {children.map((c, i) => (
                  <KidCard key={c.id} child={c} colors={childColors(children, c.id)} ideas={ideasForChild(links, folders, c)} now={now} delay={120 + i * 60} onOpen={() => setKid(c)} />
                ))}
              </div>
            ) : (
              <p className="text-sm text-sec">L'âge des enfants sert à filtrer les idées adaptées dans l'Explorer et à guider l'IA.</p>
            )}
            <button
              type="button"
              onClick={() => setKid("new")}
              className={`flex h-[52px] items-center justify-center gap-2 rounded-medium border-2 border-dashed border-dash text-sm font-bold text-sec hover:bg-sable-2/60 ${PRESS}`}
            >
              <Icon name="plus" size={18} />
              Ajouter un enfant
            </button>
          </div>
        ) : (
          <div role="tabpanel" aria-label="Le cercle" className="flex flex-col gap-2.5">
            <p className="text-sm leading-[1.45] text-sec">
              Grands-parents, parrains, cousins : ils voient seulement les dossiers que vous leur ouvrez.
            </p>
            {circle.map((c, i) => (
              <CircleCard
                key={c.userId}
                member={c}
                index={i}
                canManage={(f) => f.ownerId === session.userId}
                onFolder={setShareFolder}
              />
            ))}
            {pendingCircle.map((inv) => (
              <PendingCircleInvitation
                key={inv.id}
                invitation={inv}
                now={now}
                onChange={(next) => circleInvites.setData((list) => (list ?? []).flatMap((x) => (x.id !== inv.id ? [x] : next ? [next] : [])))}
              />
            ))}
            {circle.length === 0 && pendingCircle.length === 0 && (
              <div className="flex items-center gap-3 rounded-large border-2 border-dashed border-dash px-3.5 py-3">
                <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-sable-2 text-muted">
                  <Icon name="folder" size={20} />
                </span>
                <span className="flex flex-col gap-0.5">
                  <span className="text-[15px] font-bold">Personne dans le cercle</span>
                  <span className="text-[13px] text-sec">Ouvrez un dossier à un proche : il ne verra que celui-là.</span>
                </span>
              </div>
            )}
          </div>
        )}

        <button
          type="button"
          onClick={() => setInviteOpen(true)}
          className={`flex items-center gap-3 rounded-large bg-ecorce p-3.5 text-left text-sable ${PRESS}`}
        >
          <span className="flex h-[46px] w-[46px] shrink-0 items-center justify-center rounded-[15px] bg-braise text-ecorce">
            <Icon name="personPlus" size={22} />
          </span>
          <span className="flex flex-1 flex-col gap-px">
            <span className="text-[15px] font-bold">Inviter autour du feu</span>
            <span className="text-[13px] text-bark-soft">Un parent au foyer, ou un proche dans le cercle</span>
          </span>
          <Icon name="chevronRight" size={20} />
        </button>

        <section className="flex flex-col gap-2.5" aria-labelledby="our-year">
          <div className="flex items-baseline justify-between">
            <h2 id="our-year" className="font-display text-xl font-bold">Notre année en idées</h2>
            <span className="text-[13px] text-sec">{year}</span>
          </div>
          <div className="relative overflow-hidden rounded-large bg-sky px-4 pt-4 pb-[18px]">
            <div aria-hidden="true" className="absolute inset-x-0 bottom-0 h-[26px] border-t-2 border-ecorce bg-ground" />
            <svg aria-hidden="true" width="34" height="30" viewBox="0 0 48 42" className="absolute right-[18px] bottom-6">
              <path d="M24 7L44 41H4z" fill="#D45A43" stroke="var(--color-ecorce)" strokeWidth="2.4" strokeLinejoin="round" />
              <path d="M24 22l11 19H13z" fill="#5E4558" stroke="var(--color-ecorce)" strokeWidth="2.4" strokeLinejoin="round" />
            </svg>
            <div className="relative grid grid-cols-3 gap-2">
              <YearTile n={yearStats.outings} label={yearStats.outings > 1 ? "sorties gardées" : "sortie gardée"} color="text-sortie-ink" />
              <YearTile n={yearStats.recipes} label={yearStats.recipes > 1 ? "recettes gardées" : "recette gardée"} color="text-recette-ink" />
              <YearTile n={yearStats.gifts} label={yearStats.gifts > 1 ? "idées cadeaux" : "idée cadeau"} color="text-cadeau-ink" />
            </div>
            <p className="relative mt-3 mr-14 mb-[18px] text-sm font-semibold">
              {yearStats.bestMonth
                ? `Votre meilleur mois : ${monthName(yearStats.bestMonth.month)}, ${plural(yearStats.bestMonth.count, "idée gardée", "idées gardées")}.`
                : "Gardez vos premières idées : elles rempliront votre année."}
            </p>
          </div>
          <p className="text-[13px] text-sec">
            En tout : {plural(links.length, "idée", "idées")} · {plural(folders.length, "dossier", "dossiers")} · {likesReceived} j'aime reçus
          </p>
        </section>

        <section className="flex flex-col gap-2.5" aria-labelledby="settings-title">
          <h2 id="settings-title" className="font-display text-xl font-bold">Réglages</h2>
          <nav aria-label="Réglages" className="overflow-hidden rounded-large bg-card">
            <AppearanceRow />
            <ListRow icon="heart" label="Tribus suivies" value={following.data ? String(following.data.length) : undefined} onClick={() => setFollowingOpen(true)} />
            <ListRow icon="cart" label="Liste de courses" to="/courses" />
            <ListRow icon="lock" label="Confidentialité & mes données" to="/confidentialite" />
            <ListRow icon="help" label="Aide & FAQ" to="/aide" />
            <ListRow icon="tipi" label="À propos de Tribbae" to="/a-propos" last={!session.isAdmin} />
            {session.isAdmin && <ListRow icon="crown" label="Administration" to="/admin" last />}
          </nav>
          <p className="py-1 text-center text-xs text-muted">
            Connecté en tant que {session.displayName} ·{" "}
            <button type="button" onClick={logout} className="font-semibold text-sec underline-offset-2 hover:text-terracotta hover:underline">
              Se déconnecter
            </button>
          </p>
        </section>
      </Page>

      {deleteOpen && <DeleteAccountSheet onClose={() => setDeleteOpen(false)} />}
      {appsOpen && <ConnectedAppsSheet onClose={() => openPanel(null)} />}
      {kid && (
        <KidSheet
          kid={kid}
          now={now}
          onClose={() => setKid(undefined)}
          onIdeas={(c) => {
            setKid(undefined);
            openAi({ childId: c.id });
          }}
        />
      )}
      {nameOpen && <TribeNameSheet current={tribeName} onClose={() => setNameOpen(false)} onSaved={setTribe} />}
      <MembersSheet
        open={membersOpen}
        onClose={() => setMembersOpen(false)}
        accounts={accounts}
        invitations={invitations}
        meId={session.userId}
        onChange={setTribe}
        onInvite={() => { setMembersOpen(false); setInviteOpen(true); }}
      />
      {inviteOpen && (
        <InviteSheet
          folders={myFolders}
          onClose={() => setInviteOpen(false)}
          onTribe={(t) => { setTribe(t); setInviteOpen(false); setTab("foyer"); }}
          onCircle={(inv) => {
            circleInvites.setData((list) => [...(list ?? []).filter((x) => x.id !== inv.id), inv]);
            setInviteOpen(false);
            setTab("cercle");
          }}
          onShareLink={(f) => { setInviteOpen(false); setShareFolder(f); }}
        />
      )}
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
          <ul className="flex flex-col divide-y divide-sable-2 overflow-hidden rounded-[18px] bg-card">
            {following.data.map((u, i) => {
              const [bg, ink] = AVATAR_COLORS[i % AVATAR_COLORS.length];
              return (
                <li key={u.id} className="flex items-center gap-3 px-3.5 py-3">
                  <span className="flex h-10 w-10 items-center justify-center rounded-small font-display font-extrabold" style={{ background: bg, color: ink }}>{initial(u.displayName)}</span>
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

      <Sheet open={settingsOpen} onClose={() => openPanel(null)} title="Paramètres">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-3 rounded-medium bg-card p-3.5">
            <span className="flex h-12 w-12 items-center justify-center rounded-medium bg-braise font-display text-xl font-extrabold">{initial(session.displayName)}</span>
            <span className="flex flex-col">
              <span className="font-bold">{session.displayName}</span>
              <span className="text-xs text-sec">{session.isAdmin ? "Administrateur" : "Membre"}</span>
            </span>
          </div>
          <button
            type="button"
            onClick={() => { openPanel(null); setNameOpen(true); }}
            className="flex items-center gap-3 rounded-medium bg-card p-3.5 text-left hover:bg-sable-2"
          >
            <Icon name="tipi" size={20} className="text-terracotta" />
            <span className="flex flex-1 flex-col">
              <span className="text-xs text-sec">Nom de la tribu</span>
              <span className="font-bold">La tribu {tribeName}</span>
            </span>
            <Icon name="pencil" size={17} className="text-sec" />
          </button>
          <button
            type="button"
            onClick={() => openPanel("applications")}
            className="flex items-center gap-3 rounded-medium bg-card p-3.5 text-left hover:bg-sable-2"
          >
            <Icon name="sparkles" size={20} className="text-prune" />
            <span className="flex flex-1 flex-col">
              <span className="font-bold">Applications connectées</span>
              <span className="text-xs text-sec">Voir et retirer l'accès donné à Claude</span>
            </span>
            <Icon name="dots" size={17} className="text-sec" />
          </button>
          <section aria-labelledby="my-data" className="flex flex-col gap-2 rounded-medium bg-card p-3.5">
            <h3 id="my-data" className="font-bold">Mes données</h3>
            <p className="text-sm text-sec">
              Récupérez tout ce que Tribbae conserve sur vous, ou supprimez votre compte. Détails dans la{" "}
              <RouterLink to="/confidentialite" className="font-semibold text-terracotta">politique de confidentialité</RouterLink>.
            </p>
            <Button variant="ghost" size="sm" icon="send" loading={exporting} onClick={() => void exportData()}>
              Télécharger mes données
            </Button>
            <Button variant="danger" size="sm" icon="trash" onClick={() => { openPanel(null); setDeleteOpen(true); }}>
              Supprimer mon compte
            </Button>
          </section>
          <p className="text-sm text-sec">
            Pour modifier votre mot de passe, écrivez-nous à{" "}
            <a href="mailto:contact@tribbae.fr" className="font-semibold text-terracotta">contact@tribbae.fr</a>.
          </p>
          <Button variant="danger" icon="logout" onClick={logout}>Se déconnecter</Button>
        </div>
      </Sheet>
    </div>
  );
}

function YearTile({ n, label, color }: { n: number; label: string; color: string }) {
  return (
    <div className="flex flex-col gap-0.5 rounded-2xl bg-card px-2 py-2.5 text-center">
      <span className={`font-display text-2xl font-extrabold ${color}`}>{n}</span>
      <span className="text-xs leading-[15px] text-sec">{label}</span>
    </div>
  );
}

/** « Apparence » : jour, soir (mode sombre « veillée ») ou comme l'appareil. Retenue sur cet appareil. */
function AppearanceRow() {
  const pref = useThemePref();
  const options: [ThemePref, string][] = [["jour", "Jour"], ["soir", "Soir"], ["auto", "Auto"]];
  return (
    <div className="flex items-center gap-3 border-b border-sable-2 px-4 py-3">
      <Icon name="sun" size={20} className="text-sec" />
      <span id="appearance-label" className="flex-1 text-[15px] font-semibold">Apparence</span>
      <div role="radiogroup" aria-labelledby="appearance-label" className="flex gap-0.5 rounded-small bg-sable-2 p-[3px]">
        {options.map(([k, label]) => (
          <button
            key={k}
            type="button"
            role="radio"
            aria-checked={pref === k}
            onClick={() => setThemePref(k)}
            className={`h-[30px] rounded-[9px] px-2.5 text-xs font-bold transition-colors ${pref === k ? "bg-card text-ecorce" : "text-sec"}`}
          >
            {label}
          </button>
        ))}
      </div>
    </div>
  );
}

function ListRow({ icon, label, value, to, onClick, last }: {
  icon: IconName; label: string; value?: string; to?: string; onClick?: () => void; last?: boolean;
}) {
  const cls = `flex w-full items-center gap-3 px-4 py-3.5 text-left text-[15px] font-semibold transition-colors hover:bg-sable/60 ${last ? "" : "border-b border-sable-2"}`;
  const content = (
    <>
      <Icon name={icon} size={20} className="text-sec" />
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

/** « il y a 2 jours » */
function ago(ms: number, now: number): string {
  const days = Math.floor((now - ms) / 86_400_000);
  if (days <= 0) return "aujourd'hui";
  if (days === 1) return "hier";
  return `il y a ${days} jours`;
}

/**
 * Carte d'une invitation en attente (foyer ou cercle) : « Relancer » (le
 * serveur renvoie l'email au plus toutes les 10 min) et « Annuler » (le lien
 * reçu ne marche plus), après confirmation.
 */
function PendingCard({ email, detail, onRemind, onCancel }: {
  email: string; detail: string; onRemind: () => Promise<void>; onCancel: () => Promise<void>;
}) {
  const { toast, confirm } = useFeedback();
  const [state, setState] = useState<"idle" | "busy" | "done">("idle");
  const remind = async () => {
    setState("busy");
    try {
      await onRemind();
      setState("done");
      toast("Invitation renvoyée");
    } catch (err) {
      setState("idle");
      toast((err as Error).message, "error");
    }
  };
  const cancel = async () => {
    const ok = await confirm({
      title: "Annuler l'invitation ?",
      text: `Le lien envoyé à ${email} ne marchera plus. Vous pourrez l'inviter à nouveau.`,
      confirmLabel: "Annuler l'invitation",
      danger: true,
    });
    if (!ok) return;
    setState("busy");
    try {
      await onCancel();
      toast("Invitation annulée");
    } catch (err) {
      setState("idle");
      toast((err as Error).message, "error");
    }
  };
  return (
    <div className="flex animate-fade-up flex-wrap items-center gap-3 rounded-large border-2 border-dashed border-dash px-3.5 py-3">
      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-sable-2 text-muted">
        <Icon name="send" size={19} />
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="truncate text-[15px] font-bold">{email}</span>
        <span className="text-[13px] text-sec">{detail}</span>
      </span>
      <span className="flex shrink-0 gap-1.5">
        <button
          type="button"
          onClick={cancel}
          disabled={state === "busy"}
          className="h-9 rounded-small px-3 text-[13px] font-bold text-sec transition-colors hover:bg-sable-2 disabled:opacity-60"
        >
          Annuler
        </button>
        <button
          type="button"
          onClick={remind}
          disabled={state !== "idle"}
          className={`h-9 rounded-small px-3 text-[13px] font-bold transition-colors ${state === "done" ? "bg-recette-bg text-recette-ink" : "bg-ecorce text-sable"}`}
        >
          {state === "done" ? "Relancé ✓" : state === "busy" ? "…" : "Relancer"}
        </button>
      </span>
    </div>
  );
}

function sentLabel(createdAt: string | undefined, now: number): string {
  const sent = Number(createdAt);
  return sent ? `Invitation envoyée ${ago(sent, now)}` : "Invitation envoyée";
}

/** Invitation au foyer en attente. */
function PendingInvitation({ invitation, now, onChange }: { invitation: TribeInvitation; now: number; onChange: (t: TribeData) => void }) {
  return (
    <PendingCard
      email={invitation.email}
      detail={sentLabel(invitation.createdAt, now)}
      onRemind={async () => onChange(await api.tribe.invite(invitation.email))}
      onCancel={async () => onChange(await api.tribe.revokeInvitation(invitation.id))}
    />
  );
}

/** Invitation au cercle en attente ; onChange(undefined) une fois annulée. */
function PendingCircleInvitation({ invitation, now, onChange }: {
  invitation: CircleInvitation; now: number; onChange: (inv: CircleInvitation | undefined) => void;
}) {
  const folders = invitation.folderNames ?? [];
  const role = invitation.role === "COLLABORATOR_ROLE_EDITOR" ? "éditeur" : "lecteur";
  return (
    <PendingCard
      email={invitation.email}
      detail={`${folders.join(", ")} · ${role} · ${sentLabel(invitation.createdAt, now).toLowerCase()}`}
      onRemind={async () =>
        onChange(await api.circle.invite(invitation.email, invitation.folderIds ?? [], invitation.role ?? "COLLABORATOR_ROLE_VIEWER"))
      }
      onCancel={async () => {
        await api.circle.revokeInvitation(invitation.id);
        onChange(undefined);
      }}
    />
  );
}

function KidCard({ child, colors, ideas, now, delay, onOpen }: {
  child: Child; colors: [string, string]; ideas: number; now: number; delay: number; onOpen: () => void;
}) {
  const [bg, ink] = colors;
  const cd = countdown(Number(child.birthDate), "anniv", now);
  const soon = !!cd && cd.days <= 30;
  return (
    <button type="button" onClick={onOpen} className={`${CARD} animate-fade-up overflow-hidden text-left ${PRESS}`} style={{ animationDelay: `${delay}ms` }}>
      <span className="relative block h-[74px]" style={{ background: bg }}>
        <span
          className="absolute -bottom-5 left-3 flex h-12 w-12 items-center justify-center rounded-[17px] border-[3px] border-card font-display text-xl font-extrabold text-white"
          style={{ background: ink }}
        >
          {initial(child.name)}
        </span>
        {cd && (
          <span
            className="absolute top-2.5 right-2.5 rounded-[9px] px-2 py-[3px] text-[11px] font-extrabold"
            style={soon ? { background: "var(--color-cadeau-ink)", color: "#fff" } : { background: "var(--color-card)", color: ink }}
          >
            {cd.label}
          </span>
        )}
      </span>
      <span className="flex flex-col gap-1 px-3 pt-[26px] pb-3">
        <span className="font-display text-[17px] font-extrabold">{child.name}</span>
        <span className="text-[13px] text-sec">{formatAge(child.birthDate)} · {plural(ideas, "idée", "idées")}</span>
      </span>
    </button>
  );
}

function CircleCard({ member, index, canManage, onFolder }: {
  member: CircleMember; index: number; canManage: (f: Folder) => boolean; onFolder: (f: Folder) => void;
}) {
  const [bg, ink] = AVATAR_COLORS[(index + 3) % AVATAR_COLORS.length];
  return (
    <div className={`${CARD} flex animate-fade-up flex-col gap-2.5 px-3.5 py-3`} style={{ animationDelay: `${index * 70}ms` }}>
      <div className="flex items-center gap-3">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full font-display text-lg font-extrabold" style={{ background: bg, color: ink }}>
          {initial(member.name)}
        </span>
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="truncate text-[15px] font-bold">{member.name}</span>
          <span className="truncate text-[13px] text-sec">{plural(member.folders.length, "dossier ouvert", "dossiers ouverts")}</span>
        </span>
        <span className={`rounded-[10px] px-2.5 py-1 text-xs font-bold ${member.editor ? "bg-recette-bg text-recette-ink" : "bg-sable-2 text-sec"}`}>
          {member.editor ? "Éditeur" : "Lecteur"}
        </span>
      </div>
      <div className="flex flex-wrap gap-1.5">
        {member.folders.map((f) => {
          const c = folderColor(f.color);
          const cls = "inline-flex h-7 items-center rounded-[10px] px-2.5 text-xs font-bold";
          return canManage(f) ? (
            <button key={f.id} type="button" onClick={() => onFolder(f)} aria-label={`Gérer l'accès à ${f.name}`} className={`${cls} hover:brightness-95`} style={{ background: c.bg, color: c.solid }}>
              {f.name}
            </button>
          ) : (
            <span key={f.id} className={cls} style={{ background: c.bg, color: c.solid }}>{f.name}</span>
          );
        })}
      </div>
    </div>
  );
}

// ── Enfants ───────────────────────────────────────────────────────────

const toDateInput = (v: Child["birthDate"]) => {
  const d = new Date(Number(v));
  return Number.isNaN(d.getTime()) ? "" : d.toISOString().slice(0, 10);
};

/** Fiche d'un enfant (anniversaire, ses dossiers, idées de l'IA) ou formulaire d'ajout. À monter quand elle est ouverte. */
function KidSheet({ kid, now, onClose, onIdeas }: { kid: Child | "new"; now: number; onClose: () => void; onIdeas: (c: Child) => void }) {
  const { children, folders, reloadChildren } = useMyData();
  const { toast, confirm } = useFeedback();
  const [editing, setEditing] = useState(kid === "new");
  const [name, setName] = useState(kid === "new" ? "" : kid.name);
  const [birth, setBirth] = useState(kid === "new" ? "" : toDateInput(kid.birthDate));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  // Après une modification, la fiche affiche la version à jour
  const child = kid === "new" ? undefined : (children.find((c) => c.id === kid.id) ?? kid);

  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !birth) return setError("Le prénom et la date de naissance sont nécessaires.");
    const ms = new Date(`${birth}T12:00:00`).getTime();
    if (ms > Date.now()) return setError("La date de naissance est dans le futur.");
    setBusy(true);
    setError("");
    try {
      if (child) await api.children.update(child.id, name.trim(), ms);
      else await api.children.create(name.trim(), ms);
      await reloadChildren();
      toast(child ? "Enfant mis à jour" : "Enfant ajouté");
      if (child) setEditing(false);
      else onClose();
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
      onClose();
    } catch (err) {
      toast((err as Error).message, "error");
    }
  };

  if (!child || editing) {
    return (
      <Sheet open onClose={onClose} title={child ? `Modifier ${child.name}` : "Ajouter un enfant"}>
        <form onSubmit={save} className="flex flex-col gap-3">
          <Field label="Prénom" value={name} onChange={(e) => setName(e.target.value)} maxLength={60} autoFocus />
          <Field label="Date de naissance" type="date" value={birth} max={new Date(now).toISOString().slice(0, 10)} onChange={(e) => setBirth(e.target.value)} />
          <p className="text-xs text-muted">L'âge sert à filtrer l'Explorer et à guider les idées de l'IA. Rien n'est public.</p>
          {error && <ErrorNote>{error}</ErrorNote>}
          <div className="flex gap-2">
            <Button type="button" variant="ghost" onClick={() => (child ? setEditing(false) : onClose())}>Annuler</Button>
            <Button type="submit" block loading={busy}>{child ? "Enregistrer" : "Ajouter"}</Button>
          </div>
          {child && (
            <button type="button" onClick={() => remove(child)} className="mt-1 self-center text-sm font-semibold text-sec hover:text-sortie-ink">
              Retirer {child.name} de la tribu
            </button>
          )}
        </form>
      </Sheet>
    );
  }

  const [bg, ink] = childColors(children, child.id);
  const cd = countdown(Number(child.birthDate), "anniv", now);
  const theirFolders = folders.filter((f) => f.childIds?.includes(child.id));
  return (
    <Sheet
      open
      onClose={onClose}
      title={`Profil de ${child.name}`}
      header={
        <div className="flex items-center gap-3.5 px-5 pt-3 pb-1 sm:px-6 sm:pt-5">
          <span className="flex h-16 w-16 shrink-0 animate-pop items-center justify-center rounded-large font-display text-[28px] font-extrabold text-white" style={{ background: ink }}>
            {initial(child.name)}
          </span>
          <span className="flex min-w-0 flex-1 flex-col gap-0.5">
            <span className="truncate font-display text-2xl font-extrabold tracking-[-0.4px]">{child.name}</span>
            <span className="text-sm text-sec">{formatAge(child.birthDate)} · né·e le {formatLongDate(Number(child.birthDate))}</span>
          </span>
          <button
            type="button"
            data-close
            onClick={onClose}
            aria-label="Fermer"
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-sable-2 text-ecorce hover:bg-line"
          >
            <Icon name="close" size={18} />
          </button>
        </div>
      }
    >
      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-2.5 rounded-medium px-3.5 py-3" style={{ background: bg, color: ink }}>
          <Icon name="cadeau" size={22} />
          <span className="flex-1 text-sm font-bold">{birthdayLabel(child.birthDate)}</span>
          {cd && <span className="text-[13px] font-extrabold">{cd.label}</span>}
        </div>
        {theirFolders.length > 0 && (
          <div className="flex flex-col gap-2">
            <span className="text-sm font-bold">Ses dossiers</span>
            <div className="flex flex-wrap gap-2">
              {theirFolders.map((f) => {
                const c = folderColor(f.color);
                return (
                  <RouterLink key={f.id} to={`/dossiers/${f.id}`} onClick={onClose} className="inline-flex h-[34px] items-center gap-1.5 rounded-small px-3 text-[13px] font-bold hover:brightness-95" style={{ background: c.bg, color: c.solid }}>
                    <FolderGlyph icon={f.icon} size={15} />
                    {f.name}
                  </RouterLink>
                );
              })}
            </div>
          </div>
        )}
        <p className="text-xs text-muted">L'âge sert à filtrer l'Explorer et à guider les idées de l'IA. Rien n'est public.</p>
        <Button icon="sparkles" block onClick={() => onIdeas(child)}>Des idées pour {child.name}</Button>
        <Button variant="ghost" icon="pencil" block onClick={() => setEditing(true)}>Modifier le profil</Button>
      </div>
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

/**
 * « Inviter autour du feu » : un parent au foyer (invitation à la tribu par
 * email) ou un proche dans le cercle (accès à des dossiers choisis).
 */
function InviteSheet({ folders, onClose, onTribe, onCircle, onShareLink }: {
  folders: Folder[]; onClose: () => void; onTribe: (t: TribeData) => void; onCircle: (inv: CircleInvitation) => void; onShareLink: (f: Folder) => void;
}) {
  const [role, setRole] = useState<"foyer" | "cercle">("foyer");
  const roles = [
    { k: "foyer", title: "Au foyer", desc: "Un parent : voit et modifie tout." },
    { k: "cercle", title: "Dans le cercle", desc: "Un proche : seulement les dossiers choisis." },
  ] as const;
  return (
    <Sheet open onClose={onClose} title="Inviter autour du feu">
      <div className="flex flex-col gap-3.5">
        <div role="radiogroup" aria-label="Type d'invitation" className="grid grid-cols-2 gap-2.5">
          {roles.map((r) => {
            const on = role === r.k;
            return (
              <button
                key={r.k}
                type="button"
                role="radio"
                aria-checked={on}
                onClick={() => setRole(r.k)}
                className={`flex flex-col gap-1 rounded-medium bg-card p-3 text-left ${SPRING} ${on ? "scale-[1.02] shadow-[inset_0_0_0_2px_#C8492C]" : "shadow-[inset_0_0_0_1px_#EADBC8]"}`}
              >
                <span className="font-display text-base font-extrabold">{r.title}</span>
                <span className="text-xs leading-4 text-sec">{r.desc}</span>
              </button>
            );
          })}
        </div>
        {role === "foyer" ? (
          <InviteForm onDone={onTribe} />
        ) : folders.length ? (
          <>
            <CircleInviteForm folders={folders} onDone={onCircle} />
            <h3 className="mt-3 text-[13px] font-bold text-sec">Ou partager un dossier par lien</h3>
            <ul className="flex flex-col gap-2">
              {folders.map((f) => {
                const c = folderColor(f.color);
                return (
                  <li key={f.id}>
                    <button
                      type="button"
                      onClick={() => onShareLink(f)}
                      className="flex w-full items-center gap-3 rounded-medium bg-card p-3 text-left hover:bg-sable-2"
                    >
                      <span className="flex h-10 w-10 items-center justify-center rounded-small text-on-solid" style={{ background: c.solid }}>
                        <FolderGlyph icon={f.icon} size={20} />
                      </span>
                      <span className="flex-1 font-bold">{f.name}</span>
                      <span className="text-xs text-sec">{plural(f.collaborators?.length ?? 0, "membre", "membres")}</span>
                    </button>
                  </li>
                );
              })}
            </ul>
          </>
        ) : (
          <NoFolderToShare onClose={onClose} />
        )}
      </div>
    </Sheet>
  );
}

function NoFolderToShare({ onClose }: { onClose: () => void }) {
  const { newFolder } = useComposer();
  return (
    <EmptyState
      icon="tipi"
      title="Aucun dossier à ouvrir"
      text="Le cercle voit seulement les dossiers que vous lui ouvrez : créez-en un d'abord."
      action={<Button icon="plus" onClick={() => { onClose(); newFolder(); }}>Créer un dossier</Button>}
    />
  );
}

function InviteForm({ onDone }: { onDone: (t: TribeData) => void }) {
  const { toast } = useFeedback();
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const invite = async (e: FormEvent) => {
    e.preventDefault();
    if (!email.trim()) return setError("Saisissez l'adresse email de votre proche.");
    setBusy(true);
    setError("");
    try {
      onDone(await api.tribe.invite(email.trim()));
      toast("Invitation envoyée");
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <form onSubmit={invite} className="flex flex-col gap-3">
      <p className="text-[15px] text-ecorce-2">
        Votre proche reçoit un lien pour rejoindre la tribu et tout gérer avec vous : idées, dossiers et enfants.
      </p>
      <Field label="Son adresse email" type="email" icon="send" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="camille@exemple.fr" autoComplete="email" />
      {error && <ErrorNote>{error}</ErrorNote>}
      <Button type="submit" icon="send" block loading={busy} className="!bg-prune !text-soleil hover:!bg-prune/90">Envoyer l'invitation</Button>
      <p className="text-center text-xs text-muted">
        Le lien expire dans 7 jours. Pas encore de compte ? Il pourra en créer un depuis le lien. Ses propres idées ne
        rejoindront la tribu qu'une fois qu'il l'aura confirmé depuis « Ma tribu ».
      </p>
    </form>
  );
}

/**
 * Invite un proche (compte ou non) à des dossiers choisis : il reçoit un lien
 * par email et n'y entre qu'en l'acceptant. L'invitation reste en attente dans
 * « Le cercle ».
 */
function CircleInviteForm({ folders, onDone }: { folders: Folder[]; onDone: (inv: CircleInvitation) => void }) {
  const { toast } = useFeedback();
  const [picked, setPicked] = useState<ReadonlySet<string>>(() => new Set(folders.slice(0, 1).map((f) => f.id)));
  const [role, setRole] = useState<CollaboratorRole>("COLLABORATOR_ROLE_VIEWER");
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const toggle = (id: string) =>
    setPicked((s) => {
      const next = new Set(s);
      if (!next.delete(id)) next.add(id);
      return next;
    });

  const invite = async (e: FormEvent) => {
    e.preventDefault();
    if (!picked.size) return setError("Choisissez au moins un dossier à ouvrir.");
    if (!email.trim()) return setError("Saisissez l'adresse email de votre proche.");
    setBusy(true);
    setError("");
    try {
      const inv = await api.circle.invite(email.trim(), folders.filter((x) => picked.has(x.id)).map((f) => f.id), role);
      toast("Invitation envoyée");
      onDone(inv);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={invite} className="flex flex-col gap-3">
      <div className="flex flex-col gap-2">
        <span className="text-sm font-bold">Dossiers ouverts</span>
        <div className="flex flex-wrap gap-2">
          {folders.map((f) => {
            const on = picked.has(f.id);
            return (
              <button
                key={f.id}
                type="button"
                aria-pressed={on}
                onClick={() => toggle(f.id)}
                className={`inline-flex h-[34px] items-center gap-1.5 rounded-small px-3 text-[13px] font-bold transition-colors ${on ? "bg-ecorce text-sable" : "bg-card text-sec shadow-[inset_0_0_0_1px_#EADBC8]"}`}
              >
                {on ? "✓ " : "+ "}{f.name}
              </button>
            );
          })}
        </div>
      </div>
      <div role="radiogroup" aria-label="Ses droits" className="flex gap-0.5 self-start rounded-small bg-sable-2 p-[3px]">
        {([["COLLABORATOR_ROLE_VIEWER", "Lecteur"], ["COLLABORATOR_ROLE_EDITOR", "Éditeur"]] as const).map(([k, label]) => (
          <button
            key={k}
            type="button"
            role="radio"
            aria-checked={role === k}
            onClick={() => setRole(k)}
            className={`h-[30px] rounded-[9px] px-3 text-xs font-bold transition-colors ${role === k ? "bg-card text-ecorce" : "text-sec"}`}
          >
            {label}
          </button>
        ))}
      </div>
      <Field label="Son adresse email" type="email" icon="send" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="mamie.jo@exemple.fr" autoComplete="email" />
      {error && <ErrorNote>{error}</ErrorNote>}
      <Button type="submit" icon="send" block loading={busy} className="!bg-prune !text-soleil hover:!bg-prune/90">Envoyer l'invitation</Button>
      <p className="text-center text-xs text-muted">
        Le lien expire dans 7 jours. Pas encore de compte ? Il pourra en créer un depuis le lien.{" "}
        {role === "COLLABORATOR_ROLE_EDITOR" ? "Il pourra ajouter ses idées" : "Il pourra consulter"} sans voir le reste de la tribu.
        Vous pourrez changer les accès à tout moment.
      </p>
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
    <section className="flex flex-col gap-3 rounded-large bg-soleil p-4">
      <span className="font-display text-lg font-bold">Bienvenue dans la tribu {tribe.name} !</span>
      <p className="text-sm text-ecorce-2">
        Vous voyez déjà tout ce que la tribu partage. Partagez aussi votre contenu{what ? ` (${what})` : ""} : les
        autres membres pourront le voir et le modifier. Vos idées restent signées de votre nom.
      </p>
      <Button icon="check" loading={busy} onClick={share} className="self-start">Partager avec la tribu</Button>
    </section>
  );
}

function MembersSheet({ open, onClose, accounts, invitations, meId, onChange, onInvite }: {
  open: boolean; onClose: () => void; accounts: TribeMember[]; invitations: TribeInvitation[]; meId: string;
  onChange: (t: TribeData) => void; onInvite: () => void;
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

  const revoke = async (inv: TribeInvitation) => {
    try {
      onChange(await api.tribe.revokeInvitation(inv.id));
      toast("Invitation annulée");
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
    <Sheet open={open} onClose={onClose} title="Le foyer">
      <ul className="mb-4 flex flex-col divide-y divide-sable-2 overflow-hidden rounded-[18px] bg-card">
        {accounts.map((m, i) => {
          const [bg, ink] = AVATAR_COLORS[i % AVATAR_COLORS.length];
          const self = m.userId === meId;
          const name = m.displayName || m.email || "Membre";
          return (
            <li key={m.userId} className="flex items-center gap-3 px-3.5 py-3">
              <span className="flex h-10 w-10 items-center justify-center rounded-small font-display font-extrabold" style={{ background: bg, color: ink }}>{initial(name)}</span>
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="truncate font-bold">{name}{self && " (vous)"}</span>
                <span className="truncate text-xs text-sec">
                  {[m.role === "TRIBE_ROLE_OWNER" ? "Créateur" : "Parent", !m.sharing && "partage de son contenu en attente", !self && m.email]
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
      {invitations.length > 0 && (
        <>
          <h3 className="mb-1.5 text-[13px] font-bold text-sec">Invitations en attente</h3>
          <ul className="mb-4 flex flex-col divide-y divide-sable-2 overflow-hidden rounded-[18px] bg-card">
            {invitations.map((inv) => (
              <li key={inv.id} className="flex items-center gap-3 px-3.5 py-3">
                <span className="flex h-10 w-10 items-center justify-center rounded-small bg-sable-2 text-sec">
                  <Icon name="send" size={18} />
                </span>
                <span className="flex min-w-0 flex-1 flex-col">
                  <span className="truncate font-bold">{inv.email}</span>
                  <span className="truncate text-xs text-sec">
                    {[inv.invitedByName && `invité par ${inv.invitedByName}`, inv.expiresAt && `jusqu'au ${shortDate(Number(inv.expiresAt))}`]
                      .filter(Boolean).join(" · ")}
                  </span>
                </span>
                <Button size="sm" variant="ghost" onClick={() => revoke(inv)}>Annuler</Button>
              </li>
            ))}
          </ul>
        </>
      )}
      <div className="flex flex-col gap-2">
        <Button variant="secondary" icon="personPlus" block onClick={onInvite}>Inviter un proche</Button>
        {me?.sharing && accounts.length > 1 && (
          <button type="button" onClick={stopSharing} className="mt-2 self-center text-sm font-semibold text-sec hover:text-sortie-ink">
            Ne plus partager mon contenu
          </button>
        )}
      </div>
    </Sheet>
  );
}

/**
 * Suppression définitive du compte (RGPD, droit à l'effacement) : on dit ce qui
 * disparaît et ce qui reste aux autres, puis on demande le mot de passe.
 */
function DeleteAccountSheet({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate();
  const { toast } = useFeedback();
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!password) return;
    setBusy(true);
    setError("");
    try {
      await api.account.delete(password);
      clearSession();
      navigate("/bienvenue", { replace: true });
      toast("Votre compte et vos données ont été supprimés");
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  };

  return (
    <Sheet open onClose={onClose} title="Supprimer mon compte">
      <form onSubmit={submit} className="flex flex-col gap-4">
        <div className="flex flex-col gap-2 rounded-medium bg-card p-3.5 text-[15px]">
          <p className="font-bold">Seront définitivement effacés :</p>
          <ul className="list-disc space-y-1 pl-5 text-ecorce-2">
            <li>votre compte (nom, email, mot de passe) ;</li>
            <li>vos idées et vos dossiers, y compris ceux partagés avec la communauté ;</li>
            <li>les profils de vos enfants ;</li>
            <li>vos commentaires, « j'aime » et abonnements.</li>
          </ul>
          <p className="text-sm text-sec">
            Les idées que vos proches ont ajoutées dans vos dossiers leur restent. Si vous avez créé la tribu, elle revient au membre le plus ancien.
          </p>
        </div>
        <p className="text-sm text-sec">Pensez à télécharger vos données avant, depuis les paramètres : cette action est irréversible.</p>
        <Field
          label="Mot de passe"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          icon="lock"
        />
        {error && <ErrorNote>{error}</ErrorNote>}
        <Button type="submit" variant="danger" icon="trash" loading={busy} disabled={!password}>
          Supprimer définitivement
        </Button>
        <Button type="button" variant="ghost" onClick={onClose}>Annuler</Button>
      </form>
    </Sheet>
  );
}

/** « Applications connectées » : accès donnés à Claude (serveur MCP), à voir et retirer. */
function ConnectedAppsSheet({ onClose }: { onClose: () => void }) {
  const apps = useAsync(() => api.connectedApps.list(), []);
  const { reload } = useMyData();

  return (
    <Sheet open onClose={onClose} title="Applications connectées">
      <div className="flex flex-col gap-4">
        <p className="text-sm text-sec">
          Les applications auxquelles vous avez donné accès à votre compte, par exemple Claude. Elles peuvent seulement
          voir vos dossiers et y ajouter des idées privées. Un accès dure au plus 90 jours.
        </p>
        {apps.error && <ErrorNote>{apps.error.message}</ErrorNote>}
        {!apps.data && !apps.error && <Spinner />}
        {apps.data?.length === 0 && (
          <p className="rounded-medium bg-card p-3.5 text-[15px] text-ecorce-2">Aucune application n'a accès à votre compte.</p>
        )}
        {apps.data?.map((app) => (
          <ConnectedAppCard key={app.id} app={app} onRevoked={(deleted) => { apps.reload(); if (deleted) reload(); }} />
        ))}
      </div>
    </Sheet>
  );
}

function ConnectedAppCard({ app, onRevoked }: { app: ConnectedApp; onRevoked: (deletedIdeas: number) => void }) {
  const { confirm, toast } = useFeedback();
  const [deleteIdeas, setDeleteIdeas] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const name = app.name || "Application";
  const ideas = app.ideaCount ?? 0;

  const revoke = async () => {
    const ok = await confirm({
      title: `Retirer l'accès de ${name} ?`,
      text: deleteIdeas
        ? `${name} ne pourra plus rien faire sur votre compte, et ${ideas > 1 ? `les ${ideas} idées qu'elle a ajoutées seront effacées` : "l'idée qu'elle a ajoutée sera effacée"}.`
        : `${name} ne pourra plus rien faire sur votre compte. Les idées qu'elle a ajoutées restent.`,
      confirmLabel: "Retirer l'accès",
      danger: true,
    });
    if (!ok) return;
    setBusy(true);
    setError("");
    try {
      const deleted = await api.connectedApps.revoke(app.id, deleteIdeas);
      toast(deleted ? `Accès retiré, ${plural(deleted, "idée effacée", "idées effacées")}` : "Accès retiré");
      onRevoked(deleted);
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  };

  return (
    <div className="flex flex-col gap-3 rounded-medium bg-card p-3.5">
      <div className="flex items-center gap-3">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-medium bg-prune text-soleil"><Icon name="sparkles" size={20} /></span>
        <span className="flex min-w-0 flex-col">
          <span className="truncate font-bold">{name}</span>
          <span className="text-xs text-sec">{app.site}{app.connectedAt ? ` · connectée le ${formatLongDate(app.connectedAt)}` : ""}</span>
        </span>
      </div>
      <ul className="list-disc space-y-0.5 pl-5 text-sm text-ecorce-2">
        {scopeLabels(app.scopes ?? []).map((label) => <li key={label}>{label}</li>)}
      </ul>
      <p className="text-xs text-sec">
        {app.lastUsedAt ? `Dernier usage le ${formatLongDate(app.lastUsedAt)}` : ""}
        {app.expiresAt ? ` · accès jusqu'au ${formatLongDate(app.expiresAt)}` : ""}
        {` · ${plural(ideas, "idée ajoutée", "idées ajoutées")}`}
      </p>
      {ideas > 0 && (
        <label className="flex items-center justify-between gap-3 text-sm">
          <span>Effacer aussi {ideas > 1 ? `les ${ideas} idées ajoutées` : "l'idée ajoutée"}</span>
          <Toggle checked={deleteIdeas} onChange={setDeleteIdeas} label="Effacer aussi les idées ajoutées par cette application" />
        </label>
      )}
      {error && <ErrorNote>{error}</ErrorNote>}
      <Button variant="danger" size="sm" icon="close" loading={busy} onClick={() => void revoke()}>
        Retirer l'accès
      </Button>
    </div>
  );
}

/** « 29 oct. » */
function shortDate(ms: number): string {
  return new Date(ms).toLocaleDateString("fr-FR", { day: "numeric", month: "short" });
}
