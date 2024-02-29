(ns year-2024.week-7.analysis
  (:require
   [aerial.hanami.templates :as ht]
   [charred.api :as charred]
   [scicloj.noj.v1.vis.hanami :as hanami]
   [tablecloth.api :as tc]))

;; # Week 7 - Valentine's Day Consumer Data

;; This week's data is from Valentine's day surveys about how much people spend and on what. The data originally comes from a kaggle dataset and has been collated into three CSVs by the R tidy tuesdays team, so we'll just use it directly.
;;
;; There are three datasets -- one on historical spending, one breaking down spending on gifts by age, one breaking it down by gender.

(def historical-spending
  (tc/dataset "https://raw.githubusercontent.com/rfordatascience/tidytuesday/master/data/2024/2024-02-13/historical_spending.csv"))

(def gifts-gender
  (tc/dataset "https://raw.githubusercontent.com/rfordatascience/tidytuesday/master/data/2024/2024-02-13/gifts_gender.csv"))

(def gifts-age
  (tc/dataset "https://raw.githubusercontent.com/rfordatascience/tidytuesday/master/data/2024/2024-02-13/gifts_age.csv"))

;; ## Historical spending

;; The first and easiest thing to plot is just spending over time. This isn't linear data (i.e. there's no inferred value missing between the years), so we'll just make a bar chart.

(-> historical-spending
    (hanami/plot ht/bar-chart
                 {:X "Year"
                  :XTYPE "ordinal"
                  :XAXIS {:labelAngle -45 :title "Year"}
                  :Y "PerPerson"
                  :YAXIS {:format "$.0f" :title "Spending per person"}}))

;; We can see spending is increasing over time. It might also be interesting to see how the proportion of people celebrating Valentine's day is changing over time:

(-> historical-spending
    (hanami/plot ht/bar-chart
                 {:X "Year"
                  :XTYPE "ordinal"
                  :XAXIS {:labelAngle -45 :title "Year"}
                  :Y "PercentCelebrating"}))

;; From this we can clearly see it's decreasing. If we change the scale of the y axis though it makes the decrease seem less pronounced. We can also format the y axis values -- to do this we'll have to update the column to support the new formatting.

