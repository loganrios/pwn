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
     (biff/form
      {:action "/author"}
      [:button.btn {:type "submit"} "Register as an author"]))))

(defn new-author [{:keys [session] :as req}]
  (let [author-id (random-uuid)]
    (biff/submit-tx req
                    [{:db/doc-type :author
                      :xt/id author-id
                      :author/user (:uid session)}])
    {:status 303
     :headers {"Location" (str "/author/" author-id)}}))

(defn author [{:keys [biff/db path-params] :as req}]
  (if-some [author (xt/entity db (parse-uuid (:id path-params)))]
    (ui/page
     {}
     [:div "Author:" (:xt/id author)])))

(def features
  {:routes ["/app" {:middleware [mid/wrap-signed-in]}
            ["" {:get app}]
            ["/author" {:post new-author}]
            ["/author/:id" {:get author}]]})
