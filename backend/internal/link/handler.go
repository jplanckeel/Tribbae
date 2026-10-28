package link

import (
	"context"
	"errors"
	"log"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"github.com/tribbae/backend/internal/auth"
	"github.com/tribbae/backend/internal/interceptor"
	"github.com/tribbae/backend/internal/preview"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"
)

type Handler struct {
	pb.UnimplementedLinkServiceServer
	svc *Service
}

func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

// toStatus traduit les erreurs métier en codes gRPC sans exposer les détails internes.
func toStatus(op string, err error) error {
	switch {
	case errors.Is(err, ErrInvalidInput):
		return status.Error(codes.InvalidArgument, err.Error())
	case errors.Is(err, ErrNotFound):
		return status.Error(codes.NotFound, "link not found")
	case errors.Is(err, ErrForbidden):
		return status.Error(codes.PermissionDenied, "not authorized")
	case errors.Is(err, auth.ErrEmailNotVerified):
		return status.Error(codes.FailedPrecondition, err.Error())
	default:
		log.Printf("link: %s: %v", op, err)
		return status.Errorf(codes.Internal, "failed to %s", op)
	}
}

func (h *Handler) toProto(l *Link, st *Stats) *pb.Link {
	id := l.ID.Hex()
	owner := st.Owners[l.OwnerID]
	return &pb.Link{
		Id:               id,
		OwnerId:          l.OwnerID,
		FolderId:         l.FolderID,
		Title:            l.Title,
		Url:              l.URL,
		Description:      l.Description,
		Category:         pb.LinkCategory(pb.LinkCategory_value[l.Category]),
		Tags:             l.Tags,
		AgeRange:         l.AgeRange,
		Location:         l.Location,
		Price:            l.Price,
		ImageUrl:         l.ImageURL,
		EventDate:        l.EventDate,
		ReminderEnabled:  l.ReminderEnabled,
		Rating:           l.Rating,
		Ingredients:      l.Ingredients,
		CreatedAt:        timestamppb.New(l.CreatedAt),
		UpdatedAt:        timestamppb.New(l.UpdatedAt),
		LikeCount:        st.LikeCount[id],
		LikedByMe:        st.LikedByMe[id],
		Favorite:         l.Favorite,
		OwnerDisplayName: owner.DisplayName,
		OwnerIsAdmin:     owner.IsAdmin,
		Visibility:       l.Visibility,
		CreatedVia:       l.CreatedVia,
	}
}

func (h *Handler) one(ctx context.Context, l *Link, userID string) *pb.Link {
	return h.toProto(l, h.svc.LoadStats(ctx, []*Link{l}, userID))
}

func (h *Handler) many(ctx context.Context, links []*Link, userID string) []*pb.Link {
	st := h.svc.LoadStats(ctx, links, userID)
	out := make([]*pb.Link, 0, len(links))
	for _, l := range links {
		out = append(out, h.toProto(l, st))
	}
	return out
}

func fromRequest(folderID, title, url, description string, category pb.LinkCategory, tags []string,
	ageRange, location, price, imageURL string, eventDate int64, reminder bool, rating int32,
	ingredients []string, visibility string) *Link {
	return &Link{
		FolderID:        folderID,
		Title:           title,
		URL:             url,
		Description:     description,
		Category:        category.String(),
		Tags:            tags,
		AgeRange:        ageRange,
		Location:        location,
		Price:           price,
		ImageURL:        imageURL,
		EventDate:       eventDate,
		ReminderEnabled: reminder,
		Rating:          rating,
		Ingredients:     ingredients,
		Visibility:      visibility,
	}
}

func (h *Handler) CreateLink(ctx context.Context, req *pb.CreateLinkRequest) (*pb.CreateLinkResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	l := fromRequest(req.FolderId, req.Title, req.Url, req.Description, req.Category, req.Tags,
		req.AgeRange, req.Location, req.Price, req.ImageUrl, req.EventDate, req.ReminderEnabled,
		req.Rating, req.Ingredients, req.Visibility)
	if err := l.sanitize(); err != nil {
		return nil, toStatus("create link", err)
	}
	// Compléter avec les métadonnées Open Graph si aucune image n'est fournie
	if l.ImageURL == "" && l.URL != "" {
		if meta, err := preview.Fetch(ctx, l.URL); err == nil {
			if l.Title == "" && meta.Title != "" {
				l.Title = meta.Title
			}
			if l.Description == "" && meta.Description != "" {
				l.Description = meta.Description
			}
			l.ImageURL = meta.Image
		}
	}
	created, err := h.svc.Create(ctx, ownerID, l)
	if err != nil {
		return nil, toStatus("create link", err)
	}
	return &pb.CreateLinkResponse{Link: h.one(ctx, created, ownerID)}, nil
}

