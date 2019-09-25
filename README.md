# carpooling-reactive-mongodb

Playground for reactive spring and mongodb.

Uses mongodb transaction functionality that is only available on replica set, so it depends on a running local mongodb instance.

## Start mongodb

Start:

```shell script
mongod --config /usr/local/etc/mongod.conf --replSet rs0
```

\[only first time\] Setup:

```shell script
mongo --eval "rs.initiate()"
```

## Build and test

Tests connect to the mongodb instance and demonstrate the application's behavior, also on concurrency:

```sh
mvn clean install
```
