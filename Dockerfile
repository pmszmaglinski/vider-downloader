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

ARG dataDirectory=/root/ViderDownloader
ARG workdirDirectory=/data

RUN mkdir -p $dataDirectory && \
    ln -s $dataDirectory $workdirDirectory

WORKDIR $workdirDirectory

COPY out/artifacts/vider_downloader_jar/vider-downloader.jar /app/
ENTRYPOINT ["/usr/bin/java","-Xmx2G","-jar","/app/vider-downloader.jar"]

