#!/bin/bash
mvn package
mv ./target/html-diff-1.0-jar-with-dependencies.jar ./release/html-diff.jar
