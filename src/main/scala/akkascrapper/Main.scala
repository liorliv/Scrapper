package akkascrapper

import com.example.grpc.scrapper.{DownloadedImagesRequest, ScrapeRequest, ScrapingServiceGrpc}
import io.grpc.netty.NettyChannelBuilder
import io.grpc.{ManagedChannel, StatusException, StatusRuntimeException}


object Main extends App {

  val htmlPageUrl = "https://salt.security/"

  private val channel: ManagedChannel = NettyChannelBuilder.forAddress("localhost", 50051)
    .usePlaintext()
    .build()

  private val stub: ScrapingServiceGrpc.ScrapingServiceBlockingStub = ScrapingServiceGrpc.blockingStub(channel)

  try {
    val res = stub.scrape(ScrapeRequest(htmlPageUrl).discardUnknownFields)
    val res2 = stub.getDownloadedImages(DownloadedImagesRequest().discardUnknownFields)
    println(s"$res")
    println(s"$res2")
  } catch {
    case e: StatusException => println(s"${e.getMessage} with status exception code - ${e.getStatus.getCode}")
    case e: StatusRuntimeException => println(s"${e.getCause} with status runtime exception code - ${e.getStatus.getCode}")
  }


}
