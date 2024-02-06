(ns year-2024.week-4.analysis
  (:require
   [aerial.hanami.common :as hc]
   [aerial.hanami.templates :as ht]
   [clojure.string :as str]
   [scicloj.kindly.v4.kind :as kind]
   [scicloj.noj.v1.vis.hanami :as hanami]
   [tablecloth.api :as tc]))

;; # Week 4 - Educational attainment of young people in English towns

;; This week's data is about English students. We'll try to answer the question from [the example article](https://www.ons.gov.uk/peoplepopulationandcommunity/educationandchildcare/articles/whydochildrenandyoungpeopleinsmallertownsdobetteracademicallythanthoseinlargertowns/2023-07-25), why do children and young people in smaller towns do better academically than those in larger towns?

;; ## Plotting the data as a scatterplot

;; The first graph in the article is a kind of scatterplot segmented by the size of the town. We'll load the data first:

(def english-education
  (-> "data/year_2024/week_4/english_education.csv"
      (tc/dataset {:key-fn keyword})))

english-education

;; We can plot the education scores sorted by town size like in the article to start:

(hanami/plot english-education
             ht/point-chart
             {:X :education_score
              :Y :size_flag
              :YTYPE "nominal"})

;; We can add jitter to this chart to spread out the points along the y axis since they're all bunched up because of how we're sorting them along the y axis:

(hanami/plot english-education
             ;; This is not ideal.. one of my many projects this year is to think up ways
             ;; to improve Clojure's dataviz wrappers
             (-> ht/point-chart
                 (assoc :encoding (assoc (hc/get-default :ENCODING)
                                         :yOffset :YOFFSET)))
             {:X :education_score
              :Y :size_flag
              :HEIGHT {:step 60}
              :TRANSFORM [{:calculate
                           ;; Generate Gaussian jitter with a Box-Muller transform, we could
                           ;; also use `random()`, but that jitters the points uniformly and
                           ;; looks worse
                           "sqrt(-2*log(random()))*cos(2*PI*random())"
                           :as "jitter"}]
              :YTYPE "nominal"
              :YOFFSET {:field "jitter" :type "quantitative"}})

;; This is slightly better, but it's already obvious that we're never going to be able to replicate the graph in the article this way -- there's no way to specify anything precise about how to offset the points along the y axis with the jittering that vega-lite supports. To proceed and try to replicate the graph from the article, we can make a beeswarm plot. For this we'll have to use vega itself (as opposed to vega-lite). Vega-lite is awesome, but it's an intentionally less complete (but as a tradeoff simpler) layer on top of vega. Every once in a while I come across a type of graph that is not well supported by vega-lite and drop into vega. Fortunately there are lots of solutions for rendering vega plots in Clojure notebooks (like [Clay](https://github.com/scicloj/clay), which I'm using to build this book).

;; ## Vega spec for a beeswarm graph

;; ### Getting a tablecloth dataset into vega

;; The first step is getting our data to render in a vega spec through Clojure. For this we can use Daniel Slutsky's amazing library [kindly](https://github.com/scicloj/kindly). It tells our namespace in a notebook-agnostic way how to render different things. If we pass a valid vega spec to `kind/vega`, our notebook will render it properly as a graph. So cool.
;;
;; So first, to just get any data rendering in our graph, we'll read (or in Clojure, `slurp`) our data from the relevant file. We have to specify the format since `csv` is not the default. For now we'll just make a simple scatterplot to get the data on the page.

(kind/vega
  ;; every dataset in vega needs to be named, this is how we reference the
  ;; data in the rest of the spec
 {:data [{:name "english-education"
          :values (slurp "data/year_2024/week_4/english_education.csv")
          :format {:type "csv"}}]
  :width 500
  :height 500
  ;; the minimal vega spec includes scales, marks, and axes
  ;; scales map data values to visual values, defining the nature of visual encodings
  :scales [{:name "yscale"
            :type "band"
            :domain {:data "english-education" :field "size_flag"}
            :range "height"}
           {:name "xscale"
            :domain [-12 12]
            :range "width"}]
  ;; axes label and provide context for the scales
  :axes [{:orient "bottom" :scale "xscale"}
         {:orient "left" :scale "yscale"}]
  ;; marks map visual values to shapes on the screen
  :marks [{:type "symbol"
           :from {:data "english-education"}
           :encode {:enter {:y {:field "size_flag"
                                :scale "yscale"}
                            :x {:field "education_score"
                                :scale "xscale"}}}}]})

