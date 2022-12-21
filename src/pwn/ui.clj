(ns pwn.ui
  (:require [clojure.java.io :as io]
            [com.biffweb :as biff]))

(defn css-path []
  (if-some [f (io/file (io/resource "public/css/main.css"))]
    (str "/css/main.css?t=" (.lastModified f))
    "/css/main.css"))

(defn base [opts & body]
  (apply
   biff/base-html
   (-> opts
       (merge #:base{:title "Project Web Novel"
                     :lang "en-US"
                     :icon "/img/glider.png"
                     :description "The author-first platform for online fiction."})
       (update :base/head (fn [head]
                            (concat [[:link {:rel "stylesheet" :href (css-path)}]
                                     [:script {:src "https://unpkg.com/htmx.org@1.8.4"}]
                                     [:script {:src "https://unpkg.com/hyperscript.org@0.9.7"}]]
                                    head))))
   body))

(defn topbar [sys]
  (let [uid (get-in sys [:session :uid])]
    [:nav#nav
     [:div.container.flex.flex-wrap.items-center.justify-between.mx-auto
      [:a.link.text-xl.font-semibold {:href "/"} "Project Web Novel"]
      [:a.link {:href "/genre"} "Genres"]
      (if uid
        [:a.link.cursor-pointer
         {:_ (str "on click toggle @hidden on #profile-nav")} "Me ▼"]
        [:a.link {:href "/auth/signin"} "Register/Login"])]
     [:div#profile-nav
      {:hidden true}
      [:.h-3]
      [:div.container.flex.flex-wrap.items-center.justify-between.mx-auto.bg-gray-50.rounded-md.p-2
       [:a.link {:href "/user/followed"} "Followed"]
       [:a.link {:href "/dash"} "Dashboard"]
       [:a.link {:href "/user/settings"} "Settings"]
       (biff/form {:action "/auth/signout" :class "inline"}
                  [:button.link {:type "submit"} "Sign out"])]]
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
   [:.p-3.mx-auto.max-w-screen-sm.w-full
    (topbar opts)
    body
    footer]))
