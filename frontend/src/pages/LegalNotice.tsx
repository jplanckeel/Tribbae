import { Link as RouterLink } from "react-router";
import LegalPage, { LegalSection, LegalValue } from "../components/LegalPage";
import { LEGAL } from "../lib/legal";

/** Mentions légales (loi pour la confiance dans l'économie numérique, art. 6 III). */
export default function LegalNotice() {
  return (
    <LegalPage title="Mentions légales" path="/mentions-legales" description="Éditeur, directeur de la publication et hébergeur de Tribbae.">
      <LegalSection title="Éditeur">
        <p><LegalValue>{LEGAL.editorName}</LegalValue></p>
        <p><LegalValue>{LEGAL.editorAddress}</LegalValue></p>
        <p><LegalValue>{LEGAL.editorStatus}</LegalValue></p>
        <p>Contact : <a href={`mailto:${LEGAL.contact}`} className="font-semibold text-link">{LEGAL.contact}</a></p>
      </LegalSection>
      <LegalSection title="Directeur de la publication">
        <p><LegalValue>{LEGAL.publicationDirector}</LegalValue></p>
      </LegalSection>
      <LegalSection title="Hébergeur">
        <p><LegalValue>{LEGAL.hostName}</LegalValue></p>
        <p><LegalValue>{LEGAL.hostAddress}</LegalValue></p>
        <p>Pays : <LegalValue>{LEGAL.hostCountry}</LegalValue></p>
      </LegalSection>
      <LegalSection title="Propriété intellectuelle">
        <p>
          Le nom Tribbae, son logo, ses illustrations et l'application sont la propriété de l'éditeur. Les idées, photos et
          commentaires restent ceux de leurs auteurs, dans les conditions des{" "}
          <RouterLink to="/cgu" className="font-semibold text-link">conditions d'utilisation</RouterLink>.
        </p>
      </LegalSection>
      <LegalSection title="Données personnelles">
        <p>
          Ce que Tribbae conserve et vos droits sont décrits dans la{" "}
          <RouterLink to="/confidentialite" className="font-semibold text-link">politique de confidentialité</RouterLink>.
        </p>
      </LegalSection>
    </LegalPage>
  );
}
