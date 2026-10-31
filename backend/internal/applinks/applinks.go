// Package applinks publie /.well-known/assetlinks.json : la déclaration qui
// permet à Android de vérifier que l'app peut ouvrir les liens du site
// (android:autoVerify dans le manifest : partage de dossier, invitations,
// mot de passe oublié, vérification de l'email). Sans elle, ces liens
// s'ouvrent dans le navigateur.
package applinks

import (
	"encoding/json"
	"fmt"
	"net/http"
	"regexp"
	"strings"
)

// Path est l'adresse imposée par Android (Digital Asset Links).
const Path = "/.well-known/assetlinks.json"

// PackageName est l'identifiant de l'app sur le Play Store (définitif).
const PackageName = "fr.tribbae.app"

// Empreinte SHA-256 d'un certificat : 32 octets en hexadécimal séparés par « : »
var fingerprintRe = regexp.MustCompile(`^[0-9A-F]{2}(:[0-9A-F]{2}){31}$`)

type statement struct {
	Relation []string `json:"relation"`
	Target   target   `json:"target"`
}

type target struct {
	Namespace    string   `json:"namespace"`
	PackageName  string   `json:"package_name"`
	Fingerprints []string `json:"sha256_cert_fingerprints"`
}

// Handler sert la déclaration pour les empreintes données (clé de signature
// Play, clé d'importation…). Sans empreinte, il répond 404 : mieux vaut aucun
// fichier qu'un fichier qui n'autorise aucune clé réelle. Une empreinte mal
// formée est refusée au démarrage plutôt que publiée.
func Handler(fingerprints []string) (http.Handler, error) {
	if len(fingerprints) == 0 {
		return http.NotFoundHandler(), nil
	}
	normalized := make([]string, 0, len(fingerprints))
	for _, f := range fingerprints {
		f = strings.ToUpper(strings.TrimSpace(f))
		if !fingerprintRe.MatchString(f) {
			return nil, fmt.Errorf("invalid SHA-256 certificate fingerprint %q (expected AA:BB:… with 32 bytes)", f)
		}
		normalized = append(normalized, f)
	}
	body, err := json.Marshal([]statement{{
		Relation: []string{"delegate_permission/common.handle_all_urls"},
		Target:   target{Namespace: "android_app", PackageName: PackageName, Fingerprints: normalized},
	}})
	if err != nil {
		return nil, err
	}
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet && r.Method != http.MethodHead {
			w.Header().Set("Allow", "GET, HEAD")
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("Cache-Control", "public, max-age=3600")
		_, _ = w.Write(body)
	}), nil
}
