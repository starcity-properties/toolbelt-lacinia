(ns toolbelt.lacinia.core
  (:require
    [clojure.tools.macro :as ctm]
    [com.walmartlabs.lacinia.resolve :as resolve]))

;; =============================================================================
;; Helpers
;; =============================================================================


(defn read-resolvers
  ([] (read-resolvers (all-ns)))
  ([namespaces]
   (letfn [(resolvers* [m [_ v]]
             (if-some [k (:resolver (meta v))]
               (assoc m k v)
               m))]
     (reduce resolvers* {} (mapcat ns-publics namespaces)))))


(defn authorized? [{:keys [authorization]} {:keys [requester]}]
  (letfn [(authorized?* [auth]
            (if (fn? auth) (auth requester) (true? auth)))]
    (if (sequential? authorization)
      (every? authorized?* authorization)
      (authorized?* authorization))))


;; =============================================================================
;; Handle errors
;; =============================================================================

(defn handle-unauthorized
  [{:keys [requester]}]
  (if (nil? requester)
    (resolve/resolve-as nil {:status  401
                             :reason  :unauthenticated
                             :message "Please login to access these resources."})
    (resolve/resolve-as nil {:status  403
                             :reason  :unauthorized
                             :message "You don't have access to these resources."})))


(defn handle-exception
  [{:keys [logger]} exception]
  (resolve/resolve-as nil {:message "Unexpected API error"
                           :status  500
                           :data    (ex-data exception)}))


;; =============================================================================
;; Macro
;; =============================================================================


(s/def ::args (s/tuple any? any? any?))

(defmacro defresolver
  "Helper macro to define a GraphQL resolver function that will catch any exceptions and return a GraphQL error.
  Takes a symbol with a keyword as metadata to identify resolver as first argument, a map with authorization
  conditions as second argument and a body that should evaluate to valid GraphQL result.
  :authorization - predicate, collection of predicate or function to determine authorization for the resolver."
  [sym & body]
  (let [[sym [args opts body]]      (ctm/name-with-attributes sym body)
        body                        (if (nil? body) opts body)
        [context params resolved]   args
        context                     (if (and (map? context)
                                             (nil? (:as context)))
                                      (assoc context :as (gensym "ctx"))
                                      context)]

    (assert (s/valid? ::args args) (s/explain-str ::args args))

    `(defn ~(vary-meta sym merge {:resolver (ffirst (meta sym))}) ~[context params resolved]
       (let [context# ~(or (:as context) context)]
         (try
           (if-not (authorized? ~opts context#)
             (handle-unauthorized context#)
             ~body)
           (catch Throwable t#
             (handle-exception context# t#)))))))