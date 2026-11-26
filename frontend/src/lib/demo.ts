// Famille fictive de l'aperçu interactif de l'accueil visiteur (components/DemoPhone)

export const DEMO_TRIBE = [
  { name: "Léa", role: "Maman · moi", home: true, color: "var(--av-0-bg)", ink: "var(--av-0-ink)" },
  { name: "Thomas", role: "Papa", home: true, color: "var(--av-2-bg)", ink: "var(--av-2-ink)" },
  { name: "Mamie", role: "Grand-mère", home: true, color: "var(--av-1-bg)", ink: "var(--av-1-ink)" },
  { name: "Paul", role: "Tonton · voit les dossiers partagés, sans l'appli", home: false, color: "var(--av-3-bg)", ink: "var(--av-3-ink)" },
];
export type Member = (typeof DEMO_TRIBE)[number];
const [LEA, THOMAS, MAMIE, PAUL] = DEMO_TRIBE;

export interface Ingredient { name: string; qty: string; aisle: string }
export interface Idea {
  id: string;
  title: string;
  category: string;
  folder?: string;
  by: Member | string;
  tags: string[];
  text: string;
  likes: number;
  done?: string;
  ingredients?: Ingredient[];
  comment?: { by: Member; text: string };
  reserved?: boolean;
}

export interface DemoFolder { id: string; name: string; color: string; icon: string }
// Couleurs et icônes des dossiers de l'appli (FOLDER_COLORS, FOLDER_ICONS) ; le nom choisit le dessin
export const FOLDERS: DemoFolder[] = [
  { id: "ete", name: "Vacances d'été", color: "BLUE", icon: "BEACH" },
  { id: "jules", name: "Anniversaire de Jules", color: "ORANGE", icon: "CAKE" },
  { id: "dimanche", name: "Recettes du dimanche", color: "GREEN", icon: "RESTAURANT" },
];

/** La prochaine sortie datée, en tête de l'accueil. */
export const EVENT = { id: "fraises", weekday: "Sam.", day: "6", meta: "10:00 · Ferme de Viltain" };
const ACT = "LINK_CATEGORY_ACTIVITE", SORTIE = "LINK_CATEGORY_EVENEMENT", LIVRE = "LINK_CATEGORY_LIVRE";
const CADEAU = "LINK_CATEGORY_CADEAU", RECETTE = "LINK_CATEGORY_RECETTE", DECO = "LINK_CATEGORY_DECORATION";

