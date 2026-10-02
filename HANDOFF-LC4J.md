# Shared Devoxx contract for the LangChain4j side

Baruch owns Koog; Viktor Gamov owns LangChain4j. Use idiomatic framework constructs
on each side and agree any model or transport difference before comparison.
The format is a three-hour live showdown, not a laptop workshop.

Build the complete application on `main` first. Once it is finished, derive the
step branches by removing features. Do not maintain unfinished round variants
while the shared implementation is still changing.

## Shared task and fixtures

Use the exact opening request in README.md, the same committed prior-decline
documents, the same corporate-speak skill and these mock MCP jars:

```bash
./gradlew :mocks:mcpJars
```

Launch `mocks/build/libs/calendar-mcp.jar` and `organizer-mcp.jar` over stdio.
Route stderr to the visible tool trace. The calendar reports past declines without
their reasons; memory supplies those reasons. The calendar dates are refreshed to
the fictional Tuesday October 6 fixture, separate from the real
session schedule. Use those same jars on both implementations.

## Round boundaries

The shared `tui/` module supplies the TamboUI stage dashboard. Embed `JclawTui`
with the LangChain4j mode label and actual `providerLegend`; keep feature and flow
labels truthful for each checkpoint. Drive candidate, verdict, human decision,
receipt and memory-write displays from real events. `./jclaw preview` is labelled
UI fixture data and must not be used as live framework evidence.

| Round | Scope |
|---|---|
| 1 | Chatbot |
| 2 | Tools / MCP |
| 3 | Conversation memory and durable sent history |
| 4 | Runtime skills, plus the Koog-framework-skill build meta-moment |
| 5 | Typed multi-agent workflow and bounded automatic review/refinement only |
| 6 | Entire human rejection, revision, approval and delivery sequence |
| 7 | Inspect actual executed routes and comparable observability evidence |

Port is a closing implementation of the same workflow, outside competitive scoring.
Use the narrative's timing and slide plan in the presentation workspace.

## Typed workflow

Gemini identifies; Claude drafts/refines; Codex reviews. The current Koog transport
uses Google API plus subscription CLIs. Agree exact model versions before rehearsal.
The serializers in domain/ are the authoritative data contract:

- `DeclineRequest` includes event, organizer, sent flavors, known attendees,
  the latest user instruction and previously proposed alternatives.
- `DeclineReview` carries that request with the exact `DeclineDeployment`.
- `DeclineCritique` decides approval and supplies feedback.
- Two refinements maximum; a third rejection, invalid verdict or unavailable critic blocks.
- Drafting cannot create events or send. Claimed supporting event IDs are invalid.
- Resolve the organizer's canonical name from the selected calendar event before
  drafting and reviewing; a model abbreviation must not become a different target.

Only a reviewed candidate reaches the application approval gate. Holding sends
nothing. A substantive human rejection starts a fresh reviewed attempt with the
latest constraints. Style-only rewrites remain ordinary chat and cannot replace
an approved candidate silently. Human approval never overrides critic rejection.

## Delivery contract

The application calls organizer `sendDecline` with all five fields:
`eventId`, `organizerName`, `message`, `callId`, `candidateId`.
The candidate identity hashes length-delimited UTF-8 event, organizer and message;
the call identity is unique to this send attempt.

The mock returns the serialized `DeclineReceipt`: delivered flag, all matching
identities/target fields, and an offset timestamp for success. Validate the raw
MCP result before announcing success or writing durable history. Tool error,
missing/malformed receipt or mismatched identities leaves delivery unconfirmed.
A matching explicit `delivered=false` confirms refusal. The hash binds a candidate;
it is not an authentication mechanism or a guarantee of idempotent delivery.

Use the same `JCLAW_MOCK_DELIVERY` fixtures listed in RUNBOOK.md. Persist only the
literal message and target from confirmed sends. Keep proposed flavors separate
from past sent flavors. Restarting preserves sent history and resets conversation.

## Runtime skills and trace evidence

Discover catalog metadata at startup; load a relevant skill body through scoped,
read-only tools before applying it. Corporate-speak defaults to eleven and keeps
facts, intent and commitments intact. A rewrite alone sends nothing.

Each side uses its own observability tooling. Show actual inputs, outputs, review
attempts and durations. Disclose absent coverage and API/CLI usage differences.
Prepared traces are labelled rehearsals. Read the actual critic reasoning rather
than scripting a moral or truthful verdict.