(-> historical-spending
    (tc/update-columns {"PercentCelebrating"
                        (partial map #(-> % (/ 100) double))})
    (hanami/plot ht/bar-chart
                 {:X "Year"
                  :XTYPE "ordinal"
                  :XAXIS {:labelAngle -45 :title "Year"}
                  :Y "PercentCelebrating"
                  :YSCALE {:domain [0 1]}
                  :YAXIS {:format "1%" :title "Percent celebrating"}}))

;; We could compute the percentage change in spending year over year per category, to see how the spending habits by category have changed over the period we have data for. First we'll get the first and last years that we have data for, then we'll compute the percentage change for each category.
;;
;; We can see there are 13 rows, so we'll just make extra sure they're sorted by year and then manually pick out the first and last ones. Then we'll want to pivot the data to lengthen it so we have one column for the categories and one for the amounts.
;;
;; This is where I ran into the first problem with the data. Sorting by year should work here, but instead we get an error about not being able to find the "Year" column.

(tc/row-count historical-spending)

;; ```clj
;; (-> historical-spending
;;     (tc/order-by "Year")
;;     (tc/select-rows [0 12]))
;; ```

;; The "Year" column header looks right:

(-> historical-spending
    tc/column-names
    first)

;; But if we inspect the first character of it, we can see it's misleading:

(-> historical-spending
    tc/column-names
    first
    first)

;; (That should just be  "Y")
;;
;; We can find out what actual character this is by dropping into Java:

(-> historical-spending
    tc/column-names
    first
    first
    ;; first convert the character to its integer value
    int
    (Integer/toHexString))

;; So this first character is actually the unicode character "U+FEFF", the dreaded "zero-width no-break space", also known as the [byte order mark](https://en.wikipedia.org/wiki/Byte_order_mark). For historical and other reasons, sometimes files start with this nefarious character.
;;
;; There are lots of ways to fix this, one is to just strip the character from the CSV first and then create our dataset. We'll have to create it a little more manually since we'll be passing the data directly the dataset constructor instead of reading it from a file:

(def historical-spending
  (let [data (-> "https://raw.githubusercontent.com/rfordatascience/tidytuesday/master/data/2024/2024-02-13/historical_spending.csv"
                 slurp
                 (subs 1)
                 charred/read-csv)]
    (tc/dataset (rest data) {:column-names (first data)})))

;; Now we should be able to pivot our data to re-organize it and make our grouped bar chart:

(-> historical-spending
    (tc/order-by "Year")
    (tc/select-rows [0 12])
    (tc/pivot->longer (complement #{"Year" "PercentCelebrating" "PerPerson"})
                      {:target-columns "Category"
                       :value-column-name "Amount"}))

;; Great. Now we can get back to computing the percent changes between the years. There are lots of ways to do this. I think the easiest is to pivot our data to tidy it up, then fold the rows by Category to group the pairs of values to work with.

(-> historical-spending
    (tc/order-by "Year")
    (tc/select-rows [12 0])
    (tc/pivot->longer (complement #{"Year" "PercentCelebrating" "PerPerson"})
                      {:target-columns "Category"
                       :value-column-name "Amount"})
    (tc/convert-types ["Amount"] :float64)
    (tc/fold-by "Category")
    (tc/map-columns "Increase" ["Amount"] (fn [[new old]]
                                            (-> new (- old) (/ old)))))

;; Now we can plot the percentage increase by category. I'll sort by the increase to make the chart look nicer.

(-> historical-spending
    (tc/order-by "Year")
    (tc/select-rows [12 0])
    (tc/pivot->longer (complement #{"Year" "PercentCelebrating" "PerPerson"})
                      {:target-columns "Category"
                       :value-column-name "Amount"})
    (tc/convert-types ["Amount"] :float64)
    (tc/fold-by "Category")
    (tc/map-columns "Increase" ["Amount"] (fn [[new old]]
                                            (-> new (- old) (/ old))))
    (hanami/plot ht/bar-chart
                 {:X "Category"
                  :XTYPE :nominal
                  :XSORT "y"
                  :Y "Increase"}))

;; ## Spending differences between men and women

;; We also have data about spending differences between men and women. We can compare those side by side if we rearrange the data a little bit first. Our gifts-by-gender dataset has a separate column per category. We'll want to rearrange the data so that the category is a single variable with each value in its own row.
;;
;; This dataset has the same issue as the last one, so we'll re-fetch it and strip the first character.

(def gifts-gender
  (let [data (-> "https://raw.githubusercontent.com/rfordatascience/tidytuesday/master/data/2024/2024-02-13/gifts_gender.csv"
                 slurp
                 (subs 1)
                 charred/read-csv)]
    (tc/dataset (rest data) {:column-names (first data)})))

;; Now we should be able to pivot our data to re-organize it and make our grouped bar chart:

(-> gifts-gender
    (tc/pivot->longer (complement #{"Gender" "SpendingCelebrating"})
                      {:target-columns "Category"
                       :value-column-name "Percentage"})
    (hanami/plot ht/bar-chart
                 {:X "Category"
                  :XTYPE :nominal
                  :Y "Percentage"
                  :COLOR "Gender"})
    (assoc-in [:encoding :xOffset] {:field "Gender"}))

;; Here we can see pretty clearly that men are significantly more likely to spend on flowers and jewelry than women, but the other categories are more evenly matched.
;;
;; We also have data about spending by age, so we can explore a bit how spending habits change with age. We'll start again with fixing the dataset and pivoting the data to make a column for categories and a column for the percentages.

(def gifts-age
  (let [data (-> "https://raw.githubusercontent.com/rfordatascience/tidytuesday/master/data/2024/2024-02-13/gifts_age.csv"
                 slurp
                 (subs 1)
                 charred/read-csv)]
    (tc/dataset (rest data) {:column-names (first data)})))

;; I think the most informative way to plot this would be to see the categories grouped together, comparing the age ranges side by side within a category.. if that makes any sense. Anyway like this:

(-> gifts-age
    (tc/pivot->longer (complement #{"Age" "SpendingCelebrating"})
                      {:target-columns "Category"
                       :value-column-name "Percentage"})
    (hanami/plot ht/bar-chart
                 {:X "Category"
                  :XTYPE :nominal
                  :Y "Percentage"
                  :COLOR "Age"})
    (assoc-in [:encoding :xOffset] {:field "Age"}))

;; This shows a pretty clear trend that spending in all categories _except_ for greeting cards seems to decrease with age. This is interesting, but seeing it makes me wonder what the main message would be if we reversed the age/category encodings:

(-> gifts-age
    (tc/pivot->longer (complement #{"Age" "SpendingCelebrating"})
                      {:target-columns "Category"
                       :value-column-name "Percentage"})
    (hanami/plot ht/bar-chart
                 {:X "Age"
                  :XTYPE :nominal
                  :Y "Percentage"
                  :COLOR "Category"})
    (assoc-in [:encoding :xOffset] {:field "Category"}))

;; I still think the first one is better, but I suppose it depends what information you're looking for. From this one we can see that people of all ages are likely to spend on candy and greeting cards, and that gift cards are the least common gift no matter the age.
;;
;; Well.. there are tons of questions we could ask of this data, but I'll leave it here for this week. See you next week :)
