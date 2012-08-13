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

The library uses the [Maven build system](http://maven.apache.org/). If you
have Maven installed on your machine, you can use the following command to
convert the corpus to a format appropriate for topic modeling with
[MALLET](http://mallet.cs.umass.edu/), for example:

    mvn compile exec:java \
      -Dexec.mainClass="edu.umd.mith.sga.mss.MalletConverter" \
      -Dexec.args="pbs-mss-pages.txt"

If you also have MALLET installed, you can then use the following commands to
train a topic model:

    mallet import-file --remove-stopwords --keep-sequence \
      --input pbs-mss-pages.txt --output pbs-mss-pages.mallet

    mallet train-topics \
      --num-topics 30 --optimize-interval 10 \
      --input pbs-mss-pages.mallet \
      --output-topic-keys pbs-mss-pages-30-keys.txt \
      --output-model pbs-mss-pages-30.model

The `examples` directory includes some sample output files.

