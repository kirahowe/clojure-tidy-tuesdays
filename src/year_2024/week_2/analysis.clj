(ns year-2024.week-2.analysis
  (:require
   [aerial.hanami.templates :as ht]
   [clojure.string :as str]
   [scicloj.noj.v1.vis.hanami :as hanami]
   [tablecloth.api :as tc])
  (:import
   java.time.Month))

;; # Week 2 - Canadian NHL Hockey Player Birth Months

;; This is a fun one. For this week's #tidytuesday adventure, we're looking at Canadian NHL hockey player birth months. It's no secret up here that hockey players are disproportionately born in the earlier months of the year. It's anecdotally obvious to anyone who's ever been on or around a hockey team. We'll explore some data this week to see if the data backs up this observation, though.
;;
;; We're mostly recreating the analysis done in [this great article on NHL player birth months](https://jlaw.netlify.app/2023/12/04/are-birth-dates-still-destiny-for-canadian-nhl-players/). The author makes a reasonable point that although it's obvious (and I agree) to any Canadian that there are way more January-to-March birthdays on any hockey team, it's not necessarily obvious whether this is expected or not. Basically by analysing this data we're trying to explore the relationship between Canadian hockey player birth months and Canadian birth months in general. Are there just more Canadians born in the first months of the year for some reason? We'll see!

;; ## Canadian Birth Months

