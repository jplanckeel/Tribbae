package follow

import (
	"context"
	"errors"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
)

// Follow represents a follow relationship between two users
type Follow struct {
	ID          primitive.ObjectID `bson:"_id,omitempty"`
	FollowerID  string             `bson:"follower_id"`  // User who follows
	FollowingID string             `bson:"following_id"` // User being followed
	CreatedAt   time.Time          `bson:"created_at"`
}

// UserProfile represents a user's public profile information
type UserProfile struct {
	ID             string
	DisplayName    string
	Email          string
	IsAdmin        bool
	FollowerCount  int32
	FollowingCount int32
}

// Service handles follow-related operations
type Service struct {
	col     *mongo.Collection
	userCol *mongo.Collection
}

// NewService creates a new follow service
func NewService(col *mongo.Collection, userCol *mongo.Collection) *Service {
	return &Service{
		col:     col,
		userCol: userCol,
	}
}

// Follow creates a follow relationship between followerID and followingID
func (s *Service) Follow(ctx context.Context, followerID, followingID string) error {
	// Validate that user is not trying to follow themselves
	if followerID == followingID {
		return errors.New("cannot follow yourself")
	}

	// Check if both users exist
	if err := s.validateUserExists(ctx, followerID); err != nil {
		return errors.New("follower user not found")
	}
	if err := s.validateUserExists(ctx, followingID); err != nil {
		return errors.New("following user not found")
	}

	// Check if follow relationship already exists
	existing, err := s.IsFollowing(ctx, followerID, followingID)
	if err != nil {
		return err
	}
	if existing {
		// Idempotent: already following, return success
		return nil
	}

	// Create follow relationship
	follow := &Follow{
		ID:          primitive.NewObjectID(),
		FollowerID:  followerID,
		FollowingID: followingID,
		CreatedAt:   time.Now(),
	}

	_, err = s.col.InsertOne(ctx, follow)
	return err
}

// Unfollow removes a follow relationship between followerID and followingID
func (s *Service) Unfollow(ctx context.Context, followerID, followingID string) error {
	filter := bson.M{
		"follower_id":  followerID,
		"following_id": followingID,
	}

	result, err := s.col.DeleteOne(ctx, filter)
	if err != nil {
		return err
	}

	// Idempotent: if no relationship existed, still return success
	if result.DeletedCount == 0 {
		return nil
	}

	return nil
}

// IsFollowing checks if followerID is following followingID
func (s *Service) IsFollowing(ctx context.Context, followerID, followingID string) (bool, error) {
	filter := bson.M{
		"follower_id":  followerID,
		"following_id": followingID,
	}

	count, err := s.col.CountDocuments(ctx, filter)
	if err != nil {
		return false, err
	}

	return count > 0, nil
}

// GetFollowers returns the list of users following the specified user
func (s *Service) GetFollowers(ctx context.Context, userID string) ([]UserProfile, error) {
	// Find all follow relationships where userID is being followed
	filter := bson.M{"following_id": userID}
	cursor, err := s.col.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)

	var follows []Follow
	if err := cursor.All(ctx, &follows); err != nil {
		return nil, err
	}

	// Get user profiles for all followers
	profiles := make([]UserProfile, 0, len(follows))
	for _, follow := range follows {
		profile, err := s.getUserProfile(ctx, follow.FollowerID)
		if err != nil {
			// Skip users that no longer exist
			continue
		}
		profiles = append(profiles, profile)
	}

	return profiles, nil
}

// GetFollowing returns the list of users that the specified user is following
func (s *Service) GetFollowing(ctx context.Context, userID string) ([]UserProfile, error) {
	// Find all follow relationships where userID is the follower
	filter := bson.M{"follower_id": userID}
	cursor, err := s.col.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)

	var follows []Follow
	if err := cursor.All(ctx, &follows); err != nil {
		return nil, err
	}

	// Get user profiles for all users being followed
	profiles := make([]UserProfile, 0, len(follows))
	for _, follow := range follows {
		profile, err := s.getUserProfile(ctx, follow.FollowingID)
		if err != nil {
			// Skip users that no longer exist
			continue
		}
		profiles = append(profiles, profile)
	}

	return profiles, nil
}

// GetFollowerCount returns the number of followers for a user
func (s *Service) GetFollowerCount(ctx context.Context, userID string) (int32, error) {
	filter := bson.M{"following_id": userID}
	count, err := s.col.CountDocuments(ctx, filter)
	if err != nil {
		return 0, err
	}
	return int32(count), nil
}

// GetFollowingCount returns the number of users that a user is following
func (s *Service) GetFollowingCount(ctx context.Context, userID string) (int32, error) {
	filter := bson.M{"follower_id": userID}
	count, err := s.col.CountDocuments(ctx, filter)
	if err != nil {
		return 0, err
	}
	return int32(count), nil
}

// validateUserExists checks if a user exists in the database
func (s *Service) validateUserExists(ctx context.Context, userID string) error {
	oid, err := primitive.ObjectIDFromHex(userID)
	if err != nil {
		return errors.New("invalid user id")
	}

	count, err := s.userCol.CountDocuments(ctx, bson.M{"_id": oid})
	if err != nil {
		return err
	}

	if count == 0 {
		return errors.New("user not found")
	}

	return nil
}

// getUserProfile retrieves a user's profile information
func (s *Service) getUserProfile(ctx context.Context, userID string) (UserProfile, error) {
	oid, err := primitive.ObjectIDFromHex(userID)
	if err != nil {
		return UserProfile{}, errors.New("invalid user id")
	}

	var user struct {
		ID          primitive.ObjectID `bson:"_id"`
		DisplayName string             `bson:"display_name"`
		Email       string             `bson:"email"`
		IsAdmin     bool               `bson:"is_admin"`
	}

	err = s.userCol.FindOne(ctx, bson.M{"_id": oid}).Decode(&user)
	if err != nil {
		return UserProfile{}, err
	}

	// Get follower and following counts
	followerCount, err := s.GetFollowerCount(ctx, userID)
	if err != nil {
		followerCount = 0
	}

	followingCount, err := s.GetFollowingCount(ctx, userID)
	if err != nil {
		followingCount = 0
	}

	return UserProfile{
		ID:             user.ID.Hex(),
		DisplayName:    user.DisplayName,
		Email:          user.Email,
		IsAdmin:        user.IsAdmin,
		FollowerCount:  followerCount,
		FollowingCount: followingCount,
	}, nil
}

