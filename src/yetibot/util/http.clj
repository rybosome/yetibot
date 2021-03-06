(ns yetibot.util.http
  (:require [http.async.client :as client]
            [clojure.string :as s]
            [clojure.data.json :as json])
  (:import (java.net URL URLEncoder)))

; synchronous api call helpers
(defmacro with-client [uri verb-fn auth & body]
  `(with-open [~'client (client/create-client)]
     (let [~'response (~verb-fn ~'client ~uri :auth ~auth)]
       ~@body)))

(defn fetch
  ([uri] (fetch uri {:user "" :password ""})) ; default empty user/pass
  ([uri auth] (fetch uri client/GET auth)) ; default GET
  ([uri verb-fn auth]
   (with-client uri verb-fn auth
                (client/await response)
                (client/string response))))

(defn get-json [& args]
  (json/read-json (apply fetch args)))

(defn encode [s]
  (URLEncoder/encode (str s) "UTF-8"))

; query string helper
(defn map-to-query-string [m]
  (s/join "&" (map (fn [[k v]] (format "%s=%s"
                                       (encode (name k)) (encode v))) m)))
