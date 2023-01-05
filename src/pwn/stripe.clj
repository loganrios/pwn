(ns pwn.stripe
  (:require [clj-http.client :as c]))

(def api-url "https://api.stripe.com/v1")

(defn base-req [{:keys [stripe/api-key]}]
  {:basic-auth [api-key ""]})

(defn endpoint [& args]
  (apply str api-url args))

(defn api-post [sys endpoint params]
  (c/post endpoint (merge (base-req sys) {:form-params params})))

(defn api-get [sys endpoint params]
  (c/get endpoint (merge (base-req sys) {:query-params params})))
