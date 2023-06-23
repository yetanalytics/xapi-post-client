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
    (let [resp (post-statement* host port key secret statement)
          status (:status resp)]
      ; handling error status codes
      ; codes other than 200, 201, 202, 203, 204, 205, 207, 300, 301, 302, 303, 304, 307 
      ; indicate error 
      (case status
        401 (throw (ex-info (str "Status code: " (:status resp)
                                 " Reason: Invalid key or secret was entered")
                            {:type ::auth-error}))
        403 (throw (ex-info (str "Status code: " (:status resp)
                                 " Reason: Cannot access area where permission is not granted")
                            {:type ::forbidden-error}))
        409 (throw (ex-info (str "Status code: " (:status resp)
                                 " Reason: Cannot insert a duplicate statement")
                            {:type ::post-error}))
        (if (> (:status resp) 307)
          (throw (ex-info (str "Status code: " (:status resp)
                               " Reason: " (:body resp))
                          {:type ::post-error})))))
    ;; catching irregular exceptions   
    ;; invalid port and auth exception messages are sent out by clj.http
    (catch UnknownHostException e
      (throw (ex-info (str "An invalid hostname was inputted")
                      {:type ::invalid-host-error}
                      e)))))
