# Vert.x-web Server using mongoClient

This project display deployment of a `vertx-web` based routing
HTTP server, using mongoDB (NoSQL database) `vertx-mongo-client` which allows storing and retrieving data efficiently and quickly even when the amount of data in the database is very large

An HTTP client POSTs a JSON object with the string property “​text​" and get JSON response with the fields "value" and "lexical" containing a words close to the numerical value and lexicographic value of the input word

momgoDB installation: 
https://docs.mongodb.com/manual/tutorial/install-mongodb-on-ubuntu/

maven dependency for vertx mongo client:
```
<dependency>
    <groupId>io.vertx</groupId>
    <artifactId>vertx-mongo-client</artifactId>
    <version>3.5.3</version>
</dependency>
```

To execute: 
```
mvn package exec:java
```

To run:

Start mongoDB service, Then - 

```
mvn org.codehaus.mojo:exec-maven-plugin:exec -Dexec.executable=java \
	-Dexec.args="-cp %classpath io.vertx.core.Launcher run test.project1.Server"
```

To test with client request:

```
curl -D- http://localhost:9000/analyze -d '{"text":"input"}'
```

