(ns year-2024.week-5.generate-dataset
  (:require
   [charred.api :as charred]
   [clojure.string :as str]
   [tablecloth.api :as tc]))

(def raw-groundhogs-response
  (-> "https://groundhog-day.com/api/v1/groundhogs/"
      slurp
      (charred/read-json {:key-fn keyword})))

(def groundhogs
  (-> raw-groundhogs-response
      :groundhogs
      tc/dataset))

(-> groundhogs
    (tc/select-columns (complement #{:predictions :successor :contact}))
    (tc/separate-column :coordinates [:latitude :longitude] #(-> %
                                                                 str/trim
                                                                 (str/replace #",$" "")
                                                                 (str/split #",")
                                                                 (->> (map parse-double))))
    (tc/update-columns [:isGroundhog :active] (partial map #(if (zero? %) false true)))
    (tc/write-csv! "data/year_2024/week_5/groundhogs.csv"))

(-> groundhogs
    (tc/select-columns [:id :predictions])
    (tc/unroll :predictions)
    (tc/separate-column :predictions [:year :shadow :details] vals)
    (tc/update-columns :shadow (partial map #(when % (if (zero? %) false true))))
    (tc/write-csv! "data/year_2024/week_5/predictions.csv"))
