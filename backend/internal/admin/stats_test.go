package admin

import (
	"context"
	"errors"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
)

// setupDB ouvre une base jetable, ou saute le test si MongoDB est absent.
func setupDB(t *testing.T) *mongo.Database {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	client, err := mongo.Connect(options.Client().ApplyURI("mongodb://localhost:27017").SetServerSelectionTimeout(3 * time.Second))
	if err != nil {
		t.Skipf("MongoDB indisponible : %v", err)
	}
	if err := client.Ping(ctx, nil); err != nil {
		t.Skipf("MongoDB indisponible : %v", err)
	}
	db := client.Database("tribbae_test_" + bson.NewObjectID().Hex())
	t.Cleanup(func() {
		_ = db.Drop(context.Background())
		_ = client.Disconnect(context.Background())
	})
	return db
}

func utc(s string) time.Time {
	t, err := time.Parse(time.RFC3339, s)
	if err != nil {
		panic(err)
	}
	return t
}

func parisDay(y int, m time.Month, d int) time.Time {
	return time.Date(y, m, d, 0, 0, 0, 0, paris)
}

func setupStats(t *testing.T, now time.Time) (*StatsService, *mongo.Database) {
	t.Helper()
	db := setupDB(t)
	svc := NewStatsService(db.Collection("links"), db.Collection("users"))
	svc.now = func() time.Time { return now }
	return svc, db
}

func insertLinks(t *testing.T, db *mongo.Database, docs ...bson.M) {
	t.Helper()
	if _, err := db.Collection("links").InsertMany(context.Background(), docs); err != nil {
		t.Fatal(err)
	}
}

func assertSeries(t *testing.T, name string, got []Point, starts []time.Time, counts []int64) {
	t.Helper()
	if len(got) != len(counts) {
		t.Fatalf("%s : %d points, attendu %d", name, len(got), len(counts))
	}
	for i := range got {
		if !got[i].Start.Equal(starts[i]) || got[i].Count != counts[i] {
			t.Errorf("%s[%d] = %s : %d, attendu %s : %d", name, i,
				got[i].Start.In(paris).Format(time.DateTime), got[i].Count,
				starts[i].Format(time.DateTime), counts[i])
		}
	}
}

func TestStatsByWeek(t *testing.T) {
	// Mercredi 8 avril 2026 ; le passage à l'heure d'été a lieu le dimanche 29 mars à 2 h
	svc, db := setupStats(t, time.Date(2026, 4, 8, 12, 0, 0, 0, paris))
	insertLinks(t, db,
		// Hors fenêtre : dimanche 8 mars 23 h (Paris)
		bson.M{"created_at": utc("2026-03-08T22:00:00Z"), "visibility": "public"},
		// Dimanche 22 mars 23:59:59 (UTC+1) : semaine du 16
		bson.M{"created_at": utc("2026-03-22T22:59:59Z"), "visibility": "public"},
		// Lundi 23 mars 0 h pile : semaine du 23, pas du 16
		bson.M{"created_at": utc("2026-03-22T23:00:00Z"), "visibility": "private"},
		// Dimanche 29 mars 1 h 30, avant le changement d'heure (UTC+1)
		bson.M{"created_at": utc("2026-03-29T00:30:00Z"), "visibility": "public"},
		// Dimanche 29 mars 23 h 30, après (UTC+2) : toujours la semaine du 23
		bson.M{"created_at": utc("2026-03-29T21:30:00Z")}, // sans visibilité : privée
		// Lundi 30 mars 0 h 30 (UTC+2) : semaine du 30, bien qu'on soit encore le 29 en UTC
		bson.M{"created_at": utc("2026-03-29T22:30:00Z"), "visibility": "private"},
	)

	st, err := svc.Stats(context.Background(), "", 5)
	if err != nil {
		t.Fatal(err)
	}
	if st.Period != PeriodWeek {
		t.Errorf("période par défaut : %q", st.Period)
	}
	starts := []time.Time{
		parisDay(2026, 3, 9), parisDay(2026, 3, 16), parisDay(2026, 3, 23),
		parisDay(2026, 3, 30), parisDay(2026, 4, 6),
	}
	// Les semaines vides (9 mars, 6 avril) valent 0
	assertSeries(t, "ideas", st.Ideas, starts, []int64{0, 1, 3, 1, 0})
	assertSeries(t, "public", st.PublicIdeas, starts, []int64{0, 1, 1, 0, 0})
	assertSeries(t, "private", st.PrivateIdeas, starts, []int64{0, 0, 2, 1, 0})
	assertSeries(t, "newUsers", st.NewUsers, starts, []int64{0, 0, 0, 0, 0})
	if Total(st.Ideas) != 5 || Total(st.PublicIdeas) != 2 || Total(st.PrivateIdeas) != 3 {
		t.Errorf("totaux : %d / %d / %d", Total(st.Ideas), Total(st.PublicIdeas), Total(st.PrivateIdeas))
	}
}

