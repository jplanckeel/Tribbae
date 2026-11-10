/**
 * Identité légale de Tribbae, reprise par les mentions légales, les CGU et la
 * page Confidentialité. À COMPLÉTER AVANT PUBLICATION (loi pour la confiance
 * dans l'économie numérique, art. 6 III ; RGPD art. 13) : tant qu'un champ
 * commence par « [à compléter », il s'affiche tel quel, en évidence.
 */
export const LEGAL = {
  editorName: "[à compléter : nom ou raison sociale de l'éditeur]",
  editorAddress: "[à compléter : adresse postale de l'éditeur]",
  editorStatus: "[à compléter : statut (particulier, entreprise individuelle, société…) et, le cas échéant, numéro SIREN]",
  publicationDirector: "[à compléter : nom du directeur ou de la directrice de la publication]",
  hostName: "[à compléter : nom de l'hébergeur]",
  hostAddress: "[à compléter : adresse et téléphone de l'hébergeur]",
  hostCountry: "[à compléter : pays où sont hébergées les données]",
  contact: "contact@tribbae.fr",
  termsUpdated: "10 novembre 2026",
};

/** Vrai tant qu'un texte légal n'a pas été complété : il est alors mis en évidence. */
export const isPlaceholder = (text: string) => text.startsWith("[à compléter");
