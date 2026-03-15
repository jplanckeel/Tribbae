package comment

import (
	"context"
	"testing"
	"time"

	"github.com/leanovate/gopter"
	"github.com/leanovate/gopter/gen"
	"github.com/leanovate/gopter/prop"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
)

// setupTestDB creates a test database connection
func setupTestDB(t *testing.T) (*mongo.Client, *mongo.Database, func()) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	
	// Connect to MongoDB (assumes MongoDB is running locally for tests)
	clientOpts := options.Client().
		ApplyURI("mongodb://localhost:27017").
		SetServerSelectionTimeout(5 * time.Second)
	
	client, err := mongo.Connect(ctx, clientOpts)
	if err != nil {
		t.Skipf("Skipping test: Failed to connect to MongoDB: %v", err)
	}
	
	// Ping to verify connection
	if err := client.Ping(ctx, nil); err != nil {
		t.Skipf("Skipping test: MongoDB not available: %v", err)
	}
	
	// Use a test database
	dbName := "tribbae_test_" + primitive.NewObjectID().Hex()
	db := client.Database(dbName)
	
	// Cleanup function
	cleanup := func() {
		ctx := context.Background()
		if err := db.Drop(ctx); err != nil {
			t.Logf("Failed to drop test database: %v", err)
		}
		if err := client.Disconnect(ctx); err != nil {
			t.Logf("Failed to disconnect from MongoDB: %v", err)
		}
	}
	
	return client, db, cleanup
}

// **Feature: social-features-fixes, Property 10: Comment storage completeness**
// Property 10: Comment storage completeness
// For any comment created on a link, the stored comment should contain all required fields: user_id, link_id, text, and created_at timestamp
// **Validates: Requirements 5.1**
func TestProperty_CommentStorageCompleteness(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	
	ctx := context.Background()
	commentsCol := db.Collection("comments")
	linksCol := db.Collection("links")
	usersCol := db.Collection("users")
	
	svc := NewService(commentsCol, linksCol, usersCol)
	
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)
	
	properties.Property("created comment contains all required fields", prop.ForAll(
		func(commentText string, seed int64) bool {
			// Clean up collections before each test
			commentsCol.DeleteMany(ctx, bson.M{})
			linksCol.DeleteMany(ctx, bson.M{})
			usersCol.DeleteMany(ctx, bson.M{})
			
			// Constrain comment text to non-empty strings
			if commentText == "" {
				commentText = "Test comment"
			}
			// Limit length to reasonable size
			if len(commentText) > 1000 {
				commentText = commentText[:1000]
			}
			
			// Create a user
			userID := primitive.NewObjectID()
			user := bson.M{
				"_id":          userID,
				"email":        "user@example.com",
				"display_name": "Test User",
				"is_admin":     false,
				"created_at":   time.Now(),
			}
			
			if _, err := usersCol.InsertOne(ctx, user); err != nil {
				t.Logf("Failed to insert user: %v", err)
				return false
			}
			
			// Create a link
			linkID := primitive.NewObjectID()
			link := bson.M{
				"_id":        linkID,
				"owner_id":   userID.Hex(),
				"title":      "Test Link",
				"url":        "https://example.com",
				"created_at": time.Now(),
			}
			
			if _, err := linksCol.InsertOne(ctx, link); err != nil {
				t.Logf("Failed to insert link: %v", err)
				return false
			}
			
			// Record time before creating comment
			beforeCreate := time.Now()
			
			// Create comment
			createdComment, err := svc.CreateComment(ctx, linkID.Hex(), userID.Hex(), commentText)
			if err != nil {
				t.Logf("CreateComment failed: %v", err)
				return false
			}
			
			// Record time after creating comment
			afterCreate := time.Now()
			
			// Retrieve the comment directly from database
			commentOID, err := primitive.ObjectIDFromHex(createdComment.ID)
			if err != nil {
				t.Logf("Invalid comment ID returned: %v", err)
				return false
			}
			
			var storedComment Comment
			err = commentsCol.FindOne(ctx, bson.M{"_id": commentOID}).Decode(&storedComment)
			if err != nil {
				t.Logf("Failed to retrieve comment from database: %v", err)
				return false
			}
			
			// Verify all required fields are present and correct
			
			// 1. Verify user_id is stored correctly
			if storedComment.UserID != userID.Hex() {
				t.Logf("user_id mismatch: expected=%s, got=%s", userID.Hex(), storedComment.UserID)
				return false
			}
			
			// 2. Verify link_id is stored correctly
			if storedComment.LinkID != linkID.Hex() {
				t.Logf("link_id mismatch: expected=%s, got=%s", linkID.Hex(), storedComment.LinkID)
				return false
			}
			
			// 3. Verify text is stored correctly
			if storedComment.Text != commentText {
				t.Logf("text mismatch: expected=%s, got=%s", commentText, storedComment.Text)
				return false
			}
			
			// 4. Verify created_at timestamp is set and reasonable
			if storedComment.CreatedAt.IsZero() {
				t.Logf("created_at is not set (zero value)")
				return false
			}
			
			// Verify created_at is within reasonable time range
			if storedComment.CreatedAt.Before(beforeCreate.Add(-1*time.Second)) || 
			   storedComment.CreatedAt.After(afterCreate.Add(1*time.Second)) {
				t.Logf("created_at timestamp is outside expected range: %v (expected between %v and %v)", 
					storedComment.CreatedAt, beforeCreate, afterCreate)
				return false
			}
			
			// 5. Verify updated_at timestamp is also set (bonus check)
			if storedComment.UpdatedAt.IsZero() {
				t.Logf("updated_at is not set (zero value)")
				return false
			}
			
			// 6. Verify the comment has a valid ID
			if storedComment.ID.IsZero() {
				t.Logf("comment ID is not set (zero value)")
				return false
			}
			
			return true
		},
		gen.AnyString(),
		gen.Int64(),
	))
	
	properties.TestingRun(t)
}

