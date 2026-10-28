import { createBrowserRouter, Navigate, RouterProvider, useParams } from "react-router";
import { AppLayout, BareLayout, RequireAuth } from "./components/Shell";
import Admin from "./pages/Admin";
import Explorer from "./pages/Explorer";
import About from "./pages/About";
import Faq from "./pages/Faq";
import Privacy from "./pages/Privacy";
import FolderDetail from "./pages/FolderDetail";
import Home from "./pages/Home";
import CircleInvitation from "./pages/CircleInvitation";
import ConnectApp from "./pages/ConnectApp";
import Invitation from "./pages/Invitation";
import LinkDetail from "./pages/LinkDetail";
import MyIdeas from "./pages/MyIdeas";
import { ForgotPassword, NewPassword } from "./pages/Password";
import SharedFolder from "./pages/SharedFolder";
import ShareTarget from "./pages/ShareTarget";
import Shopping from "./pages/Shopping";
import Tribe from "./pages/Tribe";
import VerifyEmail from "./pages/VerifyEmail";
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
      { path: "/invitation", element: <Invitation /> },
      { path: "/invitation-cercle", element: <CircleInvitation /> },
      { path: "/connecter-application", element: <ConnectApp /> },
      { path: "/mot-de-passe/oublie", element: <ForgotPassword /> },
      { path: "/mot-de-passe/nouveau", element: <NewPassword /> },
      { path: "/verifier-email", element: <VerifyEmail /> },
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
      { path: "/a-propos", element: <About /> },
      { path: "/confidentialite", element: <Privacy /> },
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
