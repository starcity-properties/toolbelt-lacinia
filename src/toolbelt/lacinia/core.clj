(ns toolbelt.lacinia.core
  (:require
    [clojure.tools.macro :as ctm]
    [clojure.spec.alpha :as s]
    [com.walmartlabs.lacinia.resolve :as resolve]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.util :as util]
    [com.walmartlabs.lacinia :as lacinia]))

;; =============================================================================
;; Schema
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


(defn compile-attach
  "Compile `schema` and attach `resolvers`."
  [schema resolvers]
  (schema/compile (util/attach-resolvers schema resolvers)))


(defn parse-scalars
  "Helper function to parse/serialize custom scalars with function `f`, wrapping the call to `f` in a try/catch
  returning nil if exception is thrown according to lacinia's flow."
  [f]
  (fn [v] (try (f v) (catch Throwable _ nil))))


(def execute lacinia/execute)


;; =============================================================================
;; Resolver tools
;; =============================================================================


(defn resolve-errors
  "Return GraphQL error, will call `resolve-as` with nil as first argument and `errors` as the second argument."
  [errors]
  (resolve/resolve-as nil errors))


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
                             :reason  :unauthorized
                             :message "Please login to access these resources."})
    (resolve/resolve-as nil {:status  403
                             :reason  :forbidden
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
(s/def ::resolver (s/and keyword? #(some? (namespace %))))
(s/def ::metadata (s/or :keyword #(= 1 (count %)) :map (s/keys :req-un [::resolver])))

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
  (let [metadata                  (meta sym)
        [sym [args opts body]]    (ctm/name-with-attributes sym body)
        body                      (if (nil? body) opts body)
        [context params resolved] (apply normalize-args* args)
        resolver                  (or (:resolver metadata) (ffirst metadata))]

    (assert (s/valid? ::metadata metadata (s/explain-str ::metadata metadata)))
    (assert (s/valid? ::resolver resolver) (s/explain-str ::resolver resolver))
    (assert (s/valid? ::args args) (s/explain-str ::args args))

    `(defn ~(vary-meta sym merge {:resolver resolver}) ~[context params resolved]
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