// **Feature: social-features-fixes, Property 12: Comment deletion by link owner**
// Property 12: Comment deletion by link owner
// For any comment on a link, when deleted by the link's owner, the comment should no longer exist in the database
// **Validates: Requirements 5.5**
func TestProperty_CommentDeletionByLinkOwner(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	
	ctx := context.Background()
	commentsCol := db.Collection("comments")
	linksCol := db.Collection("links")
	usersCol := db.Collection("users")
	
	svc := NewService(commentsCol, linksCol, usersCol)
	
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)
	
	properties.Property("link owner can delete any comment on their link and it no longer exists", prop.ForAll(
		func(commentText string, seed int64) bool {
			// Clean up collections before each test
			commentsCol.DeleteMany(ctx, bson.M{})
			linksCol.DeleteMany(ctx, bson.M{})
			usersCol.DeleteMany(ctx, bson.M{})
			
			// Constrain comment text to non-empty strings
			if commentText == "" {
				commentText = "Test comment"
			}
			// Limit length to reasonable size
			if len(commentText) > 1000 {
				commentText = commentText[:1000]
			}
			
			// Create a link owner
			linkOwnerID := primitive.NewObjectID()
			linkOwner := bson.M{
				"_id":          linkOwnerID,
				"email":        "owner@example.com",
				"display_name": "Link Owner",
				"is_admin":     false,
				"created_at":   time.Now(),
			}
			
			if _, err := usersCol.InsertOne(ctx, linkOwner); err != nil {
				t.Logf("Failed to insert link owner: %v", err)
				return false
			}
			
			// Create a comment author (different from link owner)
			authorID := primitive.NewObjectID()
			author := bson.M{
				"_id":          authorID,
				"email":        "author@example.com",
				"display_name": "Comment Author",
				"is_admin":     false,
				"created_at":   time.Now(),
			}
			
			if _, err := usersCol.InsertOne(ctx, author); err != nil {
				t.Logf("Failed to insert author: %v", err)
				return false
			}
			
			// Create a link owned by linkOwner
			linkID := primitive.NewObjectID()
			link := bson.M{
				"_id":        linkID,
				"owner_id":   linkOwnerID.Hex(),
				"title":      "Test Link",
				"url":        "https://example.com",
				"created_at": time.Now(),
			}
			
			if _, err := linksCol.InsertOne(ctx, link); err != nil {
				t.Logf("Failed to insert link: %v", err)
				return false
			}
			
			// Create comment by author (not the link owner)
			createdComment, err := svc.CreateComment(ctx, linkID.Hex(), authorID.Hex(), commentText)
			if err != nil {
				t.Logf("CreateComment failed: %v", err)
				return false
			}
			
			// Verify comment exists before deletion
			commentOID, err := primitive.ObjectIDFromHex(createdComment.ID)
			if err != nil {
				t.Logf("Invalid comment ID: %v", err)
				return false
			}
			
			countBefore, err := commentsCol.CountDocuments(ctx, bson.M{"_id": commentOID})
			if err != nil {
				t.Logf("Failed to count comments before deletion: %v", err)
				return false
			}
			
			if countBefore != 1 {
				t.Logf("Comment should exist before deletion, count=%d", countBefore)
				return false
			}
			
			// Verify the comment author is NOT the link owner (important for this test)
			if authorID.Hex() == linkOwnerID.Hex() {
				t.Logf("Test setup error: author and link owner should be different")
				return false
			}
			
			// Link owner deletes the comment (even though they didn't write it)
			err = svc.DeleteComment(ctx, createdComment.ID, linkOwnerID.Hex())
			if err != nil {
				t.Logf("DeleteComment by link owner failed: %v", err)
				return false
			}
			
			// Verify comment no longer exists in database
			countAfter, err := commentsCol.CountDocuments(ctx, bson.M{"_id": commentOID})
			if err != nil {
				t.Logf("Failed to count comments after deletion: %v", err)
				return false
			}
			
			if countAfter != 0 {
				t.Logf("Comment should not exist after deletion by link owner, count=%d", countAfter)
				return false
			}
			
			// Additional verification: trying to retrieve the comment should fail
			var deletedComment Comment
			err = commentsCol.FindOne(ctx, bson.M{"_id": commentOID}).Decode(&deletedComment)
			if err != mongo.ErrNoDocuments {
				t.Logf("Expected ErrNoDocuments when retrieving deleted comment, got: %v", err)
				return false
			}
			
			return true
		},
		gen.AnyString(),
		gen.Int64(),
	))
	
	properties.TestingRun(t)
}

