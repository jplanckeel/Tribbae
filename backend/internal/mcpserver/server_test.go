package mcpserver

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/modelcontextprotocol/go-sdk/mcp"
	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"

	"github.com/tribbae/backend/internal/folder"
	"github.com/tribbae/backend/internal/link"
	"github.com/tribbae/backend/internal/oauth"
)

func setupTestDB(t *testing.T) *mongo.Database {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	client, err := mongo.Connect(options.Client().ApplyURI("mongodb://localhost:27017").SetServerSelectionTimeout(3 * time.Second))
	if err != nil {
		t.Skipf("Skipping test: MongoDB unavailable: %v", err)
	}
	if err := client.Ping(ctx, nil); err != nil {
		t.Skipf("Skipping test: MongoDB unavailable: %v", err)
	}
	db := client.Database("tribbae_test_" + bson.NewObjectID().Hex())
	t.Cleanup(func() {
		_ = db.Drop(context.Background())
		_ = client.Disconnect(context.Background())
	})
	return db
}

// fakeTokens accepte « tok-<userID>[-<scope>…] » ; sans scope, tous les droits.
type fakeTokens struct{}

func (fakeTokens) VerifyAccessToken(_ context.Context, raw string) (*oauth.AccessToken, error) {
	parts := strings.Split(raw, "-")
	if len(parts) < 2 || parts[0] != "tok" {
		return nil, oauth.ErrInvalidToken
	}
	scopes := parts[2:]
	if len(scopes) == 0 {
		scopes = oauth.Scopes
	}
	return &oauth.AccessToken{UserID: parts[1], ClientID: "tbm_client_test", GrantID: "grant-" + parts[1], Scopes: scopes, ExpiresAt: time.Now().Add(time.Minute)}, nil
}

type fixture struct {
	db     *mongo.Database
	srv    *Server
	url    string
	alice  string
	bob    string
	folder map[string]string
}

func insertFolder(t *testing.T, db *mongo.Database, doc bson.M) string {
	t.Helper()
	id := bson.NewObjectID()
	doc["_id"] = id
	if _, err := db.Collection("folders").InsertOne(context.Background(), doc); err != nil {
		t.Fatal(err)
	}
	return id.Hex()
}

// setup : Alice a un dossier privé, un dossier public, un dossier de Bob où
// elle est éditrice et un autre où elle n'est que lectrice ; Bob a aussi un
// dossier privé qu'Alice ne voit pas.
func setup(t *testing.T, quota int) *fixture {
	db := setupTestDB(t)
	f := &fixture{db: db, alice: bson.NewObjectID().Hex(), bob: bson.NewObjectID().Hex(), folder: map[string]string{}}
	f.folder["mine"] = insertFolder(t, db, bson.M{"owner_id": f.alice, "name": "Sorties", "visibility": "private"})
	f.folder["public"] = insertFolder(t, db, bson.M{"owner_id": f.alice, "name": "Publiques", "visibility": "public"})
	f.folder["editor"] = insertFolder(t, db, bson.M{"owner_id": f.bob, "name": "Noël", "visibility": "private",
		"collaborators": bson.A{bson.M{"user_id": f.alice, "role": "editor"}}})
	f.folder["viewer"] = insertFolder(t, db, bson.M{"owner_id": f.bob, "name": "Vacances", "visibility": "private",
		"collaborators": bson.A{bson.M{"user_id": f.alice, "role": "viewer"}}})
	f.folder["bob"] = insertFolder(t, db, bson.M{"owner_id": f.bob, "name": "Privé de Bob", "visibility": "private"})

	links := link.NewService(db.Collection("links"), db.Collection("folders"))
	folders := folder.NewService(db.Collection("folders"), db.Collection("links"), db.Collection("users"), "https://tribbae.example")
	f.srv = New(Config{
		SiteURL:             "https://tribbae.example",
		ResourceMetadataURL: "https://tribbae.example/.well-known/oauth-protected-resource/mcp",
		AllowedOrigins:      []string{"https://tribbae.example", "https://claude.ai"},
		RequireHTTPS:        false,
		DailyQuota:          quota,
	}, links, folders, NewAudit(db.Collection(AuditCollection)))
	ts := httptest.NewServer(f.srv.Handler(fakeTokens{}))
	t.Cleanup(ts.Close)
	f.url = ts.URL + Path
	return f
}

type bearer struct {
	token string
	next  http.RoundTripper
}

func (b bearer) RoundTrip(r *http.Request) (*http.Response, error) {
	r = r.Clone(r.Context())
	r.Header.Set("Authorization", "Bearer "+b.token)
	return b.next.RoundTrip(r)
}

