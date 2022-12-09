(ns pwn.feat.app
  (:require [com.biffweb :as biff :refer [q]]
            [pwn.middleware :as mid :refer [wrap-work
                                            wrap-chapter]]
            [pwn.ui :as ui]
            [pwn.util :as util :refer [uid->author
                                       uid->works]]
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


(defn works-list [works]
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
        (author-info author)
        [:.h-3]
        (new-work-form)
        (let [works (uid->works db user-id)]
          (works-list works))]
       (become-author-form)))))

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

(defn delete-chapter [{:keys [biff/db work chapter] :as req}]
  (biff/submit-tx req
                  [{:db/op :delete
                    :xt/id (:xt/id chapter)}
                   [::xt/put
                    (assoc work :work/chapters (remove #(= (:xt/id chapter) %) (:work/chapters work)))]])
  {:status 303
   :headers {"Location" (str "/app/work/" (:xt/id work))}})


(defn update-blurb [{:keys [work params] :as req}]
  (biff/submit-tx req
                  [[::xt/put
                    (assoc work :work/blurb (:blurb params))]])
  {:status 303
   :headers {"Location" (str "/app/work/" (:xt/id work))}})

(defn blurb-form [work]
 (biff/form
  {:action (str "/app/work/" (:xt/id work) "/blurb")}
  (let [{:keys [work/blurb]} work]
    [:textarea#blurb
     {:class "resize rounded-md"
      :name "blurb"
      :wrap "soft"
      :placeholder (when (not (seq blurb)) "Your blurb here.")}
     blurb])
  [:.h-1]
  [:button.btn {:type "submit"} "Update Blurb"]))

(defn update-work-title [{:keys [work params] :as req}]
  (biff/submit-tx req
                  [[::xt/put
                    (assoc work :work/title (:title params))]])
  {:status 303
   :headers {"Location" (str "/app/work/" (:xt/id work))}})

(defn update-chapter-title [{:keys [work chapter params] :as req}]
  (biff/submit-tx req
                  [[::xt/put
                    (assoc chapter :chapter/title (:title params))]])
  {:status 303
   :headers {"Location" (str "/app/work/" (:xt/id work) "/chapter/" (:xt/id chapter))}})

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

(defn work-title-form [work]
  (biff/form
   {:action (str "/app/work/" (:xt/id work) "/title")}
   (let [{:keys [work/title]} work]
     [:input#title
      {:name "title"
       :type "text"
       :value title
       :required true}])
   [:h-1]
   [:button.btn {:type "submit"} "Update Title"]))

(defn work [{:keys [biff/db work owner]}]
  (ui/page
   {}
   [:a.btn {:href (str "/app")}
    "Back to Author Dashboard"]
   [:.h-3]
   [:div
    (work-title-form work)
    [:.text-sm "Owned by: " owner]]
   [:.h-3]
   (blurb-form work)
   [:.h-3]
   (new-chapter-form work)
   [:div
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
            ["/author" {:post new-author}]
            ["/work" {:post new-work}]
            ["/work/:work-id" {:middleware [wrap-work]}
             ["" {:get work}]
             ["/delete" {:post delete-work}]
             ["/blurb" {:post update-blurb}]
             ["/title" {:post update-work-title}]
             ["/chapter" {:post new-chapter}]
             ["/chapter/:chapter-id" {:middleware [wrap-chapter]}
              ["" {:get chapter
                   :post update-chapter}]
              ["/delete" {:post delete-chapter}]]]]})
