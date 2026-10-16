package link

import (
	"context"
	"errors"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
)

type Link struct {
	ID              primitive.ObjectID `bson:"_id,omitempty"    json:"id"`
	OwnerID         string             `bson:"owner_id"         json:"owner_id"`
	FolderID        string             `bson:"folder_id"        json:"folder_id"`
	Title           string             `bson:"title"            json:"title"`
	URL             string             `bson:"url"              json:"url"`
	Description     string             `bson:"description"      json:"description"`
	Category        string             `bson:"category"         json:"category"`
	Tags            []string           `bson:"tags"             json:"tags"`
	AgeRange        string             `bson:"age_range"        json:"age_range"`
	Location        string             `bson:"location"         json:"location"`
	Price           string             `bson:"price"            json:"price"`
	ImageURL        string             `bson:"image_url"        json:"image_url"`
	EventDate       int64              `bson:"event_date"       json:"event_date"`
	ReminderEnabled bool               `bson:"reminder_enabled" json:"reminder_enabled"`
	Rating          int32              `bson:"rating"           json:"rating"`
	Ingredients     []string           `bson:"ingredients"      json:"ingredients"`
	Visibility      string             `bson:"visibility"       json:"visibility"` // "private" | "public"
	Favorite        bool               `bson:"favorite"         json:"favorite"`
	CreatedAt       time.Time          `bson:"created_at"       json:"created_at"`
	UpdatedAt       time.Time          `bson:"updated_at"       json:"updated_at"`
}

type LinkLike struct {
	ID        primitive.ObjectID `bson:"_id,omitempty"`
	LinkID    string             `bson:"link_id"`
	UserID    string             `bson:"user_id"`
	CreatedAt time.Time          `bson:"created_at"`
}

type Service struct {
	peers       func(ctx context.Context, userID string) []string
	col         *mongo.Collection
	folderCol   *mongo.Collection
	linkLikeCol *mongo.Collection
	userCol     *mongo.Collection
}

func NewService(col *mongo.Collection, folderCol *mongo.Collection) *Service {
	return &Service{
		col:         col,
		folderCol:   folderCol,
		linkLikeCol: col.Database().Collection("link_likes"),
		userCol:     col.Database().Collection("users"),
	}
}

// SetTribePeers branche la tribu : peers(userID) retourne les comptes dont
// userID voit et modifie le contenu (lui compris). Sans tribu : lui seul.
func (s *Service) SetTribePeers(peers func(ctx context.Context, userID string) []string) {
	s.peers = peers
}

func (s *Service) peerIDs(ctx context.Context, userID string) []string {
	if userID == "" {
		return nil
	}
	if s.peers == nil {
		return []string{userID}
	}
	return s.peers(ctx, userID)
}

// isPeer : le contenu de ownerID appartient à la tribu de userID (ou à lui).
func (s *Service) isPeer(ctx context.Context, userID, ownerID string) bool {
	if userID == "" || ownerID == "" {
		return false
	}
	if ownerID == userID {
		return true
	}
	for _, p := range s.peerIDs(ctx, userID) {
		if p == ownerID {
			return true
		}
	}
	return false
}

// accessibleFolderIDs retourne les IDs de dossiers auxquels l'utilisateur a accès
func (s *Service) accessibleFolderIDs(ctx context.Context, userID string) ([]string, error) {
	filter := bson.M{
		"$or": bson.A{
			bson.M{"owner_id": bson.M{"$in": s.peerIDs(ctx, userID)}},
			bson.M{"collaborators.user_id": userID},
		},
	}
	cursor, err := s.folderCol.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)

	var ids []string
	for cursor.Next(ctx) {
		var f struct {
			ID primitive.ObjectID `bson:"_id"`
		}
		if err := cursor.Decode(&f); err == nil {
			ids = append(ids, f.ID.Hex())
		}
	}
	return ids, nil
}

