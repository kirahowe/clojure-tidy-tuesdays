(ns year-2024.week-2.generate-dataset
  )

(def teams
  (-> "https://api.nhle.com/stats/rest/en/team"
      tc/dataset))
