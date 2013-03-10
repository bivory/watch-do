(ns watch-do.core
  (:require [clojure.pprint :as pp]
            [clojure.java.shell :as shell]
            [clojure.tools.cli :as cli]
            [watch-do.watcher :as watcher]))

(defn- print-shell-results
  [results]
  (let [{:keys [exit out err] :or [out "" err ""]} results]
    (when (not= 0 exit)
      (println "Error" exit)
      (println err))
    (println out)
    results))

(defn- handle-change
  [cmd cmd-opts ev ctx]
  (println ev "-->" (.toString (.toAbsolutePath ctx)))
  (try
    (let [results (if (coll? cmd-opts)
                    (apply shell/sh cmd cmd-opts)
                    (shell/sh cmd cmd-opts))]
      (print-shell-results results))
    (catch Exception e (str "caught exception: " (.getMessage e)))))

(defn- add-watch-do
  [cmd cmd-opts path]
  (let [handler (partial handle-change cmd cmd-opts)]
    ;;(println "Watching" path "-->" cmd)
    (watcher/watch path
                   :create handler
                   :delete handler
                   :modify handler)))

(defn- add-group-watch-do
  [cmd cmd-opts group]
  (let [watch (partial add-watch-do cmd cmd-opts)]
    (if (coll? group)
      (map watch group)
      (watch group))))

(defn- add-groups-watch-do
  "[{:cmd 'command' :cmd-opt 'opts' :files [...]}]"
  [groups]
  (map #(add-group-watch-do (% :cmd) (% :cmd-opts) (% :files)) groups))

(defn -main
  [& args]
  (let [[opts args banner]
        (cli/cli args
                 ["-h" "--help" "Show help" :flag true :default false]
                 ["-w" "--watch" "Watch files"])]
    (when (or (:help opts) (= nil (:watch opts)))
      (println banner)
      (System/exit 0))
    ;;(println "Parsed opts: ")
    ;;(pp/pprint opts)
    (let [group-file (:watch opts)
          groups (load-file group-file)]
      (println (str group-file ":"))
      (pp/pprint groups)
      (add-groups-watch-do (or groups [])))))
