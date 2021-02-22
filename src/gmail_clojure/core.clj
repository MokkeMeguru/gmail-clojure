(ns gmail-clojure.core
  (:gen-class)
  (:require [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.core.async :refer [go-loop <! timeout]]
            ;; test
            [orchestra.spec.test :as st])
  (:import [com.google.api.services.gmail GmailScopes]
           [com.google.api.client.json JsonFactory]
           [com.google.api.client.json.jackson2 JacksonFactory]
           [com.google.api.client.http.javanet NetHttpTransport]
           [com.google.api.client.googleapis.javanet GoogleNetHttpTransport]
           [com.google.api.client.extensions.java6.auth.oauth2 AuthorizationCodeInstalledApp]
           [com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeFlow]
           [com.google.api.client.googleapis.auth.oauth2  GoogleAuthorizationCodeFlow GoogleAuthorizationCodeFlow$Builder]
           [com.google.api.client.util.store FileDataStoreFactory]
           [com.google.api.client.googleapis.auth.oauth2 GoogleClientSecrets]
           [com.google.api.client.extensions.jetty.auth.oauth2 LocalServerReceiver LocalServerReceiver$Builder]
           [com.google.api.services.gmail Gmail$Builder Gmail]
           [java.util Properties]
           [javax.mail.internet MimeMessage]
           [javax.mail Session]
           [javax.mail.internet InternetAddress]
           [javax.mail Message$RecipientType]
           [org.apache.commons.codec.binary Base64]
           [com.google.api.services.gmail.model Message]
           [com.google.api.client.auth.oauth2 Credential]
           [com.google.api.services.gmail.model Label]))

;; static value


(def json-factory (JacksonFactory/getDefaultInstance))
(def http-transport (GoogleNetHttpTransport/newTrustedTransport))
(def charset "utf-8")
(def encode "base64")

;; domain


