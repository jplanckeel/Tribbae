import { useEffect, useState } from "react";
import { admin } from "../api";
import { Crown, Sparkles } from "lucide-react";

interface User {
  id: string;
  email: string;
  displayName: string;
  isAdmin: boolean;
  isPremium: boolean;
  createdAt: number;
}

export default function Admin() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    loadUsers();
  }, []);

  const loadUsers = async () => {
    try {
      setLoading(true);
      const res = await admin.listUsers();
      setUsers(res.users);
    } catch (err: any) {
      setError(err.message || "Erreur lors du chargement des utilisateurs");
    } finally {
      setLoading(false);
    }
  };

  const togglePremium = async (userId: string, currentStatus: boolean) => {
    try {
      await admin.updateUserPremium(userId, !currentStatus);
      await loadUsers();
    } catch (err: any) {
      alert(err.message || "Erreur lors de la mise à jour");
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-gray-500">Chargement...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-red-500">{error}</div>
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto p-6">
      <div className="flex items-center gap-3 mb-6">
        <Crown className="w-8 h-8 text-orange-500" />
        <h1 className="text-3xl font-bold">Administration</h1>
      </div>

      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Utilisateur
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Email
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Statut
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Tribbae+
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {users.map((user) => (
              <tr key={user.id}>
                <td className="px-6 py-4 whitespace-nowrap">
                  <div className="flex items-center">
                    <div className="text-sm font-medium text-gray-900">
                      {user.displayName}
                    </div>
                    {user.isAdmin && (
                      <Crown className="w-4 h-4 text-orange-500 ml-2" />
                    )}
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <div className="text-sm text-gray-500">{user.email}</div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                    user.isAdmin
                      ? "bg-orange-100 text-orange-800"
                      : "bg-gray-100 text-gray-800"
                  }`}>
                    {user.isAdmin ? "Admin" : "Utilisateur"}
                  </span>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  {user.isPremium ? (
                    <div className="flex items-center gap-1 text-orange-500">
                      <Sparkles className="w-4 h-4" />
                      <span className="text-sm font-medium">Actif</span>
                    </div>
                  ) : (
                    <span className="text-sm text-gray-400">Inactif</span>
                  )}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm">
                  <button
                    onClick={() => togglePremium(user.id, user.isPremium)}
                    className={`px-3 py-1 rounded-md font-medium transition-colors ${
                      user.isPremium
                        ? "bg-gray-200 text-gray-700 hover:bg-gray-300"
                        : "bg-orange-500 text-white hover:bg-orange-600"
                    }`}
                  >
                    {user.isPremium ? "Désactiver" : "Activer"} Tribbae+
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-6 p-4 bg-orange-50 rounded-lg border border-orange-200">
        <div className="flex items-start gap-2">
          <Sparkles className="w-5 h-5 text-orange-500 mt-0.5" />
          <div>
            <h3 className="font-semibold text-orange-900">À propos de Tribbae+</h3>
            <p className="text-sm text-orange-700 mt-1">
              Les utilisateurs Tribbae+ bénéficient d'une recherche IA améliorée avec Google Gemini pour des suggestions encore plus pertinentes.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
