(ns watch-do.watcher-test
  (:use midje.sweet
        watch-do.watcher)
  (:require [me.raynes.fs :as fs]))

(def path "test/tmp/")
;; Create the testing directory if it doesn't exist.
(when (not (fs/exists? path)) (fs/mkdir path))

(defn cb
  "Test helper that handles the callback when a watched path changes."
  [check ev ctx]
  ;;(println ev ctx)
  (swap! check (constantly {:ev ev :path (.toString ctx)})))

(defn watching-for
  "Test helper that verifies that the change event is the expended event."
  [check parent-path ev path]
  ;;(println "watching for" ev path)
  (Thread/sleep 15000)
  ;;(println "saw" (:ev @check) (str parent-path (:path @check)))
  (and (= (:ev @check) ev)
       (= (str parent-path (:path @check)) path)))

(fact "A user can watch a directory for changes"
      (let [check (atom {})
            dir (str path (fs/temp-name "tmp-dir"))
            file (str path (fs/temp-name "tmp"))]
        (watch-path path
                    :create (partial cb check)
                    :modify (partial cb check)
                    :delete (partial cb check))

        ;; Create
        (fs/mkdir dir)
        (watching-for check path :create dir) => true

        ;; Delete
        (fs/delete-dir dir)
        (watching-for check path :delete dir) => true

        ;; Create File
        (fs/touch file)
        (watching-for check path :create file) => true

        ;; Delete File
        (fs/delete file)
        (watching-for check path :delete file) => true

        (unwatch-path path)))
