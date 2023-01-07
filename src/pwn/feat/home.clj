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
                                       uid->username
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

(defn chapters-list [db work chapters]
  (if (seq chapters)
    (for [chapter (map #(xt/entity db %) chapters)]
      [:div
       [:a.text-lg.link {:href (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}
        (:chapter/title chapter)]
       [:span.text-gray-600.px-2 (biff/format-date (:chapter/created-at chapter) "d MMM H:mm aa")]])
    [:div "This work has no chapters."]))

(defn recent-chapters-list [db work chapters]
  (if (seq chapters)
    (let [last-five-chapters (take 5 (reverse (sort-by :chapter/created-at (map #(xt/entity db %) chapters))))]
      (for [chapter last-five-chapters]
        [:div
         [:a.link {:href (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}
          (:chapter/title chapter)]
         [:span.text-gray-600.px-2 (biff/format-date (:chapter/created-at chapter) "d MMM H:mm aa")]]))
    [:div "This work has no chapters."]))

(defn get-chapter-index [chapters-list chapter-id]
  (first (keep-indexed (fn [index item] (when (#(= chapter-id %) item) index))
                       chapters-list)))

(defn get-prev-ch-id [chapters-list current-ch-index]
  (get chapters-list (- current-ch-index 1)))

(defn get-next-ch-id [chapters-list current-ch-index]
  (get chapters-list (+ current-ch-index 1)))

(defn chapter-navigation [work-id prev-ch-id next-ch-id]
  [:div.flex.flex-row.justify-between
   [:a.link.pr-5 {:href (str "/work/" work-id "/chapter/" prev-ch-id)
                  :class (when (nil? prev-ch-id) "invisible")}
    "< Previous"]
   [:a.link {:href (str "/work/" work-id)}
    "Work Home"]
   [:a.link.pl-5 {:href (str "/work/" work-id "/chapter/" next-ch-id)
                  :class (when (nil? next-ch-id) "invisible")}
    "Next >"]])

(defn author-works-list [db works]
  (if (seq works)
    [:div
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
           [:.h-3]]))]
    [:div "This author has no works."]))

(defn works-list [db works]
  (for [work works]
    (let [{:work/keys [owner title primary-genre secondary-genre blurb chapters]} work]
      [:div
        [:a.link.text-lg.font-semibold {:href (str "/work/" (:xt/id work))}
         title]
        " by "
        (let [{:keys [xt/id author/pen-name]} (uid->author db owner)]
          [:a.link {:href (str "/author/" id)}
           pen-name])
        [:div
         (if (= primary-genre secondary-genre)
           [:div (genreid->name db primary-genre)]
           [:div (genreid->name db primary-genre) " " (genreid->name db secondary-genre)])]
        [:.h-1]
        blurb
        [:.h-3]
        (when (seq chapters)
          [:div
            "Most Recent Post: "
            (first (recent-chapters-list db work chapters))])
        [:.h-1]
        [:div.flex.flex-row.justify-between
         [:span (str (follower-count db (:xt/id work)) " Followers")]
         [:span.w-2.inline-block]
         [:span (str (count chapters) " Chapters")]
         [:span.w-2.inline-block]]
        [:.h-4.border-b]])))

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
  (let [comment-id (random-uuid)
        prev-comments (:chapter/comments chapter)]
    (biff/submit-tx req
                    [{:db/doc-type :comment
                      :xt/id comment-id
                      :comment/content (:comment params)
                      :comment/timestamp (biff/now)
                      :comment/owner (:uid session)}
                     {:db/doc-type :chapter
                      :db/op :merge
                      :xt/id (:xt/id chapter)
                      :chapter/comments (vec (conj prev-comments comment-id))}]))
  {:status 303
   :headers {"Location" (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}})

(defn delete-comment [{:keys [work chapter comment] :as req}]
  (let [comment-id (:xt/id comment)
        prev-comments (:chapter/comments chapter)]
    (biff/submit-tx req
                   [{:db/op :delete
                     :xt/id comment-id}
                    {:db/doc-type :chapter
                     :db/op :merge
                     :xt/id (:xt/id chapter)
                     :chapter/comments (vec (disj (set prev-comments) comment-id))}]))
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
    [:button.btn {:type "submit"} "Submit"]
    [:button.link {:href (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}
            "Cancel"]]))

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

(defn delete-reply [{:keys [biff/db work chapter comment] :as req}]
  (let [reply-id (:xt/id comment)
        parent-comment (biff/lookup db :comment/replies reply-id)
        prev-comments (:comment/replies parent-comment)]
    (biff/submit-tx req
                   [{:db/op :delete
                     :xt/id reply-id}
                    {:db/doc-type :comment
                     :db/op :merge
                     :xt/id (:xt/id parent-comment)
                     :comment/replies (disj prev-comments reply-id)}]))
  {:status 303
   :headers {"Location" (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}})

(defn reply-view [db user admin owner work chapter comment recur-count]
  (for [comment-id (:comment/replies comment)]
    (let [reply (xt/entity db comment-id)
          {:comment/keys [content timestamp]} reply
          reply-owner (xt/entity db (:comment/owner reply))]
      [:div
       [:div
        [:span.font-bold
         (if (= (:comment/owner reply) owner)
           (let [author (uid->author db (:comment/owner reply))]
             [:div
              "Author: "
              [:a.link {:href (str "/author/" (:xt/id author))}
                (:author/pen-name author)]])
           (:user/username reply-owner))]
        [:span.w-2.inline-block]
        [:span.text-gray-600 (biff/format-date timestamp "d MMM h:mm aa")]]
       [:div {:hx-target "this" :hx-swap "outerHTML"}
        [:div
         (if (= (:comment/owner comment) owner)
           (str "@" (:author/pen-name (uid->author db (:comment/owner comment))))
           (str "@" (uid->username db (:comment/owner comment))))]
        content
        (if (= user (:comment/owner reply))
          [:div
           [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id reply) "/reply")
                     :hx-target "closest div"}
            "Reply"]
           [:span.w-2.inline-block]
           [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id reply) "/edit")}
            "Edit"]
           [:span.w-2.inline-block]
           [:span
            (biff/form
             {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id reply) "/delete-reply")
              :class "inline"}
             [:button.link {:type "submit"} "Delete"])]]
          (if (= user owner)
            [:div
             [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id reply) "/reply")
                       :hx-target "closest div"}
              "Reply"]
             [:span.w-2.inline-block]
             (biff/form
              {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id reply) "/delete-reply")
               :class "inline"}
              [:button.link {:type "submit"} "Delete"])]
            (if admin
              [:div
                [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id reply) "/reply")
                          :hx-target "closest div"}
                 "Reply"]
                [:span.w-2.inline-block]
                (biff/form
                 {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id reply) "/delete-reply")
                  :class "inline"}
                 [:button.link {:type "submit"} "Delete"])]
              (if user
                [:.div
                 [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/reply")
                           :hx-target "closest div"}
                  "Reply"]]
                nil))))]
       [:div#reply]
       [:p.whitespace-pre-wrap.mb-6]
       (if (:comment/replies reply)
         (let [new-recur-count (inc recur-count)
               max-recur-count 4]
           (if (< recur-count max-recur-count)
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
       [:div
        [:span.font-bold
         (if (= (:comment/owner comment) owner)
           (let [author (uid->author db (:comment/owner comment))]
             [:div
              "Author: "
              [:a.link {:href (str "/author/" (:xt/id author))}
                (:author/pen-name author)]])
           (:user/username comment-owner))]
        [:span.w-2.inline-block]
        [:span.text-gray-600 (biff/format-date timestamp "d MMM h:mm aa")]]
       [:div {:hx-target "this" :hx-swap "outerHTML"}
        content
        (if (= user (:comment/owner comment))
          [:div
           [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/reply")
                     :hx-target "closest div"}
            "Reply"]
           [:span.w-2.inline-block]
           [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/edit")}
            "Edit"]
           [:span.w-2.inline-block]
           [:span
            (biff/form
             {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/delete-comment")
              :class "inline"}
             [:button.link {:type "submit"} "Delete"])]]
          (if (= user owner)
            [:div
             [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/reply")
                       :hx-target "closest div"}
              "Reply"]
             [:span.w-2.inline-block]
             (biff/form
              {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/delete-comment")
               :class "inline"}
              [:button.link {:type "submit"} "Delete"])]
            (if admin
              [:div
                [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/reply")
                          :hx-target "closest div"}
                 "Reply"]
                [:span.w-2.inline-block]
                (biff/form
                 {:action (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/delete-comment")
                  :class "inline"}
                 [:button.link {:type "submit"} "Delete"])]
              (if user
                [:.div
                 [:a.link {:hx-get (str "/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/comment/" (:xt/id comment) "/reply")
                           :hx-target "closest div"}
                  "Reply"]]
                nil))))]
       [:div#reply]
       [:p.whitespace-pre-wrap.mb-6]
       [:div.mx-3
        (reply-view db user admin owner work chapter comment 0)]])))

