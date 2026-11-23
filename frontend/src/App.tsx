import { createBrowserRouter, Navigate, RouterProvider, useParams } from "react-router";
import { AppLayout, BareLayout, RequireAuth } from "./components/Shell";
// Pages d'arrivée depuis un moteur de recherche (accueil, Explorer, catégories,
// fiches, dossiers) : dans le paquet principal, pour s'afficher sans attente.
// Les autres écrans se chargent à la demande (premier affichage plus rapide).
import Explorer from "./pages/Explorer";
import FolderDetail from "./pages/FolderDetail";
import Home from "./pages/Home";
import LinkDetail from "./pages/LinkDetail";

/** Écran chargé à la demande ; `guard` : réservé aux comptes connectés. */
function page(load: () => Promise<{ default: React.ComponentType }>, guard = false) {
  return async () => {
    const { default: Page } = await load();
    return { element: guard ? <RequireAuth><Page /></RequireAuth> : <Page /> };
  };
}

function LegacyCategory() {
  const { slug } = useParams();
  return <Navigate to={`/category/${slug}`} replace />;
}

const router = createBrowserRouter([
  {
    element: <BareLayout />,
    children: [
      { path: "/bienvenue", lazy: page(() => import("./pages/Welcome")) },
      { path: "/connexion", lazy: page(() => import("./pages/Welcome").then((m) => ({ default: m.Auth }))) },
      { path: "/invitation", lazy: page(() => import("./pages/Invitation")) },
      { path: "/invitation-cercle", lazy: page(() => import("./pages/CircleInvitation")) },
      { path: "/offrir", lazy: page(() => import("./pages/OfferGift")) },
      { path: "/connecter-application", lazy: page(() => import("./pages/ConnectApp")) },
      { path: "/mot-de-passe/oublie", lazy: page(() => import("./pages/Password").then((m) => ({ default: m.ForgotPassword }))) },
      { path: "/mot-de-passe/nouveau", lazy: page(() => import("./pages/Password").then((m) => ({ default: m.NewPassword }))) },
      { path: "/verifier-email", lazy: page(() => import("./pages/VerifyEmail")) },
      { path: "/share/:token", lazy: page(() => import("./pages/SharedFolder")) },
    ],
  },
  {
    element: <AppLayout />,
    children: [
      { path: "/", element: <Home /> },
      { path: "/explorer", element: <Explorer /> },
      { path: "/category/:slug", element: <Explorer /> },
      { path: "/links/:id", element: <LinkDetail /> },
      { path: "/dossiers/:id", element: <FolderDetail /> },
      { path: "/aide", lazy: page(() => import("./pages/Faq")) },
      { path: "/a-propos", lazy: page(() => import("./pages/About")) },
      { path: "/confidentialite", lazy: page(() => import("./pages/Privacy")) },
      { path: "/cgu", lazy: page(() => import("./pages/Terms")) },
      { path: "/mentions-legales", lazy: page(() => import("./pages/LegalNotice")) },
      { path: "/mes-idees", lazy: page(() => import("./pages/MyIdeas"), true) },
      { path: "/courses", lazy: page(() => import("./pages/Shopping"), true) },
      { path: "/tribu", lazy: page(() => import("./pages/Tribe"), true) },
      { path: "/reglages", element: <Navigate to="/tribu?ouvrir=parametres" replace /> },
      { path: "/admin", lazy: page(() => import("./pages/Admin"), true) },
      { path: "/partager", lazy: page(() => import("./pages/ShareTarget"), true) },
      // Anciennes URLs (favoris, liens partagés, référencement)
      { path: "/login", element: <Navigate to="/connexion" replace /> },
      { path: "/discover", element: <Navigate to="/explorer?vue=dossiers" replace /> },
      { path: "/folders", element: <Navigate to="/mes-idees" replace /> },
      { path: "/tags", element: <Navigate to="/mes-idees?vue=tags" replace /> },
      { path: "/children", element: <Navigate to="/tribu" replace /> },
      { path: "/categorie/:slug", element: <LegacyCategory /> },
      { path: "*", element: <Navigate to="/" replace /> },
    ],
  },
]);

export default function App() {
  return <RouterProvider router={router} />;
}
