import { Link as RouterLink } from "react-router";
import { initial } from "../lib/format";
import { setThemePref, useThemePref, type ThemePref } from "../lib/theme";
import Icon, { type IconName } from "./Icon";
import Seo from "./Seo";
import { Page } from "./Shell";

/**
 * Écran « Réglages » de Ma tribu (maquette « Ma tribu · Réglages regroupés ») :
 * le compte, puis la tribu, les préférences, les outils, le compte et ses
 * données, et l'aide. Les actions restent celles de Ma tribu (feuilles et
 * confirmations), qui les passe ici.
 */
export default function SettingsView(p: {
  name: string;
  email?: string;
  owner: boolean;
  isAdmin: boolean;
  tribeName: string;
  membersLabel: string;
  childrenLabel?: string;
  followingCount?: number;
  exporting: boolean;
  onBack: () => void;
  onRename: () => void;
  onMembers: () => void;
  onChildren: () => void;
  onFollowing: () => void;
  onApps: () => void;
  onPassword: () => void;
  onExport: () => void;
  onDelete: () => void;
  onLogout: () => void;
}) {
  return (
    <Page>
      <Seo title="Réglages · Tribbae" noindex />
      <div className="mx-auto flex w-full max-w-2xl flex-col gap-5">
        <div className="flex items-center gap-3">
          <button type="button" onClick={p.onBack} aria-label="Retour à Ma tribu" className="flex h-10 w-10 items-center justify-center rounded-full bg-sable-2 text-ecorce hover:bg-card">
            <Icon name="chevronLeft" size={20} />
          </button>
          <span className="text-[13px] font-bold text-sec">Ma tribu</span>
        </div>
        <div className="flex flex-col gap-1">
          <h1 className="font-display text-[32px] leading-tight font-extrabold tracking-[-0.8px]">Réglages</h1>
          <span className="text-sm text-sec">Votre compte et votre tribu, au même endroit.</span>
        </div>

        <section aria-label="Votre compte" className="flex items-center gap-3.5 rounded-large bg-card p-4">
          <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-[20px] bg-braise font-display text-2xl font-extrabold text-[#3a211b]">{initial(p.name)}</span>
          <span className="flex min-w-0 flex-1 flex-col gap-0.5">
            <span className="text-[17px] font-bold">{p.name}</span>
            {p.email && <span className="truncate text-[13px] text-sec">{p.email}</span>}
            {p.owner && <span className="mt-1 self-start rounded-lg bg-sable-2 px-2 py-0.5 text-[11px] font-bold text-sec">Créateur de la tribu</span>}
          </span>
        </section>

        <Group title="La tribu" note="Le foyer et le cercle se gèrent aussi directement depuis Ma tribu.">
          <Row icon="tipi" label="Nom de la tribu" sub={`La tribu ${p.tribeName}`} onClick={p.onRename} />
          <Row icon="people" label="Membres et invitations" value={p.membersLabel} onClick={p.onMembers} />
          <Row icon="person" label="Les enfants" value={p.childrenLabel} onClick={p.onChildren} />
          <Row icon="heart" label="Tribus suivies" value={p.followingCount === undefined ? undefined : String(p.followingCount)} onClick={p.onFollowing} last />
        </Group>

        <Group title="Préférences" note="L'apparence est retenue sur cet appareil.">
          <AppearanceRow />
        </Group>

        <Group title="Outils">
          <Row icon="cart" label="Liste de courses" to="/courses" />
          <Row icon="sparkles" iconClass="text-prune" label="Applications connectées" sub="Voir et retirer l'accès donné à Claude" onClick={p.onApps} last />
        </Group>

        <Group title="Compte et données">
          <Row icon="lock" label="Mot de passe" sub="Recevoir un lien par email" onClick={p.onPassword} />
          <Row icon="lock" label="Confidentialité" to="/confidentialite" />
          <Row icon="send" label={p.exporting ? "Préparation de vos données…" : "Télécharger mes données"} onClick={p.onExport} />
          <Row icon="trash" label="Supprimer mon compte" onClick={p.onDelete} danger last />
        </Group>

        <Group title="Aide">
          <Row icon="help" label="Aide & FAQ" to="/aide" />
          <Row icon="tipi" label="À propos de Tribbae" to="/a-propos" last={!p.isAdmin} />
          {p.isAdmin && <Row icon="crown" label="Administration" to="/admin" badge="Admin" last />}
        </Group>

        <button
          type="button"
          onClick={p.onLogout}
          className="flex h-[52px] items-center justify-center gap-2 rounded-medium bg-card text-[15px] font-bold text-sortie-ink shadow-[inset_0_0_0_1.5px_var(--color-sortie-bg)]"
        >
          <Icon name="logout" size={20} />
          Se déconnecter
        </button>
        <span className="text-center text-xs text-muted">Tribbae {__APP_VERSION__}</span>
      </div>
    </Page>
  );
}

function Group({ title, note, children }: { title: string; note?: string; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="px-1 text-xs font-bold tracking-[0.6px] text-sec uppercase">{title}</h2>
      <div className="overflow-hidden rounded-large bg-card">{children}</div>
      {note && <span className="px-1 text-xs text-sec">{note}</span>}
    </section>
  );
}

function Row({ icon, iconClass = "text-sec", label, sub, value, badge, to, onClick, last, danger }: {
  icon: IconName; iconClass?: string; label: string; sub?: string; value?: string; badge?: string; to?: string; onClick?: () => void; last?: boolean; danger?: boolean;
}) {
  const cls = `flex w-full items-center gap-3 px-4 py-[13px] text-left transition-colors hover:bg-sable/60 ${last ? "" : "border-b border-sable-2"} ${danger ? "text-sortie-ink" : ""}`;
  const content = (
    <>
      <Icon name={icon} size={20} className={danger ? "" : iconClass} />
      <span className="flex min-w-0 flex-1 flex-col gap-px">
        <span className="text-[15px] font-semibold">{label}</span>
        {sub && <span className="truncate text-[13px] text-sec">{sub}</span>}
      </span>
      {value && <span className="shrink-0 text-[13px] text-sec">{value}</span>}
      {badge && <span className="rounded-lg bg-sable-2 px-[7px] py-0.5 text-[11px] font-bold text-sec">{badge}</span>}
      {!danger && <Icon name="chevronRight" size={18} className="text-muted" />}
    </>
  );
  return to ? <RouterLink to={to} className={cls}>{content}</RouterLink> : <button type="button" onClick={onClick} className={cls}>{content}</button>;
}

/** « Apparence » : jour, soir (mode sombre « veillée ») ou comme l'appareil. Retenue sur cet appareil. */
function AppearanceRow() {
  const pref = useThemePref();
  const options: [ThemePref, string][] = [["jour", "Jour"], ["soir", "Soir"], ["auto", "Auto"]];
  return (
    <div className="flex items-center gap-3 px-4 py-3">
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

