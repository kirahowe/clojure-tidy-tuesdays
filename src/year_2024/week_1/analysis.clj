(ns year-2024.week-1.analysis
  (:require [tablecloth.api :as tc]))

;; # Holiday Movies

;; In the interest of just getting started, this week I'll look at [a recent dataset from the 2023 tidy tuesday repo about holiday movies](https://github.com/rfordatascience/tidytuesday/tree/master/data/2023/2023-12-12), published by the R4DS community.
;; For posterity, I've also saved the data in this repo, where it's available in the `data/year_2024/week_1/` folder.

;; For details about how the dataset was generated, see `data/year_2024/week_1/generate_dataset.clj`.

;; To get started, we'll load the data into our notebook, starting with the dataset of holiday movies:

(def holiday-movies
  (tc/dataset "data/week-1/holiday-movies"))
