(ns spec-provider.core
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]))

(def preds
  [string?
   float?
   integer?])

(def pred->form
  {string?  'string?
   float?   'float?
   integer? 'integer?})

(def pred->name
  {string?  :string
   float?   :float
   integer? :integer})

(defn- safe-inc [x] (if x (inc x) 1))
(defn- safe-set-conj [s x] (if s (conj s x) #{x}))

(defn assimilate-attr-types [stats v]
  (reduce
   (fn [stats pred]
     (if (pred v)
       (update stats pred safe-inc)
       stats)) stats preds))

(defn assimilate-map [stats map]
  (reduce
   (fn [stats [k v]]
     (-> stats
         (update-in [k :types] assimilate-attr-types v)
         (update-in [k :distinct] safe-set-conj v)
         (update-in [k :count] safe-inc)))
   stats map))

(defn summarize-attr-value [{types :types
                             distinct-values :distinct}]
  (cond (= 1 (count types))
        (pred->form (ffirst types))

        (> (count types) 1)
        (concat
         (list 'clojure.spec/or)
         (mapcat (juxt pred->name pred->form) (map first types)))))

(defn- qualified-key? [k] (some? (namespace k)))
(defn- qualify-key [k] (keyword (str *ns*) (name k))) ;;TODO we need to pass ns as a param

(defn summarize-keys [stats]
  (let [highest-freq (apply max (map :count (vals stats)))
        extract-keys (fn [filter-fn]
                       (->> stats
                            (filter filter-fn)
                            (mapv (comp qualify-key key))
                            not-empty))
        req    (extract-keys
                (fn [[k v]] (and (qualified-key? k) (= (:count v) highest-freq))))
        opt    (extract-keys
                (fn [[k v]] (and (qualified-key? k) (< (:count v) highest-freq))))
        un-req (extract-keys
                (fn [[k v]] (and (not (qualified-key? k)) (= (:count v) highest-freq))))
        un-opt (extract-keys
                (fn [[k v]] (and (not (qualified-key? k)) (< (:count v) highest-freq))))]
    (cond-> (list 'clojure.spec/keys)
      req (concat [:req req])
      opt (concat [:opt opt])
      un-req (concat [:un-req un-req])
      un-opt (concat [:un-opt un-opt]))))

(defn derive-spec [data]
  (let [stats (reduce assimilate-map {} data)]
    (concat (map
             (fn [[k v]]
               (list `s/def (qualify-key k) (summarize-attr-value v)))
             stats)
            [(summarize-keys stats)])))

(s/def ::id (s/or :numeric (s/and integer? pos?) :string string?))
(s/def ::first-name string?)
(s/def ::surname string?)
(s/def ::age (s/and integer? pos?))
(s/def ::phone-number string?)
(s/def ::person
  (s/keys :req-un [::id ::first-name ::surname ::age]
          :opt-un [::phone-number]))

;;(s/form (s/or :numeric (s/and integer? pos?) :string string?))

(comment
  > (derive-spec (gen/sample (s/gen ::person) 100))

  ;;produces:
  ((clojure.spec/def
     :spec-provider.core/id
     (clojure.spec/or :integer integer? :string string?))
   (clojure.spec/def :spec-provider.core/first-name string?)
   (clojure.spec/def :spec-provider.core/surname string?)
   (clojure.spec/def :spec-provider.core/age integer?)
   (clojure.spec/def :spec-provider.core/phone-number string?)
   (clojure.spec/keys
    :un-req
    [:spec-provider.core/id
     :spec-provider.core/first-name
     :spec-provider.core/surname
     :spec-provider.core/age]
    :un-opt
    [:spec-provider.core/phone-number])))
