(set-env!
 :source-paths   #{"src"}
 :dependencies '[[org.clojure/clojure "1.8.0"     :scope "provided"]
                 [adzerk/boot-reload    "0.4.4"      :scope "test"]
                 [org.clojure/tools.nrepl "0.2.12"]

                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [base64-clj "0.1.1"]
                 [cheshire "5.5.0"]])

(require
 '[adzerk.boot-reload    :refer [reload]])

(def +version+ "0.0.1-SNAPSHOT")

(task-options!
 pom  {:project     'yenda/burp-clj
       :version     +version+
       :description "Takes a burp dump and format/display it in various formats"
       :url         "https://github.com/yenda/burp-clj"
       :scm         {:url "https://github.com/yenda" }
       :license     {"MIT" "https://opensource.org/licenses/MIT"}}
 aot {:all true}
 jar {:main 'burp_clj.core :file "burp-clj.jar"})

(deftask uberjar
  []
  (comp
   (aot)
   (pom)
   (uber)
   (jar)
   (target)))
