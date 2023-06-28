(ns com.yetanalytics.postclient
  (:require [cheshire.core :as json]
            [clj-http.client :as client]) 
  (:import [java.net UnknownHostException]))

(def headers
  {"Content-Type" "application/json"
   "X-Experience-API-Version" "1.0.3"}) 

(defn- post-statement* 
  [endpoint key secret statement]
  (client/post (str endpoint "/statements")
               {:basic-auth [key secret]
                :body (json/generate-string statement)
                :headers headers
                :throw-exceptions false
                :throw-entire-message? true
                :as :json}))

;

(defn post-statement
  [endpoint key secret statement]
  (try
    (let [{:keys [status body headers]} (post-statement* endpoint key secret statement)]
      ;; handling error status codes
      ;; codes other than 200, 201, 202, 203, 204, 205, 207 indicate error 
      (case status
        401 (throw (ex-info (str "Status code: " status
                                 " Reason: Invalid key or secret was entered")
                            {:type ::auth-error}))
        403 (throw (ex-info (str "Status code: " status
                                 " Reason: Cannot access area where permission is not granted")
                            {:type ::forbidden-error}))
        409 (throw (ex-info (str "Status code: " status
                                 " Reason: Cannot insert a duplicate statement")
                            {:type ::post-error}))
        (cond
          (<= 300 status 307)
          (throw (ex-info (str "Status code: " status " Location: " (:location headers))
                          {:type ::redirect-error}))
          (<= 308 status)
          (throw (ex-info (str "Status code: " status " Reason: " body)
                          {:type ::post-error}))))
          body)
    ;; catching irregular exceptions   
    ;; invalid port and auth exception messages are sent out by clj.http
    (catch UnknownHostException e
      (throw (ex-info (str "An invalid hostname was inputted")
                      {:type ::invalid-host-error}
                      e)))))