func TestStatsByMonth(t *testing.T) {
	svc, db := setupStats(t, time.Date(2026, 4, 15, 9, 0, 0, 0, paris))
	insertLinks(t, db,
		// 31 janvier 23 h 59 : hors fenêtre
		bson.M{"created_at": utc("2026-01-31T22:59:00Z"), "visibility": "public"},
		// 28 février 23 h 30 (UTC+1) : février
		bson.M{"created_at": utc("2026-02-28T22:30:00Z"), "visibility": "public"},
		// 1er mars 0 h 30 (UTC+1) : mars, bien qu'on soit encore en février en UTC
		bson.M{"created_at": utc("2026-02-28T23:30:00Z"), "visibility": "private"},
		// 31 mars 23 h 30 (UTC+2) : mars
		bson.M{"created_at": utc("2026-03-31T21:30:00Z"), "visibility": "public"},
		// 1er avril 0 h 30 (UTC+2) : avril
		bson.M{"created_at": utc("2026-03-31T22:30:00Z"), "visibility": "public"},
	)
	users := db.Collection("users")
	if _, err := users.InsertMany(context.Background(), []bson.M{
		// Inscrite en février, revenue en avril : active seulement en avril
		{"created_at": utc("2026-02-10T10:00:00Z"), "last_seen_at": utc("2026-04-02T10:00:00Z")},
		// Inscrite le 1er mars à 0 h 30, jamais revenue depuis
		{"created_at": utc("2026-02-28T23:30:00Z"), "last_seen_at": utc("2026-02-28T23:30:00Z")},
		// Ancien compte sans last_seen_at (avant la mesure) : ni nouveau ni actif
		{"created_at": utc("2025-06-01T10:00:00Z")},
	}); err != nil {
		t.Fatal(err)
	}

	st, err := svc.Stats(context.Background(), PeriodMonth, 3)
	if err != nil {
		t.Fatal(err)
	}
	starts := []time.Time{parisDay(2026, 2, 1), parisDay(2026, 3, 1), parisDay(2026, 4, 1)}
	assertSeries(t, "ideas", st.Ideas, starts, []int64{1, 2, 1})
	assertSeries(t, "public", st.PublicIdeas, starts, []int64{1, 1, 1})
	assertSeries(t, "private", st.PrivateIdeas, starts, []int64{0, 1, 0})
	assertSeries(t, "newUsers", st.NewUsers, starts, []int64{1, 1, 0})
	assertSeries(t, "activeUsers", st.ActiveUsers, starts, []int64{0, 1, 1})
}

func TestStatsEmptyWindowIsFilled(t *testing.T) {
	svc, _ := setupStats(t, time.Date(2026, 10, 26, 10, 0, 0, 0, paris))
	st, err := svc.Stats(context.Background(), "", 0)
	if err != nil {
		t.Fatal(err)
	}
	for name, s := range map[string][]Point{
		"ideas": st.Ideas, "public": st.PublicIdeas, "private": st.PrivateIdeas,
		"newUsers": st.NewUsers, "activeUsers": st.ActiveUsers,
	} {
		if len(s) != defaultStatsCount {
			t.Errorf("%s : %d points, attendu %d", name, len(s), defaultStatsCount)
		}
		if Total(s) != 0 {
			t.Errorf("%s : total %d sur une base vide", name, Total(s))
		}
	}
	// Lundi 26 octobre 2026 : la dernière semaine commence le jour même
	if last := st.Ideas[len(st.Ideas)-1].Start; !last.Equal(parisDay(2026, 10, 26)) {
		t.Errorf("semaine en cours : %s", last.In(paris))
	}
}

func TestStatsInvalidRequest(t *testing.T) {
	svc := NewStatsService(nil, nil) // refusé avant toute requête
	for _, c := range []struct {
		period string
		count  int
	}{{"day", 12}, {"week", 53}, {"month", -1}} {
		if _, err := svc.Stats(context.Background(), c.period, c.count); !errors.Is(err, ErrInvalidStatsRequest) {
			t.Errorf("%q × %d accepté : %v", c.period, c.count, err)
		}
	}
}

// Sans MongoDB : les débuts de semaine restent des lundis 0 h à Paris de part
// et d'autre du passage à l'heure d'hiver (dimanche 25 octobre 2026).
func TestPeriodStartsAcrossWinterTime(t *testing.T) {
	starts := periodStarts(utc("2026-11-04T10:00:00Z"), PeriodWeek, 3)
	want := []time.Time{parisDay(2026, 10, 19), parisDay(2026, 10, 26), parisDay(2026, 11, 2)}
	for i := range want {
		if !starts[i].Equal(want[i]) {
			t.Errorf("début %d : %s, attendu %s", i, starts[i].In(paris), want[i])
		}
	}
	if d := starts[2].Sub(starts[1]); d != 168*time.Hour {
		t.Errorf("semaine après le changement : %s", d)
	}
	if d := starts[1].Sub(starts[0]); d != 169*time.Hour {
		t.Errorf("semaine du changement : %s", d)
	}
	// Mois : le 1er à 0 h, y compris d'une année sur l'autre
	months := periodStarts(utc("2026-01-15T10:00:00Z"), PeriodMonth, 2)
	if !months[0].Equal(parisDay(2025, 12, 1)) || !months[1].Equal(parisDay(2026, 1, 1)) {
		t.Errorf("mois : %v", months)
	}
}
