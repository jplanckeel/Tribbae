import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router";
import Seo from "../components/Seo";
import { ErrorNote, PageSpinner } from "../components/ui";
import { VerifyEmailNotice } from "../components/VerifyEmailBanner";
import * as api from "../lib/api";
import { useAsync } from "../lib/useAsync";
import { useSession } from "../lib/session";
import { AuthShell } from "./Welcome";

/** Jeton du lien reçu par email, placé après le # : jamais envoyé au serveur ni aux statistiques. */
function readToken(): string {
  return decodeURIComponent(window.location.hash.replace(/^#/, ""));
}

// Un lien ne sert qu'une fois : confirmé une seule fois par chargement de la
// page, même si l'effet est rejoué (StrictMode)
const verifications = new Map<string, Promise<boolean>>();

function verifyOnce(token: string): Promise<boolean> {
  let p = verifications.get(token);
  if (!p) {
    // Le lien peut être celui d'un autre compte que celui connecté : on relit le compte
    p = api.auth.verifyEmail(token).then(() =>
      api.reloadAccount().then(
        () => true,
        () => true, // confirmée quand même : le bandeau se mettra à jour au prochain retour
      ),
    );
    verifications.set(token, p);
  }
  return p;
}

/** « Confirmer mon adresse » : écran ouvert depuis le lien de l'email (bienvenue ou renvoi). */
export default function VerifyEmail() {
  const session = useSession();
  const [token] = useState(readToken);
  const result = useAsync(() => (token ? verifyOnce(token) : Promise.resolve(false)), [token]);

  // Le jeton ne reste ni dans la barre d'adresse ni dans l'historique
  useEffect(() => {
    if (window.location.hash) window.history.replaceState(window.history.state, "", window.location.pathname);
  }, []);

  const home = (
    <RouterLink
      to={session ? "/" : "/connexion"}
      className="flex h-14 items-center justify-center rounded-[18px] bg-terracotta text-base font-bold text-white transition-colors hover:bg-terracotta-dark"
    >
      {session ? "Ouvrir Tribbae" : "Me connecter"}
    </RouterLink>
  );

  return (
    <AuthShell back="/">
      <Seo title="Confirmer mon adresse · Tribbae" noindex />
      <div className="flex w-full max-w-md flex-col gap-4">
        {result.loading ? (
          <PageSpinner />
        ) : result.data ? (
          <>
            <h1 className="font-display text-[32px] leading-none font-extrabold tracking-[-0.8px]">Adresse confirmée !</h1>
            <p className="text-[15px] text-sec">Merci. Vous pouvez maintenant publier vos idées et inviter votre tribu autour du feu.</p>
            {home}
          </>
        ) : (
          <>
            <h1 className="font-display text-[32px] leading-none font-extrabold tracking-[-0.8px]">
              {token ? "Lien plus valable" : "Lien incomplet"}
            </h1>
            {result.error ? (
              <ErrorNote>{result.error.message}</ErrorNote>
            ) : (
              <p className="text-[15px] text-sec">Ouvrez le lien tel qu'il apparaît dans l'email.</p>
            )}
            {session ? <VerifyEmailNotice /> : <p className="text-[15px] text-sec">Connectez-vous pour en recevoir un nouveau.</p>}
            {home}
          </>
        )}
      </div>
    </AuthShell>
  );
}
