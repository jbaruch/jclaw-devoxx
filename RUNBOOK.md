# Devoxx rehearsal runbook

This is a working build, not a completed rehearsal record. Seven competitive
rounds lead to a verdict and a five-minute Port epilogue. Memory and skills are
separate rounds. All human rejection and approval belongs to guardrails.

## Build and launch

```bash
./gradlew :app:test :mocks:mcpJars
./jclaw workflow           # round 5: reviewed proposal or blocked; never asks to send
./jclaw guardrails         # round 6: full human approval / rejection sequence
./jclaw observability      # round 7: same workflow, round-7 trace metadata
```

Append `plain` for stdout. Use `./jclaw --help` for standalone tools.
The launcher sources the ignored local .env, builds the installed app and mock
jars, and keeps memory intact. It never changes branches or clears local data.

Complete the app on `main` before deriving step branches by removing features.
Rounds 1–4 then need their own branches before the seven-round run is stage-ready.
Do not present the combined baseline as those missing checkpoints.

## Shared input and observed outcomes

Use the README's exact opening ask on both frameworks. Use the same mock jars,
seed memory and corporate-speak skill. Agree model roles and exact model selection
with Viktor; disclose transport or model differences before comparing results.

Read the actual candidate and verdict. Immediate approval, refinement and blocking
are all possible. Do not promise the critic will discover a particular excuse.
A prepared result used after failure must be identified as a rehearsal.

## Round 5: automated loop

1. Inspect identification, draft and the complete critic input.
2. Follow the actual route: approve, refine up to twice, or block.
3. Confirm the app stops at a reviewed proposal without asking for approval.
4. Show native Koog task/verification helpers before the custom subscription-CLI
   integration once that teaching example is built.

## Round 6: human loop and delivery

1. Run guardrails mode and read the exact reviewed message and recipient.
2. Hold it: no delivery and no sent-history write.
3. Reject with a substantive change. Confirm a new reviewed candidate carries
   that latest instruction; earlier approval cannot approve the replacement.
4. Approve the current candidate. Inspect the matching mock receipt and stored
   literal outbound message.
5. Restart and retrieve the new sent history. A draft or style rewrite is not a send.

Delivery fixtures use `JCLAW_MOCK_DELIVERY`: `success` (default), `refused`,
`error`, `malformed`, `wrong-event`, `wrong-call`, `wrong-candidate`.
An explicit refusal is failed delivery. A tool error or invalid receipt leaves
delivery unconfirmed; check the organizer before retrying. Neither writes a
confirmed sent fact. These are fixtures, not live framework verdicts.

## Round 7: trace evidence

Configure Langfuse locally, run observability mode, then quit cleanly to flush.
Select the newest trace by timestamp, session and Devoxx release metadata.
Inspect actual node inputs/outputs, repeated review attempts, feedback and duration.

Gemini API usage and CLI transport evidence have different coverage. No equivalent
CLI token price is claimed. Human confirmation, mock delivery and the final memory
write currently remain outside the native agent trace; explain this boundary.

## Skills meta-moment

Distinguish the coding agent reading the Koog framework skill during this build
from running j-claw reading corporate-speak to answer a user. Show the skill read,
the resulting code change and its compile/test result. BUILD-NOTES.md records
the source versions and corrections; the plugin refresh issue remains open.

## Remaining rehearsal work

- Finish the complete app and compile the native-helper teaching example.
- Derive the seven step branches from the completed app by removing features.
- Refresh or explicitly label the inherited dated calendar fixture.
- Run actual Google, Claude and Codex transports with the intended model versions.
- Exercise approval, substantive rejection, both refinement limits and unavailable critic.
- Verify skill loading and projector readability in the TUI.
- Verify live Langfuse export and agree comparable evidence with Viktor.
- Build and rehearse the same workflow in Port.
- Time the full sequence, prepare clearly labelled recovery checkpoints and final resources.
