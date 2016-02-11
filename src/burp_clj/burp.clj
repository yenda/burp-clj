(ns burp-clj.burp
  (:require [clojure.xml :as c-xml]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :refer [xml-> xml1-> text attr attr=]]
            [cheshire.core :as json]
            [base64-clj.core :refer [decode]]))

(def separator (str "\n" (apply str (take 50 (repeat "="))) "\n"))


(defn base64?
  [burp-xml]
  (let [z      (xml-zip (first burp-xml))]
    (xml1->  z :request (attr :base64))))


(defn beautify-content
  [header content]
  (if (clojure.string/includes? header "Content-Type: application/json")
    (-> content
        json/parse-string
        (json/generate-string {:pretty true}))
    content))

(defn beautify
  [element]
  (let [[header content] (clojure.string/split element #"\r\n\r\n")
        content          (when content (beautify-content header content))]
    (str header "\r\n\r\n" content)))

(defn keep-begining-and-end
  [element]
  (str (apply str (take 4 element))
       "..."
       (apply str(take-last 5 element))))

(defn remove-cookie-value
  [cookie]
  (let [[name value] (clojure.string/split cookie #"=")]
    (str name
         "="
         (keep-begining-and-end value))))

(defn anonymize-request-cookies
  [element]
  (let [v (clojure.string/split element #" ")
        header (first v)]
    (->> (drop 1 v) ; drop the header
         (map remove-cookie-value)
         (clojure.string/join #" ")
         (str header " "))))

(defn anonymize-response-cookies
  [element]
  (let [v (-> element
              (clojure.string/split #" ")
              (update 1 remove-cookie-value))]
    (clojure.string/join #" " v)))

(defn anonymize-request
  [request]
  (clojure.string/replace request #"Cookie:[^\n]*" anonymize-request-cookies))

(defn anonymize-response
  [response]
  (clojure.string/replace response #"Set-Cookie:[^\n]*" anonymize-response-cookies))

(defn anonymize-basic-fn
  [element]
  (let [v (-> element
              (clojure.string/split #" ")
              (update 2 keep-begining-and-end))]
    (clojure.string/join #" " v)))

(defn anonymize-basic
  [element]
  (clojure.string/replace element #"Authorization: Basic [^\n]*" anonymize-basic-fn))

(defn request-response->map
  [request-response]
  (let [z      (xml-zip request-response)]
    {:request  (xml1-> z :request  text)
     :response (xml1-> z :response text)}))

(defn transform-request
  [base64]
  (comp beautify
        anonymize-basic
        anonymize-request
        (when base64 decode)))

(defn transform-response
  [base64]
  (comp beautify
        anonymize-basic
        anonymize-response
        (when base64 decode)))

(defn transform-request-response
  [request-response base64]
  (-> request-response
      (update :request  (transform-request base64))
      (update :response (transform-response base64))))

(defn burp-xml->map
  [burp-xml]
  (let [base64 (base64? burp-xml)]
    (->> burp-xml
         (map request-response->map)
         (map #(transform-request-response % base64)))))

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
       (apply str)))

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
