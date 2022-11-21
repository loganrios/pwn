(ns pwn.seed
  (:require [com.biffweb :as biff :refer [q]]
            [xtdb.api :as xt]
            [pwn.repl :refer [get-sys fq em->uid]]
            [clojure.string :as str]))

;; Users

(def users ["ax" "ay" "az"
            "ux" "uy" "uz"])

(defn gen-user [nm]
  {:db/doc-type :user
   :xt/id (random-uuid)
   :user/email (str nm "@pwn")
   :user/joined-at :db/now})

(def users-seed
  (map gen-user ["ax" "ay" "az"
                 "ux" "uy" "uz"]))

(defn nm->uid [nm]
  (em->uid users-seed (str nm "@pwn")))

;; Authors

(defn gen-author [nm]
  {:db/doc-type :author
   :xt/id (random-uuid)
   :author/user (nm->uid nm)
   :author/pen-name (str nm " authorson")})

(def authors-seed
  (map gen-author ["ax" "ay" "az"]))

;; Chapters
;; awkwardly depends on resources/words-source.txt
;; but at least it's fast

(def words
  (remove #(= "" %)
          (str/split
           (slurp "resources/words-source.txt")
           #"\s")))

;; HACK (shuffle words) is definitely the bottleneck
(defn random-para []
  (let [word-min 100
        word-range 200]
    (str (str/join " " (take (+ word-min (rand-int word-range)) (shuffle words))))))

(defn random-content []
  (let [para-min 20
        para-range 20]
    (str/join "\n\n" (for [_ (range (+ para-min (rand-int para-range)))]
                       (random-para)))))

(defn gen-chapter [id]
  {:db/doc-type :chapter
   :xt/id id
   :chapter/title (str "Chapter " id)
   :chapter/content (random-content)})

(def chapter-ids
  (for [_ (range 30)]
    (random-uuid)))

(def chapters-seed
  (map gen-chapter chapter-ids))

;; Works
;; has to be done a little differently,
;; because we have to divy up the chapters.

(defn gen-work [owner-nm title ch-start ch-amt]
  {:db/doc-type :work
   :xt/id (random-uuid)
   :work/title title
   :work/blurb (str "A new work by " owner-nm ".")
   :work/owner (nm->uid owner-nm)
   :work/chapters (->> chapter-ids
                       (drop ch-start)
                       (take ch-amt)
                       (vec))})

(def works-seed
  (map #(apply gen-work %)
       [["ax" "Ax the Unicorn" 0 10]
        ["ay" "Ancient Aliens: The Web Novel" 10 10]
        ["az" "The Phantom Tollbooth" 20 10]]))

;; Submitting seeds

(defn seed [documents]
  (let [{:keys [biff/db] :as sys} (get-sys)]
    (biff/submit-tx sys documents)))

(defn seed-all []
  (seed users-seed)
  (seed authors-seed)
  (seed chapters-seed)
  (seed works-seed))

(comment

  ;; before running, be sure to eval all forms
  ;; (especially because we slurp a 500kb txt file)
  (seed-all)

  (fq (:biff/db (get-sys)))

  nil)