;; This is the minimal reproduction of the vega-lite scatterplot we made above. This is where we'll dive deeper into vega to do some things vega-lite can't do. In order to turn this into a beeswarm plot, we can add what vega calls [forces](https://vega.github.io/vega/docs/transforms/force/).

;; ### Turning a scatterplot into a beeswarm graph in vega

;; Force transforms compute force-directed layouts, so we can use one to compute the placement of each point in our graph such that they cluster together based on their y value but don't overlap. To compute a beeswarm layout we'll tell vega to treat each point like it's attracted to other ones that share its y value but not to collide with them.

(kind/vega
 {:data [{:name "english-education"
          :values (slurp "data/year_2024/week_4/english_education.csv")
          :format {:type "csv"}}]
  :width 800
  :height 800
  :scales [{:name "yscale"
            :type "band"
            :domain {:data "english-education" :field "size_flag"}
            :range "height"}
           {:name "xscale"
            :domain [-12 12]
            :range "width"}]
  :axes [{:orient "bottom" :scale "xscale"}
         {:orient "left" :scale "yscale"}]
  :marks [{:type "symbol"
           :from {:data "english-education"}
           :encode {:enter {;; we update the mark encoding to use the x and y values
                            ;; computed by the force transform
                            :yfocus {:field "size_flag"
                                     :scale "yscale"
                                     :band 0.5}
                            :xfocus {:field "education_score"
                                     :scale "xscale"
                                     :band 0.5}}}
           ;; this is the new part -- a simulated force attracting the points to each other
           :transform [{:type "force"
                        :static true
                        :forces [{:force "x" :x "xfocus"}
                                 {:force "y" :y "yfocus"}
                                 {:force "collide" :radius 4}]}]}]})

;; This is pretty close to the graph we're going for! We can add some more aesthetic details to make it look neater, like changing the definition of the points and adding a grid along the x axis:

(kind/vega
 {:data [{:name "english-education"
          :values (slurp "data/year_2024/week_4/english_education.csv")
          :format {:type "csv"}}]
  :width 800
  :height 800
  :scales [{:name "yscale"
            :type "band"
            :domain {:data "english-education" :field "size_flag"}
            :range "height"}
           {:name "xscale"
            :domain [-12 12]
            :range "width"}]
  :axes [{:orient "bottom" :scale "xscale" :grid true}
         {:orient "left" :scale "yscale"}]
  :marks [{:type "symbol"
           :from {:data "english-education"}
           :encode {:enter {:yfocus {:field "size_flag"
                                     :scale "yscale"
                                     :band 0.5}
                            :xfocus {:field "education_score"
                                     :scale "xscale"
                                     :band 0.5}
                            :fill {:value "skyblue"}
                            :stroke {:value "white"}
                            :strokeWidth {:value 1}
                            :zindex {:value 0}}}
           :transform [{:type "force"
                        :static true
                        :forces [{:force "x" :x "xfocus"}
                                 {:force "y" :y "yfocus"}
                                 {:force "collide" :radius 4}]}]}]})

;; Woohoo. Making progress. Next we can add the average lines. We'll transform the data we already have into a new grouped dataset with the average computed per `size_flag`, and add another mark layer on top to show these lines:

(kind/vega
 {:data [{:name "english-education"
          :values (slurp "data/year_2024/week_4/english_education.csv")
          :format {:type "csv"}}
         ;; this is the new dataset, computed from the existing one
         {:name "averages"
          :source "english-education"
          :transform [{:type "aggregate",
                       :fields ["education_score"],
                       :groupby ["size_flag"],
                       :ops ["average"],
                       :as ["avg"]}]}]
  :width 800
  :height 800
  :scales [{:name "yscale"
            :type "band"
            :domain {:data "english-education" :field "size_flag"}
            :range "height"}
           {:name "xscale"
            :domain [-12 12]
            :range "width"}]
  :axes [{:orient "bottom" :scale "xscale" :grid true :title "Education score"}
         {:orient "left" :scale "yscale" :title "Town size"}]
  :marks [{:type "symbol"
           :from {:data "english-education"}
           :encode {:enter {:yfocus {:field "size_flag"
                                     :scale "yscale"
                                     :band 0.5}
                            :xfocus {:field "education_score"
                                     :scale "xscale"
                                     :band 0.5}
                            :fill {:value "skyblue"}
                            :stroke {:value "white"}
                            :strokeWidth {:value 1}
                            :zindex {:value 0}}}
           :transform [{:type "force"
                        :static true
                        :forces [{:force "x" :x "xfocus"}
                                 {:force "y" :y "yfocus"}
                                 {:force "collide" :radius 4}]}]}
          ;; this is the new layer with the lines -- it's second so that the lines show up on top
          {:type "symbol"
           ;; we tell it to use the data from our new, computed dataset
           :from {:data "averages"}
           :encode {:enter {:x {:field "avg" :scale "xscale"}
                            :y {:field "size_flag" :scale "yscale" :band 0.5}
                            :shape {:value "stroke"}
                            :strokeWidth {:value 1.5}
                            :stroke {:value "black"}
                            :size {:value 14000}
                            :angle {:value 90}}}}]})

;; We can add the town selection dropdown like in the example graph using vega [signals](https://vega.github.io/vega/docs/signals/).

(let [town-name-values (-> english-education
                           (tc/select-columns [:town11nm])
                           tc/rows
                           flatten
                           sort
                           (conj ""))]
  (kind/vega
   {:data [{:name "english-education"
            :values (slurp "data/year_2024/week_4/english_education.csv")
            :format {:type "csv"}}
           {:name "averages"
            :source "english-education"
            :transform [{:type "aggregate",
                         :fields ["education_score"],
                         :groupby ["size_flag"],
                         :ops ["average"],
                         :as ["avg"]}]}]
    :width 800
    :height 800
    :scales [{:name "yscale"
              :type "band"
              :domain {:data "english-education" :field "size_flag"}
              :range "height"}
             {:name "xscale"
              :domain [-12 12]
              :range "width"}]
    :axes [{:orient "bottom" :scale "xscale" :grid true :title "Education score"}
           {:orient "left" :scale "yscale" :title "Town size"}]
    :signals [{:name "highlight"
               :value nil
               :bind {:input :select
                      :options town-name-values
                      :labels (map #(str/replace % #" BUAS?D?" "") town-name-values)}}]
    :marks [{:type "symbol"
             :from {:data "english-education"}
             :encode {:enter {:yfocus {:field "size_flag"
                                       :scale "yscale"
                                       :band 0.5}
                              :xfocus {:field "education_score"
                                       :scale "xscale"
                                       :band 0.5}
                              :fill {:value "skyblue"}
                              :stroke {:value "white"}
                              :strokeWidth {:value 1}
                              :zindex {:value 0}}
                      :update {:fill {:signal "datum['town11nm'] === highlight ? 'orange' : 'skyblue'"}
                               :stroke {:signal "datum['town11nm'] === highlight ? 'purple' : 'white'"}
                               :strokeWidth {:signal "datum['town11nm'] === highlight ? 2 : 1"}
                               :zindex {:signal "datum['town11nm'] === highlight ? 1 : 0"}}}
             :transform [{:type "force"
                          :static true
                          :forces [{:force "x" :x "xfocus"}
                                   {:force "y" :y "yfocus"}
                                   {:force "collide" :radius 4}]}]}
            {:type "symbol"
             :from {:data "averages"}
             :encode {:enter {:x {:field "avg" :scale "xscale"}
                              :y {:field "size_flag" :scale "yscale" :band 0.5}
                              :shape {:value "stroke"}
                              :strokeWidth {:value 1.5}
                              :stroke {:value "black"}
                              :size {:value 14000}
                              :angle {:value 90}}}}]}))

;; The select dropdown isn't super nice or in a great spot. The way to style and position it better is with CSS, but I'm going to call that out of scope for this exercise. In theory, this dropdown would be above the chart and look much nicer. This is great, though. We have more or less re-created the main graphic from the article. We can see that smaller towns seem to have higher educational attainment on average, and check the value for any given town by selecting its value from a dropdown.
;;
;; One of the many projects I'd like to tackle is making a wrapper for vega to make it intuitive to use from Clojure. Anyway, moving on to the next graph in the article.

;; ## Income deprivation and town size

;; ### Normalized, stacked bar chart

;; The next one is a simple normalized stacked bar chart exploring the relationship between town size and income deprivation. For this one we can drop back into vega-lite (and hanami):

(-> english-education
    (hanami/plot ht/bar-chart
                 {:XAGG "sum"
                  :XSTACK "normalize"
                  :XTITLE "Count"
                  :X :education_score
                  :YTYPE "nominal"
                  :Y :size_flag
                  :YTITLE "Town size"
                  :COLOR "income_flag"
                  :CTYPE "nominal"}))

;; The data from the "BUA"s and London is making this look less than ideal, so we'll filter for only the categories of towns that are included in the article's graph:

(-> english-education
    (tc/select-rows #(#{"Small Towns" "Medium Towns" "Large Towns" "City"}
                       (:size_flag %)))
    (hanami/plot ht/bar-chart
                 {:XAGG "sum"
                  :XSTACK "normalize"
                  :XTITLE "Count"
                  :X :education_score
                  :YTYPE "nominal"
                  :Y :size_flag
                  :YTITLE "Town size"
                  :COLOR "income_flag"
                  :CTYPE "nominal"}))

;; This seems to show that the incidence of income deprivation is higher the larger the town size. We can visualize this relationship in another way by making a scatterplot, like in the article.

;; ### Scatterplot

;; For this graph we'll use the dataset provided in the article (since it contains income deprivation scores, not just classifications).

(-> "data/year_2024/week_4/education-and-income-scores.csv"
    tc/dataset
    (hanami/plot ht/point-chart
                 {:X "Educational attainment score"
                  :Y "Income deprivation score"
                  :YSCALE {:zero false}}))

;; This reveals that income deprivation seems to be worse in larger towns, but this isn't the whole story. The article also investigates regional differences. To see the effect of region on education scores, we can plot scores vs region and encode the town size in the color of the points. We'll go back to our original dataset for this. The `rgn11nm` column contains the region we're looking for.

;; ### Education score by region

(-> english-education
    (hanami/plot ht/point-chart
                 {:X :education_score
                  :Y :rgn11nm
                  :YTYPE "nominal"
                  :COLOR "income_flag"
                  :CTYPE "nominal"}))

;; This is pretty close, but we can take the average of all scores in a given region to reduce some of the noise, and make the points larger to make it easier to see them.

(-> english-education
    (hanami/plot ht/point-chart
                 {:X :education_score
                  :XAGG "mean"
                  :Y :rgn11nm
                  :YTYPE "nominal"
                  :MSIZE 100
                  :COLOR "income_flag"
                  :CTYPE "nominal"}))

;; This reveals an interesting relationship between region and education scores. The North West has the highest education scores at all levels of income deprivation.

;; ### Effect of being near the coast

;; The next graph is an interesting one showing that coastal towns have worse outcomes than non-coastal towns. The graph is similar to the previous one, but in order to get the data into a sensible shape to visualize I'm going to wrangle it a bit ahead of throwing it into our viz function. We have the `coastal_detailed` column that gives us, well, details about the coastal-ness of each town. Inspecting the distinct values in this column shows us it's a bit of a mess, though.

(-> english-education :coastal_detailed vec distinct)

;; This one column contains strings that describe a town as either "smaller" or "large", and "non-coastal", "seaside", or "other coastal". The first thing we'll do is tidy up this data. Each variable should be in its own column, plus we'll delete all the rows that don't have a value for `coastal_detailed`. Then we'll pass it to our viz function, taking the average of the education score for each category we're plotting, just like above.

(-> english-education
    (tc/select-columns [:education_score :coastal_detailed])
    (tc/drop-missing :coastal_detailed)
    (tc/map-columns :town_size [:coastal_detailed]
                    (fn [v]
                      (when v
                        (if (str/includes? v "Smaller") "Small" "Large"))))
    (tc/map-columns :coastal_type [:coastal_detailed]
                    (fn [v]
                      (when v
                        (cond
                          (str/includes? v "non-coastal") "Non-coastal"
                          (str/includes? v "seaside") "Seaside"
                          :else "Other coastal"))))
    (hanami/plot ht/point-chart
                 {:X :education_score
                  :XAGG "mean"
                  :Y :town_size
                  :YTYPE "nominal"
                  :COLOR "coastal_type"
                  :CTYPE "nominal"
                  :MSIZE 100
                  :HEIGHT 100}))

;; This reveals in interesting relationship between the proximity of a town to the coast and education scores. For whatever reason, inland towns have better educational attainment.

;; ### Widening educational attainment gap over time

;; The article also observes that the gap in educational attainment between students from high vs low income deprivation areas widens over time. We can see this in the data by finding the average "key stage 2" (end of primary school in the UK) attainment in a given income deprivation category and comparing it to two later measures of educational attainment (key stage 4 and level 3).
;;
;; I'll define a function for computing the average of a sequence of numbers, for the sake of clarifying the data transformation.

(defn- average [vals]
  (/ (reduce + vals) (count vals)))

;; We'll also filter out rows that have "Cities" and `nil` as their `income_flag` because these aren't comparable to the other income flag values.

(-> english-education
    (tc/drop-missing :income_flag)
    (tc/drop-rows #(= "Cities" (:income_flag %)))
    (tc/group-by [:income_flag])
    (tc/aggregate-columns [:key_stage_2_attainment_school_year_2007_to_2008
                           :key_stage_4_attainment_school_year_2012_to_2013
                           :level_3_at_age_18]
                          average))

;; We'll calculate the gap in attainment between the different deprivation categories. I'm going to rename the columns first to make them more succinct. Then we'll add new columns that show the gap between each stage for a given income deprivation level (compared to the lowest deprivation category).

(let [ds (-> english-education
             (tc/drop-missing :income_flag)
             (tc/drop-rows #(= "Cities" (:income_flag %)))
             (tc/group-by [:income_flag])
             (tc/aggregate-columns [:key_stage_2_attainment_school_year_2007_to_2008
                                    :key_stage_4_attainment_school_year_2012_to_2013
                                    :level_3_at_age_18]
                                   average)
             (tc/rename-columns {:key_stage_2_attainment_school_year_2007_to_2008 :key_stage_2
                                 :key_stage_4_attainment_school_year_2012_to_2013 :key_stage_4
                                 :level_3_at_age_18 :level_3}))
      lowest-deprivation-vals (tc/select-rows ds #(str/includes? (:income_flag %) "Lower"))]
  (-> ds
      (tc/map-columns :key_stage_2_gap [:key_stage_2]
                      #(- % (first (:key_stage_2 lowest-deprivation-vals))))
      (tc/map-columns :key_stage_4_gap [:key_stage_4]
                      #(- % (first (:key_stage_4 lowest-deprivation-vals))))
      (tc/map-columns :level_3_gap [:level_3]
                      #(- % (first (:level_3 lowest-deprivation-vals))))))

;; This data is already pretty easy to interpret just as a table. For the sake of the exercise, we can plot it in a similar way to the article. To do this we'll tidy up the data in a different way, in all senses of the word. Right now this data is structured in a way that makes it easy to interpret when it's printed as a table, but it's hard to plot because some of the column names encode a variable in our dataset across multiple columns (the education attainment stage). To fix this, we'll use tablecloth's `pivot->longer`, so that each row represents one observation and each column represents a single variable. Then we can plot it.

(let [ds (-> english-education
             (tc/drop-missing :income_flag)
             (tc/drop-rows #(= "Cities" (:income_flag %)))
             (tc/group-by [:income_flag])
             (tc/aggregate-columns [:key_stage_2_attainment_school_year_2007_to_2008
                                    :key_stage_4_attainment_school_year_2012_to_2013
                                    :level_3_at_age_18]
                                   average)
             (tc/rename-columns {:key_stage_2_attainment_school_year_2007_to_2008 :key_stage_2
                                 :key_stage_4_attainment_school_year_2012_to_2013 :key_stage_4
                                 :level_3_at_age_18 :level_3}))
      lowest-deprivation-vals (tc/select-rows ds #(str/includes? (:income_flag %) "Lower"))]
  (-> ds
      (tc/map-columns :key_stage_2_gap [:key_stage_2]
                      #(- % (first (:key_stage_2 lowest-deprivation-vals))))
      (tc/map-columns :key_stage_4_gap [:key_stage_4]
                      #(- % (first (:key_stage_4 lowest-deprivation-vals))))
      (tc/map-columns :level_3_gap [:level_3]
                      #(- % (first (:level_3 lowest-deprivation-vals))))
      (tc/select-columns [:income_flag :key_stage_2_gap :key_stage_4_gap :level_3_gap])
      (tc/pivot->longer [:key_stage_2_gap :key_stage_4_gap :level_3_gap]
                        {:target-columns :gap
                         :value-column-name :value})
      (hanami/plot ht/point-chart
                   {:X :gap
                    :XTYPE "nominal"
                    :Y :value
                    :COLOR "income_flag"
                    :CTYPE "nominal"
                    :MSIZE 100})))

;; This could be tidied up a bit with nicer names and colours, but this is the idea. I think the main takeaway from this  graph is that data should be tidy for the sake of visualizing it, and that naming columns is important.

;; ## Pursuit of higher education

;; The next question we can answer with this data is how many students from different income deprivation areas pursue higher education. This chart looks complex but it's just a simple bar chart faceted by educational attainment milestones. We have the information to make this chart, but again it's not organized in a nice way for visualizing.
;;
;; The data we care about for this chart are the town size flag, and the three educational attainment values in the columns `level_3_at_age_18`, `activity_at_age_19:_full-time_higher_education`, and `activity_at_age_19:_appprenticeships`. We'll pivot our data again in a different way this time to organize the data for this visualization, then aggregate the values to calculate the average value for each town size/attainment level pair. As an added bonus, the data in the `activity...` columns are strings, not numbers, so we'll coerce those to numbers too so that we can do math on them (like calculating the average).

(-> english-education
    (tc/select-columns [:size_flag
                        :level_3_at_age_18
                        :activity_at_age_19:_full-time_higher_education
                        :activity_at_age_19:_sustained_further_education])
    (tc/rename-columns {:level_3_at_age_18 "Level 3 qualifications at age 18"
                        :activity_at_age_19:_full-time_higher_education "In full-time higher education at age 19"
                        :activity_at_age_19:_sustained_further_education  "In further education at age 19"})
    (tc/pivot->longer ["Level 3 qualifications at age 18"
                       "In full-time higher education at age 19"
                       "In further education at age 19"]
                      {:target-columns :attainment_measure
                       :value-column-name :value})
    ;; coerce all the values to be numbers
    (tc/update-columns :value (partial map #(if (string? %) (parse-double %) %)))
    ;; lose the nil ones, it messes up our calculation
    (tc/drop-missing :value)
    ;; calculate the average value for each size flag/attainment measure pair
    (tc/group-by [:size_flag :attainment_measure])
    (tc/aggregate-columns :value average)
    ;; plot this
    (hanami/plot {:facet :FACET
                  :spec {:encoding :ENCODING
                         :layer :LAYER
                         :width :WIDTH}
                  :data {:values :DATA
                         :format :DFMT}}
                 {:Y :size_flag
                  :YTITLE "Town size"
                  :YTYPE "nominal"
                  :YSORT ["Small towns"
                          "Medium towns"
                          "Large towns"
                          "Cities"
                          "Outer London"
                          "Inner London"]
                  :X :value
                  :XTITLE "Percentage of population"
                  :FACET {:column {:field :attainment_measure
                                   :type "nominal"
                                   :title "Attainment measure"
                                   :sort ["Level 3 qualifications at age 18"
                                          "In full-time higher education at age 19"
                                          "In further education at age 19"]}}
                  :LAYER [{:mark "bar"}
                          {:mark {:type "text" :dx -3 :color "white" :align "right"}
                           :encoding {:text {:field "value" :format ".1f"}}}]
                  :WIDTH 140}))

;; ## Connection to other town residents

;; The last graph in the article is one showing the relationship between the educational attainment of older and younger residents. We can see that education scores seem to be correlated with the incidence of high educational attainment among older residents of a town. We don't have exactly the right data to reproduce this graph, but we can do something similar with the data we do have.
;;
;; We can plot the educational attainment values vs. the educational attainment classification of residents aged 35-64 and add some jitter to spread out the dots, which will reveal the same general relationship as the more precise data -- students in towns with more highly educated older generations tend to have higher educational attainment. It's still worth pointing out that there is a huge overlap between the highest low-education town educational attainment scores and the lowest high-education town scores, so there are obviously many other factors at play. But it's still an interesting observation.

(-> english-education
    (tc/drop-missing :level4qual_residents35-64_2011)
    (hanami/plot {:encoding (assoc (hc/get-default :ENCODING)
                                   :yOffset :YOFFSET)
                  :mark {:type "circle"}
                  :transform :TRANSFORM
                  :height 300
                  :width 500
                  :data {:values :DATA
                         :format :DFMT}}
                 {:X :education_score
                  :Y :level4qual_residents35-64_2011
                  :YTYPE "nominal"
                  :YSORT ["High" "Medium" "Low"]
                  :YOFFSET {:field  "jitter" :type "quantitative"}
                  :TRANSFORM [{:calculate "sqrt(-2*log(random()))*cos(2*PI*random())"
                               :as "jitter"}]}))

;; That sums up our work this week. There are so many graphs in this one, it was a lot of fun to play around and see some common patterns emerging. I'm looking forward to many projects revolving around tidying up the dataviz story in Clojure.
;;
;; See you next week :)
