# fiware-cosmos-orion-flink-connector-examples

This repository contains a few examples for getting started with the [**fiware-cosmos-orion-flink-connector**](https://github.com/sonsoleslp/fiware-cosmos-orion-flink-connector/)*[]:

## Setup

In order to run the examples, first you need to clone the repository:
```
git clone https://github.com/sonsoleslp/fiware-cosmos-orion-flink-connector
cd fiware-cosmos-orion-flink-connector
```

Next, [download](https://github.com/sonsoleslp/fiware-cosmos-orion-flink-connector/releases/tag/1.0) the connector JAR from the connector repository and from the `fiware-cosmos-orion-flink-connector` run:

```
mvn install:install-file -Dfile=$(PATH_DOWNLOAD)/orion.flink.connector-1.0.jar -DgroupId=org.fiware.cosmos -DartifactId=orion.flink.connector -Dversion=1.0 -Dpackaging=jar
```

where `PATH_DOWNLOAD` is the path where you downloaded the JAR.

## Example 1 : Receive simulated notifications

The first example makes use of the `OrionSource` in order to receive notifications from the Orion Context Broker. For simplicity, in this example the notifications are simulated with a curl command.
Specifically, the example receives a notification every second that a node changed its temperature, and calculates the minimum temperature in a given interval.
In order to simulate the notifications you can run the following script (available at `files/example1/curl_Notification.sh`):
```
//TODO
```

This is the code of the example:
```
package org.fiware.cosmos.orion.flink.connector.examples.example1

import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.windowing.time.Time
import org.fiware.cosmos.orion.flink.connector.{OrionSource}

object Example1{

  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    // Create Orion Source. Receive notifications on port 9001
    val eventStream = env.addSource(new OrionSource(9001))

    // Process event stream
    val processedDataStream = eventStream
      .flatMap(event => event.entities)
      .map(entity => {
        val temp = entity.attrs("temperature").value.asInstanceOf[Number].floatValue()
        new Temp_Node( entity.id, temp)
      })
      .keyBy("id")
      .timeWindow(Time.seconds(5), Time.seconds(2))
      .min("temperature")

    // Print the results with a single thread, rather than in parallel
    processedDataStream.print().setParallelism(1)
    env.execute("Socket Window NgsiEvent")
  }

  case class Temp_Node(id: String, temperature: Float)
}
```

After importing the necessary dependencies, the first step is creating the source and adding it to the environment.

```
val eventStream = env.addSource(new OrionSource(9001))
```

The `OrionSource` accepts a port number as a parameter. The data received from this source is a `DataStream` of `NgsiEvent` objects.
You can check the details of this object in the [connector docs](https://github.com/sonsoleslp/fiware-cosmos-orion-flink-connector/blob/master/README.md#orionsource)

In the example, the first step of the processing is flat-mapping the entities. This operation is performed in order to put together the entity objects of severall NGSI Events.

```
val processedDataStream = eventStream
  .flatMap(event => event.entities)
```

Once you have all the entities, you can iterate over them (with `map`) and extract the desired attributes; in this case, it is the temperature.
```
// ...
.map(entity => {
    val temp = entity.attrs("temperature").value.asInstanceOf[Number].floatValue()
    new Temp_Node( entity.id, temp)
})
```

In each iteration you create a custom object with the properties you need: the entity id and the temperature. For this purpose, you can define a case class like so:
```
case class Temp_Node(id: String, temperature: Float)
```

Now you can group the created objects by entity id and perform operations on them:
```
// ...
.keyBy("id")
```

You can provide a custom processing window, like so:
```
// ...
.timeWindow(Time.seconds(5), Time.seconds(2))
```

And then specify the operation to perform in said time interval:
```
// ...
.min("temperature")
```

After the processing, you can print the results on the console:
```
processedDataStream.print().setParallelism(1)
```

Or you can persist them using the sink of your choice.

## Example 2 : Complete Orion Scenario with docker-compose

The second example does the same processing as the previous one but it writes the processed data back in the Context Broker.
In order to do it, it needs to have a Context Broker up and running. For this purpose, a `docker-compose` file is provided under `files/example2`, which deploys all the necessary containers for this scenario.


```
package org.fiware.cosmos.orion.flink.connector.examples.example2

import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.windowing.time.Time
import org.fiware.cosmos.orion.flink.connector._

object Example2 {
  final val URL_CB = "http://flinkexample_orion_1:1026/v2/entities/"
  final val CONTENT_TYPE = ContentType.JSON
  final val METHOD = HTTPMethod.POST

  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    // Create Orion Source. Receive notifications on port 9001
    val eventStream = env.addSource(new OrionSource(9001))

    // Process event stream
    val processedDataStream = eventStream
      .flatMap(event => event.entities)
      .map(entity => {
        val temp = entity.attrs("temperature").value.asInstanceOf[Number].floatValue()
        new Temp_Node(
          entity.id,
          temp)
      })
      .keyBy("id")
      .timeWindow(Time.seconds(5), Time.seconds(2))
      .min("temperature")
      .map(tempNode => {
        val url = URL_CB + tempNode.id + "/attrs"
        OrionSinkObject(tempNode.toString, url, CONTENT_TYPE, METHOD)
      })


    // Add Orion Sink
    OrionSink.addSink( processedDataStream )

    // print the results with a single thread, rather than in parallel
    processedDataStream.map(orionSinkObject => orionSinkObject.content).print().setParallelism(1)
    env.execute("Socket Window NgsiEvent")
  }

  case class Temp_Node(id: String, temperature: Float) extends  Serializable {
    override def toString :String = { "{\"temperature_min\": { \"value\":" + temperature + ", \"type\": \"Float\"}}" }
  }
}
```
// TODO