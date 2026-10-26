import { useState, type FormEvent } from "react";
import { shareOrCopy } from "../lib/share";
import * as api from "../lib/api";
import type { CircleInvitation, CollaboratorRole, Folder } from "../lib/types";
import { useAsync } from "../lib/useAsync";
import { useFeedback } from "./feedback";
import { AVATAR_COLORS } from "../lib/categories";
import Icon from "./Icon";
import Sheet from "./Sheet";
import { Avatar, Button, ErrorNote, Field } from "./ui";

const shareUrlFor = (token: string) => `${window.location.origin}/share/${token}`;

/** À monter uniquement quand la feuille est ouverte. */
export default function ShareFolderSheet({ onClose, folder, onChange }: {
  onClose: () => void;
  folder: Folder;
  onChange: (f: Folder) => void;
}) {
  const { toast, confirm } = useFeedback();
  const [created, setCreated] = useState<string>();
  const token = created ?? folder.shareToken;
  const [email, setEmail] = useState("");
  const [role, setRole] = useState<CollaboratorRole>("COLLABORATOR_ROLE_EDITOR");
  const [busy, setBusy] = useState(false);
  const [linkBusy, setLinkBusy] = useState(false);
  const [error, setError] = useState("");
  // Invitations en attente qui ouvrent ce dossier (envoyées par moi)
  const invites = useAsync(() => api.circle.invitations(), [folder.id]);
  const pending = (invites.data ?? []).filter((inv) => inv.folderIds?.includes(folder.id));

  const makeLink = async () => {
    setLinkBusy(true);
    try {
      const r = await api.folders.share(folder.id);
      setCreated(r.shareToken);
      onChange({ ...folder, shareToken: r.shareToken });
      const res = await shareOrCopy(shareUrlFor(r.shareToken), folder.name);
      if (res === "copied") toast("Lien copié");
    } catch (e) {
      toast((e as Error).message, "error");
    } finally {
      setLinkBusy(false);
    }
  };

  const copy = async () => {
    if (!token) return;
    const res = await shareOrCopy(shareUrlFor(token), folder.name);
    if (res === "copied") toast("Lien copié");
    if (res === "failed") toast("Copie impossible, sélectionnez le lien à la main.", "error");
  };

  const invite = async (e: FormEvent) => {
    e.preventDefault();
    if (!email.trim()) return;
    setBusy(true);
    setError("");
    try {
      // Invitation par email : la personne (compte ou non) entre en acceptant le lien
      const inv = await api.circle.invite(email.trim(), [folder.id], role);
      invites.setData((list) => [...(list ?? []).filter((x) => x.id !== inv.id), inv]);
      setEmail("");
      toast("Invitation envoyée");
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const remove = async (userId: string, name: string) => {
    if (!(await confirm({ title: `Retirer ${name} ?`, text: "Cette personne n'aura plus accès au dossier.", confirmLabel: "Retirer", danger: true }))) return;
    try {
      onChange(await api.folders.removeCollaborator(folder.id, userId));
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };

  const cancel = async (inv: CircleInvitation) => {
    const others = (inv.folderNames ?? []).filter((n) => n !== folder.name);
    const ok = await confirm({
      title: "Annuler l'invitation ?",
      text: `Le lien envoyé à ${inv.email} ne marchera plus${others.length ? `, ni pour ${others.join(", ")}` : ""}. Vous pourrez l'inviter à nouveau.`,
      confirmLabel: "Annuler l'invitation",
      danger: true,
    });
    if (!ok) return;
    try {
      await api.circle.revokeInvitation(inv.id);
      invites.setData((list) => (list ?? []).filter((x) => x.id !== inv.id));
      toast("Invitation annulée");
    } catch (e) {
      toast((e as Error).message, "error");
    }
  };

  const collaborators = folder.collaborators ?? [];

  return (
    <Sheet open onClose={onClose} title="Inviter dans le dossier">
      <div className="flex flex-col gap-6">
        <section className="flex flex-col gap-2.5">
          <h3 className="text-[13px] font-bold text-sec">Lien de partage</h3>
          <p className="text-sm text-ecorce-2">
            Toute personne qui a le lien peut consulter ce dossier, même sans compte.
          </p>
          {token ? (
            <div className="flex items-center gap-2 rounded-medium bg-card p-2 pl-4 shadow-card">
              <input
                readOnly
                aria-label="Lien de partage"
                value={shareUrlFor(token)}
                onFocus={(e) => e.currentTarget.select()}
                className="min-w-0 flex-1 bg-transparent text-sm text-sec outline-none"
              />
              <Button size="sm" variant="dark" icon="copy" onClick={copy}>Copier</Button>
            </div>
          ) : (
            <Button variant="dark" icon="link" loading={linkBusy} onClick={makeLink} className="self-start">
              Créer un lien de partage
            </Button>
          )}
        </section>

        <section className="flex flex-col gap-3">
          <h3 className="text-[13px] font-bold text-sec">Membres invités</h3>
          {collaborators.length > 0 ? (
            <ul className="flex flex-col divide-y divide-sable-2 overflow-hidden rounded-[18px] bg-card">
              {collaborators.map((c, i) => {
                const [bg, ink] = AVATAR_COLORS[(i + 1) % AVATAR_COLORS.length];
                const name = c.displayName || c.email || "Membre";
                return (
                  <li key={c.userId} className="flex items-center gap-3 px-3.5 py-3">
                    <Avatar name={name} color={bg} ink={ink} size={36} rounded="rounded-small" />
                    <span className="flex min-w-0 flex-1 flex-col">
                      <span className="truncate text-sm font-bold">{name}</span>
                      <span className="text-xs text-sec">{c.role === "COLLABORATOR_ROLE_EDITOR" ? "Peut ajouter des idées" : "Lecture seule"}</span>
                    </span>
                    <button
                      type="button"
                      aria-label={`Retirer ${name}`}
                      onClick={() => remove(c.userId, name)}
                      className="flex h-9 w-9 items-center justify-center rounded-full text-sec hover:bg-sortie-bg hover:text-sortie-ink"
                    >
                      <Icon name="close" size={18} />
                    </button>
                  </li>
                );
              })}
            </ul>
          ) : (
            !pending.length && <p className="text-sm text-sec">Invitez un proche pour remplir ce dossier ensemble : il reçoit un lien par email, avec ou sans compte Tribbae.</p>
          )}
          {pending.length > 0 && (
            <ul className="flex flex-col gap-2">
              {pending.map((inv) => (
                <li key={inv.id} className="flex items-center gap-3 rounded-[18px] border-2 border-dashed border-dash px-3.5 py-2.5">
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-small bg-sable-2 text-muted">
                    <Icon name="send" size={16} />
                  </span>
                  <span className="flex min-w-0 flex-1 flex-col">
                    <span className="truncate text-sm font-bold">{inv.email}</span>
                    <span className="text-xs text-sec">En attente · {inv.role === "COLLABORATOR_ROLE_EDITOR" ? "peut ajouter des idées" : "lecture seule"}</span>
                  </span>
                  <button
                    type="button"
                    onClick={() => cancel(inv)}
                    className="h-9 shrink-0 rounded-small px-3 text-[13px] font-bold text-sec hover:bg-sable-2"
                  >
                    Annuler
                  </button>
                </li>
              ))}
            </ul>
          )}

          <form onSubmit={invite} className="flex flex-col gap-3">
            <Field
              label="Email du proche"
              icon="personPlus"
              type="email"
              autoComplete="email"
              placeholder="camille@exemple.fr"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
            <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="Droits">
              {([
                ["COLLABORATOR_ROLE_EDITOR", "Peut ajouter"],
                ["COLLABORATOR_ROLE_VIEWER", "Lecture seule"],
              ] as const).map(([k, label]) => (
                <button
                  key={k}
                  type="button"
                  role="radio"
                  aria-checked={role === k}
                  onClick={() => setRole(k)}
                  className={`h-11 rounded-small text-sm font-semibold transition-colors ${role === k ? "bg-ecorce text-sable" : "bg-sable-2 text-sec"}`}
                >
                  {label}
                </button>
              ))}
            </div>
            {error && <ErrorNote>{error}</ErrorNote>}
            <Button type="submit" loading={busy} disabled={!email.trim()} icon="send">Inviter</Button>
          </form>
        </section>
      </div>
    </Sheet>
  );
}
