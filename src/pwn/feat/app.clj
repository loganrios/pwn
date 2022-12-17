(ns pwn.feat.app
  (:require [com.biffweb :as biff :refer [q]]
            [pwn.middleware :as mid :refer [wrap-work
                                            wrap-chapter]]
            [pwn.ui :as ui]
            [pwn.util :as util :refer [uid->author
                                       uid->works
                                       get-all-genres
                                       follower-count]]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]
            [cheshire.core :as cheshire]))

(defn auth-info [email]
  [:div "Signed in as " email ". "
   (biff/form
    {:action "/auth/signout"
     :class "inline"}
    [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
     "Sign out."])])

(defn author-info [author]
  [:div (str "Your pen name is: " (:author/pen-name author))
   [:.text-sm "Author UUID: " (:xt/id author)]])

(defn new-work-form []
  (biff/form
   {:action "/app/work"}
   [:div "Create a new work"]
   [:input#title
    {:name "title"
     :type "text"
     :placeholder "Work Title"}]
   [:button.btn {:type "submit"} "Create"]))

(defn new-chapter-form [work]
  (biff/form
   {:action (str "/app/work/" (:xt/id work) "/chapter")}
   [:div "Create a new chapter"]
   [:input#title
    {:name "title"
     :type "text"
     :placeholder "Chapter Title"}]
   [:button.btn {:type "submit"} "Create"]))

(defn become-author-form []
  (biff/form
   {:action "/app/author"}
   [:div "Become an author"]
   [:input#pen-name
    {:name "pen-name"
     :type "text"
     :placeholder "Pen Name"}]
   [:button.btn {:type "submit"} "Create"]))

(defn create-genre-form []
  (biff/form
   {:action "/app/create-genre"}
   [:div "Add a New Genre to ProjectWebNovel"]
   [:div "(genre-id and slug must match or the genre page will break.)"]
   [:input#genre-id
    {:name "genre-id"
     :type "keyword"
     :placeholder "Genre ID (Do not include ':')"}]
   [:.h-1]
   [:input#slug
    {:name "slug"
     :type "string"
     :placeholder "Slug"}]
   [:.h-1]
   [:input#description
    {:name "description"
     :type "string"
     :placeholder "Description"}]
   [:.h-1]
   [:input#display-name
    {:name "display-name"
     :type "string"
     :placeholder "User-facing Genre Name (case sensitive)"}]
   [:button.btn {:type "submit"} "Create"]))

(defn works-list [db works]
  (if (seq works)
    [:div
     [:.h-3]
     [:div "Works:"
      (for [work works]
        (let [work-map (first work)]
          [:div
           [:a.text-blue-500.hover:text-blue-800 {:href (str "/app/work/" (:xt/id work-map))}
            (:work/title work-map)]
           " | "
           (str "Followers: " (follower-count db (:xt/id work-map)))
           " | "
           (biff/form
            {:action (str "/app/work/" (:xt/id work-map) "/delete")
             :class "inline"}
            [:button.text-blue-500.hover:text-blue-800 {:type "submit"} "Delete"])]))]]
    [:div "You have no works."]))

