package config

import "testing"

func TestValidate(t *testing.T) {
	strong := "0123456789abcdef0123456789abcdef"
	cases := []struct {
		name    string
		cfg     Config
		wantErr bool
	}{
		{"local with example secret", Config{BaseURL: "http://localhost:8080", JWTSecret: insecureJWTSecret}, false},
		{"prod with example secret", Config{BaseURL: "https://tribbae.bananaops.cloud", JWTSecret: insecureJWTSecret}, true},
		{"prod with short secret", Config{BaseURL: "https://tribbae.bananaops.cloud", JWTSecret: "short"}, true},
		{"prod with medium secret", Config{BaseURL: "https://tribbae.bananaops.cloud", JWTSecret: "0123456789abcdefXYZ"}, false},
		{"prod with strong secret", Config{BaseURL: "https://tribbae.bananaops.cloud", JWTSecret: strong}, false},
	}
	for _, c := range cases {
		if err := c.cfg.Validate(); (err != nil) != c.wantErr {
			t.Errorf("%s: Validate() error = %v, wantErr %v", c.name, err, c.wantErr)
		}
	}
}
