(ns pwn.feat.app
  (:require [com.biffweb :as biff :refer [q]]
            [pwn.middleware :as mid]
            [pwn.ui :as ui]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]
            [cheshire.core :as cheshire]))

(defn app [{:keys [session biff/db] :as req}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))]
    (ui/page
     {}
     nil
     [:div "Signed in as " email ". "
      (biff/form
       {:action "/auth/signout"
        :class "inline"}
       [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
        "Sign out"])
      "."]
     [:.h-3]
     (if-some [author (ffirst
                       (q db
                          '{:find [(pull author [*])]
                            :where [[author :author/user user-id]]
                            :in [user-id]}
                          (:uid session)))]
       [:div
        [:div "Your author is: " (:author/pen-name author)]
        [:div.text-sm "Author UUID: " (:xt/id author)]
        [:.h-3]
        (biff/form
         {:action "/app/work"}
         [:div "Create a new work"]
         [:input#title
          {:name "title"
           :type "text"
           :placeholder "Work title"}]
         [:button.btn {:type "submit"} "Create"])
        (let [works (first
                     (q db
                        '{:find [(pull works [*])]
                          :where [[works :work/owner user-id]]
                          :in [user-id]}
                        (:uid session)))]
          (if (seq works)
            [:div
             [:.h-3]
             [:div "Works:"
              (for [work works]
                [:div (:work/title work)])]]
            [:div "You have no works."]))]
       (biff/form
        {:action "/app/author"}
        [:div "Become an author"]
        [:input#pen-name
         {:name "pen-name"
          :type "text"
          :placeholder "Pen Name"}]
        [:button.btn {:type "submit"} "Create"])))))

(defn new-author [{:keys [params session] :as req}]
  (let [author-id (random-uuid)]
    (biff/submit-tx req
                    [{:db/doc-type :author
                      :xt/id author-id
                      :author/user (:uid session)
                      :author/pen-name (:pen-name params)}])
    {:status 303
     :headers {"Location" "/app"}}))

(defn new-work [{:keys [params session] :as req}]
  (let [work-id (random-uuid)]
    (biff/submit-tx req
                    [{:db/doc-type :work
                      :xt/id work-id
                      :work/owner (:uid session)
                      :work/title (:title params)}]))
  {:status 303
   :headers {"Location" "/app"}})

(def features
  {:routes ["/app" {:middleware [mid/wrap-signed-in]}
            ["" {:get app}]
            ["/author" {:post new-author}]
            ["/work" {:post new-work}]]})
