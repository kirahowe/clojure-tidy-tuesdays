(ns year-2024.week-3.generate-dataset
  (:require
   [charred.api :as charred]
   [clj-http.client :as client]
   [tablecloth.api :as tc]))

;; # Week 3 Data - US Polling Places 2012-2020

;; The data this week is about US polling stations. Not all states have good data (apparently there are several where voting happens primarily by mail), but for the ones that do have data on multiple elections, we'll try to answer the question: How have polling location counts per county changed over time?
;;
;; This code is mostly a "translation" into Clojure of the [cleaning script](https://github.com/rfordatascience/tidytuesday/tree/master/data/2024/2024-01-16#cleaning-script) from the R4DS tidy tuesday repo.
;;
;; First we get the list of states that have data. We have to authenticate with the github API, for which you'll need a [GitHub API token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens). R has a package that facilitates access to the github API, for us we can make a little helper function to call github's API with our token:

(defn call-gh-api! [endpoint]
  (client/get (str"https://api.github.com/" endpoint)
              {:oauth-token (System/getenv "GITHUB_API_TOKEN")}))

;; I'll assign the results of the initial API call to avoid fetching the data too many times:

(defonce polling-places
  (call-gh-api! "/repos/PublicI/us-polling-places/contents/data"))

;; Now we can parse the data, again using charred:

(-> polling-places
    :body
    (charred/read-json {:key-fn keyword}))

;; Inspecting these, we can see it looks like a collection of links for each state that has polling data:

(-> polling-places
    :body
    (charred/read-json {:key-fn keyword})
    first)

;; I'll assign the parsed result so we don't have to keep copying the above lines:

(def parsed-polling-places
  (-> polling-places
      :body
      (charred/read-json {:key-fn keyword})))

;; So we'll collect the URLs for each state. Again I'll assign these results so we're not wasting API calls. Remember that Clojure is lazy, so the API calls won't actually happen until the first time we reference `state-data` (which will take a few seconds since it's making 39 requests to github's API).

(defonce state-url-data
  (->> parsed-polling-places
       (map :name)
       (map #(str "/repos/PublicI/us-polling-places/contents/data/" % "/output"))
       (map call-gh-api!)))

;; We'll parse the resulting state data and assign it:

(def parsed-state-urls
  (->> state-url-data
       (map :body)
       (mapcat #(charred/read-json % {:key-fn keyword}))))

;; Now we'll collect the download URLs for each state's data and call each one to fetch the data. Starting now (since we're working with CSVs and not json APIs anymore), we can move into tablecloth-land. Since this is the step that calls 39 CSV endpoints, I'll assign the results here, too, to avoid slowing things down if we re-load this namespace:

(def state-data
  (->> parsed-state-urls
       (map :download_url)
       ;; grab all the data at each URL and put it into a tablecloth dataset
       (map #(tc/dataset % {:key-fn keyword}))))

;; Since all the datasets have the same number and names of columns, we can concat them all together to make one big dataset

(def all-state-data
  (->> state-data
       (apply tc/concat)
     ;; we'll de-dupe the rows since there are some duplicates,
     ;; with no arguments, tablecloth's unique-by just takes unique rows:
       tc/unique-by))

(tc/write-csv! all-state-data "data/year_2024/week_3/polling-places.csv")
