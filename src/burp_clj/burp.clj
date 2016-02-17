(ns burp-clj.burp
  (:require [clojure.xml :as c-xml]
            [clojure.zip :refer [xml-zip node]]
            [clojure.data.zip.xml :refer [xml-> xml1-> attr attr=]]
            [clojure.data.zip :as cdz]
            [cheshire.core :as json]
            [base64-clj.core :refer [decode]]
            [clojure.string :as str]))

(defn text
  "Returns the textual contents of the given location, similar to
  xpaths's value-of"
  [loc]
  (apply str (xml-> loc cdz/descendants node string?)))

;;;
;;; MESSAGES
;;;

(defn beautify-content
  [header content]
  (if (str/includes? header "Content-Type: application/json")
    (-> content
        json/parse-string
        (json/generate-string {:pretty true}))
    content))

(defn keep-begining-and-end
  [e]
  (str (str/join (take 4 e))
       "..."
       (str/join (take-last 5 e))))

(defn remove-cookie-value
  [cookie]
  (let [[name value] (str/split cookie #"=")]
    (str name
         "="
         (keep-begining-and-end value))))

(defn anonymize-cookies
  [cookies]
  (let [v (str/split cookies #" ")
        header (first v)]
    (->> (drop 1 v) ; drop the header
         (map remove-cookie-value)
         (str/join #" ")
         (str header " "))))

(defn anonymize-set-cookies
  [set-cookies]
  (-> set-cookies
      (str/split #" ")
      (update 1 remove-cookie-value)
      (#(str/join #" " %))))

(defn anonymize-basic
  [basic]
  (let [v (-> basic
              (str/split #" ")
              (update 2 keep-begining-and-end))]
    (str/join #" " v)))

(defn beautify
  [message]
  (let [[header content] (str/split message #"\r\n\r\n")
        content          (when content (beautify-content header content))]
    (str header "\r\n\r\n" content)))

(defn anonymize
  [message]
  (-> message
      (str/replace #"Cookie:[^\n]*"               anonymize-cookies)
      (str/replace #"Set-Cookie:[^\n]*"           anonymize-set-cookies)
      (str/replace #"Authorization: Basic [^\n]*" anonymize-basic)))

(defn transform
  [message base64]
  (->> message
       (#(if base64 (decode %) %))
       anonymize
       beautify))

;;;
;;; REQUEST-RESPONSE
;;;

(defn cleanup-request-response
  [request-response]
  (let [{:keys [request response base64]} request-response]
    {:request  (transform request  base64)
     :response (transform response base64)}))

(defn request-response->map
  [request-response]
  (let [z      (xml-zip request-response)]
    {:base64   (Boolean/valueOf (xml1-> z :request (attr :base64)))
     :request  (xml1-> z :request  text)
     :response (xml1-> z :response text)}))

;;;
;;; BURP FILE
;;;

(defn burp-xml->map
  [burp-xml]
  (map (comp cleanup-request-response
             request-response->map)
       burp-xml))

(def separator (str "\n" (apply str (take 50 (repeat "="))) "\n"))

(defn request-response-map->str
  [request-response-map]
  (let [{:keys [request response]} request-response-map]
    (str request separator response separator)))

(defn burp-xml->str
  [burp-xml]
  (->> burp-xml
       burp-xml->map
       (map request-response-map->str)
       (interleave (repeat separator))
       (str/join)))

(defn parse-burp-xml
  [filename]
  (->> filename
       clojure.java.io/file
       c-xml/parse
       :content))

(defn burp->str
  [filename]
  (-> filename
      parse-burp-xml
      burp-xml->str))

(defn burp->txt
  [filename]
  (->> filename
       burp->str
       (spit (str filename ".txt"))))
