(ns pwn.feat.home
  (:require [com.biffweb :as biff :refer [q]]
            [pwn.middleware :as mid :refer [wrap-work
                                            wrap-chapter
                                            wrap-author
                                            wrap-genre
                                            wrap-comment]]
            [pwn.ui :as ui]
            [pwn.util :as util :refer [uid->author
                                       uid->works
                                       genreid->name
                                       get-all-genres
                                       follower-count]]
            [xtdb.api :as xt]
            [clojure.string :as str]))

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
       [:a.link {:href (str "/work/" (:xt/id work))}
        (:work/title work)]
       " | By: "
       (let [{:keys [xt/id author/pen-name]} (uid->author db owner)]
         [:a.link {:href (str "/author/" id)}
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
       [:a.link {:href (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}
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
        user (xt/entity db user-id)
        prev-follows (:user/followed user)
        work-id (:xt/id work)]
    (biff/submit-tx req
                    [{:db/doc-type :user
                      :db/op :merge
                      :xt/id user-id
                      :user/followed (set (conj prev-follows work-id))}]))
  {:status 303
   :headers {"Location" (str "/work/" (:xt/id work))}})

(defn unfollow-work [{:keys [session biff/db work] :as req}]
  (let [user-id (:uid session)
        user (xt/entity db user-id)
        prev-follows (:user/followed user)
        work-id (:xt/id work)]
    (biff/submit-tx req
                    [{:db/doc-type :user
                      :db/op :merge
                      :xt/id user-id
                      :user/followed (disj prev-follows work-id)}]))
  {:status 303
   :headers {"Location" (str "/work/" (:xt/id work))}})

(defn new-comment-form [work chapter]
  (biff/form
   {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/new-comment")}
   [:div "Add a comment"]
   [:div.flex-row.flex-grow.flex.items-center
    [:textarea#comment
     {:class "resize-rounded md"
      :name "comment"
      :wrap "soft"
      :placeholder "Enter comment text here"}]
    [:button.btn {:type "submit"} "Post"]]))

(defn new-comment [{:keys [session work chapter params] :as req}]
  (let [comment-id (random-uuid)]
    (biff/submit-tx req
                    [{:db/doc-type :comment
                      :xt/id comment-id
                      :comment/content (:comment params)
                      :comment/timestamp (biff/now)
                      :comment/owner (:uid session)}
                     [::xt/put
                      (assoc chapter :chapter/comments (conj (vec (:chapter/comments chapter)) comment-id))]]))
  {:status 303
   :headers {"Location" (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}})

(defn delete-comment [{:keys [work chapter comment] :as req}]
  (biff/submit-tx req
                  [[::xt/put
                    (assoc chapter :chapter/comments (remove #(= (:xt/id comment) %) (:chapter/comments chapter)))]])
  {:status 303
   :headers {"Location" (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}})

(defn edit-comment-form [{:keys [work chapter comment] :as req}]
  (let [{:comment/keys [content]} comment]
    (biff/form
     {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/edit")}
     [:div.flex-row.flex-grow.flex.items-center
      [:textarea#edit-comment
       {:class "resize-rounded md"
        :name "new-content"
        :wrap "soft"}
       content]
      [:button.btn {:type "submit"} "Submit"]])))

(defn edit-comment [{:keys [work chapter comment params] :as req}]
  (biff/submit-tx req
                  [{:db/doc-type :comment
                    :db/op :merge
                    :xt/id (:xt/id comment)
                    :comment/content (:new-content params)}])
  {:status 303
   :headers {"Location" (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}})

(defn reply-comment-form [{:keys [work chapter comment] :as req}]
  (biff/form
   {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/reply")}
   [:div.flex-row.flex-grow.flex.items-center
    [:textarea#reply-comment
     {:class "resize-rounded md"
      :name "reply-content"
      :wrap "soft"
      :placeholder "Enter your reply here."}]
    [:button.btn {:type "submit"} "Submit"]]))

(defn reply-comment [{:keys [session work chapter comment params] :as req}]
  (let [comment-id (random-uuid)
        prev-replies (:comment/replies comment)]
    (biff/submit-tx req
                    [{:db/doc-type :comment
                      :xt/id comment-id
                      :comment/content (:reply-content params)
                      :comment/timestamp (biff/now)
                      :comment/owner (:uid session)}
                     {:db/doc-type :comment
                      :db/op :merge
                      :xt/id (:xt/id comment)
                      :comment/replies (set (conj prev-replies comment-id))}]))
  {:status 303
   :headers {"Location" (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}})

(defn reply-view [db user admin owner work chapter comment recur-count]
  (for [comment-id (:comment/replies comment)]
    (let [reply (xt/entity db comment-id)
          {:comment/keys [content timestamp]} reply
          reply-owner (xt/entity db (:comment/owner reply))]
      [:div
       [:.text-sm
        [:span.font-bold
         (if (= (:comment/owner reply) owner)
           [:span.text-orange-600
            (str "Author - " (:author/pen-name (uid->author db (:comment/owner reply))))]
           (:user/username reply-owner))]
        [:span.w-2.inline-block]
        [:span.text-gray-600 (biff/format-date timestamp "d MMM h:mm aa")]]
       [:div {:hx-target "this" :hx-swap "outerHTML"}
        [:div
         (if (= (:comment/owner comment) owner)
           (str "@" (:author/pen-name (uid->author db (:comment/owner comment))))
           (str "@" (:comment/owner comment)))]
        content
        (if (= user (:comment/owner reply))
          [:.text-sm
           [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id reply) "/reply")
                     :hx-target "closest div"}
            "Reply"]
           [:span.w-2.inline-block]
           [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id reply) "/edit")}
            "Edit"]
           [:span.w-2.inline-block]
           [:span
            (biff/form
             {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id reply) "/delete")
              :class "inline"}
             [:button.link {:type "submit"} "Delete"])]]
          (if (= user owner)
            [:div
             [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id reply) "/reply")
                       :hx-target "closest div"}
              "Reply"]
             [:span.w-2.inline-block]
             (biff/form
              {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id reply) "/delete")
               :class "inline"}
              [:button.link {:type "submit"} "Delete"])]
            (if admin
              [:div
               [:.text-sm
                [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id reply) "/reply")
                          :hx-target "closest div"}
                 "Reply"]]
               [:span.w-2.inline-block]
               (biff/form
                {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id reply) "/delete")
                 :class "inline"}
                [:button.link {:type "submit"} "Delete"])]
              [:.text-sm
               [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id reply) "/reply")
                         :hx-target "closest div"}
                "Reply"]])))]
       [:div]
       [:p.whitespace-pre-wrap.mb-6]
       (if (:comment/replies reply)
         (let [new-recur-count (inc recur-count)]
           (if (< recur-count 4)
             [:div.mx-3
              (reply-view db user admin owner work chapter reply new-recur-count)]
             [:div
              (reply-view db user admin owner work chapter reply new-recur-count)]))
         nil)])))

