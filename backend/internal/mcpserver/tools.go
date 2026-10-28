package mcpserver

import (
	"context"
	"errors"
	"slices"
	"strings"
	"time"

	"github.com/modelcontextprotocol/go-sdk/mcp"
	"go.mongodb.org/mongo-driver/v2/bson"

	"github.com/tribbae/backend/internal/folder"
	"github.com/tribbae/backend/internal/link"
	"github.com/tribbae/backend/internal/oauth"
)

const (
	maxFolders = 200
	maxLinks   = 100
)

// Erreurs renvoyées à l'agent (en anglais, sans contenu d'utilisateur).
var (
	errScope      = errors.New("this connection is not allowed to do that; reconnect Tribbae with the missing permission")
	errFolder     = errors.New("folder not found or not writable; call list_folders and use a folder with writable=true")
	errPublic     = errors.New("this folder is public; ideas can only be added to public folders from the Tribbae app")
	errQuota      = errors.New("daily limit of ideas added through this connector reached; try again tomorrow")
	errInternal   = errors.New("internal error, please retry later")
	errFolderRead = errors.New("folder not found")
)

// no sert aux indications facultatives des outils (pointeurs) : aucun ne
// détruit rien ni ne contacte d'autre service.
var no = false

func (s *Server) addTools(server *mcp.Server) {
	mcp.AddTool(server, &mcp.Tool{
		Name:        "list_folders",
		Title:       "List folders",
		Description: "Lists the folders of the account (its own, its family's and those shared with it). writable=true means ideas can be added to it with create_link.",
		Annotations: &mcp.ToolAnnotations{ReadOnlyHint: true, OpenWorldHint: &no},
	}, s.listFolders)
	mcp.AddTool(server, &mcp.Tool{
		Name:        "list_links",
		Title:       "List ideas in a folder",
		Description: "Lists the ideas of a folder (most recent first, at most 100), optionally filtered by a text found in the title or URL. Use it to avoid adding a duplicate.",
		Annotations: &mcp.ToolAnnotations{ReadOnlyHint: true, OpenWorldHint: &no},
	}, s.listLinks)
	mcp.AddTool(server, &mcp.Tool{
		Name:        "create_link",
		Title:       "Add an idea",
		Description: "Adds a private idea (activity, outing, gift, recipe, book…) with an https link to a writable folder. Ideas added here are always private.",
		Annotations: &mcp.ToolAnnotations{DestructiveHint: &no, OpenWorldHint: &no},
	}, s.createLink)
}

// run vérifie le droit demandé, exécute l'outil et journalise son résultat.
func run[Out any](s *Server, ctx context.Context, req *mcp.CallToolRequest, tool, scope string, f func(c caller) (Out, string, error)) (*mcp.CallToolResult, Out, error) {
	var zero Out
	c, err := callerFrom(req)
	if err != nil {
		return nil, zero, err
	}
	if !slices.Contains(c.scopes, scope) {
		s.audit.Record(ctx, c, tool, OutcomeForbidden)
		return nil, zero, errScope
	}
	out, outcome, err := f(c)
	s.audit.Record(ctx, c, tool, outcome)
	if err != nil {
		return nil, zero, err
	}
	return nil, out, nil
}

// --- list_folders ---

type listFoldersInput struct{}

type folderOut struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Writable bool   `json:"writable" jsonschema:"ideas can be added to this folder with create_link"`
}

type listFoldersOutput struct {
	Folders []folderOut `json:"folders"`
}

func (s *Server) listFolders(ctx context.Context, req *mcp.CallToolRequest, _ listFoldersInput) (*mcp.CallToolResult, listFoldersOutput, error) {
	return run(s, ctx, req, "list_folders", oauth.ScopeFoldersRead, func(c caller) (listFoldersOutput, string, error) {
		folders, err := s.folders.List(ctx, c.userID)
		if err != nil {
			logError("list_folders", err)
			return listFoldersOutput{}, OutcomeError, errInternal
		}
		out := listFoldersOutput{Folders: []folderOut{}}
		for _, f := range folders {
			if len(out.Folders) == maxFolders {
				break
			}
			id := f.ID.Hex()
			out.Folders = append(out.Folders, folderOut{
				ID:       id,
				Name:     cleanText(f.Name, false),
				Writable: s.writable(ctx, f, c.userID),
			})
		}
		return out, OutcomeOK, nil
	})
}

