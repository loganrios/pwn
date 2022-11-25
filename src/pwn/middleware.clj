(ns pwn.middleware
  (:require [xtdb.api :as xt]))

(defn wrap-redirect-signed-in [handler]
  (fn [{:keys [session] :as req}]
    (if (some? (:uid session))
      {:status 303
       :headers {"location" "/app"}}
      (handler req))))

(defn wrap-signed-in [handler]
  (fn [{:keys [session] :as req}]
    (if (some? (:uid session))
      (handler req)
      {:status 303
       :headers {"location" "/"}})))

(defn wrap-work [handler]
  (fn [{:keys [biff/db path-params] :as req}]
    (if-some [work (xt/entity db (parse-uuid (:work-id path-params)))]
      (let [owner (:work/owner work)]
       (handler (assoc req :work work :owner owner)))
      (handler req))))

(defn wrap-chapter [handler]
  (fn [{:keys [biff/db path-params] :as req}]
    (if-some [chapter (xt/entity db (parse-uuid (:chapter-id path-params)))]
      (handler (assoc req :chapter chapter))
      (handler req))))
