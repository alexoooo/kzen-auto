
# kzen-auto

https://en.wikipedia.org/wiki/Robotic_process_automation  

To auto-reload backend:
1) Run `tech.kzen.auto.server.dev.BackendDevelopment` from IDE
2) Run `./gradlew -t :kzen-auto-jvm:classes` from CLI

To run frontend in dev mode:  
Run `./gradlew :kzen-auto-jvm:frontendDevelopment -PjsWatch` from CLI  
OR  
Run `tech.kzen.auto.server.dev.FrontendDevelopment` from IDE  

And then if you need auto-reload the frontend, do the following in a separate process:  
Run `./gradlew -t :kzen-auto-js:build -x test -PjsWatch` from CLI  

To build self-contained jar and executable it from CLI:
1) Run `./gradlew jar`
2) Run `java -jar kzen-auto-jvm/build/libs/kzen-auto-jvm-*.jar`