(defn home [{:keys [biff/db] :as sys}]
  (ui/page
   sys
   (if-let [uid (get-in sys [:session :uid])]
     [:p.inline "Welcome back, "
      [:p.font-semibold.inline (:user/username (biff/lookup db :xt/id uid))]
      "!"]
     [:div (biff/unsafe (slurp "resources/introduction.html"))])
   [:.h-3]
   [:.text-xl.font-semibold "Now on PWN..."]
   [:.h-3]
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
      [:div.text-xl.font-semibold title]
      [:div.text-lg "by " (:author/pen-name (uid->author db owner))]
      [:.h-3]
      [:div.flex.items-center
       (when user-id
         (biff/form
          {:action (str "/work/" (:xt/id work) (if follower? "/unfollow" "/follow"))
           :class "inline"}
          [:button.btn {:type "submit"} (if follower? "Unfollow" "Follow")]))
       [:div.px-2 "This work has " (follower-count db (:xt/id work)) " follower(s)."]]
      [:.h-1]
      (if (= primary-genre secondary-genre)
        [:div (genreid->name db primary-genre)]
        [:div (genreid->name db primary-genre) " " (genreid->name db secondary-genre)])
      [:.h-3]
      [:.h-3]
      [:div.bg-gray-100.dark:bg-zinc-700.rounded-md.p-5 blurb]
      [:.h-3]
      [:div (chapters-list db work chapters)]])))

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
        [:a.link.text-xl.font-semibold {:href (str "/work/" (:xt/id work))} (:work/title work)]
        [:div.text-lg.font-semibold "⤷" title]
        [:.h-3]
        (chapter-navigation (:xt/id work) previous-chapter-id next-chapter-id)
        [:.h-6]
        [:div
         (biff/unsafe content)]
        [:.h-6]
        (chapter-navigation (:xt/id work) previous-chapter-id next-chapter-id)
        [:.h-3]
        (if user
          (new-comment-form work chapter)
          nil)
        [:.h-3]
        (comment-view db user admin owner work chapter)]
       [:div
        (chapter-navigation (:xt/id work) previous-chapter-id next-chapter-id)
        [:.h-3]
        "This chapter has no content."]))))

