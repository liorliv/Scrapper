package akkascrapper

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.Materializer
import akka.util.Timeout
import com.example.grpc.scrapper.{DownloadedImagesRequest, DownloadedImagesResponse, ScrapeRequest, ScrapeResponse}
import com.example.grpc.scrapper.ScrapingServiceGrpc.ScrapingService

import scala.concurrent.{ExecutionContext, Future}

class ScrapingServiceImpl(
    htmlParsingActor: ActorRef[HtmlParsingActor.Message],
    imageDownloadActor: ActorRef[ImageDownloadActor.Message],
    fileSystemActor: ActorRef[FileSystemActor.Message]
)(implicit system: ActorSystem[_], materializer: Materializer, timeout: Timeout, executionContext: ExecutionContext)
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
