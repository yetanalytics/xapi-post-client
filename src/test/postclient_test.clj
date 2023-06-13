(ns postclient_test
  (:require [clojure.test   :refer [deftest testing is use-fixtures]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [com.yetanalytics.lrs :as lrs]
            [com.yetanalytics.lrs.impl.memory :as mem :refer [new-lrs]]
            [com.yetanalytics.lrs.pedestal.routes :refer [build]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [com.yetanalytics.lrs.xapi.document   :as doc]
            [io.pedestal.http :as http]
            [com.yetanalytics.xapipe.test-support.lrs :as lrst]
            [com.yetanalytics.lrs.protocol  :as lrsp])
  (:import [java.net ServerSocket]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init Test Config
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def auth-ident
  {:agent  {"objectType" "Agent"
            "account"    {"homePage" "http://example.org"
                          "name"     "12341234-0000-4000-1234-123412341234"}}
   :scopes #{:scope/all}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; https://gist.github.com/apeckham/78da0a59076a4b91b1f5acf40a96de69
(defn- get-free-port []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

;; A version of mem/fixture-state* that allows any path
(defn fixture-state
  "Get the state of an LRS from a file"
  [path]
  (-> (io/file path)
      slurp
      read-string
      (update :state/statements
              (partial conj (ss/statements-priority-map)))
      (update :state/attachments
              #(reduce-kv
                (fn [m sha2 att]
                  (assoc m sha2 (update att :content byte-array)))
                {}
                %))
      (update :state/documents
              (fn [docs]
                (into {}
                      (for [[ctx-key docs-map] docs]
                        [ctx-key
                         (into
                          (doc/documents-priority-map)
                          (for [[doc-id doc] docs-map]
                            [doc-id (update doc :contents byte-array)]))]))))))

(defn lrs
  "Make a new LRS at port, seeding from file if seed-path is present.
  Returns a map of:
  :port - For debugging
  :lrs - The LRS itself
  :start - A function of no args that will start the LRS
  :stop - A function of no args that will stop the LRS
  :dump - A function of no args that will dump memory LRS state
  :load - A function of two args, statements and attachments to load data
  :request-config - A request config ala xapipe.client"
  [& {:keys [stream-path
             sink
             seed-path
             port]}]
  (let [port (or port
                 (get-free-port))
        lrs (cond
              sink
              (lrst/->SinkLRS)

              stream-path
              (lrst/stream-lrs stream-path)
              :else
              (new-lrs
               (cond->
                {:mode :sync}
                 (not-empty seed-path) (assoc :init-state (fixture-state seed-path)))))
        service
        {:env                   :dev
         :lrs                   lrs
         ::http/join?           false
         ::http/allowed-origins {:creds           true
                                 :allowed-origins (constantly true)}
         ::http/routes          (build {:lrs lrs})
         ::http/resource-path   "/public"
         ::http/type            :jetty

         ::http/host              "0.0.0.0"
         ::http/port              port
         ::http/container-options {:h2c? true
                                   :h2?  false
                                   :ssl? false}}
        server (-> service
                   i/xapi-default-interceptors
                   http/create-server)]
    (cond-> {:lrs            lrs
             :port           port
             :start          #(do
                                (log/debugf "Starting LRS on port %d" port)
                                (http/start server))
             :stop           #(do
                                (log/debugf "Stopping LRS on port %d" port)
                                (http/stop server))
             :dump           #(mem/dump lrs)
             :load           (fn [statements & [attachments]]
                               (lrs/store-statements
                                lrs
                                {}
                                (into [] statements)
                                (into [] attachments)))
             :request-config {:url-base    (format "http://0.0.0.0:%d" port)
                              :xapi-prefix "/xapi"}
             :type (cond
                     sink :sink
                     stream-path :stream
                     :else
                     :mem)}
      stream-path (assoc :stream-path stream-path))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sample statements
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def stmt-0
  {"id"     "00000000-0000-4000-8000-000000000000"
   "actor"  {"mbox"       "mailto:sample.foo@example.com"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"
                        "zh-CN" "回答了"}}
   "object" {"id" "http://www.example.com/tincan/activities/multipart"}})

(def stmt-4
  {"id"     "00000000-0000-4000-8000-000000000000"
   "actor" {"objectType" "Agent"
            "name" "Eva Loftus"
            "mbox" "mailto:eva@example.adlnet.gov"}
   "verb" {"id" "http://adlnet.gov/expapi/verbs/voided"
           "display" {"en-US" "voided"}}
   "object" {"objectType" "StatementRef"
             "id" "e3612d97-3900-4bef-92fd-d8db73e79e1b"}})

(def stmt-1
  {"id"     "00000000-0000-4000-8000-000000000001"
   "actor" {"objectType" "Agent"
            "name" "V Han"
            "mbox" "mailto:v@example.adlnet.gov"}
   "verb" {"id" "http://adlnet.gov/expapi/verbs/voided"
           "display" {"en-US" "voided"}}
   "object" {"objectType" "StatementRef"
             "id" "6a368259-c58a-4f1c-be2b-df442fbb7601"}})

(def stmt-2
  {"id"     "00000000-0000-4000-8000-000000000002"
   "actor" {"objectType" "Agent"
            "name" "Pablo Brunet"
            "mbox" "mailto:pablo@example.adlnet.gov"}
   "verb" {"id" "http://adlnet.gov/expapi/verbs/voided"
           "display" {"en-US" "voided"}}
   "object" {"objectType" "StatementRef"
             "id" "6a368259-c58a-4f1c-be2b-df442fbb7601"}})
(get [1 2 3] 1)
(get stmt-0 "id")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Unit tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest test-statement-fns
  (let [id-0  (get stmt-0 "id")
        id-1  (get stmt-1 "id")
        id-2  (get stmt-2 "id")
        agt-0 (-> stmt-0 (get "actor"))
        agt-1 (-> stmt-1 (get "actor"))
        vrb-1 (get-in stmt-1 ["verb" "id"])
        act-1 (get-in stmt-1 ["object" "id"])]
    (println "does the code reach here?")


    (comment
      (testing "statement ID queries"
        (is (= {:statement stmt-0}
               (lrsp/-get-statements lrs auth-ident {:voidedStatementId id-0} #{})))
        (is (= {:statement (update-in stmt-0 ["verb" "display"] dissoc "zh-CN")}
               (lrsp/-get-statements lrs
                                     auth-ident
                                     {:voidedStatementId id-0 :format "canonical"}
                                     #{"en-US"})))
        (is (= {:statement
                {"id"     id-1
                 "actor"  {"objectType" "Agent"
                           "mbox"       "mailto:sample.agent@example.com"}
                 "verb"   {"id" "http://adlnet.gov/expapi/verbs/answered"}
                 "object" {"id" "http://www.example.com/tincan/activities/multipart"}}}
               (lrsp/-get-statements lrs auth-ident {:statementId id-1 :format "ids"} #{})))
        (is (= {:statement stmt-2}
               (lrsp/-get-statements lrs auth-ident {:statementId id-2} #{})))))



    (component/stop lrs)
    (support/unstrument-lrsql)))





