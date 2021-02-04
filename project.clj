(defproject gmail_clojure "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.google.api-client/google-api-client "1.23.0"]
                 [com.google.oauth-client/google-oauth-client-jetty "1.23.0"]
                 [com.google.apis/google-api-services-gmail "v1-rev83-1.23.0"]
                 [com.sun.mail/javax.mail "1.6.2"]
                 [commons-codec/commons-codec "1.15"]
                 [orchestra "2021.01.01-1"]]
  :main ^:skip-aot gmail-clojure.core
  :target-path "target/%s"
  :profiles {:dev {:resource-paths ["resources"]}
             :uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
