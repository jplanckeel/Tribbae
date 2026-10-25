import "@fontsource-variable/bricolage-grotesque/wght.css";
import "@fontsource-variable/dm-sans/wght.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
// Avant App (et donc le routeur) : son écouteur de retour arrière passe en premier
import "./lib/transitions";
import "./lib/theme";
import App from "./App";
import { FeedbackProvider } from "./components/feedback";
import { renewSessionIfStale } from "./lib/api";
import { DataProvider } from "./lib/data";
import "./index.css";

// Session glissante : jeton renouvelé au lancement et au retour sur l'onglet
void renewSessionIfStale();
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "visible") void renewSessionIfStale();
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <FeedbackProvider>
      <DataProvider>
        <App />
      </DataProvider>
    </FeedbackProvider>
  </StrictMode>,
);
