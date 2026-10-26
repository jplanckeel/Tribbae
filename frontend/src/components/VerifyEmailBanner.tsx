import { useState } from "react";
import * as api from "../lib/api";
import { useSession } from "../lib/session";
import { useFeedback } from "./feedback";
import Icon from "./Icon";

/**
 * Bandeau « Confirmez votre adresse » : tant que l'adresse du compte n'est pas
 * confirmée, publier et inviter sont refusés par le serveur. Le lien est dans
 * l'email de bienvenue ; « Renvoyer l'email » en envoie un nouveau.
 */
export default function VerifyEmailBanner() {
  const session = useSession();
  if (!session || session.emailVerified) return null;
  return (
    <div className="mx-auto max-w-[1200px] px-4 pt-3 md:px-6">
      <VerifyEmailNotice />
    </div>
  );
}

/** Le bandeau seul, sans marges de page (aussi sur l'écran d'un lien expiré). */
export function VerifyEmailNotice() {
  const session = useSession();
  const { toast } = useFeedback();
  const [state, setState] = useState<"idle" | "sending" | "sent">("idle");
  if (!session || session.emailVerified) return null;

  const resend = async () => {
    setState("sending");
    try {
      const r = await api.auth.resendVerification();
      if (r.emailVerified) {
        api.markEmailVerified();
        toast("Votre adresse est déjà confirmée.");
        return;
      }
      setState("sent");
      toast("Email envoyé : ouvrez le lien qu'il contient.");
    } catch (err) {
      setState("idle");
      toast((err as Error).message, "error");
    }
  };

  return (
    <div role="status" className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-[18px] border border-line bg-peach px-4 py-3">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-[12px] bg-idee-bg text-idee-ink">
        <Icon name="mail" size={18} />
      </span>
      <p className="min-w-0 flex-1 text-sm text-ecorce">
        <strong className="font-bold">Confirmez votre adresse email.</strong>{" "}
        <span className="text-sec">Ouvrez le lien que nous vous avons envoyé pour publier vos idées et inviter votre tribu.</span>
      </p>
      <button
        type="button"
        onClick={resend}
        disabled={state !== "idle"}
        className="h-10 shrink-0 rounded-[14px] px-3 text-sm font-bold text-link transition-colors hover:bg-sable-2 disabled:opacity-60"
      >
        {state === "sent" ? "Email envoyé" : state === "sending" ? "Envoi…" : "Renvoyer l'email"}
      </button>
    </div>
  );
}
