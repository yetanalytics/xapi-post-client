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

(defn post-statement
  [endpoint key secret statement]
  (try
    (let [{:keys [status body]} (post-statement* endpoint key secret statement)]
      ; handling error status codes
      ; codes other than 200, 201, 202, 203, 204, 205, 207, 300, 301, 302, 303, 304, 307 
      ; indicate error 
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
        (if (and (< status 308) (> status 299))
          (throw (ex-info (str "Status code: " status
                               " Reason: " body)
                          {:type ::redirect-error}))))
      ;; last error conditional
      (if (> status 307)
        (throw (ex-info (str "Status code: " status
                             " Reason: " body)
                        {:type ::post-error})))
      body)
    ;; catching irregular exceptions   
    ;; invalid port and auth exception messages are sent out by clj.http
    (catch UnknownHostException e
      (throw (ex-info (str "An invalid hostname was inputted")
                      {:type ::invalid-host-error}
                      e)))))
