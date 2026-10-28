package oauth

import (
	"context"
	"errors"
	"log"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"github.com/tribbae/backend/internal/interceptor"
)

// AppIdeas compte et efface les idées qu'une application a ajoutées
// (link.Service).
type AppIdeas interface {
	CountCreatedByClient(ctx context.Context, ownerID, clientID string) (int64, error)
	DeleteCreatedByClient(ctx context.Context, ownerID, clientID string) (int64, error)
}

// AppsHandler sert l'écran « Applications connectées » (ConnectedAppService),
// avec la session de l'app : un jeton MCP n'y a jamais accès.
type AppsHandler struct {
	svc   *Service
	ideas AppIdeas
}

func NewAppsHandler(svc *Service, ideas AppIdeas) *AppsHandler {
	return &AppsHandler{svc: svc, ideas: ideas}
}

func (h *AppsHandler) ListConnectedApps(ctx context.Context, _ *pb.ListConnectedAppsRequest) (*pb.ListConnectedAppsResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "not authenticated")
	}
	grants, err := h.svc.ListGrants(ctx, userID)
	if err != nil {
		log.Printf("oauth: list grants: %v", err)
		return nil, status.Error(codes.Internal, "failed to list connected apps")
	}
	resp := &pb.ListConnectedAppsResponse{Apps: make([]*pb.ConnectedApp, 0, len(grants))}
	for _, g := range grants {
		n, err := h.ideas.CountCreatedByClient(ctx, userID, g.ClientID)
		if err != nil {
			log.Printf("oauth: count ideas: %v", err)
		}
		resp.Apps = append(resp.Apps, &pb.ConnectedApp{
			Id:          g.ID.Hex(),
			Name:        g.ClientName,
			Site:        g.RedirectHost,
			Scopes:      g.Scopes,
			ConnectedAt: timestamppb.New(g.CreatedAt),
			LastUsedAt:  timestamppb.New(g.LastUsedAt),
			ExpiresAt:   timestamppb.New(g.ExpiresAt),
			IdeaCount:   int32(n),
		})
	}
	return resp, nil
}

func (h *AppsHandler) RevokeConnectedApp(ctx context.Context, req *pb.RevokeConnectedAppRequest) (*pb.RevokeConnectedAppResponse, error) {
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, "not authenticated")
	}
	g, err := h.svc.GrantOf(ctx, userID, req.GetId())
	if errors.Is(err, ErrGrantNotFound) {
		return nil, status.Error(codes.NotFound, "connected app not found")
	}
	if err != nil {
		log.Printf("oauth: find grant: %v", err)
		return nil, status.Error(codes.Internal, "failed to revoke the connected app")
	}
	resp := &pb.RevokeConnectedAppResponse{}
	// Les idées d'abord : si leur effacement échoue, l'accès est encore là et
	// une nouvelle demande reprend tout
	deleteIdeas := func() error {
		if !req.GetDeleteIdeas() {
			return nil
		}
		n, err := h.ideas.DeleteCreatedByClient(ctx, userID, g.ClientID)
		resp.DeletedIdeas += int32(n)
		return err
	}
	if err := deleteIdeas(); err != nil {
		log.Printf("oauth: delete app ideas: %v", err)
		return nil, status.Error(codes.Internal, "failed to delete the ideas of the connected app, please retry")
	}
	if err := h.svc.RevokeGrant(ctx, g); err != nil {
		log.Printf("oauth: revoke grant: %v", err)
		return nil, status.Error(codes.Internal, "failed to revoke the connected app")
	}
	// Une idée ajoutée entre les deux étapes
	if err := deleteIdeas(); err != nil {
		log.Printf("oauth: delete app ideas: %v", err)
	}
	return resp, nil
}