// writable : l'utilisateur peut y écrire dans l'app, et le dossier n'est pas
// public (y ranger une idée la publierait).
func (s *Server) writable(ctx context.Context, f *folder.Folder, userID string) bool {
	return f.Visibility != "public" && s.links.CanEditFolder(ctx, f.ID.Hex(), userID)
}

// memberFolder retrouve un dossier parmi ceux du compte (sa tribu, ou
// partagés avec lui) ; jamais un dossier public d'un inconnu.
func (s *Server) memberFolder(ctx context.Context, folderID, userID string) (*folder.Folder, error) {
	if _, err := bson.ObjectIDFromHex(folderID); err != nil {
		return nil, nil
	}
	folders, err := s.folders.List(ctx, userID)
	if err != nil {
		return nil, err
	}
	for _, f := range folders {
		if f.ID.Hex() == folderID {
			return f, nil
		}
	}
	return nil, nil
}

// --- list_links ---

type listLinksInput struct {
	FolderID string `json:"folder_id" jsonschema:"id of a folder returned by list_folders"`
	Query    string `json:"query,omitempty" jsonschema:"optional text to look for in the title or URL"`
}

type linkOut struct {
	ID        string `json:"id"`
	Title     string `json:"title"`
	URL       string `json:"url,omitempty"`
	Category  string `json:"category,omitempty"`
	CreatedAt string `json:"created_at" jsonschema:"date the idea was added, YYYY-MM-DD"`
}

type listLinksOutput struct {
	Links []linkOut `json:"links"`
	// Truncated : d'autres idées correspondent mais ne sont pas listées
	Truncated bool `json:"truncated"`
}

func (s *Server) listLinks(ctx context.Context, req *mcp.CallToolRequest, in listLinksInput) (*mcp.CallToolResult, listLinksOutput, error) {
	return run(s, ctx, req, "list_links", oauth.ScopeLinksRead, func(c caller) (listLinksOutput, string, error) {
		query := strings.ToLower(cleanText(in.Query, false))
		if err := checkLen("query", query, maxQueryLen); err != nil {
			return listLinksOutput{}, OutcomeInvalid, err
		}
		f, err := s.memberFolder(ctx, in.FolderID, c.userID)
		if err != nil {
			logError("list_links", err)
			return listLinksOutput{}, OutcomeError, errInternal
		}
		if f == nil {
			return listLinksOutput{}, OutcomeForbidden, errFolderRead
		}
		links, err := s.links.List(ctx, c.userID, in.FolderID)
		if err != nil {
			logError("list_links", err)
			return listLinksOutput{}, OutcomeError, errInternal
		}
		slices.SortFunc(links, func(a, b *link.Link) int { return b.CreatedAt.Compare(a.CreatedAt) })
		out := listLinksOutput{Links: []linkOut{}}
		for _, l := range links {
			if query != "" && !strings.Contains(strings.ToLower(l.Title), query) && !strings.Contains(strings.ToLower(l.URL), query) {
				continue
			}
			if len(out.Links) == maxLinks {
				out.Truncated = true
				break
			}
			out.Links = append(out.Links, linkOut{
				ID:        l.ID.Hex(),
				Title:     cleanText(l.Title, false),
				URL:       l.URL,
				Category:  categoryName(l.Category),
				CreatedAt: l.CreatedAt.In(paris).Format(time.DateOnly),
			})
		}
		return out, OutcomeOK, nil
	})
}

// --- create_link ---

type createLinkInput struct {
	FolderID    string   `json:"folder_id" jsonschema:"id of a folder with writable=true from list_folders"`
	Title       string   `json:"title" jsonschema:"short title, at most 200 characters"`
	URL         string   `json:"url" jsonschema:"https URL of the page describing the idea"`
	Description string   `json:"description,omitempty" jsonschema:"at most 2000 characters"`
	Category    string   `json:"category" jsonschema:"one of: idee, cadeau, activite, sortie, recette, livre, deco"`
	Tags        []string `json:"tags,omitempty" jsonschema:"at most 10 tags of 40 characters"`
	AgeRange    string   `json:"age_range,omitempty" jsonschema:"suitable ages, e.g. 3–6 ans"`
	Location    string   `json:"location,omitempty" jsonschema:"place or city"`
	Price       string   `json:"price,omitempty" jsonschema:"price as text, e.g. 12 € or gratuit"`
	EventDate   string   `json:"event_date,omitempty" jsonschema:"date of an outing or event, YYYY-MM-DD or YYYY-MM-DDTHH:MM (Paris time)"`
	Visibility  string   `json:"visibility,omitempty" jsonschema:"ignored: ideas added here are always private"`
}

