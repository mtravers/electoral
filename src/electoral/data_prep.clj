(ns electoral.data-prep
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [org.candelbio.multitool.core :as u]
            [org.candelbio.multitool.cljcore :as ju]
            [voracious.formats.csv :as csv]
            [voracious.formats.json :as vjson]
            ))

;;; Does not work
(ju/cd "/opt/mt/repos/electoral")

;;; ⊥⊥⊤⊤ Data prep ⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤⊥⊥⊤⊤

(def raw-data (csv/read-csv-file-maps "/opt/mt/repos/electoral/docs/data/countypres_2000-2020.csv"))

(def pop-data (->> "/opt/mt/repos/electoral/docs/data/co-est2023-alldata.csv"
                   csv/read-csv-file-maps
                   (map #(assoc % :fips (+ (* (:STATE %) 1000) (:COUNTY %))))
                   (map #(assoc % :population (:POPESTIMATE2020 %)))
                   (map #(select-keys % [:fips :population]))
                   (u/index-by :fips)))


(def area-data (->> (csv/read-csv-file-maps
                     "/opt/mt/repos/electoral/docs/data/county_geo.tsv"
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
    (prn (get indexed (by (first ds))))
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
  (->> (vjson/read-file "/opt/mt/repos/electoral/scrape/states.json")
      (map #(update % :name (fn [n] (-> n
                                        str/lower-case
                                        (str/replace " " "-")))))
      (u/index-by :name)))

;;; {["VA" "PRINCE WILLIAM"] {:county_fips 51153. ...}
(def fips-index
  (u/index-by (juxt :state_po :county_name)
              all-years))

(u/defn-memoized lookup-fips
  [state-name county-name]
  (get-in fips-index [[(get-in state-abbrev [state-name :abbreviation])
                       (str/upper-case county-name)]
                      :county_fips]))

(defn data-2024-state
  [state-name]
  (prn :s state-name)
  (as-> state-name state
    (format "/opt/mt/repos/electoral/scrape/%s.json" state)
    (vjson/read-file state)
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

(def x (group-by :state_po all-years))
(def x2024 (group-by :state_po data-2024))
#_
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

#_
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
              (vjson/read-file state))
        mapdata (get-in raw [:races 0 :mapData])
        city->county (memoize
                      (fn [city-name]
                        (prn :city city-name)
                        (let [city-name (str/replace (str/upper-case city-name) "-" " ")]
                          (-> (u/walk-collect (fn [x]
                                                (when (= (or (:name x) (:NAME x))
                                                         city-name) x))
                                              raw)
                              first
                              :COUNTY))))]
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


(defn =uncased
  [s1 s2]
  (and s1 s2 (= (str/lower-case s1) (str/lower-case s2))))
  

(defn rejigger-maine
  [state-name]
  (let [raw (as-> state-name state
              (format "/opt/mt/repos/electoral/scrape/%s.json" state)
              (vjson/read-file state))
        mapdata (get-in raw [:races 0 :mapData])
        city->county (memoize
                      (fn [city-name]
                        (prn :city city-name)
                        (let [city-name (str/replace (str/upper-case city-name) "-" " ")]
                          (-> (u/walk-collect (fn [x]
                                                (when (= (or (:name x) (:NAME x))
                                                         city-name) x))
                                              raw)
                              first
                              :COUNTY))))]
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

#_
(write-all-years)

;;; Something is really slow
