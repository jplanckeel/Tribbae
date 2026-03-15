package follow

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

// **Feature: social-features-fixes, Property 7: Follow relationship creation**
// Property 7: Follow relationship creation
// For any two distinct users A and B, when A follows B, a follow relationship should exist in the database with follower_id=A and following_id=B
// **Validates: Requirements 4.1**
func TestProperty_FollowRelationshipCreation(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	
	ctx := context.Background()
	followsCol := db.Collection("follows")
	usersCol := db.Collection("users")
	
	svc := NewService(followsCol, usersCol)
	
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)
	
	properties.Property("follow creates relationship in database", prop.ForAll(
		func(seed int64) bool {
			// Clean up collections before each test
			followsCol.DeleteMany(ctx, bson.M{})
			usersCol.DeleteMany(ctx, bson.M{})
			
			// Create two distinct users
			userAID := primitive.NewObjectID()
			userBID := primitive.NewObjectID()
			
			userA := bson.M{
				"_id":          userAID,
				"email":        "userA@example.com",
				"display_name": "User A",
				"is_admin":     false,
				"created_at":   time.Now(),
			}
			userB := bson.M{
				"_id":          userBID,
				"email":        "userB@example.com",
				"display_name": "User B",
				"is_admin":     false,
				"created_at":   time.Now(),
			}
			
			if _, err := usersCol.InsertOne(ctx, userA); err != nil {
				t.Logf("Failed to insert user A: %v", err)
				return false
			}
			if _, err := usersCol.InsertOne(ctx, userB); err != nil {
				t.Logf("Failed to insert user B: %v", err)
				return false
			}
			
			// User A follows User B
			if err := svc.Follow(ctx, userAID.Hex(), userBID.Hex()); err != nil {
				t.Logf("Follow failed: %v", err)
				return false
			}
			
			// Verify the follow relationship exists in the database
			var follow Follow
			filter := bson.M{
				"follower_id":  userAID.Hex(),
				"following_id": userBID.Hex(),
			}
			
			if err := followsCol.FindOne(ctx, filter).Decode(&follow); err != nil {
				t.Logf("Follow relationship not found in database: %v", err)
				return false
			}
			
			// Verify the relationship has correct IDs
			if follow.FollowerID != userAID.Hex() {
				t.Logf("Incorrect follower_id: expected=%s, got=%s", userAID.Hex(), follow.FollowerID)
				return false
			}
			
			if follow.FollowingID != userBID.Hex() {
				t.Logf("Incorrect following_id: expected=%s, got=%s", userBID.Hex(), follow.FollowingID)
				return false
			}
			
			// Verify created_at is set
			if follow.CreatedAt.IsZero() {
				t.Logf("CreatedAt not set")
				return false
			}
			
			return true
		},
		gen.Int64(),
	))
	
	properties.TestingRun(t)
}

// **Feature: social-features-fixes, Property 8: Follow/unfollow round trip**
// Property 8: Follow/unfollow round trip
// For any two distinct users A and B, following B then immediately unfollowing B should result in no follow relationship existing between A and B
// **Validates: Requirements 4.2**
func TestProperty_FollowUnfollowRoundTrip(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	
	ctx := context.Background()
	followsCol := db.Collection("follows")
	usersCol := db.Collection("users")
	
	svc := NewService(followsCol, usersCol)
	
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)
	
	properties.Property("follow then unfollow removes relationship", prop.ForAll(
		func(seed int64) bool {
			// Clean up collections before each test
			followsCol.DeleteMany(ctx, bson.M{})
			usersCol.DeleteMany(ctx, bson.M{})
			
			// Create two distinct users
			userAID := primitive.NewObjectID()
			userBID := primitive.NewObjectID()
			
			userA := bson.M{
				"_id":          userAID,
				"email":        "userA@example.com",
				"display_name": "User A",
				"is_admin":     false,
				"created_at":   time.Now(),
			}
			userB := bson.M{
				"_id":          userBID,
				"email":        "userB@example.com",
				"display_name": "User B",
				"is_admin":     false,
				"created_at":   time.Now(),
			}
			
			if _, err := usersCol.InsertOne(ctx, userA); err != nil {
				t.Logf("Failed to insert user A: %v", err)
				return false
			}
			if _, err := usersCol.InsertOne(ctx, userB); err != nil {
				t.Logf("Failed to insert user B: %v", err)
				return false
			}
			
			// User A follows User B
			if err := svc.Follow(ctx, userAID.Hex(), userBID.Hex()); err != nil {
				t.Logf("Follow failed: %v", err)
				return false
			}
			
			// User A unfollows User B
			if err := svc.Unfollow(ctx, userAID.Hex(), userBID.Hex()); err != nil {
				t.Logf("Unfollow failed: %v", err)
				return false
			}
			
			// Verify no follow relationship exists
			filter := bson.M{
				"follower_id":  userAID.Hex(),
				"following_id": userBID.Hex(),
			}
			
			count, err := followsCol.CountDocuments(ctx, filter)
			if err != nil {
				t.Logf("Failed to count documents: %v", err)
				return false
			}
			
			if count != 0 {
				t.Logf("Follow relationship still exists after unfollow: count=%d", count)
				return false
			}
			
			// Also verify using IsFollowing
			isFollowing, err := svc.IsFollowing(ctx, userAID.Hex(), userBID.Hex())
			if err != nil {
				t.Logf("IsFollowing failed: %v", err)
				return false
			}
			
			if isFollowing {
				t.Logf("IsFollowing returned true after unfollow")
				return false
			}
			
			return true
		},
		gen.Int64(),
	))
	
	properties.TestingRun(t)
}

