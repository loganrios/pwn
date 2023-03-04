(ns pwn.middleware
  (:require [com.biffweb :as biff]
            [xtdb.api :as xt]))

(defn wrap-redirect-signed-in [handler]
  (fn [{:keys [session] :as req}]
    (if (some? (:uid session))
      {:status 303
       :headers {"Location" "/signin?error=not-signed-in"}}
      (handler req))))

(defn wrap-signed-in [handler]
  (fn [{:keys [session] :as req}]
    (if (some? (:uid session))
      (handler req)
      {:status 303
       :headers {"Location" "/"}})))

(defn wrap-admin [handler]
  (fn [{:keys [session biff/db] :as req}]
    (let [admin (biff/lookup db :admin/user (:uid session))]
      (if admin
        (handler (assoc req :admin admin))
        {:status 303
         :headers {"Location" "/"}}))))

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

(defn wrap-author [handler]
  (fn [{:keys [biff/db path-params] :as req}]
    (if-some [author (xt/entity db (parse-uuid (:author-id path-params)))]
      (handler (assoc req :author author))
      (handler req))))

(defn wrap-genre [handler]
  (fn [{:keys [biff/db path-params] :as req}]
    (if-some [genre (xt/entity db (keyword (:genre-id path-params)))]
      (handler (assoc req :genre genre))
      (handler req))))

(defn wrap-comment [handler]
  (fn [{:keys [biff/db path-params] :as req}]
    (if-some [comment (xt/entity db (parse-uuid (:comment-id path-params)))]
      (handler (assoc req :comment comment))
      (handler req))))
