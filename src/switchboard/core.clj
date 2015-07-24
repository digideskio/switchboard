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
            [clojure.string :refer [split, join]]
            [clojure.pprint :refer [pprint]]
            [puget.printer :refer [cprint]]
            [org.httpkit.client :as http]
            [clojure.data.json :as json])
  (:gen-class))


;; Map of Github projects' shorthand identifiers; used with `gh` below.
(def github-projects {"inv" "pyinvoke/invoke"
                      "fab" "fabric/fabric"
                      "par" "paramiko/paramiko"})

;; User/organization accounts to search within for repo names
(def github-accounts ["bitprophet" "urbanairship"])

; Helpers
(defn build-url [base & xs] (join "/" (conj (remove nil? xs) base)))
(def gh (partial build-url "https://github.com"))
(def gh-api (partial build-url "https://api.github.com"))
(defn gh-proj [proj & xs] (apply gh (github-projects proj) xs))

; External data - cached
(def gh-token (System/getenv "SWITCHBOARD_GITHUB_TOKEN"))


; Github subroutines
(defn repo-from-accounts [proj]
  (first (filter
           #(= 200 (:status @%))
           (map
             #(http/get (gh-api "repos" % proj) {:oauth-token gh-token})
             github-accounts))))


;; `gh`: GitHub expansions
;;
;; **Basics**
;;
;; * Empty invocation (`gh`): go to `github.com`.
;; * Anything not matching one of the other rules:
;;     * First, the input is interpreted as being a repo name under each of the
;;       accounts listed in `github-accounts` (in order) and tested for
;;       existence. E.g. if `(def github-accounts ["foo" "bar"])`, `gh blah`
;;       will first check for `github.com/foo/blah`, redirecting to it if it
;;       exists, then will check `github.com/bar/blah`, etc.
;;     * Should all of those tests fail, the input is simply slapped onto
;;       `github.com/` directly, e.g. `gh randomuser/randomrepo` becomes
;;       `github.com/randomuser/randomrepo`.
;;
;; **Project shortcuts**
;;
;; * Shorthand project name found in `github-projects` (e.g. `gh inv`): go to
;;   its project page.
;; * Project + issue number (`gh inv 123`): go to that issue's page.
;; * Project + 'new' (`gh inv new`): go to 'new issue' page.
;; * Project + anything else (`gh inv namespace`): issue search on that
;;   project.
(defn github [rest]
  (if (nil? rest)
    (gh)
    (let [[proj rest] (split rest #" " 2)]
      (if (contains? github-projects proj)
        (cond
          ; Testing for nil rest must come first to avoid NPEs/etc during regex
          ; tests farther down.
          (nil? rest) (gh-proj proj)
          (= "new" rest) (gh-proj proj "issues/new")
          (re-matches #"\d+" rest) (gh-proj proj "issues" rest)
          :else (gh-proj proj (str
                                "search?q="
                                rest
                                "&ref=cmdform&type=Issues")))
        (let [result (repo-from-accounts proj)]
          (if (nil? result)
            ; Fall-through: just slap whatever strings were given onto
            ; github. If 'rest' is empty or nil, it won't mattress.
            (gh proj rest)
            ; Got an account-based result -> go there (looking at response
            ; data since it's easier than trying to reserve the arg given
            ; to http/head up in the filter-map)
            ((json/read-str (:body @result)) "html_url")))))))


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
    (if-not (empty? query)
      ; Use HTTP 307 so browsers don't cache when manually testing/poking
      (redirect (dispatch (split query #" " 2)) :temporary-redirect)
      {:body "What?", :status 400})))

; App wrapping requests w/ easy access to params via map+keyword
(def app (-> handler
           wrap-keyword-params
           wrap-params))

; Human-facing app adding stacktrace display to the mix
(def human-app (-> app wrap-stacktrace-web))
