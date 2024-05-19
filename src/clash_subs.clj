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
  [u]
  (let [query            (:query u)
        sni              (:sni query)
        skip-cert-verify (contains? #{"1" "true"} (:allowInsecure query))]
    {:name             (:fragment u)
     :server           (:server u)
     :port             (:port u)
     :type             (:scheme u)
     :password         (:password u)
     :sni              sni
     :skip-cert-verify skip-cert-verify
     :udp              true}))

(defn- valid-proxy-set
  [proxy-names]
  (set (concat ["DIRECT" "REJECT"] proxy-names)))

(defn- uri-list->proxies
  [uri-list]
  (let [proxies (map (fn [uri]
                       (-> uri
                           (parse-uri)
                           (parsed-uri->clash)))
                  uri-list)]
    proxies))

(defn- uri-list->yaml
  [uri-list xform]
  (let [proxies (->> uri-list
                     (uri-list->proxies)
                     (into [] xform))
        proxies {:proxies proxies}]
    (yaml/generate-string
      proxies
      :dumper-options
      {:indent           4
       :indicator-indent 2})))

(defn- uri-list+template->yaml
  [uri-list template xform]
  (let [leaf-proxies       (->> uri-list
                                (uri-list->proxies)
                                (into [] xform))
        leaf-proxy-names   (map :name leaf-proxies)
        branch-proxy-names (map :name (:proxy-groups template))
        valid-branch-names (valid-proxy-set branch-proxy-names)
        valid-names        (valid-proxy-set (concat leaf-proxy-names branch-proxy-names))
        update-group       (fn [group]
                             (update group
                                     :proxies
                                     (fn [proxy-names]
                                       (if (not-any? valid-branch-names proxy-names)
                                         leaf-proxy-names
                                         (keep valid-names proxy-names)))))
        template           (-> template
                               (assoc :proxies leaf-proxies)
                               (update :proxy-groups #(map update-group %)))]

    (yaml/generate-string
      template
      :dumper-options
      {:indent           4
       :indicator-indent 2
       :flow-style       :block})))

(defn convert-subs
  [{:keys [url template allow-insecure? remove-localhost?]
    :or   {allow-insecure?   false
           remove-localhost? true}}]
  (let [uri-list (fetch-uri-list url)
        template (some-> template
                         slurp
                         yaml/parse-string)
        xforms   (cond-> []
                   (not allow-insecure?)
                     (conj (map #(assoc % :skip-cert-verify false)))
                   remove-localhost?
                     (conj (remove #(#{"0.0.0.0"
                                       "127.0.0.1"
                                       "localhost"}
                                      (:server %)))))
        xform    (apply comp xforms)]
    (if template
      (uri-list+template->yaml uri-list
                               template
                               xform)
      (uri-list->yaml uri-list xform))))

(comment
  (def trojan
    "trojan://deadbeef-dead-beef-dead-fa1100000604@cpc.people.com.cn:443?allowInsecure=1&peer=www.gov.cn&sni=www.gov.cn&type=tcp#%F0%9F%87%AD%F0%9F%87%B0%E9%A6%99%E6%B8%AF%2004%20%7C%20%E4%B8%93%E7%BA%BF")

  (-> trojan
      parse-uri
      (yaml/generate-string :dumper-options {:flow-style :flow})
      println)

)
