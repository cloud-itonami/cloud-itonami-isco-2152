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
    2. no-actuation      — proposal :effect must be :propose.
    3. board basis        — a BOM approval must cite a REGISTERED
                           board belonging to this client.
    4. power-budget arithmetic — the sum of the proposed BOM entries'
                           power draws must not exceed the board's
                           registered :power-budget-mw.
    5. approved-vendor membership — every proposed component's vendor
                           must be a member of the board's registered
                           :approved-vendors set (no invented or
                           unapproved supplier).
  ESCALATION invariants (:escalate? true, human sign-off):
    6. :op :approve-production (release to manufacturing).
    7. low confidence (< `confidence-floor`)."
  (:require [electronicseng.store :as store]))

(def confidence-floor 0.6)

(defn- hard-violations [{:keys [request proposal]} client-record b]
  (let [{:keys [op bom]} proposal
        approve? (= :approve-bom op)
        total-power (when (seq bom) (reduce + (map :power-mw bom)))
        unapproved (when (and b (seq bom))
                     (into [] (remove #(contains? (:approved-vendors b) (:vendor %))) bom))]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

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
        risky-op? (= :approve-production (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
