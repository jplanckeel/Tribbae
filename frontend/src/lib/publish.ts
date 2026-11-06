/** Confirmation avant de publier : mêmes textes que data/PublishConfirm.kt (PublishConfirmTest). */
const NO_NAMES = "Les prénoms des enfants ne sont jamais publiés.";

export const PUBLISH_IDEA = {
  title: "Publier cette idée ?",
  text: `Votre idée sera visible par toutes les familles dans Explorer. ${NO_NAMES}`,
  confirmLabel: "Publier",
};

export function publishFolder(ideas: number) {
  const what = ideas > 1 ? `Les ${ideas} idées de ce dossier seront visibles` : ideas === 1 ? "L'idée de ce dossier sera visible" : "Ce dossier sera visible";
  return { title: "Publier ce dossier ?", text: `${what} par toutes les familles dans Explorer. ${NO_NAMES}`, confirmLabel: "Publier" };
}
