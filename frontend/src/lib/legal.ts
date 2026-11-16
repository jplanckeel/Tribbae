/**
 * Identité légale de Tribbae, reprise par les mentions légales, les CGU et la
 * page Confidentialité (loi pour la confiance dans l'économie numérique, art. 6
 * III ; RGPD art. 13). L'éditeur est un particulier qui use de l'anonymat prévu
 * par l'art. 6 III-2 : son identité est communiquée à l'hébergeur, le public ne
 * voit que l'hébergeur et l'adresse de contact. Ne jamais écrire ici de nom
 * civil ni d'adresse postale de l'éditeur (dépôt public). Tant qu'un champ
 * commence par « [à compléter », il s'affiche tel quel, en évidence.
 */
export const LEGAL = {
  editorName: "Éditeur non professionnel (identité communiquée à l'hébergeur)",
  editorAddress: "Adresse communiquée à l'hébergeur conformément à l'article 6 III-2 de la LCEN",
  editorStatus: "Particulier, sans activité commerciale. Tribbae est gratuit et ne contient pas de publicité.",
  publicationDirector: "Éditeur non professionnel",
  // Entité contractante d'Hostinger pour les clients de l'Union européenne
  // (conditions générales d'Hostinger, section 2)
  hostName: "Hostinger International Ltd",
  hostAddress: "61 Lordou Vironos Street, 6023 Larnaca, Chypre",
  hostPhone: "[à compléter : téléphone de l'hébergeur]",
  hostCountry: "France",
  contact: "contact@tribbae.fr",
  termsUpdated: "16 novembre 2026",
};

/** Vrai tant qu'un texte légal n'a pas été complété : il est alors mis en évidence. */
export const isPlaceholder = (text: string) => text.startsWith("[à compléter");
