# kzen-auto
Office automation

Dev mode (two processes for client refresh, and server from IDE):
> > run KzenAutoApp from IDE
>
> > ./gradlew -t kzen-auto-js:watch
>
> > cd kzen-auto-js && yarn run start


Dist:
> ./gradlew assemble
>
> java -jar server/build/libs/server-*.jar

Web:
> http://localhost:8080/

