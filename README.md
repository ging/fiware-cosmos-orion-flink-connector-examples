# fiware-cosmos-orion-flink-connector-examples

This repository contains a few examples for getting started with the [**fiware-cosmos-orion-flink-connector**](https://github.com/sonsoleslp/fiware-cosmos-orion-flink-connector/):

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
In order to simulate the notifications coming from the Context Broker you can run the following script (available at `files/example1/curl_Notification.sh`):
```
while true
do
    temp=$(shuf -i 18-53 -n 1)
    number=$(shuf -i 1-3113 -n 1)

    curl -v -s -S X POST http://localhost:9001 \
    --header 'Content-Type: application/json; charset=utf-8' \
    --header 'Accept: application/json' \
    --header 'User-Agent: orion/0.10.0' \
    --header "Fiware-Service: demo" \
    --header "Fiware-ServicePath: /test" \
    -d  '{
         "data": [
             {
                 "id": "R1","type": "Node",
                 "co": {"type": "Float","value": 0,"metadata": {}},
                 "co2": {"type": "Float","value": 0,"metadata": {}},
                 "humidity": {"type": "Float","value": 40,"metadata": {}},
                 "pressure": {"type": "Float","value": '$number',"metadata": {}},
                 "temperature": {"type": "Float","value": '$temp',"metadata": {}},
                 "wind_speed": {"type": "Float","value": 1.06,"metadata": {}}
             }
         ],
         "subscriptionId": "57458eb60962ef754e7c0998"
     }'


    sleep 1
done
```

This is the code of the example which is explained step by step below:
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
You just need to run the following command (probably with `sudo`):
```
docker-compose up
```

Once you have the Context Broker and the rest of the machines up and running, you need to create some entities and subscribe to them in order to get a notification whenever their value change.
First, let's create a room entity (you can find the script under `files/example2/curl_CreateNewEntity.sh`):
```
curl localhost:1026/v2/entities -s -S -H 'Content-Type: application/json' -d @- <<EOF
{
  "id": "Room1",
  "type": "Room",
  "temperature": {
    "value": 23,
    "type": "Float"
  },
  "pressure": {
    "value": 720,
    "type": "Integer"
  },
  "temperature_min": {
    "value": 0,
    "type": "Float"
  }
}
EOF
```

Now you can subscribe to any changes in the attributes you are interested in. Again, you can find this script under (`files/example2/curl_SubscribeToEntityNotifications.sh`). Do not forget to change `$MY_IP` to your machine's IP Address (must be accesible from the docker container):
```
curl -v localhost:1026/v2/subscriptions -s -S -H 'Content-Type: application/json' -d @- <<EOF
{
  "description": "A subscription to get info about Room1",
  "subject": {
	"entities": [
  	{
    	"id": "Room1",
    	"type": "Room"
  	}
	],
	"condition": {
  	"attrs": [
    	"pressure",
	"temperature"
  	]
	}
  },
  "notification": {
	"http": {
  	"url": "http://$MY_IP:9001/notify"
	},
	"attrs": [
  	"temperature",
	"pressure"
	]
  },
  "expires": "2040-01-01T14:00:00.00Z",
  "throttling": 5
}
EOF
```

You might want to check that you have created it correcty by running:
```
curl localhost:1026/v2/subscriptions
```

Now you may start triggering changes in the entity's attributes. For that, you can use the following script (`files/example2/curl_ChangeAttributes.sh`):
```
while true
do
    timestamp=$(shuf -i 1-100000000 -n 1)
    temp=$(shuf -i 18-53 -n 1)
    number=$(shuf -i 1-3113 -n 1)
    # echo

    curl localhost:1026/v2/entities/Room1/attrs -s -S -H 'Content-Type: application/json' -X PATCH -d '{
      "temperature": {
        "value": '$temp',
        "type": "Float"
      },
      "pressure": {
        "value": '$number',
        "type": "Float"
      }
    }'
    sleep 1
done
```

Let's take a look at the Example2 code now:

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
        new Temp_Node(entity.id, temp)
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

As you can see, it is very similar to the previous example. The main difference is that it writes the processed data back in the Context Broker through the **`OrionSink`**.
After calculating the minimum temperature, the output data needs to be adapted to the format accepted by the **`OrionSink`**. It accepts a stream of **`OrionSinkObject`**, which accepts 4 arguments:
  * **Message**: It is the content of the update that is going to be sent to the Context Broker. It can be a single value converted to string or, more commonly, a stringified JSON containing the new data. The connector does not build the JSON from a Scala object for us; we need to build it ourselves. We may want to override the `toString` message of the case class we've created in order to facilitate this process, as seen on the example.
  * **URL**: It is the URL to which the message will be posted. Normally it has a common base but it somewhat varies depending on the entity we're receiving data from.
  * **Content Type**: Whether the message is in JSON format (`ContentType.JSON`) or in plain text (`ContentType.Plain`).
  * **HTTP Method**: The HTTP method used for sending the update. It can be: `HTTPMethod.POST`, `HTTPMethod.PUT` or `HTTPMethod.PATCH`.

In the example, an **`OrionSinkObject`** is built from the `Temp_Node` object converted to JSON. Thus, the specified data type is JSON. The URL is formed with the hostname of the docker container where the Context Broker is, and the id of the specific entity we are receiving data from. It uses the HTTP Post method in order to send the message to the Context Broker.
```
// ...
.map(tempNode => {
    val url = URL_CB + tempNode.id + "/attrs"
    OrionSinkObject(tempNode.toString, url, CONTENT_TYPE, METHOD)
})
```
Finally, we send the processed DataStream through the OrionSink
```
OrionSink.addSink( processedDataStream )
```
If you run the example, you will see that the minimum temperature calculated is displayed in the console.
You can test that it has been changed in the Context Broker as well by running the following command several times and checking that the `temperature_min` attribute is constantly changing:
```
curl localhost:1026/v2/entities/Room1
```

### Example 3
