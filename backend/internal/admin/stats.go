package admin

import (
	"context"
	"errors"
	"fmt"
	"time"
	_ "time/tzdata" // l'image de production (alpine) n'a pas la base des fuseaux

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
)

// Les périodes sont découpées à l'heure de Paris : semaines du lundi 0 h,
// mois du 1er à 0 h, changements d'heure compris.
const statsTimezone = "Europe/Paris"

var paris = mustLoadLocation(statsTimezone)

func mustLoadLocation(name string) *time.Location {
	loc, err := time.LoadLocation(name)
	if err != nil {
		panic(err)
	}
	return loc
}

// Unités de période acceptées par GetStats ($dateTrunc les connaît sous ce nom)
const (
	PeriodWeek  = "week"
	PeriodMonth = "month"
)

const (
	defaultStatsCount = 12
	maxStatsCount     = 52
)

// ErrInvalidStatsRequest : période inconnue ou nombre de périodes hors bornes
var ErrInvalidStatsRequest = errors.New("invalid stats request")

// Point : nombre d'éléments de la période commençant à Start
type Point struct {
	Start time.Time
	Count int64
}

// Stats : séries alignées, count points chacune, de la plus ancienne période
// à la période en cours.
type Stats struct {
	Period       string
	Ideas        []Point
	PublicIdeas  []Point
	PrivateIdeas []Point
	NewUsers     []Point
	// Comptes dont last_seen_at tombe dans la période. Seule la dernière
	// activité est gardée : un compte revenu depuis n'est compté que dans sa
	// période la plus récente, donc les périodes passées sont sous-estimées.
	ActiveUsers []Point
}

// StatsService compte les nouvelles idées et les comptes par période.
type StatsService struct {
	links *mongo.Collection
	users *mongo.Collection
	now   func() time.Time
}

func NewStatsService(links, users *mongo.Collection) *StatsService {
	return &StatsService{links: links, users: users, now: time.Now}
}

// Stats retourne les séries des count dernières périodes ("week" ou
// "month"), la période en cours comprise. Valeurs vides : week et 12.
func (s *StatsService) Stats(ctx context.Context, period string, count int) (*Stats, error) {
	if period == "" {
		period = PeriodWeek
	}
	if count == 0 {
		count = defaultStatsCount
	}
	if period != PeriodWeek && period != PeriodMonth {
		return nil, fmt.Errorf("%w: period must be %q or %q", ErrInvalidStatsRequest, PeriodWeek, PeriodMonth)
	}
	if count < 1 || count > maxStatsCount {
		return nil, fmt.Errorf("%w: count must be between 1 and %d", ErrInvalidStatsRequest, maxStatsCount)
	}

	starts := periodStarts(s.now(), period, count)
	since := starts[0]

	ideas, err := countByPeriod(ctx, s.links, "created_at", period, since, nil)
	if err != nil {
		return nil, fmt.Errorf("count ideas: %w", err)
	}
	public, err := countByPeriod(ctx, s.links, "created_at", period, since, bson.M{"visibility": "public"})
	if err != nil {
		return nil, fmt.Errorf("count public ideas: %w", err)
	}
	newUsers, err := countByPeriod(ctx, s.users, "created_at", period, since, nil)
	if err != nil {
		return nil, fmt.Errorf("count new users: %w", err)
	}
	active, err := countByPeriod(ctx, s.users, "last_seen_at", period, since, nil)
	if err != nil {
		return nil, fmt.Errorf("count active users: %w", err)
	}

	st := &Stats{
		Period:      period,
		Ideas:       fill(starts, ideas),
		PublicIdeas: fill(starts, public),
		NewUsers:    fill(starts, newUsers),
		ActiveUsers: fill(starts, active),
	}
	// Privée = tout ce qui n'est pas publié (visibilité "private" ou absente)
	st.PrivateIdeas = make([]Point, len(starts))
	for i := range starts {
		st.PrivateIdeas[i] = Point{Start: starts[i], Count: st.Ideas[i].Count - st.PublicIdeas[i].Count}
	}
	return st, nil
}

// Total additionne les points d'une série.
func Total(points []Point) int64 {
	var n int64
	for _, p := range points {
		n += p.Count
	}
	return n
}

// periodStarts retourne les débuts (heure de Paris) des count périodes se
// terminant par celle qui contient now, de la plus ancienne à la plus récente.
func periodStarts(now time.Time, period string, count int) []time.Time {
	now = now.In(paris)
	var cur time.Time
	if period == PeriodMonth {
		cur = time.Date(now.Year(), now.Month(), 1, 0, 0, 0, 0, paris)
	} else {
		daysSinceMonday := (int(now.Weekday()) + 6) % 7
		cur = time.Date(now.Year(), now.Month(), now.Day()-daysSinceMonday, 0, 0, 0, 0, paris)
	}
	starts := make([]time.Time, count)
	for i := range count {
		back := count - 1 - i
		// AddDate en heure de Paris : un lundi 0 h reste un lundi 0 h d'un
		// changement d'heure à l'autre (semaines de 167 ou 169 heures)
		if period == PeriodMonth {
			starts[i] = cur.AddDate(0, -back, 0)
		} else {
			starts[i] = cur.AddDate(0, 0, -7*back)
		}
	}
	return starts
}

// countByPeriod compte les documents de col dont dateField est postérieur à
// since (et qui vérifient extraMatch), par période de Paris. La clé de la
// carte est l'instant de début de période (Unix).
func countByPeriod(ctx context.Context, col *mongo.Collection, dateField, unit string, since time.Time, extraMatch bson.M) (map[int64]int64, error) {
	match := bson.M{dateField: bson.M{"$gte": since}}
	for k, v := range extraMatch {
		match[k] = v
	}
	pipeline := mongo.Pipeline{
		{{Key: "$match", Value: match}},
		{{Key: "$group", Value: bson.M{
			"_id": bson.M{"$dateTrunc": bson.M{
				"date":        "$" + dateField,
				"unit":        unit,
				"timezone":    statsTimezone,
				"startOfWeek": "monday",
			}},
			"count": bson.M{"$sum": 1},
		}}},
		{{Key: "$sort", Value: bson.M{"_id": 1}}},
	}
	cur, err := col.Aggregate(ctx, pipeline)
	if err != nil {
		return nil, err
	}
	var rows []struct {
		Start time.Time `bson:"_id"`
		Count int64     `bson:"count"`
	}
	if err := cur.All(ctx, &rows); err != nil {
		return nil, err
	}
	counts := make(map[int64]int64, len(rows))
	for _, r := range rows {
		counts[r.Start.Unix()] = r.Count
	}
	return counts, nil
}

// fill aligne les comptes sur les débuts de période : 0 pour une période vide.
func fill(starts []time.Time, counts map[int64]int64) []Point {
	points := make([]Point, len(starts))
	for i, start := range starts {
		points[i] = Point{Start: start, Count: counts[start.Unix()]}
	}
	return points
}
