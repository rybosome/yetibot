(ns yetibot.commands.wolfram
  (:require [clojure.string :as s]
            [clojure.xml :as xml]
            [yetibot.hooks :refer [cmd-hook]]))

(def app-id (System/getenv "WOLFRAM_APP_ID"))
(def endpoint (str "http://api.wolframalpha.com/v2/query?appid=" app-id))

(defn parse-imgs-from-xml [xml]
  (let [xs (xml-seq xml)]
    (for [el xs :when (= :img (:tag el))] [(:alt (:attrs el)) (:src (:attrs el))])))

(defn search-wolfram
  "wolfram <query> # search for <query> on Wolfram Alpha"
  [{q :match}]
  (flatten
    (map #(str (second %) "&t=.jpg")
      (parse-imgs-from-xml
        (xml/parse (str endpoint "&input=" q))))))

(cmd-hook #"wolfram"
          #".*" search-wolfram)
