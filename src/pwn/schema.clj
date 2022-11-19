(ns pwn.schema
  (:require [malli.core :as malc]
            [malli.registry :as malr]))

(def schema
  {:user/id :uuid
   :user [:map {:closed true}
          [:xt/id :user/id]
          [:user/email :string]
          [:user/joined-at inst?]]

   :author/id :uuid
   :author/pen-name :string
   :author/user :user/id
   :author [:map {:closed true}
            [:xt/id :author/id]
            [:author/user :user/id]
            [:author/pen-name :string]]

   :work/id :uuid
   :work [:map {:closed true}
          [:xt/id :work/id]
          [:work/owner :user/id]
          [:work/title :string]]})

(def malli-opts {:registry (malr/composite-registry malc/default-registry schema)})
