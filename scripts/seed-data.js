import http from 'k6/http';
import { check, sleep } from 'k6';

// Configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const NUM_USERS = 15; // Augmenté pour plus de diversité
const NUM_FOLDERS_PER_USER = 4; // Plus de dossiers
const NUM_LINKS_PER_FOLDER = 6; // Plus de liens par dossier
const PUBLIC_FOLDER_RATIO = 0.8; // 80% de dossiers publics

// Données de test
const firstNames = ['Alice', 'Bob', 'Charlie', 'Diana', 'Emma', 'Frank', 'Grace', 'Henry', 'Iris', 'Jack'];
const lastNames = ['Martin', 'Dubois', 'Bernard', 'Thomas', 'Robert', 'Petit', 'Durand', 'Leroy', 'Moreau', 'Simon'];

const folderNames = [
  // Recettes
  'Recettes de famille',
  'Recettes rapides',
  'Recettes végétariennes',
  'Desserts faciles',
  'Cuisine du monde',
  'Recettes pour enfants',
  'Apéritifs et tapas',
  'Plats mijotés',
  // Cadeaux
  'Idées cadeaux Noël',
  'Cadeaux anniversaire',
  'Cadeaux pour bébé',
  'Cadeaux pour ados',
  'Cadeaux DIY',
  'Cadeaux écolos',
  // Activités
  'Activités week-end',
  'Activités pluvieuses',
  'Sorties en famille',
  'Activités créatives',
  'Sports et loisirs',
  'Balades nature',
  'Jeux pour enfants',
  // Autres
  'Restaurants à tester',
  'Voyages en Europe',
  'Bricolage maison',
  'Livres à lire',
  'Films à voir',
  'Sorties culturelles',
  'Idées déco',
  'Jardinage',
];

const folderTags = {
  // Recettes
  'Recettes de famille': ['recette', 'famille', 'cuisine'],
  'Recettes rapides': ['recette', 'rapide', 'cuisine'],
  'Recettes végétariennes': ['recette', 'végétarien', 'santé'],
  'Desserts faciles': ['recette', 'dessert', 'sucré'],
  'Cuisine du monde': ['recette', 'international', 'voyage'],
  'Recettes pour enfants': ['recette', 'enfants', 'facile'],
  'Apéritifs et tapas': ['recette', 'apéritif', 'convivial'],
  'Plats mijotés': ['recette', 'mijoté', 'hiver'],
  // Cadeaux
  'Idées cadeaux Noël': ['cadeau', 'noël', 'fêtes'],
  'Cadeaux anniversaire': ['cadeau', 'anniversaire', 'fête'],
  'Cadeaux pour bébé': ['cadeau', 'bébé', 'naissance'],
  'Cadeaux pour ados': ['cadeau', 'ado', 'jeune'],
  'Cadeaux DIY': ['cadeau', 'diy', 'fait-main'],
  'Cadeaux écolos': ['cadeau', 'écolo', 'durable'],
  // Activités
  'Activités week-end': ['activité', 'week-end', 'loisirs'],
  'Activités pluvieuses': ['activité', 'intérieur', 'enfants'],
  'Sorties en famille': ['activité', 'famille', 'sortie'],
  'Activités créatives': ['activité', 'créatif', 'diy'],
  'Sports et loisirs': ['activité', 'sport', 'extérieur'],
  'Balades nature': ['activité', 'nature', 'randonnée'],
  'Jeux pour enfants': ['jeux', 'enfants', 'activité'],
  // Autres
  'Restaurants à tester': ['restaurant', 'sortie', 'gastronomie'],
  'Voyages en Europe': ['voyage', 'europe', 'vacances'],
  'Bricolage maison': ['bricolage', 'diy', 'maison'],
  'Livres à lire': ['livre', 'lecture', 'culture'],
  'Films à voir': ['film', 'cinéma', 'culture'],
  'Sorties culturelles': ['culture', 'sortie', 'musée'],
  'Idées déco': ['décoration', 'maison', 'diy'],
  'Jardinage': ['jardinage', 'plantes', 'extérieur'],
};

