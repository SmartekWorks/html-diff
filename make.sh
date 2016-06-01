#!/bin/bash
mvn package
mv ./target/result-diff-1.0-jar-with-dependencies.jar ./release/result-diff.jar
