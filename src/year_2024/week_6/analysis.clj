(ns year-2024.week-6.analysis
  (:require
   [aerial.hanami.templates :as ht]
   [scicloj.noj.v1.vis.hanami :as hanami]
   [tablecloth.api :as tc]))

;; # Week 6 - A Few World Heritage Sites

;; The data this week is a tiny subset of the data on UNESCO World Heritage Sites. The inspiration comes from the [1 dataset, 100 visualizations project](https://100.datavizproject.com), which used this tiny dataset to demonstrate tons of different ways to visualize the same data.
;;
;; I'm not going to do 100 different visualizations, but this is an interesting opportunity to explore some different types of datavizes. We'll do a few basic ones, and then look into a few more unique ones to explores the boundaries of what's possible today with our current Clojure data science toolkit.

;; ## Bar charts and variations

;; First we'll load our data. It isn't downloaded this time, we'll just construct it manually. There are lots of ways to pass a "dataset literal" to tablecloth, one of the more succinct ways is as a map with keys as column names and values as column values.

(def heritage-sites
  (-> {:country ["Norway" "Denmark" "Sweden"]
       :2004 [5 4 13]
       :2022 [8 10 15]}
      tc/dataset))

;; This data isn't "tidy", so first we'll clean it up to make it so. This means we'll structure our data such that each column contains only one variable, each row contains one observation, and each cell contains a single value. In this tiny dataset our variables (things that measure a given attribute/property of each item) are "year" and "world heritage site count". The items or units we're measuring are the countries, and so each cell should contain a single value for a given country/year combination. Then we can make a simple stacked bar chart.

(def tidy-heritage-sites
  ;; tidy up the data, pivoting it longer so we have one value per cell
  (-> heritage-sites
      (tc/pivot->longer [:2004 :2022] {:target-columns :year
                                       :value-column-name :count
                                       :splitter #":(\d{4})"
                                       :datatypes {:year :int16}})))

(-> tidy-heritage-sites
    ;; make a simple stacked bar chart
    (hanami/plot ht/bar-chart
                 {:X :year
                  :XTYPE :ordinal
                  :XAXIS {:labelAngle -45 :title "Year"}
                  :Y :count
                  :YTITLE "World Heritage Sites"
                  :COLOR {:field :country :title "Country"}}))

;; This works, but it doesn't tell a super clear story right away. Stacking different sized rectangles on top of each other is not an ideal way to convey which ones are important and which aren't. A grouped bar chart might be more intuitive. We can also add labels by adding a text layer.

(-> tidy-heritage-sites
    (hanami/plot (assoc ht/layer-chart :encoding :ENCODING)
                 {:X :year
                  :XTYPE :ordinal
                  :Y :count
                  :LAYER [{:mark {:type "bar"}
                           :encoding {:color {:field "country"}}}
                          {:mark {:type "text" :dy 140}
                           :encoding {:text {:field "count" :type "quantitative"}
                                      :y {:scale {:zero false}}}}]})
    (assoc-in [:encoding :xOffset] {:field "country"}))

;; This is slightly better, we can more easily compare the countries now, but this still doesn't draw our attention to the most noteworthy thing about the data. Grouping the data by country instead of year tells a different story.

(-> tidy-heritage-sites
    (hanami/plot (assoc ht/layer-chart :encoding :ENCODING)
                 {:X :country
                  :XTYPE :ordinal
                  :Y :count
                  :LAYER [{:mark {:type "bar"}
                           :encoding {:color {:field "year"}}}
                          {:mark {:type "text" :dy 140}
                           :encoding {:text {:field "count" :type "quantitative"}
                                      :y {:scale {:zero false}}}}]})
    (assoc-in [:encoding :xOffset] {:field "year"}))

;; This makes it immediately obvious that Denmark saw the biggest increase in its number of world heritage sites between 2004 and 2012. Another way to organize the data is to stack the years. Adding the labels and getting the years in the right order is a little bit cumbersome, but possible:

(-> tidy-heritage-sites
    (hanami/plot ht/layer-chart
                 {:TRANSFORM [{:calculate "indexof(['2004', '2022'], datum.year)" :as "order"}]
                  :LAYER [{:mark {:type "bar"}
                           :encoding {:x {:field :country :type :ordinal}
                                      :y {:field :count :type :quantitative}
                                      :color {:field :year}
                                      :order {:field :order}}}
                          {:mark {:type "text" :dy 14}
                           :encoding {:text {:field :year :type :quantitative}
                                      :x {:field :country :type "nominal"}
                                      :y {:field :count :type :quantitative :stack "zero"}
                                      :order {:field :order}}}]}))

;; All this is definitely making me reflect on what's missing from Clojure's dataviz story. For one, it would be challenging to create many of the other types of charts from the example website. Even making these various bar charts is a pretty cumbersome and verbose. There's definitely space for a more succinct, grammar-based viz library that doesn't conflate aesthetic concerns with data concerns so much. For example, we shouldn't have to care at all about the underlying implementation of a viz library to sort two marks on the screen, but here we have to dig into the internals of vega lite to manually compute a new column in the underlying dataset that gets passed to the renderer in order to tell it how to sort them. Ideally that would be abstracted away behind a simple API.
;;
;; Anyway, obviously vega-lite is an amazing library, and it's fun bumping up against its edges often enough to get some ideas about what would be interesting to change or improve -- although also dangerous! I'm not sure I need any more projects right now 😄
;;
;; I think in the interest of time I'll wrap up this week's explorations here to give myself some extra bandwidth to dive more into the world of data viz and graphics libraries. Wish me luck, and see you next week :)
