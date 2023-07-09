package akkascrapper

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.Materializer
import akka.util.ByteString

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ImageDownloadActor {

  sealed trait Message

  case class Image(url: String, res: HttpResponse)

  case class DownloadImage(urls: String, replyTo: ActorRef[Image]) extends Message

  def apply()(implicit mat: Materializer): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case DownloadImage(urls, replyTo) =>
          import context.executionContext
          val request = HttpRequest(HttpMethods.GET, urls)
          Http(context.system)
            .singleRequest(
              request,
              settings = ConnectionPoolSettings(context.system)
                .withMaxOpenRequests(300)
            )
            .onComplete {
              case Failure(_) => replyTo ! Image(urls, HttpResponse())
              case Success(value) =>
                val entityDataFuture: Future[ByteString] = value.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
                entityDataFuture.onComplete {
                  case Failure(_) => replyTo ! Image(urls, HttpResponse())
                  case Success(entityData) =>
                    val newResponse = HttpResponse(value.status, headers = value.headers, entity = entityData)
                    replyTo ! Image(urls, newResponse)
                }
            }

          Behaviors.same
      }
    }
}
