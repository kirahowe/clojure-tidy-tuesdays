(ns year-2024.week-8.analysis
  (:require
   [clojure.string :as str]
   [scicloj.kindly.v4.kind :as kind]
   [tablecloth.api :as tc]))

;; # Week 8 - R Consortium ISC Grants

;; This week's data is about funding available from the R Consortium Infrastructure Steering Committee (ISC) Grant Program. They've been awarding grants since 2016, with a "commitment to bolstering and enhancing the R Ecosystem".
;;
;; There's no example article to replicate, but we'll poke around the data a bit to explore some patterns and see what kinds of projects get funded.
;;
;; The data this week was gathered in a somewhat interesting way; it was scraped off of the web page of funding announcements. You can check out the code [here](), and I'm working on a more generic guide to web scraping in Clojure.



;; Are there any keywords that stand out in the titles or summaries of awarded grants? Have the funded amounts changed over time?

;; Are there any keywords that stand out in the titles or summaries of awarded grants? Have the funded amounts changed over time?

(def all-grants (tc/dataset "data/year_2024/week_8/isc-grants.csv"))

(def stopwords (-> "src/year_2024/week_8/stopwords.txt"
                    slurp
                    (str/split #"\n")
                    set
                    (conj "")))

(def counts
  (let [titles-and-summaries (-> all-grants
                                 (tc/select-columns ["title" "summary"])
                                 (tc/columns :as-seqs)
                                 flatten)]
    (->> titles-and-summaries
         (mapcat #(str/split % #" "))
         (map #(-> % (str/replace #"\W" "") str/lower-case))
         (remove stopwords)
         frequencies
         (sort-by second))))

(def words
  (let [titles-and-summaries (-> all-grants
                                 (tc/select-columns ["title" "summary"])
                                 (tc/columns :as-seqs)
                                 flatten)]
    (->> titles-and-summaries
         (mapcat #(str/split % #" "))
         (map #(-> % (str/replace #"\W" "") str/lower-case))
         (remove stopwords))))

(kind/vega {:data
            [{:name "table"
              :values words
              :transform
              [{:type "countpattern"
                :field "data"
                :case "upper"
                :pattern "[\\w']{3,}"}
               {:type "formula",
                :as "angle",
                :expr "[-45, -25, 0, 25, 45][~~(random() * 5)]"}
               ;; {:type "formula",
               ;;  :as "weight",
               ;;  :expr "if(datum.text=='CHRISTMAS' |
               ;;              datum.text=='HOLIDAY' |
               ;;              datum.text=='HANNUKAH', 600, 300)"}
               ]}],
            :scales
            [{:name "color"
              :type "ordinal",
              :domain {:data "table", :field "text"},
              :range ["grey" "blue" "red" "orange"]}],
            :marks
            [{:type "text",
              :from {:data "table"},
              :encode
              {:enter
               {:text {:field "text"},
                :align {:value "center"},
                :baseline {:value "alphabetic"},
                :fill {:scale "color", :field "text"}},
               :update {:fillOpacity {:value 1}},
               :hover {:fillOpacity {:value 0.5}}},
              :transform
              [{:fontSizeRange [5 60],
                :type "wordcloud",
                :font "Helvetica Neue, Arial",
                :size [1200 900],
                :padding 2,
                :fontWeight {:field "datum.weight"},
                :rotate {:field "datum.angle"},
                :fontSize {:field "datum.count"},
                :text {:field "text"}}]}]})
