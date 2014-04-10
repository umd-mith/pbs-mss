The Percy Bysshe Shelley Manuscript Corpus
==========================================

This corpus was prepared by Tatsuo Tokoo with support from a Grant-in-Aid for
Scientific Research (KAKENHI) from the Japan Society for the Promotion of
Science (JSPS) in 2001-2002. It is presented here with minor revisions and
tools for manipulation by the [Maryland Institute for Technology in the
Humanities](http://mith.umd.edu/).

The corpus is licensed under a [Creative Commons Attribution 3.0 Unported
License](http://creativecommons.org/licenses/by/3.0/) and all supporting
software is released by MITH under the [Apache License, Version
2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

The Corpus Format
-----------------

The corpus uses a custom markup scheme implemented by Tatsuo Tokoo. Each line
represents a line of the manuscript, with metadata about the line following
the text and set apart by forward slashes. Within the text of the line, angle
brackets are used to indicate "doubtful words" and square brackets to
indicate deletions.

Working with the Corpus
-----------------------

The `src` directory contains utilities for working with this format in Java
(or any other language that targets the Java Virtual Machine; the library
itself is written in Scala), along with some simple command line tools.

These utilities can be built and run using [sbt](http://www.scala-sbt.org/).
For example, to create TEI files for the MS. Shelley adds. c. 4 notebook,
you could run the following command:

``` bash
./sbt "run-main edu.umd.mith.sga.mss.OxfordImporter \
  /mnt/data/sga/ox/bhv2011/readme.txt BOD+c4 ms_shelley_adds_c4"
```

The first argument is the path to the manifest for the notebook, the second is
the shelfmark abbreviation used in this corpus (see [the
key](https://github.com/umd-mith/pbs-mss/blob/master/src/main/resources/edu/umd/mith/sga/mss/shelfmarks.txt)
for the full list of abbreviations), and the third argument is the shelfmark
identifier you wish to use in the output.

Running the process using this command will not include `ulx`, `uly`, `lrx`, or `lry`
attributes on the `surface` elements (these are used to indicate the size of the
facsimile image). To generate these elements, point the application to the directory
containing both the manifest and the TIF images:

``` bash
./sbt "run-main edu.umd.mith.sga.mss.OxfordImporter \
  /mnt/data/sga/ox/bhv2011/ BOD+c4 ms_shelley_adds_c4"
```

Note that this will run much slower, since it's reading the sizes of the images files,
which can be very large.

Either process will generate a top-level TEI file in the `output` directory.
This file includes the transcription files, which will be generated in a
directory under the output directory. For example, for the commands above,
you'll see the following files:

```
ox-ms_shelley_adds_c4.xml
ox-ms_shelley_adds_c4/ox-ms_shelley_adds_c4-0002.xml
ox-ms_shelley_adds_c4/ox-ms_shelley_adds_c4-0003.xml
ox-ms_shelley_adds_c4/ox-ms_shelley_adds_c4-0004.xml
...

```

The top-level file will also include `msItem` elements for every work represented
in the notebook. For example, in the top-level file produced for MS. Shelley Adds.
c. 4 we see the following:
              
``` xml              
<msItem>
  <bibl>
    <title>The Triumph of Life</title>
  </bibl>
  <locusGrp>
    <locus target="#ox-ms_shelley_adds_c4-0046 #ox-ms_shelley_adds_c4-0047 ...
    ...
```

These elements are used by the Shared Canvas manifest generator to create ranges
in the logical Shared Canvas manifest for the notebook.

Additional metadata that you wish to include in the Shared Canvas manifest should
be added to these elements. See the examples in the [Shelley-Godwin
Archive](https://github.com/umd-mith/sga) repository for more detailed examples.

