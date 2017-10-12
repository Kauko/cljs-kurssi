(ns widgetshop.services.products
  (:require [widgetshop.components.http :refer [publish! transit-response]]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [routes GET POST]]
            [clojure.java.jdbc :as jdbc]))

(defn fetch-products-for-category [db category]
  (into []
        (comp
          (map #(update % :price double))
          (map #(update % :rating double)))
        (jdbc/query db [(str "SELECT p.id,p.name,p.description,p.price,"
                             "  avg(pr.rating) AS rating, count(pr.rating) AS ratings_count"
                             "  FROM product p"
                             "  JOIN product_category pc ON pc.product_id = p.id "
                             "  LEFT JOIN product_rating pr ON pr.product_id = p.id"
                             " WHERE pc.category_id = ?"
                             " GROUP BY p.id, p.name, p.description, p.price")
                        category])))

(defn fetch-product-categories [db]
  (jdbc/query db ["SELECT c.id, c.name, c.description FROM category c"]))

(defrecord ProductsService []
  component/Lifecycle
  (start [{:keys [db http] :as this}]
    (assoc this ::routes
           (publish! http
                     (routes
                      (GET "/categories" []
                           (transit-response
                            (fetch-product-categories db)))
                      (GET "/products/:category" [category]
                           (transit-response
                            (fetch-products-for-category db (Long/parseLong category))))))))
  (stop [{stop ::routes :as this}]
    (stop)
    (dissoc this ::routes)))
