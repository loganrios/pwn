(ns pwn.feat.dash
  (:require [com.biffweb :as biff :refer [q]]
            [pwn.middleware :as mid :refer [wrap-work
                                            wrap-chapter]]
            [pwn.ui :as ui]
            [pwn.util :as util :refer [uid->author
                                       uid->works
                                       get-all-genres
                                       follower-count]]
            [xtdb.api :as xt]))

(defn auth-info [email]
  [:div "Signed in as " email ". "
   (biff/form {:action "/auth/signout" :class "inline"}
              [:button.link {:type "submit"} "Sign out."])])

(defn author-info [author]
  [:div (str "Your pen name is: " (:author/pen-name author))
   [:.text-sm "Author UUID: " (:xt/id author)]])

(defn new-work-form []
  (biff/form
   {:action "/dash/work"}
   [:div "Create a new work"]
   [:input#title
    {:name "title"
     :type "text"
     :placeholder "Work Title"}]
   [:button.btn {:type "submit"} "Create"]))

(defn new-chapter-form [work]
  (biff/form
   {:action (str "/dash/work/" (:xt/id work) "/chapter")}
   [:div "Create a new chapter"]
   [:input#title
    {:name "title"
     :type "text"
     :placeholder "Chapter Title"}]
   [:button.btn {:type "submit"} "Create"]))

(defn become-author-form []
  (biff/form
   {:action "/dash/author"}
   [:div "Become an author"]
   [:input#pen-name
    {:name "pen-name"
     :type "text"
     :placeholder "Pen Name"}]
   [:button.btn {:type "submit"} "Create"]))

(defn works-list [db works]
  (if (seq works)
    [:div
     [:.h-3]
     [:div "Works:"
      (for [work works]
        (let [work-map (first work)]
          [:div
           [:a.link {:href (str "/dash/work/" (:xt/id work-map))}
            (:work/title work-map)]
           " | "
           (str "Followers: " (follower-count db (:xt/id work-map)))
           " | "
           (biff/form
            {:action (str "/dash/work/" (:xt/id work-map) "/delete")
             :class "inline"}
            [:button.link
             {:type "submit"
              :onclick "return confirm('Are you sure you want to delete?')"}
             "Delete"])]))]]
    [:div "You have no works."]))

