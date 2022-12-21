(ns pwn
  (:require [com.biffweb :as biff]
            [pwn.feat.dash :as dash]
            [pwn.feat.settings :as settings]
            [pwn.feat.auth :as auth]
            [pwn.feat.admin :as admin]
            [pwn.feat.home :as home]
            [pwn.feat.sponsor :as sponsor]
            [pwn.feat.worker :as worker]
            [pwn.schema :refer [malli-opts]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [ring.middleware.anti-forgery :as anti-forgery]
            [nrepl.cmdline :as nrepl-cmd]))

(def features
  [dash/features
   settings/features
   auth/features
   admin/features
   home/features
   sponsor/features
   worker/features])

(def routes [["" {:middleware [anti-forgery/wrap-anti-forgery
                               biff/wrap-anti-forgery-websockets
                               biff/wrap-render-rum]}
              (keep :routes features)]
             (keep :api-routes features)])

(def handler (-> (biff/reitit-handler {:routes routes})
                 (biff/wrap-inner-defaults {})))

(def static-pages (apply biff/safe-merge (map :static features)))

(defn generate-assets! [sys]
  (biff/export-rum static-pages "target/resources/public")
  (biff/delete-old-files {:dir "target/resources/public"
                          :exts [".html"]}))

(defn on-save [sys]
  (biff/add-libs)
  (biff/eval-files! sys)
  (generate-assets! sys)
  (test/run-all-tests #"pwn.test.*"))

(def components
  [biff/use-config
   biff/use-random-default-secrets
   biff/use-xt
   biff/use-queues
   biff/use-tx-listener
   biff/use-outer-default-middleware
   biff/use-jetty
   biff/use-chime
   (biff/use-when
    :pwn/enable-beholder
    biff/use-beholder)])

(defn start []
  (biff/start-system
   {:pwn/chat-clients (atom #{})
    :biff/features #'features
    :biff/after-refresh `start
    :biff/handler #'handler
    :biff/malli-opts #'malli-opts
    :biff.beholder/on-save #'on-save
    :biff.xtdb/tx-fns biff/tx-fns
    :biff/config "config.edn"
    :biff/components components})
  (generate-assets! @biff/system)
  (log/info "Go to" (:biff/base-url @biff/system)))

(defn -main [& args]
  (start)
  (apply nrepl-cmd/-main args))
