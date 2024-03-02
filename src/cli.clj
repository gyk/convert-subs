(ns cli
  (:require [babashka.cli :as cli]
            [clash-subs]))

(def opt-spec
  {:url            {:ref   "<subs-url>"
                    :desc  "The subscription URL"
                    :alias :u}
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
    (println (clash-subs/convert-subs {:url             (:url opts)
                                       :allow-insecure? (:allow-insecure opts)
                                       :template        (:template opts)}))))
