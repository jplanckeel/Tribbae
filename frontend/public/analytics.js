// Mesure d'audience Umami (sans cookie) : avant chaque envoi, on retire ce qui
// pourrait être personnel ou secret.
//  - L'adresse d'un lien de partage contient un jeton secret qui ouvre le
//    dossier : il est remplacé par « :token ».
//  - Le titre d'une page peut être celui d'une idée privée : on envoie le nom
//    de l'écran à la place (« Idée », « Dossier »…).
// Les paramètres de recherche (?q=…) et les ancres sont exclus par les
// attributs data-exclude-search / data-exclude-hash du script Umami.
(function () {
  var SCREENS = [
    [/^\/$/, "Accueil"],
    [/^\/explorer$/, "Explorer"],
    [/^\/category\/[^/]+$/, "Catégorie"],
    [/^\/links\/[^/]+$/, "Idée"],
    [/^\/dossiers\/[^/]+$/, "Dossier"],
    [/^\/share\/[^/]+$/, "Dossier partagé"],
    [/^\/aide$/, "Aide"],
    [/^\/confidentialite$/, "Confidentialité"],
    [/^\/mes-idees$/, "Mes idées"],
    [/^\/tribu$/, "Ma tribu"],
    [/^\/courses$/, "Liste de courses"],
    [/^\/partager$/, "Partage vers Tribbae"],
    [/^\/connexion$/, "Connexion"],
    [/^\/bienvenue$/, "Bienvenue"],
    [/^\/admin$/, "Administration"],
  ];

  function cleanPath(path) {
    return path.replace(/^\/share\/[^/?#]+/, "/share/:token");
  }

  // Adresse complète (https://…) ou chemin seul. Le jeton est retiré quel que
  // soit le domaine : un lien de partage peut aussi arriver en provenance.
  function clean(value) {
    if (!value) return value;
    try {
      var u = new URL(value, location.origin);
      var path = cleanPath(u.pathname);
      return value.charAt(0) === "/" ? path : u.origin + path;
    } catch (e) {
      return value;
    }
  }

  function screen(path) {
    for (var i = 0; i < SCREENS.length; i++) if (SCREENS[i][0].test(path)) return SCREENS[i][1];
    return "Autre";
  }

  window.tribbaeUmamiBeforeSend = function (type, payload) {
    payload.url = clean(payload.url);
    payload.referrer = clean(payload.referrer);
    try {
      payload.title = screen(new URL(payload.url, location.origin).pathname);
    } catch (e) {
      payload.title = "Tribbae";
    }
    return payload;
  };
})();
