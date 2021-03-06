(ns ethlance.pages.invoice-detail-page
  (:require
    [cljs-react-material-ui.reagent :as ui]
    [ethlance.components.message-bubble :refer [message-bubble]]
    [ethlance.components.misc :as misc :refer [col row paper row-plain line a currency]]
    [ethlance.constants :as constants]
    [ethlance.ethlance-db :as ethlance-db]
    [ethlance.styles :as styles]
    [ethlance.utils :as u]
    [goog.string :as gstring]
    [re-frame.core :refer [subscribe dispatch]]
    [reagent.core :as r]))

(defn invoice-description [{:keys [:invoice/description :invoice/contract]}]
  (when (seq description)
    [message-bubble
     {:side :left
      :user (:contract/freelancer contract)}
     description]))

(defn invoice-info [{:keys [:invoice/status :invoice/contract :invoice/created-on :invoice/amount
                            :invoice/worked-hours :invoice/worked-minutes :invoice/worked-from :invoice/worked-to
                            :invoice/rate :invoice/paid-on :invoice/cancelled-on :invoice/conversion-rate
                            :invoice/id]
                     :as invoice}]
  (let [{:keys [:contract/job :contract/freelancer]} contract
        {:keys [:job/payment-type :job/reference-currency]} job
        created-on-timestamp (u/get-time created-on)]
    [misc/call-on-change
     {:args id
      :on-change #(dispatch [:load-conversion-rates-historical created-on-timestamp])
      :load-on-mount? true}
     [row
      [invoice-description invoice]
      [col
       {:xs 12}
       [line "Created by" [a {:route-params (select-keys freelancer [:user/id])
                              :route :freelancer/detail}
                           (:user/name freelancer)]]
       [line "Job" [a {:route-params (select-keys job [:job/id])
                       :route :job/detail}
                    (:job/title job)]]
       [line "Proposal" [a {:route-params (select-keys contract [:contract/id])
                            :route :contract/detail}
                         (:contract/id contract)]]
       [:div {:style styles/margin-top-gutter}]
       [line "Worked" (str worked-hours
                           (u/pluralize " hour" worked-hours)
                           (when (pos? worked-minutes)
                             (str " " worked-minutes (u/pluralize " minute" worked-minutes))))]
       (when (or (pos? reference-currency) (= payment-type 1))
         [line (cond
                 (= payment-type 1) (constants/payment-types 1)
                 (pos? reference-currency) (constants/payment-types 2))
          (u/format-currency rate reference-currency {:full-length? true})])
       (when (pos? reference-currency)
         [:div
          [line "Converted with rate"
           [misc/conversion-rate {:currency reference-currency :value conversion-rate}]]
          (let [[tampered? correct-rate] @(subscribe [:db/conversion-rate-tampered?
                                                      created-on-timestamp
                                                      conversion-rate
                                                      reference-currency])]
            (when tampered?
              [:div
               {:style {:color styles/accent1-color}}
               "Looks like this conversion rate is invalid. Correct should be"
               [misc/conversion-rate
                {:currency reference-currency
                 :value correct-rate}]
               ". Please report this to Ethlance team"]))])
       (when (and (pos? reference-currency)
                  (= payment-type 1))
         [line (str "Amount in " (u/currency-full-name reference-currency))
          (u/format-currency (u/ether->currency amount
                                                reference-currency
                                                {reference-currency (u/big-num->num conversion-rate)})
                             reference-currency)])
       [line "Amount" (u/format-currency amount 0 {:full-length? true})]

       [:div {:style styles/margin-top-gutter}]
       [line "Worked from" (u/format-date worked-from)]
       [line "Worked to" (u/format-date worked-to)]
       [line "Created on" (u/format-datetime created-on)]
       (when paid-on
         [line "Paid on" (u/format-datetime paid-on)])
       (when cancelled-on
         [line "Cancelled on" (u/format-datetime cancelled-on)])]]]))

(defn invoice-detail-page []
  (let [invoice (subscribe [:invoice/detail])
        invoice-id (subscribe [:invoice/route-invoice-id])
        by-me? (subscribe [:invoice/by-me?])
        for-me (subscribe [:invoice/for-me?])
        form-pay (subscribe [:form.invoice/pay-invoice])
        form-cancel (subscribe [:form.invoice/cancel-invoice])]
    (fn []
      (let [{:keys [:invoice/contract :invoice/id :invoice/created-on :invoice/status
                    :invoice/amount :invoice/conversion-rate]} @invoice
            job-title (get-in contract [:contract/job :job/title])]
        [misc/call-on-change
         {:load-on-mount? true
          :args @invoice-id
          :on-change #(dispatch [:after-eth-contracts-loaded
                                 [:contract.db/load-invoices ethlance-db/invoice-entity-fields [@invoice-id]]])}
         [misc/center-layout
          [paper
           {:loading? (or (empty? job-title)
                          (not conversion-rate)
                          (:loading? @form-pay)
                          (:loading? @form-cancel))
            :style styles/paper-section-main}
           [row-plain
            {:middle "xs"
             :between "xs"
             :style styles/margin-bottom-gutter}
            [:h1 "Invoice #" id]
            [misc/status-chip
             {:background-color (styles/invoice-status-colors status)}
             (constants/invoice-status status)]]
           (when (seq job-title)
             [:div
              [invoice-info @invoice]
              [row-plain
               {:end "xs"}
               (when (and (= status 1) @by-me?)
                 [ui/raised-button
                  {:secondary true
                   :label "Cancel Invoice"
                   :disabled (:loading? @form-cancel)
                   :style styles/margin-top-gutter-less
                   :on-touch-tap #(dispatch [:contract.invoice/cancel-invoice {:invoice/id id}])}])
               (when (and (= status 1) @for-me)
                 [ui/raised-button
                  {:primary true
                   :label "Pay Invoice"
                   :disabled (:loading? @form-pay)
                   :style styles/margin-top-gutter-less
                   :on-touch-tap #(dispatch [:contract.invoice/pay-invoice {:invoice/id id} amount])}])]])]]]))))