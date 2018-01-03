#!/bin/sh

docker build -t ttrss-builder .
docker rm -f ttrss-builder
docker run --name ttrss-builder ttrss-builder true
docker cp ttrss-builder:/opt/src/org.fox.ttrss/build/outputs/apk/org.fox.ttrss-release-unsigned.apk .
