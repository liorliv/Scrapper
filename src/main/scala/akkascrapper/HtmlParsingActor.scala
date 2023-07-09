package akkascrapper

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{HttpRequest, MediaTypes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.util.ByteString

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

object HtmlParsingActor {
  sealed trait Message
  case class ParseHtml(url: String, replyTo: ActorRef[List[String]]) extends Message

  def apply()(implicit ec: ExecutionContext, mat: Materializer): Behavior[Message] =
    Behaviors.receive { (context, message) =>
      message match {
        case ParseHtml(url, replyTo) =>
          Http(context.system)
            .singleRequest(HttpRequest(uri = url).addHeader(Accept(MediaTypes.`text/html`)))
            .flatMap(response => Unmarshal(response.entity).to[ByteString])
            .map(_.utf8String)
            .onComplete {
              case Success(html) =>
                val imageUrls = parseImageUrls(html)
                replyTo ! imageUrls
              case Failure(ex) =>
                context.log.error("Failed to parse HTML: {}", ex.getMessage)
                replyTo ! List.empty[String]
            }
          Behaviors.same
      }
    }

  private def parseImageUrls(html: String): List[String] = {
    val doc = org.jsoup.Jsoup.parse(html)
    val imgElements = doc.select("img")
    imgElements.asScala.toList.map(_.absUrl("src"))
  }
}
