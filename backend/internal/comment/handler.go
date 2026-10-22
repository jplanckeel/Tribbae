package comment

import (
	"context"
	"log"
	"strings"
	"unicode/utf8"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"github.com/tribbae/backend/internal/interceptor"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"
)

// maxCommentLen borne la taille d'un commentaire (en caractères).
const maxCommentLen = 2000

// LinkVisibility indique si un utilisateur peut voir un lien.
type LinkVisibility func(ctx context.Context, linkID, userID string) bool

type Handler struct {
	pb.UnimplementedCommentServiceServer
	svc     *Service
	canView LinkVisibility
}

// NewHandler crée le handler ; canView restreint les commentaires aux liens
// que l'utilisateur a le droit de voir (nil = pas de restriction).
func NewHandler(svc *Service, canView LinkVisibility) *Handler {
	return &Handler{svc: svc, canView: canView}
}

func (h *Handler) visible(ctx context.Context, linkID string) bool {
	if h.canView == nil {
		return true
	}
	userID, _ := interceptor.UserIDFromContext(ctx)
	return h.canView(ctx, linkID, userID)
}

// toProto converts a CommentWithUser to protobuf format
func (h *Handler) toProto(comment CommentWithUser) *pb.Comment {
	return &pb.Comment{
		Id:              comment.ID,
		LinkId:          comment.LinkID,
		UserId:          comment.UserID,
		UserDisplayName: comment.UserDisplayName,
		UserIsAdmin:     comment.UserIsAdmin,
		Text:            comment.Text,
		CreatedAt:       timestamppb.New(comment.CreatedAt),
		UpdatedAt:       timestamppb.New(comment.UpdatedAt),
	}
}

// CreateComment creates a new comment on a link
func (h *Handler) CreateComment(ctx context.Context, req *pb.CreateCommentRequest) (*pb.CreateCommentResponse, error) {
	// Get authenticated user ID
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "not authenticated")
	}

	// Validate request
	if req.LinkId == "" {
		return nil, status.Errorf(codes.InvalidArgument, "link_id is required")
	}
	req.Text = strings.TrimSpace(req.Text)
	if req.Text == "" {
		return nil, status.Errorf(codes.InvalidArgument, "text is required")
	}
	if utf8.RuneCountInString(req.Text) > maxCommentLen {
		return nil, status.Errorf(codes.InvalidArgument, "comment is too long")
	}
	if !h.visible(ctx, req.LinkId) {
		return nil, status.Errorf(codes.NotFound, "link not found")
	}

	// Create comment
	comment, err := h.svc.CreateComment(ctx, req.LinkId, userID, req.Text)
	if err != nil {
		log.Printf("ERROR: Failed to create comment: %v", err)
		if err.Error() == "comment text is required" {
			return nil, status.Errorf(codes.InvalidArgument, "comment text is required")
		}
		if err.Error() == "invalid link id" {
			return nil, status.Errorf(codes.InvalidArgument, "invalid link id")
		}
		if err.Error() == "link not found" {
			return nil, status.Errorf(codes.NotFound, "link not found")
		}
		return nil, status.Errorf(codes.Internal, "failed to create comment")
	}

	log.Printf("INFO: Comment created - commentID=%s, linkID=%s, userID=%s", comment.ID, comment.LinkID, comment.UserID)
	return &pb.CreateCommentResponse{Comment: h.toProto(*comment)}, nil
}

// GetComments returns all comments for a link
func (h *Handler) GetComments(ctx context.Context, req *pb.GetCommentsRequest) (*pb.GetCommentsResponse, error) {
	// Validate request
	if req.LinkId == "" {
		return nil, status.Errorf(codes.InvalidArgument, "link_id is required")
	}

	if !h.visible(ctx, req.LinkId) {
		return nil, status.Errorf(codes.NotFound, "link not found")
	}

	// Get comments
	comments, err := h.svc.GetComments(ctx, req.LinkId)
	if err != nil {
		log.Printf("ERROR: Failed to get comments: %v", err)
		return nil, status.Errorf(codes.Internal, "failed to get comments")
	}

	// Convert to proto
	protoComments := make([]*pb.Comment, 0, len(comments))
	for _, comment := range comments {
		protoComments = append(protoComments, h.toProto(comment))
	}

	return &pb.GetCommentsResponse{Comments: protoComments}, nil
}

// DeleteComment deletes a comment
func (h *Handler) DeleteComment(ctx context.Context, req *pb.DeleteCommentRequest) (*pb.DeleteCommentResponse, error) {
	// Get authenticated user ID
	userID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "not authenticated")
	}

	// Validate request
	if req.CommentId == "" {
		return nil, status.Errorf(codes.InvalidArgument, "comment_id is required")
	}

	// Delete comment
	if err := h.svc.DeleteComment(ctx, req.CommentId, userID); err != nil {
		log.Printf("ERROR: Failed to delete comment: %v", err)
		if err.Error() == "invalid comment id" {
			return nil, status.Errorf(codes.InvalidArgument, "invalid comment id")
		}
		if err.Error() == "comment not found" {
			return nil, status.Errorf(codes.NotFound, "comment not found")
		}
		if err.Error() == "not authorized to delete this comment" {
			return nil, status.Errorf(codes.PermissionDenied, "not authorized to delete this comment")
		}
		return nil, status.Errorf(codes.Internal, "failed to delete comment")
	}

	log.Printf("INFO: Comment deleted - commentID=%s, userID=%s", req.CommentId, userID)
	return &pb.DeleteCommentResponse{}, nil
}

// GetCommentCount returns the number of comments for a link
func (h *Handler) GetCommentCount(ctx context.Context, req *pb.GetCommentCountRequest) (*pb.GetCommentCountResponse, error) {
	// Validate request
	if req.LinkId == "" {
		return nil, status.Errorf(codes.InvalidArgument, "link_id is required")
	}

	if !h.visible(ctx, req.LinkId) {
		return nil, status.Errorf(codes.NotFound, "link not found")
	}

	// Get comment count
	count, err := h.svc.GetCommentCount(ctx, req.LinkId)
	if err != nil {
		log.Printf("ERROR: Failed to get comment count: %v", err)
		return nil, status.Errorf(codes.Internal, "failed to get comment count")
	}

	return &pb.GetCommentCountResponse{Count: count}, nil
}