const linkTemplates = {
  RECETTE: [
    { title: 'Gâteau au chocolat facile', description: 'Un délicieux gâteau moelleux', url: 'https://example.com/gateau-chocolat', ingredients: ['chocolat', 'farine', 'oeufs', 'sucre', 'beurre'] },
    { title: 'Quiche lorraine maison', description: 'La vraie recette traditionnelle', url: 'https://example.com/quiche-lorraine', ingredients: ['pâte brisée', 'lardons', 'crème', 'oeufs', 'gruyère'] },
    { title: 'Salade César', description: 'Fraîche et savoureuse', url: 'https://example.com/salade-cesar', ingredients: ['laitue', 'poulet', 'parmesan', 'croûtons', 'sauce césar'] },
    { title: 'Pâtes carbonara', description: 'Recette italienne authentique', url: 'https://example.com/carbonara', ingredients: ['pâtes', 'lardons', 'oeufs', 'parmesan', 'poivre'] },
    { title: 'Tarte aux pommes', description: 'Dessert classique et réconfortant', url: 'https://example.com/tarte-pommes', ingredients: ['pommes', 'pâte feuilletée', 'sucre', 'cannelle', 'beurre'] },
    { title: 'Lasagnes bolognaise', description: 'Plat familial généreux', url: 'https://example.com/lasagnes', ingredients: ['pâtes', 'viande hachée', 'tomates', 'béchamel', 'parmesan'] },
    { title: 'Crêpes sucrées', description: 'Pour la Chandeleur ou le goûter', url: 'https://example.com/crepes', ingredients: ['farine', 'lait', 'oeufs', 'sucre', 'beurre'] },
    { title: 'Poulet rôti aux herbes', description: 'Tendre et savoureux', url: 'https://example.com/poulet-roti', ingredients: ['poulet', 'herbes', 'citron', 'ail', 'huile'] },
    { title: 'Soupe de légumes', description: 'Réconfortante et saine', url: 'https://example.com/soupe-legumes', ingredients: ['carottes', 'poireaux', 'pommes de terre', 'bouillon', 'crème'] },
    { title: 'Tiramisu maison', description: 'Dessert italien onctueux', url: 'https://example.com/tiramisu', ingredients: ['mascarpone', 'café', 'biscuits', 'cacao', 'oeufs'] },
  ],
  CADEAU: [
    { title: 'Lego Creator Expert', description: 'Set de construction avancé', url: 'https://example.com/lego', price: '89.99€', ageRange: '10 ans' },
    { title: 'Livre "Le Petit Prince"', description: 'Classique de la littérature', url: 'https://example.com/petit-prince', price: '12.90€', ageRange: '8 ans' },
    { title: 'Coffret de peinture', description: 'Kit complet pour artistes en herbe', url: 'https://example.com/peinture', price: '34.99€', ageRange: '6 ans' },
    { title: 'Puzzle 1000 pièces', description: 'Paysage de montagne', url: 'https://example.com/puzzle', price: '19.99€', ageRange: '12 ans' },
    { title: 'Jeu de société Dobble', description: 'Jeu d\'observation rapide', url: 'https://example.com/dobble', price: '14.99€', ageRange: '6 ans' },
    { title: 'Trottinette électrique', description: 'Pour les déplacements urbains', url: 'https://example.com/trottinette', price: '299€', ageRange: '14 ans' },
    { title: 'Coffret LEGO Harry Potter', description: 'Château de Poudlard', url: 'https://example.com/lego-hp', price: '129€', ageRange: '9 ans' },
    { title: 'Tablette graphique', description: 'Pour dessiner numériquement', url: 'https://example.com/tablette', price: '79€', ageRange: '12 ans' },
    { title: 'Coffret de magie', description: '50 tours de magie', url: 'https://example.com/magie', price: '24.99€', ageRange: '8 ans' },
    { title: 'Drone avec caméra', description: 'Pour photos aériennes', url: 'https://example.com/drone', price: '149€', ageRange: '14 ans' },
  ],
  ACTIVITE: [
    { title: 'Parc Astérix', description: 'Parc d\'attractions familial', url: 'https://example.com/asterix', location: 'Plailly', price: '49€', ageRange: '3 ans' },
    { title: 'Musée du Louvre', description: 'Visite culturelle', url: 'https://example.com/louvre', location: 'Paris', price: '17€', ageRange: '8 ans' },
    { title: 'Accrobranche', description: 'Parcours dans les arbres', url: 'https://example.com/accrobranche', location: 'Fontainebleau', price: '25€', ageRange: '6 ans' },
    { title: 'Cinéma en famille', description: 'Dernier film d\'animation', url: 'https://example.com/cinema', location: 'Centre-ville', price: '9.50€', ageRange: '4 ans' },
    { title: 'Atelier cuisine enfants', description: 'Apprendre à cuisiner en s\'amusant', url: 'https://example.com/atelier-cuisine', location: 'Lyon', price: '35€', ageRange: '7 ans' },
    { title: 'Aquarium de Paris', description: 'Découverte du monde marin', url: 'https://example.com/aquarium', location: 'Paris', price: '22€', ageRange: '3 ans' },
    { title: 'Escape Game famille', description: 'Énigmes et aventure', url: 'https://example.com/escape', location: 'Centre-ville', price: '28€', ageRange: '10 ans' },
    { title: 'Parc zoologique', description: 'Rencontre avec les animaux', url: 'https://example.com/zoo', location: 'Vincennes', price: '19€', ageRange: '2 ans' },
    { title: 'Bowling', description: 'Partie en famille', url: 'https://example.com/bowling', location: 'Centre commercial', price: '15€', ageRange: '5 ans' },
    { title: 'Piscine à vagues', description: 'Parc aquatique', url: 'https://example.com/piscine', location: 'Banlieue', price: '18€', ageRange: '4 ans' },
  ],
  IDEE: [
    { title: 'Organiser un pique-nique', description: 'Sortie en plein air', url: 'https://example.com/pique-nique', location: 'Parc local' },
    { title: 'Soirée jeux de société', description: 'Moment convivial en famille', url: 'https://example.com/jeux-societe' },
    { title: 'Atelier bricolage DIY', description: 'Créer des objets déco', url: 'https://example.com/diy' },
    { title: 'Jardinage avec les enfants', description: 'Planter des légumes', url: 'https://example.com/jardinage' },
    { title: 'Soirée cinéma maison', description: 'Film + pop-corn', url: 'https://example.com/cinema-maison' },
    { title: 'Chasse au trésor', description: 'Jeu d\'aventure dans le quartier', url: 'https://example.com/chasse-tresor' },
    { title: 'Atelier pâtisserie', description: 'Faire des cookies ensemble', url: 'https://example.com/patisserie' },
    { title: 'Camping dans le jardin', description: 'Nuit sous la tente', url: 'https://example.com/camping' },
    { title: 'Karaoké familial', description: 'Chanter tous ensemble', url: 'https://example.com/karaoke' },
    { title: 'Observation des étoiles', description: 'Soirée astronomie', url: 'https://example.com/etoiles' },
  ],
  EVENEMENT: [
    { title: 'Anniversaire 5 ans', description: 'Fête d\'anniversaire à thème pirate', url: 'https://example.com/anniv-pirate', eventDate: Date.now() + 30 * 24 * 60 * 60 * 1000, location: 'Maison' },
    { title: 'Noël en famille', description: 'Réveillon et cadeaux', url: 'https://example.com/noel', eventDate: Date.now() + 90 * 24 * 60 * 60 * 1000, location: 'Chez grand-mère' },
    { title: 'Vacances d\'été', description: 'Séjour à la mer', url: 'https://example.com/vacances-ete', eventDate: Date.now() + 180 * 24 * 60 * 60 * 1000, location: 'Bretagne' },
    { title: 'Rentrée scolaire', description: 'Préparation et fournitures', url: 'https://example.com/rentree', eventDate: Date.now() + 150 * 24 * 60 * 60 * 1000, location: 'École' },
    { title: 'Fête des mères', description: 'Cadeau et activité spéciale', url: 'https://example.com/fete-meres', eventDate: Date.now() + 60 * 24 * 60 * 60 * 1000 },
    { title: 'Halloween', description: 'Déguisements et bonbons', url: 'https://example.com/halloween', eventDate: Date.now() + 45 * 24 * 60 * 60 * 1000, location: 'Quartier' },
    { title: 'Pâques', description: 'Chasse aux oeufs', url: 'https://example.com/paques', eventDate: Date.now() + 120 * 24 * 60 * 60 * 1000, location: 'Jardin' },
    { title: 'Fête des pères', description: 'Surprise pour papa', url: 'https://example.com/fete-peres', eventDate: Date.now() + 75 * 24 * 60 * 60 * 1000 },
    { title: 'Carnaval', description: 'Défilé costumé', url: 'https://example.com/carnaval', eventDate: Date.now() + 100 * 24 * 60 * 60 * 1000, location: 'Centre-ville' },
    { title: 'Fête de l\'école', description: 'Kermesse et spectacle', url: 'https://example.com/fete-ecole', eventDate: Date.now() + 140 * 24 * 60 * 60 * 1000, location: 'École' },
  ],
};

