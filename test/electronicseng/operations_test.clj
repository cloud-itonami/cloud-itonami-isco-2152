(ns electronicseng.operations-test
  "The authority boundary: which operations this desk may perform at
  all. These tests pin the refusal that `electronicseng.operations`
  exists to produce — an op absent from the catalog is refused BEFORE
  the BOM invariants are considered, so an unauthorized op can no
  longer skip them by declining to be called :approve-bom."
  (:require [clojure.test :refer [deftest testing is]]
            [electronicseng.operations :as operations]
            [electronicseng.governor :as governor]
            [electronicseng.actor :as actor]
            [electronicseng.store :as store]))

(defn- seeded-store []
  (-> (store/mem-store)
      (store/register-client! {:client-id "c1" :name "Acme Devices"})
      (store/register-board! {:board-id "b1" :client-id "c1"
                              :name "Sensor rev A"
                              :power-budget-mw 100
                              :approved-vendors #{"TI" "Nordic"}})))

(defn- proposal [op extra]
  (merge {:op op :effect :propose :board-id "b1" :confidence 0.95} extra))

(defn- check [p] (governor/check {:client-id "c1"} {} p (seeded-store)))

(defn- violation-rules [verdict] (into #{} (map :rule) (:violations verdict)))

;; ── the catalog itself ────────────────────────────────────────────

(deftest catalog-is-the-authority-list
  (testing "the two operations the advisor is documented to propose"
    (is (operations/authorized? :approve-bom))
    (is (operations/authorized? :approve-production)))
  (testing "deny-by-default — anything else is not authorized"
    (is (not (operations/authorized? :order-parts)))
    (is (not (operations/authorized? :unknown))
        ":unknown is what advisor/parse-proposal emits on a bad LLM response")
    (is (not (operations/authorized? nil)))))

(deftest derived-sets-are-not-hand-maintained
  (is (= #{:approve-bom :approve-production} operations/authorized-ops))
  (is (= #{:approve-production} operations/releasing-ops))
  (testing "every derived set agrees with the catalog it came from"
    (is (= operations/authorized-ops (set (keys operations/catalog))))
    (is (every? #(operations/release? %) operations/releasing-ops))))

(deftest bom-bearing-ops-are-declared-not-guessed
  (is (operations/requires-bom? :approve-bom))
  (is (not (operations/requires-bom? :approve-production)))
  (testing "an unauthorized op carries no BOM authority"
    (is (not (operations/requires-bom? :order-parts)))))

;; ── the refusal the component exists to produce ───────────────────

(deftest unauthorized-op-is-refused-hard
  (let [v (check (proposal :order-parts {}))]
    (is (false? (:ok? v)) "an op absent from the catalog must not commit")
    (is (true? (:hard? v)) "authorization is HARD, never overridable")
    (is (contains? (violation-rules v) :unauthorized-op)
        "refused for the reason this component names, not incidentally")))

(deftest unauthorized-op-cannot-smuggle-past-the-bom-invariants
  (testing "the regression this component was written for: an op that is
            simply not :approve-bom used to skip BOTH hard BOM checks"
    (let [v (check (proposal :order-parts
                             {:bom [{:part "X" :vendor "GhostSupplier"
                                     :power-mw 99999}]}))]
      (is (false? (:ok? v))
          "999x over budget from an unapproved vendor must never be :ok?")
      (is (contains? (violation-rules v) :unauthorized-op)))))

(deftest parse-failure-op-is-refused-on-authorization
  (testing ":unknown is refused for being unauthorized, not merely for
            being low-confidence — it is refused even at high confidence"
    (let [v (check (proposal :unknown {:confidence 0.99}))]
      (is (false? (:ok? v)))
      (is (true? (:hard? v)))
      (is (contains? (violation-rules v) :unauthorized-op)))))

;; ── the authorized path still works (the fix is not a blanket deny) ──

(deftest authorized-conforming-bom-still-commits
  (let [v (check (proposal :approve-bom
                           {:bom [{:part "MCU" :vendor "TI" :power-mw 40}
                                  {:part "Radio" :vendor "Nordic" :power-mw 30}]}))]
    (is (true? (:ok? v)) "a conforming BOM from approved vendors commits")
    (is (empty? (:violations v)))))

(deftest authorized-op-still-hits-the-bom-invariants
  (testing "power-budget arithmetic still fires for the authorized op"
    (let [v (check (proposal :approve-bom
                             {:bom [{:part "MCU" :vendor "TI" :power-mw 400}]}))]
      (is (false? (:ok? v)))
      (is (contains? (violation-rules v) :power-budget-exceeded))))
  (testing "approved-vendor membership still fires for the authorized op"
    (let [v (check (proposal :approve-bom
                             {:bom [{:part "MCU" :vendor "GhostSupplier"
                                     :power-mw 10}]}))]
      (is (false? (:ok? v)))
      (is (contains? (violation-rules v) :unapproved-vendor)))))

(deftest releasing-op-still-escalates
  (testing "escalation is derived from the catalog, not hardcoded"
    (let [v (check (proposal :approve-production {}))]
      (is (false? (:ok? v)))
      (is (true? (:escalate? v)) "release to manufacturing needs sign-off")
      (is (false? (:hard? v)) "escalation is not a hard refusal"))))

;; ── end to end: the graph must not commit a record for it ─────────

(deftest unauthorized-op-commits-no-record-through-the-graph
  (let [s (seeded-store)
        graph (actor/build-graph {:store s})]
    (actor/run-request! graph
                        {:client-id "c1" :op :order-parts :board-id "b1"
                         :bom [{:part "X" :vendor "GhostSupplier" :power-mw 99999}]}
                        {} "t-unauthorized")
    (is (empty? (store/records-of s "c1"))
        "no record may be committed for an unauthorized op")
    (is (= [:hold] (mapv :disposition (store/ledger s)))
        "and the refusal must be on the append-only ledger")))
