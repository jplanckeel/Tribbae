// Package netsafe fournit un client HTTP qui refuse de joindre des adresses
// internes (loopback, réseau privé, link-local, metadata cloud…), afin que les
// fonctionnalités qui récupèrent une URL fournie par un utilisateur (aperçu
// Open Graph) ne puissent pas servir de rebond vers l'infrastructure (SSRF).
package netsafe

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"strings"
	"syscall"
	"time"
)

// ErrForbiddenAddress est retourné quand la destination résout vers une IP interne.
var ErrForbiddenAddress = errors.New("destination address is not allowed")

// ErrInvalidURL est retourné quand l'URL n'est pas une URL http(s) absolue.
var ErrInvalidURL = errors.New("only absolute http(s) URLs are allowed")

// IsPublicIP indique si l'adresse est routable publiquement.
func IsPublicIP(ip net.IP) bool {
	if ip == nil {
		return false
	}
	if ip.IsLoopback() || ip.IsPrivate() || ip.IsUnspecified() ||
		ip.IsLinkLocalUnicast() || ip.IsLinkLocalMulticast() ||
		ip.IsInterfaceLocalMulticast() || ip.IsMulticast() {
		return false
	}
	// 100.64.0.0/10 (CGNAT) et 0.0.0.0/8 ne sont pas couverts par IsPrivate.
	if v4 := ip.To4(); v4 != nil {
		if v4[0] == 0 || (v4[0] == 100 && v4[1]&0xC0 == 64) {
			return false
		}
	}
	return true
}

// ValidateURL vérifie qu'une URL est absolue et en http(s).
func ValidateURL(raw string) (*url.URL, error) {
	u, err := url.Parse(strings.TrimSpace(raw))
	if err != nil || u.Host == "" || (u.Scheme != "http" && u.Scheme != "https") {
		return nil, ErrInvalidURL
	}
	return u, nil
}

// control est appelé après la résolution DNS, juste avant le connect : on
// vérifie l'IP réellement contactée, ce qui neutralise le DNS rebinding.
func control(_, address string, _ syscall.RawConn) error {
	host, _, err := net.SplitHostPort(address)
	if err != nil {
		return err
	}
	if !IsPublicIP(net.ParseIP(host)) {
		return ErrForbiddenAddress
	}
	return nil
}

// NewClient retourne un client HTTP qui ne joint que des IP publiques et suit
// au plus 5 redirections (chacune repasse par la vérification d'IP).
func NewClient(timeout time.Duration) *http.Client {
	dialer := &net.Dialer{Timeout: 5 * time.Second, Control: control}
	transport := &http.Transport{
		Proxy:                 nil,
		DialContext:           dialer.DialContext,
		TLSHandshakeTimeout:   5 * time.Second,
		ResponseHeaderTimeout: timeout,
		MaxIdleConns:          10,
		IdleConnTimeout:       30 * time.Second,
	}
	return &http.Client{
		Timeout:   timeout,
		Transport: transport,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			if len(via) >= 5 {
				return errors.New("too many redirects")
			}
			if req.URL.Scheme != "http" && req.URL.Scheme != "https" {
				return ErrInvalidURL
			}
			return nil
		},
	}
}

// GetWithHeaders effectue un GET sur une URL utilisateur avec le client
// sécurisé, avec des en-têtes supplémentaires (ils remplacent les valeurs par
// défaut, User-Agent compris).
func GetWithHeaders(ctx context.Context, client *http.Client, raw string, headers map[string]string) (*http.Response, error) {
	u, err := ValidateURL(raw)
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u.String(), nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (compatible; Tribbae/1.0)")
	req.Header.Set("Accept", "text/html,application/xhtml+xml")
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("fetch: %w", err)
	}
	return resp, nil
}