(defn comment-view [db user admin owner work chapter]
  (for [comment-id (:chapter/comments chapter)]
    (let [comment (xt/entity db comment-id)
          {:comment/keys [content timestamp]} comment
          comment-owner (xt/entity db (:comment/owner comment))]
      [:div
       [:.text-sm
        [:span.font-bold
         (if (= (:comment/owner comment) owner)
           [:span.text-orange-600
            (str "Author - " (:author/pen-name (uid->author db (:comment/owner comment))))]
           (:user/username comment-owner))]
        [:span.w-2.inline-block]
        [:span.text-gray-600 (biff/format-date timestamp "d MMM h:mm aa")]]
       [:div {:hx-target "this" :hx-swap "outerHTML"}
        content
        (if (= user (:comment/owner comment))
          [:.text-sm
           [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/reply")
                     :hx-target "closest div"}
            "Reply"]
           [:span.w-2.inline-block]
           [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/edit")}
            "Edit"]
           [:span.w-2.inline-block]
           [:span
            (biff/form
             {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/delete")
              :class "inline"}
             [:button.link {:type "submit"} "Delete"])]]
          (if (= user owner)
            [:div
             [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/reply")
                       :hx-target "closest div"}
              "Reply"]
             [:span.w-2.inline-block]
             (biff/form
              {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/delete")
               :class "inline"}
              [:button.link {:type "submit"} "Delete"])]
            (if admin
              [:div
               [:.text-sm
                [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/reply")
                          :hx-target "closest div"}
                 "Reply"]]
               [:span.w-2.inline-block]
               (biff/form
                {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/delete")
                 :class "inline"}
                [:button.link {:type "submit"} "Delete"])]
              [:.text-sm
               [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/reply")
                         :hx-target "closest div"}
                "Reply"]])))]
       [:div]
       [:p.whitespace-pre-wrap.mb-6]
       [:div.mx-3
        (reply-view db user admin owner work chapter comment 0)]])))