(defn author [{:keys [biff/db author] :as sys}]
  (ui/page
   sys
   [:div.font-semibold (str (:author/pen-name author) "'s Author Profile")]
   [:.h-3]
   "Works: "
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
       (let [works-list (get-works-by-genre db (:xt/id genre))]
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
                [:a.text-lg.link {:href (str "/work/" (:xt/id work))}
                 (:work/title work)]
                [:.h-1]
                [:div.text-sm
                 (recent-chapters-list db work (:work/chapters work))]
                [:.h-3]])
             (str "You are not following any works."))])))

(defn search [{:keys [biff/db params] :as sys}]
  (ui/page
   sys
   (let [search-input (:search params)
         regex (re-pattern (str "(?i)" search-input))
         works (q db
                  '{:find [works-list]
                    :where [[works-list :work/title title]
                            [(re-find re title)]]
                    :in [re]}
                  regex)
         authors (q db
                    '{:find [authors-list]
                      :where [[authors-list :author/pen-name name]
                              [(re-find re name)]]
                      :in [re]}
                    regex)]
     [:div
       (if (seq works)
        (for [work works]
          (let [work-info (biff/lookup db :xt/id (first work))
                {:work/keys [owner title primary-genre secondary-genre blurb]} work-info]
            [:div
             [:a.link.text-lg.font-semibold {:href (str "/work/" (:xt/id work-info))}
              title]
             " by "
             (let [{:keys [xt/id author/pen-name]} (uid->author db owner)]
               [:a.link {:href (str "/author/" id)}
                pen-name])
             [:div
              (if (= primary-genre secondary-genre)
                [:div (genreid->name db primary-genre)]
                [:div (genreid->name db primary-genre) " " (genreid->name db secondary-genre)])]
             [:div
              blurb]
             [:.h-3]]))
        [:div
         "No Works Found."
         [:.h-5]])
       (if (seq authors)
         (for [author authors]
           (let [author-info (biff/lookup db :xt/id (first author))
                 {:keys [xt/id author/user author/pen-name]} author-info
                 author-works (author-works-list db (uid->works db user))]
             [:div
               "Author: "
               [:a.link {:href (str "/author/" id)}
                pen-name]
               [:.h-5]
               (str "Works by " pen-name ":")
               [:.h-3]
               author-works
               [:.h-5]]))
         "No Authors Found.")])))

(def features
  {:routes [""
            ["/" {:get home}]
            ["/search" {:post search}]
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
               ["/delete-comment" {:post delete-comment}]
               ["/reply" {:get reply-comment-form
                          :post reply-comment}]
               ["/delete-reply" {:post delete-reply}]]]]
            ["/genre" {:get genre-home}]
            ["/genre/:genre-id" {:middleware [wrap-genre]}
             ["" {:get genre-by-id}]]]})
