(ns pwn.util
  (:require [com.biffweb :as biff :refer [q]]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [clj-http.client :as http]
            [xtdb.api :as xt]))

(defn email-signin-enabled? [sys]
  (every? sys [:postmark/api-key :recaptcha/site-key :recaptcha/secret-key]))

(defn postmark [{:keys [postmark/api-key]} method endpoint & [form-params options]]
  (http/request
   (merge {:method method
           :url (str "https://api.postmarkapp.com" endpoint)
           :headers {"X-Postmark-Server-Token" api-key}
           :as :json
           :content-type :json
           :form-params (cske/transform-keys csk/->PascalCase form-params)}
          options)))

(defn send-email [{:keys [postmark/from] :as sys} form-params]
  (biff/catchall-verbose
   (postmark sys :post "/email" (merge {:from from} form-params))))

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

(defn genreid->name [db genre-id]
  (:genre/display-name (xt/entity db (keyword genre-id))))

(defn get-all-genres [{:keys [biff/db] :as req}]
  (q req
     '{:find (pull genre [*])
       :where [[genre :genre/display-name]]}))
