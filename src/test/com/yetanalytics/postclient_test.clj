(ns com.yetanalytics.postclient-test
  (:require [clojure.test   :refer [deftest is are testing use-fixtures]]
            [clojure.string :as cstr]
            [clojure.tools.logging :as log]
            [com.yetanalytics.lrs :as lrs]
            [com.yetanalytics.lrs.impl.memory :as mem]
            [com.yetanalytics.lrs.pedestal.interceptor :as i]
            [com.yetanalytics.lrs.pedestal.routes :refer [build]]
            [io.pedestal.interceptor.chain :as chain]
            [io.pedestal.interceptor :refer [interceptor]]
            [com.yetanalytics.lrs.protocol  :as lrsp]
            [io.pedestal.http :as http]
            [com.yetanalytics.postclient :as pc])
  (:import [java.net ServerSocket]
           [java.util Base64 Base64$Decoder]
           [java.nio.charset Charset]))

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

;; helper functions for testing user/pass params

(def utf8-charset
  (Charset/forName "UTF-8"))

(def ^Base64$Decoder decoder
  "The default Base64 decoder."
  (Base64/getDecoder))

(defn bytes->str
  "Converts `bytes` into a string. Assumes UTF-8 encoding."
  [^"[B" bytes]
  (String. bytes ^Charset utf8-charset))

