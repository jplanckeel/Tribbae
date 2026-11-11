package comment

import (
	"context"
	"errors"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// Comment represents a comment on a link
type Comment struct {
	ID        bson.ObjectID `bson:"_id,omitempty"`
	LinkID    string        `bson:"link_id"`
	UserID    string        `bson:"user_id"`
	Text      string        `bson:"text"`
	CreatedAt time.Time     `bson:"created_at"`
	UpdatedAt time.Time     `bson:"updated_at"`
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
	peers   func(ctx context.Context, userID string) []string
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
	linkOID, err := bson.ObjectIDFromHex(linkID)
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
		ID:        bson.NewObjectID(),
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

// Erreurs de DeleteComment (typées : le handler les traduit en codes gRPC).
var (
	ErrInvalidID  = errors.New("invalid comment id")
	ErrNotFound   = errors.New("comment not found")
	ErrNotAllowed = errors.New("not authorized to delete this comment")
)

// SetTribePeers branche la tribu : peers(userID) retourne les comptes dont
// userID gère le contenu comme le sien (lui compris).
func (s *Service) SetTribePeers(peers func(ctx context.Context, userID string) []string) {
	s.peers = peers
}

// DeleteComment supprime un commentaire. Règle unique, la même sur les deux
// clients : son auteur, ou l'auteur de l'idée et sa tribu (qui gèrent ses
// idées comme les leurs).
func (s *Service) DeleteComment(ctx context.Context, commentID, userID string) error {
	commentOID, err := bson.ObjectIDFromHex(commentID)
	if err != nil {
		return ErrInvalidID
	}
	var comment Comment
	if err := s.col.FindOne(ctx, bson.M{"_id": commentOID}).Decode(&comment); err != nil {
		if errors.Is(err, mongo.ErrNoDocuments) {
			return ErrNotFound
		}
		return err
	}
	if comment.UserID != userID && !s.managesIdea(ctx, comment.LinkID, userID) {
		return ErrNotAllowed
	}
	_, err = s.col.DeleteOne(ctx, bson.M{"_id": commentOID})
	return err
}

// managesIdea : userID est l'auteur de l'idée ou de sa tribu.
func (s *Service) managesIdea(ctx context.Context, linkID, userID string) bool {
	linkOID, err := bson.ObjectIDFromHex(linkID)
	if err != nil {
		return false
	}
	var link struct {
		OwnerID string `bson:"owner_id"`
	}
	if err := s.linkCol.FindOne(ctx, bson.M{"_id": linkOID}).Decode(&link); err != nil {
		return false
	}
	if link.OwnerID == userID {
		return true
	}
	if s.peers == nil {
		return false
	}
	for _, p := range s.peers(ctx, userID) {
		if p == link.OwnerID {
			return true
		}
	}
	return false
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
		ID          bson.ObjectID `bson:"_id"`
		DisplayName string        `bson:"display_name"`
		IsAdmin     bool          `bson:"is_admin"`
	}
	oids := make([]bson.ObjectID, 0, len(comments))
	seen := map[bson.ObjectID]bool{}
	for _, c := range comments {
		if oid, err := bson.ObjectIDFromHex(c.UserID); err == nil && !seen[oid] {
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
		if _, err := bson.ObjectIDFromHex(c.UserID); err != nil {
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
