(ns postclient
  (:require [cheshire.core :refer :all]
            [clj-http.client :as client]) 
  (:import [java.net ConnectException UnknownHostException]))

(def headers
  {"Content-Type" "application/json"
   "X-Experience-API-Version" "1.0.3"}) 

;; EDN representation of a sample xapi statement
(def sample-statement
  {"actor" {"objectType" "Agent"
            "name" "Eva Kim"
            "mbox" "mailto:admin@example.adlnet.gov"}
   "verb" {"id" "http://adlnet.gov/expapi/verbs/voided"
           "display" {"en-US" "voided"}}
   "object" {"objectType" "StatementRef"
             "id" "31ce785c-750d-4432-9356-4b08fd35c538"}})

(def sample-statement2
  {"actor" {"objectType" "Agent"
            "name" "Minva Kim"
            "mbox" "mailto:admin@example.adlnet.gov"}
   "verb" {"id" "http://adlnet.gov/expapi/verbs/voided"
           "display" {"en-US" "voided"}}
   "object" {"objectType" "StatementRef"
             "id" "31ce785c-750d-4432-9356-4b08fd35c538"}})



;; sample-statement with wrong object id
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

(defn post-statement-helper 
  [host port key secret statement]
  (client/post (str "http://" host ":" port "/xapi/statements")
               {:basic-auth [key secret]
                :body (generate-string statement)
                :headers headers
                :throw-exceptions false
                :throw-entire-message? true}))


(defn post-statement
  [host port key secret statement]
  (try
    (let [resp (post-statement-helper host port key secret statement)]
      ; handling error status codes
      ; codes other than 200, 201, 202, 203, 204, 205, 207, 300, 301, 302, 303, 304, 307 
      ; indicate error
      (cond 
        (= (:status resp) 401) (throw (ex-info (str "Status code: " (:status resp) " Reason: Invalid key or secret was entered") 
                                                {:type ::post-error})) 
        (= (:status resp) 409) (throw (ex-info (str "Status code: " (:status resp) " Reason: Cannot insert a duplicate statement")
                                                {:type ::post-error}))
        (> (:status resp) 307)  (throw (ex-info (str "Status code: " (:status resp) " Reason: " (:body resp))
                        {:type ::post-error}))
        :else {}))
    ;; catching irregular exceptions   
    ;; invalid port and auth exception messages are sent out by clj.http
    (catch UnknownHostException e
      (throw (ex-info (str "Error: " (.getMessage e))
                      {:type ::invalid-host-error
                       :message "An invalid hostname was inputted"}
                      e)))))

; REPl testing
(comment 
  ; wrong host --> built in error is good enough
  (post-statement "wrong_host" "8080" "username" "password" sample-statement)
  ; wrong portnumber --> built in error is good enough
  (post-statement "localhost" "65538" "username" "password" sample-statement)
  ; wrong username and password --> status 401
  (post-statement "localhost" "8080" "wrong_username" "password" sample-statement)
  (post-statement "localhost" "8080" "username" "wrong_password" sample-statement)
  ; badly formatted sample-statement --> status 400
  (post-statement "8080" "localhost" "username" "password" error-sample-statement)
  ;; duplicate
  (post-statement "localhost" "8080" "username" "password" sample-statement2) 
  (post-statement "localhost" "8080" "username" "password" sample-statement2)
)




