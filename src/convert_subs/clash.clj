(ns convert-subs.clash
  (:require
    [clj-yaml.core :as yaml]))

; The whole module probably doesn't work anymore as Clash was cooked.

(defn parsed-uri->item
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

(defn replace-proxies
  [_template _proxies]
  (throw (ex-info "Not implemented" {})))

(comment
  ; Deprecated code

  (defn- valid-proxy-set
    [proxy-names]
    (set (concat ["DIRECT" "REJECT"] proxy-names)))

  (defn uri-list+template->yaml
    [uri-list template xform]
    (let [leaf-proxies       (into [] xform uri-list)
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
)
