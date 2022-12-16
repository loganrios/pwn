(ns pwn.feat.sponsors
  (:require [clj-http.client :as c]
            [cheshire.core :as json]))

;; must be set before usage
;; TODO integrate into config.edn
(def api-key "")

(def api-route "https://api.stripe.com/v1/")

(defn endpoint [route]
  (str api-route route))

(def base-params
  {:basic-auth [api-key ""]})

(defn api-post [route form-params]
  (json/parse-string
   (:body
    (c/post (endpoint route)
            (merge base-params
                   {:form-params form-params})))
   true))

(comment

  (api-post "/account_links"
            {:account "acct_1MFR9P4gONBdg3Za"
             :refresh_url "https://example.com/reauth"
             :return_url "https://example.com/return"
             :type "account_onboarding"})
  ;; => {:object "account_link",
  ;;     :created 1671153507,
  ;;     :expires_at 1671153807,
  ;;     :url "https://connect.stripe.com/setup/s/acct_1MFR9P4gONBdg3Za/UXB1lIu4Wyxv"}

  ;; Accounts
  ;;     :type "standard",
  ;;     :created 1671148063,
  ;;     :payouts_enabled false,
  ;;     :id "acct_1MFR9P4gONBdg3Za",
  ;;     :tos_acceptance {:date nil, :ip nil, :user_agent nil},
  ;;     :charges_enabled false,
  ;;     :default_currency "usd",
  ;;     :country "US",
  ;;     :metadata {},
  ;;     :object "account"}

  nil)
