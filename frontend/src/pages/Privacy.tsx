import { useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router";
import Icon from "../components/Icon";
import Seo from "../components/Seo";
import { Page } from "../components/Shell";
import { Toggle } from "../components/ui";

// À compléter avant publication : identité de l'éditeur (nom ou raison sociale,
// adresse) et hébergeur du serveur (nom, pays). Obligatoires (RGPD art. 13,
// loi pour la confiance dans l'économie numérique).
const EDITOR = "[à compléter : nom et adresse de l'éditeur de Tribbae]";
const HOST = "[à compléter : hébergeur du serveur et pays]";
const CONTACT = "contact@tribbae.fr";
const UPDATED = "20 octobre 2026";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-2 rounded-large bg-card px-5 py-4 text-[15px] leading-relaxed text-ecorce-2">
      <h2 className="font-display text-lg font-bold text-ecorce">{title}</h2>
      {children}
    </section>
  );
}

// Clé lue par le script Umami : présente, plus rien n'est envoyé depuis ce navigateur
const OPT_OUT_KEY = "umami.disabled";

function readOptOut(): boolean {
  try {
    return localStorage.getItem(OPT_OUT_KEY) !== null;
  } catch {
    return false;
  }
}

/** Droit d'opposition à la mesure d'audience, mémorisé dans ce navigateur. */
function AudienceChoice() {
  const [optedOut, setOptedOut] = useState(readOptOut);
  const change = (measure: boolean) => {
    try {
      if (measure) localStorage.removeItem(OPT_OUT_KEY);
      else localStorage.setItem(OPT_OUT_KEY, "1");
      setOptedOut(!measure);
    } catch {
      // Stockage bloqué (navigation privée) : rien n'est mémorisé
    }
  };
  return (
    <div className="flex items-center justify-between gap-3 rounded-medium bg-sable px-4 py-3">
      <span className="text-[15px] font-semibold text-ecorce">
        {optedOut ? "Mes visites ne sont pas comptées" : "Compter mes visites (anonymement)"}
      </span>
      <Toggle checked={!optedOut} onChange={change} label="Compter mes visites" />
    </div>
  );
}

