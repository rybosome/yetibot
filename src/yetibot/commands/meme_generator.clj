(ns yetibot.commands.meme-generator
  (:require [http.async.client :as client]
            [clojure.string :as s]
            [yetibot.core :as core]
            [robert.hooke :as rh])
  (:use [yetibot.hooks :only [cmd-hook]]
        [yetibot.util.http]))

(def base-uri "http://version1.api.memegenerator.net/")
(def base-image-uri "http://a.static.memegenerator.net")
(def apis {:trending "Generators_Select_ByTrending"
           :popular "Generators_Select_ByPopular?pageIndex=0&pageSize=12&days=7"
           :instance-popular "Instances_Select_ByPopular"
           :create "Instance_Create"
           :search-generators "Generators_Search?q="})

(def auth {:user (System/getenv "MEME_USER")
           :password (System/getenv "MEME_PASS")})


; API calls
(def gen-trending
  "Retrieves trending generators"
  (memoize (fn []
             (get-json (str base-uri (:trending apis)) auth))))

(defn gen-popular []
  "Retrieves popular generators"
  (let [uri (str base-uri (:popular apis))]
    (println uri)
    (get-json uri auth)))

(defn search-generators [q]
  (get-json (str base-uri (:search-generators apis) (encode q)) auth))

(defn get-first-generator [q]
  (let [json (search-generators q)
        result (:result json)]
    (if (empty? result)
      {:urlName ""} ; stub an empty result to prevent errors TODO: improve
      (first result))))

(defn instances-popular
  ([] (instances-popular ""))
  ([gen]
   "Retrieves popular instances. Retrieves popular only for `gen` if specified."
   (println (str "gen is " gen))
   (let [uri (str base-uri (:instance-popular apis) "?"
                  (map-to-query-string
                    (merge {:languageCode "en" :pageSize 20 :days 7}
                           (when-not (empty? gen)
                             ; clear days to search for popular instance of all time
                             {:days 7 :urlName (:urlName (get-first-generator gen))}))))]
     (println uri)
     (get-json uri auth))))

(defn create-instance [query-string]
  (let [uri (str base-uri (:create apis) "?" query-string)]
    (println uri)
    (let [result (:result (get-json uri auth))]
      (println result)
      ; hit the instance url to force actual generation
      (with-client (:instanceUrl result) client/GET auth
                   (client/await response)
                   result))))

; Helpers
(defn extract-ids [json]
  {:generatorID (:generatorID json)
   :imageID (re-find #"\d+(?=\D*$)" (:imageUrl json))})

(defn build-instance-params [meme text1 text2]
  "Accepts `meme` arg of either:
  - meme name (performs a search for meme json)
  - an existing valid json response to avoid a search request
  text1 is required, text2 is optional. When text2 is missing, text1 is used
  in place of text2 and text1 is left empty"
  (merge (extract-ids
           (if (string? meme)
             (first (:result (search-generators meme)))
             meme))
         auth
         {:username (:user auth)
         :languageCode 'en
         :text0 text1
         :text1 text2}))


; Chat senders
(defn chat-instance [i]
  (str (s/replace (:instanceImageUrl i) "400x" "500x")))

(defn chat-instance-popular
  "meme popular                # list random popular meme instances from the top 20 in the last day"
  [_]
  (chat-instance (rand-nth (:result (instances-popular)))))

(defn chat-instance-popular-for-gen
  "meme popular <generator>    # list random popular meme instances for <generator> from the top 20 in the last day"
  [{[_ gen] :match}]
  (let [json (instances-popular gen)
        result (:result json)]
    (if (empty? result)
      (str "No popular instances for " gen)
      (chat-instance (rand-nth result)))))

; This no longer needs to return a string. The chat handler stringifies it for us.
(defn chat-meme-list [l]
  (if (and l (seq (:result l)))
    (map #(:displayName %) (:result l))
    "No results"))


;; retry api calls - TODO add https://github.com/joegallo/robert-bruce

(defn trending-cmd
  "meme trending               # list trending generators"
  [_] (chat-meme-list (gen-trending)))

(defn search-cmd
  "meme search <term>          # query available meme generators"
  [{[_ term] :match}]
  (chat-meme-list
    (search-generators term)))

(defn generate-cmd
  "meme <generator>: <line1> / <line2> # generate an instance"
  [{[_ inst line1 line2] :match}]
  (println (str "generate meme " inst))
  (chat-instance
    (create-instance
      (map-to-query-string
        (build-instance-params inst line1 line2)))))

(defn generate-auto-split-cmd
  "meme <generator>: <text> # autosplit <text> in half and generate the instance"
  [{[_ inst text] :match}]
  (prn "auto split" inst text)
  (let [spl (s/split text #"\s")]
    (generate-cmd
      {:match (list* nil inst
                     (map (partial s/join " ")
                          (split-at (/ (count spl) 2) spl)))})))

(cmd-hook ["meme" #"meme$"]
          #"^popular$" chat-instance-popular
          #"^popular\s(.+)" chat-instance-popular-for-gen
          #"^trending" trending-cmd
          #"^(.+?):(.+)\/(.*)$" generate-cmd
          #"^(.+?):(.+)$" generate-auto-split-cmd
          #"^(?:search\s)?(.+)" search-cmd)