func (f *fixture) session(t *testing.T, token string) *mcp.ClientSession {
	t.Helper()
	client := mcp.NewClient(&mcp.Implementation{Name: "test", Version: "1"}, nil)
	cs, err := client.Connect(context.Background(), &mcp.StreamableClientTransport{
		Endpoint:   f.url,
		HTTPClient: &http.Client{Transport: bearer{token, http.DefaultTransport}},
		MaxRetries: -1,
	}, nil)
	if err != nil {
		t.Fatalf("connect: %v", err)
	}
	t.Cleanup(func() { _ = cs.Close() })
	return cs
}

// call appelle un outil ; err est le texte d'une erreur d'outil (IsError).
func call(t *testing.T, cs *mcp.ClientSession, tool string, args map[string]any) (map[string]any, string) {
	t.Helper()
	res, err := cs.CallTool(context.Background(), &mcp.CallToolParams{Name: tool, Arguments: args})
	if err != nil {
		t.Fatalf("%s: %v", tool, err)
	}
	if res.IsError {
		var msg string
		for _, c := range res.Content {
			if tc, ok := c.(*mcp.TextContent); ok {
				msg += tc.Text
			}
		}
		return nil, msg
	}
	raw, _ := json.Marshal(res.StructuredContent)
	var out map[string]any
	_ = json.Unmarshal(raw, &out)
	return out, ""
}

func validIdea(folderID string) map[string]any {
	return map[string]any{"folder_id": folderID, "title": "Ferme pédagogique", "url": "https://ferme.example/visite", "category": "sortie"}
}

func (f *fixture) countLinks(t *testing.T, filter bson.M) int64 {
	n, err := f.db.Collection("links").CountDocuments(context.Background(), filter)
	if err != nil {
		t.Fatal(err)
	}
	return n
}

func TestListFoldersShowsOnlyAccessibleFoldersAndWhereToWrite(t *testing.T) {
	f := setup(t, 0)
	out, errMsg := call(t, f.session(t, "tok-"+f.alice), "list_folders", nil)
	if errMsg != "" {
		t.Fatal(errMsg)
	}
	got := map[string]bool{}
	for _, it := range out["folders"].([]any) {
		m := it.(map[string]any)
		got[m["id"].(string)] = m["writable"].(bool)
	}
	want := map[string]bool{f.folder["mine"]: true, f.folder["editor"]: true, f.folder["viewer"]: false, f.folder["public"]: false}
	if len(got) != len(want) {
		t.Errorf("dossiers = %v, want %v", got, want)
	}
	for id, w := range want {
		if g, ok := got[id]; !ok || g != w {
			t.Errorf("dossier %s : writable=%v (présent %v), want %v", id, g, ok, w)
		}
	}
}

func TestCreateLinkIsPrivateAndMarked(t *testing.T) {
	f := setup(t, 0)
	args := validIdea(f.folder["mine"])
	args["visibility"] = "public"
	args["tags"] = []string{"ferme", "Ferme", "animaux"}
	args["event_date"] = time.Now().AddDate(0, 1, 0).Format("2006-01-02")
	out, errMsg := call(t, f.session(t, "tok-"+f.alice), "create_link", args)
	if errMsg != "" {
		t.Fatal(errMsg)
	}
	if out["visibility"] != "private" || !strings.HasPrefix(out["url"].(string), "https://tribbae.example/links/") {
		t.Errorf("réponse = %v", out)
	}
	var l link.Link
	id, _ := bson.ObjectIDFromHex(out["id"].(string))
	if err := f.db.Collection("links").FindOne(context.Background(), bson.M{"_id": id}).Decode(&l); err != nil {
		t.Fatal(err)
	}
	if l.Visibility != "private" || l.CreatedVia != "mcp" || l.OAuthClientID != "tbm_client_test" || l.OwnerID != f.alice ||
		l.Category != "LINK_CATEGORY_EVENEMENT" || len(l.Tags) != 2 || l.EventDate == 0 || l.ImageURL != "" {
		t.Errorf("idée enregistrée = %+v", l)
	}
	// Dossier de Bob où Alice est éditrice : autorisé, comme dans l'app
	if _, errMsg := call(t, f.session(t, "tok-"+f.alice), "create_link", validIdea(f.folder["editor"])); errMsg != "" {
		t.Errorf("dossier où Alice est éditrice : %s", errMsg)
	}
}

