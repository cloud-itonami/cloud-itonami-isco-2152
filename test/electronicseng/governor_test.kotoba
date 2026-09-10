(ns electronicseng.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [electronicseng.store :as store]
            [electronicseng.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-board! st {:board-id "B-1" :client-id "client-1"
                               :name "sensor-hub-rev2"
                               :power-budget-mw 500
                               :approved-vendors #{"TI" "STM"}})
    st))

(defn- approve [bom]
  {:op :approve-bom :effect :propose :board-id "B-1" :bom bom
   :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-budget-and-approved-vendors
  (let [st (fresh-store)
        v (governor/check req {} (approve [{:part "MCU" :vendor "STM" :power-mw 200}
                                           {:part "RADIO" :vendor "TI" :power-mw 150}]) st)]
    (is (:ok? v))))

(deftest ok-at-exact-budget
  (testing "power sum exactly equal to the budget is within margin"
    (let [st (fresh-store)
          v (governor/check req {} (approve [{:part "MCU" :vendor "STM" :power-mw 500}]) st)]
      (is (:ok? v)))))

(deftest hard-on-power-budget-exceeded
  (testing "power arithmetic is not a purchasing preference"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (approve [{:part "MCU" :vendor "STM" :power-mw 300}
                                                    {:part "RADIO" :vendor "TI" :power-mw 300}])
                                          :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :power-budget-exceeded (:rule %)) (:violations v))))))

(deftest hard-on-unapproved-vendor
  (let [st (fresh-store)
        v (governor/check req {} (approve [{:part "MCU" :vendor "Unbranded" :power-mw 100}]) st)]
    (is (:hard? v))
    (is (some #(= :unapproved-vendor (:rule %)) (:violations v)))))

(deftest hard-on-unknown-board
  (let [st (fresh-store)
        v (governor/check req {} (assoc (approve []) :board-id "B-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-board (:rule %)) (:violations v)))))

(deftest hard-on-foreign-board
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (approve []) st)]
      (is (:hard? v))
      (is (some #(= :board-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (approve []) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (approve []) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest escalates-production-release
  (let [st (fresh-store)
        v (governor/check req {} {:op :approve-production :effect :propose
                                  :board-id "B-1" :confidence 0.9 :stake :high} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (approve [{:part "MCU" :vendor "STM" :power-mw 100}])
                                        :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
