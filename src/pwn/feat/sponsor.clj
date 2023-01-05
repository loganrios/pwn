(ns pwn.feat.sponsor
  (:require
   [cheshire.core :as json]
   [com.biffweb :as biff]
   [xtdb.api :as xt]
   [pwn.ui :as ui]
   [pwn.middleware :as mid]
   [pwn.stripe :as stripe]))

(defn create-customer [{:keys [biff/db session] :as sys}]
  (let [uid (:uid session)
        {:user/keys [email]} (xt/entity db uid)
        customer-id (-> (stripe/api-post sys (stripe/endpoint "/customers") {:email email})
                        (:body)
                        (json/parse-string)
                        (get "id"))]
    (biff/submit-tx sys [{:db/doc-type :user
                          :db/op :merge
                          :xt/id uid
                          :user/stripe-customer customer-id}])
    {:status 303
     :headers {"Location" "/user/settings/sponsor"}}))

(defn home [{:keys [biff/db session] :as sys}]
  (let [uid (:uid session)
        {:user/keys [stripe-customer]} (xt/entity db uid)]
    (ui/page
     sys
     [:div "Sponsoring home!"]
     (if stripe-customer
       [:div "You are " stripe-customer]
       (biff/form
        {:action "/user/settings/sponsor/account"}
        [:button.btn {:type "submit"} "Enable sponsoring"])))))

(def features
  {:routes ["/user/settings/sponsor" {:middleware [mid/wrap-signed-in]}
            ["" {:get home}]
            ["/account" {:post create-customer}]]})
