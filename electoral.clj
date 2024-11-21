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
   :params [{:name "year" :value "2020" :bind {:input "range" :min 2000 :max 2024 :step 4}}
            ;; TODO needs a better name. Clamp?
            {:name "winners" :value false :bind {:input "checkbox" }}
            {:name "brush_scatter"
             :select {:type "interval" :encodings ["x" "y"]}
             :views ["scatter"]
             }
            {:name "brush_map"
             :select {:type "interval" :encodings ["longitude" "latitude"]}
             :views ["map"]
             }
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
    {:calculate "geoCentroid(null,datum.geo)" :as "centroid"}
    ]
   ;; :resolve {:scale {:color "independent"}}
   :vconcat [
             {:hconcat [

                        ;; map colored by %dem
                        {
                         :projection {:type "albersUsa"
                                      :precision 0.8 ;Work around Vega bug https://github.com/vega/vega-lite/issues/9321
                                      }
                         :width 750
                         :height 450
                         :layer [{:mark {:type "geoshape"}
                                  :encoding {:shape {:field "geo" :type "geojson"}
                                             :color {:field "dempc" 
                                                     :title "% dem"
                                                     :type "quantitative"
                                                     :scale {:domain [0 100]
                                                             :range ["#DD1327" "#DDCAE0" "#1750E0" ]
                                                             }}
                                             :stroke {:value "gray"
                                                      :condition {:test {:or [{:param "brush_map"} {:param "brush_scatter"}]}
                                                                  :value "orange"
                                                                  :empty false}
                                                      }
                                             :strokeWidth {:value 0.5
                                                           :condition {:test {:or [{:param "brush_map"} {:param "brush_scatter"}]}
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
                                 {:name "map"
                                  :mark {:type :circle :opacity 0}
                                  :encoding {:latitude {:field "centroid[1]" :type :quantitative}
                                             :longitude {:field "centroid[0]" :type :quantitative}
                                             }}
                                 ]

                         }

                        ;; Density/%dem scatterplot
                        {:mark {:type "circle" :filled true}
                         :name "scatter"
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
                                    :stroke {:value "gray"
                                             :condition {:test {:or [{:param "brush_map"} {:param "brush_scatter"}]}
                                                         :value "orange"
                                                         :empty false}                                             
                                             }
                                    :strokeWidth {:value 0.5
                                                  :condition {:test {:or [{:param "brush_map"} {:param "brush_scatter"}]}
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
             #_
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


;;; Fixes bug in u/ version and changes defaults
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
  (spit "/opt/mt/repos/electoral/docs/index.html"
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

;;; Ex
#_{:county_fips 20083,
  :totalvotes 1088,
  :candidatevotes 217,
  :county_name "HODGEMAN",
  :state_po "KS",
  :year 2000,
  :population 1737,
  :area 859.992}
(def all-years (mapcat year-data years))



;;; → multitool!
(defn select-by
  [seq prop val]
  (u/some-thing #(= (prop %) val) seq))

(defn infer-from
  [ds source-ds & {:keys [by infer]}]
  (let [indexed (u/index-by by source-ds)] ;TODO memoize
    (prn (get indexed ))
    (for [m ds]
      (merge m
             (select-keys (get indexed (by m)) infer)))))

;;; Scrape

;;; {"39039" {:totalVote 18886, "gop" 13098, "dem" 5602, "lib" 89, "ind" 55, "other" 3}...}
{:county_fips 20083,
  :totalvotes 1088,
  :candidatevotes 217,
  :county_name "HODGEMAN",
  :state_po "KS",
  :year 2000,
  :population 1737,
  :area 859.992}

;;; Argh, some files use county name instead of fip as keyword, just to make my life hell
(def state-abbrev
  (->> (voracious.formats.json/read-file "/opt/mt/repos/electoral/scrape/states.json")
      (map #(update % :name (fn [n] (-> n
                                        str/lower-case
                                        (str/replace " " "-")))))
      (u/index-by :name)))

;;; {["VA" "PRINCE WILLIAM"] {:county_fips 51153. ...}
(def fips-index
  (u/index-by (juxt :state_po :county_name)
              all-data))

(defn lookup-fips
  [state-name county-name]
  (get-in fips-index [[(get-in state-abbrev [state-name :abbreviation])
                       (str/upper-case county-name)]
                      :county_fips]))

(defn data-2024-state
  [state-name]
  (prn :s state-name)
  (as-> state-name state
    (format "/opt/mt/repos/electoral/scrape/%s.json" state)
    (voracious.formats.json/read-file state)
    (get-in state [:races 0 :mapData])
    (map (fn [[k d]]
           {:county_fips (or (u/coerce-numeric-hard (name k))
                             (lookup-fips state-name (name k)))
            :totalvotes (:totalVote d)
            :year 2024
            :candidatevotes (:votes (select-by (:candidates d) :party "dem"))}) ;provisional
         state)
    (filter #(number? (:county_fips %)) state) ;TODO some strings appearing, not sure where or why, throw it out
    (infer-from state all-years :by :county_fips :infer [:county_name :state_po :population :area])))

(def states (ju/file-lines "/opt/mt/repos/electoral/scrape/states.txt"))
(def data-2024 (mapcat data-2024-state states))

(def all-data-plus (concat all-years data-2024))

(defn write-all-years
  []
  (csv/write-csv-file-maps
   "/opt/mt/repos/electoral/docs/data/counties.csv"
   all-data-plus))

;;; County count

(def x (group-by :state_po all-data))
(def x2024 (group-by :state_po data-2024))
(u/map-values (fn [vs] (count (distinct (map :county_fips vs)))) x)
{"WI" 72,
 "SC" 46,
 "MN" 87,
 "NV" 17,
 "NM" 33,
 "NE" 93,
 "AK" 41,
 "NH" 10,
 "ME" 16,
 "NY" 62,
 "TN" 95,
 "FL" 67,
 "IA" 99,
 "GA" 159,
 "IL" 102,
 "RI" 6,
 "VA" 134,
 "MI" 83,
 "PA" 67,
 "UT" 29,
 "WY" 23,
 "SD" 67,
 "MO" 116,
 "KY" 120,
 "CT" 9,
 "AR" 75,
 "ID" 44,
 "DC" 1,
 "MA" 14,
 "OK" 77,
 "AL" 67,
 "VT" 14,
 "MS" 82,
 "CA" 58,
 "LA" 64,
 "DE" 3,
 "WA" 39,
 "KS" 105,
 "MD" 24,
 "ND" 53,
 "TX" 254,
 "OR" 36,
 "NC" 100,
 "AZ" 15,
 "IN" 92,
 "WV" 55,
 "CO" 64,
 "HI" 4,
 "MT" 56,
 "NJ" 21,
 "OH" 88}


;;; [all 24] – filterd by difference

([nil 41]
 ["AK" 41]
 ["NH" [10 6]]
 ["ME" [16 10]]
 ["RI" [6 3]]
 ["VA" [134 133]]
 ["SD" [67 66]]
 ["MO" [116 115]]
 ["CT" [9 5]]
 ["DC" 1]
 ["MA" [14 8]]
 ["VT" [14 10]])

;;; RI and other NE states seem to have data organized by city and needs to be rejiggered


(def +& (u/vectorize +))


(defn rejigger
  [state-name]
  (let [raw (as-> state-name state
              (format "/opt/mt/repos/electoral/scrape/%s.json" state)
              (voracious.formats.json/read-file state))
        mapdata (get-in raw [:races 0 :mapData])
        city->county (fn [city-name]
                       (prn :city city-name)
                       (-> (u/walk-collect (fn [x]
                                             (when (= (or (:name x) (:NAME x))
                                                      (str/replace (str/upper-case city-name) "-" " ")) x))
                                           raw)
                           first
                           :COUNTY))]
        ;; Getting punchy and can't think of good var names
        (as-> (group-by (comp city->county name :name) (vals (u/self-label :name mapdata))) blah
          ;; map of county names to seq of city element
          (do (prn :poop (count blah) (first blah)))
          (u/map-values (fn [cities] (reduce +& (map (fn [city]
                                                       (prn :city city)
                                                       [(:totalVote city) (:votes (select-by (:candidates city) :party "dem"))])
                                                     cities)))
                        blah)
          (do (prn :blah blah) blah)
          (map (fn [[k [total dem]]]
                 {:county_fips (lookup-fips state-name k) ;TODO case issues
                  :totalvotes total
                  :year 2024
                  :candidatevotes dem})
               blah)
          (infer-from blah all-years :by :county_fips :infer [:county_name :state_po :population :area]))))
        
;;; To rejigger

; Broken in some other way: "AK" "DC"
(def to-rejigger #{"NH" "ME" "RI" "CT" "MA" "VT"})

(def state-name (u/index-by :abbreviation (vals state-abbrev)))
  
(def rejiggered
  (concat (remove #(contains? to-rejigger (:state_po %))  data-2024 )
          (map (comp rejigger :name state-name) to-rejigger)))

(def all-data-plus (concat all-years rejiggered))

(defn write-all-years
  []
  (csv/write-csv-file-maps
   "/opt/mt/repos/electoral/docs/data/counties.csv"
   all-data-plus))  
