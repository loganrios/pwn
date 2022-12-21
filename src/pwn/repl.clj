(ns pwn.repl
  (:require [com.biffweb :as biff :refer [q lookup lookup-id submit-tx]]
            [xtdb.api :as xt]))

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

(comment
  ;;Genre Seed for Testing

  (defn seed [documents]
    (let [{:keys [biff/db] :as sys} (get-sys)]
      (biff/submit-tx sys documents)))

  (def genres ["Fantasy" "SciFi" "Romance"
               "Drama" "Contemporary" "Nonfiction"])

  (defn gen-genre [nm]
    {:db/doc-type :genre
     :xt/id (keyword nm)
     :genre/slug nm
     :genre/description (str "This genre is " nm)
     :genre/display-name nm})

  (def genres-seed
    (map gen-genre genres))

  (seed genres-seed)

  (sort (keys @biff/system))

  (fq (:biff/db (get-sys)))

  ;; Check the terminal for output.
  (biff/submit-job (get-sys) :echo {:foo "bar"})
  (deref (biff/submit-job-for-result (get-sys) :echo {:foo "bar"}))

  nil)
