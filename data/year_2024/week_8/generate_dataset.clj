(ns year-2024.week-8.generate-dataset
  (:require
   [clojure.string :as str]
   [hickory.core :as hc]
   [hickory.select :as hs]
   [tablecloth.api :as tc]))

(def base-url "https://www.r-consortium.org")
(def grants-url "https://www.r-consortium.org/all-projects/awarded-projects")

(defonce cached-fetch (memoize slurp))

(def grants-page (cached-fetch grants-url))

(def urls
  (->> grants-page
       hc/parse
       hc/as-hickory
       (hs/select (hs/descendant (hs/class "main-content")
                                 (hs/tag "a")))
       (map (comp :href :attrs))
       (filter #(str/starts-with? % "/projects/awarded-projects/"))
       (map #(str/replace % #"#.+$" ""))
       distinct
       (map (partial str base-url))))

(defn- parse-project-description [project-html]
  (->> project-html
       (hs/select (hs/tag "p"))
       (map (fn [p]
              [(some-> p
                       (get-in [:content 0 :content 0])
                       (str/replace #":" "")
                       (str/replace #" " "_")
                       str/lower-case)
               (as-> (get-in p [:content 2]) content
                 (if (string? content)
                   (str/trim content)
                   (some-> content :content first)))]))
       (remove (comp nil? first))
       (into {})))

(defn get-grant-data [url]
  (let [[_ year group] (re-find #".+(\d{4})-group-(\d$)" url)
        project-description-html (->> url
                                      cached-fetch
                                      hc/parse
                                      hc/as-hickory
                                      (hs/select (hs/class "project-description")))]
    (->> project-description-html
         (map (fn [project]
                (merge
                 {"year" year
                  "group" group
                  "title" (->> project
                               (hs/select (hs/descendant (hs/tag "h3") (hs/tag "a")))
                               first :content first)}
                 (parse-project-description project)))))))

(-> (mapcat get-grant-data urls)
     tc/dataset
     (tc/update-columns {"funded" (fn [col]
                                    (map #(-> %
                                              (str/replace #"\$|," "")
                                              parse-long)
                                         col))})
     (tc/write-csv! "data/year_2024/week_8/isc-grants.csv"))