(defn home [{:keys [biff/db] :as sys}]
  (ui/page
   sys
   (if-let [uid (get-in sys [:session :uid])]
     [:div "Welcome back, " (:user/username (biff/lookup db :xt/id uid)) "!"]
     [:div (biff/unsafe (slurp "resources/introduction.html"))])
   (works-list (:biff/db sys) (get-all-works sys))))

(defn work [{:keys [session biff/db work] :as sys}]
  (ui/page
   sys
   (let [user-id (:uid session)
         user (xt/entity db user-id)
         {:work/keys [title owner blurb chapters primary-genre secondary-genre]} work
         follower? (some (fn [follow-list]
                           (= (:xt/id work) follow-list))
                         (:user/followed user))]
     [:div
      [:div.container.flex.flex-wrap.items-center.justify-between.mx-auto
       (when user-id
         (if follower?
           (biff/form
            {:action (str "/work/" (:xt/id work) "/unfollow")}
            [:button.btn {:type "submit"}
             "Unfollow"])
           (biff/form
            {:action (str "/work/" (:xt/id work) "/follow")}
            [:button.btn {:type "submit"}
             "Follow"])))]
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
      [:div "This work has " (follower-count db (:xt/id work)) " followers."]
      [:.h-3]
      [:div blurb]
      [:.h-3]
      [:div (chapters-list db work chapters)]])))

;; HACK do not commit
(defn comment-block [& _]
  [:div "HACK comment-block"])

(defn chapter [{:keys [session biff/db work owner chapter] :as sys}]
  (ui/page
   sys
   (let [{:chapter/keys [title content comments]} chapter
         {:work/keys [chapters]} work
         user (:uid session)
         admin (biff/lookup db :admin/user user)
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
        (if user
          (new-comment-form work chapter)
          nil)
        [:.h-3]
        (comment-view db user admin owner work chapter)]
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
           [:a.link {:href (str "/work/" (:xt/id work-map))}
            title]
           [:div
            (if (= primary-genre secondary-genre)
              [:div (genreid->name db primary-genre)]
              [:div (genreid->name db primary-genre) " " (genreid->name db secondary-genre)])
            [:div blurb]]
           [:.h-3]]))]]
    [:div "This author has no works."]))

(defn author [{:keys [biff/db author] :as sys}]
  (ui/page
   sys
   [:div (:author/pen-name author)]
   [:div (author-works-list db (uid->works db (:author/user author)))]))

(defn genre-home [{:keys [biff/db] :as sys}]
  (ui/page
   sys
   (let [genre-list (get-all-genres db)]
     [:div
      (if (seq genre-list)
        [:div
         [:div "Choose a Genre: "]
         [:.h-1]
         [:div
          (for [genre genre-list]
            [:div [:a.link {:href (str "/genre/" (name (:xt/id genre)))}
                   (:genre/display-name genre)]])]]
        [:div "There are currently no genres to choose from."])])))

(defn genre-by-id [{:keys [biff/db genre] :as sys}]
  (ui/page
   sys
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
              [:a.link {:href (str "/work/" (:xt/id work))}
               (str title)]
              " | By: "
              (let [{:keys [:xt/id author/pen-name]} (uid->author db owner)]
                [:a.link {:href (str "/author/" id)}
                 pen-name])
              [:div (if (= primary-genre secondary-genre)
                      [:div (genreid->name db primary-genre)]
                      [:div (genreid->name db primary-genre) " " (genreid->name db secondary-genre)])]
              [:div blurb]
              [:.h-3]])))]])))

(defn followed [{:keys [session biff/db] :as sys}]
  (ui/page
   sys
   (let [user-id (:uid session)
         {:user/keys [followed]} (xt/entity db user-id)]
     [:div (if (seq followed)
             (for [work (map #(xt/entity db %) followed)]
               [:div
                [:a.link {:href (str "/work/" (:xt/id work))}
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
              ["/new-comment" {:post new-comment}]
              ["/comment/:comment-id" {:middleware [wrap-comment]}
               ["/edit" {:get edit-comment-form
                         :post edit-comment}]
               ["/reply" {:get reply-comment-form
                          :post reply-comment}]
               ["/delete" {:post delete-comment}]]]]
            ["/genre" {:get genre-home}]
            ["/genre/:genre-id" {:middleware [wrap-genre]}
             ["" {:get genre-by-id}]]]})
