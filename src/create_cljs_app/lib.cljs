(ns create-cljs-app.lib
  (:require [cljs.core.async :refer [take!]]
            [create-cljs-app.template :refer [use-template]]
            [create-cljs-app.utils :refer
             [exit-with-reason get-commands has-java? should-use-git? should-use-yarn?
              is-supported-node? silent-install]]
            [create-cljs-app.messages :refer
             [begin-msg done-msg init-git-msg install-packages-msg java-warning
              node-error]]
            ["path" :refer [basename join]]
            ["fs" :refer [existsSync]]
            ["shelljs" :refer [exec rm]]))


(defn create
  "Create an app from the template files on the given path.
  Optional `options` can have `:begin-msg` `:done-msg` and `:install-extras` functions"
  ([cwd path]
   (create cwd path {}))
  ([cwd path options]
   ; Bail early if the node version is unsupported.
   (when (not (is-supported-node? (.-version js/process)))
    (node-error)
    (.exit js/process 1))

   (let [abs-path (join cwd path)
         name     (basename abs-path)
         use-yarn (should-use-yarn?)
         use-git  (should-use-git?)
         commands (get-commands use-yarn)]

    (cond
      (= path "")           (exit-with-reason "You must provide a name for your app.")
      (existsSync abs-path) (exit-with-reason "A folder with the same name already exists.")

      :else (do
              (if-some [alt-begin-msg (:begin-msg options)]
                (alt-begin-msg abs-path)
                (begin-msg abs-path))
              (use-template abs-path name commands)
              (.chdir js/process path)
              (install-packages-msg)
              (take!
               (silent-install commands)
               (fn [code]
                 (let [install-failed? (not= code 0)]
                   (when use-git
                     (let [exec-options #js {:silent true :fatal true}]
                       (try
                         (exec "git init" exec-options)
                         (exec "git add -A" exec-options)
                         (exec
                          "git commit -m \"Initial commit from Create CLJS App\""
                          exec-options)
                         (init-git-msg)
                                        ; Catch and remove the .git directory to not leave it
                                        ; half-done.
                         (catch js/Object _e (rm "-rf" ".git")))))
                   (when-some [install-extras (:install-extras options)]
                     (install-extras))
                   (when (not (has-java?)) (java-warning))
                   (if-some [alt-done-msg (:done-msg options)]
                     (alt-done-msg name path abs-path install-failed?)
                     (done-msg name path abs-path commands install-failed?))))))))))

(def exports #js {:create create})