// canAccessLink vérifie si l'utilisateur peut voir un lien : son auteur, tout
// le monde si le lien est public, les membres de son dossier, et les visiteurs
// d'un dossier public — mais seulement pour les idées du propriétaire du
// dossier (cf. folderAccess).
func (s *Service) canAccessLink(ctx context.Context, l *Link, userID string) bool {
	if s.isPeer(ctx, userID, l.OwnerID) {
		return true
	}
	if l.Visibility == "public" {
		return true
	}
	member, publicOwner := s.folderAccess(ctx, l.FolderID, userID)
	return member || (publicOwner != "" && l.OwnerID == publicOwner)
}

// CanView indique si l'utilisateur (éventuellement anonyme) peut voir le lien.
func (s *Service) CanView(ctx context.Context, linkID, userID string) bool {
	id, err := primitive.ObjectIDFromHex(linkID)
	if err != nil {
		return false
	}
	var l Link
	if err := s.col.FindOne(ctx, bson.M{"_id": id}).Decode(&l); err != nil {
		return false
	}
	return s.canAccessLink(ctx, &l, userID)
}

// folderAccess décrit l'accès d'un utilisateur à un dossier : member s'il en
// est propriétaire ou collaborateur ; sinon, si le dossier est public,
// publicOwner est l'ID de son propriétaire.
//
// Rendre un dossier public est la décision de son propriétaire : elle publie
// ses propres idées, pas celles que ses collaborateurs y ont ajoutées (RGPD :
// chacun décide seul de publier son contenu). Celles-ci restent réservées aux
// membres, sauf si leur auteur les a lui-même rendues publiques.
func (s *Service) folderAccess(ctx context.Context, folderID, userID string) (member bool, publicOwner string) {
	fid, err := primitive.ObjectIDFromHex(folderID)
	if err != nil {
		return false, ""
	}
	var f struct {
		OwnerID       string `bson:"owner_id"`
		Visibility    string `bson:"visibility"`
		Collaborators []struct {
			UserID string `bson:"user_id"`
		} `bson:"collaborators"`
	}
	if err := s.folderCol.FindOne(ctx, bson.M{"_id": fid}).Decode(&f); err != nil {
		return false, ""
	}
	if userID != "" {
		if s.isPeer(ctx, userID, f.OwnerID) {
			return true, ""
		}
		for _, c := range f.Collaborators {
			if c.UserID == userID {
				return true, ""
			}
		}
	}
	if f.Visibility == "public" {
		return false, f.OwnerID
	}
	return false, ""
}

// canEditFolder vérifie si l'utilisateur peut éditer dans un dossier
func (s *Service) canEditFolder(ctx context.Context, folderID, userID string) bool {
	if folderID == "" {
		return false
	}
	fid, err := primitive.ObjectIDFromHex(folderID)
	if err != nil {
		return false
	}
	count, _ := s.folderCol.CountDocuments(ctx, bson.M{
		"_id": fid,
		"$or": bson.A{
			bson.M{"owner_id": bson.M{"$in": s.peerIDs(ctx, userID)}},
			bson.M{"collaborators": bson.M{"$elemMatch": bson.M{"user_id": userID, "role": "editor"}}},
		},
	})
	return count > 0
}

// folderIsPublic : le dossier est publié dans la communauté.
func (s *Service) folderIsPublic(ctx context.Context, folderID string) bool {
	fid, err := primitive.ObjectIDFromHex(folderID)
	if err != nil {
		return false
	}
	n, _ := s.folderCol.CountDocuments(ctx, bson.M{"_id": fid, "visibility": "public"})
	return n > 0
}

