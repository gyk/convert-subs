(ns cli
  (:require [babashka.cli :as cli]
            [convert-subs.core :refer [convert]]))

(def opt-spec
  {:url            {:ref   "<subs-url>"
                    :desc  "The subscription URL"
                    :alias :u}
   :kind           {:ref     "<kind>"
                    :desc    "The kind of subscription (e.g., 'clash', 'sing-box')"
                    :alias   :k
                    :default "sing-box"}
   :template       {:ref   "<template-yaml>"
                    :desc  "The template YAML file"
                    :alias :t}
   :allow-insecure {:desc    "Allow insecure / Skip cert verify (DANGEROUS!)"
                    :default false}
   :help           {:desc "Print this message"}})

(defn run
  []
  (let [opts (cli/parse-opts
               *command-line-args*
               {:spec     opt-spec
                :restrict true})
        _ (when (:help opts)
            (println (cli/format-opts {:spec opt-spec}))
            (System/exit 0))]
    (println (convert {:url             (:url opts)
                       :kind            (:kind opts)
                       :allow-insecure? (:allow-insecure opts)
                       :template        (:template opts)}))))
