import { useEffect, useState, type FormEvent } from "react";
import { Link as RouterLink, useNavigate, useSearchParams } from "react-router";
import Icon from "../components/Icon";
import Seo from "../components/Seo";
import { Button, ErrorNote, Field } from "../components/ui";
import * as api from "../lib/api";
import { setSession } from "../lib/session";
import { AuthShell } from "./Welcome";

/** « Mot de passe oublié » : demande d'un lien de réinitialisation par email. */
export function ForgotPassword() {
  const [params] = useSearchParams();
  const [email, setEmail] = useState(params.get("email") ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [sentTo, setSentTo] = useState("");

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api.auth.forgotPassword(email.trim());
      setSentTo(email.trim());
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell back="/connexion">
      <Seo title="Mot de passe oublié · Tribbae" noindex />
      {sentTo ? (
        <div className="flex w-full max-w-md flex-col gap-4">
          <span className="flex h-16 w-16 items-center justify-center rounded-[18px] bg-[var(--color-activite-bg)] text-[var(--color-activite-ink)]">
            <Icon name="send" size={30} />
          </span>
          <h1 className="font-display text-[32px] leading-none font-extrabold tracking-[-0.8px]">Regardez vos emails</h1>
          <p className="text-[15px] text-sec">
            Si un compte Tribbae utilise <strong className="text-ecorce">{sentTo}</strong>, un lien pour choisir un nouveau
            mot de passe vient d'y être envoyé. Il est valable une heure.
          </p>
          <p className="text-[13px] text-sec">Rien reçu ? Regardez dans les indésirables, ou vérifiez l'adresse et recommencez dans une minute.</p>
          <Button type="button" variant="secondary" block onClick={() => setSentTo("")}>
            Changer d'adresse
          </Button>
          <RouterLink to="/connexion" className="text-center text-sm font-semibold text-terracotta hover:text-terracotta-dark">
            Retour à la connexion
          </RouterLink>
        </div>
      ) : (
        <form onSubmit={submit} className="flex w-full max-w-md flex-col gap-4" noValidate>
          <div className="flex flex-col gap-1">
            <h1 className="font-display text-[32px] leading-none font-extrabold tracking-[-0.8px]">Mot de passe oublié</h1>
            <p className="text-[15px] text-sec">Indiquez l'adresse de votre compte : nous vous enverrons un lien pour en choisir un nouveau.</p>
          </div>
          <Field
            label="Email"
            type="email"
            autoComplete="email"
            inputMode="email"
            required
            placeholder="vous@exemple.fr"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          {error && <ErrorNote>{error}</ErrorNote>}
          <Button type="submit" loading={busy} block className="!h-14 text-base" disabled={!email.trim()}>
            Recevoir le lien
          </Button>
        </form>
      )}
    </AuthShell>
  );
}

/** Jeton du lien reçu par email, placé après le # : jamais envoyé au serveur ni aux statistiques. */
function readToken(): string {
  return decodeURIComponent(window.location.hash.replace(/^#/, ""));
}

/** « Nouveau mot de passe » : écran ouvert depuis le lien de l'email. */
export function NewPassword() {
  const navigate = useNavigate();
  const [token] = useState(readToken);
  const [password, setPassword] = useState("");
  const [showPwd, setShowPwd] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  // Le jeton ne reste ni dans la barre d'adresse ni dans l'historique
  useEffect(() => {
    if (window.location.hash) window.history.replaceState(window.history.state, "", window.location.pathname);
  }, []);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (password.length < 8) return setError("Le mot de passe doit contenir au moins 8 caractères.");
    setBusy(true);
    setError("");
    try {
      const r = await api.auth.resetPassword(token, password);
      setSession({ token: r.token, userId: r.userId, displayName: r.displayName ?? "", isAdmin: !!r.isAdmin, emailVerified: !!r.emailVerified });
      navigate("/", { replace: true });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell back="/connexion">
      <Seo title="Nouveau mot de passe · Tribbae" noindex />
      {!token ? (
        <div className="flex w-full max-w-md flex-col gap-4">
          <h1 className="font-display text-[32px] leading-none font-extrabold tracking-[-0.8px]">Lien incomplet</h1>
          <p className="text-[15px] text-sec">Ouvrez le lien tel qu'il apparaît dans l'email, ou demandez-en un nouveau.</p>
          <RouterLink to="/mot-de-passe/oublie" className="text-center text-sm font-semibold text-terracotta hover:text-terracotta-dark">
            Demander un nouveau lien
          </RouterLink>
        </div>
      ) : (
        <form onSubmit={submit} className="flex w-full max-w-md flex-col gap-4" noValidate>
          <div className="flex flex-col gap-1">
            <h1 className="font-display text-[32px] leading-none font-extrabold tracking-[-0.8px]">Nouveau mot de passe</h1>
            <p className="text-[15px] text-sec">Choisissez-le, puis vous serez connecté. Vos autres appareils devront se reconnecter.</p>
          </div>
          <div className="relative">
            <Field
              label="Nouveau mot de passe"
              type={showPwd ? "text" : "password"}
              autoComplete="new-password"
              required
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              hint="8 caractères minimum"
            />
            <button
              type="button"
              onClick={() => setShowPwd((v) => !v)}
              aria-label={showPwd ? "Masquer le mot de passe" : "Afficher le mot de passe"}
              aria-pressed={showPwd}
              className="absolute top-[34px] right-2 flex h-10 w-10 items-center justify-center rounded-small text-muted hover:text-ecorce"
            >
              <Icon name="eye" size={20} />
            </button>
          </div>
          {error && (
            <>
              <ErrorNote>{error}</ErrorNote>
              {/n'est plus valable/.test(error) && (
                <RouterLink to="/mot-de-passe/oublie" className="text-center text-sm font-semibold text-terracotta hover:text-terracotta-dark">
                  Demander un nouveau lien
                </RouterLink>
              )}
            </>
          )}
          <Button type="submit" loading={busy} block className="!h-14 text-base" disabled={!password}>
            Enregistrer et me connecter
          </Button>
        </form>
      )}
    </AuthShell>
  );
}
