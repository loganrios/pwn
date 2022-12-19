(ns pwn.feat.admin
  (:require [com.biffweb :as biff :refer [q]]
            [pwn.middleware :as mid]
            [pwn.ui :as ui]
            [xtdb.api :as xt]))

(defn new-admin-form []
  (biff/form
   {:action "/admin/promote"}
   [:div "Add a New Admin to ProjectWebNovel"]
   [:input#email
    {:name "email"
     :type "email"
     :placeholder "Email"}]
   [:button.btn {:type "submit"} "Add"]))

(defn new-admin [{:keys [biff/db admin params] :as req}]
  (let [admin-id (random-uuid)
        new-admin (biff/lookup-id db :user/email (:email params))]
    (when-not (biff/lookup-id db :admin/user new-admin)
     (biff/submit-tx req
                     [{:db/doc-type :admin
                       :xt/id admin-id
                       :admin/user new-admin
                       :admin/promoted-by (:xt/id admin)}])))
  {:status 303
   :headers {"Location" "/admin"}})

(defn create-genre-form []
  (biff/form
   {:action "/admin/create-genre"}
   [:div "Add a New Genre to ProjectWebNovel"]
   [:input#genre-id
    {:name "genre-id"
     :type "keyword"
     :placeholder "Genre ID"}]
   [:.h-1]
   [:input#description
    {:name "description"
     :type "string"
     :placeholder "Description"}]
   [:.h-1]
   [:input#display-name
    {:name "display-name"
     :type "string"
     :placeholder "User-facing Genre Name (case sensitive)"}]
   [:.h-1]
   [:button.btn {:type "submit"} "Create"]))

(defn new-genre [{:keys [params] :as req}]
  (biff/submit-tx req
                  [{:db/doc-type :genre
                    :xt/id (keyword (:genre-id params))
                    :genre/description (:description params)
                    :genre/display-name (:display-name params)}])
 {:status 303
  :headers {"Location" "/admin"}})

(defn admin [{:keys [biff/db]}]
  (ui/page
   {}
   [:div "This is the Admin Page! Congratz on making it in life!"]
   [:.h-3]
   (new-admin-form)
   [:.h-3]
   (create-genre-form)))

(def features
  {:routes ["/admin" {:middleware [mid/wrap-admin]}
            ["" {:get admin}]
            ["/promote" {:post new-admin}]
            ["/create-genre" {:post new-genre}]]})