func TestCreateLinkRefusesFoldersWithoutWriteAccess(t *testing.T) {
	f := setup(t, 0)
	cs := f.session(t, "tok-"+f.alice)
	for name, want := range map[string]string{
		"bob":    "not writable", // dossier d'un autre
		"viewer": "not writable", // simple lectrice
		"public": "public",       // y ranger l'idée la publierait
	} {
		if _, errMsg := call(t, cs, "create_link", validIdea(f.folder[name])); !strings.Contains(errMsg, want) {
			t.Errorf("dossier %s : %q, want %q", name, errMsg, want)
		}
	}
	if _, errMsg := call(t, cs, "create_link", validIdea("pas-un-id")); errMsg == "" {
		t.Error("dossier inexistant accepté")
	}
	if n := f.countLinks(t, bson.M{}); n != 0 {
		t.Errorf("%d idée(s) créée(s), want 0", n)
	}
}

func TestCreateLinkValidation(t *testing.T) {
	f := setup(t, 0)
	cs := f.session(t, "tok-"+f.alice)
	cases := map[string]func(m map[string]any){
		"http":               func(m map[string]any) { m["url"] = "http://ferme.example/" },
		"javascript":         func(m map[string]any) { m["url"] = "javascript:alert(1)" },
		"ip privée":          func(m map[string]any) { m["url"] = "https://192.168.1.10/admin" },
		"localhost":          func(m map[string]any) { m["url"] = "https://localhost/x" },
		"identifiants":       func(m map[string]any) { m["url"] = "https://user:pass@ferme.example/" },
		"sans titre":         func(m map[string]any) { m["title"] = "  " },
		"titre trop long":    func(m map[string]any) { m["title"] = strings.Repeat("a", 201) },
		"catégorie inconnue": func(m map[string]any) { m["category"] = "LINK_CATEGORY_ADMIN" },
		"trop de tags":       func(m map[string]any) { m["tags"] = strings.Split("a b c d e f g h i j k", " ") },
		"date absurde":       func(m map[string]any) { m["event_date"] = "2099-01-01" },
		"date passée":        func(m map[string]any) { m["event_date"] = "2001-01-01" },
		"date mal formée":    func(m map[string]any) { m["event_date"] = "demain" },
		"description énorme": func(m map[string]any) { m["description"] = strings.Repeat("a", 2001) },
	}
	for name, edit := range cases {
		args := validIdea(f.folder["mine"])
		edit(args)
		if _, errMsg := call(t, cs, "create_link", args); errMsg == "" {
			t.Errorf("%s : accepté", name)
		}
	}
	if n := f.countLinks(t, bson.M{}); n != 0 {
		t.Errorf("%d idée(s) créée(s), want 0", n)
	}
}

func TestDailyQuota(t *testing.T) {
	f := setup(t, 2)
	cs := f.session(t, "tok-"+f.alice)
	for i := range 2 {
		if _, errMsg := call(t, cs, "create_link", validIdea(f.folder["mine"])); errMsg != "" {
			t.Fatalf("création %d : %s", i+1, errMsg)
		}
	}
	if _, errMsg := call(t, cs, "create_link", validIdea(f.folder["mine"])); !strings.Contains(errMsg, "daily limit") {
		t.Errorf("quota dépassé : %q", errMsg)
	}
	// Les idées créées dans l'app ne comptent pas
	if n := f.countLinks(t, bson.M{"created_via": "mcp"}); n != 2 {
		t.Errorf("%d idées MCP, want 2", n)
	}
	var e auditEntry
	opts := options.FindOne().SetSort(bson.D{{Key: "at", Value: -1}})
	if err := f.db.Collection(AuditCollection).FindOne(context.Background(), bson.M{"tool": "create_link"}, opts).Decode(&e); err != nil || e.Outcome != OutcomeQuota {
		t.Errorf("audit du quota : %+v %v", e, err)
	}
}

func TestScopesAreEnforcedPerTool(t *testing.T) {
	f := setup(t, 0)
	cs := f.session(t, "tok-"+f.alice+"-folders:read")
	if _, errMsg := call(t, cs, "list_folders", nil); errMsg != "" {
		t.Errorf("list_folders avec folders:read : %s", errMsg)
	}
	if _, errMsg := call(t, cs, "create_link", validIdea(f.folder["mine"])); !strings.Contains(errMsg, "not allowed") {
		t.Errorf("create_link sans links:write : %q", errMsg)
	}
	if _, errMsg := call(t, cs, "list_links", map[string]any{"folder_id": f.folder["mine"]}); !strings.Contains(errMsg, "not allowed") {
		t.Errorf("list_links sans links:read : %q", errMsg)
	}
}

