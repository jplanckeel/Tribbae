import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { auth } from "../api";

export default function Login() {
  const [isRegister, setIsRegister] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    try {
      if (isRegister) {
        const res = await auth.register(email, password, displayName);
        localStorage.setItem("token", res.token);
        localStorage.setItem("displayName", displayName);
        localStorage.setItem("isAdmin", String(res.isAdmin));
      } else {
        const res = await auth.login(email, password);
        localStorage.setItem("token", res.token);
        localStorage.setItem("displayName", res.displayName || "");
        localStorage.setItem("isAdmin", String(res.isAdmin));
      }
      navigate("/");
    } catch (err: any) {
      setError(err.message || "Erreur de connexion");
    }
  };

  return (
    <div className="min-h-screen bg-amber-50 flex items-center justify-center px-4">
      <div className="bg-white rounded-3xl shadow-lg p-8 w-full max-w-sm">
        <div className="flex justify-center mb-4">
          <img
            src="/tribbae.jpg"
            alt="Tribbae"
            className="w-20 h-20 rounded-2xl object-cover shadow-md"
          />
        </div>
        <h1 className="text-2xl font-bold text-orange-500 text-center mb-2">
          Tribbae
        </h1>
        <p className="text-gray-400 text-center text-sm mb-6">
          {isRegister ? "Créer un compte" : "Se connecter"}
        </p>

        {error && (
          <div className="bg-red-50 text-red-600 text-sm rounded-xl px-4 py-2 mb-4">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {isRegister && (
            <input
              type="text"
              placeholder="Prénom"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm"
            />
          )}
          <input
            type="email"
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm"
          />
          <input
            type="password"
            placeholder="Mot de passe"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:border-orange-400 focus:outline-none text-sm"
          />
          <button
            type="submit"
            className="w-full py-2.5 rounded-xl bg-orange-500 text-white font-semibold hover:bg-orange-600 transition-colors"
          >
            {isRegister ? "Créer mon compte" : "Connexion"}
          </button>
        </form>

        <button
          onClick={() => { setIsRegister(!isRegister); setError(""); }}
          className="w-full text-center text-sm text-orange-500 mt-4 hover:underline"
        >
          {isRegister ? "Déjà un compte ? Se connecter" : "Pas de compte ? S'inscrire"}
        </button>
      </div>
    </div>
  );
}
