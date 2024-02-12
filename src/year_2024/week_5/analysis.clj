(ns year-2024.week-5.analysis
  (:require
   [aerial.hanami.templates :as ht]
   [scicloj.noj.v1.vis.hanami :as hanami]
   [tablecloth.api :as tc]
   [tech.v3.dataset.rolling :as rolling]
   [tech.v3.dataset.column :as tmdcol]))

;; # Week 5 - Groundhog Predictions

;; This week's dataset is about groundhog day. If you haven't heard of groundhog day, it's a tradition observed around North America every year on February 2. The story is that Pennsylvania Dutch immigrants had a superstition that if a groundhog sees its shadow on February 2 winter will last for six more weeks, and if it doesn't spring will come early.
;;
;; The website [groundhog-day.com](https://groundhog-day.com) has a great API that provides all the info you could ever want about groundhogs around the continent and their predictions about the length of winter. We'll be exploring that data to see if we can come up with a way to visualize it to convey the information in a more interesting way than just showing it in a table.

;; ## Increasing popularity of Groundhog Day

;; One thing I quickly noticed when I started poking around the data is that for most of history there were very few groundhogs making predictions. We can visualize this increasing popularity of groundhog day in lots of ways. My first thought was to just make a simple line chart showing the numbers of new groundhogs reporting every year.
;;
;; First we load the data, making keywords keys as always.

(def groundhogs
  (-> "data/year_2024/week_5/groundhogs.csv"
      (tc/dataset {:key-fn keyword})))

;; Then we have to wrangle the data a little bit to add each groundhog's debut year to our dataset. We can do this by finding the earliest prediction for each groundhog, and joining this year to our groundhogs dataset.

(def predictions
  (-> "data/year_2024/week_5/predictions.csv"
      (tc/dataset {:key-fn keyword})))

(def groundhogs-with-debut-year
  (-> predictions
      (tc/group-by [:id])
      ;; find the earliest year for each unique ID
      (tc/aggregate {:first-year #(apply min (:year %))})
      ;; add these to the groundhogs dataset
      (tc/inner-join groundhogs :id)))

;; Now we have a dataset listing all the groundhogs that report on the weather that includes their first year in the business. We can count the number of new groundhogs reporting every year and plot this as a line:

(-> groundhogs-with-debut-year
    (tc/group-by [:first-year])
    (tc/aggregate {:new-groundhogs tc/row-count})
    (hanami/plot ht/line-chart
                 {:X :first-year
                  :XTITLE "Year"
                  :XTYPE :temporal
                  :Y :new-groundhogs
                  :YTITLE "Number of groundhogs debuting in this year"}))

;; This doesn't really tell us much. It might be more easy to interpret as a cumulative area chart -- with the area showing the cumulative count of groundhogs. One way to do this is directly in vega-lite, with a transform:

(-> groundhogs-with-debut-year
    (tc/group-by [:first-year])
    (tc/aggregate {:new-groundhogs tc/row-count})
    (hanami/plot ht/area-chart
                 {:X :first-year
                  :XTITLE "Year"
                  :XTYPE :temporal
                  :Y :cumulative-count
                  :YTITLE "Cumulative count of groundhogs"
                  :TRANSFORM [{:sort [{:field :first-year}]
                               :window [{:as :cumulative-count
                                         :field :new-groundhogs
                                         :op "sum"}]}]}))

;; But in some cases, for example if the dataset were way bigger or if there were more complicated calculations to do than just summing up a field, we might want to manipulate the data on the JVM side and just send the points to plot to the browser. This is a good chance to demonstrate how to work with rolling windows in tablecloth too, so for the sake of demonstration, that would look like this:

(-> groundhogs-with-debut-year
    (tc/group-by [:first-year])
    (tc/aggregate {:new-groundhogs tc/row-count})
    (rolling/expanding {:cumulative-count (rolling/sum :new-groundhogs)})
    ;; now we have our years and cumulative count of groundhogs computed
    (hanami/plot ht/area-chart
                 {:X :first-year
                  :XTYPE :temporal
                  :Y :cumulative-count}))

;; That's a bit better. It's obvious from this that groundhog day really took off in the 2010s. We could show this more clearly by plotting the number of new groundhogs per decade:

(-> groundhogs-with-debut-year
    ;; add a new column with the decade of each groundhog's first year
    (tc/map-columns :decade [:first-year] #(-> %
                                               (/ 10)
                                               (Math/floor)
                                               (* 10)
                                               int))
    ;; count how many new groundhogs there were per decade
    (tc/group-by [:decade])
    (tc/aggregate {:count tc/row-count})
    ;; make a simple bar chart with this data
    (hanami/plot ht/bar-chart
                 {:X :decade
                  :XTYPE :temporal
                  :MSIZE 15
                  :Y :count}))

;; Over 30 places got groundhogs reporting on the weather in the 2010s. I wonder what happened then? I'm also curious about where these groundhogs are from.

;; ## Geographic distribution of groundhogs

;; To see this we can show each groundhog's location on a map. They're all in the USA or Canada, so we can project these onto a map of those two countries. Vega-lite supports simple projections onto maps, so we'll try our luck with it. We'll have to write a custom hanami template, though, but that's no problem.

(-> groundhogs-with-debut-year
    (tc/map-columns :decade [:first-year] #(-> %
                                               (/ 10)
                                               (Math/floor)
                                               (* 10)
                                               int))
    (tc/select-columns [:id :decade :latitude :longitude])
    (hanami/plot {:layer [{:data {:format {:type "topojson" :feature :default} :url :TOPOURL}
                           :mark {:fill "lightgray" :type "geoshape"}
                           :projection :PROJECTION}
                          {:data {:values :DATA :format :DFMT}
                           :encoding {:latitude {:field :LAT :type "quantitative"}
                                      :longitude {:field :LONG :type "quantitative"}
                                      :color :COLOR}
                           :projection :PROJECTION
                           :mark {:type "circle" :size :MSIZE}}]
                  :height :HEIGHT
                  :width :WIDTH}
                 {:HEIGHT 1000
                  :WIDTH 900
                  :TOPOURL "https://code.highcharts.com/mapdata/custom/usa-and-canada.topo.json"
                  :PROJECTION {:type "conicConformal"
                               :parallels [45 33]
                               :rotate [105]}
                  :LAT "latitude"
                  :LONG "longitude"
                  :MSIZE 80
                  :COLOR {:field "decade"
                          :scale {:scheme "category10"}}}))

;; This shows us that Groundhog Day is much more widely observed in the north-east than anywhere else. I also can't help but notice three dots in the province where I live, so I have to find out who my local groundhogs are.

(-> groundhogs-with-debut-year
    (tc/select-rows #(= "Canada" (:country %)))
    tc/row-count)

;; There are 14 groundhogs in total in Canada. We can see just the ones in Nova Scotia:

(-> groundhogs-with-debut-year
    (tc/select-rows #(= "Nova Scotia" (:region %))))

;; It turns out the one closest to me is actually a lobster! Which is adorable. Lobster fishing is a huge industry in my area, so this is a great gimmick. Anyway, moving on.
;;
;; I think it would be interesting to explore the predictions a little bit too.

;; ## Groundhog predictions and geographic location

;; I'm curious whether groundhogs (or other creatures) that live further north are more likely to predict more winter. It definitely feels like winter is never going to end up here most years, especially in February.
;;
;; To explore this, we can compute the proportion of "continued winter" predictions per groundhog, and plot that against latitude.

(-> predictions
    (tc/group-by [:id])
    (tc/aggregate {:winter-percent #(double (/ (->> % :shadow (remove false?) count)
                                               (tc/row-count %)))})
    (tc/inner-join groundhogs [:id])
    (tc/select-columns [:id :winter-percent :latitude])
    (hanami/plot ht/point-chart
                 {:X :latitude
                  :XSCALE {:zero false}
                  :Y :winter-percent}))

;; It doesn't really look like there's any relationship here. We can quantify it by computing the correlation coefficient between these two values. This is a simple calculation that's built right in to `tech.ml.dataset`, the library underlying tablecloth, so we can pass our columns right into it. We also have to tell it which type of correlation to compute (`:pearson`, `:spearman`, and `:kendall` are supported).

(let [ds (-> predictions
             (tc/group-by [:id])
             (tc/aggregate {:winter-percent #(double (/ (->> % :shadow (remove false?) count)
                                                        (tc/row-count %)))})
             (tc/inner-join groundhogs [:id]))]
  (tmdcol/correlation (:winter-percent ds) (:latitude ds) :pearson))

;; This is effectively no correlation, so interestingly enough groundhogs living further north do not disproportionately predict longer winters.

;; ## Winter vs. spring predictions

;; The last thing we can look at is the proportion of groundhogs that predict winter vs spring each year. We'll select only years since 1980, when groundhog day started to take off, and filter out rows that have no prediction

(-> predictions
    (tc/select-rows #(< 1980 (:year %)))
    (tc/select-rows #(not (nil? (:shadow %))))
    (hanami/plot ht/bar-chart
                 {:X :year
                  :XTYPE :temporal
                  :YAGG "count"
                  :YSTACK "normalize"
                  :YTITLE "Percentage of predictions"
                  :YGRID false
                  :COLOR {:field "shadow" :scale {:range ["gold" "lightblue"]}
                          :legend {:title "Prediction"
                                   :labelExpr "{'true': 'Extended winter', 'false': 'Early spring'}[datum.label]"}}
                  :WIDTH 700
                  :MSIZE 13
                  :BACKGROUND "white"}))

;; So there we have it, some visualizations exploring the groundhog day data. On to next week. See you then :)
