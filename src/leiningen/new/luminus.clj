(ns leiningen.new.luminus
  (:require [leiningen.new.templates
             :refer [name-to-path year
                     sanitize sanitize-ns project-name]]
            [leinjacker.utils :refer [lein-generation]]
            [selmer.parser :as selmer]
            [leiningen.core.main :as main]
            [leiningen.new.common :refer :all]
            [leiningen.new.auth :refer [auth-features]]
            [leiningen.new.db :refer [db-features]]
            [leiningen.new.cljs :refer [cljs-features]]
            [leiningen.new.cucumber :refer [cucumber-features]]
            [leiningen.new.aleph :refer [aleph-features]]
            [leiningen.new.http-kit :refer [http-kit-features]]
            [leiningen.new.immutant :refer [immutant-features]]
            [leiningen.new.swagger :refer [swagger-features]]
            [leiningen.new.sassc :refer [sassc-features]]
            [leiningen.new.site :refer [site-features]])
  (:import java.io.File))

(def core-assets
  [[".gitignore" "core/gitignore"]
   ["project.clj" "core/project.clj"]
   ["profiles.clj" "core/profiles.clj"]
   ["Procfile" "core/Procfile"]
   ["README.md" "core/README.md"]

   ;; core namespaces
   ["src/<<sanitized>>/core.clj" "core/src/core.clj"]
   ["src/<<sanitized>>/handler.clj" "core/src/handler.clj"]
   ["src/<<sanitized>>/routes/home.clj" "core/src/home.clj"]
   ["src/<<sanitized>>/layout.clj" "core/src/layout.clj"]
   ["src/<<sanitized>>/middleware.clj" "core/src/middleware.clj"]
   ["src/<<sanitized>>/session.clj" "core/src/session.clj"]

   ;;HTML templates
   ["resources/templates/base.html" "core/resources/templates/base.html"]
   ["resources/templates/home.html" "core/resources/templates/home.html"]
   ["resources/templates/about.html" "core/resources/templates/about.html"]
   ["resources/templates/error.html" "core/resources/templates/error.html"]

   ;; public resources, example URL: /css/screen.css
   ["resources/public/css/screen.css" "core/resources/css/screen.css"]
   ["resources/docs/docs.md" "core/resources/docs.md"]
   "resources/public/js"
   "resources/public/img"

   ;; tests
   ["test/<<sanitized>>/test/handler.clj" "core/test/handler.clj"]])

(defn render-template [template options]
  (selmer/render
    (str "<% safe %>" template "<% endsafe %>")
    options
    {:tag-open \< :tag-close \> :filter-open \< :filter-close \>}))

(defn format-options [options]
  (-> options
      (update-in [:dependencies] (partial indent dependency-indent))
      (update-in [:dev-dependencies] (partial indent dev-dependency-indent))
      (update-in [:plugins] (partial indent plugin-indent))))

(defn unsupported-jetty-java-version? [java-version]
  (as-> java-version %
        (clojure.string/split % #"\.")      
        (take 2 %)
        (map #(Integer/parseInt %) %)
        (and (< (first %) 2)
             (< (second %) 8))))

(defn generate-project
  "Create a new Luminus project"
  [options]
  (main/info "Generating a Luminus project.")
  (with-redefs [leiningen.new.templates/render-text render-template]
    (let [java-version (System/getProperty "java.version")
          [assets options]
          (-> [core-assets options]
              auth-features
              db-features
              cucumber-features
              site-features
              cljs-features
              swagger-features
              aleph-features
              http-kit-features
              immutant-features
              sassc-features)]
      (when (and (= "jetty" (:server options))
                 (unsupported-jetty-java-version? java-version))
        (main/info (str "Warning: Jetty requires JDK 8+, found: " java-version)))
      (render-assets assets (format-options options)))))

(defn format-features [features]
  (apply str (interpose ", " features)))

(defn luminus
  "Create a new Luminus project"
  [name & feature-params]
  (let [supported-features #{"+cljs" "+site" "+h2" "+postgres" "+dailycred"
                             "+mysql" "+http-kit" "+cucumber" "+mongodb"
                             "+auth" "+immutant" "+sassc" "+swagger"
                             "+aleph"}
        options {:name       (project-name name)
                 :selmer-renderer render-template
                 :min-lein-version "2.0.0"
                 :project-ns (sanitize-ns name)
                 :sanitized  (name-to-path name)
                 :year       (year)
                 :server "jetty"
                 :features   (set feature-params)}
        unsupported (-> (set feature-params)
                        (clojure.set/difference supported-features)
                        (not-empty))]
    (cond
      (< (lein-generation) 2)
      (main/info "Leiningen version 2.x is required.")

      (re-matches #"\A\+.+" name)
      (main/info "Project name is missing.\nTry: lein new luminus PROJECT_NAME"
                 name (clojure.string/join " " (:features options)))

      unsupported
      (main/info "Unrecognized options:" (format-features unsupported)
                 "\nSupported options are:" (format-features supported-features))

      (.exists (File. name))
      (main/info "Could not create project because a directory named" name "already exists!")

      :else
      (generate-project options))))
