import { useEffect, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router";
import { useFeedback } from "../components/feedback";
import { TipiMark } from "../components/Logo";
import Seo from "../components/Seo";
import { Button, ErrorNote, PageSpinner } from "../components/ui";
import * as api from "../lib/api";
import { AVATAR_COLORS } from "../lib/categories";
import { useMyData } from "../lib/data";
import { initial, plural } from "../lib/format";
import { clearSession, useSession } from "../lib/session";
import { useAsync } from "../lib/useAsync";
import { AuthShell } from "./Welcome";

// Le jeton du lien (après le #) est gardé le temps de se connecter ou de créer
// son compte, puis effacé : il ne passe jamais par l'adresse de la page.
// Nom de l'entrée sessionStorage (pas un secret : le jeton est la valeur stockée)
const PENDING_INVITATION_ITEM = "tribbae.invitation";

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

/** « Rejoindre la tribu » : écran ouvert depuis le lien de l'email d'invitation. */
export default function Invitation() {
  const session = useSession();
  const navigate = useNavigate();
  const { toast } = useFeedback();
  const { setTribe } = useMyData();
  const [token] = useState(readToken);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const info = useAsync(() => (token ? api.tribe.lookupInvitation(token) : Promise.resolve(undefined)), [token]);

  useEffect(() => {
    storeToken(token);
    if (window.location.hash) window.history.replaceState(window.history.state, "", window.location.pathname);
  }, [token]);

  const accept = async () => {
    setBusy(true);
    setError("");
    try {
      setTribe(await api.tribe.acceptInvitation(token));
      storeToken("");
      toast("Bienvenue dans la tribu !");
      navigate("/tribu", { replace: true });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const inv = info.data;
  const email = inv?.email ?? "";
  const authLink = (register: boolean) =>
    `/connexion?${new URLSearchParams({ ...(register ? { mode: "inscription" } : {}), email, next: "/invitation" })}`;

  let body;
  if (!token || info.error) {
    body = (
      <div className="flex w-full max-w-md flex-col gap-4">
        <h1 className="font-display text-[32px] leading-none font-extrabold tracking-[-0.8px]">Invitation introuvable</h1>
        <p className="text-[15px] text-sec">
          {info.error?.message ?? "Ouvrez le lien tel qu'il apparaît dans l'email d'invitation."} Demandez à la personne
          qui vous a invité de vous renvoyer une invitation depuis « Ma tribu ».
        </p>
        <RouterLink to="/" className="text-center text-sm font-semibold text-terracotta hover:text-terracotta-dark">
          Découvrir Tribbae
        </RouterLink>
      </div>
    );
  } else if (!inv) {
    body = <PageSpinner />;
  } else {
    const members = inv.memberNames ?? [];
    const inviter = inv.invitedByName || "Un membre de Tribbae";
    const tribeName = inv.tribeName || inviter;
    body = (
      <div className="flex w-full max-w-md flex-col gap-4">
        <div className="flex items-center gap-3">
          <span className="flex h-11 w-11 items-center justify-center rounded-full bg-[#FFC857] font-bold">{initial(inviter)}</span>
          <span className="text-[15px] text-sec"><strong className="text-ecorce">{inviter}</strong> vous invite à rejoindre sa tribu</span>
        </div>
        <h1 className="font-display text-[32px] leading-[1.05] font-extrabold tracking-[-0.8px]">Rejoignez la tribu «{"\u00a0"}{tribeName}{"\u00a0"}»</h1>
        <div className="flex items-center gap-4 rounded-[22px] border border-line bg-white/60 p-4">
          <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-[18px] bg-sable-2"><TipiMark size={40} /></span>
          <span className="flex min-w-0 flex-1 flex-col">
            <span className="truncate font-display text-xl font-bold">{tribeName}</span>
            <span className="text-[13px] text-sec">{plural(members.length, "membre", "membres")}</span>
          </span>
          <span className="flex">
            {members.slice(0, members.length > 3 ? 2 : 3).map((m, i) => {
              const [bg, ink] = AVATAR_COLORS[i % AVATAR_COLORS.length];
              return (
                <span key={i} className="-ml-2.5 flex h-[34px] w-[34px] items-center justify-center rounded-full border-2 border-sable text-[13px] font-bold first:ml-0" style={{ background: bg, color: ink }}>
                  {initial(m)}
                </span>
              );
            })}
            {members.length > 3 && (
              <span className="-ml-2.5 flex h-[34px] w-[34px] items-center justify-center rounded-full border-2 border-sable bg-prune text-xs font-bold text-soleil">
                +{members.length - 2}
              </span>
            )}
          </span>
        </div>
        <p className="text-[15px] text-ecorce-2">
          Dans une tribu, chacun ajoute ses idées de sorties, de cadeaux ou de recettes dans des dossiers partagés, et
          toute la famille les retrouve au même endroit. Vos propres idées restent privées tant que vous n'avez pas
          confirmé leur partage.
        </p>
        {error && <ErrorNote>{error}</ErrorNote>}
        {session ? (
          <>
            <Button block loading={busy} onClick={accept} className="!h-14 text-base">Rejoindre la tribu</Button>
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
              Créer mon compte et rejoindre
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
      <Seo title="Invitation dans une tribu · Tribbae" noindex />
      {body}
    </AuthShell>
  );
}
