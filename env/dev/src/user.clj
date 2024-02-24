(ns user)

(defn help []
  (println "Welcome to Clojure Tidy Tuesdays!")
  (println)
  (println "Available commands are:")
  (println)
  (println "(build)      ;; build all of the namespaces in the project into a website using quarto"))

(defn dev
  "Load and switch to the 'dev' namespace."
  []
  (require 'dev)
  (help)
  (in-ns 'dev)
  :loaded)