func (s *Service) Create(ctx context.Context, ownerID string, l *Link) (*Link, error) {
	if err := l.sanitize(); err != nil {
		return nil, err
	}
	if l.FolderID != "" && !s.canEditFolder(ctx, l.FolderID, ownerID) {
		return nil, ErrForbidden
	}
	l.ID = primitive.NewObjectID()
	l.OwnerID = ownerID
	l.CreatedAt = time.Now()
	l.UpdatedAt = time.Now()
	if l.Tags == nil {
		l.Tags = []string{}
	}
	if l.Ingredients == nil {
		l.Ingredients = []string{}
	}
	// Default to private if visibility is not set
	if l.Visibility == "" {
		l.Visibility = "private"
	}
	if _, err := s.col.InsertOne(ctx, l); err != nil {
		return nil, err
	}
	return l, nil
}

func (s *Service) Get(ctx context.Context, linkID, userID string) (*Link, error) {
	id, err := primitive.ObjectIDFromHex(linkID)
	if err != nil {
		return nil, ErrNotFound
	}
	var l Link
	if err := s.col.FindOne(ctx, bson.M{"_id": id}).Decode(&l); err != nil {
		if errors.Is(err, mongo.ErrNoDocuments) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	if !s.canAccessLink(ctx, &l, userID) {
		return nil, ErrNotFound
	}
	return &l, nil
}

func (s *Service) List(ctx context.Context, ownerID, folderID string) ([]*Link, error) {
	if folderID != "" {
		// Si un dossier est spécifié, retourner ses liens seulement si l'utilisateur y a accès
		member, publicOwner := s.folderAccess(ctx, folderID, ownerID)
		switch {
		case member:
			return s.listByFolder(ctx, bson.M{"folder_id": folderID})
		case publicOwner != "":
			// Visiteur d'un dossier public : idées du propriétaire, et celles
			// que leur auteur a lui-même publiées
			return s.listByFolder(ctx, PublicFolderFilter(folderID, publicOwner))
		default:
			return nil, ErrNotFound
		}
	}
	// Sinon, retourner les liens propres + ceux des dossiers partagés
	folderIDs, err := s.accessibleFolderIDs(ctx, ownerID)
	if err != nil {
		return nil, err
	}

	// Construire le filtre : toujours inclure les liens propres
	// N'ajouter le filtre folder_id que si l'utilisateur a des dossiers accessibles
	conditions := bson.A{
		// Ses idées et celles de sa tribu
		bson.M{"owner_id": bson.M{"$in": s.peerIDs(ctx, ownerID)}},
	}
	if len(folderIDs) > 0 {
		conditions = append(conditions, bson.M{"folder_id": bson.M{"$in": folderIDs}})
	}
	filter := bson.M{"$or": conditions}

	cursor, err := s.col.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)
	var links []*Link
	if err := cursor.All(ctx, &links); err != nil {
		return nil, err
	}
	// Toujours retourner un slice vide plutôt que nil
	if links == nil {
		links = []*Link{}
	}
	return links, nil
}

// PublicFolderFilter sélectionne les idées d'un dossier visibles hors de ses
// membres (dossier public, lien de partage) : celles de son propriétaire et
// celles que leur auteur a rendues publiques.
func PublicFolderFilter(folderID, folderOwnerID string) bson.M {
	return bson.M{"folder_id": folderID, "$or": bson.A{
		bson.M{"owner_id": folderOwnerID},
		bson.M{"visibility": "public"},
	}}
}

func (s *Service) listByFolder(ctx context.Context, filter bson.M) ([]*Link, error) {
	cursor, err := s.col.Find(ctx, filter)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)
	var links []*Link
	if err := cursor.All(ctx, &links); err != nil {
		return nil, err
	}
	if links == nil {
		links = []*Link{}
	}
	return links, nil
}

