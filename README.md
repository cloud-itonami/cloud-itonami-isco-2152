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
23 tests / 58 assertions green.

The BOM HARD invariants — arithmetic and set membership, not
purchasing preference:

1. **Power-budget arithmetic** — the sum of the proposed BOM entries'
   power draws must not exceed the board's registered power budget.
2. **Approved-vendor membership** — every proposed component's vendor
   must be a member of the board's registered approved-vendors set
   (no invented or unapproved supplier) — supply-chain traceability is
   set membership.

Both of those hang off the operation's name, so the operation itself
is the third membership test. `src/electronicseng/operations.kotoba`
is the catalog of operations this desk is authorized to perform, and
`:op` is **deny-by-default**: an op absent from it is refused before
any BOM is considered. Without that, a proposal that simply was not
called `:approve-bom` skipped both invariants above — an LLM advisor
names its own operation, and `{:op :order-parts :confidence 0.95}`
carrying a 99999mW part from an unregistered vendor was accepted with
an empty violation list. The catalog is load-bearing: the governor
derives authorization, whether the BOM checks apply, and what
escalates from it, so there is no second list to keep in sync.

Also HARD: unauthorized `:op`, unregistered/foreign board,
unregistered organization, non-`:propose` effect. Escalations (always human sign-off):
`:approve-production` (release to manufacturing), low confidence
(< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
