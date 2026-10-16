import { useNavigate } from "react-router";
import Icon, { type IconName } from "../components/Icon";
import { Page } from "../components/Shell";

const SECTIONS: { title: string; icon: IconName; items: [string, string][] }[] = [
  {
    title: "Démarrage",
    icon: "tipi",
    items: [
      ["Comment créer ma tribu ?", "Depuis l'écran de bienvenue, touchez « Créer ma tribu », puis renseignez un nom, votre email et un mot de passe d'au moins 8 caractères."],
      ["Puis-je explorer sans compte ?", "Oui : l'Explorer, les idées et dossiers publics ainsi que les liens de partage sont consultables sans compte. Il en faut un pour enregistrer, aimer ou commenter."],
      ["Mes idées sont-elles synchronisées entre le téléphone et le web ?", "Oui, une fois connecté avec le même compte, l'application Android et le site affichent les mêmes idées et dossiers."],
    ],
  },
  {
    title: "Idées & dossiers",
    icon: "idee",
    items: [
      ["Comment ajouter une idée ?", "Touchez le losange « + » en bas de l'écran (ou « Ajouter » sur ordinateur). Collez un lien : le titre, l'image et la description sont récupérés automatiquement. Vous pouvez aussi partir d'une catégorie."],
      ["Quelles sont les catégories ?", "Activité, Cadeau, Recette, Sortie, Idée, Livre et Déco. Chacune a sa couleur pour être repérée d'un coup d'œil."],
      ["Comment ranger mes idées ?", "Dans « Mes idées », créez un dossier (nom, couleur, icône), puis choisissez-le dans le formulaire d'une idée. Les idées sans dossier restent dans « Non rangées »."],
      ["À quoi servent les rappels ?", "Donnez une date à une sortie ou une activité et activez « Me le rappeler la veille » : l'application Android vous prévient, et la cloche de l'accueil liste ce qui arrive."],
      ["Comment fonctionne la liste de courses ?", "Ajoutez les ingrédients de vos recettes, puis sélectionnez les recettes de la semaine dans « Liste de courses » : les ingrédients sont regroupés et se cochent au fil des rayons."],
    ],
  },
  {
    title: "Partage & communauté",
    icon: "tribe",
    items: [
      ["Comment inviter un proche ?", "Ouvrez un dossier puis « Inviter » : créez un lien de partage (lecture sans compte) ou ajoutez un proche par email pour qu'il puisse ajouter ses idées."],
      ["Comment rendre une idée publique ?", "Dans le formulaire de l'idée, activez « Partager avec la communauté ». Elle apparaît alors dans l'Explorer. Un dossier peut aussi être rendu public."],
      ["Comment garder une idée de la communauté ?", "Sur sa fiche, touchez « Enregistrer dans mes idées » : une copie privée est ajoutée à votre tribu."],
      ["Comment suivre une tribu ?", "Sur la fiche d'une idée publique, touchez « Suivre » à côté du nom de la tribu. Retrouvez-les dans « Ma tribu » > « Tribus suivies »."],
    ],
  },
  {
    title: "IA",
    icon: "sparkles",
    items: [
      ["Comment fonctionne la génération d'idées ?", "Décrivez ce que vous cherchez (« sorties nature pour des 3–8 ans »). L'IA cherche sur le web et propose des idées que vous choisissez avant de les ranger dans un dossier."],
      ["Est-ce gratuit ?", "Oui, la génération est incluse ; elle demande d'être connecté. Elle peut prendre jusqu'à une minute."],
    ],
  },
  {
    title: "Compte & confidentialité",
    icon: "lock",
    items: [
      ["Mes idées privées sont-elles visibles ?", "Non. Seuls vous et les membres invités dans un dossier les voient. Les échanges sont chiffrés (HTTPS)."],
      ["Comment changer mon mot de passe ou supprimer mon compte ?", "Écrivez-nous à support@tribbae.com. La suppression du compte efface définitivement vos données."],
    ],
  },
];

export default function Faq() {
  const navigate = useNavigate();
  return (
    <Page className="max-w-3xl">
      <title>Aide & FAQ · Tribbae</title>
      <div className="flex items-center gap-3">
        <button type="button" onClick={() => navigate(-1)} aria-label="Retour" className="flex h-11 w-11 items-center justify-center rounded-full bg-white shadow-soft hover:bg-sable-2">
          <Icon name="chevronLeft" size={22} />
        </button>
        <h1 className="font-display text-[28px] font-extrabold tracking-[-0.6px]">Aide & FAQ</h1>
      </div>
      {SECTIONS.map((s) => (
        <section key={s.title} className="flex flex-col gap-2.5">
          <h2 className="flex items-center gap-2 text-lg font-bold">
            <Icon name={s.icon} size={20} className="text-terracotta" />
            {s.title}
          </h2>
          <div className="flex flex-col divide-y divide-sable-2 overflow-hidden rounded-l bg-white">
            {s.items.map(([q, a]) => (
              <details key={q} className="group">
                <summary className="flex cursor-pointer list-none items-center gap-3 px-4 py-3.5 text-[15px] font-semibold [&::-webkit-details-marker]:hidden">
                  <span className="flex-1">{q}</span>
                  <Icon name="chevronDown" size={18} className="text-muted transition-transform group-open:rotate-180" />
                </summary>
                <p className="px-4 pb-4 text-[15px] leading-relaxed text-ecorce-2">{a}</p>
              </details>
            ))}
          </div>
        </section>
      ))}
      <p className="text-center text-sm text-sec">
        Une autre question ? <a href="mailto:support@tribbae.com" className="font-semibold text-terracotta">support@tribbae.com</a>
      </p>
    </Page>
  );
}
