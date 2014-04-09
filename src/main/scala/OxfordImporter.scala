package edu.umd.mith.sga.mss

import java.io.{ File, PrintWriter }
import org.apache.sanselan._
import scala.io.Source
import scala.util.parsing.combinator._
import scalaz._, Scalaz._
import scalaz.concurrent.{ Future, Task }
import scalaz.stream._

class OxfordImporter(manifestDir: File, shelfmarkAbbrev: String) extends TeiTemplates {
  val HeaderPattern = """^(Filename)\s+(Description)$""".r
  val TargetPattern = """^([^\s]+\d\d\d\dt)\s+(Target Image)$""".r
  val LinePattern = """^([^\s]+\d\d\d\d)\s+([^,]+)\s+(.+)$""".r
  val PageNumberPattern = """fol\. (\d+)([rv]|[ab] (?:recto|verso))""".r 
  
  val manifestSource = Source.fromFile(new File(manifestDir, "readme.txt"))
  val manifest: List[(String, String, String, PageNumber)] =
    manifestSource.getLines.map(_.trim).filter(_.nonEmpty).flatMap {
      case LinePattern(id, shelfmark, folio @ PageNumberPattern(number, modifier)) =>
        some((id, shelfmark, folio, toPageNumber(number, modifier)))
      case LinePattern(_, _, _) => None
      case HeaderPattern(_, _) | TargetPattern(_, _) => None
      case line => throw new RuntimeException(f"Invalid line: $line%s.")
    }.toList

  manifestSource.close()

  def createTei(outputDir: File, libraryCode: String, shelfmarkCode: String) {
    val volume = CorpusReader.byShelfmark(shelfmarkAbbrev).run.getOrElse(
      throw new RuntimeException(f"Invalid shelfmark: $shelfmarkAbbrev%s.")
    )

    val id = f"$libraryCode%s-$shelfmarkCode%s"
    val transcriptionDir = new File(outputDir, id)
    if (!outputDir.exists) outputDir.mkdir()
    if (!transcriptionDir.exists) transcriptionDir.mkdir()

    manifest.zipWithIndex.map {
      case ((id, shelfmark, folio, pageNumber), n) =>
        val idSeq = id.takeRight(4).toInt

        if (idSeq != n + 1) throw new RuntimeException(
           f"Sequence numbers don't match: $idSeq%d and $n%d."
        )

        val page = volume.page.get(pageNumber)
        val image = Sanselan.getBufferedImage(new File(manifestDir, id + ".tiff"))

        val content = surfaceTemplate(
          libraryCode,
          shelfmarkCode,
          idSeq,
          shelfmark,
          folio,
          image.getWidth,
          image.getHeight,
          Nil,
          page.fold(Vector.empty[Line])(_.lines.map(_._2))
        )

        val writer = new PrintWriter(new File(transcriptionDir, f"$id%s-$idSeq%04d.xml"))
        writer.write(content)
        writer.close()
    }
  }

  def toPageNumber(number: String, modifier: String) = modifier.head match {
    case 'r' => Recto(number)
    case 'v' => Verso(number)
    case ab if modifier.endsWith("recto") => Recto(number + ab)
    case ab if modifier.endsWith("verso") => Verso(number + ab)
  }
}

