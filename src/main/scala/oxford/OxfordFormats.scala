package edu.umd.mith.sga.mss.oxford

import edu.umd.mith.sga.mss._
import java.io.File
import scala.io.Source
import scala.util.parsing.combinator._
import scalaz._, Scalaz._

case class PageInfo(
  oxfordId: String,
  shelfmarkLabel: String,
  pageNumberInfo: String \/ PageNumberInfo
)

case class PageNumberInfo(
  label: String,
  pageNumbers: List[PageNumber]
)

trait OxfordFormats {
  val HeaderPattern = """^(Filename)\s+(Description)$""".r
  val TargetPattern = """^([^\s]+\d\d\d\dt)\s+(Target Image)$""".r
  val LinePattern = """^([^\s]+\d\d\d\d)\s+([^,]+),\s+(.+)$""".r
  val FolioPattern = """^(?:(?:fol\.)|(?:p\.)) (.+)$""".r

  /** Oxford's abbreviations for page numbers differs from Tatsuo's, but this
    * object provides parsing into the same algebraic data type.
    */
  object PageNumberParser extends RegexParsers {
    override val skipWhitespace = false

    val plain: Parser[PageNumber] = "\\d+".r.map(Plain(_, ""))

    val rv: Parser[PageNumber] = ("\\d+".r ~ "[rv]".r).map {
      case n ~ "r" => Recto(n)
      case n ~ "v" => Verso(n)
    }

    val sub: Parser[PageNumber] = ("\\d+[abc]".r ~ ("\\s+".r ~> "recto|verso".r)).map {
      case n ~ "recto" => Recto(n)
      case n ~ "verso" => Verso(n)
    }

    val item: Parser[PageNumber] = plain | rv | sub

    def items: Parser[List[PageNumber]] =
      item.map(List(_)) ||| rep1sep(item, "\\s+and\\s+".r)

    def apply(s: String) = parseAll(items, s.trim)
  }

  /** Reads the Oxford "readme.txt" manifest into a list containing
    * information about each page.
    */
  def parseManifest(file: File): List[PageInfo] = {
    val manifestSource = Source.fromFile(file)
    val manifest: List[PageInfo] =
      manifestSource.getLines.map(_.trim).filter(_.nonEmpty).flatMap {
        case LinePattern(id, shelfmark, FolioPattern(pageNumberLabel)) =>
          val pageNumbers = List(Plain(pageNumberLabel, ""))
          // PageNumberParser(pageNumberLabel).getOrElse(
          //   throw new RuntimeException(f"Invalid page number: $pageNumberLabel%s.")
          // )

          PageInfo(id, shelfmark, PageNumberInfo(pageNumberLabel, pageNumbers).right).some

        case LinePattern(id, shelfmark, pageNumber) =>
          PageInfo(id, shelfmark, pageNumber.left).some

        case HeaderPattern(_, _) | TargetPattern(_, _) => None

        case line => throw new RuntimeException(f"Invalid line: $line%s.")
      }.toList

    manifestSource.close()
    manifest
  }


  /** Sanselan is very slow, so we also want to allow users to parse the output
    * of ImageMagick's `identify` application to determine image dimensions.
    */
  def parseImageInformation(file: File): Map[String, (Int, Int)] = {
    val ImageInformationLine = """^(?:.+?)/([^/]+)\.tif(?:\[\d+\])?\sTIFF\s(\d+)x(\d+)\s.*""".r

    val imageInformationSource = Source.fromFile(file)

    val imageSizes = imageInformationSource.getLines.map(_.trim).map {
      case ImageInformationLine(oxfordId, w, h) => oxfordId -> (w.toInt, h.toInt)
    }.toMap

    imageInformationSource.close()
    imageSizes
  }
}
