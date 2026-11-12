package link

import (
	"context"
	"errors"
	"log"
	"time"

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
	// blocked : comptes bloqués par l'utilisateur, dont on cache les idées
	blocked func(ctx context.Context, userID string) map[string]bool
}

// SetBlocked branche le blocage entre comptes (modération).
func (h *Handler) SetBlocked(fn func(ctx context.Context, userID string) map[string]bool) {
	h.blocked = fn
}

// withoutBlocked retire les idées des comptes que l'utilisateur a bloqués.
func (h *Handler) withoutBlocked(ctx context.Context, userID string, links []*Link) []*Link {
	if h.blocked == nil || userID == "" {
		return links
	}
	blocked := h.blocked(ctx, userID)
	if len(blocked) == 0 {
		return links
	}
	out := links[:0:0]
	for _, l := range links {
		if !blocked[l.OwnerID] {
			out = append(out, l)
		}
	}
	return out
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
	case errors.Is(err, ErrHidden), errors.Is(err, ErrGiftList):
		return status.Error(codes.FailedPrecondition, err.Error())
	case errors.Is(err, ErrDoneQuota):
		return status.Error(codes.ResourceExhausted, err.Error())
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
	out := &pb.Link{
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
		PublicViaFolder:  l.FolderID != "" && st.PublicFolders[l.FolderID] == l.OwnerID && !l.Hidden,
		Hidden:           l.Hidden,
		GiftStatus:       st.Gifts[id],
		DoneFamilies:     st.DoneFamilies[id],
	}
	// « C'est fait » : seulement pour la tribu de l'idée et les membres de son dossier
	if st.DoneVisible[id] {
		out.Redo = l.Redo
		out.DonePromptDismissed = l.DonePromptDismissed
		if d := st.Done[id]; d.Count > 0 {
			out.DoneCount = d.Count
			out.LastDoneAt = timestamppb.New(d.Last.Add(12 * time.Hour))
		}
	}
	return out
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
	if req.Status != "" && req.Status != "all" && req.Status != "todo" && req.Status != "done" {
		return nil, status.Error(codes.InvalidArgument, "status must be all, todo or done")
	}
	links, err := h.svc.List(ctx, userID, req.FolderId)
	if err != nil {
		return nil, toStatus("list links", err)
	}
	out := h.many(ctx, links, userID)
	if req.Status == "todo" || req.Status == "done" {
		kept := out[:0]
		for _, l := range out {
			if (l.DoneCount > 0) == (req.Status == "done") {
				kept = append(kept, l)
			}
		}
		out = kept
	}
	return &pb.ListLinksResponse{Links: out}, nil
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

func (h *Handler) ListLikedLinks(ctx context.Context, _ *pb.ListLikedLinksRequest) (*pb.ListLikedLinksResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	links, err := h.svc.ListLiked(ctx, userID)
	if err != nil {
		return nil, toStatus("list liked links", err)
	}
	return &pb.ListLikedLinksResponse{Links: h.many(ctx, links, userID)}, nil
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
	return &pb.ListCommunityLinksResponse{Links: h.many(ctx, h.withoutBlocked(ctx, userID, links), userID)}, nil
}

func (h *Handler) ListNewLinks(ctx context.Context, req *pb.ListNewLinksRequest) (*pb.ListNewLinksResponse, error) {
	userID, _ := interceptor.UserIDFromContext(ctx)

	links, err := h.svc.ListNew(ctx, req.Limit)
	if err != nil {
		return nil, toStatus("list new links", err)
	}
	return &pb.ListNewLinksResponse{Links: h.many(ctx, h.withoutBlocked(ctx, userID, links), userID)}, nil
}

func doneToProto(d *LinkDone) *pb.LinkDone {
	return &pb.LinkDone{
		Id:           d.ID.Hex(),
		LinkId:       d.LinkID,
		UserId:       d.UserID,
		DoneOn:       DoneDay(d.DoneOn),
		WithUserIds:  d.WithUserIDs,
		WithChildIds: d.WithChildIDs,
		Rating:       d.Rating,
		Note:         d.Note,
		PhotoUrl:     d.PhotoURL,
		CreatedAt:    timestamppb.New(d.CreatedAt),
	}
}

func (h *Handler) MarkLinkDone(ctx context.Context, req *pb.MarkLinkDoneRequest) (*pb.LinkDoneResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	d, l, err := h.svc.MarkDone(ctx, req.LinkId, userID, DoneInput{
		DoneOn: req.DoneOn, WithUserIDs: req.WithUserIds, WithChildIDs: req.WithChildIds,
		Rating: req.Rating, Note: req.Note, PhotoURL: req.PhotoUrl, Redo: req.Redo,
	})
	if err != nil {
		return nil, toStatus("mark link done", err)
	}
	return &pb.LinkDoneResponse{Done: doneToProto(d), Link: h.one(ctx, l, userID)}, nil
}

func (h *Handler) UpdateLinkDone(ctx context.Context, req *pb.UpdateLinkDoneRequest) (*pb.LinkDoneResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	d, l, err := h.svc.UpdateDone(ctx, req.LinkId, req.DoneId, userID, DoneInput{
		DoneOn: req.DoneOn, WithUserIDs: req.WithUserIds, WithChildIDs: req.WithChildIds,
		Rating: req.Rating, Note: req.Note, PhotoURL: req.PhotoUrl, Redo: req.Redo,
	})
	if err != nil {
		return nil, toStatus("update link done", err)
	}
	return &pb.LinkDoneResponse{Done: doneToProto(d), Link: h.one(ctx, l, userID)}, nil
}

func (h *Handler) UnmarkLinkDone(ctx context.Context, req *pb.UnmarkLinkDoneRequest) (*pb.UnmarkLinkDoneResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	l, err := h.svc.UnmarkDone(ctx, req.LinkId, req.DoneId, userID)
	if err != nil {
		return nil, toStatus("unmark link done", err)
	}
	return &pb.UnmarkLinkDoneResponse{Link: h.one(ctx, l, userID)}, nil
}

func (h *Handler) ListLinkDones(ctx context.Context, req *pb.ListLinkDonesRequest) (*pb.ListLinkDonesResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	dones, err := h.svc.ListDones(ctx, req.LinkId, userID)
	if err != nil {
		return nil, toStatus("list link dones", err)
	}
	out := make([]*pb.LinkDone, 0, len(dones))
	for _, d := range dones {
		out = append(out, doneToProto(d))
	}
	return &pb.ListLinkDonesResponse{Dones: out}, nil
}

func (h *Handler) DismissDonePrompt(ctx context.Context, req *pb.DismissDonePromptRequest) (*pb.DismissDonePromptResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	l, err := h.svc.DismissDonePrompt(ctx, req.LinkId, userID)
	if err != nil {
		return nil, toStatus("dismiss done prompt", err)
	}
	return &pb.DismissDonePromptResponse{Link: h.one(ctx, l, userID)}, nil
}

func (h *Handler) GetDoneSummary(ctx context.Context, req *pb.GetDoneSummaryRequest) (*pb.GetDoneSummaryResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "unauthenticated")
	}
	year := int(req.Year)
	if year == 0 {
		year = time.Now().Year()
	}
	sum, err := h.svc.DoneSummary(ctx, userID, year)
	if err != nil {
		return nil, toStatus("get done summary", err)
	}
	return &pb.GetDoneSummaryResponse{
		Year: sum.Year, Outings: sum.Outings, Recipes: sum.Recipes, Books: sum.Books,
		Total: sum.Total, BestMonth: sum.BestMonth, BestMonthCount: sum.BestMonthCount,
	}, nil
}
