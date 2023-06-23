(ns com.yetanalytics.postclient
  (:require [cheshire.core :as json]
            [clj-http.client :as client]) 
  (:import [java.net UnknownHostException]))

(def headers
  {"Content-Type" "application/json"
   "X-Experience-API-Version" "1.0.3"}) 

(defn- post-statement* 
  [host port key secret statement]
  (client/post (str "http://" host ":" port "/xapi/statements")
               {:basic-auth [key secret]
                :body (json/generate-string statement)
                :headers headers
                :throw-exceptions false
                :throw-entire-message? true}))

(defn post-statement
  [host port key secret statement]
  (try
    (let [resp (post-statement* host port key secret statement)]
      ; handling error status codes
      ; codes other than 200, 201, 202, 203, 204, 205, 207, 300, 301, 302, 303, 304, 307 
      ; indicate error
      (cond 
        (= (:status resp) 401) 
        (throw (ex-info (str "Status code: " (:status resp) 
                             " Reason: Invalid key or secret was entered")
                        {:type ::auth-error})) 
        (= (:status resp) 409) 
        (throw (ex-info (str "Status code: " (:status resp)
                             " Reason: Cannot insert a duplicate statement")
                        {:type ::post-error}))
        (> (:status resp) 307)
        (throw (ex-info (str "Status code: " (:status resp)
                             " Reason: " (:body resp))
                        {:type ::post-error}))
        :else {}))
    ;; catching irregular exceptions   
    ;; invalid port and auth exception messages are sent out by clj.http
     (catch UnknownHostException e
       (throw (ex-info (str "An invalid hostname was inputted")
                       {:type ::invalid-host-error}
                       e)))
     (catch Exception e
       (throw (ex-info (str "An unexpected error has occured")
                       {:type ::functional-error}
                       e)))))
