(ns convert-subs.sing-box)


(defn parsed-uri->item
  [u]
  (let [{:keys [query scheme]} u]
    (case scheme
      "hysteria2"
        (let [{:keys [password server port fragment]} u
              {:keys [insecure sni]} query]
          {:password    password
           :server      server
           :server_port (Integer/parseInt port)
           :tag         fragment
           :tls         {:enabled     true
                         :insecure    (contains? #{"1" "true"} insecure)
                         :server_name sni}
           :type        scheme})

      "vless"
        (let [{:keys [password server port fragment]} u
              {:keys [security host path fp allowInsecure sni type]} query]
          {:flow            ""
           :packet_encoding "xudp"
           :server          server
           :server_port     (Integer/parseInt port)
           :tag             fragment
           :tls             (when (= security "tls")
                              {:enabled     true
                               :insecure    (contains? #{"1" "true"} allowInsecure)
                               :server_name sni
                               :utls        (when fp
                                              {:enabled     true
                                               :fingerprint (case fp
                                                              "safari" "safari"
                                                              "chrome")})})
           :transport       (when (= type "ws")
                              {:early_data_header_name "Sec-WebSocket-Protocol"
                               :headers        {:Host [host]}
                               :max_early_data 2048
                               :path           path
                               :type           "ws"})
           :type            scheme
           :uuid            password})

      (throw (ex-info (str "Scheme not yet supported: " scheme)
                      {:scheme scheme
                       :uri    u})))))

(defn- update-outbound
  [outbound proxies]
  (let [{:keys [type]} outbound
        proxy-tags     (mapv :tag proxies)]
    ; https://sing-box.sagernet.org/configuration/outbound/#fields
    (case type
      ; Pass-through
      ("direct" "block" "dns")
        outbound

      "selector"
        (assoc outbound :outbounds (conj proxy-tags "urltest"))

      "urltest"
        (assoc outbound :outbounds proxy-tags)

      nil)))

(defn- update-outbounds
  [outbounds proxies]
  (concat
    (keep #(update-outbound % proxies) outbounds)
    proxies))


(defn replace-proxies
  [template proxies]
  (update template
          :outbounds
          (fn [outbounds]
            (update-outbounds outbounds proxies))))
