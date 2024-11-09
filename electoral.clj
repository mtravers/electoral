(ns voracious.projects.electoral
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [org.candelbio.multitool.core :as u]
            [org.candelbio.multitool.cljcore :as ju]
            ))

(def spec
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json",
   :width 500,
   :height 300,
   :data {:url "data/us-10m.json", :format {:type "topojson", :feature "counties"}},
   :transform
   [{:lookup "id", :from {:data {:url "data/unemployment.tsv"}, :key "id", :fields ["rate"]}}],
   :projection {:type "albersUsa"},
   :mark "geoshape",
   :encoding {:color {:field "rate", :type "quantitative" :scale {:scheme "redblue"}}}})

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



