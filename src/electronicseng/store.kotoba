(ns electronicseng.store
  "SSoT for the ISCO-08 2152 community electronics engineers actor
  (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors section).
  Modeled on cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client — a registered organization (:client-id, :name)
    board  — a registered board/assembly {:board-id :client-id :name
             :power-budget-mw number :approved-vendors #{vendor-str}}.
             `:power-budget-mw` is the registered maximum total power
             draw (mW) a proposed bill-of-materials must not exceed;
             `:approved-vendors` is the registered set of vendors a
             proposed component's source may come from.
    record — a committed operating record (approved BOM entry) —
             written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (board [s board-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-board! [s b])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (board [_ board-id] (get-in @a [:boards board-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-board! [s b]
    (swap! a assoc-in [:boards (:board-id b)] b) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :boards {} :records [] :ledger []}
                                   seed)))))
