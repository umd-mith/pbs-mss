package edu.umd.mith.sga.mss.oxford

import java.io.File
import org.clapper.argot._, ArgotConverters._

trait OxfordCliParser { this: OxfordFormats =>
  val parser = new ArgotParser(
    "oxford-import",
    preUsage = Some("PBS-MSS Oxford importer Version 0.1.0.")
  )

  def getShelfmarkId: String = shelfmarkId.value.getOrElse(
    parser.usage("You must provide a shelfmark identifier.")
  )

  def getShelfmarkAbbrev: String = shelfmarkAbbrev.value.getOrElse(
    parser.usage("You must provide Tokoo's shelfmark abbreviation.")
  )

  def getManifest: List[PageInfo] = manifestFile.value.orElse(
    imageDir.value.map { dir =>
      val file = new File(dir, "readme.txt")
      if (!file.exists || !file.isFile) {
        parser.usage(s"""Manifest file "${file.getName}" does not exist.""")
      } else file
    }
  ).fold(
     parser.usage("You must provide a manifest file.")
  )(parseManifest)

  def getImageInformation: Option[Map[String, (Int, Int)]] =
    imageInformationFile.value.map(parseImageInformation)

  val shelfmarkAbbrev = parser.option[String](
    List("a", "abbrev"),
    "abbreviation",
    "Tokoo's shelfmark abbreviation"
  )

  val shelfmarkId = parser.option[String](
    List("i", "id"),
    "identifier",
    "shelfmark identifier for output"
  )

  val manifestFile = parser.option[File](
    List("m", "manifest"),
    "file",
    "Oxford manifest file"
  ) { (s, _) =>
    val file = new File(s)
    if (!file.exists || !file.isFile) {
      parser.usage(s"""Manifest file "$s" does not exist.""")
    } else file
  }

  val imageDir = parser.option[File](
    List("d", "dir"),
    "directory",
    "image directory"
  ) { (s, _) =>
    val dir = new File(s)
    if (!dir.exists || !dir.isDirectory) {
      parser.usage(s"""Image directory "$s" does not exist.""")
    } else dir
  }

  val imageInformationFile = parser.option[File](
    List("s", "sizes"),
    "file",
    "image sizes (output of ImageMagick `identify`)"
  ) { (s, _) =>
    val file = new File(s)
    if (!file.exists || !file.isFile) {
      parser.usage(s"""Image information file "$s" does not exist.""")
    } else file
  }
}
