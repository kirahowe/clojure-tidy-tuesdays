(ns year-2024.week-2.generate-dataset
  (:require
   [charred.api :as charred]
   [clojure.string :as str]
   [tablecloth.api :as tc])
  (:import java.time.Month))

;; # Week 2 Data - NHL Hockey Player Birthdates

;; The data this week is about NHL hockey player birth dates. This code is mostly a "translation" into Clojure of the [data cleaning script from this week's Tidy Tuesday](https://github.com/rfordatascience/tidytuesday/blob/master/data/2024/2024-01-09/readme.md#cleaning-script) from the R4DS online learning community. The idea is to show and explain how to gather data from different types of APIs in Clojure.
;;
;; First we'll make a function to transform the headers of the data we'll fetch from the API. Keywords are better and more idiomatic to work with in Clojure (as opposed to strings), and this also kebab-cases the headers (also conventional in Clojure).

(defn- kebab-cased-keyword [val]
  (-> val
      (str/replace #"[^A-Za-z0-9-_\s]" "")
      (str/split #"(?=[A-Z])")
      ((partial str/join "-"))
      (str/replace " " "-")
      (str/replace "_" "-")
      str/lower-case
      keyword))

;; ## Teams Data

;; First we'll get the data on all NHL teams from the NHL API and select just the team name and tri-code columns:

(def teams
  (-> "https://api.nhle.com/stats/rest/en/team"
      ;; the easiest way to request a URL in Clojure is to `slurp` it
      slurp
      ;; `charred` is the fasted library for reading JSON, we'll also pass it our function to format the headers on the fly
      (charred/read-json :key-fn kebab-cased-keyword)
      ;; this particular API returns the actual data wrapped in a map under the `data` key, which is fairly common
      :data
      ;; then we can make a dataset out of it, tablecloth knows what to do with a sequence of maps
      tc/dataset
      (tc/select-columns [:full-name :tri-code])))

;; We'll save this to work with later
(tc/write-csv! teams "data/year_2024/week_2/nhl-teams.csv")

;; ## Player Data, via seasons and rosters

;; Next we fetch the rosters for every NHL team using these team codes:

(-> teams
    ;; first, collect the values of the `tri-code` column
    (tc/select-columns :tri-code)
    tc/rows
    flatten
    ;; then we'll call the seasons API with each of these team codes to get the season IDs, collecting them into a map keyed by team code, since we need both the team code and the season IDs for the next step:
    (as-> team-codes
          (zipmap team-codes (map (fn [team-code]
                                    (println "fetching roster season for " team-code)
                                    (-> (str "https://api-web.nhle.com/v1/roster-season/" team-code)
                                        slurp
                                        charred/read-json))
                                  team-codes)))
    ;; now we have a list of team codes and corresponding season IDs, we'll call the rosters API for every season to fetch the rosters for each team/season combo. In order to preserve the team codes and season IDs we'll add these into the resulting player data objects while we have them. I'm also flattening the results into one big list (the API returns the rosters as maps keyed by position, which is included in the player data anyway) and logging each API call so I can have some info whilst this is running -- it takes a while to get through the whole collection and I want to make sure it's not stuck
    (as-> team-seasons
          (mapcat (fn [[team-code season-ids]]
                    (mapcat (fn [season-id]
                              (println "fetching roster for team " team-code " and season " season-id)
                              (let [season-roster (-> (str "https://api-web.nhle.com/v1/roster/" team-code "/" season-id)
                                                      slurp
                                                      charred/read-json)]
                                (->> season-roster
                                     vals
                                     flatten
                                     (map #(assoc % "teamCode" team-code "seasonId" season-id)))))
                            season-ids))
                  team-seasons))
    ;; ok! now we have a huge list of player data that we can finally turn into a dataset. tablecloth knows what to do with a list of maps and does what you would expect, making the map keys into the column headers and the values of each map a row
    tc/dataset
    ;; if you inspect the data a bit at this point, you'll see some of the columns are internationalised. For most columns, this just makes it look like the values are wrapped in an unnecessary map under the "default" key. The reason is that some of the spellings are different in different locales. We'll update all of the relevant columns to select the default one for our purposes.
    (tc/update-columns ["firstName" "lastName" "birthCity" "birthStateProvince"]
                       (fn [col]
                         (map (fn [{:strs [default]}]
                                default)
                              col)))
    ;; now we have our rosters dataset! we'll save it to a file and use that file from here on out to save re-fetching all of this data
    (tc/write-csv! "data/year_2024/week_2/nhl-rosters.csv"))

;; lastly, we'll filter down this list to unique players, since many appear multiple times on different team rosters over the years. We'll save this as a separate dataset, after extracting the birth month and year from the birth dates column
(-> "data/year_2024/week_2/nhl-rosters.csv"
    (tc/dataset {:key-fn kebab-cased-keyword})
    (tc/unique-by [:id :first-name :last-name :birth-date :birth-country :birth-state-province])
    (tc/map-columns :birth-year [:birth-date] #(.getYear %))
    (tc/map-columns :birth-month [:birth-date] #(.getMonth %))
    (tc/write-csv! "data/year_2024/week_2/nhl-player-births.csv"))

;; Canadian Births

;; We'll fetch all of the births in Canada from the statscan website. The data is available as a gzipped csv, which tablecloth knows what to do with. I'm cleaning up the headers again on the fly:
(-> "https://www150.statcan.gc.ca/n1/tbl/csv/13100415-eng.zip"
    (tc/dataset {:key-fn (comp kebab-cased-keyword str/lower-case)})
    ;; next we select only the rows that are for monthly births, for all of Canada, and count the number of live births
    (tc/select-rows (fn [row]
                      (and
                       (not (str/includes? (:month-of-birth row) "Total"))
                       (= "Number of live births" (:characteristics row))
                       (str/starts-with? (:geo row) "Canada"))))
    ;; next we convert the month-of-birth strings into a birth month number
    (tc/map-columns :month [:month-of-birth] (fn [birth-month]
                                               (->> birth-month
                                                    (re-find #"Month of birth, (\w+)")
                                                    second
                                                    str/upper-case
                                                    (Month/valueOf)
                                                    .getValue)))
    ;; and lastly, select just the year, month, and births columns, updating the number of births to be integer values and renaming the columns to something more meaningful:
    (tc/select-columns [:ref-date :month :value])
    (tc/update-columns :value (partial map int))
    (tc/rename-columns {:ref-date :years
                        :value :births})
    ;; we'll save this dataset as `canada_births_1991_2022`
    (tc/write-csv! "data/year_2024/week_2/canada_births_1991_2022.csv"))
