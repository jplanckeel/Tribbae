package netsafe

import (
	"compress/gzip"
	"context"
	"errors"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	utls "github.com/refraction-networking/utls"
)

func TestBrowserClientRefusesInternalAddresses(t *testing.T) {
	c := NewBrowserClient(3 * time.Second)
	for _, u := range []string{"https://127.0.0.1:1/", "https://169.254.169.254/latest/meta-data/", "http://10.0.0.1/"} {
		_, err := GetWithHeaders(context.Background(), c, u, nil)
		if !errors.Is(err, ErrForbiddenAddress) {
			t.Errorf("%s : err = %v, want ErrForbiddenAddress", u, err)
		}
	}
}

// testTransport cible un serveur de test local (le dialer de prod refuse le loopback).
func testTransport(srv *httptest.Server) *chromeTransport {
	return &chromeTransport{
		dial: func(ctx context.Context, network, _ string) (net.Conn, error) {
			return (&net.Dialer{}).DialContext(ctx, network, srv.Listener.Addr().String())
		},
		tlsConfig: func(host string) *utls.Config { return &utls.Config{ServerName: host, InsecureSkipVerify: true} },
		fallback:  http.DefaultTransport,
	}
}

func TestChromeTransportHTTP2AndHTTP1(t *testing.T) {
	handler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Accept-Encoding") != "gzip" {
			t.Errorf("Accept-Encoding = %q", r.Header.Get("Accept-Encoding"))
		}
		w.Header().Set("Content-Encoding", "gzip")
		w.Header().Set("X-Proto", r.Proto)
		zw := gzip.NewWriter(w)
		_, _ = io.WriteString(zw, "<title>Bonjour</title>")
		_ = zw.Close()
	})
	for _, h2 := range []bool{true, false} {
		srv := httptest.NewUnstartedServer(handler)
		srv.EnableHTTP2 = h2
		srv.StartTLS()
		c := &http.Client{Transport: testTransport(srv), Timeout: 5 * time.Second}
		resp, err := c.Get("https://exemple.test/")
		if err != nil {
			t.Fatalf("h2=%v : %v", h2, err)
		}
		body, _ := io.ReadAll(resp.Body)
		_ = resp.Body.Close()
		want := "HTTP/1.1"
		if h2 {
			want = "HTTP/2.0"
		}
		if string(body) != "<title>Bonjour</title>" || resp.Header.Get("X-Proto") != want {
			t.Errorf("h2=%v : body=%q proto=%s", h2, body, resp.Header.Get("X-Proto"))
		}
		srv.Close()
	}
}
