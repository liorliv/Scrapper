package akkascrapper

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import java.nio.file.{Files, Paths}
import scala.concurrent.ExecutionContext

object FileSystemActor {
  sealed trait Message
  case class SaveImage(url: String, response: HttpResponse) extends Message
  case class GetPaths(replyTo: ActorRef[List[String]]) extends Message

  def apply()(implicit ec: ExecutionContext, mat: Materializer): Behavior[Message] = Behaviors.receive { (context, message) =>
    message match {
      case SaveImage(url, response) =>
        val fileName = Paths.get(s"src/main/resources/tmp", url.split("/").lastOption.getOrElse("default.jpg"))
        val fileSink = FileIO.toPath(fileName)
        response.entity.dataBytes.runWith(fileSink)
        Behaviors.same

      case GetPaths(replyTo) =>
        replyTo ! getAllPathsInFolder("src/main/resources/tmp")
        Behaviors.same
    }
  }

  def getAllPathsInFolder(folderPath: String): List[String] = {
    val directory = Paths.get(folderPath)

    val stream = Files.walk(directory)

    try {
      val paths = stream.toArray.map(_.asInstanceOf[java.nio.file.Path])
      paths.map(_.toString).toList
    } finally {
      stream.close()
    }
  }
}
