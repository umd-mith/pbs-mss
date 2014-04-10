package edu.umd.mith.sga.mss

import java.io.{ File, PrintWriter }
import org.apache.sanselan._
import scala.io.Source
import scala.util.parsing.combinator._
import scala.xml.PrettyPrinter
import scalaz._, Scalaz._

trait OxfordFormats {
  /** Oxford's abbreviations for page numbers differs from Tatsuo's, but this
    * object provides parsing into the same algebraic data type.
    */
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

/** Provides a couple of constructors and serves as the driver for the Oxford
  * import process.
  */
object OxfordImporter {
  def fromManifest(manifest: File, shelfmarkAbbrev: String) =
    new OxfordImporter(manifest, None, shelfmarkAbbrev)

  def fromDirectory(dir: File, shelfmarkAbbrev: String) =
    new OxfordImporter(new File(dir, "readme.txt"), Some(dir), shelfmarkAbbrev)

  /** If the first command-line argument is a file, we assume that it's a
    * manifest and don't attempt to process images. If it's a directory, we
    * assume that it contains both the manifest and the images.
    *
    * The second argument is Tatsuo's abbreviation (e.g. "BOD+c4"), and the
    * third is the desired shelfmark identifier for the output (e.g.
    * "ms_shelley_adds_c4").
    */
  def main(args: Array[String]) {
    val file = new File(args(0))
    val shelfmarkAbbrev = args(1)
    val shelfmarkId = args(2)

    val importer = if (file.isFile) {
      fromManifest(file, shelfmarkAbbrev)
    } else if (file.isDirectory) {
      fromDirectory(file, shelfmarkAbbrev)
    } else throw new RuntimeException(f"No such file or directory: $file%s.")

    importer.createTei(new File("output"), shelfmarkId)
  }
}

/** The constructor here does a lot of work, which isn't ideal, but this is
  * essentially a glorified one-off script, and all of this data is such a
  * mess that it'll need a lot of special attention, anyway.
  */
class OxfordImporter(manifestFile: File, dir: Option[File], shelfmarkAbbrev: String)
  extends OxfordFormats with TeiTemplates {
  val HeaderPattern = """^(Filename)\s+(Description)$""".r
  val TargetPattern = """^([^\s]+\d\d\d\dt)\s+(Target Image)$""".r
  val LinePattern = """^([^\s]+\d\d\d\d)\s+([^,]+),\s+(.+)$""".r
  val FolioPattern = """^fol. (.+)$""".r

  case class PageInfo(
    oxfordId: String,
    shelfmarkLabel: String,
    pageNumberInfo: String \/ PageNumberInfo
  )

  case class PageNumberInfo(
    label: String,
    pageNumbers: List[PageNumber]
  )

  /** Reads the Oxford "readme.txt" manifest into a list containing
    * information about each page.
    */
  val manifestSource = Source.fromFile(manifestFile)
  val manifest: List[PageInfo] =
    manifestSource.getLines.map(_.trim).filter(_.nonEmpty).flatMap {
      case LinePattern(id, shelfmark, FolioPattern(pageNumberLabel)) =>
        val pageNumbers = PageNumberParser(pageNumberLabel).getOrElse(
          throw new RuntimeException(f"Invalid page number: $pageNumberLabel%s.")
        )

        PageInfo(id, shelfmark, PageNumberInfo(pageNumberLabel, pageNumbers).right).some
      case LinePattern(id, shelfmark, pageNumber) =>
        PageInfo(id, shelfmark, pageNumber.left).some
      case HeaderPattern(_, _) | TargetPattern(_, _) => None
      case line => throw new RuntimeException(f"Invalid line: $line%s.")
    }.toList

  manifestSource.close()

  /** Save the TEI content into a top-level file and a number of transcription
    * files in the given directory using the given identifier.
    */
  def createTei(outputDir: File, shelfmarkId: String) {
    val libraryId = "ox"

    // Parse the data file and create the volume we need.
    val volume = CorpusReader.byShelfmark(shelfmarkAbbrev).run.getOrElse(
      throw new RuntimeException(f"Invalid shelfmark: $shelfmarkAbbrev%s.")
    )

    val sgaId = f"$libraryId%s-$shelfmarkId%s"
    val transcriptionDir = new File(outputDir, sgaId)
    if (!outputDir.exists) outputDir.mkdir()
    if (!transcriptionDir.exists) transcriptionDir.mkdir()

    val pages = manifest.zipWithIndex.collect {
      case (
        PageInfo(
          oxfordId,
          shelfmarkLabel,
          pageNumberInfo
        ),
        n
      ) =>
        val idSeq = oxfordId.takeRight(4).toInt

        if (idSeq != n + 1) throw new RuntimeException(
           f"Sequence numbers don't match: $idSeq%d and $n%d in $id%s."
        )
    
        val sgaPageId = f"$libraryId%s-$shelfmarkId%s-$idSeq%04d"

        val size = dir.map { d =>
          val image = Sanselan.getBufferedImage(new File(d, oxfordId + ".tif"))

          (image.getWidth, image.getHeight)
        }

        val pageNumbers = pageNumberInfo.toOption.fold(List.empty[PageNumber])(
          _.pageNumbers
        )

        val pages = pageNumbers.flatMap(volume.page.get)

        val pageNumberLabel = pageNumberInfo.fold(identity, _.label)

        val content = surfaceTemplate(
          libraryId,
          shelfmarkId,
          idSeq,
          pageNumberLabel,
          shelfmarkLabel,
          size,
          pages
        )
        
        val file = new File(transcriptionDir, f"$sgaPageId%s.xml")
        val writer = new PrintWriter(file)

        writer.write(content)
        writer.close()

        pageNumbers -> (sgaPageId, shelfmarkLabel, file)
    }

    val pageMap = pages.flatMap {
      case (pageNumbers, fileInfo) => pageNumbers.map(_ -> fileInfo)
    }.toMap

    /** This looks convoluted, but essentially we're just creating a map from
      * titles to a list of page identifiers for pages that contain content
      * from each title.
      */
    val works: List[(String, List[String])] = volume.pages.map {
      case (pageNumber, page) =>
        page.lines.map(_._2.title).toSet.toVector.map((title: WorkTitle) =>
          title.name -> pageMap.get(pageNumber).fold(List.empty[String]) {
            case (sgaPageId, _, _) => List(sgaPageId)
          }
        ).toMap
      }.suml.toList.sortBy(-_._2.size)

    /** We assume that all pages will have the same shelfmark label (which
      * is reasonable, since we've selected our pages that way, but does rely
      * on Oxford and Tatsuo agreeing on shelfmarks.
      */
    val shelfmarkLabel = pages.head._2._2

    val tei = teiTemplate(libraryId, shelfmarkId, shelfmarkLabel, pages.map(_._2._3), works)

    /** We output the top-level file after manually printing the processing
      * instructions.
      */
    val prettyPrinter = new PrettyPrinter(4096, 2)
    val writer = new PrintWriter(new File(outputDir, f"$sgaId%s.xml"))
    writer.write("""<?xml version="1.0" encoding="UTF-8"?>""")
    writer.write("""<?xml-model href="../../schemata/shelley_godwin_odd.rng"""")
    writer.write("""  type="application/xml" schematypens="http://relaxng.org/ns/structure/1.0"?>""")
    writer.write(prettyPrinter.format(tei))
    writer.close()
  }
}

