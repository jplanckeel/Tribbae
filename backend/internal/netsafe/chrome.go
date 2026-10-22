package netsafe

import (
	"bufio"
	"compress/gzip"
	"context"
	"errors"
	"io"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	utls "github.com/refraction-networking/utls"
	"golang.org/x/net/http2"
)

// Certains pare-feu applicatifs (Akamai, DataDome…) refusent les connexions
// dont l'empreinte TLS est celle du client Go, même avec un User-Agent de
// navigateur : la poignée de main TLS de Go est reconnaissable. NewBrowserClient
// ouvre donc ses connexions HTTPS avec l'empreinte de Chrome (utls) et parle
// HTTP/2 ou HTTP/1.1 selon ce que le serveur négocie.

// ChromeHeaders sont les en-têtes envoyés par Chrome pour une navigation :
// ils vont avec l'empreinte TLS (un « Chrome » sans eux reste suspect).
var ChromeHeaders = map[string]string{
	"User-Agent":                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
	"Accept":                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
	"Accept-Language":           "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7",
	"Sec-Ch-Ua":                 `"Chromium";v="133", "Google Chrome";v="133", "Not(A:Brand";v="99"`,
	"Sec-Ch-Ua-Mobile":          "?0",
	"Sec-Ch-Ua-Platform":        `"Windows"`,
	"Sec-Fetch-Dest":            "document",
	"Sec-Fetch-Mode":            "navigate",
	"Sec-Fetch-Site":            "none",
	"Sec-Fetch-User":            "?1",
	"Upgrade-Insecure-Requests": "1",
}

type dialFunc func(ctx context.Context, network, addr string) (net.Conn, error)

// chromeTransport est un RoundTripper sans réutilisation de connexion : un
// aperçu ne fait qu'une poignée de requêtes (redirections comprises).
type chromeTransport struct {
	dial      dialFunc
	tlsConfig func(host string) *utls.Config
	fallback  http.RoundTripper // HTTP en clair, ou échec de la poignée de main Chrome
}

// NewBrowserClient est NewClient avec l'empreinte TLS de Chrome. La protection
// SSRF est la même : la connexion TCP passe par le même dialer filtrant.
func NewBrowserClient(timeout time.Duration) *http.Client {
	c := NewClient(timeout)
	dialer := &net.Dialer{Timeout: 5 * time.Second, Control: control}
	c.Transport = &chromeTransport{
		dial:      dialer.DialContext,
		tlsConfig: func(host string) *utls.Config { return &utls.Config{ServerName: host} },
		fallback:  c.Transport,
	}
	return c
}

func (t *chromeTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	if req.URL.Scheme != "https" {
		return t.fallback.RoundTrip(req)
	}
	resp, err := t.roundTrip(req)
	var hsErr *handshakeError
	if errors.As(err, &hsErr) {
		// Serveur qui n'accepte pas la poignée de main imitée : client Go standard
		return t.fallback.RoundTrip(req)
	}
	return resp, err
}

type handshakeError struct{ err error }

func (e *handshakeError) Error() string { return "chrome tls handshake: " + e.err.Error() }
func (e *handshakeError) Unwrap() error { return e.err }

func (t *chromeTransport) roundTrip(req *http.Request) (*http.Response, error) {
	ctx := req.Context()
	host, port := req.URL.Hostname(), req.URL.Port()
	if port == "" {
		port = "443"
	}
	raw, err := t.dial(ctx, "tcp", net.JoinHostPort(host, port))
	if err != nil {
		return nil, err
	}
	conn := utls.UClient(raw, t.tlsConfig(host), utls.HelloChrome_Auto)
	// La connexion suit le contexte de la requête (timeout du client, annulation)
	stop := context.AfterFunc(ctx, func() { _ = conn.Close() })
	release := func() {
		stop()
		_ = conn.Close()
	}
	if err := conn.HandshakeContext(ctx); err != nil {
		release()
		return nil, &handshakeError{err}
	}

	if req.Header.Get("Accept-Encoding") == "" {
		req = req.Clone(ctx)
		req.Header.Set("Accept-Encoding", "gzip")
	}

	var resp *http.Response
	if conn.ConnectionState().NegotiatedProtocol == "h2" {
		cc, err := (&http2.Transport{}).NewClientConn(conn)
		if err != nil {
			release()
			return nil, err
		}
		resp, err = cc.RoundTrip(req)
		if err != nil {
			_ = cc.Close()
			release()
			return nil, err
		}
		prev := release
		release = func() { _ = cc.Close(); prev() }
	} else {
		if err := req.Write(conn); err != nil {
			release()
			return nil, err
		}
		resp, err = http.ReadResponse(bufio.NewReader(conn), req)
		if err != nil {
			release()
			return nil, err
		}
	}

	body := resp.Body
	if strings.EqualFold(resp.Header.Get("Content-Encoding"), "gzip") {
		zr, err := gzip.NewReader(body)
		if err != nil {
			_ = body.Close()
			release()
			return nil, err
		}
		body = &readCloser{Reader: zr, closer: body}
		resp.Header.Del("Content-Encoding")
		resp.Header.Del("Content-Length")
		resp.ContentLength = -1
		resp.Uncompressed = true
	}
	resp.Body = &readCloser{Reader: body, closer: body, after: release}
	return resp, nil
}

// readCloser ferme le corps puis libère la connexion, une seule fois.
type readCloser struct {
	io.Reader
	closer io.Closer
	after  func()
	once   sync.Once
}

func (r *readCloser) Close() error {
	err := r.closer.Close()
	r.once.Do(func() {
		if r.after != nil {
			r.after()
		}
	})
	return err
}
