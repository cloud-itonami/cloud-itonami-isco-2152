# cloud-itonami-isco-2152

Open Business Blueprint for **ISCO-08 2152**: Electronics Engineers — an ISCO
**Wave 1 (design & governance)** occupation per ADR-2607121000. This
is the SECOND wave-1 blueprint batch (21xx engineering design
professions): the design/analysis work is cognitive; physical
execution remains robotics-gated and out of the actor's scope.

**Maturity: `:implemented`** — ElectronicsEngineersAdvisor ⊣
ElectronicsEngineersGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
13 tests / 27 assertions green.

The BOM HARD invariants — arithmetic and set membership, not
purchasing preference:

1. **Power-budget arithmetic** — the sum of the proposed BOM entries'
   power draws must not exceed the board's registered power budget.
2. **Approved-vendor membership** — every proposed component's vendor
   must be a member of the board's registered approved-vendors set
   (no invented or unapproved supplier) — supply-chain traceability is
   set membership.

Also HARD: unregistered/foreign board, unregistered organization,
non-`:propose` effect. Escalations (always human sign-off):
`:approve-production` (release to manufacturing), low confidence
(< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
