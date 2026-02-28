package link

import (
	"context"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"github.com/tribbae/backend/internal/interceptor"
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

func (h *Handler) toProto(ctx context.Context, l *Link, userID string) *pb.Link {
	likeCount, _ := h.svc.GetLikeCount(ctx, l.ID.Hex())
	likedByMe, _ := h.svc.IsLikedByUser(ctx, l.ID.Hex(), userID)

	return &pb.Link{
		Id:               l.ID.Hex(),
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
		LikeCount:        likeCount,
		LikedByMe:        likedByMe,
		Favorite:         l.Favorite,
		OwnerDisplayName: h.svc.GetOwnerDisplayName(ctx, l.OwnerID),
	}
}

func (h *Handler) CreateLink(ctx context.Context, req *pb.CreateLinkRequest) (*pb.CreateLinkResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	l := &Link{
		FolderID:        req.FolderId,
		Title:           req.Title,
		URL:             req.Url,
		Description:     req.Description,
		Category:        req.Category.String(),
		Tags:            req.Tags,
		AgeRange:        req.AgeRange,
		Location:        req.Location,
		Price:           req.Price,
		ImageURL:        req.ImageUrl,
		EventDate:       req.EventDate,
		ReminderEnabled: req.ReminderEnabled,
		Rating:          req.Rating,
		Ingredients:     req.Ingredients,
	}
	// Scraper OG si pas d'image fournie
	if l.ImageURL == "" && l.URL != "" {
		if meta, err := scrapeOG(l.URL); err == nil {
			if l.Title == "" && meta.Title != "" {
				l.Title = meta.Title
			}
			if l.Description == "" && meta.Description != "" {
				l.Description = meta.Description
			}
			if meta.Image != "" {
				l.ImageURL = meta.Image
			}
		}
	}
	created, err := h.svc.Create(ctx, ownerID, l)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to create link: %v", err)
	}
	return &pb.CreateLinkResponse{Link: h.toProto(ctx, created, ownerID)}, nil
}

func (h *Handler) GetLink(ctx context.Context, req *pb.GetLinkRequest) (*pb.GetLinkResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	l, err := h.svc.Get(ctx, req.LinkId, ownerID)
	if err != nil {
		return nil, status.Errorf(codes.NotFound, "link not found: %v", err)
	}
	return &pb.GetLinkResponse{Link: h.toProto(ctx, l, ownerID)}, nil
}

func (h *Handler) ListLinks(ctx context.Context, req *pb.ListLinksRequest) (*pb.ListLinksResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	links, err := h.svc.List(ctx, ownerID, req.FolderId)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to list links: %v", err)
	}
	var pbLinks []*pb.Link
	for _, l := range links {
		pbLinks = append(pbLinks, h.toProto(ctx, l, ownerID))
	}
	return &pb.ListLinksResponse{Links: pbLinks}, nil
}

func (h *Handler) UpdateLink(ctx context.Context, req *pb.UpdateLinkRequest) (*pb.UpdateLinkResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	l := &Link{
		FolderID:        req.FolderId,
		Title:           req.Title,
		URL:             req.Url,
		Description:     req.Description,
		Category:        req.Category.String(),
		Tags:            req.Tags,
		AgeRange:        req.AgeRange,
		Location:        req.Location,
		Price:           req.Price,
		ImageURL:        req.ImageUrl,
		EventDate:       req.EventDate,
		ReminderEnabled: req.ReminderEnabled,
		Rating:          req.Rating,
		Ingredients:     req.Ingredients,
	}
	updated, err := h.svc.Update(ctx, req.LinkId, ownerID, l)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to update link: %v", err)
	}
	return &pb.UpdateLinkResponse{Link: h.toProto(ctx, updated, ownerID)}, nil
}

func (h *Handler) DeleteLink(ctx context.Context, req *pb.DeleteLinkRequest) (*pb.DeleteLinkResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	if err := h.svc.Delete(ctx, req.LinkId, ownerID); err != nil {
		return nil, status.Errorf(codes.Internal, "failed to delete link: %v", err)
	}
	return &pb.DeleteLinkResponse{}, nil
}

func (h *Handler) LikeLink(ctx context.Context, req *pb.LikeLinkRequest) (*pb.LikeLinkResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	count, err := h.svc.LikeLink(ctx, req.LinkId, ownerID)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to like link: %v", err)
	}
	return &pb.LikeLinkResponse{LikeCount: count}, nil
}

func (h *Handler) UnlikeLink(ctx context.Context, req *pb.UnlikeLinkRequest) (*pb.UnlikeLinkResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	count, err := h.svc.UnlikeLink(ctx, req.LinkId, ownerID)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to unlike link: %v", err)
	}
	return &pb.UnlikeLinkResponse{LikeCount: count}, nil
}

func (h *Handler) ToggleFavoriteLink(ctx context.Context, req *pb.ToggleFavoriteLinkRequest) (*pb.ToggleFavoriteLinkResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	favorite, err := h.svc.ToggleFavorite(ctx, req.LinkId, ownerID)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to toggle favorite: %v", err)
	}
	return &pb.ToggleFavoriteLinkResponse{Favorite: favorite}, nil
}

func (h *Handler) ListCommunityLinks(ctx context.Context, req *pb.ListCommunityLinksRequest) (*pb.ListCommunityLinksResponse, error) {
	// Endpoint public — pas d'auth requise, userID vide pour likedByMe
	userID, _ := interceptor.UserIDFromContext(ctx)

	links, err := h.svc.ListCommunity(ctx, req.Category, req.Limit)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to list community links: %v", err)
	}
	var pbLinks []*pb.Link
	for _, l := range links {
		pbLinks = append(pbLinks, h.toProto(ctx, l, userID))
	}
	return &pb.ListCommunityLinksResponse{Links: pbLinks}, nil
}

func (h *Handler) ListNewLinks(ctx context.Context, req *pb.ListNewLinksRequest) (*pb.ListNewLinksResponse, error) {
	userID, _ := interceptor.UserIDFromContext(ctx)

	links, err := h.svc.ListNew(ctx, req.Limit)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to list new links: %v", err)
	}
	var pbLinks []*pb.Link
	for _, l := range links {
		pbLinks = append(pbLinks, h.toProto(ctx, l, userID))
	}
	return &pb.ListNewLinksResponse{Links: pbLinks}, nil
}
