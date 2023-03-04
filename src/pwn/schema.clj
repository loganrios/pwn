(ns pwn.schema
  (:require [malli.core :as malc]
            [malli.registry :as malr]))

(def schema
  {:user/id :uuid
   :user [:map {:closed true}
          [:xt/id :user/id]
          [:user/email :string]
          [:user/username :string]
          [:user/followed {:optional true} [:set :work/id]]
          [:user/joined-at inst?]
          [:user/stripe-account {:optional true} :string]
          [:user/stripe-customer {:optional true} :string]]

   :sponsorship/id :uuid
   :sponsorship [:map {:closed true}
                 :xt/id :sponsorship/id]


   :admin/id :uuid
   :admin [:map {:closed true}
           [:xt/id :admin/id]
           [:admin/user :user/id]
           [:admin/promoted-by {:optional true} :admin/id]]

   :author/id :uuid
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
             [:chapter/created-at inst?]
             [:chapter/comments {:optional true} [:vector :comment/id]]]

   :genre/id :keyword
   :genre [:map {:closed true}
           [:xt/id :genre/id]
           [:genre/description :string]
           [:genre/display-name :string]]

   :comment/id :uuid
   :comment [:map {:closed true}
             [:xt/id :comment/id]
             [:comment/owner :user/id]
             [:comment/content :string]
             [:comment/timestamp inst?]
             [:comment/replies {:optional true} [:set :comment/id]]]})

(def features
  {:schema schema})
