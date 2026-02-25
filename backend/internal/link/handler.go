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

func toProto(l *Link) *pb.Link {
	return &pb.Link{
		Id:              l.ID.Hex(),
		OwnerId:         l.OwnerID,
		FolderId:        l.FolderID,
		Title:           l.Title,
		Url:             l.URL,
		Description:     l.Description,
		Category:        pb.LinkCategory(pb.LinkCategory_value[l.Category]),
		Tags:            l.Tags,
		AgeRange:        l.AgeRange,
		Location:        l.Location,
		Price:           l.Price,
		ImageUrl:        l.ImageURL,
		EventDate:       l.EventDate,
		ReminderEnabled: l.ReminderEnabled,
		Rating:          l.Rating,
		Ingredients:     l.Ingredients,
		CreatedAt:       timestamppb.New(l.CreatedAt),
		UpdatedAt:       timestamppb.New(l.UpdatedAt),
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
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	return &pb.CreateLinkResponse{Link: toProto(created)}, nil
}

func (h *Handler) GetLink(ctx context.Context, req *pb.GetLinkRequest) (*pb.GetLinkResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	l, err := h.svc.Get(ctx, req.LinkId, ownerID)
	if err != nil {
		return nil, status.Errorf(codes.NotFound, err.Error())
	}
	return &pb.GetLinkResponse{Link: toProto(l)}, nil
}

func (h *Handler) ListLinks(ctx context.Context, req *pb.ListLinksRequest) (*pb.ListLinksResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	links, err := h.svc.List(ctx, ownerID, req.FolderId)
	if err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	var pbLinks []*pb.Link
	for _, l := range links {
		pbLinks = append(pbLinks, toProto(l))
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
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	return &pb.UpdateLinkResponse{Link: toProto(updated)}, nil
}

func (h *Handler) DeleteLink(ctx context.Context, req *pb.DeleteLinkRequest) (*pb.DeleteLinkResponse, error) {
	ownerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "unauthenticated")
	}
	if err := h.svc.Delete(ctx, req.LinkId, ownerID); err != nil {
		return nil, status.Errorf(codes.Internal, err.Error())
	}
	return &pb.DeleteLinkResponse{}, nil
}
