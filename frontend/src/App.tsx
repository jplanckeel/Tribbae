import { createBrowserRouter, Navigate, RouterProvider, useParams } from "react-router";
import { AppLayout, BareLayout, RequireAuth } from "./components/Shell";
import Admin from "./pages/Admin";
import Explorer from "./pages/Explorer";
import Faq from "./pages/Faq";
import FolderDetail from "./pages/FolderDetail";
import Home from "./pages/Home";
import LinkDetail from "./pages/LinkDetail";
import MyIdeas from "./pages/MyIdeas";
import SharedFolder from "./pages/SharedFolder";
import ShareTarget from "./pages/ShareTarget";
import Shopping from "./pages/Shopping";
import Tribe from "./pages/Tribe";
import Welcome, { Auth } from "./pages/Welcome";

const auth = (el: React.ReactNode) => <RequireAuth>{el}</RequireAuth>;

function LegacyCategory() {
  const { slug } = useParams();
  return <Navigate to={`/category/${slug}`} replace />;
}

const router = createBrowserRouter([
  {
    element: <BareLayout />,
    children: [
      { path: "/bienvenue", element: <Welcome /> },
      { path: "/connexion", element: <Auth /> },
      { path: "/share/:token", element: <SharedFolder /> },
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
      { path: "/aide", element: <Faq /> },
      { path: "/mes-idees", element: auth(<MyIdeas />) },
      { path: "/courses", element: auth(<Shopping />) },
      { path: "/tribu", element: auth(<Tribe />) },
      { path: "/admin", element: auth(<Admin />) },
      { path: "/partager", element: auth(<ShareTarget />) },
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
