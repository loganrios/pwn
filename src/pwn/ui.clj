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
                                     [:script {:src "https://unpkg.com/htmx.org@1.8.6"}]
                                     [:script {:src "https://unpkg.com/hyperscript.org@0.9.8"}]
                                     (when recaptcha
                                       [:script {:src "https://www.google.com/recaptcha/api.js"
                                                 :async "async" :defer "defer"}])]
                                      
                                    head))))
   body))

(defn header [{:keys [session]}]
  [:header.bg-slate-800.py-2
   [:div {:class '[flex mx-auto items-center text-white gap-4
                   text-lg flex-wrap px-3 max-w-screen-md]}
    [:a {:href "/"} [:img.h-10 {:alt "pwn.ink logo" :src "/img/pwn-herb.png"}]]
    [:.flex-grow]
    [:a.hover:underline {:href "/genre"} "Genres"]
    [:a.hover:underline {:href "/search"} "Search"]
    (if (:uid session)
      [:a.hover:underline {:href "/user/settings"} "Me"]
      [:a.hover:underline {:href "/signin"} "Sign in"])]])

(defn footer [{:keys [::recaptcha]}]
  (let [yr (biff/format-date (biff/now) "yyyy")]
    [:footer.flex.flex-col.justify-center.items-center.text-xs.py-4.bg-slate-800
     [:.text-xs.text-slate-400 "pwn.ink © " yr " Keionsoft Consulting, LLC."]
     [:.h-2]
     [:.flex.flex-row
      [:a.text-slate-400.hover:underline.px-2 {:href "maito:pwn@keionsoft.com"} "Contact Us"]
      [:a.text-slate-400.hover:underline.px-2 {:href "https://github.com/loganrios/pwn"} "Source Code"]
      [:a.text-slate-400.hover:underline.px-2 {:href "https://keionsoft.com"} "Parent Company"]]
     [:.h-2]
     (when recaptcha [:<> [:.h-2] biff/recaptcha-disclosure])]))

(defn page [opts & body]
  (base
   opts
   (header opts)
   [:.flex-grow.bg-slate-50
    [:.p-3.mx-auto.max-w-screen-md.w-full
     (when (bound? #'csrf/*anti-forgery-token*)
       {:hx-headers (cheshire/generate-string
                     {:x-csrf-token csrf/*anti-forgery-token*})})
     body]]
   [:.flex-grow.bg-slate-50]
   (footer opts)))

(defn section [& body]
  (into [:section.bg-slate-50.shadow.rounded.p-3] body))