/** Politique de confidentialité (RGPD, articles 12 à 14). */
export default function Privacy() {
  const navigate = useNavigate();
  const mail = <a href={`mailto:${CONTACT}`} className="font-semibold text-terracotta">{CONTACT}</a>;
  return (
    <Page className="max-w-3xl">
      <Seo
        title="Confidentialité & données personnelles · Tribbae"
        description="Quelles données Tribbae conserve, pourquoi, qui les voit, combien de temps, et comment les télécharger ou supprimer votre compte."
        path="/confidentialite"
      />
      <div className="flex items-center gap-3">
        <button type="button" onClick={() => navigate(-1)} aria-label="Retour" className="flex h-11 w-11 items-center justify-center rounded-full bg-card shadow-soft hover:bg-sable-2">
          <Icon name="chevronLeft" size={22} />
        </button>
        <h1 className="font-display text-[28px] font-extrabold tracking-[-0.6px]">Confidentialité</h1>
      </div>
      <p className="text-[15px] text-sec">
        En bref : vos idées sont privées par défaut, rien n'est vendu ni utilisé pour de la publicité, aucun cookie
        n'est déposé, et vous pouvez à tout moment télécharger vos données ou supprimer votre compte. Mise à jour le {UPDATED}.
      </p>

      <Section title="Qui est responsable de vos données ?">
        <p>{EDITOR}. Pour toute question ou demande sur vos données : {mail}.</p>
      </Section>

      <Section title="Ce que nous conservons">
        <ul className="list-disc space-y-1 pl-5">
          <li><strong>Votre compte</strong> : email, nom affiché, mot de passe (chiffré de façon irréversible, nous ne le connaissons pas), date d'inscription.</li>
          <li><strong>Ce que vous créez</strong> : idées (titre, lien, description, image, tags, âge, prix, lieu, date, ingrédients), dossiers, commentaires, « j'aime », tribus suivies. Les photos que vous ajoutez sont gardées sur notre serveur, réduites et débarrassées de leurs métadonnées (position GPS, appareil) ; seuls ceux qui voient l'idée en ont l'adresse, et une photo qu'aucune idée n'utilise plus est effacée sous deux jours.</li>
          <li><strong>Votre tribu</strong> : son nom et ses membres.</li>
          <li><strong>Les profils enfants</strong> : prénom et date de naissance, saisis par vous, uniquement pour proposer des idées adaptées à leur âge. Ils ne sont jamais publics.</li>
        </ul>
        <p>
          Les demandes faites à l'IA sont traitées par un modèle installé sur notre propre serveur : elles ne sont
          transmises à aucun service extérieur, ni conservées, ni écrites dans les journaux.
        </p>
      </Section>

      <Section title="Pourquoi, et sur quelle base">
        <ul className="list-disc space-y-1 pl-5">
          <li>Faire fonctionner le service que vous avez demandé en créant un compte : ranger, synchroniser et partager vos idées (exécution du contrat, art. 6.1.b du RGPD).</li>
          <li>Protéger les comptes contre les abus, par exemple en limitant les tentatives de connexion (intérêt légitime, art. 6.1.f). L'adresse IP sert alors quelques minutes en mémoire, sans être enregistrée.</li>
        </ul>
        <p>Aucune publicité, aucun profilage, aucune revente.</p>
      </Section>

      <Section title="Qui voit vos idées">
        <ul className="list-disc space-y-1 pl-5">
          <li>Par défaut, <strong>vous seul</strong>.</li>
          <li>Les membres de votre tribu. On ne rejoint une tribu qu'en acceptant l'invitation reçue par email, et votre propre contenu n'y entre qu'après votre confirmation.</li>
          <li>Les personnes que vous invitez dans un dossier, ou qui reçoivent son lien de partage.</li>
          <li>Tout le monde, y compris les moteurs de recherche, seulement pour les idées et dossiers que <strong>vous</strong> choisissez de rendre publics, avec votre nom affiché. Vous pouvez les rendre privés à tout moment.</li>
        </ul>
      </Section>

      <Section title="Hébergement et services tiers">
        <p>Les données sont hébergées chez {HOST}. Nous n'utilisons aucun outil de publicité ni de réseau social.</p>
        <p>
          Quand vous ajoutez un lien, notre serveur lit la page pour en récupérer le titre et l'image : le site visité
          voit notre serveur, pas vous. En revanche, les images des idées s'affichent depuis le site d'origine, qui
          reçoit alors, comme pour toute image du web, l'adresse IP de votre appareil (sauf pour vos photos, servies par
          Tribbae).
        </p>
        <p>
          Les emails liés à votre compte (bienvenue, ajout à une tribu, confirmation de suppression) sont envoyés par{" "}
          <a href="https://www.brevo.com/fr/legal/privacypolicy/" className="font-semibold text-terracotta" rel="noopener" target="_blank">Brevo</a>{" "}
          (France), qui reçoit pour cela votre adresse email, votre nom et le contenu du message. Nous n'envoyons ni
          publicité ni lettre d'information.
        </p>
      </Section>

      <Section title="Mesure d'audience">
        <p>
          Sur le site web, nous comptons les visites avec{" "}
          <a href="https://umami.is/privacy" className="font-semibold text-terracotta" rel="noopener" target="_blank">Umami</a>{" "}
          pour savoir quelles pages sont utiles : écran visité, site d'où vous venez, type d'appareil, navigateur, langue
          et pays. Umami ne dépose aucun cookie, ne vous suit pas d'un site à l'autre et ne conserve pas votre adresse IP ;
          les statistiques ne servent qu'à nous, de façon agrégée.
        </p>
        <p>
          Ne sont jamais envoyés : le contenu de vos idées (les titres des pages sont remplacés par le nom de l'écran),
          vos recherches, ni les adresses secrètes des liens de partage. Si votre navigateur demande à ne pas être
          suivi (« Do Not Track »), rien n'est mesuré. L'application Android ne mesure rien.
        </p>
        <AudienceChoice />
      </Section>

      <Section title="Cookies et stockage local">
        <p>
          Tribbae ne dépose aucun cookie. Votre navigateur ou l'application garde seulement votre jeton de connexion
          (valable 30 jours) et votre liste de courses, pour que vous restiez connecté : c'est strictement nécessaire au
          service et ne demande donc pas de consentement.
        </p>
      </Section>

      <Section title="Combien de temps">
        <p>
          Vos données sont conservées tant que votre compte existe. Quand vous le supprimez, elles sont effacées
          immédiatement et définitivement de la base.
        </p>
      </Section>

      <Section title="Vos droits">
        <ul className="list-disc space-y-1 pl-5">
          <li><strong>Accès et portabilité</strong> : « Ma tribu » › Paramètres › « Télécharger mes données » (fichier JSON).</li>
          <li><strong>Effacement</strong> : « Ma tribu » › Paramètres › « Supprimer mon compte ».</li>
          <li><strong>Rectification</strong> : modifiez vos idées, dossiers et le nom de la tribu dans l'application ; pour votre email, votre nom ou votre mot de passe, écrivez-nous.</li>
          <li><strong>Opposition, limitation</strong>, ou toute autre demande : {mail}. Nous répondons sous un mois.</li>
        </ul>
        <p>
          Si vous estimez que vos droits ne sont pas respectés, vous pouvez saisir la{" "}
          <a href="https://www.cnil.fr/fr/plaintes" className="font-semibold text-terracotta" rel="noopener" target="_blank">CNIL</a>.
        </p>
      </Section>

      <Section title="Sécurité">
        <p>
          Les échanges sont chiffrés (HTTPS), les mots de passe chiffrés de façon irréversible, et chaque accès à une
          idée ou un dossier est vérifié par le serveur.
        </p>
      </Section>

      <p className="text-center text-sm text-sec">
        Une question ? {mail} · <RouterLink to="/aide" className="font-semibold text-terracotta">Aide & FAQ</RouterLink>
      </p>
    </Page>
  );
}
