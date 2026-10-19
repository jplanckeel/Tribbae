package seo

import (
	"context"
	"errors"
	"time"

	"github.com/tribbae/backend/internal/folder"
	"github.com/tribbae/backend/internal/link"
)

// Idea est une idée publique telle que présentée aux moteurs.
type Idea struct {
	ID          string
	Title       string
	Description string
	Category    string // LINK_CATEGORY_…
	URL         string // page d'origine
	ImageURL    string
	AgeRange    string
	Price       string
	Location    string
	Tags        []string
	Ingredients []string
	EventDate   time.Time // zéro si pas de date
	Author      string
	Created     time.Time
	Updated     time.Time
}

// Collection est un dossier public (ou ouvert par un lien de partage).
type Collection struct {
	ID        string
	Name      string
	Author    string
	BannerURL string
	Tags      []string
	Updated   time.Time
	Ideas     []Idea
}

// Source donne accès au contenu public, et à lui seul : une idée ou un dossier
// privé n'existe pas pour les moteurs (nil, nil).
type Source interface {
	Idea(ctx context.Context, id string) (*Idea, error)
	Folder(ctx context.Context, id string) (*Collection, error)
	SharedFolder(ctx context.Context, token string) (*Collection, error)
	PublishedIdeas(ctx context.Context, category string, limit int) ([]Idea, error)
	PublishedFolders(ctx context.Context, limit int) ([]Collection, error)
}

// NewSource branche Source sur les services idées et dossiers, en lecture
// anonyme : mêmes règles de visibilité qu'un visiteur sans compte.
func NewSource(links *link.Service, folders *folder.Service) Source {
	return &serviceSource{links: links, folders: folders}
}

type serviceSource struct {
	links   *link.Service
	folders *folder.Service
}

func (s *serviceSource) Idea(ctx context.Context, id string) (*Idea, error) {
	l, err := s.links.Get(ctx, id, "")
	if errors.Is(err, link.ErrNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	ideas := s.toIdeas(ctx, []*link.Link{l})
	return &ideas[0], nil
}

func (s *serviceSource) Folder(ctx context.Context, id string) (*Collection, error) {
	f, err := s.folders.Get(ctx, id, "")
	if errors.Is(err, folder.ErrNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	links, err := s.links.List(ctx, "", id)
	if err != nil && !errors.Is(err, link.ErrNotFound) {
		return nil, err
	}
	return s.collection(ctx, f, links), nil
}

func (s *serviceSource) SharedFolder(ctx context.Context, token string) (*Collection, error) {
	f, _, err := s.folders.GetByShareToken(ctx, token)
	if errors.Is(err, folder.ErrNotFound) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	// Seuls le nom et la bannière servent à l'aperçu : pas d'idées
	return s.collection(ctx, f, nil), nil
}

func (s *serviceSource) PublishedIdeas(ctx context.Context, category string, limit int) ([]Idea, error) {
	links, err := s.links.ListPublished(ctx, category, int64(limit))
	if err != nil {
		return nil, err
	}
	return s.toIdeas(ctx, links), nil
}

func (s *serviceSource) PublishedFolders(ctx context.Context, limit int) ([]Collection, error) {
	folders, err := s.folders.ListPublished(ctx, int64(limit))
	if err != nil {
		return nil, err
	}
	stats := s.folders.LoadStats(ctx, folders)
	out := make([]Collection, 0, len(folders))
	for _, f := range folders {
		out = append(out, Collection{
			ID: f.ID.Hex(), Name: f.Name, Author: stats.Owners[f.OwnerID].DisplayName,
			BannerURL: f.BannerURL, Tags: f.Tags, Updated: f.UpdatedAt,
		})
	}
	return out, nil
}

func (s *serviceSource) collection(ctx context.Context, f *folder.Folder, links []*link.Link) *Collection {
	stats := s.folders.LoadStats(ctx, []*folder.Folder{f})
	return &Collection{
		ID: f.ID.Hex(), Name: f.Name, Author: stats.Owners[f.OwnerID].DisplayName,
		BannerURL: f.BannerURL, Tags: f.Tags, Updated: f.UpdatedAt,
		Ideas: s.toIdeas(ctx, links),
	}
}

func (s *serviceSource) toIdeas(ctx context.Context, links []*link.Link) []Idea {
	if len(links) == 0 {
		return nil
	}
	stats := s.links.LoadStats(ctx, links, "")
	out := make([]Idea, 0, len(links))
	for _, l := range links {
		out = append(out, Idea{
			ID: l.ID.Hex(), Title: l.Title, Description: l.Description, Category: l.Category,
			URL: l.URL, ImageURL: l.ImageURL, AgeRange: l.AgeRange, Price: l.Price, Location: l.Location,
			Tags: l.Tags, Ingredients: l.Ingredients, EventDate: eventTime(l.EventDate),
			Author: stats.Owners[l.OwnerID].DisplayName, Created: l.CreatedAt, Updated: l.UpdatedAt,
		})
	}
	return out
}

// eventTime convertit la date d'événement : le mobile stocke des
// millisecondes, d'anciennes données web des secondes.
func eventTime(v int64) time.Time {
	switch {
	case v <= 0:
		return time.Time{}
	case v < 1e11:
		return time.Unix(v, 0)
	default:
		return time.UnixMilli(v)
	}
}
