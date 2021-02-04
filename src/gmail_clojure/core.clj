(ns gmail-clojure.core
  (:gen-class)
  (:require [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            ;; test
            [orchestra.spec.test :as st])
  (:import [com.google.api.services.gmail GmailScopes]
           [com.google.api.client.json JsonFactory]
           [com.google.api.client.json.jackson2 JacksonFactory]
           [com.google.api.client.http.javanet NetHttpTransport]
           [com.google.api.client.googleapis.javanet GoogleNetHttpTransport]
           [com.google.api.client.extensions.java6.auth.oauth2 AuthorizationCodeInstalledApp]
           [com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeFlow]
           [com.google.api.client.googleapis.auth.oauth2 GoogleAuthorizationCodeFlow$Builder]
           [com.google.api.client.util.store FileDataStoreFactory]
           [com.google.api.client.googleapis.auth.oauth2 GoogleClientSecrets]
           [com.google.api.client.extensions.jetty.auth.oauth2 LocalServerReceiver]
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

;; resource files
(s/def ::credential-file (s/and url? file-exist?))
(s/def ::tokens-dir (s/and url? file-exist?))

;; google api's settings
(s/def ::http-transport net-http-transport?)
(s/def ::scope gmail-api-scope?)
(s/def ::scopes (s/coll-of ::scope))
(s/def ::application-name string?)
(s/def ::service gmail-service?)

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

(defn get-credential "
  get credential info from credential-file
  and then, save token from google

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
  (with-open [in (io/input-stream credential-file)]
    (let [secrets  (GoogleClientSecrets/load json-factory (java.io.InputStreamReader. in))
          file-data-store-factory (FileDataStoreFactory. (io/file tokens-dir))
          flow (.. (GoogleAuthorizationCodeFlow$Builder. http-transport json-factory secrets scopes)
                   (setDataStoreFactory file-data-store-factory)
                   (setAccessType "offline")
                   build)]
      (-> flow
          (AuthorizationCodeInstalledApp. (LocalServerReceiver.))
          (.authorize "user")))))

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


(def application-name "Gmail API usage example")
(def credential-file (io/resource "credentials.json"))
(def tokens-dir (io/resource "tokens"))
(def scopes [GmailScopes/GMAIL_LABELS
             GmailScopes/GMAIL_SEND])
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
owner      : meguru.mokke@gmail.com
")
(def message-map  {:to "meguru.mokke@gmail.com"
                   :from "meguru.mokke@gmail.com"
                   :subject "test"
                   :message example-message})

(st/instrument)
(defn send-example-message []
  (let [credential  (get-credential credential-file tokens-dir scopes)
        service   (get-service application-name credential)
        user-id user
        email-content (create-message message-map)
        message (create-message-with-email email-content)]
    (send-message service user-id message)))

(defn get-gmail-labels []
  (let [credential (get-credential credential-file tokens-dir scopes)
        service (get-service application-name credential)
        user-id user]
    (gmail-labels service user-id)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [labels (get-gmail-labels)
        message-response (send-example-message)]
    (println "labels are: ")
    (clojure.pprint/pprint (mapv #(.getName %)  labels))
    (println "send example message to " (:to message-map))
    (clojure.pprint/pprint message-response)))
