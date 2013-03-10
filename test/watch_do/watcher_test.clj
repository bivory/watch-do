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
  ;;(println "saw" ev ctx)
  (let [cb (:cb @check)]
    (deliver cb {:ev ev :path (.toString ctx)})))

(defn set-watching!
  [check]
  (swap! check (constantly {:cb (promise)})))

(defn watching-for
  "Test helper that verifies that the change event is the expended event."
  [check parent-path ev path]
  ;;(println "watching for" ev path)
  (let [cb (:cb @check)]
    (and (not= nil (deref cb 15000 nil))
         (= (:ev @cb) ev)
         (= (str parent-path (:path @cb)) path))))

(facts "About watching directories"
      (let [check (atom {})
            dir (str path (fs/temp-name "tmp-dir"))
            file (str path (fs/temp-name "tmp"))]

        "A user can unwatch a directory"
        (set-watching! check)
        (watch-path path
                    :create (partial cb check)
                    :modify (partial cb check)
                    :delete (partial cb check))
        (unwatch-path path)
        (fs/mkdir dir)
        (watching-for check path :create dir) => false
        (fs/delete-dir dir)

        "A user can watch a directory for changes"
        (watch-path path
                    :create (partial cb check)
                    :modify (partial cb check)
                    :delete (partial cb check))

        "Creating a directory will be noticed"
        (set-watching! check)
        (fs/mkdir dir)
        (watching-for check path :create dir) => true

        "Deleting a directory will be noticed"
        (set-watching! check)
        (fs/delete-dir dir)
        (watching-for check path :delete dir) => true

        "Creating a file will be noticed"
        (set-watching! check)
        (fs/touch file)
        (watching-for check path :create file) => true

        "Deleting a file will be noticed"
        (set-watching! check)
        (fs/delete file)
        (watching-for check path :delete file) => true

        (unwatch-path path)))

(facts "About watching files"
      (let [check (atom {})
            file (str path (fs/temp-name "tmp"))]

        "A user can unwatch a file"
        (set-watching! check)
        (watch-path file
                    :create (partial cb check)
                    :modify (partial cb check)
                    :delete (partial cb check))
        (unwatch-path file)
        (fs/touch file)
        (watching-for check path :create file) => false
        (fs/delete file)

        "A user can watch a file for changes"
        (watch-path file
                    :create (partial cb check)
                    :modify (partial cb check)
                    :delete (partial cb check))

        "Creating a file will be noticed"
        (set-watching! check)
        (fs/touch file)
        (watching-for check path :create file) => true

        "Modifying a file will be noticed"
        (set-watching! check)
        (fs/touch file)
        (watching-for check path :modify file) => true

        "Deleting a file will be noticed"
        (set-watching! check)
        (fs/delete file)
        (watching-for check path :delete file) => true

        (unwatch-path path)))
