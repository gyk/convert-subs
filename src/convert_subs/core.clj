(ns convert-subs.core
  (:require
    [cheshire.core :as json]
    [clj-yaml.core :as yaml]
    [clojure.string :as str]
    [convert-subs.interface :refer [parsed-uri->item replace-proxies]])
  (:import
    (java.net URLDecoder)))


(defn- fetch-uri-list
  [url]
  (let [b64-content (slurp url)
        decoder     (java.util.Base64/getDecoder)
        content     (String. (.decode decoder b64-content))]
    (str/split-lines content)))

(def pattern
  #"(?<scheme>\w+):\/\/(?<password>[0-9a-fA-F]{8}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{12})@(?<server>[^:]+)+:(?<port>\d+)/?\?(?<query>[^#]*)(#(?<fragment>.*))?")

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
    (mapv (fn [[k v]]
            [(keyword k) (URLDecoder/decode v "UTF-8")])
      $)
    (into {} $)))

(defn- parse-uri
  [uri]
  (-> uri
      (-parse-uri)
      (update :query parse-query-params)
      (update :fragment #(URLDecoder/decode % "UTF-8"))))

(defn- uri-list->proxies
  [uri-list xform]
  (into [] xform uri-list))


(defn convert-uri-list
  [uri-list
   {:keys [kind template allow-insecure? remove-localhost?]
    :or   {allow-insecure?   false
           remove-localhost? true}}]
  (let [kind        (keyword kind)
        output-type (case kind
                      :clash    :yaml
                      :sing-box :json
                      (throw (ex-info "Unsupported kind" {:kind kind})))

        template    (some-> template
                            slurp
                            yaml/parse-string)
        xforms      (cond-> [(map parse-uri)
                             (map #(parsed-uri->item kind %))]
                      (and (= kind :clash) allow-insecure?)
                        (conj (map #(assoc % :skip-cert-verify false)))

                      remove-localhost?
                        (conj (remove #(#{"0.0.0.0"
                                          "127.0.0.1"
                                          "localhost"}
                                         (:server %)))))
        xform       (apply comp xforms)
        proxies     (uri-list->proxies uri-list xform)
        config      (if template
                      (replace-proxies kind
                                       template
                                       proxies)
                      proxies)]
    (if (= output-type :yaml)
      (yaml/generate-string
        config
        :dumper-options
        {:indent           4
         :indicator-indent 2})
      (json/encode
        config
        {:pretty (json/create-pretty-printer
                   (assoc json/default-pretty-print-options
                     :indentation    "    "
                     :indent-arrays? true))}))))

(defn convert
  [{:keys [url] :as params}]
  (let [uri-list (fetch-uri-list url)]
    (convert-uri-list uri-list params)))

(comment
  ; Portal setup
  (require '[babashka.deps :as deps])
  (deps/add-deps '{:deps {djblue/portal {:mvn/version "0.59.1"}}})
  (require '[portal.api :as p])

  (do
    (p/open
      {:app      false
       :launcher :vs-code})
    (add-tap #'p/submit))

  (do
    (remove-tap #'p/submit)
    (p/close))

  (tap> :go)
)

(comment
  (def hysteria2
    "hysteria2://deadbeef-dead-beef-dead-fa1100000604@cpc.people.com.cn:20000/?insecure=1&sni=www.gov.cn&mport=20000-55000#%E5%89%A9%E4%BD%99%E6%B5%81%E9%87%8F%EF%BC%9A95.78%20GB")
  (def trojan
    "trojan://deadbeef-dead-beef-dead-fa1100000604@cpc.people.com.cn:443?allowInsecure=1&peer=www.gov.cn&sni=www.gov.cn&type=tcp#%F0%9F%87%AD%F0%9F%87%B0%E9%A6%99%E6%B8%AF%2004%20%7C%20%E4%B8%93%E7%BA%BF")

  (parsed-uri->item :sing-box (parse-uri hysteria2))
  (parsed-uri->item :clash (parse-uri trojan))
)
