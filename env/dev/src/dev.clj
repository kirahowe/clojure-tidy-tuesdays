(ns dev
  (:require
   [aerial.hanami.common :as hc]
   [scicloj.clay.v2.api :as clay]))

(defn build []
  (swap! hc/_defaults
         assoc
         :BACKGROUND "white")

  (clay/make!
   {:show false
    :run-quarto false
    :format [:quarto :html]
    :book {:title "Clojure Tidy Tuesdays"}
    :base-source-path "src"
    :base-target-path "docs"
    :subdirs-to-sync ["src" "data"]
    :source-path ["index.clj"
                  "year_2024/week_1/analysis.clj"
                  "year_2024/week_2/analysis.clj"
                  "year_2024/week_3/analysis.clj"
                  "year_2024/week_4/analysis.clj"
                  "year_2024/week_5/analysis.clj"]}))

(defn build-cli [_]
  (build)
  (System/exit 0))
