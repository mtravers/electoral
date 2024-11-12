(ns voracious.projects.electoral
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [org.candelbio.multitool.core :as u]
            [org.candelbio.multitool.cljcore :as ju]
            [voracious.formats.csv :as csv]
            ))

;;; ⊥⊥⊤⊤ Vega spec ⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤

(def spec
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"

   :data {:url "data/counties.csv"}
   :params [{:name "year" :value "2020" :bind {:input "range" :min 2000 :max 2020 :step 4}}
            ;; TODO needs a better name. Clamp?
            {:name "winners" :value false :bind {:input "checkbox" }}
            {:name "brush" :select "interval" :encodings ["x" "y"]}
            ]
   :transform
   [{:filter "datum.year == year"}
    {:lookup "county_fips"
     :from {:data {:url "data/us-10m.json"  :format {:type "topojson" :feature "counties"}}
            :key "id"}
     :as "geo"}
    {:calculate "100 * (datum.candidatevotes / datum.totalvotes)" :as "demp"}
    {:calculate "winners ? (datum.demp > 50 ? 85 : 15) : datum.demp" :as "dempc"}
    {:calculate "datum.population / datum.area" :as "density"}
    ]
   ;; :resolve {:scale {:color "independent"}}
   :vconcat [
             {:hconcat [

                        ;; map colored by %dem
                        {:mark {:type "geoshape"}
                         :projection {:type "albersUsa"
                                      :precision 0.8 ;Work around Vega bug https://github.com/vega/vega-lite/issues/9321
                                      }
                         :width 750
                         :height 450
                         :encoding {:shape {:field "geo" :type "geojson"}
                                    :color {:field "dempc" 
                                            :title "% dem"
                                            :type "quantitative"
                                            :scale {:domain [0 100]
                                                    :range ["#DD1327" "#DDCAE0" "#1750E0" ]
                                                    }}
                                    :stroke {:value "gray"
                                             :condition {:param "brush" ;This does NOT work, no idea why
                                                         :value "orange"
                                                         :empty false}
                                             }

                                    :strokeWidth {:value 0.5
                                                  :condition {:param "brush" ;This does NOT work, no idea why
                                                              :value 12 
                                                              :empty false}}
                                    :strokeOpacity {:value 0.5}
                                    :tooltip [{:field :county_name :title "county"}
                                              {:field :state_po :title "state"}
                                              {:field :population :type :quantitative}
                                              {:field :density :type :quantitative}
                                              {:field :dempc :type :quantitative :title "% dem"}
                                              ]
                                    }
                         }

                        ;; Density/%dem scatterplot
                        {:mark {:type "circle" :filled true}
                         :height 400 :width 600
                         :encoding {:x {:field "density"
                                        :type :quantitative
                                        :axis {:grid false}

                                        :scale {:type :log}}
                                    :y {:field "demp"
                                        :type :quantitative
                                        :axis {:grid false}
                                        }
                                    :size {:field "population"
                                           :type :quantitative
                                           :scale {:range [15, 800]}}
                                    :stroke {:value "gray"}
                                    :strokeWidth {:value 0.5
                                                  :condition {:param "brush"
                                                              :empty false
                                                              :value 12}}
                                    :strokeOpacity {:value 0.5}
                                    :color {:field "dempc" 
                                            :title "% dem"
                                            :type "quantitative"
                                            :scale {:domain [0 100]}}
                                    :tooltip [{:field :county_name :title "county"}
                                              {:field :state_po :title "state"}
                                              {:field :population :type :quantitative}
                                              {:field :density :type :quantitative}
                                              {:field :dempc :type :quantitative :title "% dem"}
                                              ]
                                    }}
                        ]}

             ;; Density
             #_
             {:mark {:type "geoshape", :tooltip {:content "data"}}
              :projection {:type "albersUsa"
                           :precision 0.81
                           }
              :width 750
              :height 450
              :encoding {:shape {:field "geo" :type "geojson"}
                         :color {:field "density" 
                                 :type "quantitative"
                                 :scale {:type :log}
                                 }}
              }

             ;; Just for testing – brushing works fine here
             {:mark {:type "circle"}
              :width 750
              :height 450
              :encoding  {:x {:field "population"
                              :type :quantitative
                              :scale {:type :log}
                              }
                          :y {:field "area"
                              :type :quantitative
                              :scale {:type :log}
                              }
                          :stroke {:value "gray"}
                          :strokeWidth {:value 0.5
                                        :condition {:param "brush"
                                                    :empty false
                                                    :value 12}}
                          :strokeOpacity {:value 0.5}
                          :color {:field "dempc" 
                                  :title "% dem"
                                  :type "quantitative"
                                  :scale {:domain [0 100]}}
                          :tooltip [{:field :county_name :title "county"}
                                    {:field :state_po :title "state"}
                                    {:field :population :type :quantitative}
                                    {:field :density :type :quantitative}
                                    {:field :dempc :type :quantitative :title "% dem"}
                                    ]
                          }
              }


             ;; This is too slow, use single layer and switch the data
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

(defn gen
  []
  (spit "/opt/mt/repos/electoral/electoral.html"
   (expand-template
   (slurp "/opt/mt/repos/electoral/electoral.html.template")
   {:spec (json/write-str spec)}
   :key-fn keyword))
  (spit "/opt/mt/repos/electoral/spec.json"
        (with-out-str (json/pprint spec))))

;;; ⊥⊥⊤⊤ Data prep ⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤

(def raw-data (csv/read-csv-file-maps "/opt/mt/repos/electoral/data/countypres_2000-2020.csv"))

(def pop-data (->> "/opt/mt/repos/electoral/data/co-est2023-alldata.csv"
                   csv/read-csv-file-maps
                   (map #(assoc % :fips (+ (* (:STATE %) 1000) (:COUNTY %))))
                   (map #(assoc % :population (:POPESTIMATE2020 %)))
                   (map #(select-keys % [:fips :population]))
                   (u/index-by :fips)))


(def area-data (->> (csv/read-csv-file-maps
                     "/opt/mt/repos/electoral/data/county_geo.tsv"
                     :separator \tab)
                    (map #(assoc % :fips (:GEOID %)))
                    (map #(assoc % :area (:ALAND_SQMI %)))
                    (map #(select-keys % [:fips :area]))
                    (u/index-by :fips)))


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
                          ]) d)
    (map #(assoc % :population (get-in pop-data [(:county_fips %) :population]) ) d)
    (map #(assoc % :area (get-in area-data [(:county_fips %) :area]) ) d)))

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





