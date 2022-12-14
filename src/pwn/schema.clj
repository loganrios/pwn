(ns pwn.schema
  (:require [malli.core :as malc]
            [malli.registry :as malr]))

(def schema
  {:user/id :uuid
   :user [:map {:closed true}
          [:xt/id :user/id]
          [:user/email :string]
          [:user/followed {:optional true} [:vector :work/id]]
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
          [:work/blurb {:optional true} :string]
          [:work/title :string]
          [:work/chapters {:optional true} [:vector :chapter/id]]
          [:work/primary-genre {:optional true} :genre/id]
          [:work/secondary-genre {:optional true} :genre/id]]

   :chapter/id :uuid
   :chapter [:map {:closed true}
             [:xt/id :chapter/id]
             [:chapter/title :string]
             [:chapter/content {:optional true} :string]
             [:chapter/created-at inst?]]

   :genre/id :keyword
   :genre [:map {:closed true}
           [:xt/id :genre/id]
           [:genre/slug :string]
           [:genre/description :string]
           [:genre/display-name :string]]})

(def malli-opts {:registry (malr/composite-registry malc/default-registry schema)})
