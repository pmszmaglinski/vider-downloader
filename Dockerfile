FROM ubuntu:focal-20220105

ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get -y update && \
    apt-get -y install openjdk-17-jre \
                    libleptonica-dev \
                    g++ \
                    autoconf \
                    automake \
                    libtool \
                    pkg-config \
                    libpng-dev \
                    libjpeg8-dev \
                    libtiff5-dev \
                    zlib1g-dev \
                    tesseract-ocr \
                    libtesseract-dev &&\
    rm -rf /var/lib/apt/lists/*

COPY out/artifacts/vider_downloader_jar/vider-downloader.jar /app/
WORKDIR /app
ENTRYPOINT ["/usr/bin/java","-jar","vider-downloader.jar"]

#TODO: - Create files (logs,configs) outside of container. Mount external volume ?
#      - Where file are downloaded ? Contaner user's homedir ?
