import { useState, type FormEvent } from "react";
import { Link as RouterLink, Navigate, useNavigate, useSearchParams } from "react-router";
import welcome from "../assets/welcome.jpg";
import Icon from "../components/Icon";
import { TipiMark } from "../components/Logo";
import { Button, ErrorNote, Field } from "../components/ui";
import * as api from "../lib/api";
import { safeNext } from "../lib/format";
import { setSession, useSession } from "../lib/session";

export default function Welcome() {
  const session = useSession();
  const [params] = useSearchParams();
  const next = params.get("next");
  if (session) return <Navigate to={safeNext(next)} replace />;
  const suffix = next ? `&next=${encodeURIComponent(next)}` : "";

  return (
    <div className="flex min-h-dvh flex-col bg-[#F7955A] md:flex-row">
      <title>Tribbae — Les idées de la tribu au même endroit</title>
      <div className="flex flex-1 items-end justify-center px-2.5 pt-10 md:items-center md:p-12">
        <img
          src={welcome}
          width={724}
          height={562}
          alt="Le tipi Tribbae entouré de la famille, d'un cadeau, d'une ampoule, de couverts et d'un ballon"
          className="block h-auto w-[370px] max-w-full md:w-[560px]"
        />
      </div>
      <div className="flex flex-col gap-3.5 rounded-t-[32px] bg-sable px-6 pt-[30px] pb-[34px] md:w-[480px] md:justify-center md:rounded-none md:px-12">
        <h1 className="font-display text-[38px] leading-none font-extrabold tracking-[-1px] md:text-5xl">Tribbae</h1>
        <p className="text-[17px] leading-[1.45] text-ecorce-2">
          Toutes les idées de la tribu au même endroit : sorties, recettes, cadeaux, activités.
        </p>
        <div className="mt-2 flex flex-col gap-2.5">
          <RouterLink
            to={`/connexion?mode=inscription${suffix}`}
            className="flex h-14 items-center justify-center rounded-m bg-terracotta text-base font-bold text-white transition-colors hover:bg-terracotta-dark"
          >
            Créer ma tribu
          </RouterLink>
          <RouterLink
            to={next ? `/connexion?next=${encodeURIComponent(next)}` : "/connexion"}
            className="flex h-14 items-center justify-center rounded-m border-2 border-ecorce text-base font-bold transition-colors hover:bg-ecorce/5"
          >
            J'ai déjà un compte
          </RouterLink>
        </div>
        <span className="text-center text-[13px] text-sec">Invité par un proche ? Ouvrez simplement son lien.</span>
        <RouterLink to="/explorer" className="flex items-center justify-center gap-1.5 text-sm font-semibold text-terracotta hover:text-terracotta-dark">
          <Icon name="compass" size={18} />
          Explorer sans compte
        </RouterLink>
      </div>
    </div>
  );
}

export function Auth() {
  const session = useSession();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const register = params.get("mode") === "inscription";
  const next = safeNext(params.get("next"));
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [showPwd, setShowPwd] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  if (session) return <Navigate to={next} replace />;

  const switchMode = () => {
    const p = new URLSearchParams(params);
    if (register) p.delete("mode");
    else p.set("mode", "inscription");
    setParams(p, { replace: true });
    setError("");
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (register && password.length < 8) return setError("Le mot de passe doit contenir au moins 8 caractères.");
    setBusy(true);
    setError("");
    try {
      if (register) {
        const r = await api.auth.register(email.trim(), password, name.trim());
        setSession({ token: r.token, userId: r.userId, displayName: name.trim(), isAdmin: !!r.isAdmin });
      } else {
        const r = await api.auth.login(email.trim(), password);
        setSession({ token: r.token, userId: r.userId, displayName: r.displayName ?? "", isAdmin: !!r.isAdmin });
      }
      navigate(next, { replace: true });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-dvh flex-col bg-braise">
      <title>{register ? "Créer ma tribu · Tribbae" : "Connexion · Tribbae"}</title>
      <div className="relative flex h-[200px] shrink-0 items-end justify-center overflow-hidden md:h-[240px]">
        <div aria-hidden="true" className="absolute -bottom-[170px] left-1/2 h-[360px] w-[360px] -translate-x-1/2 rounded-full bg-soleil" />
        <RouterLink to="/bienvenue" aria-label="Retour" className="absolute top-5 left-5 flex h-11 w-11 items-center justify-center rounded-full bg-white/60 hover:bg-white/80">
          <Icon name="chevronLeft" size={22} />
        </RouterLink>
        <span className="relative mb-0"><TipiMark size={120} stroke={1.8} /></span>
      </div>
      <div className="flex flex-1 justify-center rounded-t-[32px] bg-sable px-6 pt-8 pb-10">
        <form onSubmit={submit} className="flex w-full max-w-md flex-col gap-4" noValidate>
          <div className="flex flex-col gap-1">
            <h1 className="font-display text-[32px] leading-none font-extrabold tracking-[-0.8px]">
              {register ? "Créer ma tribu" : "Bon retour !"}
            </h1>
            <p className="text-[15px] text-sec">
              {register ? "Quelques secondes et votre tipi est monté." : "Retrouvez les idées de votre tribu."}
            </p>
          </div>

          {register && (
            <Field
              label="Nom de la tribu ou prénom"
              autoComplete="nickname"
              required
              maxLength={60}
              placeholder="Dupont"
              value={name}
              onChange={(e) => setName(e.target.value)}
              hint={name.trim() ? `On vous appellera « La tribu ${name.trim()} »` : undefined}
            />
          )}
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
          <div className="relative">
            <Field
              label="Mot de passe"
              type={showPwd ? "text" : "password"}
              autoComplete={register ? "new-password" : "current-password"}
              required
              minLength={register ? 8 : undefined}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              hint={register ? "8 caractères minimum" : undefined}
            />
            <button
              type="button"
              onClick={() => setShowPwd((v) => !v)}
              aria-label={showPwd ? "Masquer le mot de passe" : "Afficher le mot de passe"}
              aria-pressed={showPwd}
              className="absolute top-[34px] right-2 flex h-10 w-10 items-center justify-center rounded-s text-muted hover:text-ecorce"
            >
              <Icon name="eye" size={20} />
            </button>
          </div>

          {error && <ErrorNote>{error}</ErrorNote>}

          <Button type="submit" loading={busy} block className="!h-14 text-base" disabled={!email || !password || (register && !name.trim())}>
            {register ? "Créer ma tribu" : "Se connecter"}
          </Button>
          <button type="button" onClick={switchMode} className="text-center text-sm font-semibold text-terracotta hover:text-terracotta-dark">
            {register ? "J'ai déjà un compte" : "Pas encore de compte ? Créer ma tribu"}
          </button>
        </form>
      </div>
    </div>
  );
}
