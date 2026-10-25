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

	return s.withUsers(ctx, comments), nil
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
	out := s.withUsers(ctx, []Comment{*comment})
	if len(out) == 0 {
		return nil, errors.New("invalid user id")
	}
	return &out[0], nil
}

// withUsers joint à chaque commentaire le nom et le statut de son auteur, en
// une seule requête (seulement ces deux champs). Les commentaires dont
// l'auteur n'a pas un id valide sont écartés ; un auteur introuvable (compte
// supprimé) donne un nom vide.
func (s *Service) withUsers(ctx context.Context, comments []Comment) []CommentWithUser {
	type author struct {
		ID          primitive.ObjectID `bson:"_id"`
		DisplayName string             `bson:"display_name"`
		IsAdmin     bool               `bson:"is_admin"`
	}
	oids := make([]primitive.ObjectID, 0, len(comments))
	seen := map[primitive.ObjectID]bool{}
	for _, c := range comments {
		if oid, err := primitive.ObjectIDFromHex(c.UserID); err == nil && !seen[oid] {
			seen[oid] = true
			oids = append(oids, oid)
		}
	}
	authors := map[string]author{}
	if len(oids) > 0 {
		proj := options.Find().SetProjection(bson.M{"display_name": 1, "is_admin": 1})
		if cur, err := s.userCol.Find(ctx, bson.M{"_id": bson.M{"$in": oids}}, proj); err == nil {
			var rows []author
			if cur.All(ctx, &rows) == nil {
				for _, r := range rows {
					authors[r.ID.Hex()] = r
				}
			}
		}
	}

	result := make([]CommentWithUser, 0, len(comments))
	for _, c := range comments {
		if _, err := primitive.ObjectIDFromHex(c.UserID); err != nil {
			continue
		}
		a := authors[c.UserID]
		result = append(result, CommentWithUser{
			ID:              c.ID.Hex(),
			LinkID:          c.LinkID,
			UserID:          c.UserID,
			UserDisplayName: a.DisplayName,
			UserIsAdmin:     a.IsAdmin,
			Text:            c.Text,
			CreatedAt:       c.CreatedAt,
			UpdatedAt:       c.UpdatedAt,
		})
	}
	return result
}
