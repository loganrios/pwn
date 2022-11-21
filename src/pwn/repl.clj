(ns pwn.repl
  (:require [com.biffweb :as biff :refer [q]]
            [xtdb.api :as xt]))

(defn get-sys []
  (biff/assoc-db @biff/system))

(defn fq
  "List all entities in the database."
  [db]
  (q db
     '{:find [(pull e [*])]
       :where [[e :xt/id]]}))

(defn em->uid [all-users em]
  (->> all-users
       (filter #(= em (:user/email %)))
       (first)
       (:xt/id)))

(comment

  ;; As of writing this, calling (biff/refresh) with Conjure causes stdout to
  ;; start going to Vim. fix-print makes sure stdout keeps going to the
  ;; terminal. It may not be necessary in your editor.
  (biff/fix-print (biff/refresh))

  (let [{:keys [biff/db]} (get-sys)]
    (q db '{:find [e]
            :where [[e :author/user]]}))

  (sort (keys @biff/system))

  (fq (:biff/db (get-sys)))

  ;; Check the terminal for output.
  (biff/submit-job (get-sys) :echo {:foo "bar"})
  (deref (biff/submit-job-for-result (get-sys) :echo {:foo "bar"}))

 nil)
