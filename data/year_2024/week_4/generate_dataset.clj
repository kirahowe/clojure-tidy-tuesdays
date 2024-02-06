(ns year-2024.week-4.generate-dataset
  (:require
   [clojure.string :as str]
   [tablecloth.api :as tc]))

;; # Week 4 Data - Educational attainment of young people in English towns

;; The data this week is just a simple excerpt from a dataset published by the UK's Office for National Statistics. We'll extract the data from an excel spreadsheet that they publish. Tablecloth supports .xlsx files iff the underlying Java library for working with excel is added as a dependency and required. It's not required by default because the core team at `tech.ml.dataset` (upon which tablecloth is built) did not want to impose it on all users by default.
;;
;; We can add it here, though. We'll need to add this to our `deps.edn` file:
;;
;;```clj
;; org.dhatim/fastexcel-reader {:mvn/version "0.12.8" :exclusions [org.apache.poi/poi-ooxml]}
;;```
;;
;; and then we can require the library

(require '[tech.v3.libs.fastexcel :as xl])

;; And now our excel spreadsheet should load. There are more details in the [`tech.ml.dataset` docs](https://techascent.github.io/tech.ml.dataset/tech.v3.libs.fastexcel.html) about how excel files are handled, but the important part to know for now is that if there is only one sheet in the workbook, it does what you'd expect and loads the only sheet as the dataset and if there is more than one sheet in the workbook it errors.
;;
;; You can see the helpful error message if you try loading the workbook:

(-> "https://www.ons.gov.uk/file?uri=/peoplepopulationandcommunity/educationandchildcare/datasets/educationalattainmentofyoungpeopleinenglishtownsdata/200708201819/youngpeoplesattainmentintownsreferencetable1.xlsx"
    tc/dataset)

;; So instead we'll load the whole workbook:

(def sheets
  (-> "https://www.ons.gov.uk/file?uri=/peoplepopulationandcommunity/educationandchildcare/datasets/educationalattainmentofyoungpeopleinenglishtownsdata/200708201819/youngpeoplesattainmentintownsreferencetable1.xlsx"
      xl/workbook->datasets))

;; This returns a sequence of datasets named after the sheets. We can check what the sheets are named:

(map tc/dataset-name sheets)

;; "Data" is probably the one we want, we can double check by inspecting the column names:

(map tc/column-names sheets)

;; Indeed. So we'll write just this data to a csv and use that for this week's challenge! I'll clean up the names first so they're predictably snake-cased.

(defn clean-string [val]
  (-> val
      (str/split #" ")
      ((partial str/join "_"))
      str/lower-case))

(-> sheets
    second
    (tc/rename-columns clean-string)
    (tc/write-csv! "data/year_2024/week_4/english_education.csv"))

;; There's another dataset that will come in handy as we try to re-create the graphs from the article, too, which is also provided as an excel file. We can load this one directly as a dataset, since it only contains one sheet. Inspecting it, though, we'll see that the first few rows have metadata about the dataset (which is the worst way to include metadata, but hey, at least it's there..). Inspecting the first few rows of the dataset, we can see that the real column names are actually in row 4, where the dataset actually "starts":

(-> "https://www.ons.gov.uk/visualisations/dvc2651b/fig3/datadownload.xlsx"
    tc/dataset
    tc/head)

;; So we'll tell tablecloth to ignore the first 4 rows (since we don't particularly care about the metadata), and save the data as a CSV that'll be easier to work with:

(-> "https://www.ons.gov.uk/visualisations/dvc2651b/fig3/datadownload.xlsx"
    (tc/dataset {:n-initial-skip-rows 4})
    (tc/write-csv! "data/year_2024/week_4/education-and-income-scores.csv"))
