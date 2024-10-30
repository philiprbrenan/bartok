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

//D1 Comparison

//D0 Tests                                                                      // Testing

//  ok(m.printHorizontally(), """
//    2    4       |
//1,2  3,4  5,6,7,8|
//""");

  static void test_split_reduction()                                            // Only split the branches that really have to be split and recombine the iother side of the split if possible to create a denser tree
   {final int BitsPerKey = 5, BitsPerData = 6, MaxKeysPerLeaf = 4, size = 10,
      N = 9;

    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);
    for (int i = 1; i <= N; i++) m.put(m.new Key(i), m.new Data(2*i));
    final Key         k = m.new Key(8);
    final Path        p = m.new Path(k);
    final Stuck.Index i = p.lastNotFull();
    m.execute();

    //stop(m.print());
    ok(m.print(), """
      8(2-0)      7(4-0)      6(6-0)9        |
1,2=8       3,4=7       5,6=6        7,8,9=9 |
""");

    //stop(p);                                                                  // Path starts at root and then goes to leaf 4
    p.ok("""
T   At  Wide  Index       Value   Field name
V    0     5                  8   key
T   At  Wide  Index       Value   Field name
B    0     1                  1   found
T   At  Wide  Index       Value   Field name
S    0    25            1048576   path
A    0    20      0           0     array     array
V    0     4                  0       nodeIndex     array.nodeIndex
A    4    20      1           0     array     array
V    4     4                  0       nodeIndex     array.nodeIndex
A    8    20      2           0     array     array
V    8     4                  0       nodeIndex     array.nodeIndex
A   12    20      3           0     array     array
V   12     4                  0       nodeIndex     array.nodeIndex
A   16    20      4           0     array     array
V   16     4                  0       nodeIndex     array.nodeIndex
V   20     5                  1     unary     unary
T   At  Wide  Index       Value   Field name
V    0     4                  9   nodeIndex
T   At  Wide  Index       Value   Field name
V    0     4                  1   leafIndex
""");

    //stop(i);                                                                  // The root is full so there is no last not full branch
    i.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                  0   s
V    0     5                  0     pathIndex     pathIndex
B    5     1                  0     valid     valid
V    6     4                  0     value     value
""");

/*

If all the banches in the path are full, then split the root.  The two halves
are in the first and second positions of the root.  Find the one containing the
key and replace the root with it in the path to make path that starts with a
non empty branch.

Starting at the first not full element, use it as the parent and the next
element as the child to be splt.  Get the new split out child.  Find the key in
either the old or new child.  Replace the path entry with the key bearing
child.

Try and combine the non keybearing child with its left or right neighbor
depending on which side of the key bearing child it is.

Continue until all the branches beyond the start point in the path.

Now we have to split the leaf in the same way using the last branch contining
the key as the parent.

The advanatage of this technique is that it minimizes the nmber of expensive
splits performed while keeping the tree dense.  Splitting all the branches in
the path startingat the root can split branches unnecessarily, and failing to
recombine them afterwards leaves the tree more open than necessary.
 */

    m.reset();
m.new Say() {void action() {say("AAAA", i.valid);}};

    m.new Unless(i.valid)                                                       // Root is the first branch to be split
     {void Then()                                                               //
       {final NN root = m.new NN(m.root);
m.new Say() {void action() {say("BBBB");}};
        m.branchSplitRoot();
        final KeyNext left  = m.branchGetFirst(root);
        final KeyNext right = m.branchGetLast (root);
        final NN      child = m.branchFindFirstGreaterOrEqual(root, k);         // Step down from root

        final Layout.Bit keyInLeft = m.equals(left.next().v, child.v);          // Stepped to left hand child
        m.new Say() {void action() {say("CCCC", left, right, child, keyInLeft);}};
       }
     };
m.new Say() {void action() {say("DDDD");}};
    m.execute();
say("AAAA", m.print());
   }

  static void test_merge_leaves_into_root()                                     // Merge leaves into root
   {final int BitsPerKey = 5, BitsPerData = 6, MaxKeysPerLeaf = 4, size = 3,
      N = 5;

    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);
    for (int i = 1; i <= N; i++) m.put(m.new Key(i), m.new Data(2*i));
    m.execute();
    m.leafSetCurrentSize(2,2);

    //stop(m.print());
    ok(m.print(), """
      1(2-0)2      |
1,2=1        3,4=2 |
""");

    m.reset();
    m.branchMergeLeaves(0,0);
    m.execute();
    //stop(m.print());
    ok(m.print(), """
0-1          |
   1,2,3,4=1 |
""");

    m.new If(m.branchIsEmpty(m.new NN(0)))
     {void Then()
       {m.new Say() {void action() {say("AAAA");}};
        final Layout.Variable v = Layout.createVariable("leaf", m.leaf.width);
       }
     };

    m.execute();

    //stop(m.print());
    ok(m.print(), """
0-1          |
   1,2,3,4=1 |
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_split_reduction();
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    //test_split_reduction();
    test_merge_leaves_into_root();
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