// **Feature: social-features-fixes, Property 9: Follower count consistency**
// Property 9: Follower count consistency
// For any user, the follower count should equal the number of follow relationships where that user is the following_id
// **Validates: Requirements 4.6, 4.7**
func TestProperty_FollowerCountConsistency(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	
	ctx := context.Background()
	followsCol := db.Collection("follows")
	usersCol := db.Collection("users")
	
	svc := NewService(followsCol, usersCol)
	
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)
	
	properties.Property("follower count equals number of follow relationships", prop.ForAll(
		func(numFollowers int) bool {
			// Clean up collections before each test
			followsCol.DeleteMany(ctx, bson.M{})
			usersCol.DeleteMany(ctx, bson.M{})
			
			// Constrain to reasonable range
			if numFollowers < 0 {
				numFollowers = 0
			}
			if numFollowers > 20 {
				numFollowers = 20
			}
			
			// Create target user (the one being followed)
			targetUserID := primitive.NewObjectID()
			targetUser := bson.M{
				"_id":          targetUserID,
				"email":        "target@example.com",
				"display_name": "Target User",
				"is_admin":     false,
				"created_at":   time.Now(),
			}
			
			if _, err := usersCol.InsertOne(ctx, targetUser); err != nil {
				t.Logf("Failed to insert target user: %v", err)
				return false
			}
			
			// Create follower users and establish follow relationships
			followerIDs := make([]string, numFollowers)
			for i := 0; i < numFollowers; i++ {
				followerID := primitive.NewObjectID()
				follower := bson.M{
					"_id":          followerID,
					"email":        "follower" + followerID.Hex() + "@example.com",
					"display_name": "Follower " + followerID.Hex(),
					"is_admin":     false,
					"created_at":   time.Now(),
				}
				
				if _, err := usersCol.InsertOne(ctx, follower); err != nil {
					t.Logf("Failed to insert follower: %v", err)
					return false
				}
				
				followerIDs[i] = followerID.Hex()
				
				// Create follow relationship
				if err := svc.Follow(ctx, followerID.Hex(), targetUserID.Hex()); err != nil {
					t.Logf("Follow failed: %v", err)
					return false
				}
			}
			
			// Get follower count from service
			count, err := svc.GetFollowerCount(ctx, targetUserID.Hex())
			if err != nil {
				t.Logf("GetFollowerCount failed: %v", err)
				return false
			}
			
			// Verify count matches expected
			if int(count) != numFollowers {
				t.Logf("Follower count mismatch: expected=%d, got=%d", numFollowers, count)
				return false
			}
			
			// Also verify by counting documents directly
			filter := bson.M{"following_id": targetUserID.Hex()}
			dbCount, err := followsCol.CountDocuments(ctx, filter)
			if err != nil {
				t.Logf("Failed to count documents: %v", err)
				return false
			}
			
			if int(dbCount) != numFollowers {
				t.Logf("Database count mismatch: expected=%d, got=%d", numFollowers, dbCount)
				return false
			}
			
			// Verify GetFollowers returns correct list
			followers, err := svc.GetFollowers(ctx, targetUserID.Hex())
			if err != nil {
				t.Logf("GetFollowers failed: %v", err)
				return false
			}
			
			if len(followers) != numFollowers {
				t.Logf("GetFollowers length mismatch: expected=%d, got=%d", numFollowers, len(followers))
				return false
			}
			
			return true
		},
		gen.IntRange(0, 20),
	))
	
	properties.TestingRun(t)
}

