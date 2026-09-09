(ns electronicseng.governor
  "ElectronicsEngineersGovernor — the independent safety/traceability
  layer for the ISCO-08 2152 community electronics engineers actor
  (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors section).
  Modeled on cloud-itonami-isco-4311's bookkeeping.governor.
  Electronics twist: a bill-of-materials's total power draw is an
  arithmetic sum checked against the registered power budget, and a
  component's vendor is either a member of the registered
  approved-vendors set or it is not — supply-chain traceability is set
  membership, not a purchasing preference.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. authorized op     — :op must be a member of
                           `electronicseng.operations/authorized-ops`.
                           Deny-by-default: the catalog is the authority
                           list, and an op absent from it is refused
                           before any BOM is considered. Without this,
                           an op that simply is not named :approve-bom
                           skips invariants 4 and 5 entirely.
    3. no-actuation      — proposal :effect must be :propose.
    4. board basis        — a BOM approval must cite a REGISTERED
                           board belonging to this client.
    5. power-budget arithmetic — the sum of the proposed BOM entries'
                           power draws must not exceed the board's
                           registered :power-budget-mw.
    6. approved-vendor membership — every proposed component's vendor
                           must be a member of the board's registered
                           :approved-vendors set (no invented or
                           unapproved supplier).
  ESCALATION invariants (:escalate? true, human sign-off):
    7. a releasing op (`operations/release?` — :approve-production
       releases to manufacturing).
    8. low confidence (< `confidence-floor`).

  Which ops exist, which carry a BOM, and which release outside the
  desk are NOT enumerated here — they are derived from
  `electronicseng.operations/catalog`, so there is no second list to
  keep in sync."
  (:require [electronicseng.store :as store]
            [electronicseng.operations :as operations]))

(def confidence-floor 0.6)

(defn- hard-violations [{:keys [request proposal]} client-record b]
  (let [{:keys [op bom]} proposal
        authorized? (operations/authorized? op)
        approve? (operations/requires-bom? op)
        total-power (when (seq bom) (reduce + (map :power-mw bom)))
        unapproved (when (and b (seq bom))
                     (into [] (remove #(contains? (:approved-vendors b) (:vendor %))) bom))]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not authorized?)
      (conj {:rule :unauthorized-op
             :detail (str "未認可の操作 " (pr-str op) "（認可は "
                          (pr-str (sort operations/authorized-ops))
                          " の集合membershipであって、名前の付け方の問題ではない）")})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and approve? (nil? b))
      (conj {:rule :unknown-board :detail "未登録 board への BOM 承認は不可"})

      (and approve? b (not= (:client-id b) (:client-id request)))
      (conj {:rule :board-wrong-client :detail "board が別 client のもの"})

      (and approve? b (seq bom) (> total-power (:power-budget-mw b)))
      (conj {:rule :power-budget-exceeded
             :detail (str "BOM 合計消費電力 " total-power "mW > 登録済み予算 "
                          (:power-budget-mw b) "mW（電力算術は購買の好みではない）")})

      (and approve? b (seq unapproved))
      (conj {:rule :unapproved-vendor
             :detail (str "未承認ベンダーの部品 " (mapv :vendor unapproved)
                          "（サプライチェーン追跡は集合membershipであって購買の好みではない）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `electronicseng.store/Store`. Pure — never
  mutates the store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        b (some->> (:board-id proposal) (store/board store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record b)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (operations/release? (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
