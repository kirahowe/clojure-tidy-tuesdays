(ns year-2024.week-1.generate-dataset
  (:require
   [clojure.string :as str]
   [tablecloth.api :as tc]))

;; # Generating the dataset

;; This is mostly just a "translation" into Clojure of the [cleaning script](https://github.com/rfordatascience/tidytuesday/tree/master/data/2023/2023-12-12#cleaning-script) provided in the R4DS tidy tuesday repo, with some additional explanations and commentary.

;; ## Fetching the ratings

;; First we load the ratings for every title in IMDB title. R's `read_tsv` function has a handy option to indicate what values to consider as missing. Tablecloth knows what to do with a gzipped tsv URL by default, but it doesn't have options to automatically clean up nils, so we'll transform our dataset to accomplish the same thing:

(def imdb-ratings
  (-> "https://datasets.imdbws.com/title.ratings.tsv.gz"
      tc/dataset))

;; We can inspect the dataset to see what unique values exist in each column, to see which ones we might want to consider "missing":

;; Check the column names:

(-> imdb-ratings tc/column-names)

;; They're strings, keywords will be easier to work with. Tablecloth does have an option for that:

(def imdb-ratings
  (-> "https://datasets.imdbws.com/title.ratings.tsv.gz"
      (tc/dataset {:key-fn keyword})))

(-> imdb-ratings tc/column-names)

;; We can see the IDs (in the `tconst` row) mostly start with `tt`, we can check if there are any that don't:

(-> imdb-ratings
    (tc/select-rows (fn [row]
                      (not (str/starts-with? (:tconst row) "tt"))))
    tc/row-count)

;; There are not, so we'll accept all of the IDs as valid values. What about the other columns? We can inspect the unique values in those columns to see if there are any we might want to exclude:

(-> imdb-ratings :averageRating distinct sort)

;; The only values in that column look like valid ratings. What about the `:numVotes` column?

(-> imdb-ratings :numVotes distinct sort)

;; There are too many to eyeball this time.. we can check the type of values in the column to make sure they're all numbers:

(->> (:numVotes imdb-ratings) (map type) distinct)

;; They're all numbers! So it looks like we don't have any missing values to account for here. IMDB keeps a neat database, which is great.

;; Tablecloth actually includes a handy function that could have told us most of this, there's a function called `info` that gives us some basic info about what's in the columns we're working with, including the type(s) of data that each column contains:

(tc/info imdb-ratings)

;; Anyway, next thing they do in the R cleaning script is call `janitor::clean_names()`. We'll look into what that does eventually, but for now we can skip it since there are no names to clean here, we already have exclusively numbers and IDs. Lastly they filter out all the titles that have fewer than 10 votes. We'll re-assign the resulting dataset to `imdb-rating` to "save" this as the final version. Note in reality `imdb-ratings` will be bound to whatever version was last evaluated, but we'll assume we're working top-down in this notebook (and in general):

(def imdb-ratings
  (-> "https://datasets.imdbws.com/title.ratings.tsv.gz"
      (tc/dataset {:key-fn keyword})
      (tc/select-rows (fn [row] (< 10 (:numVotes row))))))

;; OK! Next we fetch all imdb titles. We'll go through a similar process to inspect the data a bit to check whether there are any values we want to consider "missing". There are over 10 million rows in this dataset, so there's no way we can check everything just eyeballing it. Also don't try to print this out at the repl -- it will be way too slow. We can download the data though and assign it to `imdb-titles`:

(defonce imdb-titles
  (-> "https://datasets.imdbws.com/title.basics.tsv.gz"
      (tc/dataset {:key-fn keyword})))

;; For our explorations this time we'll just work with a tiny subset of the data. We can check out what the first few rows contain pretty quickly:

(-> imdb-titles
    (tc/head 10))

;; Here we have some values we probably want to clean up. We'll implement the same kind of cleaning that the R script does on ingestion, replacing all empty strings, `"NA"`s, or `"\N"`s with empty values. Note I'm just doing this to the first few rows for now as we build up the cleaning script. It will be slow to do on the whole thing so we'll wait until we're pretty sure it's working:

(-> imdb-titles
    (tc/head 10)
    (tc/update-columns :all (fn [col]
                                 (map (fn [val]
                                        (if (#{"" "NA" "\\N"} val)
                                          nil
                                          val))
                                      col))))

;; Next

;; (-> imdb-titles
;;     (tc/update-columns :all (fn [col]
;;                               (map (fn [val]
;;                                      (if (#{"" "NA" "\\N"} val)
;;                                        nil
;;                                        val))
;;                                    col)))


;;     ;; (tc/select-rows (fn [row]
;;     ;;                   (some (fn [val] (or (= "N/A" val)
;;     ;;                                       (= "\N" val)))
;;     ;;                         (vals row))))
;;     ;; (tc/replace-missing)
;;     ;; (tc/select-rows (fn [row] ()))
;;     )


;; Then they call `clean_names()`:

;; make_clean_names <- function (string,
;;                               case = "snake",
;;                               replace=
;;                               c ("'" = "",
;;                                  "\"" = "",
;;                                  "%" = "_percent_",
;;                                  "#" = "_number_"),
;;                               ascii=TRUE,
;;                               use_make_names=TRUE,
;;                               allow_dupes=FALSE,
;;                               #default arguments for snake_case::to_any_case
;;                               sep_in = "\\.",
;;                               transliterations = "Latin-ASCII",
;;                               parsing_option = 1,
;;                               numerals = "asis",
;;                               ...)

(defn clean-string [val]
  (-> val
      str/lower-case
      (str/replace " " "_")
      (str/replace "-" "_")
      (str/replace "'" "")
      (str/replace "\"" "")
      (str/replace "%" "_percent_")
      (str/replace "#" "_number_")))

(defn clean-names [ds]
  (-> ds
      (tc/update-columns :all (partial map clean-string))))

;; (-> imdb-titles
;;     (tc/update-columns :all (fn [col]
;;                               (map (fn [val]
;;                                      (if (#{"" "NA" "\\N"} val)
;;                                        nil
;;                                        val))
;;                                    col)))
;;     clean-names
;;     ;; (tc/update-columns :all (partial map clean-string))

;; ;; (tc/select-rows (fn [row]
;;     ;;                   (some (fn [val] (or (= "N/A" val)
;;     ;;                                       (= "\N" val)))
;;     ;;                         (vals row))))
;;     ;; (tc/replace-missing)
;;     ;; (tc/select-rows (fn [row] ()))
;;     )
;; ```r
;; imdb_ratings <- readr::read_tsv ("https://datasets.imdbws.com/title.ratings.tsv.gz",
;;                                  na = c ("", "NA", "\\N")) |>
;; janitor::clean_names () |>
;; dplyr::filter (num_votes >= 10)
;; imdb_titles <- readr::read_tsv ("https://datasets.imdbws.com/title.basics.tsv.gz",
;;                                 na = c ("", "NA", "\\N")) |>
;; janitor::clean_names () |>
;; #A handful of titles have miscoded data, which can be detected by cases where
;; #the "isAdult" field has a value other than 0 or 1. That's convenient,
;; #because I want to get rid of anything other than 0.
;; dplyr::filter (is_adult == 0) |>
;; dplyr::select (-is_adult) |>
;; dplyr::mutate (#Create a column for easier title searching.
;;                          simple_title = tolower (primary_title) |>
;;                          stringr::str_remove_all ("[[:punct:]]"))
;; ```
