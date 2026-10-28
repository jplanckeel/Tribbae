import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router";
import Seo from "../components/Seo";
import { Button, ErrorNote, PageSpinner } from "../components/ui";
import * as api from "../lib/api";
import { NEVER_ALLOWED, scopeLabels } from "../lib/oauth";
import { clearSession, useSession } from "../lib/session";
import { useAsync } from "../lib/useAsync";
import { AuthShell } from "./Welcome";

// L'identifiant de la demande (après le #) est gardé le temps de se connecter,
// puis effacé : il ne passe jamais par l'adresse de la page.
// Nom de l'entrée sessionStorage (pas un secret : l'identifiant est la valeur stockée)
const PENDING_REQUEST_ITEM = "tribbae.oauthRequest";

function storedRequest(): string {
  try {
    return sessionStorage.getItem(PENDING_REQUEST_ITEM) ?? "";
  } catch {
    return "";
  }
}

function storeRequest(id: string) {
  try {
    if (id) sessionStorage.setItem(PENDING_REQUEST_ITEM, id);
    else sessionStorage.removeItem(PENDING_REQUEST_ITEM);
  } catch {
    // Stockage indisponible (navigation privée) : il faudra relancer la connexion
  }
}

function readRequest(): string {
  return decodeURIComponent(window.location.hash.replace(/^#/, "")) || storedRequest();
}

/**
 * « Connecter une application » : page de consentement OAuth, ouverte par
 * une application (Claude) qui demande l'accès au compte.
 */
export default function ConnectApp() {
  const session = useSession();
  const [requestId] = useState(readRequest);
  const [busy, setBusy] = useState<"" | "approve" | "deny">("");
  const [error, setError] = useState("");
  const info = useAsync(
    () => (session && requestId ? api.oauth.consentInfo(requestId) : Promise.resolve(undefined)),
    [requestId, session?.token],
  );

  useEffect(() => {
    storeRequest(requestId);
    if (window.location.hash) window.history.replaceState(window.history.state, "", window.location.pathname);
  }, [requestId]);

  const decide = async (approve: boolean) => {
    setBusy(approve ? "approve" : "deny");
    setError("");
    try {
      const redirect = await api.oauth.decide(requestId, approve);
      storeRequest("");
      window.location.assign(redirect);
    } catch (err) {
      setError((err as Error).message);
      setBusy("");
    }
  };

  let body;
  if (!requestId || info.error) {
    body = (
      <div className="flex w-full max-w-md flex-col gap-4">
        <h1 className="font-display text-[32px] leading-none font-extrabold tracking-[-0.8px]">Demande introuvable</h1>
        <p className="text-[15px] text-sec">
          {info.error?.message ?? "Cette page s'ouvre depuis l'application qui demande l'accès à votre compte."} Relancez la
          connexion depuis cette application.
        </p>
        <RouterLink to="/" className="text-center text-sm font-semibold text-terracotta hover:text-terracotta-dark">
          Retour à Tribbae
        </RouterLink>
      </div>
    );
  } else if (!session) {
    body = (
      <div className="flex w-full max-w-md flex-col gap-4">
        <h1 className="font-display text-[32px] leading-[1.05] font-extrabold tracking-[-0.8px]">Connectez-vous pour continuer</h1>
        <p className="text-[15px] text-sec">Une application demande l'accès à votre compte Tribbae. Connectez-vous pour voir ce qu'elle demande.</p>
        <RouterLink
          to={`/connexion?${new URLSearchParams({ next: "/connecter-application" })}`}
          className="flex h-14 items-center justify-center rounded-[18px] bg-terracotta text-base font-bold text-white hover:bg-terracotta-dark"
        >
          Me connecter
        </RouterLink>
      </div>
    );
  } else if (!info.data) {
    body = <PageSpinner />;
  } else {
    const { clientName, redirectHost, scopes } = info.data;
    body = (
      <div className="flex w-full max-w-md flex-col gap-4">
        <h1 className="font-display text-[32px] leading-[1.05] font-extrabold tracking-[-0.8px]">
          Autoriser {clientName || "cette application"} ?
        </h1>
        <p className="text-[15px] text-sec">
          Le site <strong className="text-ecorce">{redirectHost}</strong> demande l'accès au compte de{" "}
          <strong className="text-ecorce">{session.displayName}</strong>.
        </p>
        <div className="rounded-[22px] border border-line bg-card p-4">
          <p className="mb-2 text-[13px] font-bold tracking-wide text-sec uppercase">Elle pourra</p>
          <ul className="flex list-disc flex-col gap-1 pl-5 text-[15px] text-ecorce">
            {scopeLabels(scopes).map((label) => <li key={label}>{label}</li>)}
          </ul>
          <p className="mt-4 mb-2 text-[13px] font-bold tracking-wide text-sec uppercase">Elle ne pourra jamais</p>
          <ul className="flex list-disc flex-col gap-1 pl-5 text-[15px] text-ecorce-2">
            {NEVER_ALLOWED.map((label) => <li key={label}>{label}</li>)}
          </ul>
        </div>
        <p className="text-[13px] text-sec">
          Les idées ajoutées par l'application restent privées et sont marquées comme telles. L'accès dure au plus 90 jours.
          N'autorisez que les applications que vous avez vous-même connectées.
        </p>
        {error && <ErrorNote>{error}</ErrorNote>}
        <Button block loading={busy === "approve"} disabled={busy !== ""} onClick={() => decide(true)} className="!h-14 text-base">
          Autoriser
        </Button>
        <Button block variant="ghost" loading={busy === "deny"} disabled={busy !== ""} onClick={() => decide(false)} className="!h-14 text-base">
          Refuser
        </Button>
        <button type="button" onClick={() => clearSession()} className="text-center text-[13px] font-semibold text-terracotta">
          Ce n'est pas mon compte
        </button>
      </div>
    );
  }

  return (
    <AuthShell back="/">
      <Seo title="Connecter une application · Tribbae" noindex />
      {body}
    </AuthShell>
  );
}