func TestListLinks(t *testing.T) {
	f := setup(t, 0)
	cs := f.session(t, "tok-"+f.alice)
	for _, title := range []string{"Ferme pédagogique", "Musée des sciences"} {
		args := validIdea(f.folder["mine"])
		args["title"] = title
		if _, errMsg := call(t, cs, "create_link", args); errMsg != "" {
			t.Fatal(errMsg)
		}
	}
	out, errMsg := call(t, cs, "list_links", map[string]any{"folder_id": f.folder["mine"], "query": "MUSÉE"})
	if errMsg != "" {
		t.Fatal(errMsg)
	}
	if ls := out["links"].([]any); len(ls) != 1 || ls[0].(map[string]any)["category"] != "sortie" {
		t.Errorf("recherche : %v", out)
	}
	// Dossier d'un autre, même s'il existe : introuvable
	if _, errMsg := call(t, cs, "list_links", map[string]any{"folder_id": f.folder["bob"]}); !strings.Contains(errMsg, "not found") {
		t.Errorf("dossier de Bob : %q", errMsg)
	}
}

func TestAuditHasNoArguments(t *testing.T) {
	f := setup(t, 0)
	if _, errMsg := call(t, f.session(t, "tok-"+f.alice), "create_link", validIdea(f.folder["mine"])); errMsg != "" {
		t.Fatal(errMsg)
	}
	var raw bson.M
	if err := f.db.Collection(AuditCollection).FindOne(context.Background(), bson.M{"tool": "create_link"}).Decode(&raw); err != nil {
		t.Fatal(err)
	}
	if raw["user_id"] != f.alice || raw["client_id"] != "tbm_client_test" || raw["outcome"] != OutcomeOK || raw["at"] == nil {
		t.Errorf("audit = %v", raw)
	}
	for k := range raw {
		switch k {
		case "_id", "user_id", "client_id", "grant_id", "tool", "outcome", "at":
		default:
			t.Errorf("champ inattendu dans l'audit : %s", k)
		}
	}
}

func post(t *testing.T, url string, header map[string]string) *http.Response {
	t.Helper()
	req, _ := http.NewRequest(http.MethodPost, url, strings.NewReader(`{"jsonrpc":"2.0","id":1,"method":"tools/list"}`))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json, text/event-stream")
	for k, v := range header {
		req.Header.Set(k, v)
	}
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	_ = res.Body.Close()
	return res
}

func TestHTTPGuards(t *testing.T) {
	f := setup(t, 0)
	// Sans jeton : 401 qui pointe vers les métadonnées (découverte OAuth de Claude)
	res := post(t, f.url, nil)
	if res.StatusCode != http.StatusUnauthorized || !strings.Contains(res.Header.Get("WWW-Authenticate"), `resource_metadata="https://tribbae.example/.well-known/oauth-protected-resource/mcp"`) {
		t.Errorf("sans jeton : %d %q", res.StatusCode, res.Header.Get("WWW-Authenticate"))
	}
	// Un JWT de l'app n'est pas un jeton MCP
	if res := post(t, f.url, map[string]string{"Authorization": "Bearer eyJhbGciOiJIUzI1NiJ9.e30.x"}); res.StatusCode != http.StatusUnauthorized {
		t.Errorf("JWT de l'app : %d", res.StatusCode)
	}
	// Origin inconnu (page web, DNS rebinding) : refusé, même avec un jeton
	if res := post(t, f.url, map[string]string{"Authorization": "Bearer tok-" + f.alice, "Origin": "https://evil.example"}); res.StatusCode != http.StatusForbidden {
		t.Errorf("Origin inconnu : %d", res.StatusCode)
	}
	if res := post(t, f.url, map[string]string{"Authorization": "Bearer tok-" + f.alice, "Origin": "https://claude.ai"}); res.StatusCode != http.StatusOK {
		t.Errorf("Origin claude.ai : %d", res.StatusCode)
	}
	if res := post(t, f.url, map[string]string{"Authorization": "Bearer tok-" + f.alice}); res.StatusCode != http.StatusOK {
		t.Errorf("sans Origin (serveur à serveur) : %d", res.StatusCode)
	}

	// HTTPS exigé hors local
	f.srv.cfg.RequireHTTPS = true
	h := f.srv.Handler(fakeTokens{})
	r := httptest.NewRequest(http.MethodPost, Path, strings.NewReader(`{}`))
	r.Header.Set("Authorization", "Bearer tok-"+f.alice)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	if w.Code != http.StatusBadRequest {
		t.Errorf("HTTP en clair : %d", w.Code)
	}
}

func TestRateLimitPerGrant(t *testing.T) {
	f := setup(t, 0)
	for range perGrantPerMinute {
		f.srv.grants.Allow("grant|grant-" + f.alice)
	}
	if res := post(t, f.url, map[string]string{"Authorization": "Bearer tok-" + f.alice}); res.StatusCode != http.StatusTooManyRequests {
		t.Errorf("au-delà de la limite : %d", res.StatusCode)
	}
}
