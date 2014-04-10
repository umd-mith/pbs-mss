package edu.umd.mith.sga.mss

import java.io.{ File, PrintWriter }
import org.apache.sanselan._
import scala.io.Source
import scala.util.parsing.combinator._
import scalaz._, Scalaz._
import scalaz.concurrent.{ Future, Task }
import scalaz.stream._

trait OxfordFormats {
  object PageNumberParser extends RegexParsers {
    override val skipWhitespace = false

    val plain: Parser[PageNumber] = ("\\d+".r ~ "[rv]".r).map {
      case n ~ "r" => Recto(n)
      case n ~ "v" => Verso(n)
    }

    val sub: Parser[PageNumber] = ("\\d+[abc]".r ~ ("\\s+".r ~> "recto|verso".r)).map {
      case n ~ "recto" => Recto(n)
      case n ~ "verso" => Verso(n)
    }

    val item: Parser[PageNumber] = plain | sub

    def items: Parser[List[PageNumber]] =
      item.map(List(_)) ||| rep1sep(item, "\\s+and\\s+".r)

    def apply(s: String) = parseAll(items, s.trim)
  }
}

object OxfordImporter {
  def fromManifest(manifest: File, shelfmarkAbbrev: String) =
    new OxfordImporter(manifest, None, shelfmarkAbbrev)

  def fromDirectory(dir: File, shelfmarkAbbrev: String) =
    new OxfordImporter(new File(dir, "readme.txt"), Some(dir), shelfmarkAbbrev)
}

class OxfordImporter(manifestFile: File, dir: Option[File], shelfmarkAbbrev: String)
  extends OxfordFormats with TeiTemplates {
  val HeaderPattern = """^(Filename)\s+(Description)$""".r
  val TargetPattern = """^([^\s]+\d\d\d\dt)\s+(Target Image)$""".r
  val LinePattern = """^([^\s]+\d\d\d\d)\s+([^,]+),\s+(.+)$""".r
  val FolioPattern = """^fol. (.+)$""".r
 
  val manifestSource = Source.fromFile(manifestFile)
  val manifest: List[(String, String, Option[(String, List[PageNumber])])] =
    manifestSource.getLines.map(_.trim).filter(_.nonEmpty).flatMap {
      case LinePattern(id, shelfmark, FolioPattern(pageNumberLabel)) =>
        val pageNumbers = PageNumberParser(pageNumberLabel).getOrElse(
          throw new RuntimeException(f"Invalid page number: $pageNumberLabel%s.")
        )

        Some((id, shelfmark, Some((pageNumberLabel, pageNumbers))))
      case LinePattern(id, shelfmark, _) => Some((id, shelfmark, None)) 
      case HeaderPattern(_, _) | TargetPattern(_, _) => None
      case line => throw new RuntimeException(f"Invalid line: $line%s.")
    }.toList

  manifestSource.close()

  def createTei(outputDir: File, libraryCode: String, shelfmarkCode: String) {
    val volume = CorpusReader.byShelfmark(shelfmarkAbbrev).run.getOrElse(
      throw new RuntimeException(f"Invalid shelfmark: $shelfmarkAbbrev%s.")
    )

    val sgaCode = f"$libraryCode%s-$shelfmarkCode%s"
    val transcriptionDir = new File(outputDir, sgaCode)
    if (!outputDir.exists) outputDir.mkdir()
    if (!transcriptionDir.exists) transcriptionDir.mkdir()

    println(volume.pages.map(_._1).mkString(" "))

    manifest.zipWithIndex.collect {
      case ((id, shelfmark, Some((pageNumberLabel, pageNumbers))), n) =>
        val idSeq = id.takeRight(4).toInt

        if (idSeq != n + 1) throw new RuntimeException(
           f"Sequence numbers don't match: $idSeq%d and $n%d in $id%s."
        )

        println(pageNumbers.mkString(" "))
        val pages = pageNumbers.flatMap(volume.page.get)
        val size = dir.map { d =>
          val image = Sanselan.getBufferedImage(new File(d, id + ".tif"))

          (image.getWidth, image.getHeight)
        }

        val content = surfaceTemplate(
          libraryCode,
          shelfmarkCode,
          idSeq,
          shelfmark,
          pageNumberLabel,
          size,
          Nil,
          pages
        )
        
        val writer = new PrintWriter(new File(transcriptionDir, f"$sgaCode%s-$idSeq%04d.xml"))
        writer.write(content)
        writer.close()
    }
  }
}

