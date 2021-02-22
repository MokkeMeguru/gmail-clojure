# gmail_clojure

the usage of gmail api with clojure

## Usage

help

```sh
lein run -h
```

### get credential tokens from oauth 2.0 using browser

```sh
lein run -g -c credentials.json -t tokens -p 8090
```

```text
refresh token: <token>
expires in seconds:  3467
```

### get gmail labels example

```shell
lein run -e list_labels -c credentials.json -t tokens
```

```text
refresh token: <token>
expires in seconds:  3454
("Labels:"
 "CHAT"
 "SENT"
 "INBOX"
 "IMPORTANT"
 "TRASH"
 "DRAFT"
 "SPAM"
 "CATEGORY_FORUMS"
 "CATEGORY_UPDATES"
 "CATEGORY_PERSONAL"
 "CATEGORY_PROMOTIONS"
 "CATEGORY_SOCIAL"
 "STARRED"
 "UNREAD")
```

### send sample email example

```sh
lein run -e send_mail -c credentials.json -t tokens -a <sample email address>
```

```text
refresh token: <token>
expires in seconds:  3220
{:id <id>,
 :label-ids ["SENT"],
 :thread-id <thread-id>}
```

### cron job example to update credential tokens

```shell
lein run -l -c credential.json -t tokens
```

```text
sec:  1
refresh token: <token>
expires in seconds:  2289
temporary-task-succeed?:  true
sec:  2
refresh token: <token>
expires in seconds:  2287
temporary-task-succeed?:  true
<...>
sec:  11
close
info: channel was closed
```

## Requirement

- Google Cloud Project

  - enable Gmail API

  <img src="https://github.com/MokkeMeguru/gmail-clojure/blob/main/doc/gmail-api.png" width="400">

  - activate OAuth

  - add test user

  <img src="https://github.com/MokkeMeguru/gmail-clojure/blob/main/doc/oauth.png" width="400">

  - add oauth client id for web server (node.js, tomcat etc...)
    and then, download secret json file for this client id as **credential.json**

  <img src="https://github.com/MokkeMeguru/gmail-clojure/blob/main/doc/auth-account.png" width="400">

- save **credential.json** into resources/
