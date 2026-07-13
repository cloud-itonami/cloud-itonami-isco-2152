(ns electronicseng.advisor
  "ElectronicsEngineersAdvisor — proposes a BOM operation (approve a
  bill of materials, approve production release) for a registered
  organization. Swappable mock/llm; the advisor ONLY proposes —
  `electronicseng.governor` computes the power-budget sum and checks
  vendor membership independently. Modeled on
  cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-bom|:approve-production
               :effect :propose :board-id str
               :bom [{:part str :vendor str :power-mw number}]
               :stake kw :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake board-id bom] :as request}]
  {:op op
   :effect :propose
   :board-id board-id
   :bom bom
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are an electronics engineering advisor. Given a request,
   propose an :op, the :board-id and :bom (bill of materials), an
   honest :confidence and a :stake. Never call an over-budget BOM or
   an unapproved-vendor part conforming — the governor sums the power
   draw and checks vendor membership.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
