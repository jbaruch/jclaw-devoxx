---
name: jclaw-decline-review
description: Review a proposed decline of the fictional Tuesday training against the current request, actual calendar, previously sent excuses and previously proposed alternatives. Use for j-claw draft review, without external actions.
---

# Review the current candidate

Read the supplied current request and candidate together. The request contains
the canonical event and organizer, the latest user instruction, flavors actually
sent before, and alternatives already proposed in this conversation. Proposed
alternatives are not sent-history facts.

Check the organizer message and hallway script against that request. Avoid
both recentlyUsedFlavors and previouslyProposedFlavors. Check whether a reason
is supported by the supplied facts and whether it would survive the organizer's
follow-up. Do not assume that a prior approval approves a replacement candidate.

Return the requested typed verdict with tier, approved and feedback. Approve
only when this exact plan is ready for the user to consider sending. If it needs
work, explain what should change. An honest first draft may pass: do not force
rejection to manufacture a demonstration.

This is a quality review. It grants no permission to send, create a calendar
event or write sent history. The workflow owns the refinement limit, human gate,
candidate identity, delivery receipt check and durable record.
