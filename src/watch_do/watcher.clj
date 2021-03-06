(ns watch-do.watcher
  "Cloned from https://gist.github.com/moonranger/4023683"
  (:require [clojure.pprint :as pp])
  (:import (java.nio.file FileSystems
                          Path
                          Paths
                          StandardWatchEventKinds
                          WatchService
                          WatchKey
                          WatchEvent)
           (java.io File)
           (java.util.concurrent TimeUnit)))

(def ^:private kw-to-event
  {:create StandardWatchEventKinds/ENTRY_CREATE
   :delete StandardWatchEventKinds/ENTRY_DELETE
   :modify StandardWatchEventKinds/ENTRY_MODIFY})

(def ^:private event-to-kw (->> kw-to-event
                                (map (fn [[k v]] [v k]))
                                (into {})))

(def ^:dynamic *watch-timeout* 1)

(def ^:private initial-stats {:watcher nil
                              :fs nil
                              :running false
                              :watching-paths {}
                              :watching-files {}})
(def ^:private watch-stats (atom initial-stats))

(defn- get-or-create-watch-service
  []
  (if-let [watcher (:watcher @watch-stats)]
    watcher
    (let [fs (FileSystems/getDefault)
          watcher (.newWatchService fs)]
      (swap! watch-stats merge {:watcher watcher :fs fs})
      watcher)))

(defn- get-file-parent-directory
  [fs file]
  (let [parent (.getParentFile (.toFile file))]
    (if (nil? parent)
      (.getPath fs "." (make-array String 0))
      (.toPath parent))))

(defn- file?
  [path]
  (let [f (.toFile path)]
    (.isFile f)))

(defn- directory?
  [path]
  (let [f (.toFile path)]
    (.isDirectory f)))

(defn- handle-watch-events
  [watch-key handlers]
  (let [events (.pollEvents watch-key)]
    (doseq [ev events]
      (let [kind (.kind ev)
            ctx (.context ev)
            file (.toFile ctx)
            handler-key (event-to-kw kind)
            handler (handlers handler-key)]
        (handler handler-key file)))
    (.reset watch-key)))

(defn- poll-events
  [watcher]
  (if-let [watch-key (.poll watcher
                            *watch-timeout*
                            TimeUnit/MINUTES)]
    (future
      (let [path (.watchable watch-key)
            file-handlers (get-in @watch-stats
                                  [:watching-files
                                   path
                                   :handlers])
            path-handlers (get-in @watch-stats
                                  [:watching-paths
                                   path
                                   :handlers])
            handlers (if (nil? file-handlers) path-handlers file-handlers)]
        (handle-watch-events watch-key handlers)))))

(defn- start-watcher
  []
  (when-not (:running @watch-stats)
    (swap! watch-stats assoc :running true)
    (future
      (let [watcher (:watcher @watch-stats)]
        (while (:running @watch-stats)
          (poll-events watcher))))))

(defn- cancel-watch-key
  [watch-key handlers]
  (.cancel watch-key)
  (handle-watch-events watch-key handlers))

(defn- get-path
  [fs pathname]
  (.getPath fs pathname (make-array String 0)))

(defn- watch-file
  [filename & {:as handlers}]
  (let [watcher (get-or-create-watch-service)
        fs (:fs @watch-stats)
        path (get-path fs filename)
        watch-events (->> handlers
                          (map (comp kw-to-event first))
                          into-array)
        watch-dir (let [parent (get-file-parent-directory fs path)]
                    (if (nil? parent)
                      (throw (IllegalArgumentException. (str "Bad file:" filename)))
                      parent))
        watch-key (.register watch-dir watcher watch-events)
        wrap-fn (fn [handler ev ctx]
                  (let [full-path (-> (.toFile path) (.getCanonicalFile))
                        full-ctx (-> (str (.getParent ctx)
                                          watch-dir (File/separator)
                                          (.getName ctx))
                                     (File.)
                                     (.getCanonicalFile))]
                    (when (= full-path full-ctx) (handler ev full-ctx))))
        wrapped-handlers (into {} (map (fn [[k v]] [k (partial wrap-fn v)]) handlers))]
    (swap! watch-stats
           assoc-in
           [:watching-files watch-dir]
           {:handlers wrapped-handlers
            :watch-key watch-key
            :file-path path})
    (start-watcher)))

(defn- watch-path
  [pathname & {:as handlers}]
  (let [watcher (get-or-create-watch-service)
        fs (:fs @watch-stats)
        path (get-path fs pathname)
        watch-events (->> handlers
                          (map (comp kw-to-event first))
                          into-array)
        watch-type (if (directory? path) :path :file)
        watch-key (.register path watcher watch-events)
        wrap-fn (fn [handler ev ctx]
                  (let [full-ctx (-> (str (.getParent ctx)
                                          path (File/separator)
                                          (.getName ctx))
                                     (File.)
                                     (.getCanonicalFile))]
                    (handler ev full-ctx)))
        wrapped-handlers (into {} (map (fn [[k v]] [k (partial wrap-fn v)]) handlers))]
    (swap! watch-stats
           assoc-in
           [:watching-paths path]
           {:handlers wrapped-handlers :watch-key watch-key})
    (start-watcher)))

(defn watch
  "Start watching a path and call the handlers if files
   are created/modified/deleted in that directory or watch a file.
   If the watcher thread is not started, this function automatically
   starts it. The handlers are given via keyword args, currently supported
   keywords are `:create`, `:modify`, and `:delete`."
  [pathname & handlers]
  (if (= (count handlers) 0)
    (throw (IllegalArgumentException. "No handlers specified."))
    (let [watcher (get-or-create-watch-service)
          fs (:fs @watch-stats)
          path (get-path fs pathname)
          watch-type (if (directory? path) :path :file)]
      (if (= watch-type :path)
        (apply watch-path pathname handlers)
        (apply watch-file pathname handlers)))))

(defn unwatch
  "Stop watching a path or file."
  [pathname]
  (let [fs (:fs @watch-stats)
        path (get-path fs pathname)
        watch-type (if (directory? path) :watching-paths :watching-files)
        path (if (= watch-type :watching-files)
               (get-file-parent-directory fs path)
               (get-path (:fs @watch-stats) pathname))
        {:keys [watch-key handlers]} (get-in @watch-stats
                                             [watch-type path])]
    (when watch-key
      (cancel-watch-key watch-key handlers)
      (swap! watch-stats update-in [watch-type] dissoc path))))

(defn stop-watchers
  "Close all the watching services and stop the polling threads"
  []
  (let [{:keys [watcher watching-paths handlers]} @watch-stats]
    (doseq [[_ {:keys [watch-key handlers]}] watching-paths]
      (cancel-watch-key watch-key handlers))
    (.close watcher)
    (reset! watch-stats initial-stats)
    true))
