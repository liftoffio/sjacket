(ns net.cgrand.sjacket.parser
  "A grammar and parser for Clojure."
  (:require [net.cgrand.parsley :as p]
            [net.cgrand.regex :as re]
            [net.cgrand.regex.charset :as cs]
            [net.cgrand.regex.unicode :as unicode]))

(def macro-char (cs/charset "\";'@^`~()[]{}\\%#"))
(def terminating-macro-char (cs/- macro-char #{\# \'}))
(def dispatch-macro-char (cs/charset "^'\"({=!<_"))

(def whitespace-char
  (cs/- 
    (cs/+
      (unicode/cats "Zs")
      (unicode/cats "Zl")
      (unicode/cats "Zp")
      \, {\u0009 \u000D, \u001C \u001F})
    ; non-breaking spaces
    #{\u00A0, \u2007 \u202F}))

(def constituent-char
  (cs/not whitespace-char macro-char))

;; maps to readToken
(def token-char 
  (cs/not whitespace-char terminating-macro-char))

(def start-token-char
  (cs/- token-char {\0 \9} "/:" macro-char))

(defn token [re]
  ; TODO compute the union of two regexes
  (re/regex re (re/?! token-char)))

#_(re/regex #{\+ \-} :? 
       #{["0" (?! {\8 \9})] 
         [{\1 \9} {\0 \9} :*]
         ["0" #{\x \X} {\0 \9 \A \F \a \f} :+]
         ["0" {\0 \7} :+ (?! {\0 \9})]
         [{\1 \9} {\0 \9} :? #{\r \R} {\0 \9 \A \Z \a \z} :+]})


(def rules
  {:sexpr- #{:nil :boolean :char :string :regex :number :symbol :keyword
             :list :vector :map :set :fn
             :meta :var :deref :quote :syntax-quote :unquote :unquote-splicing
             :unreadable :eval :reader-literal}
   :nil (token "nil")
   :boolean #{(token "true") (token "false")}
   :char (re/regex \\ (re/+ (cs/not whitespace-char)))
   :string (p/unspaced
              ["\""
               (re/regex
                   (re/* #{(cs/not \" \\)
                           [\\ (cs/charset "trn\\\"bf")]
                           ["\\u" {\0 \9} (re/repeat constituent-char 3 3)]
                           ["\\" {\0 \9} (re/repeat constituent-char 0 2)]}))
               "\""])
   :regex [(re/regex \# (re/?= \"))
           \"
           (re/regex (re/* #{(cs/not \" \\)
                             [\\ cs/any-char]}))
           \"]
   ;; numbers should be validated but this is the exact "scope" of a number
   :number (re/regex (re/? #{\+ \-}) {\0 \9} (re/* constituent-char))
   :unrestricted.name (token #{"/"
                               [start-token-char (re/* (cs/- token-char \/))]})
   :sym.ns (re/regex start-token-char
                 (re/* token-char)
                 (re/?= \/))
   :sym.name (re/regex
               (re/?! #{(token #{"true" "false" "nil"})
                        [#{\+ \-} {\0 \9}]})
               (token 
                 #{"/"
                   [(cs/+ start-token-char \%) (re/* (cs/- token-char \/))]}))
   :symbol #{(p/unspaced :sym.ns "/" :unrestricted.name)
             :sym.name}
   :kw.ns (re/regex start-token-char
                 (re/* token-char)
                 (re/?= \/))
   :keyword [(re/regex (re/repeat ":" 1 2))
             #{(p/unspaced :kw.ns "/" :unrestricted.name)
               (p/unspaced :unrestricted.name)}]
   :list ["(" :sexpr* ")"]
   :vector ["[" :sexpr* "]"]
   :map ["{" :sexpr* "}"]
   :set ["#{" :sexpr* "}"]
   :fn ["#(" :sexpr* ")"]
   :meta [#"#?\^" :sexpr :sexpr]
   :var ["#'" :symbol]
   :deref ["@" :sexpr]
   :quote ["'" :sexpr]
   :syntax-quote ["`" :sexpr]
   :unquote [#"~(?!@)" :sexpr]
   :unquote-splicing ["~@" :sexpr]
   :eval ["#=" :list]
   :reader-literal [(re/regex \# (re/?! dispatch-macro-char)) :symbol :sexpr]
   
   :comment (p/unspaced #{";" "#!"} #"[^\n]*")
   :unreadable "#<"
   :discard ["#_" :sexpr]
   
   :newline \newline
   :whitespace (re/regex (re/+ (cs/- whitespace-char \newline)))})

(def space-nodes #{:comment :discard :newline :whitespace})

(def parser 
  (p/make-parser {:main :sexpr*
                  :space [space-nodes :*]
                  :root-tag ::root}
                 rules))
