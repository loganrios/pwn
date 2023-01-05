(ns pwn.feat.settings
  (:require
   [xtdb.api :as xt]
   [com.biffweb :as biff]
   [pwn.middleware :as mid]
   [pwn.ui :as ui]))

(defn user-info-form [user]
  (biff/form
   {:action "/user/settings"}
   (let [{:user/keys [email username joined-at]} user]
     [:div
      [:p "Username: "]
      [:input#username.rounded-md
       {:name "username"
        :type "text"
        :value username
        :required true}]
      [:.h-1]
      [:div (str "Joined: " (biff/format-date joined-at "d MMM YYYY"))]
      [:.h-1]
      [:button.btn {:type "submit"} "Update User Info"]])))

(defn auth-info [email]
  [:div "Signed in as " email ". "
   (biff/form {:action "/auth/signout" :class "inline"}
              [:button.link {:type "submit"} "Sign out."])])

(defn user [{:keys [session biff/db] :as sys}]
  (let [user-id (:uid session)
        user (xt/entity db user-id)]
    (ui/page
     sys
     (auth-info (:user/email user))
     [:.h-3]
     [:div
      (user-info-form user)]
     [:.h-3]
     [:a.link {:href "/user/settings/sponsor"} "Sponsor Dashboard"])))

(defn update-user [{:keys [session biff/db params] :as req}]
  (let [user-id (:uid session)
        user (xt/entity db user-id)]
    (biff/submit-tx req
                    [{:db/doc-type :user
                      :db/op merge
                      :xt/id (:xt/id user)
                      :user/username (:username params)}]))
  {:status 303
   :headers {"Location" "/user/settings"}})

(def features
  {:routes ["/user/settings" {:middleware [mid/wrap-signed-in]}
            ["" {:get user
                 :post update-user}]]})
