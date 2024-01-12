(ns year-2024.week-1.generate-dataset
  (:require
   [clojure.string :as str]
   [tablecloth.api :as tc])
  (:import
   [com.ibm.icu.text Transliterator]))

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

;; Anyway, the next thing they do is call `janitor::clean_names()` on the dataset, which is basically a wrapper function that applies [this `make_clean_names()` function](https://rdrr.io/cran/janitor/src/R/make_clean_names.R) to the names (of the columns). We can implement our own "name cleaning" function that does something similar. First, here's a function called `clean-string` that does (most of) what `make_clean_names()` does. We can improve it as the weeks go on:

;; This will be used in the to-ascii conversion part. Note this is also a good time to point out how easy it is to use Java libraries in Clojure:
(def transliterator
  (Transliterator/getInstance "Any-Latin; Latin-ASCII; Any-Lower"))

(defn to-ascii
  "Replaces characters in a given UTF-8 string with their lowercase ASCII equivalents.
       Ref:
       https://unicode-org.github.io/icu-docs/apidoc/released/icu4j/
       http://userguide.icu-project.org/transforms/general"
  [s]
  (.transliterate transliterator s))

(defn clean-string [val]
  (-> val
      ;; snake case everything
      (str/split #"(?=[A-Z])")
      ((partial str/join "_"))
      str/lower-case
      (str/replace " " "_")
      (str/replace "-" "_")
      ;; remove quotes, backslashes, and some symbols
      (str/replace "'" "")
      (str/replace "\"" "")
      (str/replace "%" "_percent_")
      (str/replace "#" "_number_")
      ;; convert everything to ASCII
      to-ascii))

;; We can write a wrapper/helper function that applies our name-cleaning to the header rows in a dataset. I still want them to be keywords though because those are better to work with:

(defn clean-names [ds]
  (tc/rename-columns ds (fn [keyword-column-name]
                          (-> keyword-column-name
                              name
                              clean-string
                              keyword))))

;; Now we can use this:

(-> imdb-ratings
    clean-names)

;; The last thing they do in the R cleaning script is filter out all the titles that have fewer than 10 votes:

(-> imdb-ratings
    clean-names
    (tc/select-rows (fn [row] (< 10 (:num_votes row)))))

;; OK! Next we fetch all imdb titles. We'll go through a similar process to inspect the data a bit to check whether there are any values we want to consider "missing". There are over 10 million rows in this dataset, so there's no way we can check everything just eyeballing it. Also don't try to print this out at the repl -- it will be way too slow. We can download the data though and assign it to `imdb-titles`. This one I'll assign using a `defonce` so we don't re-download the data every time the namespace loads:

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

;; Next they call `clean_names` to transform the column names:

(-> imdb-titles
    (tc/head 10)
    (tc/update-columns :all (fn [col]
                              (map (fn [val]
                                     (if (#{"" "NA" "\\N"} val)
                                       nil
                                       val))
                                   col)))
    clean-names)

;; Then they filter through the data. The comment on the R script is this:
;; > A handful of titles have miscoded data, which can be detected by cases where the "isAdult" field has a value other than 0 or 1. That's convenient, because I want to get rid of anything other than 0.
;; So we'll filter out anything other than 0. This is kind of sneaky, though, because if we check out `tc/info` for this dataset, we'll see all the columns contain strings, so we actually need to filter for the string "0", not the number:

(-> imdb-titles
    (tc/head 10)
    (tc/update-columns :all (fn [col]
                              (map (fn [val]
                                     (if (#{"" "NA" "\\N"} val)
                                       nil
                                       val))
                                   col)))
    clean-names
    (tc/select-rows (fn [row] (= "0" (:is_adult row)))))

;; Lastly they add a new column with "simple" titles to make searching easier, meaning all characters are lower-cased and punctuation has been removed. R has a cool syntax for indicating punctuation in regexes, the Java (and therefore Clojure) equivalent is `\p{Punct}`:

(-> imdb-titles
    (tc/head 10)
    (tc/update-columns :all (fn [col]
                              (map (fn [val]
                                     (if (#{"" "NA" "\\N"} val)
                                       nil
                                       val))
                                   col)))
    clean-names
    (tc/select-rows (fn [row] (= "0" (:is_adult row))))
    (tc/map-columns :simple_title [:primary_title]
                    (fn [primary-title]
                      (-> primary-title
                          str/lower-case
                          (str/replace #"\p{Punct}" "")))))

;; OK! We have the two main datasets in roughly the same shape as the R script. It turns out that running the transformations above on the entire 10M+ row titles dataset is really, really slow. To avoid the delays, I'm going to filter down the titles to the subset of holiday movies we're looking for _first_, and then apply the other (much slower) clean-up type transformations to the smaller dataset. In future weeks I'll look at some ways to improve the performance of these kinds of transformations. I'm also assigning the datasets new names so we can continue working with them without repeating all the steps every time.

(def cleaned-imdb-ratings
  (-> imdb-ratings
      clean-names
      (tc/select-rows (fn [row] (< 10 (:num_votes row))))))

(def holiday-movie-titles
  (-> imdb-titles
      clean-names
      (tc/select-rows (fn [row] (= "0" (:is_adult row))))
      ;; select only rows that have these movie-type values in the `title_type` column
      (tc/select-rows (comp #{"movie" "video" "tvMovie"} :title_type))
      ;; delete the `end_year` column, it's only relevant for series
      (tc/select-columns (complement #{:end_year}))
      ;; rename the `start_year` column to just `year`, to make sense for movies
      (tc/rename-columns {:start_year :year})
      ;; find movies with holiday-related words in their titles, first adding the simple_title column the removing any rows that have no simple title (because their titles contained only punctuation)
      (tc/map-columns :simple_title [:primary_title]
                      (fn [primary-title]
                        (-> primary-title
                            str/lower-case
                            (str/replace #"\p{Punct}" ""))))
      (tc/drop-missing :simple_title)
      (tc/select-rows (fn [row]
                        (or
                         (str/includes? (:simple_title row) "holiday")
                         (str/includes? (:simple_title row) "christmas")
                         (str/includes? (:simple_title row) "xmas")
                         (str/includes? (:simple_title row) "hanuk")
                         (str/includes? (:simple_title row) "kwanzaa"))))
      ;; now that the dataset is _much_ smaller, we can apply the n/a filtering to the entire thing and it won't take forever
      (tc/update-columns :all (fn [col]
                                (map (fn [val]
                                       (if (#{"" "NA" "\\N"} val)
                                         nil
                                         val))
                                     col)))))

;; Next they join these cleaned datasets together by the ID column (`tconst`) to create one dataset of rated titles:


(def rated-holiday-movies
  (-> holiday-movie-titles
      (tc/inner-join cleaned-imdb-ratings :tconst)))

;; Lastly we lengthen the genres column into separate values to create a holiday movie genres dataset and save it:

(-> rated-holiday-movies
    (tc/select-columns [:tconst :genres])
    (tc/update-columns {:genres (partial map #(when % (str/split % #",")))})
    (tc/unroll [:genres])
    (tc/write-csv! "data/year_2024/week_1/holiday-movie-genres.csv"))

;; And save the whole holiday movies dataset:

(-> rated-holiday-movies
    (tc/write-csv! "data/year_2024/week_1/holiday-movies.csv"))

;; Now we have the datasets ready to explore! Check out `src/year_2024/week_1/analysis.clj` for some more.
