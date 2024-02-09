(ns cli
  (:require [babashka.cli :as cli]
            [clash-subs]))

(def opt-spec
  {:url            {:ref   "<subs-url>"
                    :desc  "The subscription URL"
                    :alias :u}
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
    (println (clash-subs/convert-subs (:url opts) (true? (:allow-insecure opts))))))
