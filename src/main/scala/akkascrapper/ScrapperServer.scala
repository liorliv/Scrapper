package akkascrapper

import java.util.logging.Logger
import io.grpc.{Server, ServerBuilder}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.Materializer
import akka.util.Timeout
import com.example.grpc.scrapper.{DownloadedImagesRequest, DownloadedImagesResponse, ScrapeRequest, ScrapeResponse, ScrapingServiceGrpc}
import com.example.grpc.scrapper.ScrapingServiceGrpc.ScrapingService
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

object ScrapperServer {
  private val logger = Logger.getLogger(classOf[Server].getName)

  def main(args: Array[String]): Unit = {
    val server = new ScrapperServer(ExecutionContext.global)
    server.start()
    server.blockUntilShutdown()
  }

  private val port = 50051
}

class ScrapperServer(executionContext: ExecutionContext) { self =>
  private[this] var server: Server = null

  implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "HtmlImageDownloader")
  implicit val timeout: Timeout = Timeout(5.minutes)

  val htmlParsingActor = system.systemActorOf(HtmlParsingActor(), "HtmlParsingActor")
  val imageDownloadActor = system.systemActorOf(ImageDownloadActor(), "ImageDownloadActor")
  val fileSystemActor = system.systemActorOf(FileSystemActor(), "FileSystemActor")

  val htmlPageUrl = "https://salt.security/"

  private def start(): Unit = {

    val a = new ScrapingServiceImpl(htmlParsingActor, imageDownloadActor, fileSystemActor)
    server = ServerBuilder.forPort(ScrapperServer.port).addService(ScrapingServiceGrpc.bindService(a, executionContext)).build.start
    ScrapperServer.logger.info("Server started, listening on " + ScrapperServer.port)
    sys.addShutdownHook {
      System.err.println("*** shutting down gRPC server since JVM is shutting down")
      self.stop()
      System.err.println("*** server shut down")
    }
  }

  private def stop(): Unit = {
    if (server != null) {
      server.shutdown()
    }
  }

  private def blockUntilShutdown(): Unit = {
    if (server != null) {
      server.awaitTermination()
    }
  }

  class ScrapingServiceImpl(
     htmlParsingActor: ActorRef[HtmlParsingActor.Message],
     imageDownloadActor: ActorRef[ImageDownloadActor.Message],
     fileSystemActor: ActorRef[FileSystemActor.Message]
)
    extends ScrapingService {

    override def scrape(request: ScrapeRequest): Future[ScrapeResponse] = {
      val imageUrlsFuture = htmlParsingActor ? (HtmlParsingActor.ParseHtml(request.url, _))
      val imageUrlsAndResponsesFuture = imageUrlsFuture.map { imageUrls =>
        imageUrls.map { imageUrl =>
          val responseFuture = imageDownloadActor ? (ImageDownloadActor.DownloadImage(imageUrl, _))
          responseFuture.map(response => response)
        }
      }

      imageUrlsAndResponsesFuture
        .flatMap { imageUrlsAndResponses =>
          Future.sequence(imageUrlsAndResponses)
        }
        .map { imageUrlsAndResponses =>
          imageUrlsAndResponses.foreach { response =>
            fileSystemActor ! FileSystemActor.SaveImage(response.url, response.res)
          }
          ScrapeResponse(success = true, message = "Scraping completed successfully")
        }
        .recover {
          case ex =>
            ScrapeResponse(message = s"Scraping failed: ${ex.getMessage}")
        }
    }

    override def getDownloadedImages(request: DownloadedImagesRequest): Future[DownloadedImagesResponse] = {
      val resultFuture = fileSystemActor ? FileSystemActor.GetPaths
      resultFuture.map { list => DownloadedImagesResponse(list) }
    }
  }

}
