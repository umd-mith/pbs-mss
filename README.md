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
you would first run the following command from the project directory to build
the application:

``` bash
./sbt assembly
```

Now if the `ahu2012/` directory contains the Oxford manifest (`readme.txt`), you
could run the following:

``` bash
java -jar target/scala-2.10/pbs-mss-assembly-0.1.0-SNAPSHOT.jar \
  --manifest ahu2012/readme.txt \
  --abbrev   BOD+e16 \
  --id       ms_shelley_adds_e16
```

Note that this will generate warnings about not being able to determine image
sizes. To find these sizes, you can take one of the following approaches. If
you have the images locally, you can read the sizes (and manifest) directly:

``` bash
java -jar target/scala-2.10/pbs-mss-assembly-0.1.0-SNAPSHOT.jar \
  --dir      /mnt/data/sga/ox/ahu2012 \
  --abbrev   BOD+e16 \
  --id       ms_shelley_adds_e16
```

This is extremely slow, though, since all of the images have to be processed by
the application. A much faster approach is to first use
[ImageMagick](http://www.imagemagick.org/) to read the image sizes:

``` bash
identify /mnt/data/sga/ox/ahu2012/*.tif > ahu2012/images.txt
```

Now you can run the following:

``` bash
java -jar target/scala-2.10/pbs-mss-assembly-0.1.0-SNAPSHOT.jar \
  --manifest ahu2012/readme.txt \
  --abbrev   BOD+e16 \
  --id       ms_shelley_adds_e16 \
  --sizes    ahu2012/images.txt
```

All of these processes will generate a top-level TEI file in the `output`
directory.
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

