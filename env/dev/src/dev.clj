(ns dev
  (:require [scicloj.clay.v2.api :as clay]))

(defn build []
  (clay/make!
   {:format [:quarto :html]
    :book {:title "Clojure Tidy Tuesdays"}
    :base-source-path "src"
    :subdirs-to-sync ["notebooks" "data"]
    :source-path ["index.clj"
                  "year_2024/week_1/analysis.clj"]}))
