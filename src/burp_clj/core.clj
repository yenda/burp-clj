(ns burp-clj.core
  (:gen-class)
  [:require [burp-clj.burp :refer [burp->txt]]])

(defn -main
  [filename & args]
  (println "Beautiful burp is now converting your file...")
  (burp->txt filename)
  (println "Conversion done"))

