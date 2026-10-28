package mcpserver

import (
	"context"
	"time"

	"go.mongodb.org/mongo-driver/v2/mongo"
)

// AuditCollection garde une trace de chaque appel d'outil, 90 jours (index
// TTL), effacée avec le compte.
const AuditCollection = "mcp_audit"

// AuditRetention : durée de conservation du journal (index TTL sur at)
const AuditRetention = 90 * 24 * time.Hour

// Résultats d'un appel, tels que journalisés.
const (
	OutcomeOK          = "ok"
	OutcomeInvalid     = "invalid"      // arguments refusés par la validation
	OutcomeForbidden   = "forbidden"    // droit manquant (scope, dossier)
	OutcomeQuota       = "quota"        // quota journalier atteint
	OutcomeRateLimited = "rate_limited" // trop d'appels par minute
	OutcomeError       = "error"        // erreur interne
)

// auditEntry ne contient ni argument d'outil ni jeton : seulement qui, quoi,
// quand et le résultat.
type auditEntry struct {
	UserID   string    `bson:"user_id"`
	ClientID string    `bson:"client_id"`
	GrantID  string    `bson:"grant_id"`
	Tool     string    `bson:"tool,omitempty"`
	Outcome  string    `bson:"outcome"`
	At       time.Time `bson:"at"`
}

// Audit écrit le journal des appels.
type Audit struct {
	col *mongo.Collection
	now func() time.Time
}

func NewAudit(col *mongo.Collection) *Audit { return &Audit{col: col, now: time.Now} }

// Record journalise un appel. Un échec d'écriture ne fait jamais échouer
// l'appel : il est seulement signalé dans les logs.
func (a *Audit) Record(ctx context.Context, c caller, tool, outcome string) {
	if a == nil {
		return
	}
	ctx, cancel := context.WithTimeout(context.WithoutCancel(ctx), 2*time.Second)
	defer cancel()
	e := auditEntry{UserID: c.userID, ClientID: c.clientID, GrantID: c.grantID, Tool: tool, Outcome: outcome, At: a.now()}
	if _, err := a.col.InsertOne(ctx, e); err != nil {
		logError("audit", err)
	}
}
