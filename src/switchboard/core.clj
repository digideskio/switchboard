;; # Overview
;;
;; Switchboard is designed to run as a browser backend search engine which
;; takes a single string query, parses it, and interprets it according to a set
;; of rules.

(ns switchboard.core
  (:require [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace-web]]
            [ring.util.response :refer [not-found, redirect]]
            [clojure.string :refer [split]]
            [clojure.pprint :refer [pprint]]
            [puget.printer :refer [cprint]]
            [org.httpkit.client :as http])
  (:gen-class))


;; Github module, key: `gh`
;;
;; * Empty invocation (`gh`): go to `github.com`.
;; * Shorthand project name found in `github-projects` (e.g. `gh inv`): go to
;;   its project page.
(defn github [rest]
  (case rest
    nil "https://github.com"))


;; Dispatch requests to given modules based on first word ("key").
;;
;; When no matching key is found, all text is used as-is in a Google search.
(defn dispatch [[key rest]]
  (case key
    "gh" (github rest)
    (str "https://google.com/search?q=" key (if rest (str " " rest)))))


; Basic HTTP handler logic
(defn handler [request]
  (let [query (-> request :params :query)]
    (if-not (nil? query)
      (redirect (dispatch (split query #" " 2)))
      (not-found "What are you even doing?"))))

; App wrapping requests w/ easy access to params via map+keyword
(def app (-> handler
           wrap-keyword-params
           wrap-params))

; Human-facing app adding stacktrace display to the mix
(def human-app (-> app wrap-stacktrace-web))
