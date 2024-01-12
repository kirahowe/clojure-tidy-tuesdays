(ns user)

(defn help []
  (println "Welcome to Clojure Tidy Tuesdays!")
  (println)
  (println "Available commands are:")
  (println)
  (println "(build)      ;; build all of the namespaces in the project into a website using quarto, note this will be very slow the first time as it hasn't cached any of the very large datasets that get downloaded and used")
  )

(defn dev
  "Load and switch to the 'dev' namespace."
  []
  (require 'dev)
  (help)
  (in-ns 'dev)
  :loaded)
