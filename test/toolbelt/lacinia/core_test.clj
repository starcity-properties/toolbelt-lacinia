(ns toolbelt.lacinia.core-test
  (:require
    [clojure.test :refer :all]
    [toolbelt.lacinia.core :as lacinia :refer [defresolver]]))


;; =============================================================================
;; Helper handlers
;; =============================================================================


(defn unauthorized-handler [context params resolved]
  {:unauthorized [context params resolved]})


(defn exception-handler [context params resolved exception]
  {:exception [context params resolved]})


;; =============================================================================
;; Dummy resolvers
;; =============================================================================

(def dummy-account {:db/id 123})

(defn resolver-body
  [body {:keys [authorized unauthorized-handler exception-handler]}]
  (let [auth-fn     (or unauthorized-handler lacinia/*unauthorized-handler*)
        exc-handler (or exception-handler lacinia/*exception-handler*)]
    (fn [context params resolved]
      (try
        (if-not (lacinia/authorized? authorized)
          (auth-fn context params resolved)
          (body context params resolved))
        (catch Throwable t
          (exc-handler context params resolved t))))))


(defn resolver
  [params]
  (resolver-body (fn [context params resolved] [context params resolved]) params))

(defn resolver-with-exception
  [params]
  (resolver-body (fn [_ _ _] (throw (ex-info "Test exception" {}))) params))


(defresolver ^::resolver macro-resolver-authorized
  [{:keys [requester] :as ctx} params resolved]
  {:authorization (= (:db/id dummy-account) (:db/id requester))}
  [ctx params resolved])


(defresolver ^::resolver macro-resolver-unauthorized-handler
  [{:keys [requester]} params resolved]
  {:authorization        [(= (:db/id dummy-account) (:db/id requester))]
   :unauthorized-handler unauthorized-handler}
  (do nil))


(defresolver ^::resolver macro-resolver-exception
  [{:keys [requester] :as ctx} params resolved]
  {:authorization     (= (:db/id dummy-account) (:db/id requester))
   :exception-handler exception-handler}
  (throw (ex-info "test exception" {})))


;; =============================================================================
;; Tests
;; =============================================================================

(deftest defresolver-test
  (testing "Macro should produce valid content if authorized."
    (is (= [{:requester dummy-account} {} {}]
           ((resolver {:authorized true}) {:requester dummy-account} {} {})
           (macro-resolver-authorized {:requester dummy-account} {} {})))

    (testing "Unauthorized handler should be used if provided."
      (let [[context params resolved :as args] [{:requester nil} {} {}]]
        (is (= {:unauthorized args}
               ((resolver {:authorized           false
                           :unauthorized-handler unauthorized-handler}) context params resolved)
               (macro-resolver-unauthorized-handler context params resolved)))))

    (testing "Exception thrown should be handled by exception-handler if provided."
      (let [[context params resolved :as args] [{:requester dummy-account} {} {}]]
        (is (= {:exception args}
               ((resolver-with-exception {:authorized        true
                                          :exception-handler exception-handler}) context params resolved)
               (macro-resolver-exception context params resolved)))))

    (testing "Exceptions thrown in a resolver should be wrapped and not thrown by the resolver."
      (let [[context params resolved] [{:requester true} {} {}]]
        (is (macro-resolver-exception context params resolved))))))