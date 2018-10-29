package org.fiware.cosmos.orion.flink.connector.examples.example6
import java.util

import org.apache.flink.streaming.connectors.nifi.{NiFiDataPacket, NiFiDataPacketBuilder, NiFiSink, _}
import org.apache.flink.api.common.functions.{AggregateFunction, RuntimeContext}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.nifi.remote.client.SiteToSiteClientConfig
import org.fiware.cosmos.orion.flink.connector.OrionSource
import org.apache.nifi.remote.client.SiteToSiteClient
import org.apache.nifi.remote.client.SiteToSiteClientConfig

import scala.collection.JavaConverters._


/**
  * Example6 Orion Connector
  * @author @sonsoleslp
  */
object Example6{

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

      .aggregate(new AverageAggregate)

      .map(a=>new NiFiDataPacket {
        override def getAttributes: util.Map[String, String] = {
          return null
        }

        override def getContent: Array[Byte] = {
          return "".getBytes()
        }
      })
    // print the results with a single thread, rather than in parallel

    processedDataStream.print().setParallelism(1)
    val clientConfig: SiteToSiteClientConfig = new SiteToSiteClient.Builder()
      .url("http://localhost:8080/nifi")
      .portName("Data from Flink")
      .requestBatchCount(5)
      .buildConfig()

    val nifiSink: NiFiSink[NiFiDataPacket] = new NiFiSink[NiFiDataPacket](clientConfig, new NiFiDataPacketBuilder[NiFiDataPacket]() {
       override def createNiFiDataPacket(tuple: NiFiDataPacket, ctx: RuntimeContext): NiFiDataPacket = {
         val newAttributes = Map("processed" -> "true").asJava
         new StandardNiFiDataPacket("".getBytes(), newAttributes)
      }
    })
    processedDataStream.addSink(nifiSink)
    env.execute("Socket Window NgsiEvent")
  }

  case class Temp_Node(id: String, temperature: Float)

  class AverageAggregate extends AggregateFunction[Temp_Node, (Float,Float), Float] {
    override def createAccumulator() = (0L, 0L)

    override def add(value: (Temp_Node), accumulator: (Float, Float)) =
      (accumulator._1 + value.temperature, accumulator._2 + 1L)

    override def getResult(accumulator: (Float, Float)) = accumulator._1 / accumulator._2

    override def merge(a: (Float, Float), b: (Float, Float)) =
      (a._1 + b._1, a._2 + b._2)
  }


}