func (s *Service) Update(ctx context.Context, linkID, userID string, l *Link) (*Link, error) {
	id, err := primitive.ObjectIDFromHex(linkID)
	if err != nil {
		return nil, ErrNotFound
	}

	if err := l.sanitize(); err != nil {
		return nil, err
	}

	// Charger le lien existant
	var existing Link
	if err := s.col.FindOne(ctx, bson.M{"_id": id}).Decode(&existing); err != nil {
		if errors.Is(err, mongo.ErrNoDocuments) {
			return nil, ErrNotFound
		}
		return nil, err
	}

	// Vérifier : auteur, membre de sa tribu, OU éditeur du dossier
	isPeer := s.isPeer(ctx, userID, existing.OwnerID)
	if !isPeer && !s.canEditFolder(ctx, existing.FolderID, userID) {
		return nil, ErrForbidden
	}
	// Changer une idée de dossier est réservé à son auteur et à sa tribu, et
	// seul l'auteur peut la mettre dans un dossier public (ce serait la publier
	// à sa place). Il faut aussi le droit d'écrire dans le dossier de destination.
	if l.FolderID != existing.FolderID {
		if !isPeer || (existing.OwnerID != userID && l.FolderID != "" && s.folderIsPublic(ctx, l.FolderID)) {
			return nil, ErrForbidden
		}
		if l.FolderID != "" && !s.canEditFolder(ctx, l.FolderID, userID) {
			return nil, ErrForbidden
		}
	}
	// Seul l'auteur choisit si son lien est publié dans la communauté
	if existing.OwnerID != userID {
		l.Visibility = existing.Visibility
	}
	if l.Visibility == "" {
		l.Visibility = existing.Visibility
	}

	l.UpdatedAt = time.Now()
	update := bson.M{"$set": bson.M{
		"folder_id": l.FolderID, "title": l.Title, "url": l.URL,
		"description": l.Description, "category": l.Category,
		"tags": l.Tags, "age_range": l.AgeRange, "location": l.Location,
		"price": l.Price, "image_url": l.ImageURL, "event_date": l.EventDate,
		"reminder_enabled": l.ReminderEnabled, "rating": l.Rating,
		"ingredients": l.Ingredients, "visibility": l.Visibility, "updated_at": l.UpdatedAt,
	}}
	if _, err := s.col.UpdateOne(ctx, bson.M{"_id": id}, update); err != nil {
		return nil, err
	}
	// Idée sans image dont l'adresse change (ou dont l'image est retirée) :
	// on cherche aussitôt l'image de la page, en arrière-plan
	if l.ImageURL == "" && l.URL != "" && (l.URL != existing.URL || existing.ImageURL != "") {
		s.backfillSoon(id, l.URL)
	}
	return s.Get(ctx, linkID, userID)
}

func (s *Service) Delete(ctx context.Context, linkID, userID string) error {
	id, err := primitive.ObjectIDFromHex(linkID)
	if err != nil {
		return ErrNotFound
	}

	var existing Link
	if err := s.col.FindOne(ctx, bson.M{"_id": id}).Decode(&existing); err != nil {
		if errors.Is(err, mongo.ErrNoDocuments) {
			return ErrNotFound
		}
		return err
	}

	if !s.isPeer(ctx, userID, existing.OwnerID) && !s.canEditFolder(ctx, existing.FolderID, userID) {
		return ErrForbidden
	}

	_, err = s.col.DeleteOne(ctx, bson.M{"_id": id})
	return err
}

// LikeLink ajoute un like à un lien
func (s *Service) LikeLink(ctx context.Context, linkID, userID string) (int32, error) {
	// Vérifier que le lien existe
	id, err := primitive.ObjectIDFromHex(linkID)
	if err != nil {
		return 0, ErrNotFound
	}
	var l Link
	if err := s.col.FindOne(ctx, bson.M{"_id": id}).Decode(&l); err != nil || !s.canAccessLink(ctx, &l, userID) {
		return 0, ErrNotFound
	}

	// Vérifier si déjà liké
	count, _ := s.linkLikeCol.CountDocuments(ctx, bson.M{"link_id": linkID, "user_id": userID})
	if count > 0 {
		// Déjà liké, retourner le compteur actuel
		return s.GetLikeCount(ctx, linkID)
	}

	// Ajouter le like
	like := LinkLike{
		ID:        primitive.NewObjectID(),
		LinkID:    linkID,
		UserID:    userID,
		CreatedAt: time.Now(),
	}
	if _, err := s.linkLikeCol.InsertOne(ctx, like); err != nil {
		return 0, err
	}

	return s.GetLikeCount(ctx, linkID)
}

