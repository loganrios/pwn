(ns pwn.feat.home
  (:require [com.biffweb :as biff :refer [q]]
            [pwn.middleware :as mid :refer [wrap-work
                                            wrap-chapter
                                            wrap-author
                                            wrap-genre]]
            [pwn.ui :as ui]
            [pwn.util :as util :refer [uid->author
                                       uid->works
                                       genreid->name
                                       get-all-genres
                                       follower-count]]
            [xtdb.api :as xt]
            [clojure.string :as str]))

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
             (str "function onSubscribe(token) { document.getElementById('signin-form').submit()}"))]])

(defn get-all-works [{:keys [biff/db] :as req}]
  (q db
     '{:find (pull work [*])
       :where [[work :work/title]]}))

(defn get-works-by-primary-genre [db genre]
  (q db
     '{:find [works]
       :where [[works :work/primary-genre genre]]
       :in [genre]}
     genre))

(defn get-works-by-secondary-genre [db genre]
  (q db
     '{:find [works]
       :where [[works :work/secondary-genre genre]]
       :in [genre]}
     genre))

(defn get-works-by-genre [db genre]
  (let [primary (get-works-by-primary-genre db genre)
        secondary (get-works-by-secondary-genre db genre)]
    (map first (sort-by val > (frequencies (concat primary secondary))))))

(defn works-list [db works]
  (for [work works]
   (let [{:work/keys [owner primary-genre secondary-genre blurb]} work]
    [:div
     [:a.text-blue-500.hover:text-blue-800 {:href (str "/work/" (:xt/id work))}
      (:work/title work)]
     " | By: "
     (let [{:keys [xt/id author/pen-name]} (uid->author db owner)]
      [:a.text-blue-500.hover:text-blue-800 {:href (str "/author/" id)}
       pen-name])
     [:div
       (if (= primary-genre secondary-genre)
        [:div (genreid->name db primary-genre)]
        [:div (genreid->name db primary-genre) " " (genreid->name db secondary-genre)])]
     [:div
      blurb]
     [:.h-3]])))

