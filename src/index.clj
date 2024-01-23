^:kindly/hide-code
(ns index
  (:require
   [scicloj.kindly.v4.kind :as kind]))

^:kindly/hide-code
(kind/md
 "# Welcome to Clojure Tidy Tuesdays!

This is a collection of #TidyTuesday explorations in Clojure. Tidy Tuesdays are an initiative of the R for data science online learning community, and as a Clojure enthusiast, I'm publishing implementations of the data-generation scripts and data explorations in Clojure here in 2024. Follow along to learn all about Clojure's rich data ecosystem!

You can find more code showing examples and explanations about how to download and wrangle data in the data fetching scripts in [the data folder of this project's repo](https://github.com/kiramclean/clojure-tidy-tuesdays/tree/main/data/). They're not published here as namespaces because many make lots of API calls and/or don't display nicely as published notebooks.

## Goals

Some goals for this project this year are:

- To increase the number of non-trivial examples and guides on how to work with Clojure's emerging data science stack
- To work out bugs and rough edges in these tools and libraries to help develop them for more professional use
- To learn how to use them better myself, as part of writing the [Clojure Data Workbook](https://github.com/scicloj/clojure-data-cookbook/)

## Support

This work is made possible by the ongoing funding I receive from [Clojurists together](https://www.clojuriststogether.org/) and my generous [Github Sponsors](https://github.com/sponsors/kiramclean). If you find this work valuable, please consider contributing financially to it's sustainability:

")

^:kindly/hide-code
(kind/html
 "<iframe src= \"https://github.com/sponsors/kiramclean/card\" title=\"Sponsor kiramclean\" height= \"225\" width=\"600\" style=\"border: 0;\" ></iframe>")
