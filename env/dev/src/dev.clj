(ns dev
  (:require [scicloj.clay.v2.api :as clay]))

(defn build []
  (clay/make!
   {:format [:quarto :html]
    :book {:title "Clojure Tidy Tuesdays"}
    :base-source-path "src"
    :base-target-path "docs"
    :subdirs-to-sync ["src" "data"]
    :source-path ["index.clj"
                  "year_2024/week_1/analysis.clj"
                  "year_2024/week_2/analysis.clj"
                  "year_2024/week_3/analysis.clj"
                  "year_2024/week_4/analysis.clj"]}))
