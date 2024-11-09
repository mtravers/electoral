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
   :data {:url "data/us-10m.json", :format {:type "topojson", :feature "counties"}},
   :transform
   [#_ {:filter "datum.year == '2020' && datum.party == 'DEMOCRAT'"}
    {:lookup "id", :from {:data {:url "data/counties2020.csv"}, :key "county_fips", :fields ["candidatevotes" "totalvotes"]}}
    {:calculate  "(datum.candidatevotes / datum.totalvotes) - 0.5" :as "demf"}
    ],
   :projection {:type "albersUsa"},
   :mark {:type "geoshape", :tooltip {:content "data"}}
   :encoding {:color {:field "demf"
                      :type "quantitative"
                      :scale {:scheme "redblue"}}
              }})

(def raw-data (csv/read-csv-file-maps "/opt/mt/repos/electoral/data/countypres_2000-2020.csv"))

(defn year-data
  [year]
  (as-> raw-data d
    (filter #(= year (:year %)) d)
    (filter #(= "DEMOCRAT" (:party %)) d)
    (group-by :county_fips d)
    (vals d)
    (map (fn [group] (assoc (first group) :candidatevotes (reduce + (map :candidatevotes group)))) d)
    (map #(select-keys % [:county_fips :totalvotes :candidatevotes]) d)))

(csv/write-csv-file-maps "/opt/mt/repos/electoral/data/counties2020.csv" (year-data 2020))




(defn expand-template
  "Template is a string containing {foo} elements, which get replaced by corresponding values from bindings. See tests for examples."
  [template bindings & {:keys [param-regex key-fn] :or {key-fn keyword param-regex u/double-braces}}]
  (let [matches (->> (re-seq param-regex template) 
                     (map (fn [[match key]]
                            [match (or (bindings (key-fn key)) "")])))]
    (reduce (fn [s [match repl]]
              (prn :xpnd match repl)
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