(def email-regex  #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
(def net-http-transport? (partial instance? NetHttpTransport))
(def satisfy-email-regex? (partial re-matches email-regex))
(def gmail-api-scope? string?)
(def url? (partial instance? java.net.URL))
(def file-exist? #(-> % io/as-file .exists))
(def credential? (partial instance? Credential))

(def gmail-service? (partial instance? Gmail))
(def mime-message? (partial instance? MimeMessage))
(def gmail-message? (partial instance? Message))

(def google-auth-code-flow? (partial instance? GoogleAuthorizationCodeFlow))

;; resource files
(s/def ::credential-file (s/and url? file-exist?))
(s/def ::tokens-dir (s/and url? file-exist?))

;; google api's settings
(s/def ::http-transport net-http-transport?)
(s/def ::scope gmail-api-scope?)
(s/def ::scopes (s/coll-of ::scope))
(s/def ::application-name string?)
(s/def ::service gmail-service?)
(s/def ::port pos-int?)
(s/def ::auth-code-flow google-auth-code-flow?)

(s/def ::credential credential?)
(s/def ::user-id string?)
(s/def ::gmail-label (partial instance? Label))
(s/def ::gmail-labels (s/coll-of ::gmail-label))
(s/def ::gmail-message gmail-message?)

(s/def ::id string?)
(s/def ::label-id string?)
(s/def ::label-ids (s/coll-of ::label-id))
(s/def ::thread-id string?)
(s/def ::gmail-response (s/keys :req-un [::id ::label-ids ::thread-id]))

;; email contents
(s/def ::address (s/and string? satisfy-email-regex?))

(s/def ::to ::address)
(s/def ::from ::address)
(s/def ::subject string?)
(s/def ::message string?)

(s/def ::mime-message mime-message?)

(s/fdef get-credential
  :args (s/cat
         :credential-file ::credential-file
         :tokens-dir ::tokens-dir
         :scopes ::scopes)
  :ret ::credential)

(s/fdef get-service
  :args (s/cat
         :application-name ::application-name
         :credential ::credential)
  :ret ::service)

(s/fdef create-message
  :args (s/cat
         :kwargs  (s/keys :req-un [::to
                                   ::from
                                   ::subject
                                   ::message]))
  :ret ::mime-message)

(s/fdef create-message-with-email
  :args (s/cat :email-content ::mime-message)
  :ret ::gmail-message)

(s/fdef send-message
  :args (s/cat :service ::service
               :user-id ::user-id
               :message ::gmail-message)
  :ret ::gmail-response)

(s/fdef gmail-labels
  :args (s/cat :service ::service :user ::user-id)
  :ret ::gmail-labels)

(defn- get-auth-code-flow
  [credential-file tokens-dir scopes]
  {:pre [(s/valid? ::credential-file credential-file)
         (s/valid? ::tokens-dir tokens-dir)]
   :post [(s/valid? ::auth-code-flow %)]}
  (with-open [in (io/input-stream credential-file)]
    (let [secrets (GoogleClientSecrets/load json-factory (java.io.InputStreamReader. in))
          file-data-store-factory (FileDataStoreFactory. (io/file tokens-dir))]
      (.. (GoogleAuthorizationCodeFlow$Builder. http-transport json-factory secrets scopes)
          (setDataStoreFactory file-data-store-factory)
          (setAccessType "offline")
          (setApprovalPrompt "force")
          build))))

(defn get-new-credential
  [credential-file tokens-dir scopes port]
  {:pre [(s/valid? ::credential-file credential-file)
         (s/valid? ::tokens-dir tokens-dir)
         (s/valid? ::port port)]
   :post [(s/valid? ::credential %)]}

  (let [flow (get-auth-code-flow credential-file tokens-dir scopes)
        local-server-receiver   (.. (LocalServerReceiver$Builder.)
                                    (setPort port)
                                    build)
        credential (-> flow
                       (AuthorizationCodeInstalledApp.
                        local-server-receiver)
                       (.authorize "user"))]
    (println "refresh token: " (.getRefreshToken credential))
    (println "expires in seconds: " (.getExpiresInSeconds credential))
    credential))

(defn get-credential "
  get credential info from credential-file and stored secret file
  if secret file is expired or some thing wrong, you need to generate new secret file.

  ```clojure
  (def http-transport (GoogleNetHttpTransport/newTrustedTransport))
  (def scopes [GmailScopes/GMAIL_LABELS
               GmailScopes/GMAIL_SEND])

  (def credential-file (io/resource \"credential.json\"))
  (def tokens-dir (io/resource \"tokens\"))

  (get-credential credential-file tokens-dir scopes http-transport)
  ```
  "
  [credential-file tokens-dir scopes]
  {:pre [(s/valid? ::credential-file credential-file)
         (s/valid? ::tokens-dir tokens-dir)]
   :post [(s/valid? ::credential %)]}
  (let [flow (get-auth-code-flow credential-file tokens-dir scopes)
        credential (-> flow (.loadCredential "user"))
        credential (cond
                     (nil? credential) nil
                     (or (some? (.getRefreshToken credential))
                         (nil? (.getExpiresInSeconds credential))
                         (> (.getExpiresInSeconds credential) 60)) credential
                     :else nil)]
    (when (nil? credential)
      (throw (Exception. "credential file is expired: please re-generate credential file using cli tool. https://github.com/MeguruMokke/gmail_clojure")))
    (println "refresh token: " (.getRefreshToken credential))
    (println "expires in seconds: " (.getExpiresInSeconds credential))
    credential))

(defn get-service "
  get gmail api service's connection with application-name (string)
  "
  [^String application-name credential]
  (.. (Gmail$Builder. http-transport json-factory credential)
      (setApplicationName application-name)
      build))

(defn create-message "
  create email message
  "
  [{:keys [to from subject message]}]
  {:pre [(s/valid? ::to to)
         (s/valid? ::from from)
         (s/valid? ::subject subject)
         (s/valid? ::message message)]
   :post [(s/valid? ::mime-message %)]}
  (let [props (Properties.)
        session (Session/getDefaultInstance props nil)
        email (MimeMessage. session)]
    (doto
     email
      (.setFrom (InternetAddress. from))
      (.addRecipient Message$RecipientType/TO (InternetAddress. to))
      (.setSubject subject charset)
      (.setText message charset))))

(defn create-message-with-email "
  encode email message into gmail api's code
  "
  [email-content]
  (let [binary-content (with-open [xout (java.io.ByteArrayOutputStream.)]
                         (.writeTo email-content xout)
                         (.toByteArray xout))
        encoded-content (Base64/encodeBase64URLSafeString binary-content)
        message (Message.)]
    (doto
     message
      (.setRaw encoded-content))))

(defn send-message "
  send message using google api

  ```clojure
  (defn
  (let [credential (get-credential credential-file tokens-dir scopes https-transport)
        service (get-service application-name credential http-transport)
        user-id \"me\"
        mime-message (create-message)
        gmail-message ()
  ]
    (send-message service user-id message))
  ```
  "
  [service user-id message]
  (let [response (-> service
                     .users
                     .messages
                     (.send user-id message)
                     .execute)
        response-map  (into {} response)]
    {:id (get response-map "id")
     :label-ids (-> (get response-map "labelIds") vec)
     :thread-id (get response-map "threadId")}))

(defn gmail-labels "
  get gmail's label list
  "
  [service user]
  (->
   (.. service users labels (list user) execute)
   .getLabels
   vec))

;; example value


(def application-name "gmail api usage example")
;; (def credential-file (io/resource "credentials.json"))
;; (def tokens-dir (io/resource "tokens"))
;; (def scopes [GmailScopes/GMAIL_LABELS
;;              GmailScopes/GMAIL_SEND])
;; (def port 8090)

(def user "me")

(def example-message
  "Dear <user>.
Hello, nice to meet you.
Thank you for using our service.

You receive this message from us.

---
Have a nice day!
良い一日を！
---

sended from: xxx.service
")

(st/instrument)
(defn send-example-message [credential-file tokens-dir scopes application-name address]
  (let [message-map {:to address :from address :subject "test" :message example-message}
        credential  (get-credential credential-file tokens-dir scopes)
        service   (get-service application-name credential)
        user-id user
        email-content (create-message message-map)
        message (create-message-with-email email-content)]
    (send-message service user-id message)))

(defn get-gmail-labels
  [credential-file tokens-dir scopes]
  (println (type credential-file))
  (let [credential (get-credential credential-file tokens-dir scopes)
        service (get-service application-name credential)
        user-id user]
    (gmail-labels service user-id)))

(defn api-update-cron "
  "
  [credential-file tokens-dir scopes]
  (let [channel-open? (atom true)]
    (go-loop [sec 1]
      (when @channel-open?
        (<! (timeout 1000))
        (println "sec: " sec)
        (if (> sec 10)
          (do
            (reset! channel-open? false)
            (println "close"))
          (do
            (println "temporary-task-succeed?: " (-> (get-gmail-labels credential-file tokens-dir scopes) count zero? not))
            (recur (inc sec))))))))

(def cli-options
  [["-g" "--generate-key" "generate stored credential key from oauth" :default false]
   ["-e" "--example-task TASK" "example task choices from (send_mail, list_labels)"
    :default ""
    :validate [#(some (partial = %) ["" "send_mail" "list_labels"])]]
   ["-l" "--cron-token" "repeat update token in async as an example" :default false]
   ["-p" "--port PORT"
    :default 8090
    :parse-fn #(Integer. %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-c" "--credential-file CREDENTIAL_FILE" "credential file from google https://console.cloud.google.com/apis/credentials"
    :default (io/resource "credentials.json")
    :parse-fn io/resource
    :validate [#(clojure.string/ends-with? % ".json")]]
   ["-t" "--tokens-dir TOKENS_DIR" "tokens dir to save"
    :default (io/resource "tokens")
    :parse-fn io/resource]
   ["-h" "--help" "Print this help" :default false]
   ;; ["-s" "--scopes SCOPE" "Google API's scopes (multi args)"
   ;;  :default []
   ;;  :assoc-fn (fn [m k v]
   ;;              (assoc m k (conj (get m k) v)))
   ;;  :parse-fn #(str "https://www.googleapis.com/auth/gmail." %)]
   ["-a" "--address" "Test email address"
    :default "meguru.mokke@gmail.com"
    :parse-fn str
    :validate [#(s/valid? ::address %)]]])

;; (= 2020 (:port (:options (parse-opts ["-p" "2020"] cli-options))))
;; (:credential-file (:options (parse-opts ["-c" "sample.json"] cli-options)))
;; (true? (:help (:options (parse-opts ["-h"] cli-options))))
;; (false? (:help (:options (parse-opts [] cli-options))))
;; (= "send_mail" (:example-task (:options (parse-opts ["-e" "send_mail"] cli-options))))
;; (= "list_labels" (:example-task (:options (parse-opts ["-e" "list_labels"] cli-options))))
;; (true? (:generate-key (:options (parse-opts ["-g"] cli-options))))
;; (= (io/resource "credentials.json") (:credential-file (:options (parse-opts ["-c" "credentials.json"] cli-options))))
;; (= (io/resource "") (:tokens-dir (:options (parse-opts ["-t" ""] cli-options))))
;; (:scopes (:options (parse-opts [] cli-options)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        {:keys [example-task cron-token generate-key port credential-file tokens-dir address help]} options]
    (cond
      help (println summary)
      generate-key (get-new-credential credential-file tokens-dir [GmailScopes/GMAIL_LABELS GmailScopes/GMAIL_SEND] port)
      cron-token (api-update-cron credential-file tokens-dir [GmailScopes/GMAIL_LABELS])
      (= example-task "list_labels") (clojure.pprint/pprint
                                      (cons "Labels:"
                                            (map
                                             (fn [k] (.getName k))
                                             (get-gmail-labels credential-file tokens-dir [GmailScopes/GMAIL_LABELS]))))
      (and (= example-task "send_mail")) (send-example-message credential-file tokens-dir [GmailScopes/GMAIL_SEND] application-name address)
      :else (println summary))))

;; (-main "-g" "-c" "credentials.json" "-t" "tokens")
;; (-main "-e" "list_labels" "-c" "credentials.json" "-t" "tokens" "-p" "8090")
;; (-main "-e" "send_mail" "-c" "credentials.json" "-t" "tokens" "-p" "8090")
;; (-main "-l" "-c" "credentials.json" "-t" "tokens")
