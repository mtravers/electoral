(ns electoral.electoral
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [org.candelbio.multitool.core :as u]
            ))

;;; ⊥⊥⊤⊤ Vega spec ⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤

(def brush-color "limegreen")

;;; So verbose and weird I'm abstracting it
(def brush-test
  '{:and [{:or [{:param "brush_map" :empty false}
                {:param "brush_scatter" :empty false}]}
          {:or [{:param "brush_map"} {:param "brush_scatter"}]}]})

(def spec
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :data {:url "data/counties.csv" :name "counties"}
   :background "#f8f8f8"
   :params [{:name "year" :value "2020" :bind {:input "range" :min 2000 :max 2020 :step 4}}
            ;; TODO needs a better name. Clamp?
            {:name "winners" :value false :bind {:input "checkbox" }}
            {:name "brush_scatter"
             :select {:type "interval" :encodings ["x" "y"]}
             :views ["scatter"]}
            {:name "brush_map"
             :select {:type "interval" :encodings ["longitude" "latitude"]}
             :views ["map"]}
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
   ;; Might be needed or useful for experimental views
   ;; :resolve {:scale {:color "independent"}}
   :vconcat
   [{:hconcat
     [
      ;; Map colored by %dem
      {:projection {:type "albersUsa"
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
                                    :condition {:test brush-test
                                                :value brush-color
                                                :empty false}
                                    }
                           :strokeWidth {:value 0.5
                                         :condition {:test brush-test
                                                     :value 3
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

      ;; Scatterplot Density/%dem 
      {:mark {:type "circle" :filled true}
       :name "scatter"
       :background "lightgray" ;doesn't work!
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
                           :condition {:test brush-test
                                       :value brush-color
                                       ;; :empty false
                                       }                                             
                           }
                  :strokeWidth {:value 0.5
                                :condition {:test brush-test
                                            ;; :empty false
                                            :value 3}}
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

    ;; Dev only

    #_
    {:hconcat
     [
      ;; Density

      ;; Fucks up color mapping somehow

      {:mark {:type "geoshape", :tooltip {:content "data"}}
       :projection {:type "albersUsa"
                    :precision 0.81
                    }
       :width 750
       :height 450
       :encoding {:shape {:field "geo" :type "geojson"}
                  :fill {:field "density" 
                          :type "quantitative"
                          :scale {:type :log
                                  ;; :domain {:data "counties" :field "density"}
                                  ;; :range  {:scheme "greens"}
                                  }}}}


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
                                 :condition {:test brush-test
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
       }]}
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

;;; Generates the static page content from templates
(defn gen
  []
  (spit "/opt/mt/repos/electoral/docs/index.html"
        (expand-template
         (slurp "/opt/mt/repos/electoral/electoral.html.template")
         {:spec (json/write-str spec)}
         :key-fn keyword))
  (spit "/opt/mt/repos/electoral/spec.json"
        (with-out-str (json/pprint spec))))

