(ns electronicseng.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [electronicseng.actor :as actor]
            [electronicseng.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-board! st {:board-id "B-1" :client-id "client-1"
                               :name "sensor-hub-rev2"
                               :power-budget-mw 500
                               :approved-vendors #{"TI" "STM"}})
    st))

(deftest commits-an-in-budget-bom
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-bom :stake :low
                 :board-id "B-1"
                 :bom [{:part "MCU" :vendor "STM" :power-mw 200}]}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-budget-bom
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-bom :stake :low
                 :board-id "B-1"
                 :bom [{:part "MCU" :vendor "STM" :power-mw 700}]}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-releases-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-production :stake :high
                 :board-id "B-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
