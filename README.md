# kzen-auto

https://en.wikipedia.org/wiki/Robotic_process_automation

Dev mode (one process for client refresh, and one server process from IDE):

1) Run KzenAutoApp from IDE: --server.port=8081
    to start https://localhost:8081
    
2) Run from terminal: `./gradlew -t :kzen-auto-js:run`
    to run client proxy at https://localhost:8080 with live reload
    - Web UI JavaScript will be provided by webpack          
    - Everything expect `*.js` files is served by port 8081

Dist:
> ./gradlew shadowJar
>
> java -jar kzen-auto-jvm/build/libs/kzen-auto-*-all.jar

Web:
> http://localhost:8080/




To auto-reload frontend:
1) Run `tech.kzen.auto.server.dev.FrontendDevelopment` from IDE
2) Run `./gradlew -t :kzen-auto-js:build -x test -PjsWatch` from CLI



