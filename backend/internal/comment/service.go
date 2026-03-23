package comment

import (
	"context"
	"errors"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
)

// Comment represents a comment on a link
type Comment struct {
	ID        primitive.ObjectID `bson:"_id,omitempty"`
	LinkID    string             `bson:"link_id"`
	UserID    string             `bson:"user_id"`
	Text      string             `bson:"text"`
	CreatedAt time.Time          `bson:"created_at"`
	UpdatedAt time.Time          `bson:"updated_at"`
}

// CommentWithUser represents a comment with user information
type CommentWithUser struct {
	ID              string
	LinkID          string
	UserID          string
	UserDisplayName string
	UserIsAdmin     bool
	Text            string
	CreatedAt       time.Time
	UpdatedAt       time.Time
}

// Service handles comment-related operations
type Service struct {
	col     *mongo.Collection
	linkCol *mongo.Collection
	userCol *mongo.Collection
}

// NewService creates a new comment service
func NewService(col *mongo.Collection, linkCol *mongo.Collection, userCol *mongo.Collection) *Service {
	return &Service{
		col:     col,
		linkCol: linkCol,
		userCol: userCol,
	}
}

// CreateComment creates a new comment on a link
func (s *Service) CreateComment(ctx context.Context, linkID, userID, text string) (*CommentWithUser, error) {
	// Validate input
	if text == "" {
		return nil, errors.New("comment text is required")
	}

	// Verify link exists
	linkOID, err := primitive.ObjectIDFromHex(linkID)
	if err != nil {
		return nil, errors.New("invalid link id")
	}

	count, err := s.linkCol.CountDocuments(ctx, bson.M{"_id": linkOID})
	if err != nil {
		return nil, err
	}
	if count == 0 {
		return nil, errors.New("link not found")
	}

	// Create comment
	comment := &Comment{
		ID:        primitive.NewObjectID(),
		LinkID:    linkID,
		UserID:    userID,
		Text:      text,
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}

	_, err = s.col.InsertOne(ctx, comment)
	if err != nil {
		return nil, err
	}

	// Get user info and return comment with user details
	return s.getCommentWithUser(ctx, comment)
}

// GetComments returns all comments for a link, sorted by created_at descending (newest first)
func (s *Service) GetComments(ctx context.Context, linkID string) ([]CommentWithUser, error) {
	// Sort by created_at descending (newest first)
	opts := options.Find().SetSort(bson.D{{Key: "created_at", Value: -1}})
	
	cursor, err := s.col.Find(ctx, bson.M{"link_id": linkID}, opts)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)

	var comments []Comment
	if err := cursor.All(ctx, &comments); err != nil {
		return nil, err
	}

	// Convert to CommentWithUser
	result := make([]CommentWithUser, 0, len(comments))
	for _, comment := range comments {
		commentWithUser, err := s.getCommentWithUser(ctx, &comment)
		if err != nil {
			// Skip comments where user info cannot be retrieved
			continue
		}
		result = append(result, *commentWithUser)
	}

	return result, nil
}

// DeleteComment deletes a comment
// Authorization: comment author or link owner can delete
func (s *Service) DeleteComment(ctx context.Context, commentID, userID string) error {
	// Get the comment
	commentOID, err := primitive.ObjectIDFromHex(commentID)
	if err != nil {
		return errors.New("invalid comment id")
	}

	var comment Comment
	err = s.col.FindOne(ctx, bson.M{"_id": commentOID}).Decode(&comment)
	if err != nil {
		if err == mongo.ErrNoDocuments {
			return errors.New("comment not found")
		}
		return err
	}

	// Check if user is the comment author
	if comment.UserID == userID {
		// Author can delete their own comment
		_, err := s.col.DeleteOne(ctx, bson.M{"_id": commentOID})
		return err
	}

	// Check if user is the link owner
	linkOID, err := primitive.ObjectIDFromHex(comment.LinkID)
	if err != nil {
		return errors.New("invalid link id")
	}

	var link struct {
		OwnerID string `bson:"owner_id"`
	}
	err = s.linkCol.FindOne(ctx, bson.M{"_id": linkOID}).Decode(&link)
	if err != nil {
		return errors.New("link not found")
	}

	if link.OwnerID == userID {
		// Link owner can delete any comment on their link
		_, err := s.col.DeleteOne(ctx, bson.M{"_id": commentOID})
		return err
	}

	// User is neither author nor link owner
	return errors.New("not authorized to delete this comment")
}

// GetCommentCount returns the number of comments for a link
func (s *Service) GetCommentCount(ctx context.Context, linkID string) (int32, error) {
	count, err := s.col.CountDocuments(ctx, bson.M{"link_id": linkID})
	if err != nil {
		return 0, err
	}
	return int32(count), nil
}

// getCommentWithUser retrieves user information and combines it with comment data
func (s *Service) getCommentWithUser(ctx context.Context, comment *Comment) (*CommentWithUser, error) {
	userOID, err := primitive.ObjectIDFromHex(comment.UserID)
	if err != nil {
		return nil, errors.New("invalid user id")
	}

	var user struct {
		DisplayName string `bson:"display_name"`
		IsAdmin     bool   `bson:"is_admin"`
	}

	err = s.userCol.FindOne(ctx, bson.M{"_id": userOID}).Decode(&user)
	if err != nil {
		// Return empty display name if user not found
		user.DisplayName = ""
		user.IsAdmin = false
	}

	return &CommentWithUser{
		ID:              comment.ID.Hex(),
		LinkID:          comment.LinkID,
		UserID:          comment.UserID,
		UserDisplayName: user.DisplayName,
		UserIsAdmin:     user.IsAdmin,
		Text:            comment.Text,
		CreatedAt:       comment.CreatedAt,
		UpdatedAt:       comment.UpdatedAt,
	}, nil
}