func (h *Handler) GetLink(ctx context.Context, req *pb.GetLinkRequest) (*pb.GetLinkResponse, error) {
	// Méthode publique : un visiteur anonyme ne voit que les idées publiques
	// (ou celles d'un dossier public), cf. Service.canAccessLink.
	userID, _ := interceptor.UserIDFromContext(ctx)
	l, err := h.svc.Get(ctx, req.LinkId, userID)
	if err != nil {
		return nil, toStatus("get link", err)
	}
	return &pb.GetLinkResponse{Link: h.one(ctx, l, userID)}, nil
}

func (h *Handler) ListLinks(ctx context.Context, req *pb.ListLinksRequest) (*pb.ListLinksResponse, error) {
	// Méthode publique pour lister un dossier public ; la liste personnelle
	// (sans folder_id) exige une session.
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil && req.FolderId == "" {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	links, err := h.svc.List(ctx, userID, req.FolderId)
	if err != nil {
		return nil, toStatus("list links", err)
	}
	return &pb.ListLinksResponse{Links: h.many(ctx, links, userID)}, nil
}

func (h *Handler) UpdateLink(ctx context.Context, req *pb.UpdateLinkRequest) (*pb.UpdateLinkResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	l := fromRequest(req.FolderId, req.Title, req.Url, req.Description, req.Category, req.Tags,
		req.AgeRange, req.Location, req.Price, req.ImageUrl, req.EventDate, req.ReminderEnabled,
		req.Rating, req.Ingredients, req.Visibility)
	updated, err := h.svc.Update(ctx, req.LinkId, userID, l)
	if err != nil {
		return nil, toStatus("update link", err)
	}
	return &pb.UpdateLinkResponse{Link: h.one(ctx, updated, userID)}, nil
}

func (h *Handler) DeleteLink(ctx context.Context, req *pb.DeleteLinkRequest) (*pb.DeleteLinkResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	if err := h.svc.Delete(ctx, req.LinkId, userID); err != nil {
		return nil, toStatus("delete link", err)
	}
	return &pb.DeleteLinkResponse{}, nil
}

func (h *Handler) LikeLink(ctx context.Context, req *pb.LikeLinkRequest) (*pb.LikeLinkResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	count, err := h.svc.LikeLink(ctx, req.LinkId, userID)
	if err != nil {
		return nil, toStatus("like link", err)
	}
	return &pb.LikeLinkResponse{LikeCount: count}, nil
}

func (h *Handler) UnlikeLink(ctx context.Context, req *pb.UnlikeLinkRequest) (*pb.UnlikeLinkResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	count, err := h.svc.UnlikeLink(ctx, req.LinkId, userID)
	if err != nil {
		return nil, toStatus("unlike link", err)
	}
	return &pb.UnlikeLinkResponse{LikeCount: count}, nil
}

func (h *Handler) ToggleFavoriteLink(ctx context.Context, req *pb.ToggleFavoriteLinkRequest) (*pb.ToggleFavoriteLinkResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	favorite, err := h.svc.ToggleFavorite(ctx, req.LinkId, userID)
	if err != nil {
		return nil, status.Error(codes.NotFound, "link not found")
	}
	return &pb.ToggleFavoriteLinkResponse{Favorite: favorite}, nil
}

func (h *Handler) ListCommunityLinks(ctx context.Context, req *pb.ListCommunityLinksRequest) (*pb.ListCommunityLinksResponse, error) {
	// Endpoint public — pas d'auth requise, userID vide pour likedByMe
	userID, _ := interceptor.UserIDFromContext(ctx)

	links, err := h.svc.ListCommunity(ctx, req.Category, req.Limit)
	if err != nil {
		return nil, toStatus("list community links", err)
	}
	return &pb.ListCommunityLinksResponse{Links: h.many(ctx, links, userID)}, nil
}

func (h *Handler) ListNewLinks(ctx context.Context, req *pb.ListNewLinksRequest) (*pb.ListNewLinksResponse, error) {
	userID, _ := interceptor.UserIDFromContext(ctx)

	links, err := h.svc.ListNew(ctx, req.Limit)
	if err != nil {
		return nil, toStatus("list new links", err)
	}
	return &pb.ListNewLinksResponse{Links: h.many(ctx, links, userID)}, nil
}
