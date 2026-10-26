import { useEffect, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router";
import { useFeedback } from "../components/feedback";
import Icon from "../components/Icon";
import Seo from "../components/Seo";
import { Button, ErrorNote, PageSpinner } from "../components/ui";
import * as api from "../lib/api";
import { useMyData } from "../lib/data";
import { initial } from "../lib/format";
import { clearSession, useSession } from "../lib/session";
import { useAsync } from "../lib/useAsync";
import { AuthShell } from "./Welcome";

// Comme pour la tribu (pages/Invitation.tsx) : le jeton du lien (après le #)
// est gardé le temps de se connecter ou de créer son compte, puis effacé.
// Nom de l'entrée sessionStorage (pas un secret : le jeton est la valeur stockée)
const PENDING_INVITATION_ITEM = "tribbae.circleInvitation";

function storedToken(): string {
  try {
    return sessionStorage.getItem(PENDING_INVITATION_ITEM) ?? "";
  } catch {
    return "";
  }
}

function storeToken(token: string) {
  try {
    if (token) sessionStorage.setItem(PENDING_INVITATION_ITEM, token);
    else sessionStorage.removeItem(PENDING_INVITATION_ITEM);
  } catch {
    // Stockage indisponible (navigation privée) : il faudra rouvrir le lien
  }
}

function readToken(): string {
  return decodeURIComponent(window.location.hash.replace(/^#/, "")) || storedToken();
}

/** « Rejoindre le cercle » : écran ouvert depuis le lien de l'email (dossiers ouverts par un proche). */
export default function CircleInvitation() {
  const session = useSession();
  const navigate = useNavigate();
  const { toast } = useFeedback();
  const { upsertFolder } = useMyData();
  const [token] = useState(readToken);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const info = useAsync(() => (token ? api.circle.lookupInvitation(token) : Promise.resolve(undefined)), [token]);

  useEffect(() => {
    storeToken(token);
    if (window.location.hash) window.history.replaceState(window.history.state, "", window.location.pathname);
  }, [token]);

  const accept = async () => {
    setBusy(true);
    setError("");
    try {
      const folders = await api.circle.acceptInvitation(token);
      folders.forEach(upsertFolder);
      // Le lien est arrivé dans la boîte de l'adresse du compte : elle est confirmée
      api.markEmailVerified();
      storeToken("");
      toast(folders.length > 1 ? "Les dossiers sont ouverts" : "Le dossier est ouvert");
      navigate(folders.length === 1 ? `/dossiers/${folders[0].id}` : "/mes-idees", { replace: true });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const inv = info.data;
  const email = inv?.email ?? "";
  const authLink = (register: boolean) =>
    `/connexion?${new URLSearchParams({ ...(register ? { mode: "inscription" } : {}), email, next: "/invitation-cercle" })}`;

  let body;
  if (!token || info.error) {
    body = (
      <div className="flex w-full max-w-md flex-col gap-4">
        <h1 className="font-display text-[32px] leading-none font-extrabold tracking-[-0.8px]">Invitation introuvable</h1>
        <p className="text-[15px] text-sec">
          {info.error?.message ?? "Ouvrez le lien tel qu'il apparaît dans l'email d'invitation."} Demandez à la personne
          qui vous a invité de vous renvoyer une invitation.
        </p>
        <RouterLink to="/" className="text-center text-sm font-semibold text-terracotta hover:text-terracotta-dark">
          Découvrir Tribbae
        </RouterLink>
      </div>
    );
  } else if (!inv) {
    body = <PageSpinner />;
  } else {
    const inviter = inv.invitedByName || "Un membre de Tribbae";
    const folders = inv.folderNames ?? [];
    const editor = inv.role === "COLLABORATOR_ROLE_EDITOR";
    body = (
      <div className="flex w-full max-w-md flex-col gap-4">
        <div className="flex items-center gap-3">
          <span className="flex h-11 w-11 items-center justify-center rounded-full bg-[#FFC857] font-bold text-[#3A211B]">{initial(inviter)}</span>
          <span className="text-[15px] text-sec"><strong className="text-ecorce">{inviter}</strong> vous fait une place dans son cercle</span>
        </div>
        <h1 className="font-display text-[32px] leading-[1.05] font-extrabold tracking-[-0.8px]">
          {folders.length === 1 ? <>Le dossier «{" "}{folders[0]}{" "}» vous est ouvert</> : "Des dossiers vous sont ouverts"}
        </h1>
        <ul className="flex flex-col divide-y divide-line rounded-[22px] border border-line bg-card px-4">
          {folders.map((name, i) => (
            <li key={i} className="flex items-center gap-3 py-3">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[12px] bg-sable-2 text-ecorce">
                <Icon name="folder" size={18} />
              </span>
              <span className="truncate font-display text-[17px] font-bold">{name}</span>
            </li>
          ))}
        </ul>
        <p className="text-[15px] text-ecorce-2">
          {editor ? "Vous pourrez y voir les idées et en ajouter : sorties, cadeaux, recettes…" : "Vous pourrez y retrouver ses idées de sorties, de cadeaux ou de recettes."}{" "}
          Vous ne verrez que {folders.length > 1 ? "ces dossiers" : "ce dossier"}.
        </p>
        {error && <ErrorNote>{error}</ErrorNote>}
        {session ? (
          <>
            <Button block loading={busy} onClick={accept} className="!h-14 text-base">Voir {folders.length > 1 ? "les dossiers" : "le dossier"}</Button>
            <p className="text-center text-[13px] text-sec">
              Invitation envoyée à <strong className="text-ecorce">{email}</strong>.{" "}
              {/autre adresse/.test(error) && (
                <button type="button" onClick={() => { clearSession(); }} className="font-semibold text-terracotta">
                  Me connecter avec cette adresse
                </button>
              )}
            </p>
          </>
        ) : (
          <>
            <RouterLink to={authLink(true)} className="flex h-14 items-center justify-center rounded-[18px] bg-terracotta text-base font-bold text-white hover:bg-terracotta-dark">
              Créer mon compte et voir les dossiers
            </RouterLink>
            <RouterLink to={authLink(false)} className="text-center text-sm font-semibold text-terracotta hover:text-terracotta-dark">
              J'ai déjà un compte
            </RouterLink>
            <p className="text-center text-[13px] text-sec">Utilisez l'adresse invitée : <strong className="text-ecorce">{email}</strong>.</p>
          </>
        )}
      </div>
    );
  }

  return (
    <AuthShell back="/">
      <Seo title="Invitation dans le cercle · Tribbae" noindex />
      {body}
    </AuthShell>
  );
}