export const IDEAS: Idea[] = [
  { id: "paddle", title: "Paddle sur le lac d'Annecy", category: SORTIE, folder: "ete", by: THOMAS, tags: ["dès 6 ans", "25 € / h", "Annecy"], text: "Location à la plage d'Albigny, gilets fournis. Y aller tôt le matin, le lac est calme.", likes: 9, done: "Fait le 14 juillet", comment: { by: LEA, text: "Jules veut y retourner !" } },
  { id: "fraises", title: "Cueillette de fraises", category: SORTIE, folder: "ete", by: MAMIE, tags: ["dès 2 ans", "≈ 15 €", "Ferme de Viltain"], text: "Ouvert de mai à juillet. Prendre des boîtes et des bottes s'il a plu.", likes: 6 },
  { id: "cabane", title: "Cabane en carton géante", category: ACT, folder: "ete", by: LEA, tags: ["dès 4 ans", "Gratuit", "Intérieur"], text: "Deux grands cartons, du scotch et des feutres : une après-midi entière d'occupée, et une cabane qu'on garde une semaine.", likes: 14 },
  { id: "prince", title: "Le Petit Prince", category: LIVRE, folder: "jules", by: MAMIE, tags: ["dès 7 ans", "8 €"], text: "L'édition illustrée en grand format, pour la lire ensemble le soir.", likes: 5, comment: { by: THOMAS, text: "Il l'adorera, bonne idée Maman" } },
  { id: "velo", title: "Vélo 20 pouces", category: CADEAU, folder: "jules", by: THOMAS, tags: ["149 €", "Decathlon"], text: "Taille 20 pouces pour 6 à 9 ans. Couleur bleue de préférence.", likes: 3, reserved: true },
  { id: "echecs", title: "Jeu d'échecs enfant", category: CADEAU, folder: "jules", by: PAUL, tags: ["24 €", "dès 6 ans"], text: "Avec les pièces illustrées et le livret pour apprendre les règles.", likes: 2 },
  { id: "crepes", title: "Crêpes de la Chandeleur", category: RECETTE, folder: "dimanche", by: LEA, tags: ["6 pers.", "20 min"], text: "La pâte repose une heure. Une cuillère de beurre fondu dans la pâte, et elles n'attachent pas.", likes: 18, ingredients: [
    { name: "Farine", qty: "250 g", aisle: "Épicerie" }, { name: "Œufs", qty: "4", aisle: "Crèmerie" }, { name: "Lait", qty: "50 cl", aisle: "Crèmerie" }, { name: "Sucre", qty: "2 c. à soupe", aisle: "Épicerie" },
  ] },
  { id: "gratin", title: "Gratin dauphinois de Mamie", category: RECETTE, folder: "dimanche", by: MAMIE, tags: ["6 pers.", "1 h 15"], text: "Pas de fromage dans le vrai gratin dauphinois ! Une gousse d'ail frottée sur le plat.", likes: 11, ingredients: [
    { name: "Pommes de terre", qty: "1,2 kg", aisle: "Fruits et légumes" }, { name: "Crème fraîche", qty: "40 cl", aisle: "Crèmerie" }, { name: "Lait", qty: "40 cl", aisle: "Crèmerie" }, { name: "Ail", qty: "1 gousse", aisle: "Fruits et légumes" },
  ] },
  { id: "cookies", title: "Cookies aux pépites", category: RECETTE, folder: "dimanche", by: THOMAS, tags: ["20 cookies", "25 min"], text: "Sortir les cookies encore mous : ils durcissent en refroidissant.", likes: 7, ingredients: [
    { name: "Farine", qty: "200 g", aisle: "Épicerie" }, { name: "Beurre", qty: "125 g", aisle: "Crèmerie" }, { name: "Pépites de chocolat", qty: "100 g", aisle: "Épicerie" },
  ] },
  // Explorer : idées publiées par d'autres familles
  { id: "escape", title: "Escape game à la maison", category: ACT, by: "Famille Durand", tags: ["dès 8 ans", "Gratuit"], text: "Des énigmes imprimées cachées dans la maison, un cadenas à code sur la boîte du goûter.", likes: 42 },
  { id: "poterie", title: "Atelier poterie parent-enfant", category: ACT, by: "Famille Nguyen", tags: ["dès 5 ans", "30 €"], text: "Deux heures le samedi matin, on récupère les pièces cuites la semaine suivante.", likes: 27 },
  { id: "guirlande", title: "Guirlande en papier", category: DECO, by: "Famille Martin", tags: ["dès 3 ans", "Gratuit"], text: "Des bandes de papier coloré collées en anneaux : parfait pour un anniversaire.", likes: 19 },
  { id: "loup", title: "Le loup qui voulait changer de couleur", category: LIVRE, by: "Famille Leroy", tags: ["dès 3 ans", "6 €"], text: "Un album drôle pour apprendre les jours de la semaine.", likes: 23 },
];
export const EXPLORER = ["escape", "poterie", "guirlande", "loup", "crepes"];
export const NEW_IDEA: Idea = { id: "nouvelle", title: "Pique-nique au parc de la Tête d'Or", category: SORTIE, folder: "ete", by: LEA, tags: ["Tous âges", "Gratuit", "Lyon"], text: "Ajoutée à l'instant depuis un lien : Tribbae a récupéré le titre et le lieu.", likes: 0 };
