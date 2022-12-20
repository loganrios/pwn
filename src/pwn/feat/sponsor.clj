(ns pwn.feat.sponsor
  (:require [clj-http.client :as c]
            [cheshire.core :as json]
            [com.biffweb :as biff]
            [pwn.ui :as ui]
            [pwn.middleware :as mid]))

(defn home [sys]
  (ui/page
   {}
   [:div "Your account is currently not set up to receive sponsorships."]
   [:.h-3]
   [:div "To receive sponsorships, you'll first need to set up your connected Stripe account."]
   [:.h-3]
   [:a.btn "Request an account setup link"]
   [:.h-6]
   [:h1.text-lg "PII Disclaimer"]
   [:div.text-sm (biff/unsafe (slurp "resources/pii-disclaimer.html"))]))

(def features
 {:routes ["/app/sponsee" {:middleware [mid/wrap-signed-in]}
           ["" {:get home}]]})

(comment
  nil)