(defn chapters-list [db work chapters]
  (if (seq chapters)
    (for [chapter (map #(xt/entity db %) chapters)]
      [:div
       [:a.text-blue-500.hover:text-blue-800 {:href (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}
        (:chapter/title chapter)]
       " | "
       [:span.text-gray-600 (biff/format-date (:chapter/created-at chapter) "d MMM H:mm aa")]])
    [:div "This work has no chapters."]))

(defn get-chapter-index [chapters-list chapter-id]
 (first (keep-indexed (fn [index item] (when (#(= chapter-id %) item) index))
                     chapters-list)))

(defn get-prev-ch-id [chapters-list current-ch-index]
  (get chapters-list (- current-ch-index 1)))

(defn get-next-ch-id [chapters-list current-ch-index]
  (get chapters-list (+ current-ch-index 1)))

(defn follow-work [{:keys [session biff/db work] :as req}]
  (let [user-id (:uid session)
        user (xt/entity db user-id)]
    (biff/submit-tx req
                    [[::xt/put
                      (assoc user :user/followed (conj (vec (:user/followed user)) (:xt/id work)))]]))
  {:status 303
   :headers {"Location" (str "/work/" (:xt/id work))}})

(defn unfollow-work [{:keys [session biff/db work] :as req}]
  (let [user-id (:uid session)
        user (xt/entity db user-id)]
    (biff/submit-tx req
                    [[::xt/put
                      (assoc user :user/followed (remove #(= (:xt/id work) %) (:user/followed user)))]]))
  {:status 303
   :headers {"Location" (str "/work/" (:xt/id work))}})

(defn home [sys]
  (ui/page
   {:base/head (when (util/email-signin-enabled? sys)
                 recaptcha-scripts)}
   (signin-form sys)
   [:.h-3]
   (works-list (:biff/db sys) (get-all-works sys))))

(defn work [{:keys [session biff/db work] :as sys}]
  (ui/page
   {}
   (let [user-id (:uid session)
         user (xt/entity db user-id)
         {:work/keys [title owner blurb chapters primary-genre secondary-genre]} work
         follower? (some (fn [follow-list]
                           (= (:xt/id work) follow-list))
                         (:user/followed user))]
    [:div
     [:div.container.flex.flex-wrap.items-center.justify-between.mx-auto
       (if follower?
         (biff/form
          {:action (str "/work/" (:xt/id work) "/unfollow")}
          [:button.btn {:type "submit"}
           "Unfollow"])
         (biff/form
          {:action (str "/work/" (:xt/id work) "/follow")}
          [:button.btn {:type "submit"}
           "Follow"]))]
     [:.h-3]
     [:div
      title
      " | "
      (str "By: " (:author/pen-name (uid->author db owner)))]
     [:.h-1]
     [:div]
     (if (= primary-genre secondary-genre)
       [:div (genreid->name db primary-genre)]
       [:div (genreid->name db primary-genre) " " (genreid->name db secondary-genre)])
     [:.h-3]
     [:div "This work has " (follower-count db (:xt/id work))" followers."]
     [:.h-3]
     [:div blurb]
     [:.h-3]
     [:div (chapters-list db work chapters)]])))

(defn new-comment-form [chapter work]
  (biff/form
   {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/new-comment")}
   [:div "Add a comment"]
   [:input#comment
    {:name "comment"
     :type "text"
     :placeholder "Enter comment text here"}]
   [:button.btn {:type "submit"} "Post"]))

(defn new-comment [{:keys [params session work chapter] :as req}]
  (let [comment-id (random-uuid)]
    (biff/submit-tx req
                    [{:db/doc-type :comment
                      :xt/id comment-id
                      :comment/content (:comment params)
                      :comment/timestamp (biff/now)
                      :comment/owner (:uid session)}]))
  {:status 303
   :headers {"Location" (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}})

(defn chapter [{:keys [biff/db work chapter]}]
  (ui/page
   {}
   (let [{:chapter/keys [title content]} chapter
         {:work/keys [chapters]} work
         current-ch-index (get-chapter-index chapters (:xt/id chapter))
         previous-chapter-id (get-prev-ch-id (:work/chapters work) current-ch-index)
         next-chapter-id (get-next-ch-id (:work/chapters work) current-ch-index)]
    (if (seq content)
     [:div
      [:div title]
      [:.h-3]
      (when (not (nil? previous-chapter-id))
       [:a.btn {:href (str "/work/" (:xt/id work) "/chapter/" previous-chapter-id)}
        "Previous"])
      [:a.btn {:href (str "/work/" (:xt/id work))}
       "Work Home"]
      (when (not (nil? next-chapter-id))
       [:a.btn {:href (str "/work/" (:xt/id work) "/chapter/" next-chapter-id)}
        "Next"])
      [:.h-3]
      [:div
       (biff/unsafe content)]
      [:.h-3]
      (when (not (nil? previous-chapter-id))
       [:a.btn {:href (str "/work/" (:xt/id work) "/chapter/" previous-chapter-id)}
        "Previous"])
      [:a.btn {:href (str "/work/" (:xt/id work))}
       "Work Home"]
      (when (not (nil? next-chapter-id))
       [:a.btn {:href (str "/work/" (:xt/id work) "/chapter/" next-chapter-id)}
        "Next"])
      [:.h-3]
      (new-comment-form chapter work)]
     [:div
      (when (not (nil? previous-chapter-id))
       [:a.btn {:href (str "/work/" (:xt/id work) "/chapter/" previous-chapter-id)}
        "Previous"])
      [:a.btn {:href (str "/work/" (:xt/id work))}
       "Work Home"]
      (when (not (nil? next-chapter-id))
       [:a.btn {:href (str "/work/" (:xt/id work) "/chapter/" next-chapter-id)}
        "Next"])
      [:.h-3]
      "This chapter has no content."]))))

(defn author-works-list [db works]
 (if (seq works)
   [:div
    [:div "Works:"
     (for [work works]
       (let [work-map (first work)
             {:work/keys [title primary-genre secondary-genre blurb]} work-map]
         [:div
          [:a.text-blue-500.hover:text-blue-800 {:href (str "/work/" (:xt/id work-map))}
           title]
          [:div
           (if (= primary-genre secondary-genre)
             [:div (genreid->name db primary-genre)]
             [:div (genreid->name db primary-genre) " " (genreid->name db secondary-genre)])
           [:div blurb]]
          [:.h-3]]))]]
   [:div "This author has no works."]))

(defn author [{:keys [biff/db author]}]
  (ui/page
   {}
   [:div (:author/pen-name author)]
   [:div (author-works-list db (uid->works db (:author/user author)))]))

(defn genre-home [{:keys [biff/db]}]
  (ui/page
   {}
   (let [genre-list (get-all-genres db)]
     [:div
      (if (seq genre-list)
        [:div
          [:div "Choose a Genre: "]
          [:.h-1]
          [:div
           (for [genre genre-list]
             [:div [:a.text-blue-500.hover:text-blue-800 {:href (str "/genre/" (:genre/slug genre))}
                    (:genre/display-name genre)]])]]
        [:div "There are currently no genres to choose from."])])))

(defn genre-by-id [{:keys [biff/db genre]}]
  (ui/page
   {}
   (let [{:genre/keys [display-name description]} genre]
    [:div
     [:div.text-xl.font-semibold display-name]
     [:div description]
     [:.h-3]
     [:div
      (let [works-list (get-works-by-genre db (name (:xt/id genre)))]
        (for [work-id works-list]
          (let [work (xt/entity db (first work-id))
                {:work/keys [title owner blurb primary-genre secondary-genre]} work]
              [:div
                [:a.text-blue-500.hover:text-blue-800 {:href (str "/work/" (:xt/id work))}
                 (str title)]
                " | By: "
                (let [{:keys [:xt/id author/pen-name]} (uid->author db owner)]
                  [:a.text-blue-500.hover:text-blue-800 {:href (str "/author/" id)}
                   pen-name])
                [:div (if (= primary-genre secondary-genre)
                       [:div (genreid->name db primary-genre)]
                       [:div (genreid->name db primary-genre) " " (genreid->name db secondary-genre)])]
                [:div blurb]
                [:.h-3]])))]])))

(defn followed [{:keys [session biff/db] :as req}]
  (ui/page
    {}
    (let [user-id (:uid session)
          {:user/keys [followed]} (xt/entity db user-id)]
      [:div (if (seq followed)
              (for [work (map #(xt/entity db %) followed)]
                 [:div
                  [:a.text-blue-500.hover:text-blue-800 {:href (str "/work/" (:xt/id work))}
                   (:work/title work)]
                  [:.h-1]
                  (chapters-list db work (:work/chapters work))
                  [:.h-3]])
              (str "You are not following any works."))])))

(def features
  {:routes [""
            ["/" {:get home}]
            ["/user/followed" {:get followed}]
            ["/author/:author-id" {:middleware [wrap-author]}
             ["" {:get author}]]
            ["/work/:work-id" {:middleware [wrap-work]}
             ["" {:get work}]
             ["/follow" {:post follow-work}]
             ["/unfollow" {:post unfollow-work}]
             ["/chapter/:chapter-id" {:middleware [wrap-chapter]}
              ["" {:get chapter}]
              ["/new-comment" {:post new-comment}]]]
            ["/genre" {:get genre-home}]
            ["/genre/:genre-slug" {:middleware [wrap-genre]}
             ["" {:get genre-by-id}]]]})
