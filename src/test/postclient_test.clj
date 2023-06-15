(ns postclient-test
  (:require [clojure.test   :refer [deftest is testing use-fixtures]]
            [clojure.tools.logging :as log]
            [com.yetanalytics.lrs :as lrs]
            [com.yetanalytics.lrs.impl.memory :as mem]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [com.yetanalytics.lrs.pedestal.routes :refer [build]]
            [com.yetanalytics.lrs.protocol  :as lrsp]
            [io.pedestal.http :as http]
            [postclient :as pc])
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

(defn- remove-props
  "Remove properties added by `prepare-statement`."
  [statement]
  (-> statement
      (dissoc "timestamp")
      (dissoc "stored")
      (dissoc "authority")
      (dissoc "version")))

(defn get-ss
  "Same as `lrsp/-get-statements` except that `remove-props` is applied
   on the results."
  [lrs auth-ident params ltags]
  (if (or (contains? params :statementId)
          (contains? params :voidedStatementId))
    (-> (lrsp/-get-statements lrs auth-ident params ltags)
        (update :statement remove-props))
    (-> (lrsp/-get-statements lrs auth-ident params ltags)
        (update-in [:statement-result :statements]
                   (partial map remove-props)))))

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
  [& {:keys [port]}]
  (let [port (or port
                 (get-free-port))
        lrs (mem/new-lrs {:mode :sync})
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

;; fixture for uniformed testing
(def ^:dynamic *test-lrs* nil)
(defn test-fixture
  "Populate *test-lrs* with started a LRS on a free port
  LRSs are empty by default unless seed-path is provided"
  [f] 
  (let [{start-test :start 
         stop-test :stop 
         :as test} (lrs)]
    (try
      ;; start LRS --> shutdown if exceptions are thrown 
      (start-test) 
      (binding [*test-lrs* test] 
        (f)) 
      (finally 
        (stop-test)))))

(use-fixtures :each test-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sample statements
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def stmt-0
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
   "verb" {"id" "http://adlnet.gov/expapi/verbs/faded"
           "display" {"en-US" "faded"}}
   "object" {"objectType" "StatementRef"
             "id" "6a368259-c58a-4f1c-be2b-df442fbb7601"}})

(def stmt-2
  {"id"     "00000000-0000-4000-8000-000000000002"
   "actor" {"objectType" "Agent"
            "name" "Pablo Brunet"
            "mbox" "mailto:pablo@example.adlnet.gov"}
   "verb" {"id" "http://adlnet.gov/expapi/verbs/move"
           "display" {"en-US" "move"}}
   "object" {"objectType" "StatementRef"
             "id" "6a368259-c58a-4f1c-be2b-df442fbb7601"}})

(def stmt-3
  {"id"     "00000000-0000-4000-8000-000000000003"
   "actor" {"objectType" "Agent"
            "name" "Daniel Soong"
            "mbox" "mailto:daniel@example.adlnet.gov"}
   "verb" {"id" "http://adlnet.gov/expapi/verbs/fight"
           "display" {"en-US" "fight"}}
   "object" {"objectType" "StatementRef"
             "id" "6a368259-c58a-4f1c-be2b-df442fbb7601"}})





;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Unit tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest test-post-client-st0
  (let [id-0  (get stmt-0 "id")
        {:keys [port lrs]} *test-lrs*]
    ;; insert to lrs
    (pc/post-statement "localhost" port "username" "password" stmt-0)
    (testing "testing if statements match"
      (is (= {:statement stmt-0}
             (get-ss lrs auth-ident {:statementId id-0} #{}))))))

(deftest test-post-client-st1
  (let [id-1  (get stmt-1 "id")
        {:keys [port lrs]} *test-lrs*]
    ;; insert to lrs
    (pc/post-statement "localhost" port "username" "password" stmt-1)
    (testing "testing if statements match"
      (is (= {:statement stmt-1}
             (get-ss lrs auth-ident {:statementId id-1} #{}))))))

(deftest test-post-client-st2
  (let [id-2  (get stmt-2 "id")
        {:keys [port lrs]} *test-lrs*]
    ;; insert to lrs
    (pc/post-statement "localhost" port "username" "password" stmt-2)
    (testing "testing if statements match"
      (is (= {:statement stmt-2}
             (get-ss lrs auth-ident {:statementId id-2} #{}))))))

(deftest test-post-client-st3
  (let [id-3  (get stmt-3 "id")
        {:keys [port lrs]} *test-lrs*]
    ;; insert to lrs
    (pc/post-statement "localhost" port "username" "password" stmt-3)
    (testing "testing if statements match"
      (is (= {:statement stmt-3}
             (get-ss lrs auth-ident {:statementId id-3} #{}))))))

(deftest test-post-client-invalid-args
  (testing "testing for invalid hostname" 
    (try
      (pc/post-statement "invalidhost" 8080 "username" "password" stmt-0)
      (catch Exception e 
        (is (= "An invalid hostname was inputted"
                  (:message (:data (first (:via (Throwable->map e))))))))))
  (testing "testing for invalid port number"
    (try
     (pc/post-statement "localhost" 10000000 "username" "password" stmt-0)
      (catch Exception e 
        (is (= "port out of range:10000000" 
               (:message (second (:via (Throwable->map e)))))))))
   (testing "testing for invalid username"
     (try
       (pc/post-statement "localhost" 8080 "invalidusername" "password" stmt-0)
       (catch Exception e
         (is (= "An invalid username or password was inputted"
                  (:message (:data (first (:via (Throwable->map e))))))))))
   (testing "testing for invalid password"
     (try
       (pc/post-statement "localhost" 8080 "username" "invalidpassword" stmt-0)
       (catch Exception e
         (is (= "An invalid username or password was inputted"
                  (:message (:data (first (:via (Throwable->map e)))))))))))

; TODO:
;; invalid xapi statement inputs
;; completely wrong format
;; duplicate statements
;; bad lrs??
;; merge