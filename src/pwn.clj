(ns pwn
  (:require [com.biffweb :as biff]
            [pwn.email :as email]
            [pwn.feat.auth :as auth]
            [pwn.feat.dash :as dash]
            [pwn.feat.settings :as settings]
            [pwn.feat.admin :as admin]
            [pwn.feat.home :as home]
            [pwn.feat.sponsee :as sponsee]
            [pwn.feat.sponsor :as sponsor]
            [pwn.feat.worker :as worker]
            [pwn.schema :as schema]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as test]
            [clojure.tools.logging :as log]
            [malli.core :as malc]
            [malli.registry :as malr]
            [nrepl.cmdline :as nrepl-cmd]))

(def features
  [dash/features
   settings/features
   auth/features
   (biff/authentication-plugin {})
   admin/features
   home/features
   sponsee/features
   sponsor/features
   worker/features])

(def routes [["" {:middleware [biff/wrap-site-defaults]}
              (keep :routes features)]
             ["" {:middleware [biff/wrap-api-defaults]}
              (keep :api-routes features)]])

(def handler (-> (biff/reitit-handler {:routes routes})
                 biff/wrap-base-defaults))

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

(def malli-opts
  {:registry (malr/composite-registry
              malc/default-registry
              (apply biff/safe-merge
                     (keep :schema features)))})

(def components
  [biff/use-config
   biff/use-secrets
   biff/use-xt
   biff/use-queues
   biff/use-tx-listener
   biff/use-wrap-ctx
   biff/use-jetty
   biff/use-chime
   (biff/use-when
    :pwn/enable-beholder
    biff/use-beholder)])

(defn start []
  (let [ctx (biff/start-system
             {:biff/send-email #'email/send-email
              :biff/features #'features
              :biff/after-refresh `start
              :biff/handler #'handler
              :biff/malli-opts #'malli-opts
              :biff.beholder/on-save #'on-save
              :biff.xtdb/tx-fns biff/tx-fns
              :biff/components components})]
    (generate-assets! ctx)
    (log/info "Go to" (:biff/base-url ctx))))
              

(defn -main [& args]
  (start)
  (apply nrepl-cmd/-main args))
