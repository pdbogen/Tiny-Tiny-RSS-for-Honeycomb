FROM debian:buster

RUN export DEBIAN_FRONTEND=noninteractive && \
    apt-get update && \
    apt-get -y install unzip wget openjdk-8-jdk && \
    wget https://dl.google.com/android/repository/sdk-tools-linux-3859397.zip && \
    mkdir -p /opt && \
    unzip -d /opt sdk-tools-linux-3859397.zip && \
    yes | /opt/tools/bin/sdkmanager --licenses

ENV ANDROID_HOME /opt/

ADD . /opt/src

RUN cd /opt/src && \
    chmod +x gradlew && \
    ./gradlew build
