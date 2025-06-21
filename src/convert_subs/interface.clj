(ns convert-subs.interface
  (:require
    [convert-subs.clash :as clash]
    [convert-subs.sing-box :as sing-box]))


; ===== Parsed URI -> Config proxy item ===== ;

(defmulti parsed-uri->item (fn [kind _uri] kind))

(defmethod parsed-uri->item :default
  [kind _uri]
  (throw (ex-info "Unimplemented" {:kind kind})))

(defmethod parsed-uri->item :clash
  [_kind uri]
  (clash/parsed-uri->item uri))

(defmethod parsed-uri->item :sing-box
  [_kind uri]
  (sing-box/parsed-uri->item uri))


; ===== Replace Proxies in Template ===== ;

(defmulti replace-proxies (fn [kind _template _proxies] kind))

(defmethod replace-proxies :default
  [kind & _args]
  (throw (ex-info "Unimplemented" {:kind kind})))

(defmethod replace-proxies :clash
  [_kind template proxies]
  (clash/replace-proxies template proxies))

(defmethod replace-proxies :sing-box
  [_kind template proxies]
  (sing-box/replace-proxies template proxies))
