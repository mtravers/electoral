(ns voracious.projects.electoral
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [org.candelbio.multitool.core :as u]
            [org.candelbio.multitool.cljcore :as ju]
            [voracious.formats.csv :as csv]
            ))

(def spec
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json",
   :width 750,
   :height 450,
   :data {:url  "data/counties.csv",},
   :params [{:name "year" :value "2020" :bind {:input "range" :min 2000 :max 2020 :step 4}}
            ;; TODO needs a better name. Clamp?
            {:name "winners" :value false :bind {:input "checkbox" }}
            ]
   :transform
   [
    {:filter {:param "year"}}
    {:lookup "county_fips",
     :from {:data {:url "data/us-10m.json"  :format {:type "topojson", :feature "counties"}},
            :key "id"}
     :as "geo"}
    {:calculate "(datum.candidatevotes / datum.totalvotes)" :as "demf"}
    {:calculate "winners ? (datum.demf > 0.5 ? 0.9 : 0.1) : datum.demf" :as "demft"}
    ],
   :projection {:type "albersUsa"},
   :layer [{:mark {:type "geoshape", :tooltip {:content "data"}}
            :encoding {:shape {:field "geo" :type "geojson"}
                       :color {:field "demft" 
                               :type "quantitative"
                               :scale {:scheme "redblue"
                                       :domain [0,1]}}
                       }
            }
           ;; This is too slow
           #_
           {:mark {:type "geoshape", :tooltip {:content "data"}}
            :transform [{:filter "winners"}]
            :encoding {:shape {:field "geo" :type "geojson"}
                       :color {:field "demft" 
                               :type "nominal"
                               :scale {:range ["red", "blue"]}
                               }}
            }

           ]
   })

(def raw-data (csv/read-csv-file-maps "/opt/mt/repos/electoral/data/countypres_2000-2020.csv"))

(defn year-data
  [year]
  (as-> raw-data d
    (filter #(= year (:year %)) d)
    (filter #(= "DEMOCRAT" (:party %)) d)
    (group-by :county_fips d)
    (vals d)
    (map (fn [group] (assoc (first group) :candidatevotes (reduce + (map :candidatevotes group)))) d)
    (map #(select-keys % [:county_fips :totalvotes :candidatevotes
                          :county_name :state_po
                          :year
                          ]) d)))

#_
(defn write-one-year
  [year]
  (csv/write-csv-file-maps "/opt/mt/repos/electoral/data/counties2020.csv" (year-data year)))

(def years (range 2000 2024 4))

(defn write-all-years
  []
  (csv/write-csv-file-maps
   "/opt/mt/repos/electoral/data/counties.csv"
   (mapcat year-data years)))

(defn expand-template
  "Template is a string containing {foo} elements, which get replaced by corresponding values from bindings. See tests for examples."
  [template bindings & {:keys [param-regex key-fn] :or {key-fn keyword param-regex u/double-braces}}]
  (let [matches (->> (re-seq param-regex template) 
                     (map (fn [[match key]]
                            [match (or (bindings (key-fn key)) "")])))]
    (reduce (fn [s [match repl]]
              (str/replace s (u/re-pattern-literal match) (java.util.regex.Matcher/quoteReplacement repl)))
            template matches)))

(defn gen
  []
  #_ (spit "/opt/mt/repos/electoral/spec.json" (json/write-str spec))
  (spit "/opt/mt/repos/electoral/electoral.html"
   (expand-template
   (slurp "/opt/mt/repos/electoral/electoral.html.template")
   {:spec (json/write-str spec)}
   :key-fn keyword
   )))



