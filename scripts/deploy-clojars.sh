#!/usr/bin/env bash

mvn deploy:deploy-file -Dfile=target/toolbelt-lacinia.jar -DrepositoryId=clojars -Durl=https://clojars.org/repo -DpomFile=pom.xml

