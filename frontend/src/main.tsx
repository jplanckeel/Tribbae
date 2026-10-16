import "@fontsource-variable/bricolage-grotesque/wght.css";
import "@fontsource-variable/dm-sans/wght.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { FeedbackProvider } from "./components/feedback";
import { DataProvider } from "./lib/data";
import "./index.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <FeedbackProvider>
      <DataProvider>
        <App />
      </DataProvider>
    </FeedbackProvider>
  </StrictMode>,
);
