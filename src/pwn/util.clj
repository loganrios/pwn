(ns pwn.util
  (:require [com.biffweb :as biff :refer [q]]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [clj-http.client :as http]
            [xtdb.api :as xt]))

(defn uid->author [db user-id]
  (-> (q db
         '{:find [(pull author [*])]
           :where [[author :author/user uid]]
           :in [uid]}
         user-id)
      (ffirst)))

(defn uid->works [db user-id]
  (q db '{:find [(pull works [*])]
          :where [[works :work/owner uid]]
          :in [uid]}
     user-id))

(defn uid->username [db user-id]
  (:user/username (biff/lookup db :xt/id user-id)))

(defn genreid->name [db genre-id]
  (:genre/display-name (xt/entity db (keyword genre-id))))

(defn get-all-genres [{:keys [biff/db] :as req}]
  (q req
     '{:find (pull genre [*])
       :where [[genre :genre/display-name]]}))

(defn follower-count [db work-id]
   (count (q db '{:find [user]
                  :where [[user :user/followed work-id]]
                  :in [work-id]}
               work-id)))