// UnlikeLink retire un like d'un lien
func (s *Service) UnlikeLink(ctx context.Context, linkID, userID string) (int32, error) {
	// Supprimer le like
	_, err := s.linkLikeCol.DeleteOne(ctx, bson.M{"link_id": linkID, "user_id": userID})
	if err != nil {
		return 0, err
	}
	return s.GetLikeCount(ctx, linkID)
}

// GetLikeCount retourne le nombre de likes d'un lien
func (s *Service) GetLikeCount(ctx context.Context, linkID string) (int32, error) {
	count, err := s.linkLikeCol.CountDocuments(ctx, bson.M{"link_id": linkID})
	if err != nil {
		return 0, err
	}
	return int32(count), nil
}

// IsLikedByUser vérifie si un utilisateur a liké un lien
func (s *Service) IsLikedByUser(ctx context.Context, linkID, userID string) (bool, error) {
	count, err := s.linkLikeCol.CountDocuments(ctx, bson.M{"link_id": linkID, "user_id": userID})
	if err != nil {
		return false, err
	}
	return count > 0, nil
}

// ToggleFavorite bascule le statut favori d'un lien
func (s *Service) ToggleFavorite(ctx context.Context, linkID, ownerID string) (bool, error) {
	id, err := primitive.ObjectIDFromHex(linkID)
	if err != nil {
		return false, err
	}

	// Récupérer le lien actuel
	var link Link
	// Les favoris sont ceux de la tribu
	err = s.col.FindOne(ctx, bson.M{"_id": id, "owner_id": bson.M{"$in": s.peerIDs(ctx, ownerID)}}).Decode(&link)
	if err != nil {
		return false, err
	}

	// Inverser le statut favori
	newFavorite := !link.Favorite
	_, err = s.col.UpdateOne(
		ctx,
		bson.M{"_id": id},
		bson.M{"$set": bson.M{"favorite": newFavorite, "updated_at": time.Now()}},
	)
	if err != nil {
		return false, err
	}

	return newFavorite, nil
}

// ListCommunity retourne tous les liens avec visibility="public"
func (s *Service) ListCommunity(ctx context.Context, category string, limit int32) ([]*Link, error) {
	if limit <= 0 {
		limit = 6
	}
	if limit > 100 {
		limit = 100
	}

	filter := bson.M{"visibility": "public"}
	if category != "" {
		filter["category"] = category
	}

	opts := options.Find().SetLimit(int64(limit))
	linkCursor, err := s.col.Find(ctx, filter, opts)
	if err != nil {
		return nil, err
	}
	defer linkCursor.Close(ctx)

	var links []*Link
	if err := linkCursor.All(ctx, &links); err != nil {
		return nil, err
	}
	if links == nil {
		links = []*Link{}
	}
	return links, nil
}

// ListNew retourne les derniers liens publics, triés par date de création décroissante
func (s *Service) ListNew(ctx context.Context, limit int32) ([]*Link, error) {
	if limit <= 0 {
		limit = 10
	}
	if limit > 50 {
		limit = 50
	}

	opts := options.Find().SetSort(bson.D{{Key: "created_at", Value: -1}}).SetLimit(int64(limit))
	filter := bson.M{"visibility": "public"}

	cursor, err := s.col.Find(ctx, filter, opts)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)

	var links []*Link
	if err := cursor.All(ctx, &links); err != nil {
		return nil, err
	}
	if links == nil {
		links = []*Link{}
	}
	return links, nil
}

