import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { shared } from "../api";
import LinkCard from "../components/LinkCard";

export default function SharedFolder() {
  const { token } = useParams<{ token: string }>();
  const [folder, setFolder] = useState<any>(null);
  const [links, setLinks] = useState<any[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!token) return;
    shared.get(token).then((res) => {
      setFolder(res.folder);
      setLinks(res.links || []);
    }).catch(() => setError("Lien de partage invalide"));
  }, [token]);

  if (error) {
    return (
      <div className="min-h-screen bg-amber-50 flex items-center justify-center">
        <p className="text-gray-500">{error}</p>
      </div>
    );
  }

  if (!folder) {
    return (
      <div className="min-h-screen bg-amber-50 flex items-center justify-center">
        <p className="text-gray-400">Chargement...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-amber-50">
      <div className="max-w-5xl mx-auto px-4 py-8">
        <div className="text-center mb-6">
          <h1 className="text-2xl font-bold text-orange-500">Tribbae</h1>
          <h2 className="text-xl font-semibold text-gray-800 mt-2">{folder.name}</h2>
          <p className="text-gray-400 text-sm">Liste partagée</p>
        </div>
        {links.length === 0 ? (
          <p className="text-center text-gray-400">Aucun élément</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {links.map((link: any) => (
              <LinkCard key={link.id} link={link} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
