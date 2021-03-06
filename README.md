# toolbelt-lacinia
[![CircleCI](https://circleci.com/gh/starcity-properties/toolbelt-lacinia.svg?style=svg&circle-token=c24540633a95aa3de4c076910850c088ddfc4e88)](https://circleci.com/gh/starcity-properties/toolbelt-lacinia)

Lacinia tools library wrapping https://github.com/walmartlabs/lacinia for easier used across the Starcity codebase. Refer to the lacinia documentation for how to use Clojure with GraphQL.

## Getting started
### deps.edn
Add `toolbelt-lacinia` as a dependency to your `deps.edn`.
```clojure
;; Maven
{:deps {toolbelt-lacinia {:mvn/version "0.0.1"}}}

;; Github
{:deps {toolbelt-lacinia {:git/url "https://github.com/starcity-properties/toolbelt-lacinia.git" :sha <some-sha>}}}

;; Local dependency
{:deps {toolbelt-lacinia {:local/root "/path/to/toolbelt-lacinia"}}}
```
### Leiningen
Add `toolbelt-lacinia` as a dependency to your `project.clj`.
```clojure
[toolbelt-lacinia "0.0.1"]
```

## Usage
### Define resolvers
Define a resolver.
```clojure 
(require '[toolbelt.lacinia.core :refer :all)

;; Specify a unique keyword as metadata, this will be used in the GraphQL schema.
(defresolver ^:resolver/example example-resolver
 "Public resolver"         ;; Doc string (optional)
 [context params resolved] ;; Resolver arguments
 {:authorization true}     ;; Configuration map
 (println "Resolving"))    ;; Body
 
 (example-resolver {} {} {})
 Resolving
 => nil

```
### Read resolvers
Read all resolvers with the `read-resolvers` to attach to the schema when compiling. It'll pick up any resolvers that have the `:resolver` key set, which the macro will do when defined as above.
```clojure
(read-resolvers)
=> #:resolver{:example #'user/example-resolver}
```

### Authorization
Resolvers will be restricted by default and not accessible by anyone, use the `:authorization` key in the config map to specify what conditions should be true in order to be considered an authorized request.

```clojure
 (defn authorized? [c] (true? c)
 
 (defresolver ^:resolver/example example-resolver
 "Resolver restricted to authorized context."
 [context params resolved]
 {:authorization (authorized? context}
 (println "Resolving"))
 
 ;; Not authorized returns an error.
 (example-resolver false {} {})
 =>
 #com.walmartlabs.lacinia.resolve.ResolverResultImpl{:resolved-value #object[com.walmartlabs.lacinia.resolve$with_error$reify__1391
                                                                            0x492b2b77
                                                                            "com.walmartlabs.lacinia.resolve$with_error$reify__1391@492b2b77"]}
```

We can customize how to handle unauthorized requests in a few different ways, with respecting the following order:
- Adding the `:unauthorized-handler` key in the configuration map of `defresolver`
- Providing an `:unauthorized-handler` in the opts map when running `execute`
- Binding `*unauthorized-handler*`


### Exceptions
If a resolver throws an exception, it will be caught and returned as a GraphQL error: 
```clojure
(defresolver ^:resolver/error error-resolver
 "Resolver throwing exception."
 [context params resolved]
 {:authorization (authorized? context)}
 (throw (ex-info "This is an error")))
 
 ;; Exception thrown returns an error.
(error-resolver true {} {})
=>
#com.walmartlabs.lacinia.resolve.ResolverResultImpl{:resolved-value #object[com.walmartlabs.lacinia.resolve$with_error$reify__1747
                                                                            0x610b87b1
                                                                            "com.walmartlabs.lacinia.resolve$with_error$reify__1747@610b87b1"]}
```
Similar to the unauthorized handler, we can customize how to handle exceptions, with respecting the following order:
- Adding the `:exception-handler` key in the configuration map of `defresolver`
- Providing an `:exception-handler` in the opts map when running `execute`
- Binding `*exception-handler*`


## Work on toolbelt-lacinia
### Install Clojure
**Using Homebrew:**
```
brew install clojure
```
**Other:**  
Check out the [Clojure Getting Started](https://clojure.org/guides/getting_started) for alternative ways of installing clojure

### Usage
- Run Clojure REPL in the project: `clj`.
- Run tests by starting the test alias: `clj -A:test`


## License

Copyright © 2018 Starcity

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
