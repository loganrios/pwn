(ns pwn.feat.sponsor
  (:require [clj-http.client :as c]
            [cheshire.core :as json]
            [com.biffweb :as biff]
            [xtdb.api :as xt]
            [pwn.ui :as ui]
            [pwn.middleware :as mid]))

(def api-url "https://api.stripe.com/v1")

(defn base-req [{:keys [stripe/api-key]}]
  {:basic-auth [api-key ""]})

(defn endpoint [& args]
  (apply str api-url args))

(defn api-post [sys endpoint params]
  (c/post endpoint (merge (base-req sys) {:form-params params})))

;; TODO figure out how to support get params
(defn api-get [sys endpoint params]
  (c/get endpoint (merge (base-req sys) {:query-params params})))

(defn create-stripe-account! [{:keys [biff/db] :as sys}]
  (let [email (:user/email (xt/entity db (get-in sys [:session :uid])))]
    (-> (api-post sys (endpoint "/accounts")
                  {:type "express"
                   :email email
                   "capabilities[transfers][requested]" true
                   "capabilities[card_payments][requested]" true})
        (:body)
        (json/parse-string)
        (get "id"))))

(defn get-account-setup-link
  [{:keys [biff/base-url] :as sys} account-id]
  (-> (api-post sys (endpoint "/account_links")
                {:account account-id
                 :refresh_url (str base-url "/dash/sponsee")
                 :return_url (str base-url "/dash/sponsee")
                 :type "account_onboarding"})
      (:body)
      (json/parse-string)
      (get "url")))

(defn create-account-and-reroute [{:keys [biff/db] :as sys}]
  (let [uid (get-in sys [:session :uid])
        account-id (create-stripe-account! sys)
        setup-link (get-account-setup-link sys account-id)]
    (biff/submit-tx sys [{:db/doc-type :user
                          :db/op :merge
                          :xt/id uid
                          :user/stripe-account account-id}])
    (println "uid: " uid "\n"
             "account-id: " account-id "\n"
             "setup-link: " setup-link)
    {:status 303
     :headers {"Location" setup-link}}))

(defn get-started-form [_]
  [:div
   [:div "Your account is currently not set up to receive sponsorships."]
   [:.h-3]
   [:div "To receive sponsorships, you'll first need to set up your connected Stripe account."]
   [:.h-3]
   (biff/form
     {:action "/dash/sponsee/account"}
     [:button.btn {:type "submit"} "Create Stripe account"])
   [:.h-6]
   [:h1.text-lg "PII Disclaimer"]
   [:div.text-sm (biff/unsafe (slurp "resources/pii-disclaimer.html"))]])

(defn account-status-info [{:keys [biff/db session]}]
  [:div "Congratulations! Your account is enabled to receive sponsorships."])

(defn home [{:keys [biff/db session] :as sys}]
  (ui/page
   sys
   (if (:user/stripe-account (xt/entity db (:uid session)))
     (account-status-info sys)
     (get-started-form sys))))

(def features
  {:routes ["/dash/sponsee" {:middleware [mid/wrap-signed-in]}
            ["" {:get home}]
            ["/account" {:post create-account-and-reroute}]]})
