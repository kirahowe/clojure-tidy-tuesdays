(ns year-2024.week-1.analysis
  (:require [tablecloth.api :as tc]
            [scicloj.noj.v1.vis.hanami :as hanami]
            [aerial.hanami.templates :as ht]
            [scicloj.kindly.v4.kind :as kind]))

;; # Week 1 - Holiday Movies

;; In the interest of just getting started, this week I'll look at [a recent dataset from the 2023 tidy tuesday repo about holiday movies](https://github.com/rfordatascience/tidytuesday/tree/master/data/2023/2023-12-12), published by the R4DS community.
;; For posterity, I've also saved the data in this repo, where it's available in the [`data/year_2024/week_1/`](https://github.com/kiramclean/clojure-tidy-tuesdays/tree/main/data/year_2024/week_1) folder.

;; For details about how the dataset was generated, see [`src/year_2024/week_1/generate_dataset.clj`](https://github.com/kiramclean/clojure-tidy-tuesdays/blob/main/data/year_2024/week_1/generate_dataset.clj).

;; ## Making a bar chart

;; To get started, we'll load the data into our notebook, starting with the dataset of holiday movies:

(def holiday-movies
  (tc/dataset "data/year_2024/week_1/holiday-movies.csv"))

;; We'll make a graph that's similar to the one in [this article about Christmas movies](https://networkdatascience.ceu.edu/article/2019-12-16/christmas-movies), showing the top 20 movies by number of votes. For this we'll use noj, a library that nicely integrates hanami, a Clojure library that wraps vega-lite, with tablecloth.

;; First we can tell it to make a bar chart:

(-> holiday-movies
    ;; sort the movies by number of votes
    (tc/order-by "num_votes" :desc)
    ;; select the first 20
    (tc/select-rows (range 20))
    ;; make a bar chart
    (hanami/plot ht/bar-chart {}))

;; We have to tell hanami what values to use in the chart and what they are:

(-> holiday-movies
    (tc/order-by "num_votes" :desc)
    (tc/select-rows (range 20))
    (hanami/plot ht/bar-chart {:X "num_votes"
                               :Y "primary_title"
                               :YTYPE "nominal"}))

;; This works! We'll give it a few more options to tidy up the chart, like sorting the bars by number of votes rather than alphabetically and re-labelling the axes:

(-> holiday-movies
    (tc/order-by "num_votes" :desc)
    (tc/select-rows (range 20))
    (hanami/plot ht/bar-chart {:X "num_votes"
                               :Y "primary_title"
                               :YTYPE "nominal"
                               :YTITLE "Title"
                               :XTITLE "Number of votes"
                               :YSORT "-x"}))

;; ## Adding labels to the bar chart

;; In order to add the number of votes values next to the bars we'll have to rearrange some of the vega lite spec.. making this tidier and more intuitive is on the list of projects the Clojure data community is actively working on. I get that right now it requires too much information of vega-lite internals. Also it breaks the sorting and I don't know why yet..

(-> holiday-movies
    (tc/order-by "num_votes" :desc)
    (tc/select-rows (range 20))
    (hanami/plot (assoc ht/layer-chart :encoding :ENCODING)
                 {:TITLE "Christmas Movies"
                  :X "num_votes"
                  :Y "primary_title"
                  :YTYPE "nominal"
                  :YTITLE "Title"
                  :XTITLE "Number of votes"
                  :YSORT "-x"
                  :WIDTH 500
                  :XSTACK nil
                  :LAYER [{:mark {:type "bar"}}
                          {:mark {:type "text" :align "left" :baseline "middle" :dx 3}
                           :encoding {:text {:field "num_votes"}}}]}))

;; This is a similar idea to what's accomplished in the article. We don't have exactly the same data, so we can't encode the Oscar status of each movie in the colour, but we could do something else, like indicate the decade in which the film was released.

;; There are lots of ways to accomplish this. There would be a way to tell vega-lite to do it (via Hanami), but for the sake of exploring Clojure some more we'll do the data wrangling part with it. To do that we'll start with our dataset of the 20 most popular movies and add a column for the release decade, then update our plot to have the bars coloured by decade.

(-> holiday-movies
    (tc/order-by "num_votes" :desc)
    (tc/select-rows (range 20))
    ;; add a new column for the decade, computed from the year
    (tc/map-columns "decade" ["year"] (fn [year]
                                        (-> year
                                            (/ 10)
                                            (Math/floor)
                                            (* 10)
                                            int
                                            (str "s"))))
    ;; colour our bars according to the decade
    (hanami/plot ht/bar-chart {:X "num_votes"
                               :Y "primary_title"
                               :YTYPE "nominal"
                               :YTITLE "Title"
                               :XTITLE "Number of votes"
                               :YSORT "-x"
                               :COLOR "decade"}))

;; Without the bar labels, this is reasonably straightforward.

;; ## Making a word cloud

;; The next graph in the article is a graph of relationships between the movie keywords. We don't have the keywords in our dataset, but we could do something with the words of the movie titles, like a word cloud, where the size of the word reflects its occurrence in the movie titles. First we'll collect all of the words in all the titles into a list, then use those as the data for the word cloud. This is a little more involved with low-level vega details than is ideal right now, but it's possible.

(let [data (-> holiday-movies
               (tc/select-columns "primary_title")
               tc/rows
               flatten)]
  (kind/vega {:data
              [{:name "table"
                :values data
                :transform
                [{:type "countpattern"
                  :field "data"
                  :case "upper"
                  :pattern "[\\w']{3,}"
                  :stopwords "(the|a|i'm|like|too|into|ing|for|where|she|he|hers|his|how|who|what|your|yours|it|it's|is|are|we|'til|our|and|but|i'll|this|that|from|with)"
                  }
                 {:type "formula",
                  :as "angle",
                  :expr "[-45, -25, 0, 25, 45][~~(random() * 5)]"}
                 {:type "formula",
                  :as "weight",
                  :expr "if(datum.text=='CHRISTMAS' |
                            datum.text=='HOLIDAY' |
                            datum.text=='HANNUKAH', 600, 300)"}]}],
              :scales
              [{:name "color"
                :type "ordinal",
                :domain {:data "table", :field "text"},
                :range ["red" "green" "grey" "darkred" "darkgreen"]}],
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
                [{:fontSizeRange [10 56],
                  :type "wordcloud",
                  :font "Helvetica Neue, Arial",
                  :size [800 400],
                  :padding 2,
                  :fontWeight {:field "datum.weight"},
                  :rotate {:field "datum.angle"},
                  :fontSize {:field "datum.count"},
                  :text {:field "text"}}]}]}))

;; See you next week :)
