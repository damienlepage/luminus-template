(ns leiningen.new.db
  (:require [leiningen.new.common :refer :all]))

(defn select-db [{:keys [features]}]
  (cond
    (some #{"+postgres"} features) :postgres
    (some #{"+mysql"} features) :mysql
    (some #{"+mongodb"} features) :mongo
    (some #{"+h2"} features) :h2))

(defn db-dependencies [options]
  [['migratus "0.8.2"]
   ['org.clojure/java.jdbc "0.3.7"]
   ['instaparse "1.4.1"]
   ['yesql "0.5.0-rc3"]
   ({:postgres ['org.postgresql/postgresql "9.3-1102-jdbc41"]
     :mysql    ['mysql/mysql-connector-java "5.1.6"]
     :h2       ['com.h2database/h2 "1.4.187"]}
     (select-db options))])

(defn db-url [{:keys [sanitized] :as options} suffix]
  ({:postgres (str "jdbc:postgresql://localhost/" sanitized "_" suffix
                   "?user=db_user_name_here&password=db_user_password_here")
    :mysql    (str "jdbc:mysql://localhost:3306/" sanitized "_" suffix
                   "?user=db_user_name_here&password=db_user_password_here")
    :h2       (str "jdbc:h2:./" sanitized "_" suffix ".db")
    :mongo    (str "mongodb://127.0.0.1/" sanitized "_" suffix)}
    (select-db options)))

(defn relational-db-files [options]
  (let [timestamp (.format
                    (java.text.SimpleDateFormat. "yyyyMMHHmmss")
                    (java.util.Date.))]
    [["src/<<sanitized>>/db/core.clj"
      (str "db/src/" ({:postgres "postgres.db.clj"
                       :mysql    "mysql.db.clj"
                       :h2       "h2.db.clj"}
                       (select-db options)))]
     ["src/<<sanitized>>/db/migrations.clj" "db/src/migrations.clj"]
     ["resources/sql/queries.sql" "db/sql/queries.sql"]
     [(str "resources/migrations/" timestamp "-add-users-table.up.sql") "db/migrations/add-users-table.up.sql"]
     [(str "resources/migrations/" timestamp "-add-users-table.down.sql") "db/migrations/add-users-table.down.sql"]]))

(defn db-profiles [options]
  {:database-profiles (str :database-url " \"" (db-url options "dev") "\"")})

(def mongo-files
  [["src/<<sanitized>>/db/core.clj" "db/src/mongodb.clj"]])

(defn add-mongo [[assets options]]
  [(into assets mongo-files)
   (-> options
       (append-options :dependencies [['com.novemberain/monger "2.0.1"]])
       (assoc
         :db-docs ((:selmer-renderer options) (slurp-resource "db/docs/mongo_instructions.md") options))
       (merge (db-profiles options)))])

(defn add-relational-db [db [assets options]]
  [(into assets (relational-db-files options))
   (-> options
       (append-options :dependencies (db-dependencies options))
       (append-options :plugins [['migratus-lein "0.1.5"]])
       (assoc
         :migrations (str {:store :database})
         :db-docs ((:selmer-renderer options)
                    (slurp-resource (if (= :h2 db)
                                    "db/docs/h2_instructions.md"
                                    "db/docs/db_instructions.md"))
                    options))
       (merge (db-profiles options)))])

(defn db-features [state]
  (if-let [db (select-db (second state))]
    (if (= :mongo db)
      (add-mongo state)
      (add-relational-db db state))
    state))
