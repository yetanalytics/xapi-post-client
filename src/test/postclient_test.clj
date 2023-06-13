(ns postclient_test
  (:require [clojure.test   :refer [deftest testing is use-fixtures]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [com.yetanalytics.lrs :as lrs]
            [com.yetanalytics.lrs.pedestal.routes :refer [build]]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [com.yetanalytics.lrs.xapi.statements :as ss]
            [com.yetanalytics.lrs.xapi.document   :as doc]
            [io.pedestal.http :as http]
            [com.yetanalytics.lrs.protocol  :as lrsp]
            [com.yetanalytics.lrs.impl.memory :as mem])
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


(defn lrs
  "Creates a unified object to start/stop a LRS instance:
  :port - For debugging
  :lrs - The LRS itself
  :start - A function of no args that will start the LRS
  :stop - A function of no args that will stop the LRS
  :dump - A function of no args that will dump memory LRS state
  :load - A function of two args, statements and attachments to load data"
  [& {:keys [seed-path
             port]}]
  (let [port (or port
                 (get-free-port))
        lrs mem/new-lrs
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
                              :xapi-prefix "/xapi"}})))

(defn source-target-fixture
  "Populate *source-lrs* and *target-lrs* with started LRSs on two free ports.
  LRSs are empty by default unless seed-path is provided"
  ([f]
   (source-target-fixture {} f))
  ([{:keys [seed-path]} f]
   (let [{start-source :start
          stop-source :stop
          :as source} (if seed-path
                        (lrs :seed-path seed-path)
                        (lrs))
         {start-target :start
          stop-target :stop
          :as target} (lrs)]
     (try
       ;; Start Em Up!
       (start-source)
       (start-target)
       (binding [*source-lrs* source
                 *target-lrs* target]
         (f))
       (finally
         (stop-source)
         (stop-target))))))

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





