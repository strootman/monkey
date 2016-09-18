(ns monkey.core-test
  (:use clojure.test
        monkey.core)
  (:require [monkey.props :as config])
  (:import [java.net URL]
           [java.util Properties]))

(def empty-props (Properties.))

(deftest test-default-config
  (testing "default configuration settings"
    (is (= (config/amqp-uri empty-props) "amqp://guest:guestPW@localhost:5672"))
    (is (= (config/amqp-exchange-name empty-props) "de"))
    (is (true? (config/amqp-exchange-durable? empty-props)))
    (is (false? (config/amqp-exchange-auto-delete? empty-props)))
    (is (= (config/amqp-queue empty-props) "monkey"))
    (is (= (config/es-batch-size empty-props) 1000))
    (is (= (config/es-url empty-props) (URL. "http://elasticsearch:9200")))
    (is (= (config/es-index empty-props) "data"))
    (is (= (config/es-tag-type empty-props) "tag"))
    (is (= (config/es-scroll-size empty-props) 1000))
    (is (= (config/es-scroll-timeout empty-props) "1m"))
    (is (false? (config/log-progress? empty-props)))
    (is (= (config/progress-logging-interval empty-props) 10000))
    (is (= (config/retry-period empty-props) 1000))
    (is (= (config/tags-host empty-props) "dedb"))
    (is (= (config/tags-port empty-props) 5432))
    (is (= (config/tags-db empty-props) "metadata"))
    (is (= (config/tags-user empty-props) "de"))
    (is (= (config/tags-password empty-props) "notprod"))
    (is (= (config/tags-batch-size empty-props) 10))))
