(ns year-2024.week-3.analysis
  (:require
   [aerial.hanami.common :as hc]
   [aerial.hanami.templates :as ht]
   [scicloj.noj.v1.vis.hanami :as hanami]
   [tablecloth.api :as tc]))

;; # Week 3 - US Polling Places 2012-2020

;; This week's data is about US polling places from 2012-2020. It's a super interesting dataset, assembled by [The Center for Public Integrity and Stateline](https://publicintegrity.org/politics/elections/ballotboxbarriers/data-release-sheds-light-on-past-polling-place-changes/). The example article for the week has a really cool dataviz that uses some voter demographic data overlaid on the polling place data. For us without the extra demographic data, we can at least just poke around the polling places dataset to answer some basic questions. Like the one proposed in the tidy Tuesday repo this week: For states with data for multiple elections, how have polling location counts per county changed over time? Or,  what are the most common types of polling places and how have they changed over time?

;; ## Polling location counts over time

;; In order to see which states have had the largest shifts in polling location counts over time, we can group the data by election date and state and then count the number of rows in each group:

(def all-polling-locations
  (-> "data/year_2024/week_3/polling-places.csv"
      (tc/dataset {:key-fn keyword})))

(-> all-polling-locations
    (tc/group-by [:election_date :state])
    (tc/aggregate {:count tc/row-count}))

;; We can plot this data to get a glimpse of how the count of polling places per state has changed over time:

(-> all-polling-locations
    (tc/group-by [:election_date :state])
    (tc/aggregate {:count tc/row-count})
    (hanami/plot ht/line-chart
                 {:X :election_date
                  :XTYPE :temporal
                  :Y :count
                  :COLOR {:field "state" :type "nominal"}}))

;; To make this chart easier to interpret, we'll change a few things, noted inline below:

(-> all-polling-locations
    (tc/group-by [:election_date :state])
    (tc/aggregate {:count tc/row-count})
    (hanami/plot (-> ht/line-chart
                     (assoc-in [:mark :strokeWidth] :MSWIDTH)
                     (assoc-in [:mark :opacity] :MOPACITY))
                 {:X :election_date
                  :XTYPE :temporal
                  :Y :count
                  ;; make the whole chart wider
                  :WIDTH 700
                  ;; make the line widths slightly bigger and more transparent
                  :MSWIDTH 4
                  :MOPACITY 0.5
                  :COLOR {:field "state" :type "nominal"
                          ;; get rid of the legend
                          :legend nil}
                  ;; replace the legend with tooltips, to see the state when hovering over a point
                  :MTOOLTIP true
                  ;; change the y-axis to a logarithmic scale to make it easier to interpret the
                  ;; wide range of counts
                  :YSCALE {:type "log"}}))

;; Even this doesn't really tell us much, other than that most states saw no change in the overall number of polling places over the years (lots of straight lines!). To get a better idea of how the states compare to each other, we can facet this graph by state.

;; ## Faceting polling places over time graph

(-> all-polling-locations
    (tc/group-by [:election_date :state])
    (tc/aggregate {:count tc/row-count})
    ;; this is pretty messy -- for now we have to manually cobble together
    ;; the plot specifications in this way, but optimizing these to make
    ;; them more ergonomic is high on the priority list
    (hanami/plot (-> ht/line-chart
                     (assoc :encoding (assoc (hc/get-default :ENCODING)
                                             :facet
                                             :FACET)))
                 {:X :election_date
                  :XTITLE "Year"
                  :XGRID false
                  :XTYPE :temporal
                  :Y :count
                  :YTITLE "Count"
                  :YGRID false
                  :HEIGHT 50
                  :WIDTH 80
                  :COLOR {:field :state :type :nominal :legend false}
                  :FACET {:field :state :type :nominal :columns 7 :title "State"}}))

;; This is still not super informative (IMO), but better. We can at least clearly see the outliers that did see some change over time, i.e. AR, MD, MN. It also reveals states that have no or little data.

;; ## Relative changes in polling places by state

;; Next we can try to visualize the relative changes in polling station counts between two years. We can check out which states have data first and see that it probably doesn't matter much which two years we choose:

(-> all-polling-locations
    (tc/select-columns [:election_date :state])
    (tc/unique-by [:election_date :state])
    (tc/fold-by [:election_date])
    (tc/update-columns {:state (partial map count)})
    (tc/order-by :election_date))

;; No year has as many states with data as the most recent year, so for the sake of this comparison we'll compute the differences in polling station counts between the first and last years. We can do this by "folding" our dataset up by state, rolling up all the rows that share a state into one list:

(-> all-polling-locations
    ;; first we'll group all the data by election date and state to
    ;; get our dataset of polling place counts by date and state
    (tc/group-by [:election_date :state])
    (tc/aggregate {:count tc/row-count})
    ;; we'll select only the rows from 2012 and 2020 so we can
    ;; compare just those two counts
    (tc/select-rows (fn [row]
                      (let [year (-> row :election_date .getYear)]
                        (or (= 2012 year) (= 2020 year)))))
    ;; sort this dataset by election date
    (tc/order-by [:election_date])
    ;; roll up the dates and counts by state
    (tc/fold-by [:state])
    ;; select only rows that have data for both elections
    (tc/select-rows #(= 2 (count (:count %))))
    ;; now we can compute the difference between the 2012 and 2020
    ;; counts -- we'll compute this as a relative difference (i.e.
    ;; relative to the 2012 value), for the sake of comparison, so
    ;; that we can get a sense of how states compare to each other
    ;; despite the large discrepancies in absolute numbers of polling places
    (tc/map-columns :difference [:count] (fn [[c2012 c2020]]
                                           (/ (- c2020 c2012)
                                              c2012)))
    ;; for the sake of seeing the relative differences in counts we only
    ;; care about the state and our new computed column
    (tc/select-columns [:state :difference]))

;; Now we can see quantitatively what the visualizations above seemed to show -- most states saw not much change in the number of polling stations. We can visualize this data as a bar chart and see the outliers pretty clearly:

(-> all-polling-locations
    (tc/group-by [:election_date :state])
    (tc/aggregate {:count tc/row-count})
    (tc/select-rows (fn [row]
                      (let [year (-> row :election_date .getYear)]
                        (or (= 2012 year) (= 2020 year)))))
    (tc/order-by [:election_date])
    (tc/fold-by [:state])
    (tc/select-rows #(= 2 (count (:count %))))
    (tc/map-columns :difference [:count] (fn [[c2012 c2020]]
                                           (-> c2020
                                               (- c2012)
                                               (/ c2012)
                                               double)))
    (hanami/plot (assoc ht/layer-chart :encoding :ENCODING)
                 {:X :difference
                  :XAXIS {:domain false :format "1%"
                          :title "Change in polling place count 2012-2020"}
                  :YSORT "-x"
                  :Y :state
                  :YTYPE "nominal"
                  :YTITLE "State"
                  :LAYER [{:mark "bar"}
                          {:mark {:type "text"
                                  :align {:expr "datum.difference < 0 ? 'right' : 'left'"}
                                  :dx {:expr "datum.difference < 0 ? -2 : 2"}
                                  }
                           :encoding {:text {:field :difference
                                             :format ".1%"}}}]}))

;; There we have it! An interesting investigation of some data on polling places. See you next week :)
