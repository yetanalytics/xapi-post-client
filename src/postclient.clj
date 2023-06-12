(ns postclient
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :refer :all]
            [clojure.data.json :as json]))

(def headers
  {"Content-Type" "application/json"
   "X-Experience-API-Version" "1.0.3"}) 

;; EDN representation of a sample xapi statement
(def sample-statement
  {"actor" {"objectType" "Agent"
            "name" "Admin Eva"
            "mbox" "mailto:admin@example.adlnet.gov"}
   "verb" {"id" "http://adlnet.gov/expapi/verbs/voided"
           "display" {"en-US" "voided"}}
   "object" {"objectType" "StatementRef"
             "id" "e3612d97-3900-4bef-92fd-d8db73e79e1b"}})

;; EDN representation of a sample xapi statement
(def error-sample-statement
  {"actor" {"objectType" "Agent"
            "name" "Admin ERROR"
            "mbox" "mailto:admin@example.adlnet.gov"}
   "verb" {"id" "http://adlnet.gov/expapi/verbs/voided"
           "display" {"en-US" "voided"}}
   "object" {"objectType" "StatementRef"
             "id" "e3612d9wefwefwess-3900-4bef-92fd-d8db73e79e1b"}})

; stringfied json formatting of xapi statement
; used for testing only
(def json-statement
  "{\"object\":{\"id\":\"http://example.com/xapi/activity/simplestatement\",\"definition\":{\"name\":{\"en-US\":\"simple statement\"},\"description\":{\"en-US\":\"A simple Experience API statement. Note that the LRS does not need to have any prior information about the Actor (learner), the verb, or the Activity/object.\"}}},\"verb\":{\"id\":\"http://example.com/xapi/verbs#sent-a-statement\",\"display\":{\"en-US\":\"sent\"}},\"id\":\"e3612d97-3900-4bef-92fd-d8db73e79e1b\",\"actor\":{\"mbox\":\"mailto:user@example.com\",\"name\":\"Project Minoo Can API\",\"objectType\":\"Agent\"}}")

(defn post_statement_helper 
  [host port key secret statement]
  (client/post (str "http://" host ":" port "/xapi/statements")
               {:basic-auth [key secret]
                :body (generate-string statement)
                :headers headers
                :throw-exceptions false
                :throw-entire-message? true}))

(defn post_statement
  [host port key secret statement]
  (try 
    (let [resp (post_statement_helper host port key secret statement)]
      ; handling error status codes
      ; codes other than 200, 201, 202, 203, 204, 205, 207, 300, 301, 302, 303, 304, 307 
      ; indicate error
      (if ((Integer/parseInt (:status resp)) > 307)
        (println (str "Status code: " (:status resp) (:body resp)))))
    
      ; catching irregular exceptions 
      (catch Exception e
        (throw (ex-info (str "Error: " (.getMessage e))
                        {:error e})))))

; REPl testing
(comment 
  (println (json/write-str sample-statement))
  (print(str "TESTING: " (post_statement "localhost" "8080" "username" "password" sample-statement)))
  (post_statement "localhost" "8080" "username" "password" error-sample-statement)
)




