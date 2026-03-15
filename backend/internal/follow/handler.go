package follow

import (
	"context"

	pb "github.com/tribbae/backend/gen/tribbae/v1"
	"github.com/tribbae/backend/internal/interceptor"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type Handler struct {
	pb.UnimplementedFollowServiceServer
	svc *Service
}

func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

// toProto converts a UserProfile to protobuf format
func (h *Handler) toProto(profile UserProfile) *pb.UserProfile {
	return &pb.UserProfile{
		Id:             profile.ID,
		DisplayName:    profile.DisplayName,
		Email:          profile.Email,
		IsAdmin:        profile.IsAdmin,
		FollowerCount:  profile.FollowerCount,
		FollowingCount: profile.FollowingCount,
	}
}

// Follow creates a follow relationship
func (h *Handler) Follow(ctx context.Context, req *pb.FollowRequest) (*pb.FollowResponse, error) {
	// Get authenticated user ID
	followerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "not authenticated")
	}

	// Validate request
	if req.UserId == "" {
		return nil, status.Errorf(codes.InvalidArgument, "user_id is required")
	}

	// Create follow relationship
	if err := h.svc.Follow(ctx, followerID, req.UserId); err != nil {
		if err.Error() == "cannot follow yourself" {
			return nil, status.Errorf(codes.InvalidArgument, "cannot follow yourself")
		}
		if err.Error() == "follower user not found" || err.Error() == "following user not found" {
			return nil, status.Errorf(codes.NotFound, "user not found")
		}
		return nil, status.Errorf(codes.Internal, "failed to follow user: %v", err)
	}

	return &pb.FollowResponse{}, nil
}

// Unfollow removes a follow relationship
func (h *Handler) Unfollow(ctx context.Context, req *pb.UnfollowRequest) (*pb.UnfollowResponse, error) {
	// Get authenticated user ID
	followerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "not authenticated")
	}

	// Validate request
	if req.UserId == "" {
		return nil, status.Errorf(codes.InvalidArgument, "user_id is required")
	}

	// Remove follow relationship
	if err := h.svc.Unfollow(ctx, followerID, req.UserId); err != nil {
		return nil, status.Errorf(codes.Internal, "failed to unfollow user: %v", err)
	}

	return &pb.UnfollowResponse{}, nil
}

// IsFollowing checks if the authenticated user is following another user
func (h *Handler) IsFollowing(ctx context.Context, req *pb.IsFollowingRequest) (*pb.IsFollowingResponse, error) {
	// Get authenticated user ID
	followerID, err := interceptor.UserIDFromContext(ctx)
	if err != nil {
		return nil, status.Errorf(codes.Unauthenticated, "not authenticated")
	}

	// Validate request
	if req.UserId == "" {
		return nil, status.Errorf(codes.InvalidArgument, "user_id is required")
	}

	// Check if following
	isFollowing, err := h.svc.IsFollowing(ctx, followerID, req.UserId)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to check follow status: %v", err)
	}

	return &pb.IsFollowingResponse{IsFollowing: isFollowing}, nil
}

// GetFollowers returns the list of users following the specified user
func (h *Handler) GetFollowers(ctx context.Context, req *pb.GetFollowersRequest) (*pb.GetFollowersResponse, error) {
	// Validate request
	if req.UserId == "" {
		return nil, status.Errorf(codes.InvalidArgument, "user_id is required")
	}

	// Get followers
	followers, err := h.svc.GetFollowers(ctx, req.UserId)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to get followers: %v", err)
	}

	// Convert to proto
	protoFollowers := make([]*pb.UserProfile, 0, len(followers))
	for _, follower := range followers {
		protoFollowers = append(protoFollowers, h.toProto(follower))
	}

	return &pb.GetFollowersResponse{Followers: protoFollowers}, nil
}

// GetFollowing returns the list of users that the specified user is following
func (h *Handler) GetFollowing(ctx context.Context, req *pb.GetFollowingRequest) (*pb.GetFollowingResponse, error) {
	// Validate request
	if req.UserId == "" {
		return nil, status.Errorf(codes.InvalidArgument, "user_id is required")
	}

	// Get following
	following, err := h.svc.GetFollowing(ctx, req.UserId)
	if err != nil {
		return nil, status.Errorf(codes.Internal, "failed to get following: %v", err)
	}

	// Convert to proto
	protoFollowing := make([]*pb.UserProfile, 0, len(following))
	for _, user := range following {
		protoFollowing = append(protoFollowing, h.toProto(user))
	}

	return &pb.GetFollowingResponse{Following: protoFollowing}, nil
}