;; The data for this analysis are gathered from the NHL API and StatsCan. There are a bunch of files saved in [`data/year_2024/week_2`](https://github.com/kiramclean/clojure-tidy-tuesdays/tree/main/data/year_2024/week_2) you can just use, or if you want to explore how the data is collected and cleaned up you can check out [`data/year_2024/week_2/generate_dataset.clj`](https://github.com/kiramclean/clojure-tidy-tuesdays/blob/main/data/year_2024/week_2/generate_dataset.clj).

;; We start with data on all Canadian births from 1991-2022 to see the distribution of Canadian births by month.

(def canadian-births-by-month
  (-> "data/year_2024/week_2/canada_births_1991_2022.csv"
      (tc/dataset {:key-fn keyword})
      ;; group them by month
      (tc/group-by [:month])
      ;; count all births per month over the time period
      (tc/aggregate {:country-births #(reduce + (:births %))})
      ;; make a new column for the percentage of total births that each monthly total represents
      (as-> country-births-ds
            (let [total-births (->> country-births-ds
                                    :country-births
                                    (reduce +))]
              (tc/map-columns country-births-ds
                              :country-pct
                              :country-births
                              (fn [country-births]
                                (-> country-births
                                    (/ total-births)
                                    double)))))))

canadian-births-by-month

;; We can plot this to make it easier to interpret:
(-> canadian-births-by-month
    (hanami/plot (assoc ht/layer-chart :encoding :ENCODING)
                 {:TITLE "Canadian births by month 1991-2022"
                  :X :country-births
                  :XTITLE "Number of births (cumulatively)"
                  ;; vega-lite uses JS sorting, which does not sort numbers
                  ;; properly, so we'll tell it what order to use explicitly
                  :YSORT ["1" "2" "3" "4" "5" "6" "7" "8" "9" "10" "11" "12"]
                  :Y :month
                  :YTITLE "Month"
                  :YTYPE "nominal"
                  :LAYER [{:mark "bar"}
                          {:mark {:type "text" :align "left" :dx 3}
                           :encoding {:text {:field :country-pct :format ".1%"}}}]}))

;; So now we can see the distribution of births in Canada by month between 1991 and 2022. It actually looks like Canadians are less likely to be born in the earlier months of the year, with the most births in May, July, August, and September. We can compare this to the expected distribution (if each day had an exactly equal chance of being someone's birthday) by first constructing a dataset of the "expected" monthly distributions:

(def expected-births-by-month
  (let [months [1 2 3 4 5 6 7 8 9 10 11 12]]
    (tc/dataset {:month months
                 :expected-births (map (fn [month]
                                         (double (cond
                                                   (#{4 6 9 11} month) 30/365
                                                   (= month 2) 28/365
                                                   :else 31/365)))
                                       months)})))

;; And then computing the differences between the "expected" monthly births and the actual Canadian data:

(-> expected-births-by-month
    (tc/inner-join canadian-births-by-month :month)
    (tc/map-columns :difference [:expected-births :country-pct]
                    (fn [expected country]
                      (- expected country))))

;; We can check the distribution for hockey players next.

;; ## Canadian NHL Hockey Player Birth Months

;; We have a dataset that includes player info for all NHL players over all time. We'll start by doing a similar breakdown to the one we did above for all Canadians.

(def nhl-player-births-by-month
  ;; first, load the dataset:
  (-> "data/year_2024/week_2/nhl-player-births.csv"
      (tc/dataset {:key-fn keyword})
      ;; filter out just Canadian players
      (tc/select-rows #(= (:birth-country %) "CAN"))
      ;; group the players by birth month
      (tc/group-by [:birth-month])
      ;; count the number of players per month
      (tc/aggregate {:births tc/row-count})
      ;; we'll add a new column where we compute the percentage of births that each monthly total represents
      (as-> player-births-ds
          (let [total-births (->> player-births-ds
                                  :births
                                  (reduce +))]
            (tc/map-columns player-births-ds
                            :player-pct
                            :births
                            (fn [births]
                              (-> births
                                  (/ total-births)
                                  double)))))))

;; We can make a similar bar chart to see how the distribution of hockey player births compares to the all-Canada data:

(hanami/plot nhl-player-births-by-month
             (assoc ht/layer-chart :encoding :ENCODING)
             {:TITLE "NHL player births by month"
              :X :births
              :XTITLE "Number of births"
              ;; vega-lite uses JS sorting, which does not sort numbers properly, so we'll tell it what order to use explicitly
              :YSORT ["JANUARY"
                      "FEBRUARY"
                      "MARCH"
                      "APRIL"
                      "MAY"
                      "JUNE"
                      "JULY"
                      "AUGUST"
                      "SEPTEMBER"
                      "OCTOBER"
                      "NOVEMBER"
                      "DECEMBER"]
              :Y :birth-month
              :YTITLE "Month"
              :YTYPE "nominal"
              :LAYER [{:mark "bar"}
                      {:mark {:type "text" :align "left" :dx 3}
                       :encoding {:text {:field :player-pct
                                         :format ".1%"}}}]})

;; These are definitely more distributed disproportionately through the early months of the year. We can compare them on top of each other to see more clearly next.

;; ## Comparing all Canadian births to Canadian NHL player births

;; We'll simplify and combine the datasets first, before visualizing them:

(def combined-births-by-month
  (let [canada-births (-> canadian-births-by-month
                        ;; clean up the month names so they are the same in both datasets
                          (tc/update-columns {:month (fn [col]
                                                       (map #(-> % Month/of .name str/capitalize)
                                                            col))}))
        player-births (-> nhl-player-births-by-month
                          (tc/rename-columns {:birth-month :month
                                              :births :player-births})
                          (tc/update-columns {:month (partial map str/capitalize)}))
        expected-births (-> expected-births-by-month
                            (tc/rename-columns {:expected-births :expected-births-pct})
                            (tc/update-columns {:month (fn [col]
                                                         (map #(-> % Month/of .name str/capitalize)
                                                              col))}))]
    ;; join the datasets by month to make one dataset:
    (-> canada-births
        (tc/inner-join player-births :month)
        (tc/inner-join expected-births :month))))

;; The visualization in the linked article is a really great example of where ggplot really shines. I think it would be cool to explore how this might be possible to re-create with vega-lite (via the tools and wrappers we have around it Clojure), but for now we can visualize the data in a more simple way using a grouped bar chart. Since this is a pretty custom chart, I'm going to hand-write the vega-lite spec. There's tons to learn about vega-lite, but the main takeaway for now is that you can pass any vega-lite (or vega) spec to Clojure's vega-viz wrappers and it will render them:

(hanami/plot combined-births-by-month
             {:data {:values :DATA
                     :format :DFMT}
              :repeat {:layer [:expected-births-pct :country-pct :player-pct]}
              :spec {:encoding {:y {:field :month
                                    :type "nominal"
                                    :axis {:title "Month"}
                                    :sort ["January"
                                           "February"
                                           "March"
                                           "April"
                                           "May"
                                           "June"
                                           "July"
                                           "August"
                                           "September"
                                           "October"
                                           "November"
                                           "December"]}}
                     :layer [{:mark "bar"
                              :height {:step "12"}
                              :encoding {:x {:field {:repeat "layer"}
                                             :type "quantitative"
                                             :axis {:title "Percentage of births"
                                                    :format ".0%"}}
                                         :color {:datum {:repeat "layer"}
                                                 :type "nominal"
                                                 :scale {:range ["steelblue" "red" "black"]}
                                                 :legend {:title "Group"
                                                          :labelExpr "{'country-pct': 'Canada', 'player-pct': 'NHL Players', 'expected-births-pct': 'Expected'}[datum.label]"}}
                                         :yOffset {:datum {:repeat "layer"}}}}
                             {:mark {:type "text" :dx -80 :fontSize 10.5}
                              :encoding {:text {:field {:repeat "layer"} :format ".1%"}
                                         :color {:value "white"}
                                         :yOffset {:datum {:repeat "layer"}}}}]}}
             {})

;; When the data are all side-by-side like this we can see pretty clearly that it's true that NHL players are in fact more likely to be born in the earlier parts of the year.

;; ## Chi-squared test

;; The last thing the author of the original article does is a chi-squared test on the NHL player births distribution. A chi-squared test is one you can use to see whether a given variable follows a hypothesized distribution in a more quantitative way, so it's basically a more stats-y way to show that the NHL player birth dates distribution is different than we would expect. I.e., it can answer, quantitatively, the question "what are the chances that a sample of birth dates would be distributed the way our NHL player birth dates dataset is, compared to the expected distribution?" In this case our "expected" distribution is based on the distribution by month of all Canadian births.
;;
;; In Clojure, this test is already implemented in the [fastmath](https://github.com/generateme/fastmath) library, but in the interest of teaching (and because I already did it the hard way first before I learned how to use the fastmath function), we can just do it manually.
;;
;; First we compute the chi statistic. The formula is $$\sum_{k=1}^{n}\frac{(O_k - E_k)^2}{E_k}$$ where O is the actual value and E is the expected one. We can implement this in Clojure. The first thing we need is the expected values. Our "actual" values are the count of NHL player births by month, so we can compute the expected ones by applying the expected distribution (based on the Canadian births data):

(def actual-and-expected-births
  ;; first get the total count of player births
  (let [total-player-births (->> (tc/select-columns nhl-player-births-by-month [:births])
                                 tc/rows
                                 (map first)
                                 (reduce +))

      ;; clean up the month names so they are the same in both datasets
        canada-births (-> canadian-births-by-month
                          (tc/update-columns {:month (fn [col]
                                                       (map #(-> % Month/of .name str/capitalize)
                                                            col))}))]
    ;; combine the Canadian data with the NHL player data to calculate the expected values
    (-> nhl-player-births-by-month
        (tc/rename-columns {:birth-month :month
                            :births :actual})
        (tc/update-columns {:month (partial map str/capitalize)})
        (tc/inner-join canada-births :month)
        (tc/map-columns :expected
                        [:country-pct]
                        (fn [pct]
                          (Math/round (* pct total-player-births))))
        (tc/select-columns [:month :actual :expected]))))

actual-and-expected-births

;; This gives us the expected values -- the number of births we would expect per month if we randomly sampled the same number of births from the Canadian data as we have NHL player births. Now we can compute the chi squared statistic:

(def chi-squared
  (->> (tc/select-columns actual-and-expected-births [:actual :expected])
       tc/rows
       (map (fn [[actual expected]]
              ;; find the difference between the actual and expected values
              (let [a-e (- actual expected)]
                (-> a-e
                    ;; square it
                    (* a-e)
                    ;; divide it by the expected value
                    (/ expected)))))
       ;; sum them
       (reduce +)
       ;; make it a number instead of a fraction
       double
       Math/round))

chi-squared

;; In order to use this value to test whether our NHL player birth dates follow an "expected" distribution, we can calculate the P-value using a chi-squared distribution. This is also implemented in fastmath, in this case we'll use it. The P value tells us what the probability is of observing the given discrepancy between the actual and expected values. The degrees of freedom is one less than the number of categories we have, so in our case 11.

(require '[fastmath.stats :as stats])
(require '[fastmath.random :as r])

(stats/p-value (r/distribution :chi-squared {:degrees-of-freedom 11}) chi-squared)

;; Interpreting this, we can say there is a 0.0% chance that the NHL player birth data are sampled from the Canadian birth data. In other words, the probability that the NHL player birth distribution by month is a fluke is 0. It's definitely anomalous, not a result of not having a large enough sample size.
;;
;; So that wraps up our exploration of this data! I'd love to poke around some more and see how the distribution of NHL players by nationality breaks down over time, and play around with vega-lite some more to make cooler looking graphs. But here I am already late for next week's tidy tuesday, so I'll move on for now and come back once I've learned more.
;;
;; See you next week :)
