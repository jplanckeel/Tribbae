// Package moderation : signalements (idée, commentaire, compte), blocage
// entre comptes et décisions de l'administration (retrait, suspension).
package moderation

import (
	"context"
	"errors"
	"strings"
	"time"
	"unicode/utf8"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

var (
	ErrInvalidInput = errors.New("invalid input")
	ErrNotFound     = errors.New("not found")
	// ErrTooMany : trop de signalements envoyés sur 24 h
	ErrTooMany = errors.New("too many reports")
	// ErrOwnContent : on ne signale pas son propre contenu ni celui de sa tribu
	ErrOwnContent = errors.New("cannot report your own content")
	// ErrProtected : un compte administrateur ne peut pas être suspendu
	ErrProtected = errors.New("admin accounts cannot be suspended")
)

const (
	TargetIdea    = "idea"
	TargetComment = "comment"
	TargetAccount = "account"

	StatusOpen      = "open"
	StatusResolved  = "resolved"
	StatusDismissed = "dismissed"

	ActionDismiss = "dismiss"
	ActionRemove  = "remove"
	ActionSuspend = "suspend"

	// dailyReports : signalements par compte sur 24 h (au-delà, refus)
	dailyReports = 20
	maxDetails   = 500
	maxSummary   = 200
	adminListMax = 200
)

var reasons = map[string]bool{"spam": true, "inappropriate": true, "harassment": true, "other": true}

// Report : un signalement. Le résumé garde le contenu tel qu'il était.
type Report struct {
	ID            bson.ObjectID `bson:"_id,omitempty"`
	ReporterID    string        `bson:"reporter_id"`
	TargetType    string        `bson:"target_type"`
	TargetID      string        `bson:"target_id"`
	TargetOwnerID string        `bson:"target_owner_id"`
	TargetSummary string        `bson:"target_summary"`
	LinkID        string        `bson:"link_id,omitempty"`
	Reason        string        `bson:"reason"`
	Details       string        `bson:"details,omitempty"`
	Status        string        `bson:"status"`
	Action        string        `bson:"action,omitempty"`
	ResolvedBy    string        `bson:"resolved_by,omitempty"`
	CreatedAt     time.Time     `bson:"created_at"`
	ResolvedAt    *time.Time    `bson:"resolved_at,omitempty"`
}

// Notifier prévient l'administration d'un nouveau signalement.
type Notifier interface {
	ReportReceived(targetType, reason string)
}

type Service struct {
	reports, blocks, links, comments, users, folders *mongo.Collection
	notify                                           Notifier
	// canView : l'idée est visible de ce compte (mêmes règles que l'app)
	canView func(ctx context.Context, linkID, userID string) bool
	// peers : le compte et sa tribu
	peers func(ctx context.Context, userID string) []string
}

func NewService(db *mongo.Database) *Service {
	return &Service{
		reports:  db.Collection("reports"),
		blocks:   db.Collection("blocks"),
		links:    db.Collection("links"),
		comments: db.Collection("comments"),
		users:    db.Collection("users"),
		folders:  db.Collection("folders"),
	}
}

// SetAccess branche les règles de visibilité : on ne signale que ce qu'on peut
// voir, et jamais le contenu de sa propre tribu.
func (s *Service) SetAccess(canView func(ctx context.Context, linkID, userID string) bool, peers func(ctx context.Context, userID string) []string) {
	s.canView, s.peers = canView, peers
}

func (s *Service) SetNotifier(n Notifier) { s.notify = n }

func truncate(text string, n int) string {
	text = strings.TrimSpace(text)
	if utf8.RuneCountInString(text) <= n {
		return text
	}
	return string([]rune(text)[:n])
}

// target retrouve le contenu signalé : son auteur, un résumé, et l'idée
// commentée pour un commentaire.
func (s *Service) target(ctx context.Context, kind, id string) (owner, summary, linkID string, err error) {
	oid, err := bson.ObjectIDFromHex(id)
	if err != nil {
		return "", "", "", ErrNotFound
	}
	switch kind {
	case TargetIdea:
		var l struct {
			OwnerID string `bson:"owner_id"`
			Title   string `bson:"title"`
		}
		err = s.links.FindOne(ctx, bson.M{"_id": oid}).Decode(&l)
		owner, summary = l.OwnerID, l.Title
	case TargetComment:
		var c struct {
			UserID string `bson:"user_id"`
			Text   string `bson:"text"`
			LinkID string `bson:"link_id"`
		}
		err = s.comments.FindOne(ctx, bson.M{"_id": oid}).Decode(&c)
		owner, summary, linkID = c.UserID, c.Text, c.LinkID
	case TargetAccount:
		var u struct {
			DisplayName string `bson:"display_name"`
		}
		err = s.users.FindOne(ctx, bson.M{"_id": oid}, options.FindOne().SetProjection(bson.M{"display_name": 1})).Decode(&u)
		owner, summary = id, u.DisplayName
	default:
		return "", "", "", ErrInvalidInput
	}
	if errors.Is(err, mongo.ErrNoDocuments) {
		return "", "", "", ErrNotFound
	}
	return owner, truncate(summary, maxSummary), linkID, err
}

// Report enregistre un signalement. Un second signalement du même contenu par
// le même compte remplace le premier tant qu'il n'est pas traité.
func (s *Service) Report(ctx context.Context, reporterID, kind, targetID, reason, details string) error {
	if !reasons[reason] {
		return ErrInvalidInput
	}
	owner, summary, linkID, err := s.target(ctx, kind, targetID)
	if err != nil {
		return err
	}
	if s.ownedByTribe(ctx, reporterID, owner) {
		return ErrOwnContent
	}
	// Un contenu qu'on ne voit pas est inconnu : même réponse que s'il n'existait
	// pas, et son titre n'est jamais recopié dans un signalement
	if kind == TargetIdea {
		linkID = targetID
	}
	if linkID != "" && s.canView != nil && !s.canView(ctx, linkID, reporterID) {
		return ErrNotFound
	}
	if kind == TargetIdea {
		linkID = ""
	}
	n, err := s.reports.CountDocuments(ctx, bson.M{"reporter_id": reporterID, "created_at": bson.M{"$gte": time.Now().Add(-24 * time.Hour)}})
	if err != nil {
		return err
	}
	if n >= dailyReports {
		return ErrTooMany
	}
	res, err := s.reports.UpdateOne(ctx,
		bson.M{"reporter_id": reporterID, "target_type": kind, "target_id": targetID, "status": StatusOpen},
		bson.M{
			"$set": bson.M{"reason": reason, "details": truncate(details, maxDetails), "target_owner_id": owner,
				"target_summary": summary, "link_id": linkID},
			"$setOnInsert": bson.M{"created_at": time.Now()},
		},
		options.UpdateOne().SetUpsert(true))
	if err != nil {
		return err
	}
	if res.UpsertedCount == 1 && s.notify != nil {
		s.notify.ReportReceived(kind, reason)
	}
	return nil
}

// Block : userID ne voit plus les idées ni les commentaires de blockedID.
func (s *Service) Block(ctx context.Context, userID, blockedID string) error {
	if userID == blockedID {
		return ErrInvalidInput
	}
	if _, _, _, err := s.target(ctx, TargetAccount, blockedID); err != nil {
		return err
	}
	_, err := s.blocks.UpdateOne(ctx, bson.M{"user_id": userID, "blocked_id": blockedID},
		bson.M{"$setOnInsert": bson.M{"created_at": time.Now()}}, options.UpdateOne().SetUpsert(true))
	return err
}

func (s *Service) Unblock(ctx context.Context, userID, blockedID string) error {
	_, err := s.blocks.DeleteOne(ctx, bson.M{"user_id": userID, "blocked_id": blockedID})
	return err
}

// Blocked : un compte bloqué, avec son nom.
type Blocked struct {
	UserID      string
	DisplayName string
	BlockedAt   time.Time
}

func (s *Service) ListBlocked(ctx context.Context, userID string) ([]Blocked, error) {
	cur, err := s.blocks.Find(ctx, bson.M{"user_id": userID}, options.Find().SetSort(bson.D{{Key: "created_at", Value: -1}}))
	if err != nil {
		return nil, err
	}
	var rows []struct {
		BlockedID string    `bson:"blocked_id"`
		CreatedAt time.Time `bson:"created_at"`
	}
	if err := cur.All(ctx, &rows); err != nil {
		return nil, err
	}
	ids := make([]string, 0, len(rows))
	for _, r := range rows {
		ids = append(ids, r.BlockedID)
	}
	names := s.names(ctx, ids)
	out := make([]Blocked, 0, len(rows))
	for _, r := range rows {
		out = append(out, Blocked{UserID: r.BlockedID, DisplayName: names[r.BlockedID], BlockedAt: r.CreatedAt})
	}
	return out, nil
}

// BlockedSet : comptes bloqués par userID (vide sans session). Une seule requête.
func (s *Service) BlockedSet(ctx context.Context, userID string) map[string]bool {
	out := map[string]bool{}
	if userID == "" {
		return out
	}
	cur, err := s.blocks.Find(ctx, bson.M{"user_id": userID}, options.Find().SetProjection(bson.M{"blocked_id": 1}))
	if err != nil {
		return out
	}
	var rows []struct {
		BlockedID string `bson:"blocked_id"`
	}
	if cur.All(ctx, &rows) == nil {
		for _, r := range rows {
			out[r.BlockedID] = true
		}
	}
	return out
}

// names : noms affichés des comptes, en une requête.
func (s *Service) names(ctx context.Context, ids []string) map[string]string {
	out := map[string]string{}
	oids := make([]bson.ObjectID, 0, len(ids))
	for _, id := range ids {
		if oid, err := bson.ObjectIDFromHex(id); err == nil {
			oids = append(oids, oid)
		}
	}
	if len(oids) == 0 {
		return out
	}
	cur, err := s.users.Find(ctx, bson.M{"_id": bson.M{"$in": oids}}, options.Find().SetProjection(bson.M{"display_name": 1}))
	if err != nil {
		return out
	}
	var rows []struct {
		ID          bson.ObjectID `bson:"_id"`
		DisplayName string        `bson:"display_name"`
	}
	if cur.All(ctx, &rows) == nil {
		for _, r := range rows {
			out[r.ID.Hex()] = r.DisplayName
		}
	}
	return out
}

// AdminReport : un signalement avec les noms de l'auteur du contenu et du signaleur.
type AdminReport struct {
	Report
	OwnerName    string
	ReporterName string
}

// ListReports : signalements ouverts (ou traités), les plus récents d'abord.
func (s *Service) ListReports(ctx context.Context, closed bool) ([]AdminReport, error) {
	filter := bson.M{"status": StatusOpen}
	if closed {
		filter = bson.M{"status": bson.M{"$in": bson.A{StatusResolved, StatusDismissed}}}
	}
	cur, err := s.reports.Find(ctx, filter, options.Find().SetSort(bson.D{{Key: "status", Value: 1}, {Key: "created_at", Value: -1}}).SetLimit(adminListMax))
	if err != nil {
		return nil, err
	}
	var reports []Report
	if err := cur.All(ctx, &reports); err != nil {
		return nil, err
	}
	ids := make([]string, 0, 2*len(reports))
	for _, r := range reports {
		ids = append(ids, r.TargetOwnerID, r.ReporterID)
	}
	names := s.names(ctx, ids)
	out := make([]AdminReport, 0, len(reports))
	for _, r := range reports {
		out = append(out, AdminReport{Report: r, OwnerName: names[r.TargetOwnerID], ReporterName: names[r.ReporterID]})
	}
	return out, nil
}

// Resolve applique la décision de l'administration à un signalement et à tous
// les signalements ouverts du même contenu.
func (s *Service) Resolve(ctx context.Context, reportID, action, adminID string) error {
	oid, err := bson.ObjectIDFromHex(reportID)
	if err != nil {
		return ErrNotFound
	}
	var r Report
	if err := s.reports.FindOne(ctx, bson.M{"_id": oid}).Decode(&r); err != nil {
		if errors.Is(err, mongo.ErrNoDocuments) {
			return ErrNotFound
		}
		return err
	}
	status := StatusResolved
	switch action {
	case ActionDismiss:
		status = StatusDismissed
	case ActionRemove:
		if err := s.remove(ctx, r); err != nil {
			return err
		}
	case ActionSuspend:
		if err := s.Suspend(ctx, r.TargetOwnerID); err != nil {
			return err
		}
	default:
		return ErrInvalidInput
	}
	now := time.Now()
	_, err = s.reports.UpdateMany(ctx,
		bson.M{"target_type": r.TargetType, "target_id": r.TargetID, "status": StatusOpen},
		bson.M{"$set": bson.M{"status": status, "action": action, "resolved_by": adminID, "resolved_at": now}})
	return err
}

// remove retire le contenu : l'idée est dépubliée et masquée (son auteur la
// garde, sans pouvoir la republier), le commentaire est effacé. Retirer un
// compte revient à le suspendre.
func (s *Service) remove(ctx context.Context, r Report) error {
	oid, err := bson.ObjectIDFromHex(r.TargetID)
	if err != nil {
		return ErrNotFound
	}
	switch r.TargetType {
	case TargetIdea:
		_, err = s.links.UpdateOne(ctx, bson.M{"_id": oid}, bson.M{"$set": bson.M{"visibility": "private", "hidden": true, "updated_at": time.Now()}})
	case TargetComment:
		_, err = s.comments.DeleteOne(ctx, bson.M{"_id": oid})
	case TargetAccount:
		err = s.Suspend(ctx, r.TargetID)
	}
	return err
}

// Suspend ferme le compte (connexion et sessions refusées) et retire tout ce
// qu'il montrait aux autres : idées publiques (masquées), dossiers publics
// (redevenus privés, donc leurs idées aussi) et commentaires (masqués).
// Un compte administrateur ne peut pas être suspendu.
func (s *Service) Suspend(ctx context.Context, userID string) error {
	oid, err := bson.ObjectIDFromHex(userID)
	if err != nil {
		return ErrNotFound
	}
	var u struct {
		IsAdmin bool `bson:"is_admin"`
	}
	if err := s.users.FindOne(ctx, bson.M{"_id": oid}, options.FindOne().SetProjection(bson.M{"is_admin": 1})).Decode(&u); err != nil {
		if errors.Is(err, mongo.ErrNoDocuments) {
			return ErrNotFound
		}
		return err
	}
	if u.IsAdmin {
		return ErrProtected
	}
	now := time.Now()
	if _, err := s.users.UpdateOne(ctx, bson.M{"_id": oid}, bson.M{"$set": bson.M{"suspended_at": now}}); err != nil {
		return err
	}
	if _, err := s.links.UpdateMany(ctx, bson.M{"owner_id": userID, "visibility": "public"},
		bson.M{"$set": bson.M{"visibility": "private", "hidden": true, "updated_at": now}}); err != nil {
		return err
	}
	if _, err := s.folders.UpdateMany(ctx, bson.M{"owner_id": userID, "visibility": "public"},
		bson.M{"$set": bson.M{"visibility": "private", "updated_at": now}}); err != nil {
		return err
	}
	_, err = s.comments.UpdateMany(ctx, bson.M{"user_id": userID}, bson.M{"$set": bson.M{"hidden": true}})
	return err
}

// ownedByTribe : owner est le compte userID ou un membre de sa tribu.
func (s *Service) ownedByTribe(ctx context.Context, userID, owner string) bool {
	if owner == userID {
		return true
	}
	if s.peers == nil {
		return false
	}
	for _, p := range s.peers(ctx, userID) {
		if p == owner {
			return true
		}
	}
	return false
}
