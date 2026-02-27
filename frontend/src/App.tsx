import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import Navbar from "./components/Navbar";
import Home from "./pages/Home";
import Login from "./pages/Login";
import Folders from "./pages/Folders";
import Tags from "./pages/Tags";
import LinkDetail from "./pages/LinkDetail";
import SharedFolder from "./pages/SharedFolder";
import Community from "./pages/Community";
import Children from "./pages/Children";

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const token = localStorage.getItem("token");
  return token ? <>{children}</> : <Navigate to="/login" replace />;
}

function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-amber-50">
      <Navbar />
      {children}
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/share/:token" element={<SharedFolder />} />
        <Route
          path="/"
          element={
            <AppLayout><Home /></AppLayout>
          }
        />
        <Route
          path="/folders"
          element={
            <PrivateRoute>
              <AppLayout><Folders /></AppLayout>
            </PrivateRoute>
          }
        />
        <Route
          path="/tags"
          element={
            <PrivateRoute>
              <AppLayout><Tags /></AppLayout>
            </PrivateRoute>
          }
        />
        <Route
          path="/links/:id"
          element={
            <PrivateRoute>
              <AppLayout><LinkDetail /></AppLayout>
            </PrivateRoute>
          }
        />
        <Route
          path="/community"
          element={
            <PrivateRoute>
              <AppLayout><Community /></AppLayout>
            </PrivateRoute>
          }
        />
        <Route
          path="/children"
          element={
            <PrivateRoute>
              <AppLayout><Children /></AppLayout>
            </PrivateRoute>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