// **Feature: social-features-fixes, Property 13: Comment sorting by date**
// Property 13: Comment sorting by date
// For any link with multiple comments, the comments returned should be sorted by created_at in descending order (newest first)
// **Validates: Requirements 5.6**
func TestProperty_CommentSortingByDate(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	
	ctx := context.Background()
	commentsCol := db.Collection("comments")
	linksCol := db.Collection("links")
	usersCol := db.Collection("users")
	
	svc := NewService(commentsCol, linksCol, usersCol)
	
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)
	
	properties.Property("comments are sorted by created_at descending (newest first)", prop.ForAll(
		func(numComments int, seed int64) bool {
			// Clean up collections before each test
			commentsCol.DeleteMany(ctx, bson.M{})
			linksCol.DeleteMany(ctx, bson.M{})
			usersCol.DeleteMany(ctx, bson.M{})
			
			// Constrain number of comments to reasonable range (2-20)
			if numComments < 2 {
				numComments = 2
			}
			if numComments > 20 {
				numComments = 20
			}
			
			// Create a user
			userID := primitive.NewObjectID()
			user := bson.M{
				"_id":          userID,
				"email":        "user@example.com",
				"display_name": "Test User",
				"is_admin":     false,
				"created_at":   time.Now(),
			}
			
			if _, err := usersCol.InsertOne(ctx, user); err != nil {
				t.Logf("Failed to insert user: %v", err)
				return false
			}
			
			// Create a link
			linkID := primitive.NewObjectID()
			link := bson.M{
				"_id":        linkID,
				"owner_id":   userID.Hex(),
				"title":      "Test Link",
				"url":        "https://example.com",
				"created_at": time.Now(),
			}
			
			if _, err := linksCol.InsertOne(ctx, link); err != nil {
				t.Logf("Failed to insert link: %v", err)
				return false
			}
			
			// Create multiple comments with different timestamps
			// We'll insert them with deliberate time gaps to ensure sorting is testable
			createdTimestamps := make([]time.Time, numComments)
			baseTime := time.Now().Add(-time.Duration(numComments) * time.Hour)
			
			for i := 0; i < numComments; i++ {
				// Create comments with increasing timestamps
				// Each comment is 1 hour after the previous one
				timestamp := baseTime.Add(time.Duration(i) * time.Hour)
				createdTimestamps[i] = timestamp
				
				comment := Comment{
					ID:        primitive.NewObjectID(),
					LinkID:    linkID.Hex(),
					UserID:    userID.Hex(),
					Text:      "Comment " + string(rune('A'+i)),
					CreatedAt: timestamp,
					UpdatedAt: timestamp,
				}
				
				if _, err := commentsCol.InsertOne(ctx, comment); err != nil {
					t.Logf("Failed to insert comment %d: %v", i, err)
					return false
				}
				
				// Small delay to ensure timestamps are distinct
				time.Sleep(1 * time.Millisecond)
			}
			
			// Retrieve comments using the service
			comments, err := svc.GetComments(ctx, linkID.Hex())
			if err != nil {
				t.Logf("GetComments failed: %v", err)
				return false
			}
			
			// Verify we got all comments
			if len(comments) != numComments {
				t.Logf("Expected %d comments, got %d", numComments, len(comments))
				return false
			}
			
			// Verify comments are sorted by created_at descending (newest first)
			for i := 0; i < len(comments)-1; i++ {
				currentTime := comments[i].CreatedAt
				nextTime := comments[i+1].CreatedAt
				
				// Current comment should have a timestamp >= next comment (descending order)
				if currentTime.Before(nextTime) {
					t.Logf("Comments not sorted correctly: comment[%d].CreatedAt=%v is before comment[%d].CreatedAt=%v",
						i, currentTime, i+1, nextTime)
					return false
				}
			}
			
			// Additional verification: first comment should be the newest (last created)
			firstComment := comments[0]
			lastComment := comments[len(comments)-1]
			
			// The first comment in the result should have the latest timestamp
			// The last comment in the result should have the earliest timestamp
			if !firstComment.CreatedAt.After(lastComment.CreatedAt) && !firstComment.CreatedAt.Equal(lastComment.CreatedAt) {
				t.Logf("First comment should be newer than or equal to last comment: first=%v, last=%v",
					firstComment.CreatedAt, lastComment.CreatedAt)
				return false
			}
			
			return true
		},
		gen.IntRange(2, 20),
		gen.Int64(),
	))
	
	properties.TestingRun(t)
}

