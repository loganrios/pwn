(ns pwn.feat.home
  (:require [com.biffweb :as biff :refer [q]]
            [pwn.middleware :as mid :refer [wrap-work
                                            wrap-chapter]]
            [pwn.ui :as ui]
            [pwn.util :as util :refer [uid->author]]
            [xtdb.api :as xt]))

(defn recaptcha-disclosure [{:keys [link-class]}]
  [:span "This site is protected by reCAPTCHA and the Google "
   [:a {:href "https://policies.google.com/privacy"
        :target "_blank"
        :class link-class}
    "Privacy Policy"] " and "
   [:a {:href "https://policies.google.com/terms"
        :target "_blank"
        :class link-class}
    "Terms of Service"] " apply."])

(defn signin-form [{:keys [recaptcha/site-key] :as sys}]
  (biff/form
   {:id "signin-form"
    :action "/auth/send"}
   [:div [:label {:for "email"} "Email address:"]]
   [:.h-1]
   [:.flex
    [:input#email
     {:name "email"
      :type "email"
      :autocomplete "email"
      :placeholder "Enter your email address"}]
    [:.w-3]
    [:button.btn.g-recaptcha
     (merge
      (when (util/email-signin-enabled? sys)
        {:data-sitekey site-key
         :data-callback "onSubscribe"
         :data-action "subscribe"})
      {:type "submit"})
     "Sign in"]]
   [:.h-1]
   (if (util/email-signin-enabled? sys)
     [:.text-sm (recaptcha-disclosure {:link-class "link"})]
     [:.text-sm
      "Doesn't need to be a real address. "
      "Until you add API keys for Postmark and reCAPTCHA, we'll just print your sign-in "
      "link to the console. See config.edn."])))

(def recaptcha-scripts
  [[:script {:src "https://www.google.com/recaptcha/api.js"
             :async "async"
             :defer "defer"}]
   [:script (biff/unsafe
             (str "function onSubscribe(token) { document.getElementById('signin-form').submit()}))]]); }"))]])

(defn get-all-works [{:keys [biff/db] :as req}]
  (q db
     '{:find (pull work [*])
       :where [[work :work/title]]}))

(defn works-list [db works]
  (for [work works]
    [:div
     [:a.text-blue-500.hover:text-blue-800 {:href (str "/work/" (:xt/id work))}
      (:work/title work)]
     " | By: " (:author/pen-name (uid->author db (:work/owner work)))
     [:.h-3]]))

(defn chapters-list [db work chapters]
  (if (seq chapters)
    (for [chapter (map #(xt/entity db %) chapters)]
      [:div
       [:a.text-blue-500.hover:text-blue-800 {:href (str "work/" (:xt/id work) "/chapter/" (:xt/id chapter))}
        (:chapter/title chapter)]
       " | "
       [:span.text-gray-600 (biff/format-date (:chapter/created-at chapter) "d MMM H:mm aa")]])
    [:div "This work has no chapters."]))

(defn home [sys]
  (ui/page
   {:base/head (when (util/email-signin-enabled? sys)
                 recaptcha-scripts)}
   (signin-form sys)
   [:.h-3]
   (works-list (:biff/db sys) (get-all-works sys))))

(defn work [{:keys [biff/db work] :as sys}]
  (ui/page
   {}
   (let [{:work/keys [title owner blurb chapters]} work]
    [:div
     [:div title]
     [:div (str "By: " (:author/pen-name (uid->author db owner)))]
     [:.h-3]
     [:div blurb]
     [:.h-3]
     [:div (chapters-list db work chapters)]])))






(def features
  {:routes [""
            ["/" {:get home}]
            ["/work/:work-id" {:middleware [wrap-work]}
             ["" {:get work}]]]})
