(ns clash-subs
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.walk :refer [keywordize-keys]])
  (:import (java.net URLDecoder)))


(defn- fetch-uri-list
  [url]
  (let [b64-content (slurp url)
        decoder     (java.util.Base64/getDecoder)
        content     (String. (.decode decoder b64-content))]
    (str/split-lines content)))

(def pattern
  #"(?<scheme>\w+):\/\/(?<password>[0-9a-fA-F]{8}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{12})@(?<server>[^:]+)+:(?<port>\d+)\?(?<query>[^#]*)(#(?<fragment>.*))?")

(defn- -parse-uri
  [uri]
  (let [m       (doto
                  (re-matcher pattern uri)
                  (re-find)
                  (re-groups))
        names   ["scheme" "password" "server" "port" "query" "fragment"]
        matches (map #(.group m %) names)]
    (into {} (zipmap (map keyword names) matches))))

(defn- parse-query-params
  [q]
  (as-> q $
    (str/split $ #"&")
    (mapv #(str/split % #"=" 2) $)
    (into {} $)))

(defn- parse-uri
  [uri]
  (-> uri
      (-parse-uri)
      (update :query
              #(-> %
                   parse-query-params
                   keywordize-keys))
      (update :fragment #(URLDecoder/decode % "UTF-8"))))

(defn- parsed-uri->clash
  [u allow-insecure?]
  (let [query            (:query u)
        sni              (:sni query)
        skip-cert-verify (contains? #{"1" "true"} (:allowInsecure query))]
    {:name             (:fragment u)
     :server           (:server u)
     :port             (:port u)
     :type             (:scheme u)
     :password         (:password u)
     :sni              sni
     :skip-cert-verify (if allow-insecure?
                         skip-cert-verify
                         false)
     :udp              true}))

(defn- uri-list->yaml
  [uri-list allow-insecure?]
  (let [proxies (map (fn [uri]
                       (-> uri
                           (parse-uri)
                           (parsed-uri->clash allow-insecure?)))
                  uri-list)
        proxies {:proxies proxies}]
    (yaml/generate-string
      proxies
      :dumper-options
      {:indent           4
       :indicator-indent 2})))

(defn convert-subs
  ([subscribe-url]
   (convert-subs subscribe-url false))
  ([subscribe-url allow-insecure?]
   (-> subscribe-url
       (fetch-uri-list)
       (uri-list->yaml allow-insecure?))))

(comment
  (def trojan
    "trojan://deadbeef-dead-beef-dead-fa1100000604@cpc.people.com.cn:443?allowInsecure=1&peer=www.gov.cn&sni=www.gov.cn&type=tcp#%F0%9F%87%AD%F0%9F%87%B0%E9%A6%99%E6%B8%AF%2004%20%7C%20%E4%B8%93%E7%BA%BF")

  (-> trojan
      parse-uri
      (yaml/generate-string :dumper-options {:flow-style :flow})
      println)

)
