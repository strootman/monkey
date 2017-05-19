(ns monkey.index
  "This namespace implements the Indexes protocol where elastisch library is used to interface with
   the search index."
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojurewerkz.elastisch.query :as query]
            [clojurewerkz.elastisch.rest :as es]
            [clojurewerkz.elastisch.rest.bulk :as bulk]
            [clojurewerkz.elastisch.rest.document :as doc]
            [clojurewerkz.elastisch.rest.response :as resp]
            [monkey.props :as props]
            [clojurewerkz.elastisch.arguments :as ar])
  (:import [java.util UUID]
           [clojure.lang ISeq PersistentArrayMap]
           (clojurewerkz.elastisch.rest Connection)))


(defn scroll
  "Performs a scroll query, fetching the next page of results from a
   query given a scroll id"
  [^Connection conn scroll-id & args]
  (let [opts (ar/->opts args)
        qk   [:search_type :scroll :routing :preference]
        qp   (select-keys opts qk)
        body {:scroll_id scroll-id}]
    (es/post conn (es/scroll-url conn)
                      {:body body
                       :query-params qp})))

(defn- init-tag-seq
  [props es]
  (let [res (doc/search es (props/es-index props) (props/es-tag-type props)
              :query       (query/match-all)
              :_source     ["_id"]
              :sort        ["_doc"]
              :scroll      (props/es-scroll-timeout props)
              :size        (props/es-scroll-size props))]
    (if (resp/any-hits? res)
      (scroll es (:_scroll_id res) :scroll (props/es-scroll-timeout props))
      res)))


(defn- log-failures
  [res op log-failure]
  (let [fails (->> (:items res)
                (map op)
                (remove #(and (>= 200 (:status %)) (< 300 (:status %)))))]
    (doseq [fail fails]
      (log-failure (:_id fail)))))


(defn- log-index-failure
  [id]
  (log/warn "Unable to index the document for the tag" (str id)))


(defn- log-remove-failure
  [id]
  (log/warn "Unable to remove the indexed document for the tag" (str id)))


; This is how clojurewerkz.elastisch.rest.bulk/bulk-index should have been implemented
(defn- bulk-index
  "generates the content for a bulk insert operation, but allows an _id to be provided"
  ([documents]
    (let [operations (map bulk/index-operation documents)
          documents  (map #(dissoc % :_index :_type :_id) documents)]
      (interleave operations documents))))


(defn- index
  [es props tags]
  (let [fmt-tag (fn [tag] (assoc tag :_id (:id tag)))
        resp    (bulk/bulk-with-index-and-type es
                  (props/es-index props)
                  (props/es-tag-type props)
                  (bulk-index (map fmt-tag tags)))]
    (when (:errors resp)
      (log-failures (:items resp) :create log-index-failure))))


(defn- remove-ids
  [es props ids]
  (let [fmt-id (fn [id] {:_id (str id)})
        resp   (bulk/bulk-with-index-and-type es
                 (props/es-index props)
                 (props/es-tag-type props)
                 (bulk/bulk-delete (map fmt-id ids)))]
    (when (:errors resp)
      (log-failures (:items resp) :delete log-remove-failure))))


(defprotocol Indexes
  "This protocol defines the operations needed to interact with the data search index."

  (^ISeq all-tags [_]
    "returns a sequence of all of the UUIDs for the tag documents in the search index")

  (^Integer count-tags [_]
    "counts the number of tag documents currently in the search index")

  (index-tags [_ ^ISeq tags]
    "adds the provided tag documents to the search index")

  (remove-tags [_ ^ISeq ids]
    "Removes the tags with the provided ids from the search index"))


(deftype ^{:private true} Index [props es]
  Indexes

  (all-tags [_]
    (map #(UUID/fromString (:_id %)) (doc/scroll-seq es (init-tag-seq props es))))

  (count-tags [_]
    (:count (doc/count es (props/es-index props) (props/es-tag-type props))))

  (index-tags [_ tags]
    (try
      (index es props tags)
      (catch Throwable t
        (log/debug t "failed to index tags")
        (doseq [tag tags]
          (log-index-failure (:id tag))))))

  (remove-tags [_ ids]
    (try
      (remove-ids es props ids)
      (catch Throwable t
        (log/debug t "failed to remove tags")
        (doseq [id ids]
          (log-remove-failure id))))))


(defn ^monkey.index.Indexes mk-index
  "creates the object used to interact with the search index

   Parameters:
     props - The configuration properties map

   Returns:
     It returns the object."
  [^PersistentArrayMap props]
  (let [url (str (props/es-url props))
        http-opts (if (or (empty? (props/es-user props)) (empty? (props/es-password props)))
                    {}
                    {:basic-auth [(props/es-user props) (props/es-password props)]
                     :content-type :application/json})
        es (es/connect url http-opts)]
    (log/debug (format "Http-opts=%s user=%s password=%s" http-opts (props/es-user props) (props/es-password props)))
    (log/info (format "Successfully connected to Elasticsearch: %s" url))
    (->Index props
             es)))
