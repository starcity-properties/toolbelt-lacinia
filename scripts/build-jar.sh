#!/usr/bin/env bash

rm target/toolbelt-lacinia.jar

clojure -Sdeps '{:deps {pack/pack.alpha {:git/url "https://github.com/juxt/pack.alpha.git" :sha "8acf80dd4d6e5173585f5c6fec7af28a310f3ed7"}}}' -m mach.pack.alpha.skinny --no-libs --project-path target/toolbelt-lacinia.jar
