(ns toolbelt.lacinia.core
  (:require
    [clojure.tools.macro :as ctm]
    [clojure.spec.alpha :as s]
    [com.walmartlabs.lacinia.resolve :as resolve]))

;; =============================================================================
;; Helpers
;; =============================================================================


(defn read-resolvers
  "Read all resolvers declared with `defresolver` in the given `namespaces`. Will read all namespaces
  if `namespaces` is not provided."
  ([] (apply read-resolvers (all-ns)))
  ([& namespaces]
   (letfn [(resolvers* [m [_ v]]
             (if-some [k (:resolver (meta v))]
               (assoc m k v)
               m))]
     (reduce resolvers* {} (mapcat ns-publics namespaces)))))


;; =============================================================================
;; Authorization
;; =============================================================================

(defn authorized?
  "Return true if the specified `authorization` option is true for single value or all true for sequential value."
  [authorization]
  (if (sequential? authorization)
    (every? true? authorization)
    (true? authorization)))


(defn default-unauthorized-handler
  "Default unauthorized handler, which will lookup the :identity key in the provided `context` and return 401 if nil,
  otherwise 403."
  [context params resolved]
  (if (nil? (:identity context))
    (resolve/resolve-as nil {:status  401
                             :reason  :unauthenticated
                             :message "Please login to access these resources."})
    (resolve/resolve-as nil {:status  403
                             :reason  :unauthorized
                             :message "You don't have access to these resources."})))


(def ^:dynamic *unauthorized-handler* default-unauthorized-handler)


;; =============================================================================
;; Exceptions
;; =============================================================================


(defn default-exception-handler
  "Default exception handler which will return GraphQL error status 500."
  [context params resolved exception]
  (resolve/resolve-as nil {:status  500
                           :reason  :unknown
                           :message "Unexpected API error"
                           :data    (ex-data exception)}))


(def ^:dynamic *exception-handler* default-exception-handler)


;; =============================================================================
;; Macro
;; =============================================================================


(s/def ::args (s/tuple any? any? any?))

(defn- normalize-args* [& args]
  (map (fn [m]
         (if (and (map? m) (nil? (:as m)))
           (assoc m :as (gensym))
           m))
       args))


(defmacro defresolver
  "Helper macro to define a GraphQL resolver function that will catch any exceptions and return a GraphQL error.
  Takes a symbol with a keyword as metadata to identify resolver as first argument, a map with authorization
  conditions as second argument and a body that should evaluate to valid GraphQL result.
  :authorization - predicate, collection of predicate or function to determine authorization for the resolver."
  [sym & body]
  (let [[sym [args opts body]] (ctm/name-with-attributes sym body)
        body           (if (nil? body) opts body)
        [context params resolved] (apply normalize-args* args)]

    (assert (s/valid? ::args args) (s/explain-str ::args args))

    `(defn ~(vary-meta sym merge {:resolver (ffirst (meta sym))}) ~[context params resolved]
       (let [context#           ~(or (:as context) context)
             params#            ~(or (:as params) params)
             resolved#          ~(or (:as resolved) resolved)
             handle-unauthed#    ~(or (:unauthorized-handler opts) *unauthorized-handler*)
             exception-handler# ~(or (:exception-handler opts) *exception-handler*)]
         (try
           (if-not (authorized? ~(:authorization opts))
             (handle-unauthed# context# params# resolved#)
             ~body)
           (catch Throwable t#
             (exception-handler# context# params# resolved# t#)))))))