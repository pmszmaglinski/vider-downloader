#### Enable debug mode:
```java -Dlog4j.logLevel=DEBUG -jar vider-downloader.jar```

#### Run as docker container
```docker run -it --entrypoint /bin/bash -v "$(PWD):/data" viderdownloader:latest```