type createLinkOutput struct {
	ID         string `json:"id"`
	Title      string `json:"title"`
	FolderID   string `json:"folder_id"`
	Visibility string `json:"visibility"`
	URL        string `json:"url" jsonschema:"address of the idea in Tribbae"`
}

// buildLink valide les arguments et construit l'idée, toujours privée.
func buildLink(in createLinkInput, now time.Time) (*link.Link, error) {
	l := &link.Link{
		FolderID:    strings.TrimSpace(in.FolderID),
		Title:       cleanText(in.Title, false),
		Description: cleanText(in.Description, true),
		AgeRange:    cleanText(in.AgeRange, false),
		Location:    cleanText(in.Location, false),
		Price:       cleanText(in.Price, false),
		// Publier est une décision humaine, prise dans l'app
		Visibility: "private",
	}
	if l.FolderID == "" {
		return nil, invalid("folder_id is required")
	}
	if l.Title == "" {
		return nil, invalid("title is required")
	}
	for _, f := range []struct {
		name, v string
		max     int
	}{{"title", l.Title, maxTitleLen}, {"description", l.Description, maxDescriptionLen}, {"age_range", l.AgeRange, maxShortLen}, {"location", l.Location, maxShortLen}, {"price", l.Price, maxShortLen}} {
		if err := checkLen(f.name, f.v, f.max); err != nil {
			return nil, err
		}
	}
	var err error
	if l.URL, err = validURL(in.URL); err != nil {
		return nil, err
	}
	var ok bool
	if l.Category, ok = categories[strings.ToLower(strings.TrimSpace(in.Category))]; !ok {
		return nil, invalid("category must be one of: idee, cadeau, activite, sortie, recette, livre, deco")
	}
	if l.Tags, err = cleanTags(in.Tags); err != nil {
		return nil, err
	}
	if l.EventDate, err = parseEventDate(in.EventDate, now); err != nil {
		return nil, err
	}
	return l, nil
}

func (s *Server) createLink(ctx context.Context, req *mcp.CallToolRequest, in createLinkInput) (*mcp.CallToolResult, createLinkOutput, error) {
	return run(s, ctx, req, "create_link", oauth.ScopeLinksWrite, func(c caller) (createLinkOutput, string, error) {
		now := s.now()
		l, err := buildLink(in, now)
		if err != nil {
			return createLinkOutput{}, OutcomeInvalid, err
		}
		f, err := s.memberFolder(ctx, l.FolderID, c.userID)
		if err != nil {
			logError("create_link", err)
			return createLinkOutput{}, OutcomeError, errInternal
		}
		if f == nil || !s.links.CanEditFolder(ctx, l.FolderID, c.userID) {
			return createLinkOutput{}, OutcomeForbidden, errFolder
		}
		if f.Visibility == "public" {
			return createLinkOutput{}, OutcomeForbidden, errPublic
		}
		n, err := s.links.CountCreatedVia(ctx, c.userID, link.CreatedViaMCP, now.Add(-24*time.Hour))
		if err != nil {
			logError("create_link", err)
			return createLinkOutput{}, OutcomeError, errInternal
		}
		if n >= int64(s.cfg.DailyQuota) {
			return createLinkOutput{}, OutcomeQuota, errQuota
		}
		l.CreatedVia = link.CreatedViaMCP
		l.OAuthClientID = c.clientID
		created, err := s.links.Create(ctx, c.userID, l)
		if err != nil {
			if errors.Is(err, link.ErrInvalidInput) {
				return createLinkOutput{}, OutcomeInvalid, invalid("invalid idea: check the URL and field lengths")
			}
			if errors.Is(err, link.ErrForbidden) {
				return createLinkOutput{}, OutcomeForbidden, errFolder
			}
			logError("create_link", err)
			return createLinkOutput{}, OutcomeError, errInternal
		}
		return createLinkOutput{
			ID:         created.ID.Hex(),
			Title:      created.Title,
			FolderID:   created.FolderID,
			Visibility: created.Visibility,
			URL:        s.cfg.SiteURL + "/links/" + created.ID.Hex(),
		}, OutcomeOK, nil
	})
}
