# gmail_clojure

the usage of gmail api with clojure

## Usage

```clojure
lein run
```
## Requirement
- Google Cloud Project
  - enable Gmail API
  
  <img src="https://github.com/MokkeMeguru/gmail-clojure/blob/main/doc/gmail-api.png" width="400">
  
  - activate OAuth
  
  - add test user
  
  <img src="https://github.com/MokkeMeguru/gmail-clojure/blob/main/doc/oauth.png" width="400">
  
  - add oauth client id for desktop
    and then, download secret json file for this client id as **credential.json**
    
  <img src="https://github.com/MokkeMeguru/gmail-clojure/blob/main/doc/auth-account.png" width="400">
- save **credential.json** into resources/
