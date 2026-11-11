import { Link as RouterLink } from "react-router";
import LegalPage, { LegalSection, LegalValue } from "../components/LegalPage";
import { LEGAL } from "../lib/legal";

/**
 * Conditions générales d'utilisation. Texte de départ, à faire relire par un
 * juriste avant la publication (âge minimum, responsabilité, droit applicable).
 */
export default function Terms() {
  return (
    <LegalPage
      title="Conditions d'utilisation"
      path="/cgu"
      updated={LEGAL.termsUpdated}
      description="Les règles d'utilisation de Tribbae : compte, contenus publiés, modération, responsabilité."
    >
      <LegalSection title="1. Objet">
        <p>
          Tribbae (le site tribbae.fr et l'application Android) permet aux familles de garder leurs idées (sorties, activités,
          recettes, cadeaux, lectures), de les ranger en dossiers, de les partager avec leur tribu et, si elles le
          souhaitent, de les publier pour la communauté. Ces conditions s'appliquent à toute personne qui utilise Tribbae.
          Elles sont proposées par <LegalValue>{LEGAL.editorName}</LegalValue> (l'« éditeur »).
        </p>
      </LegalSection>
      <LegalSection title="2. Compte">
        <p>
          Explorer les idées publiques ne demande pas de compte. Pour enregistrer, publier, commenter ou aimer, il faut un
          compte, réservé aux personnes majeures. Vous êtes responsable de votre mot de passe et de ce qui est fait avec
          votre compte. Les informations sur vos enfants (prénom, date de naissance) ne sont visibles que de votre tribu.
        </p>
      </LegalSection>
      <LegalSection title="3. Vos contenus">
        <p>
          Vos idées sont privées par défaut : elles ne sont vues que par vous, votre tribu et les proches invités sur un
          dossier. Ce que vous publiez dans la communauté devient visible de tous, y compris sans compte.
        </p>
        <p>
          Vous restez propriétaire de vos idées, photos et commentaires. En les publiant, vous autorisez l'éditeur à les
          afficher, à les adapter à l'écran et à les montrer aux moteurs de recherche, gratuitement et tant qu'ils restent
          publiés. Vous garantissez avoir le droit de publier ce que vous partagez (photos, textes recopiés).
        </p>
      </LegalSection>
      <LegalSection title="4. Ce qui est interdit">
        <ul className="list-disc pl-5">
          <li>les contenus illégaux, violents, haineux, à caractère sexuel ou dangereux pour les enfants ;</li>
          <li>le harcèlement, les insultes, la diffusion d'informations personnelles sur autrui ;</li>
          <li>le spam, la publicité non sollicitée et les liens trompeurs ;</li>
          <li>l'usurpation d'identité et l'utilisation de contenus protégés sans autorisation ;</li>
          <li>toute tentative de perturber le service ou d'accéder aux données d'autrui.</li>
        </ul>
      </LegalSection>
      <LegalSection title="5. Signalement et modération">
        <p>
          Chacun peut signaler une idée, un commentaire ou un compte, et bloquer un compte pour ne plus voir ses contenus.
          L'éditeur examine les signalements et peut, sans préavis, retirer un contenu publié ou suspendre un compte qui ne
          respecte pas ces conditions. Pour contester une décision, écrivez à{" "}
          <a href={`mailto:${LEGAL.contact}`} className="font-semibold text-link">{LEGAL.contact}</a>.
        </p>
      </LegalSection>
      <LegalSection title="6. Idées proposées par l'IA">
        <p>
          Les idées générées par l'intelligence artificielle sont des suggestions. Vérifiez les informations pratiques
          (horaires, prix, adresses, âges) avant de vous y fier.
        </p>
      </LegalSection>
      <LegalSection title="7. Responsabilité">
        <p>
          Tribbae est fourni tel quel. L'éditeur fait de son mieux pour qu'il soit disponible et fiable, sans pouvoir le
          garantir. Les liens mènent vers des sites tiers dont l'éditeur ne contrôle pas le contenu. Chaque personne est
          responsable de ce qu'elle publie.
        </p>
      </LegalSection>
      <LegalSection title="8. Fin d'utilisation">
        <p>
          Vous pouvez supprimer votre compte à tout moment depuis les réglages : vos idées, dossiers, photos et
          commentaires sont alors effacés. Le détail figure dans la{" "}
          <RouterLink to="/confidentialite" className="font-semibold text-link">politique de confidentialité</RouterLink>.
        </p>
      </LegalSection>
      <LegalSection title="9. Évolution et droit applicable">
        <p>
          Ces conditions peuvent évoluer ; la date de mise à jour figure en haut de cette page. Elles sont soumises au droit
          français. En cas de litige, une solution amiable sera recherchée avant toute action.
        </p>
      </LegalSection>
    </LegalPage>
  );
}
