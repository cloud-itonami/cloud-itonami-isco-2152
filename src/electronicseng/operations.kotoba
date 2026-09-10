(ns electronicseng.operations
  "The catalog of operations the ISCO-08 2152 community electronics
  engineers actor is AUTHORIZED to perform — the actor's authority
  boundary, and the SSoT the governor reads it from (itonami actor
  pattern, ADR-2607011000 / CLAUDE.md Actors section).

  Why this exists as its own component. The governor originally
  enumerated two things about an operation: which one carries a bill
  of materials (`:approve-bom`, and therefore gets the power-budget
  sum and the approved-vendor membership check) and which one
  escalates (`:approve-production`). It never enumerated which
  operations are permitted AT ALL, so `:op` was allow-by-default: any
  keyword that was neither of those two fell through every hard rule
  and, at a confidence above the floor, committed a record.

  That is not a hypothetical. `electronicseng.advisor/llm-advisor`
  reads the model's response with `edn/read-string` and, for any map,
  keeps every key it finds — so the model names the operation. A
  response of `{:op :order-parts :confidence 0.95 :board-id \"b1\"
  :bom [{:vendor \"GhostSupplier\" :power-mw 99999}]}` was accepted
  with `:ok? true` and an empty `:violations`: because the op was not
  `:approve-bom`, neither the power-budget arithmetic nor the
  approved-vendor membership check ever ran. Both of the HARD
  invariants the README advertises were bypassed by declining to use
  the name they are attached to. Deny-by-default on the operation
  itself is the missing invariant.

  This catalog is load-bearing, not descriptive. `electronicseng.governor`
  derives all three of its op-dependent decisions from it —
  authorization, whether the BOM invariants apply, and whether the op
  escalates to a human. Removing an entry here refuses that operation;
  flipping `:release?` here changes what needs sign-off. There is no
  second list to keep in sync.

  Each entry:
    :requires-bom?  the proposal must cite a REGISTERED board belonging
                    to this client, and its BOM is checked against that
                    board's :power-budget-mw and :approved-vendors.
    :release?       the operation releases work outside the design desk
                    (to manufacturing), so it always escalates for
                    human sign-off.
    :description    what the desk is doing on the client's behalf.")

(def catalog
  "op keyword -> its authority record. See ns docstring: this is the
  authority list, and it is the only one."
  {:approve-bom
   {:requires-bom?  true
    :release?       false
    :description    "Approve a bill of materials against a registered board's power budget and approved-vendor set."}

   :approve-production
   {:requires-bom?  false
    :release?       true
    :description    "Release an approved design to manufacturing."}})

(def authorized-ops
  "The set of operations this desk may perform. Derived from `catalog` —
  do not maintain it by hand."
  (into #{} (keys catalog)))

(def releasing-ops
  "Operations that always need human sign-off because they release work
  outside the design desk. Derived from :release?."
  (into #{} (keep (fn [[op spec]] (when (:release? spec) op)) catalog)))

(defn spec
  "The authority record for `op`, or nil if the desk is not authorized
  to perform it."
  [op]
  (get catalog op))

(defn authorized?
  "Is `op` an operation this desk may perform at all? Deny-by-default:
  anything absent from the catalog — including nil and the `:unknown`
  that advisor/parse-proposal emits on a bad LLM response — is false."
  [op]
  (contains? catalog op))

(defn requires-bom?
  "Does `op` carry a bill of materials, so that the governor must check
  the proposed BOM against the registered board's power budget and
  approved-vendor set? False for unauthorized ops — they are refused on
  authorization before any BOM is considered."
  [op]
  (boolean (:requires-bom? (spec op))))

(defn release?
  "Does `op` release work outside the design desk (and therefore always
  escalate to a human)?"
  [op]
  (boolean (:release? (spec op))))
