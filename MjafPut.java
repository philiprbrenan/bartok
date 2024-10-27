//------------------------------------------------------------------------------
// BTree in bit machine assembler.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Design, simulate and layout  a binary tree on a silicon chip.

import java.util.*;

class MjafPut extends Mjaf                                                      // Put data into a BTree
 {

//D1 Construction                                                               // Create a BTree from nodes which can be branches or leaves.  The data associated with the BTree is stored only in the leaves opposite the keys

  MjafPut(int BitsPerKey, int BitsPerData, int MaxKeysPerLeaf, int size)        // Define a BTree with the specified dimensions
   {super(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);
   }

//D0 Tests                                                                      // Testing

//  ok(m.printHorizontally(), """
//    2    4       |
//1,2  3,4  5,6,7,8|
//""");

  static void test_split_reduction()                                            // Only split the branches that really have to be split and recombine the iother side of the split if possible to create a denser tree
   {final int BitsPerKey = 5, BitsPerData = 6, MaxKeysPerLeaf = 4, size = 5,
      N = 9;

    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);
    for (int i = 1; i <= N; i++) m.put(m.new Key(i), m.new Data(2*i));
    m.execute();
    final Path        p = m.new Path(8);
    final Stuck.Index i = p.lastNotFull();
    m.execute();

    //stop(m.print());
    ok(m.print(), """
      3(2-0)      2(4-0)      1(6-0)4        |
1,2=3       3,4=2       5,6=1        7,8,9=4 |
""");

    //stop(p);                                                                  // Path starts at root and then goes to leaf 4
    p.ok("""
T   At  Wide  Index       Value   Field name
V    0     5                  8   key
T   At  Wide  Index       Value   Field name
B    0     1                  1   found
T   At  Wide  Index       Value   Field name
S    0    20              32768   path
A    0    15      0           0     array     array
V    0     3                  0       nodeIndex     array.nodeIndex
A    3    15      1           0     array     array
V    3     3                  0       nodeIndex     array.nodeIndex
A    6    15      2           0     array     array
V    6     3                  0       nodeIndex     array.nodeIndex
A    9    15      3           0     array     array
V    9     3                  0       nodeIndex     array.nodeIndex
A   12    15      4           0     array     array
V   12     3                  0       nodeIndex     array.nodeIndex
V   15     5                  1     unary     unary
T   At  Wide  Index       Value   Field name
V    0     3                  4   nodeIndex
T   At  Wide  Index       Value   Field name
V    0     4                  1   leafIndex
""");

    //stop(i);                                                                  // The root is full so there is no last not full branch
    i.ok("""
T   At  Wide  Index       Value   Field name
S    0     9                  0   s
V    0     5                  0     pathIndex     pathIndex
B    5     1                  0     valid     valid
V    6     3                  0     value     value
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
    test_split_reduction();
   }

  public static void main(String[] args)                                        // Test if called as a program
   {try                                                                         // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_actions) oldTests(); else newTests();                          // Tests to run
      testSummary();                                                            // Summarize test results
      System.exit(testsFailed);
     }
    catch(Exception e)                                                          // Get a traceback in a format clickable in Geany
     {System.err.println(e);
      System.err.println(fullTraceBack(e));
      System.exit(1);
     }
   }
 }