(defn chapters-list [db work chapters]
  (if (seq chapters)
    (for [chapter (map #(xt/entity db %) chapters)]
      [:div
       [:a.link {:href (str "/dash/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}
        (:chapter/title chapter)]
       " | "
       [:span.text-gray-600 (biff/format-date (:chapter/created-at chapter) "d MMM H:mm aa")]
       " | "
       (biff/form
        {:action (str "/dash/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/delete")
         :class "inline"}
        [:button.link
         {:type "submit"
          :onclick "return confirm('Are you sure you want to delete?')"}
         "Delete"])])
    [:div "You have no chapters."]))

(defn new-author [{:keys [params session] :as req}]
  (let [author-id (random-uuid)]
    (biff/submit-tx req
                    [{:db/doc-type :author
                      :xt/id author-id
                      :author/user (:uid session)
                      :author/pen-name (:pen-name params)}])
    {:status 303
     :headers {"Location" "/dash"}}))

(defn new-work [{:keys [params session] :as req}]
  (let [work-id (random-uuid)]
    (biff/submit-tx req
                    [{:db/doc-type :work
                      :xt/id work-id
                      :work/owner (:uid session)
                      :work/title (:title params)
                      :work/chapters []}]))
  {:status 303
   :headers {"Location" "/dash"}})

(defn new-chapter [{:keys [work params] :as req}]
  (let [chapter-id (random-uuid)
        prev-chapters (:work/chapters work)]
    (biff/submit-tx req
                    [{:db/doc-type :chapter
                      :xt/id chapter-id
                      :chapter/title (:title params)
                      :chapter/created-at (biff/now)}
                     {:db/doc-type :work
                      :db/op :merge
                      :xt/id (:xt/id work)
                      :work/chapters (vec (conj prev-chapters chapter-id))}]))
  {:status 303
   :headers {"Location" (str "/dash/work/" (:xt/id work))}})

(defn delete-work [{:keys [biff/db work] :as req}]
  (biff/submit-tx req
                  [{:db/op :delete
                    :xt/id (:xt/id work)}])
  {:status 303
   :headers {"Location" "/dash"}})

(defn update-work [{:keys [work params] :as req}]
  (biff/submit-tx req
                    [{:db/doc-type :work
                      :db/op :merge
                      :xt/id (:xt/id work)
                      :work/title (:title params)
                      :work/blurb (:blurb params)
                      :work/primary-genre (keyword (:primary-genre params))
                      :work/secondary-genre (keyword (:secondary-genre params))}])
  {:status 303
   :headers {"Location" (str "/dash/work/" (:xt/id work))}})

(def quill-js (slurp "resources/quill.js"))

(defn chapter-content-form [work chapter]
  (biff/form
   {:action (str "/dash/work/" (:xt/id work) "/chapter/" (:xt/id chapter))
    :id "chapter-content-form"}
   (let [{:chapter/keys [content title]} chapter]
     [:div
      [:.h-1]
      [:input#chapter-title
       {:name "chapter-title"
        :type "text"
        :value title
        :required true}]
      [:.h-3]
      [:link {:href "https://cdn.quilljs.com/1.3.6/quill.snow.css" :rel "stylesheet"}]
      [:div#editor (when (seq content) (biff/unsafe content))]
      [:input {:name "chapter-content" :type "hidden"}]
      [:script (biff/unsafe quill-js)]
      [:button.btn {:type "submit"} "Update Content"]])))

(defn update-chapter [{:keys [work chapter params] :as req}]
  (biff/submit-tx req
                  [{:db/doc-type :chapter
                    :db/op :merge
                    :xt/id (:xt/id chapter)
                    :chapter/content (:chapter-content params)
                    :chapter/title (:chapter-title params)}])
  {:status 303
   :headers {"Location" (str "/dash/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}})

(defn delete-chapter [{:keys [biff/db work chapter] :as req}]
  (let [chapter-id (:xt/id chapter)
        prev-chapters (:work/chapters work)]
    (biff/submit-tx req
                    [{:db/op :delete
                      :xt/id (:xt/id chapter)}
                     {:db/doc-type :work
                      :db/op :merge
                      :xt/id (:xt/id work)
                      :work/chapters (vec (disj (set prev-chapters) chapter-id))}]))
  {:status 303
   :headers {"Location" (str "/dash/work/" (:xt/id work))}})

(defn work-content-form [db work genre-list]
  (biff/form
   {:action (str "/dash/work/" (:xt/id work))}
   (let [{:work/keys [title blurb primary-genre secondary-genre]} work]
     [:div
      [:p "Work Title: "]
      [:input#title
       {:name "title"
        :type "text"
        :value title
        :required true}]
      [:.h-3]
      [:div
       [:.h-10.w-60.flex.flex-col.flex-grow
        [:select
         {:name "primary-genre"
          :class '[text-sm
                   cursor-pointer
                   focus:border-blue-600
                   focus:ring-blue-600]}
         [:option {:value ""}
          "Select a Primary Genre"]
         (for [genre genre-list]
           (let [value (:xt/id genre)]
             [:option.cursor-pointer
              {:value value
               :selected (= (keyword primary-genre) (:xt/id genre))}
              (:genre/display-name genre)]))]
        [:.grow]]
       [:.h-10.w-60.flex.flex-col.flex-grow
        [:select
         {:name "secondary-genre"
          :class '[text-sm
                   cursor-pointer
                   focus:border-blue-600
                   focus:ring-blue-600]}
         [:option {:value ""}
          "Select a Secondary Genre"]
         (for [genre genre-list]
           (let [value (:xt/id genre)]
             [:option.cursor-pointer
              {:value value
               :selected (= (keyword secondary-genre) (:xt/id genre))}
              (:genre/display-name genre)]))]
        [:.grow]]]
      [:.h-1]
      [:p "Blurb: "]
      [:textarea#blurb
       {:class "resize rounded-md"
        :name "blurb"
        :wrap "soft"
        :placeholder (when (not (seq blurb)) "Your blurb here.")}
       blurb]])
   [:h-3]
   [:button.btn {:type "submit"} "Update Work Info"]))

(defn author-info-form [author]
  (biff/form
   {:action "/dash/author/update"}
   (let [{:author/keys [pen-name]} author]
     [:div
      [:p "Author Name: "]
      [:input#pen-name
       {:name "pen-name"
        :type "text"
        :value pen-name
        :required true}]
      [:button.btn {:type "submit"} "Update"]])))

(defn update-author [{:keys [session biff/db author params] :as req}]
  (let [user-id (:uid session)
        author-id (uid->author db user-id)]
    (biff/submit-tx req
                    [{:db/doc-type :author
                      :db/op merge
                      :xt/id (:xt/id author-id)
                      :author/pen-name (:pen-name params)}]))
  {:status 303
   :headers {"Location" "/dash"}})

(defn dash [{:keys [session biff/db] :as sys}]
  (let [user-id (:uid session)
        {:user/keys [email username]} (xt/entity db user-id)]
    (ui/page
     sys
     (auth-info email)
     [:.h-3]
     (if-some [author (uid->author db user-id)]
       [:div
        [:.h-3]
        [:h1.text-lg.font-semibold "Author Info"]
        (author-info-form author)
        [:.h-6]
        [:a.btn {:href "/dash/sponsee"} "Sponsee Dashboard"]
        [:.h-6]
        [:h1.text-lg.font-semibold "Manage Works"]
        (new-work-form)
        [:.h-6]
        (let [works (uid->works db user-id)]
          (works-list db works))
        [:.h-5]]
       (become-author-form)))))

(defn work [{:keys [biff/db work owner] :as sys}]
  (ui/page
   sys
   [:div
    (work-content-form db work (get-all-genres db))
    [:.h-3]
    (new-chapter-form work)
    [:.h-3]
    (chapters-list db work (:work/chapters work))]))

(defn chapter [{:keys [biff/db work chapter] :as sys}]
  (ui/page
   (merge sys
          {:base/head
           [[:script {:src "https://cdn.quilljs.com/1.3.6/quill.js"}]]})
   [:a.btn {:href (str "/dash/work/" (:xt/id work))}
    "Back to Work Dashboard"]
   [:.h-3]
   [:div
    (chapter-content-form work chapter)]))

(def features
  {:routes ["/dash" {:middleware [mid/wrap-signed-in]}
            ["" {:get dash}]
            ["/author"
             ["" {:post new-author}]
             ["/update" {:post update-author}]]
            ["/work" {:post new-work}]
            ["/work/:work-id" {:middleware [wrap-work]}
             ["" {:get work
                  :post update-work}]
             ["/delete" {:post delete-work}]
             ["/chapter" {:post new-chapter}]
             ["/chapter/:chapter-id" {:middleware [wrap-chapter]}
              ["" {:get chapter
                   :post update-chapter}]
              ["/delete" {:post delete-chapter}]]]]})