(defn header->key-pair
  "Given a header of the form `Basic [Base64 string]`, return a map with keys
   `:api-key` and `:secret-key`. The map can then be used as the input to
   `query-authentication`. Return `nil` if the header is `nil` or cannot
   be decoded."
  [auth-header]
  (when auth-header
    (try (let [^String auth-part   (second (re-matches #"Basic\s+(.*)"
                                                       auth-header))
               ^String decoded     (bytes->str (.decode decoder auth-part))
               [?api-key ?srt-key] (cstr/split decoded #":")]
           {:api-key    (if ?api-key ?api-key "")
            :secret-key (if ?srt-key ?srt-key "")})
         (catch Exception _ nil))))

;; helper functions for testing successful POST

(defn- remove-props
  "Remove properties added by `prepare-statement`."
  [statement]
  (dissoc statement "timestamp" "stored" "authority" "version"))

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

(defn get-auth-result [ctx]
  (let [header (header->key-pair (get-in ctx [:request :headers "authorization"]))
        key (:api-key header)
        secret (:secret-key header)]
    (if (and (= key "username") (= secret "password"))
      {:result
       {:scopes #{:scope/all},
        :prefix "",
        :auth {:basic {:username "username", :password "password"}},
        :agent
        {"account"
         {"homePage" "http://example.org",
          "name" "0188bab2-f0ab-8926-8c27-a4858a1fc04d"},
         "objectType" "Agent"}}}
      {:result :com.yetanalytics.lrs.auth/unauthorized})))

;; for error 301 redirect testing purposes

(def simulate-301
  (interceptor
   {:name ::simulate-301
    :enter
    (fn simulate-301 [ctx]
      (assoc (chain/terminate ctx)
             :response
             {:status 301
              :body   nil
              :headers {"Location" "https://www.yetanalytics.com"}}))}))

(defn create-lrs
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
        mem-lrs (mem/new-lrs {:mode :sync})
        ;; adding user/pass auth credientials to lrs instance
        lrs (reify
              lrsp/LRSAuth
              (-authenticate [this ctx]
                (get-auth-result ctx))
              (-authorize [this ctx auth-identity]
                (lrsp/-authorize mem-lrs ctx auth-identity))
              lrsp/StatementsResource
              (-store-statements [this auth-identity statements attachments]
                (lrsp/-store-statements mem-lrs auth-identity statements attachments))
              (-get-statements [this auth-identity params ltags]
                (lrsp/-get-statements mem-lrs auth-identity params ltags))
              (-consistent-through [this ctx auth-identity]
                (lrsp/-consistent-through mem-lrs ctx auth-identity))
              mem/DumpableMemoryLRS
              (dump [_]
                (mem/dump mem-lrs)))
        routes (conj (build {:lrs lrs}) ["/xapi/badpath/statements" :post [simulate-301]]) 
        service
        {:env                   :dev
         :lrs                   lrs
         ::http/join?           false
         ::http/allowed-origins {:creds           true
                                 :allowed-origins (constantly true)}
         ::http/routes          routes
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
(defn lrs-fixture
  "Populate *test-lrs* with started a LRS on a free port
  LRSs are empty by default unless seed-path is provided"
  [f] 
  (let [{start-test :start 
         stop-test :stop 
         :as test} (create-lrs)]
    (try
      ;; start LRS --> shutdown if exceptions are thrown 
      (start-test) 
      (binding [*test-lrs* test] 
        (f)) 
      (finally 
        (stop-test)))))

(use-fixtures :each lrs-fixture)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sample statements
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def stmt-0
  {"id"     "00000000-0000-4000-8000-000000000000"
   "actor" {"objectType" "Agent"
            "name" "Eva Kim"
            "mbox" "mailto:eva@example.adlnet.gov"}
   "verb" {"id" "http://adlnet.gov/expapi/verbs/voided"
           "display" {"en-US" "voided"}}
   "object" {"objectType" "StatementRef"
             "id" "e3612d97-3900-4bef-92fd-d8db73e79e1b"}})

(def stmt-0-changed
  {"id"     "00000000-0000-4000-8000-000000000000"
   "actor" {"objectType" "Agent"
            "name" "Minva Kim"
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
            "name" "Daniel Song"
            "mbox" "mailto:daniel@example.adlnet.gov"}
   "verb" {"id" "http://adlnet.gov/expapi/verbs/fight"
           "display" {"en-US" "fight"}}
   "object" {"objectType" "StatementRef"
             "id" "6a368259-c58a-4f1c-be2b-df442fbb7601"}})

(def stmt-inval
  {"id"     "00000000-0000-4000-8000-000000000004"
   "actor" {"objectType" "Agent"
            "name" "Sarah Bao"
            "mbox" "mailto:sarah@example.adlnet.gov"}
   "verb" {"id" "http://adlnet.gov/expapi/verbs/fight"
           "display" {"en-US" "fight"}}
   "object" {"objectType" "StatementRef"
             "id" "invalid-uuid"}})

(def stmt-wrong-format
  {"wrong category" "id?"
   "wrong category#2" "first name" 
   "wrong category #3" "last name"})

(def stmt-incomplete
  {"id"     "00000000-0000-4000-8000-000000000005"
   "actor" {"objectType" "Agent"
            "name" "Darshan Fester"
            "mbox" "mailto:darshan@example.adlnet.gov"}})

(def uri "http://localhost:%d/xapi")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Unit tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest test-post-client-stmts
  (let [id-0  (get stmt-0 "id")
        id-1  (get stmt-1 "id")
        id-2  (get stmt-2 "id")
        id-3  (get stmt-3 "id")
        {:keys [port lrs]} *test-lrs*
        endpoint (format uri port)]
    ;; insert statements to lrs
    (doseq [stmt [stmt-0 stmt-1 stmt-2 stmt-3]]
           (pc/post-statement endpoint "username" "password" stmt))
    (testing "testing if statements match"
      (are [stmt id] (= {:statement stmt} (get-ss lrs auth-ident {:statementId id} #{}))
        stmt-0 id-0
        stmt-1 id-1
        stmt-2 id-2
        stmt-3 id-3))))

(deftest test-post-client-return-value
   (let [id-0  (get stmt-0 "id")
         id-1  (get stmt-1 "id")
         id-2  (get stmt-2 "id")
         id-3  (get stmt-3 "id")
         {:keys [port]} *test-lrs*
         endpoint (format uri port)]
     (testing "testing if id matches return-ID"
       (are [id stmt] (= [id] (pc/post-statement endpoint "username" "password" stmt)) 
         id-0 stmt-0
         id-1 stmt-1
         id-2 stmt-2
         id-3 stmt-3))))

(deftest test-post-client-mult-statements
  (let [id-0  (get stmt-0 "id")
        id-1  (get stmt-1 "id")
        id-2  (get stmt-2 "id")
        id-3  (get stmt-3 "id")
        {:keys [port]} *test-lrs*
        endpoint (format uri port)
        return-IDs (pc/post-statement endpoint "username" "password" 
                                      [stmt-0 stmt-1 stmt-2 stmt-3])] 
    (is (= [id-0 id-1 id-2 id-3] return-IDs))))

(deftest test-post-client-invalid-args
  (testing "testing for invalid hostname" 
    (try
      (pc/post-statement "http://invalidhost:8080/xapi" "username" "password" stmt-0)
      (catch Exception e 
        (is (= ::pc/invalid-host-error 
               (-> e Throwable->map :via first :data :type))))))
  (testing "testing for invalid port number"
    (try
     (pc/post-statement "http://localhost:10000000/xapi" "username" "password" stmt-0)
      (catch Exception e 
        (is (= "port out of range:10000000"
               (-> e Throwable->map :via first :message))))))
  (testing "testing for invalid key"
    (try
      (let [{:keys [port]} *test-lrs*]
        (pc/post-statement (format uri port) "wrong_username" "password" stmt-inval))
      (catch Exception e
        (is (= ::pc/auth-error
               (-> e Throwable->map :via first :data :type))))))
  (testing "testing for invalid secret"
    (try
      (let [{:keys [port]} *test-lrs*]
        (pc/post-statement (format uri port) "username" "wrong_password" stmt-inval))
      (catch Exception e
        (is (= ::pc/auth-error
               (-> e Throwable->map :via first :data :type)))))))
  
(deftest test-post-client-invalid-statements 
  (testing "testing for statement with invalid object UUID"
   (try 
     (let [{:keys [port]} *test-lrs*] 
       (pc/post-statement (format uri port) "username" "password" stmt-inval)) 
     (catch Exception e 
       (is (= ::pc/post-error 
              (-> e Throwable->map :via first :data :type))))))
  (testing "testing for statement with completely wrong format"
    (try
      (let [{:keys [port]} *test-lrs*]
        (pc/post-statement (format uri port) "username" "password" stmt-wrong-format))
      (catch Exception e
        (is (= ::pc/post-error
               (-> e Throwable->map :via first :data :type))))))
  (testing "testing for statement with no verb and object" 
    (try 
      (let [{:keys [port]} *test-lrs*] 
        (pc/post-statement (format uri port) "username" "password" stmt-incomplete)) 
      (catch Exception e 
        (is (= ::pc/post-error 
               (-> e Throwable->map :via first :data :type)))))))
  
(deftest test-post-client-duplicate-statements
  (testing "testing for POSTing a duplicate statement"
    (try
      (let [{:keys [port]} *test-lrs*]
        (pc/post-statement (format uri port) "username" "password" stmt-0)
        (pc/post-statement (format uri port) "username" "password" stmt-0-changed))
      (catch Exception e
        (is (= ::pc/post-error 
               (-> e Throwable->map :via first :data :type)))))))

(deftest test-post-client-redirect
  (testing "testing for a URI with a bad path"
    (try
      (let [{:keys [port]} *test-lrs*]
        (pc/post-statement (str "http://localhost:" port "/xapi/badpath")
                           "username" "password" stmt-0))
      (catch Exception e
        (is (= ::pc/redirect-error 
               (-> e Throwable->map :via first :data :type)))))))
