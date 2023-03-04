(ns pwn.ui
  (:require
   [cheshire.core :as cheshire]
   [clojure.java.io :as io]
   [pwn.settings :as settings]
   [com.biffweb :as biff]
   [ring.middleware.anti-forgery :as csrf]))

(defn css-path []
  (if-some [f (io/file (io/resource "public/css/main.css"))]
    (str "/css/main.css?t=" (.lastModified f))
    "/css/main.css"))

(defn base [{:keys [::recaptcha] :as opts} & body]
  (apply
   biff/base-html
   (-> opts
       (merge #:base{:title settings/app-name
                     :lang "en-US"
                     :icon "/img/glider.png"
                     :description "The author-first platform for online fiction."})
       (update :base/head (fn [head]
                            (concat [[:link {:rel "stylesheet" :href (css-path)}]
                                     [:script {:src "https://unpkg.com/htmx.org@1.8.4"}]
                                     [:script {:src "https://unpkg.com/hyperscript.org@0.9.7"}]
                                     [:script (biff/unsafe (slurp (io/resource "darkmode.js")))]
                                     (when recaptcha
                                       [:script {:src "https://www.google.com/recaptcha/api.js"
                                                 :async "async" :defer "defer"}])]
                                      
                                    head))))
   body))

(defn topbar [sys]
  (let [uid (get-in sys [:session :uid])]
    [:nav#nav
     [:div.container.flex.flex-wrap.items-center.justify-between.mx-auto
      [:div.flex.flex-row.items-center
       [:a.link.text-xl.font-semibold {:href "/"} "Project Web Novel"]
       [:img.w-10.mx-2 {:src "/img/logo.svg"}]]
      [:button.btn.mx-6.my-3 {:onclick "toggleDarkMode()"} "Toggle Dark Mode"]
      (if uid
        [:a.link.cursor-pointer
         {:_ (str "on click toggle @hidden on #profile-nav")} "Me ▼"]
        [:a.link {:href "/auth/signin"} "Register/Login"])]
     [:div#profile-nav
      {:hidden true}
      [:.h-3]
      [:div {:class '[container flex flex-wrap items-center
                      justify-between mx-auto bg-gray-50
                      dark:bg-zinc-700 rounded-md py-2 px-5]}
       [:a.link {:href "/user/followed"} "Followed"]
       [:a.link {:href "/dash"} "Dashboard"]
       [:a.link {:href "/user/settings"} "Settings"]
       (biff/form {:action "/auth/signout" :class "inline"}
                  [:button.link {:type "submit"} "Sign out"])]
      [:.h-3]]
     [:div.container.flex.flex-wrap.items-center.justify-between.mx-auto
      [:a.link {:href "/genre"} "Genres"]
      [:div
       {:class "search-container"}
       (biff/form
        {:action "/search"}
        [:input#search
         {:name "search"
          :type "text"
          :placeholder "Search..."}]
        [:button.btn {:type "submit"}
         "Submit"])]]
     [:.h-5]]))

(def footer
  (let [yr (biff/format-date (biff/now) "yyyy")]
    [:footer.flex.flex-col.justify-center.items-center.text-xs
     [:.h-6]
     [:div.text-xs.text-gray-500 "Project Web Novel © " yr " Keionsoft Consulting, LLC."]
     [:.h-3]
     [:div.flex.flex-row.justify-center.items-center
      [:div.px-2 [:a.link {:href "mailto:pwn@keionsoft.com"} "Contact Us"]]
      [:div.px-2 [:a.link {:href "https://github.com/loganrios/pwn"} "Source Code"]]
      [:div.px-2 [:a.link {:href "https://keionsoft.com"} "Parent Company"]]]]))

(defn page [opts & body]
  (base
   opts
   [:.dark:bg-zinc-900.dark:text-white.h-screen.w-screen
    [:.p-3.mx-auto.max-w-screen-md.w-full
     (when (bound? #'csrf/*anti-forgery-token*)
       {:hx-headers (cheshire/generate-string
                     {:x-csrf-token csrf/*anti-forgery-token*})})
     (topbar opts)
     body
     footer]]))