// **Feature: social-features-fixes, Property 11: Comment deletion by author**
// Property 11: Comment deletion by author
// For any comment, when deleted by its author (user_id matches), the comment should no longer exist in the database
// **Validates: Requirements 5.4**
func TestProperty_CommentDeletionByAuthor(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	
	ctx := context.Background()
	commentsCol := db.Collection("comments")
	linksCol := db.Collection("links")
	usersCol := db.Collection("users")
	
	svc := NewService(commentsCol, linksCol, usersCol)
	
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)
	
	properties.Property("author can delete their own comment and it no longer exists", prop.ForAll(
		func(commentText string, seed int64) bool {
			// Clean up collections before each test
			commentsCol.DeleteMany(ctx, bson.M{})
			linksCol.DeleteMany(ctx, bson.M{})
			usersCol.DeleteMany(ctx, bson.M{})
			
			// Constrain comment text to non-empty strings
			if commentText == "" {
				commentText = "Test comment"
			}
			// Limit length to reasonable size
			if len(commentText) > 1000 {
				commentText = commentText[:1000]
			}
			
			// Create a user (comment author)
			authorID := primitive.NewObjectID()
			author := bson.M{
				"_id":          authorID,
				"email":        "author@example.com",
				"display_name": "Comment Author",
				"is_admin":     false,
				"created_at":   time.Now(),
			}
			
			if _, err := usersCol.InsertOne(ctx, author); err != nil {
				t.Logf("Failed to insert author: %v", err)
				return false
			}
			
			// Create a link owner (different from comment author)
			linkOwnerID := primitive.NewObjectID()
			linkOwner := bson.M{
				"_id":          linkOwnerID,
				"email":        "owner@example.com",
				"display_name": "Link Owner",
				"is_admin":     false,
				"created_at":   time.Now(),
			}
			
			if _, err := usersCol.InsertOne(ctx, linkOwner); err != nil {
				t.Logf("Failed to insert link owner: %v", err)
				return false
			}
			
			// Create a link
			linkID := primitive.NewObjectID()
			link := bson.M{
				"_id":        linkID,
				"owner_id":   linkOwnerID.Hex(),
				"title":      "Test Link",
				"url":        "https://example.com",
				"created_at": time.Now(),
			}
			
			if _, err := linksCol.InsertOne(ctx, link); err != nil {
				t.Logf("Failed to insert link: %v", err)
				return false
			}
			
			// Create comment by author
			createdComment, err := svc.CreateComment(ctx, linkID.Hex(), authorID.Hex(), commentText)
			if err != nil {
				t.Logf("CreateComment failed: %v", err)
				return false
			}
			
			// Verify comment exists before deletion
			commentOID, err := primitive.ObjectIDFromHex(createdComment.ID)
			if err != nil {
				t.Logf("Invalid comment ID: %v", err)
				return false
			}
			
			countBefore, err := commentsCol.CountDocuments(ctx, bson.M{"_id": commentOID})
			if err != nil {
				t.Logf("Failed to count comments before deletion: %v", err)
				return false
			}
			
			if countBefore != 1 {
				t.Logf("Comment should exist before deletion, count=%d", countBefore)
				return false
			}
			
			// Author deletes their own comment
			err = svc.DeleteComment(ctx, createdComment.ID, authorID.Hex())
			if err != nil {
				t.Logf("DeleteComment by author failed: %v", err)
				return false
			}
			
			// Verify comment no longer exists in database
			countAfter, err := commentsCol.CountDocuments(ctx, bson.M{"_id": commentOID})
			if err != nil {
				t.Logf("Failed to count comments after deletion: %v", err)
				return false
			}
			
			if countAfter != 0 {
				t.Logf("Comment should not exist after deletion by author, count=%d", countAfter)
				return false
			}
			
			// Additional verification: trying to retrieve the comment should fail
			var deletedComment Comment
			err = commentsCol.FindOne(ctx, bson.M{"_id": commentOID}).Decode(&deletedComment)
			if err != mongo.ErrNoDocuments {
				t.Logf("Expected ErrNoDocuments when retrieving deleted comment, got: %v", err)
				return false
			}
			
			return true
		},
		gen.AnyString(),
		gen.Int64(),
	))
	
	properties.TestingRun(t)
}
