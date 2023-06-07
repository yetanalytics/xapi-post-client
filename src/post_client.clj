(ns post_client
  (require '[clj-http.client :as client])
  (require '[clojure.string :as str]))

(def headers
  {"Content-Type" "application/json"
   "X-Experience-API-Version" "1.0.3"})

(defn print-args [arg]
  (println "print-args function called with arg: " arg))

;; statement is inserted in edn format
(defn post_statement
  [host key secret statement]
  (client/post (str "http://" host ":8080/xapi/statements")
               {:basic-auth [key secret]
                :body (json/generate-string statement)
                :headers headers
                :content-type :json
                :socket-timeout 1000
                :connection-timeout 1000
                :accept :json}))

