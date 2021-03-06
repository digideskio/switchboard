(ns switchboard.t-github
  (:require [clojure.data.json :as json]
            [midje.sweet :refer :all]
            [ring.mock.request :as mock]
            [org.httpkit.fake :refer [with-fake-http]]
            [switchboard.t-utils :refer :all]
            [switchboard.github :as github]))


(defn gh [x] (redirect (str "https://github.com" x)))

(defn gh-issue-search [repo term]
  (gh (str "/" repo "/search?q=" term "&ref=cmdform&type=Issues")))


(def gh-api-repo (partial github/gh-api "repos"))

(def fake-gh-repo-data [["bitprophet" "myrepo" :ok]
                        ["employer" "myrepo" 404]
                        ["bitprophet" "otherrepo" 404]
                        ["employer" "otherrepo" :ok]])

(defn fake-gh-repo [[acct repo action]]
  [(gh-api-repo acct repo)
   (if (= :ok action)
     (json/write-str {:html_url (github/gh acct repo)
                      :full_name (str acct "/" repo)})
     action)])

(def fake-gh-repos (flatten (map fake-gh-repo fake-gh-repo-data)))


(facts "about basic behavior"

  (fact "bare key just hits homepage"
        (query "gh") => (gh ""))

  (fact "anything not a project id, etc, is appended to github.com"
        (query "gh somebody/project") => (gh "/somebody/project")))


(facts "about specific projects"

  (fact "bare project id just hits its landing page"
        (query "gh inv") => (gh "/pyinvoke/invoke"))

  (fact "project id plus issue number goes to that issue"
        (query "gh inv 123") => (gh "/pyinvoke/invoke/issues/123"))

  (fact "project id plus 'new' goes to issue creation page"
        (query "gh inv new") => (gh "/pyinvoke/invoke/issues/new"))

  (fact "anything else becomes an issue search for that project id"
        (query "gh inv lolcats")
           => (gh-issue-search "pyinvoke/invoke" "lolcats")
        (query "gh inv a query with spaces")
           => (gh-issue-search "pyinvoke/invoke" "a query with spaces"))

  (fact "this shorthand applies to all input, not just shorthands"
        (query "gh someaccount/somerepo lolcats")
          => (gh-issue-search "someaccount/somerepo" "lolcats")))


(facts "about account searching"

  (with-fake-http fake-gh-repos

    (fact "when input attached to an account yields a repo, go there"
          (query "gh myrepo") => (gh "/bitprophet/myrepo"))

    (fact "shorthand syntax applies to private repos"
          (query "gh myrepo 25") => (gh "/bitprophet/myrepo/issues/25"))))

    ; TODO: figure out correct way to use test data for
    ; github.clj/github-(projects|accounts) instead of relying on the real
    ; thing. E.g. when employer accounts change...yea.
    ;(fact "when first account doesn't match, next is tried"
    ;      (query "gh otherrepo") => (gh "/employer/otherrepo"))))
