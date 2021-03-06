(ns watch-do.watcher-test
  (:use midje.sweet)
  (:require [me.raynes.fs :as fs]
            [watch-do.watcher :as watcher]))

(def path "test/tmp/")
;; Create the testing directory if it doesn't exist.
(when (not (fs/exists? path)) (fs/mkdir path))

(defn cb
  "Test helper that handles the callback when a watched path changes."
  [check ev ctx]
  ;; (println "saw" ev ctx)
  (let [event (:cb @check)]
    (deliver event {:ev ev :path ctx})))

(defn set-watching!
  [check]
  (swap! check (constantly {:cb (promise)})))

(defn watching-for
  "Test helper that verifies that the change event is the expended event."
  [check parent-path ev path]
  (let [cb (:cb @check)
        file (fs/file path)]
    ;; (println "watching for" ev path)
    (and (not= nil (deref cb 15000 nil))
         (= (:ev @cb) ev)
         ;; (or (println "checking" (:path @cb) file) true)
         (= (:path @cb) file))))

(facts "About watching directories"
       (let [check (atom {})
             dir (str path (fs/temp-name "directory-watch-dir"))
             file (str path (fs/temp-name "directory-watch-file"))]

         "A user can unwatch a directory"
         (set-watching! check)
         (watcher/watch path
                        :create (partial cb check)
                        :modify (partial cb check)
                        :delete (partial cb check))
         (watcher/unwatch path)
         (fs/mkdir dir)
         (watching-for check path :create dir) => false
         (fs/delete-dir dir)

         "A user can watch a directory for changes"
         (watcher/watch path
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

         (watcher/unwatch path)))

(facts "About watching files"
       (let [check (atom {})
             file (str path (fs/temp-name "file-watch-file"))]

         "A user can unwatch a file"
         (set-watching! check)
         (watcher/watch file
                        :create (partial cb check)
                        :modify (partial cb check)
                        :delete (partial cb check))
         (watcher/unwatch file)
         (fs/touch file)
         (watching-for check file :create file) => false
         (fs/delete file)))

(facts "About watching files"
       (let [check (atom {})
             file (str path (fs/temp-name "file-watch-file"))]

         "A user can watch a file for changes"
         (watcher/watch file
                        :create (partial cb check)
                        :modify (partial cb check)
                        :delete (partial cb check))

         ;;"Creating a file will be noticed"
         (set-watching! check)
         (fs/touch file)
         (watching-for check file :create file) => true

         "Modifying a file will be noticed"
         (set-watching! check)
         (fs/touch file)
         (watching-for check file :modify file) => true

         ;;"Deleting a file will be noticed"
         (set-watching! check)
         (fs/delete file)
         (watching-for check file :delete file) => true

         ;; TODO watch for a file that doesn't exist

         (watcher/unwatch file)))

(facts "About unwatching all files and paths"
       (watcher/stop-watchers) => true)


;; TODO handle multiple watchers watching the same file without replacing the previous
;; watcher
