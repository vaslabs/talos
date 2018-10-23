# talos [![Build Status](https://travis-ci.com/vaslabs/talos.svg?branch=master)](https://travis-ci.com/vaslabs/talos)

Talos is a WIP reporting tool for Akka circuit breaker

Currently it's a work in progress but the idea is as follows:

![alt text](https://docs.google.com/drawings/d/e/2PACX-1vRKebbVROyBITii1GHHigPvGbFt0QdEIzk5oT1mZa16VN30MYH4wvhqd14Qllp_1SIz3wcqDdAP5Kx6/pub?w=1440&h=1080)

## Progress

Currently you can get this screen by running the dashboard (https://github.com/kennedyoliveira/standalone-hystrix-dashboard)
and the BootstrapSpec (https://github.com/vaslabs/talos/blob/master/hystrix-reporter/src/test/scala/talos/http/BootstrapSpec.scala)

![alt text](https://user-images.githubusercontent.com/3875429/47317475-8e18ac00-d641-11e8-99fa-843e79ee7ec8.png)

## Run the demo

- Spin up the docker images provided: 

```bash
cd examples
docker-compose up
```

- Then go to a browser and navigate to (http://localhost:7979/hystrix-dashboard/)
You should see this
![alt_text](https://user-images.githubusercontent.com/3875429/47372906-a4c30f80-d6e2-11e8-8219-0a01a464ba11.png)

- The address of the stream is http://talos-demo:8080/hystrix.stream

- Click add stream and then monitor stream.

