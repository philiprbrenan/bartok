#!/usr/bin/perl -I/home/phil/perl/cpan/DataTableText/lib/
#-------------------------------------------------------------------------------
# Push Bartok bit machine code to GitHub
# Philip R Brenan at gmail dot com, Appa Apps Ltd Inc., 2024
#-------------------------------------------------------------------------------
use warnings FATAL => qw(all);
use strict;
use Carp;
use Data::Dump qw(dump);
use Data::Table::Text qw(:all);
use GitHub::Crud qw(:all);
use feature qw(say current_sub);

my $home = currentDirectory;                                                    # Local files
my $user = q(philiprbrenan);                                                    # User
my $repo = q(bartok);                                                           # Repo
my $wf   = q(.github/workflows/main.yml);                                       # Work flow on Ubuntu
my @ext  = qw(.java .md .pl);                                                   # Files to upload to github

push my @files, searchDirectoryTreesForMatchingFiles($home, @ext);              # Files to upload
my @java = map {fn $_}  grep {fe($_) eq q(java)} @files;                        # Java to test

for my $s(@files)                                                               # Upload each selected file
 {my $c = readBinaryFile $s;                                                    # Load file

  $c = expandWellKnownWordsAsUrlsInMdFormat $c if $s =~ m(README);              # Expand README

  my $t = swapFilePrefix $s, $home;                                             # File on github
  my $w = writeFileUsingSavedToken($user, $repo, $t, $c);                       # Write file into github
  lll "$w  $t";
 }

if (1)                                                                          # Write workflow
 {my $d = dateTimeStamp;
  my $c = q(com/AppaApps/Silicon);                                              # Package to classes folder
  my $j = join ', ', @java;                                                     # Java files
  my $y = <<"END";
# Test $d

name: Test
run-name: $repo

on:
  push:
    paths:
      - '**/main.yml'

concurrency:
  group: \${{ github.workflow }}-\${{ github.ref }}
  cancel-in-progress: true

jobs:

  test:
    permissions: write-all
    runs-on: ubuntu-latest

    strategy:
      matrix:
        task: [$j]

    steps:
    - uses: actions/checkout\@v3
      with:
        ref: 'main'

    - name: 'JDK 22'
      uses: oracle-actions/setup-java\@v1
      with:
        website: jdk.java.net

    - name: Install Tree
      run:
        sudo apt install tree

    - name: Position
      run: |
        mkdir -p $c
        cp `find . -name "*.java"` $c

    - name: Files
      run:
        tree

    - name: Compile
      run: |
        javac -g -d Classes -cp Classes `find $c -name "*.java"`
END

  for my $j(@java)                                                              # Java files
   {$y .= <<END;

    - name: Test $j
      if: matrix.task == '$j'
      run: |
        java -cp Classes $c/$j
END
   }

  my $f = writeFileUsingSavedToken $user, $repo, $wf, $y;                       # Upload workflow
  lll "$f  Ubuntu work flow for $repo";
 }