(defn chapters-list [db work chapters]
  (if (seq chapters)
    (for [chapter (map #(xt/entity db %) chapters)]
      [:div
       [:a.text-blue-500.hover:text-blue-800 {:href (str "/app/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}
        (:chapter/title chapter)]
       " | "
       [:span.text-gray-600 (biff/format-date (:chapter/created-at chapter) "d MMM H:mm aa")]
       " | "
       (biff/form
        {:action (str "/app/work/" (:xt/id work) "/chapter/" (:xt/id chapter) "/delete")
         :class "inline"}
        [:button.text-blue-500.hover:text-blue-800 {:type "submit"} "Delete"])])
    [:div "You have no chapters."]))

(defn new-author [{:keys [params session] :as req}]
  (let [author-id (random-uuid)]
    (biff/submit-tx req
                    [{:db/doc-type :author
                      :xt/id author-id
                      :author/user (:uid session)
                      :author/pen-name (:pen-name params)}])
    {:status 303
     :headers {"Location" "/app"}}))

(defn new-work [{:keys [params session] :as req}]
  (let [work-id (random-uuid)]
    (biff/submit-tx req
                    [{:db/doc-type :work
                      :xt/id work-id
                      :work/owner (:uid session)
                      :work/title (:title params)
                      :work/chapters []}]))
  {:status 303
   :headers {"Location" "/app"}})

(defn new-chapter [{:keys [work params] :as req}]
  (let [chapter-id (random-uuid)]
    (biff/submit-tx req
                   [{:db/doc-type :chapter
                     :xt/id chapter-id
                     :chapter/title (:title params)
                     :chapter/created-at (biff/now)}
                    [::xt/put
                     (assoc work :work/chapters (conj (vec (:work/chapters work)) chapter-id))]]))
  {:status 303
   :headers {"Location" (str "/app/work/" (:xt/id work))}})

(defn delete-work [{:keys [biff/db work] :as req}]
  (biff/submit-tx req
                  [{:db/op :delete
                    :xt/id (:xt/id work)}])
  {:status 303
   :headers {"Location" "/app"}})

(defn update-work [{:keys [work params] :as req}]
  (biff/submit-tx req
                  [[::xt/put
                    (assoc work
                           :work/title (:title params)
                           :work/blurb (:blurb params)
                           :work/primary-genre (:primary-genre params)
                           :work/secondary-genre (:secondary-genre params))]])
  {:status 303
   :headers {"Location" (str "/app/work/" (:xt/id work))}})

(def quill-js (slurp "resources/quill.js"))

(defn chapter-content-form [work chapter]
  (biff/form
   {:action (str "/app/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}
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
                  [[::xt/put
                    (assoc chapter
                           :chapter/content (:chapter-content params)
                           :chapter/title (:chapter-title params))]])
  {:status 303
   :headers {"Location" (str "/app/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}})

(defn delete-chapter [{:keys [biff/db work chapter] :as req}]
  (biff/submit-tx req
                  [{:db/op :delete
                    :xt/id (:xt/id chapter)}
                   [::xt/put
                    (assoc work :work/chapters (remove #(= (:xt/id chapter) %) (:work/chapters work)))]])
  {:status 303
   :headers {"Location" (str "/app/work/" (:xt/id work))}})

(defn work-content-form [db work genre-list]
  (biff/form
   {:action (str "/app/work/" (:xt/id work))}
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

(defn new-genre [{:keys [params] :as req}]
    (biff/submit-tx req
                    [{:db/doc-type :genre
                      :xt/id (keyword (:genre-id params))
                      :genre/slug (:slug params)
                      :genre/description (:description params)
                      :genre/display-name (:display-name params)}])
  {:status 303
   :headers {"Location" "/app"}})

(defn app [{:keys [session biff/db] :as req}]
  (let [user-id (:uid session)
        {:user/keys [email]} (xt/entity db user-id)]
    (ui/page
     {}
     nil
     (auth-info email)
     [:.h-3]
     (if-some [author (uid->author db user-id)]
       [:div
        [:.h-3]
        (author-info author)
        [:.h-3]
        (new-work-form)
        (let [works (uid->works db user-id)]
          (works-list db works))
        [:.h-5]]
       (become-author-form)))))

(defn work [{:keys [biff/db work owner]}]
  (ui/page
   {}
   [:div
    (work-content-form db work (get-all-genres db))
    [:.h-3]
    (new-chapter-form work)
    [:.h-3]
    (chapters-list db work (:work/chapters work))]))

(defn chapter [{:keys [biff/db work chapter]}]
  (ui/page
   {:base/head
     [[:script {:src "https://cdn.quilljs.com/1.3.6/quill.js"}]]}
   [:a.btn {:href (str "/app/work/" (:xt/id work))}
    "Back to Work Dashboard"]
   [:.h-3]
   [:div
    (chapter-content-form work chapter)]))

(def features
  {:routes ["/app" {:middleware [mid/wrap-signed-in]}
            ["" {:get app}]
            ["/create-genre" {:post new-genre}]
            ["/author" {:post new-author}]
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
