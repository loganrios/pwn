(ns pwn.schema
  (:require [malli.core :as malc]
            [malli.registry :as malr]))

(def schema
  {:user/id :uuid
   :user/email :string
   :user/joined-at inst?
   :user/author :author/id
   :user [:map {:closed true}
          [:xt/id :user/id]
          :user/email
          :user/joined-at
          [:user/author {:optional true}]]

   :author/id :uuid
   :author/pen-name :string
   :author [:map {:closed true}
            [:xt/id :author/id]
            :author/pen-name]

   :work/id :uuid
   :work/owner :user/id
   :work/title :string
   :work [:map {:closed true}
          [:xt/id :work/id]
          :work/title]})

(def malli-opts {:registry (malr/composite-registry malc/default-registry schema)})
