(ns postclient
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [cheshire.core :refer :all]))

(def headers
  {"Content-Type" "application/json"
   "X-Experience-API-Version" "1.0.3"})

(def sample_statement
  {"actor" {"objectType" "Agent"
            "name" "Example Admin"
            "mbox" "mailto:admin@example.adlnet.gov"}
   "verb" {"id" "http://adlnet.gov/expapi/verbs/voided"
           "display" {"en-US" "voided"}}
   "object" {"objectType" "StatementRef"
             "id" "e05-aa883-acaf-40ad-bf54-02c8ce485fb0"}})

(def json_statement
  "{\"object\":{\"id\":\"http://example.com/xapi/activity/simplestatement\",
  \"definition\":{\"name\":{\"en-US\":\"simple statement\"},\"description\":
  {\"en-US\":\"A simple Experience API statement. Note that the LRS\\n
  does not need to have any prior information about the Actor (learner), the\\n
  verb, or the Activity/object.\"}}},\"verb\":{\"id\":\"http://example.com/xapi
  /verbs#sent-a-statement\",\"display\":{\"en-US\":\"sent\"}},\"id\":\"fd41c918-
  b88b-4b20-a0a5-a4c32391aaa0\",\"actor\":{\"mbox\":\"mailto:user@example.com\"
  ,\"name\":\"Project Tin Can API\",\"objectType\":\"Agent\"}}")

;; statement is inserted in edn format
(defn post_statement 
  [host key secret statement]
  (client/post (str "http://" host ":8080/xapi/statements")
               {:basic-auth [key secret]
                :body (generate-string statement)
                :headers headers
                :content-type :json
                :socket-timeout 1000
                :connection-timeout 1000
                :accept :json}))

(print (generate-string sample_statement))
sample_statement

(post_statement "localhost" "username" "password" json_statement)




