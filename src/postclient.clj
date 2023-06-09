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
            "name" "Admin Minoo"
            "mbox" "mailto:admin@example.adlnet.gov"}
   "verb" {"id" "http://adlnet.gov/expapi/verbs/voided"
           "display" {"en-US" "voided"}}
   "object" {"objectType" "StatementRef"
             "id" "5631dbf0-ebc5-4163-874b-c32088db9fdd"}})

;; stringfied json formatting of xapi statement
; used for testing only
(def json-statement
  "{\"object\":{\"id\":\"http://example.com/xapi/activity/simplestatement\",\"definition\":{\"name\":{\"en-US\":\"simple statement\"},\"description\":{\"en-US\":\"A simple Experience API statement. Note that the LRS does not need to have any prior information about the Actor (learner), the verb, or the Activity/object.\"}}},\"verb\":{\"id\":\"http://example.com/xapi/verbs#sent-a-statement\",\"display\":{\"en-US\":\"sent\"}},\"id\":\"c6b212fa-a958-43ce-8f42-99b7d991617e\",\"actor\":{\"mbox\":\"mailto:user@example.com\",\"name\":\"Project Minoo Can API\",\"objectType\":\"Agent\"}}")

(defn post_statement 
  [host port key secret statement]
  (client/post (str "http://" host ":" port "/xapi/statements")
               {:basic-auth [key secret]
                :body (generate-string statement)
                :headers headers
                :throw-entire-message? true}))

(comment 
  (println (json/write-str sample-statement))
  (post_statement "localhost" "8080" "username" "password" sample-statement))