// **Feature: social-features-fixes, Property 19: Following count accuracy**
// Property 19: Following count accuracy
// For any user, the following count should equal the number of follow relationships where that user is the follower_id
// **Validates: Requirements 8.3**
func TestProperty_FollowingCountAccuracy(t *testing.T) {
	_, db, cleanup := setupTestDB(t)
	defer cleanup()
	
	ctx := context.Background()
	followsCol := db.Collection("follows")
	usersCol := db.Collection("users")
	
	svc := NewService(followsCol, usersCol)
	
	parameters := gopter.DefaultTestParameters()
	parameters.MinSuccessfulTests = 100
	properties := gopter.NewProperties(parameters)
	
	properties.Property("following count equals number of follow relationships where user is follower", prop.ForAll(
		func(numFollowing int) bool {
			// Clean up collections before each test
			followsCol.DeleteMany(ctx, bson.M{})
			usersCol.DeleteMany(ctx, bson.M{})
			
			// Constrain to reasonable range
			if numFollowing < 0 {
				numFollowing = 0
			}
			if numFollowing > 20 {
				numFollowing = 20
			}
			
			// Create the user who will follow others
			followerUserID := primitive.NewObjectID()
			followerUser := bson.M{
				"_id":          followerUserID,
				"email":        "follower@example.com",
				"display_name": "Follower User",
				"is_admin":     false,
				"created_at":   time.Now(),
			}
			
			if _, err := usersCol.InsertOne(ctx, followerUser); err != nil {
				t.Logf("Failed to insert follower user: %v", err)
				return false
			}
			
			// Create users to be followed and establish follow relationships
			followingIDs := make([]string, numFollowing)
			for i := 0; i < numFollowing; i++ {
				followingID := primitive.NewObjectID()
				following := bson.M{
					"_id":          followingID,
					"email":        "following" + followingID.Hex() + "@example.com",
					"display_name": "Following " + followingID.Hex(),
					"is_admin":     false,
					"created_at":   time.Now(),
				}
				
				if _, err := usersCol.InsertOne(ctx, following); err != nil {
					t.Logf("Failed to insert following user: %v", err)
					return false
				}
				
				followingIDs[i] = followingID.Hex()
				
				// Create follow relationship (followerUser follows this user)
				if err := svc.Follow(ctx, followerUserID.Hex(), followingID.Hex()); err != nil {
					t.Logf("Follow failed: %v", err)
					return false
				}
			}
			
			// Get following count from service
			count, err := svc.GetFollowingCount(ctx, followerUserID.Hex())
			if err != nil {
				t.Logf("GetFollowingCount failed: %v", err)
				return false
			}
			
			// Verify count matches expected
			if int(count) != numFollowing {
				t.Logf("Following count mismatch: expected=%d, got=%d", numFollowing, count)
				return false
			}
			
			// Also verify by counting documents directly
			filter := bson.M{"follower_id": followerUserID.Hex()}
			dbCount, err := followsCol.CountDocuments(ctx, filter)
			if err != nil {
				t.Logf("Failed to count documents: %v", err)
				return false
			}
			
			if int(dbCount) != numFollowing {
				t.Logf("Database count mismatch: expected=%d, got=%d", numFollowing, dbCount)
				return false
			}
			
			// Verify GetFollowing returns correct list
			following, err := svc.GetFollowing(ctx, followerUserID.Hex())
			if err != nil {
				t.Logf("GetFollowing failed: %v", err)
				return false
			}
			
			if len(following) != numFollowing {
				t.Logf("GetFollowing length mismatch: expected=%d, got=%d", numFollowing, len(following))
				return false
			}
			
			return true
		},
		gen.IntRange(0, 20),
	))
	
	properties.TestingRun(t)
}
