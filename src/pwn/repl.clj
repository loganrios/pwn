(ns pwn.repl
  (:require [com.biffweb :as biff :refer [q lookup lookup-id submit-tx]]
            [xtdb.api :as xt]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn get-sys []
  (biff/assoc-db @biff/system))

(defn get-db []
  (:biff/db (get-sys)))

(defn fq
  "List all entities in the database."
  [db]
  (q db
     '{:find [(pull e [*])]
       :where [[e :xt/id]]}))

(defn add-fixtures []
  (biff/submit-tx (get-sys)
                  (-> (io/resource "fixtures.edn")
                      slurp
                      edn/read-string)))



(comment
  (sort (keys @biff/system))

  (add-fixtures)

  (fq (:biff/db (get-sys)))

  ;; Check the terminal for output.
  (biff/submit-job (get-sys) :echo {:foo "bar"})
  (deref (biff/submit-job-for-result (get-sys) :echo {:foo "bar"}))

  nil)