const categories = ['LINK_CATEGORY_RECETTE', 'LINK_CATEGORY_CADEAU', 'LINK_CATEGORY_ACTIVITE', 'LINK_CATEGORY_IDEE', 'LINK_CATEGORY_EVENEMENT'];

// État global pour stocker les utilisateurs créés
let users = [];

export const options = {
  scenarios: {
    seed_data: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '10m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.1'],
    http_req_duration: ['p(95)<5000'],
  },
};

function randomElement(array) {
  return array[Math.floor(Math.random() * array.length)];
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function createUser(index) {
  const firstName = firstNames[index % firstNames.length];
  const lastName = lastNames[index % lastNames.length];
  const email = `${firstName.toLowerCase()}.${lastName.toLowerCase()}${index}@tribbae.test`;
  const password = 'Test1234!';
  const displayName = `${firstName} ${lastName}`;

  console.log(`Creating user: ${email}`);

  const payload = JSON.stringify({
    email: email,
    password: password,
    displayName: displayName,
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post(`${BASE_URL}/v1/auth/register`, payload, params);
  
  const success = check(res, {
    'user created': (r) => r.status === 200,
  });

  if (success) {
    const body = JSON.parse(res.body);
    return {
      email: email,
      password: password,
      displayName: displayName,
      token: body.token,
      userId: body.userId,
    };
  }

  return null;
}

function createFolder(user, folderName) {
  const tags = folderTags[folderName] || ['général'];
  const visibility = Math.random() > (1 - PUBLIC_FOLDER_RATIO) ? 'PUBLIC' : 'PRIVATE';

  const payload = JSON.stringify({
    name: folderName,
    icon: '📁',
    color: 'ORANGE',
    visibility: visibility,
    tags: tags,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${user.token}`,
    },
  };

  const res = http.post(`${BASE_URL}/v1/folders`, payload, params);
  
  const success = check(res, {
    'folder created': (r) => r.status === 200,
  });

  if (success) {
    const body = JSON.parse(res.body);
    console.log(`  Created folder: ${folderName} (${visibility})`);
    return body.folder;
  }

  return null;
}

function createLink(user, folder, category) {
  const categoryKey = category.replace('LINK_CATEGORY_', '');
  const templates = linkTemplates[categoryKey] || linkTemplates.IDEE;
  const template = randomElement(templates);

  const payload = {
    folderId: folder.id,
    title: template.title,
    url: template.url || '',
    description: template.description || '',
    category: category,
    tags: folder.tags || [],
    rating: randomInt(3, 5),
  };

  // Ajouter des champs spécifiques selon la catégorie
  if (categoryKey === 'RECETTE' && template.ingredients) {
    payload.ingredients = template.ingredients;
  }
  if (template.price) {
    payload.price = template.price;
  }
  if (template.ageRange) {
    payload.ageRange = template.ageRange;
  }
  if (template.location) {
    payload.location = template.location;
  }
  if (template.eventDate) {
    payload.eventDate = template.eventDate;
  }

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${user.token}`,
    },
  };

  const res = http.post(`${BASE_URL}/v1/links`, JSON.stringify(payload), params);
  
  check(res, {
    'link created': (r) => r.status === 200,
  });

  if (res.status === 200) {
    console.log(`    Created link: ${template.title}`);
  }
}

function likeFolder(user, folderId) {
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${user.token}`,
    },
  };

  http.post(`${BASE_URL}/v1/folders/${folderId}/like`, '{}', params);
}

export default function () {
  console.log('=== Starting data seeding ===');
  console.log(`Base URL: ${BASE_URL}`);
  console.log(`Creating ${NUM_USERS} users with ${NUM_FOLDERS_PER_USER} folders each`);
  console.log('');

  // Créer les utilisateurs
  for (let i = 0; i < NUM_USERS; i++) {
    const user = createUser(i);
    if (user) {
      users.push(user);
      sleep(0.5);
    }
  }

  console.log(`\nCreated ${users.length} users`);
  console.log('');

  // Créer des dossiers et des liens pour chaque utilisateur
  const allFolders = [];
  
  for (let i = 0; i < users.length; i++) {
    const user = users[i];
    console.log(`\nUser ${i + 1}/${users.length}: ${user.displayName}`);

    for (let j = 0; j < NUM_FOLDERS_PER_USER; j++) {
      const folderName = randomElement(folderNames);
      const folder = createFolder(user, folderName);
      
      if (folder) {
        allFolders.push({ folder, user });

        // Créer des liens dans ce dossier
        const numLinks = randomInt(3, NUM_LINKS_PER_FOLDER);
        for (let k = 0; k < numLinks; k++) {
          const category = randomElement(categories);
          createLink(user, folder, category);
          sleep(0.2);
        }
      }

      sleep(0.5);
    }
  }

  console.log(`\n\nCreated ${allFolders.length} folders`);
  console.log('');

  // Ajouter des likes aléatoires sur les dossiers publics
  console.log('\nAdding random likes to public folders...');
  const publicFolders = allFolders.filter(f => f.folder.visibility === 'PUBLIC');
  
  for (let i = 0; i < users.length; i++) {
    const user = users[i];
    const numLikes = randomInt(5, 15); // Plus de likes par utilisateur
    
    for (let j = 0; j < numLikes; j++) {
      const randomFolder = randomElement(publicFolders);
      if (randomFolder.user.userId !== user.userId) {
        likeFolder(user, randomFolder.folder.id);
        sleep(0.1);
      }
    }
  }

  console.log('\n=== Data seeding completed ===');
  console.log(`\nSummary:`);
  console.log(`- Users: ${users.length}`);
  console.log(`- Folders: ${allFolders.length}`);
  console.log(`- Public folders: ${publicFolders.length} (${Math.round(publicFolders.length / allFolders.length * 100)}%)`);
  console.log(`- Private folders: ${allFolders.length - publicFolders.length}`);
  console.log(`- Links: ~${allFolders.length * 4} (average)`);
  console.log(`- Likes: ~${users.length * 10} (average)`);
  console.log('');
  console.log('Test accounts:');
  users.slice(0, 5).forEach(u => {
    console.log(`  ${u.email} / Test1234!`);
  });
  console.log('');
  console.log('🎉 Vous pouvez maintenant explorer les listes communautaires sur http://localhost:5173');
}

