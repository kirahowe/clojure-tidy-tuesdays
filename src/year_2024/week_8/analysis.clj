(ns year-2024.week-8.analysis
  (:require
   [aerial.hanami.templates :as ht]
   [clojure.string :as str]
   [scicloj.kindly.v4.kind :as kind]
   [scicloj.noj.v1.vis.hanami :as hanami]
   [tech.v3.datatype.statistics :as stat]
   [tablecloth.api :as tc]))

;; # Week 8 - R Consortium ISC Grants

;; This week's data is about funding available from the R Consortium Infrastructure Steering Committee (ISC) Grant Program. They've been awarding grants since 2016, with a "commitment to bolstering and enhancing the R Ecosystem".
;;
;; There's no example article to replicate, but we'll poke around the data a bit to explore some patterns and see what kinds of projects get funded.
;;
;; The data this week was gathered in a somewhat interesting way; it was scraped off of the web page of funding announcements. You can check out the [code that generates the dataset here](https://github.com/kiramclean/clojure-tidy-tuesdays/blob/main/data/year_2024/week_8/generate_dataset.clj), and I'm working on a more generic guide to web scraping in Clojure.
;;
;; For now we'll just start poking around this data to see what we can learn about funding for projects in R.

;; ## Main keywords

;; The first question we can pretty easily answer is what the most popular keywords are in the project titles and descriptions.
;;
;; First we'll load the data:

(def all-grants (tc/dataset "data/year_2024/week_8/isc-grants.csv"))

;; I'll load a separate file of stopwords (common English words that are meaningless in our context) to exclude, like "the", "with", etc. You can see the full list in the [`stopwords.txt` file](https://github.com/kiramclean/clojure-tidy-tuesdays/blob/main/src/year_2024/week_8/stopwords.txt).

(def stopwords (-> "src/year_2024/week_8/stopwords.txt"
                    slurp
                    (str/split #"\n")
                    set
                    (conj "")))

;; Then we can collect all of the individual words by grabbing just the title and summary columns, removing any punctuation, and lower-casing them all to make for a more accurate counting.

(def words
  (let [titles-and-summaries (-> all-grants
                                 (tc/select-columns ["title" "summary"])
                                 (tc/columns :as-seqs)
                                 flatten)]
    (->> titles-and-summaries
         (mapcat #(str/split % #" "))
         (map #(-> % (str/replace #"\W" "") str/lower-case))
         (remove stopwords))))

;; We can make a wordcloud out of these using vega:

(kind/vega {:data
            [{:name "table"
              :values words
              :transform
              [{:type "countpattern"
                :field "data"
                :case "upper"
                :pattern "[\\w']{3,}"}
               {:type "formula"
                :as "angle"
                :expr "[-45, -25, 0, 25, 45][~~(random() * 5)]"}
               {:type "formula"
                :as "weight"
                :expr "if(datum.count>=20, 600, 300)"}]}]
            :scales
            [{:name "color"
              :type "ordinal",
              :domain {:data "table", :field "text"}
              :range ["grey" "blue" "skyblue" "teal" "red" "orange"]}]
            :marks
            [{:type "text"
              :from {:data "table"}
              :encode
              {:enter
               {:text {:field "text"}
                :align {:value "center"}
                :baseline {:value "alphabetic"},
                :fill {:scale "color", :field "text"}}
               :update {:fillOpacity {:value 1}}
               :hover {:fillOpacity {:value 0.5}}}
              :transform
              [{:fontSizeRange [5 60]
                :type "wordcloud"
                :font "Helvetica Neue, Arial"
                :size [900 600]
                :padding 2
                :fontWeight {:field "datum.weight"}
                :rotate {:field "datum.angle"}
                :fontSize {:field "datum.count"}
                :text {:field "text"}}]}]})

;; This kind of graphic gives us some information about the contents of the dataset, but I find them hard to derive any actual insight from. It seems obvious that the main keywords in project funding proposals would include words like "package", "cran", etc.
;;
;; We can try a simple bar chart to see if we can get a better sense of what the top words are. We'll count the frequency of the words ourselves first, then pass it to hanami to visualise.

(def word-counts
  (->> words
       frequencies
       (sort-by second)))

;; First we can quickly check the distribution of the frequencies -- very meta. This can tell us what a reasonable cut off is, rather than just eyeballing it.

(->> word-counts
     (group-by second)
     (map (fn [[k v]]
            [k (count v)]))
     (sort-by (juxt first second)))

;; We can easily visualize this to get a sense of how the frequencies are distributed:

(def word-count-ds
  (tc/dataset (->> word-counts
                   (group-by second)
                   (map (fn [[k v]]
                          [k (count v)])))
              {:column-names ["Frequency" "Number of words with this frequency"]}))

(hanami/plot word-count-ds
             ht/bar-chart
             {:X "Frequency"
              ;; this transform business is an unfortunate necessity in order to sort
              ;; discrete numeric fields the way you'd expect
              :TRANSFORM [{:calculate "toNumber(datum.Frequency)" :as "Frequency-number"}]
              :XTYPE :nominal
              :XSORT {:field "Frequency-number"}
              :Y "Number of words with this frequency"
              ;; we'll use a log scale on the y axis to account for the vast range
              :YSCALE {:type "log"}})

;; Here we can see pretty clearly that there are _tons_ (1000+) of words that appear only 1-3 times, then it drops off pretty sharply but there's still lots of noise until we get to the 10-ish range. If we select only words that appear more than 12 times, we might be able to better understand what the important ones are. Note because of a [quirk in how tablecloth instantiates datasets from arrays of arrays where one element is a string](https://github.com/scicloj/tablecloth/blob/master/src/tablecloth/api/dataset.clj#L75-L79), we'll munge our data a bit first to create the dataset. This leave us with just 27 words:

(-> {"Word" (map first word-counts)
     "Frequency" (map second word-counts)}
    tc/dataset
    (tc/select-rows #(< 12 (% "Frequency")))
    (hanami/plot ht/bar-chart
                 {:X "Frequency"
                  :Y "Word"
                  :YSORT "-x"
                  :YTYPE "nominal"}))

;; It's hard to say anything concrete here, but a few of these stand out as potential trends in R Consortium ISC grant funding, like spatial libraries, documentation, and science/analysis projects.

;; ## Funding amounts over time

;; Another interesting insight we could extract from this data is about the funding amounts. To explore this, we can plot all the funding amounts over time. A simple point chart doesn't reveal all that much:

(-> all-grants
    (hanami/plot ht/point-chart
                 {:X "year"
                  :XTYPE :temporal
                  :Y "funded"
                  :MSIZE 50
                  :COLOR "group"}))

;; Looking at this, it's not obvious what the answers to obvious questions are, like what year saw the most funding? Which groups tend to get higher amounts? Etc.
;;
;; To answer those, we could try something else:

(-> all-grants
    (tc/group-by ["year" "group"])
    (tc/aggregate {"total-funded" #(stat/sum (% "funded"))})
    (hanami/plot ht/bar-chart
                 {:X "year"
                  :XTYPE :ordinal
                  :Y "total-funded"
                  :COLOR "group"}))

;; It's way easier to interpret the meaning of lengths starting from the same baseline than it is the placement of overlapping points in 2d space. Looking at this, we can easily see at a glance that 2018 was the most lucrative year for funding, and there's no clear advantage of one group over another -- some years the first group gets more funding, others it's the second.
;;
;; This was a fun week to poke around the data, especially to explore some more about web scraping in Clojure. See you next week :)
