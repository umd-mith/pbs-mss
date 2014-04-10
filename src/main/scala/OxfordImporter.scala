package edu.umd.mith.sga.mss

import java.io.{ File, PrintWriter }
import org.apache.sanselan._
import scala.io.Source
import scala.util.parsing.combinator._
import scala.xml.PrettyPrinter
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

  def main(args: Array[String]) {
    val file = new File(args(0))
    val abbrev = args(1)
    val shelfmark = args(2)

    val importer = if (file.isFile) fromManifest(file, abbrev) else fromDirectory(file, abbrev)
    importer.createTei(new File("output"), shelfmark)
  }
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

  def createTei(outputDir: File, shelfmarkCode: String) {
    val libraryCode = "ox"
    val volume = CorpusReader.byShelfmark(shelfmarkAbbrev).run.getOrElse(
      throw new RuntimeException(f"Invalid shelfmark: $shelfmarkAbbrev%s.")
    )

    val sgaCode = f"$libraryCode%s-$shelfmarkCode%s"
    val transcriptionDir = new File(outputDir, sgaCode)
    if (!outputDir.exists) outputDir.mkdir()
    if (!transcriptionDir.exists) transcriptionDir.mkdir()

    val files = manifest.zipWithIndex.collect {
      case ((id, shelfmark, Some((pageNumberLabel, pageNumbers))), n) =>
        val idSeq = id.takeRight(4).toInt

        if (idSeq != n + 1) throw new RuntimeException(
           f"Sequence numbers don't match: $idSeq%d and $n%d in $id%s."
        )
    
        val sgaId = f"$libraryCode%s-$shelfmarkCode%s-$idSeq%04d"

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
          pages
        )
        
        val file = new File(transcriptionDir, f"$sgaCode%s-$idSeq%04d.xml")
        val writer = new PrintWriter(file)

        writer.write(content)
        writer.close()

        pageNumbers -> (sgaId, shelfmark, file)
    }

    val fileMap = files.flatMap {
      case (pageNumbers, fileInfo) => pageNumbers.map(_ -> fileInfo)
    }.toMap

    val works = volume.pages.map {
      case (pageNumber, page) =>
        page.lines.map(_._2.title).toSet.toVector.map((title: WorkTitle) =>
          title.name -> fileMap.get(pageNumber).fold(List.empty[String]) {
            case (sgaId, _, _) => List(sgaId)
          }
        ).toMap
      }.suml.toList.sortBy(-_._2.size)

    val tei = teiTemplate(libraryCode, shelfmarkCode, files.head._2._2, files.map(_._2._3), works)

    val prettyPrinter = new PrettyPrinter(4096, 2)
    val writer = new PrintWriter(new File(outputDir, f"$sgaCode%s.xml"))
    writer.write("""<?xml version="1.0" encoding="UTF-8"?>""")
    writer.write("""<?xml-model href="../../schemata/shelley_godwin_odd.rng"""")
    writer.write("""  type="application/xml" schematypens="http://relaxng.org/ns/structure/1.0"?>""")
    writer.write(prettyPrinter.format(tei))
    writer.close()
  }
}