// GetOwnerInfo retourne le display_name et le statut admin d'un user par son ID.
// Si le display_name n'est pas défini dans la base de données, retourne une chaîne vide (pas null).
// Cette chaîne vide permet aux clients de gérer l'affichage d'un nom par défaut (ex: "Anonyme").
func (s *Service) GetOwnerInfo(ctx context.Context, ownerID string) (string, bool) {
	oid, err := primitive.ObjectIDFromHex(ownerID)
	if err != nil {
		return "", false
	}
	var user struct {
		DisplayName string `bson:"display_name"`
		IsAdmin     bool   `bson:"is_admin"`
	}
	if err := s.userCol.FindOne(ctx, bson.M{"_id": oid}).Decode(&user); err != nil {
		return "", false
	}
	// user.DisplayName sera une chaîne vide si le champ n'existe pas ou est vide dans MongoDB
	return user.DisplayName, user.IsAdmin
}

// OwnerInfo contient les informations publiques d'un auteur.
type OwnerInfo struct {
	DisplayName string
	IsAdmin     bool
}

// Stats regroupe les données d'affichage (likes, auteur) d'un lot de liens,
// chargées en trois requêtes quel que soit le nombre de liens.
type Stats struct {
	LikeCount map[string]int32
	LikedByMe map[string]bool
	Owners    map[string]OwnerInfo
}

// LoadStats charge likes, "aimé par moi" et auteurs pour une liste de liens.
func (s *Service) LoadStats(ctx context.Context, links []*Link, userID string) *Stats {
	st := &Stats{
		LikeCount: map[string]int32{},
		LikedByMe: map[string]bool{},
		Owners:    map[string]OwnerInfo{},
	}
	if len(links) == 0 {
		return st
	}
	ids := make([]string, 0, len(links))
	ownerSet := map[primitive.ObjectID]struct{}{}
	for _, l := range links {
		ids = append(ids, l.ID.Hex())
		if oid, err := primitive.ObjectIDFromHex(l.OwnerID); err == nil {
			ownerSet[oid] = struct{}{}
		}
	}

	pipeline := mongo.Pipeline{
		{{Key: "$match", Value: bson.M{"link_id": bson.M{"$in": ids}}}},
		{{Key: "$group", Value: bson.M{"_id": "$link_id", "n": bson.M{"$sum": 1}}}},
	}
	if cur, err := s.linkLikeCol.Aggregate(ctx, pipeline); err == nil {
		var rows []struct {
			ID string `bson:"_id"`
			N  int32  `bson:"n"`
		}
		if cur.All(ctx, &rows) == nil {
			for _, r := range rows {
				st.LikeCount[r.ID] = r.N
			}
		}
	}

	if userID != "" {
		filter := bson.M{"link_id": bson.M{"$in": ids}, "user_id": userID}
		if cur, err := s.linkLikeCol.Find(ctx, filter, options.Find().SetProjection(bson.M{"link_id": 1})); err == nil {
			var rows []struct {
				LinkID string `bson:"link_id"`
			}
			if cur.All(ctx, &rows) == nil {
				for _, r := range rows {
					st.LikedByMe[r.LinkID] = true
				}
			}
		}
	}

	oids := make([]primitive.ObjectID, 0, len(ownerSet))
	for oid := range ownerSet {
		oids = append(oids, oid)
	}
	proj := options.Find().SetProjection(bson.M{"display_name": 1, "is_admin": 1})
	if cur, err := s.userCol.Find(ctx, bson.M{"_id": bson.M{"$in": oids}}, proj); err == nil {
		var rows []struct {
			ID          primitive.ObjectID `bson:"_id"`
			DisplayName string             `bson:"display_name"`
			IsAdmin     bool               `bson:"is_admin"`
		}
		if cur.All(ctx, &rows) == nil {
			for _, r := range rows {
				st.Owners[r.ID.Hex()] = OwnerInfo{DisplayName: r.DisplayName, IsAdmin: r.IsAdmin}
			}
		}
	}
	return st
}
