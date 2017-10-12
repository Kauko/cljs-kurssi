(ns widgetshop.main
  "Main entrypoint for the widgetshop frontend."
  (:require [reagent.core :as r]
            [cljsjs.material-ui]
            [cljs-react-material-ui.core :refer [get-mui-theme color]]
            [cljs-react-material-ui.reagent :as ui]
            [cljs-react-material-ui.icons :as ic]
            [widgetshop.app.state :as state]
            [widgetshop.app.products :as products]
            [widgetshop.server :as server]))

(defn- star [state]
  [:span {:style {:width "20px"
                  :height "20px"
                  :margin "2px"
                  :border-color "black"
                  :border-style "solid"
                  :display "inline-block"
                  }}
   [:span {:style (cond->
                    {:width "10px"
                     :height "20px"
                     :display "inline-block"}

                    (#{:full :half} state)
                    (assoc :background-color "yellow"))}]
   [:span {:style (cond->
                    {:width "10px"
                     :height "20px"
                     :display "inline-block"}

                    (= :full state)
                    (assoc :background-color "yellow"))}]])

(defn- star-state [avg num]
  (let [full-stars (int avg)
        final-star (- avg (int avg))]
    (take num
          (concat
            (take full-stars (repeat :full))
            (when (>= final-star 0.5) [:half])
            (repeat nil)))))

(defn star-rating
  ([rating] [star-rating rating nil])
  ([rating ratings_count]
   (let [states (star-state rating 5)]
     [:div
      (doall
        (map-indexed
          (fn [i state]
            ^{:key (str "star_" i)}
            [star state])
          states))
      (when ratings_count
        (str ratings_count " ratings"))])))

(defn product-view [{:keys [name description ratings] :as product}]
  (when product
    [ui/card
     {:initially-expanded true}
     [ui/card-header {:title name}]
     [ui/card-text description]
     (if (= :loading ratings)
       [ui/circular-progress]

       [:ul
        (doall
          (map-indexed
            (fn [i {:keys [rating review]}]
              ^{:key (str i "_review")}
              [:li
               [star-rating rating]
               review])
            ratings))])]))

(defn- add-to-cart [app product]
  (update app :cart conj product))

(defn- select-product [app product]
  (assoc app :selected-product product))

(defn- set-product-rating [app product ratings]
  (assoc-in app [:selected-product :ratings] (map #(select-keys % [:rating :review]) ratings)))

(defn- load-product-ratings! [app product]
  (server/get! (str "/ratings/" (:id product))
               {:on-success #(state/update-state! set-product-rating product %)})
  (assoc-in app [:selected-product :ratings] :loading))

(defn select-product! [products row-index]
  (state/update-state! select-product (get products (first (js->clj row-index))))
  (state/update-state! load-product-ratings! (get products (first (js->clj row-index)))))

(defn products-list [products]
  (if (= :loading products)
    [ui/circular-progress]

    [ui/table {:on-row-selection (partial select-product! products)}
     [ui/table-header {:display-select-all false :adjust-for-checkbox false}
      [ui/table-row
       [ui/table-header-column "Name"]
       [ui/table-header-column "Description"]
       [ui/table-header-column "Price (€)"]
       [ui/table-header-column "Rating"]
       [ui/table-header-column "Add to cart"]]]
     [ui/table-body {:display-row-checkbox false}
      (for [{:keys [id name description price rating ratings_count] :as product} products]
        ^{:key id}
        [ui/table-row
         [ui/table-row-column name]
         [ui/table-row-column description]
         [ui/table-row-column price]
         [ui/table-row-column [star-rating rating ratings_count]]
         [ui/table-row-column
          [ui/flat-button {:primary true :on-click #(state/update-state! add-to-cart product)}
           "Add to cart"]]])]]))

(defn widgetshop [app]
  [ui/mui-theme-provider
   {:mui-theme (get-mui-theme
                {:palette {:text-color (color :green600)}})}
   [:div
    [ui/app-bar {:title "Widgetshop!"
                 :icon-element-right
                 (r/as-element [ui/badge {:badge-content (count (:cart app))
                                          :badge-style {:top 12 :right 12}}
                                [ui/icon-button {:tooltip "Checkout"}
                                 (ic/action-shopping-cart)]])}]
    [ui/paper

     ;; Product category selection
     (when-not (= :loading (:categories app))
       [ui/select-field {:floating-label-text "Select product category"
                         :value (:id (:category app))
                         :on-change (fn [evt idx value]
                                      (products/select-category-by-id! value))}
        (for [{:keys [id name] :as category} (:categories app)]
          ^{:key id}
          [ui/menu-item {:value id :primary-text name}])])

     ;; Product listing for the selected category
     (let [products ((:products-by-category app) (:category app))]
       [products-list products])

     [ui/raised-button {:label        "Click me"
                        :icon         (ic/social-group)
                        :on-click     #(println "clicked")}]]

    [product-view (:selected-product app)]]])


(defn main-component []
  [widgetshop @state/app])

(defn ^:export main []
  (products/load-product-categories!)
  (r/render-component [main-component] (.getElementById js/document "app")))

(defn ^:export reload-hook []
  (r/force-update-all))
