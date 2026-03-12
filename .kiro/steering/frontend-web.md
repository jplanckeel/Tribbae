---
inclusion: fileMatch
fileMatchPattern: "fntend/**/*.{ts,tsx}"
---

# Frontend Web - Tribbae

## Stack Technique
- **Framework**: React 18+ avec TypeScript
- **Build Tool**: Vite
- **Styling**: Tailwind CSS
- **State Management**: React Hooks (useState, useEffect, useContext)
- **Routing**: React Router
- **HTTP Client**: Fetch API

## Structure du Projet Frontend

```
frontend/
├── src/
│   ├── components/        # Composants réutilisables
│   ├── pages/            # Pages de l'application
│   ├── hooks/            # Custom hooks
│   ├── services/         # Services API
│   ├── types.ts          # Types TypeScript
│   ├── App.tsx           # Composant principal
│   └── main.tsx          # Point d'entrée
├── public/               # Assets statiques
└── index.html
```

## Règles de Développement

### 1. TypeScript
- Toujours typer les props des composants
- Utiliser les interfaces pour les objets complexes
- Éviter `any`, préférer `unknown` si nécessaire
- Utiliser les types du fichier `types.ts`

### 2. Composants React
- Composants fonctionnels uniquement (pas de classes)
- Utiliser les hooks React (useState, useEffect, etc.)
- Props destructurées dans la signature
- Export nommé pour les composants

```tsx
interface MyComponentProps {
  title: string;
  onAction: () => void;
}

export function MyComponent({ title, onAction }: MyComponentProps) {
  const [state, setState] = useState<string>('');
  
  return (
    <div className="p-4">
      <h1>{title}</h1>
    </div>
  );
}
```

### 3. Tailwind CSS
- Utiliser les classes utilitaires Tailwind
- Pas de CSS inline sauf exception
- Responsive design avec préfixes `sm:`, `md:`, `lg:`
- Dark mode avec préfixe `dark:` si nécessaire

#### Couleurs du Design System
```tsx
// Couleurs principales
bg-orange-500     // #F97316 (Orange principal)
bg-orange-100     // #FFF7ED (Orange clair)
text-gray-900     // #111827 (Texte principal)
text-gray-600     // #6B7280 (Texte secondaire)
bg-gray-50        // #F9FAFB (Surface)

// Catégories
bg-yellow-400     // #FFD700 (Idée)
bg-orange-600     // #FF8C00 (Cadeau)
bg-sky-400        // #4FC3F7 (Activité)
bg-orange-500     // #FF7043 (Événement)
bg-green-400      // #81C784 (Recette)
bg-purple-600     // #9C27B0 (Livre)
bg-pink-500       // #E91E63 (Décoration)
```

### 4. API et Backend
- URL Backend: `https://tribbae.bananaops.cloud`
- Format JSON: **camelCase** (userId, displayName, etc.)
- Authentification: JWT token dans header `Authorization: Bearer <token>`
- Gérer les erreurs HTTP (401, 404, 500)

### 5. Types de Données

```typescript
// types.ts
export enum LinkCategory {
  IDEE = 'LINK_CATEGORY_IDEE',
  CADEAU = 'LINK_CATEGORY_CADEAU',
  ACTIVITE = 'LINK_CATEGORY_ACTIVITE',
  EVENEMENT = 'LINK_CATEGORY_EVENEMENT',
  RECETTE = 'LINK_CATEGORY_RECETTE',
  LIVRE = 'LINK_CATEGORY_LIVRE',
  DECORATION = 'LINK_CATEGORY_DECORATION'
}

export interface Link {
  id: string;
  ownerId: string;
  folderId?: string;
  title: string;
  url: string;
  description: string;
  category: LinkCategory;
  tags: string[];
  ageRange: string;
  location: string;
  price: string;
  imageUrl: string;
  eventDate?: number;
  reminderEnabled: boolean;
  rating: number;
  ingredients: string[];
  favorite: boolean;
  likedByMe: boolean;
  likeCount: number;
  updatedAt: string;
}

export interface Folder {
  id: string;
  name: string;
  icon: string;
  color: string;
  visibility: string;
  bannerUrl: string;
  tags: string[];
  linkCount: number;
  likeCount: number;
  updatedAt: string;
}

export interface AuthResponse {
  userId: string;
  token: string;
  displayName: string;
  isAdmin: boolean;
}
```

### 6. Service API

```typescript
// services/api.ts
const API_BASE_URL = 'https://tribbae.bananaops.cloud';

export async function login(email: string, password: string): Promise<AuthResponse> {
  const response = await fetch(`${API_BASE_URL}/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });
  
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${await response.text()}`);
  }
  
  return response.json();
}

export async function getLinks(token: string): Promise<Link[]> {
  const response = await fetch(`${API_BASE_URL}/v1/links`, {
    headers: { 
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });
  
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  
  const data = await response.json();
  return data.links || [];
}
```

### 7. Gestion d'État
- Utiliser `useState` pour l'état local
- Utiliser `useEffect` pour les effets de bord
- Context API pour l'état global (auth, user)
- LocalStorage pour la persistance (token, préférences)

### 8. Formulaires
- Validation côté client avant soumission
- Afficher les erreurs de manière claire
- Désactiver le bouton pendant la soumission
- Gérer les états de chargement

### 9. Responsive Design
- Mobile-first approach
- Breakpoints Tailwind: sm (640px), md (768px), lg (1024px)
- Tester sur mobile, tablette et desktop
- Navigation adaptée selon la taille d'écran

### 10. Performance
- Lazy loading des images
- Code splitting avec React.lazy()
- Mémoïsation avec useMemo/useCallback si nécessaire
- Éviter les re-renders inutiles

## Patterns Communs

### Page avec Liste
```tsx
export function LinksPage() {
  const [links, setLinks] = useState<Link[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  useEffect(() => {
    async function fetchLinks() {
      try {
        const token = localStorage.getItem('token');
        if (!token) throw new Error('Not authenticated');
        
        const data = await getLinks(token);
        setLinks(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Unknown error');
      } finally {
        setLoading(false);
      }
    }
    
    fetchLinks();
  }, []);
  
  if (loading) return <div>Chargement...</div>;
  if (error) return <div>Erreur: {error}</div>;
  
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      {links.map(link => (
        <LinkCard key={link.id} link={link} />
      ))}
    </div>
  );
}
```

### Formulaire avec Validation
```tsx
export function LoginForm() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    
    try {
      const response = await login(email, password);
      localStorage.setItem('token', response.token);
      localStorage.setItem('userId', response.userId);
      // Redirect...
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setLoading(false);
    }
  };
  
  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <input
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        className="w-full px-4 py-2 border rounded-lg"
        required
      />
      <input
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        className="w-full px-4 py-2 border rounded-lg"
        required
      />
      {error && <div className="text-red-500">{error}</div>}
      <button
        type="submit"
        disabled={loading}
        className="w-full bg-orange-500 text-white py-2 rounded-lg disabled:opacity-50"
      >
        {loading ? 'Connexion...' : 'Se connecter'}
      </button>
    </form>
  );
}
```

## Commandes Utiles

```bash
# Lancer le dev server
npm run dev
# ou
task frontend:dev

# Build pour production
npm run build

# Preview du build
npm run preview
```

## Règle Fondamentale
Toute nouvelle feature doit être implémentée sur les DEUX clients (mobile + web).
Le frontend web doit avoir les mêmes fonctionnalités que l'app mobile Android.

