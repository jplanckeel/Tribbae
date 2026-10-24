// Applique le thème (« Apparence » de Ma tribu, lib/theme.ts) avant le premier
// affichage, pour éviter un flash clair le soir. Fichier séparé : la CSP
// n'autorise pas les scripts en ligne.
(function () {
  var pref = "auto";
  try { pref = localStorage.getItem("theme") || "auto"; } catch (e) { /* stockage bloqué */ }
  var dark = pref === "soir" || (pref !== "jour" && window.matchMedia && matchMedia("(prefers-color-scheme: dark)").matches);
  document.documentElement.dataset.theme = dark ? "dark" : "light";
  var meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute("content", dark ? "#1C1411" : "#FBF3E7");
})();
