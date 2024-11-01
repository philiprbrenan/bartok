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
   {final int BitsPerKey = 5, BitsPerData = 6, MaxKeysPerLeaf = 4, size = 10,
      N = 9;

    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);
    for (int i = 1; i <= N; i++) m.put(m.new Key(i), m.new Data(2*i));
    final Key         k = m.new Key(8);
    final Path        p = m.new Path(k);   // Push
    final Stuck.Index i = p.lastNotFull(); // together as we are only ever interested in the last not full
    m.execute();

    //stop(m.print());
    ok(m.print(), """
      8(2-0)      7(4-0.1)      6(6-0.2)9        |
1,2=8       3,4=7         5,6=6          7,8,9=9 |
""");

    //stop(p);                                                                  // Path starts at root and then goes to leaf 9
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
the path starting at the root can split branches unnecessarily, and failing to
recombine them afterwards leaves the tree more open than necessary.
 */

    m.reset();
    m.new Unless(i.valid)                                                       // Do we need to split the root?
     {void Then()                                                               // Root is the first branch to be split
       {final NN root = m.new NN(m.root);
        m.branchSplitRoot();
        final KeyNext left  = m.branchGetFirst(root);                           // Key, next pair dscribing the left child of the root
        final NN         ln = left.next();                                      // Index of left child
        final NN         rn = m.branchGetTopNext(root);                         // Index of right child
        final NN      child = m.branchFindFirstGreaterOrEqual(root, k);         // Step down from root

        m.new IfElse (m.equals(ln.v, child.v))                                  // Did we descend to the left child?
         {void Then()                                                           // Stepped to left hand child so put it in the path and leave the right child alone as there is no other child to combine it with
           {p.path.setElementAt(ln.v, root.v);                                  // Place the left child back in the path because the leaf we are seeking is under it
           }
          void Else()                                                           // Stepped to right hand child so put it in the path and leave the left child alone as there is no other child to combine it with
           {p.path.setElementAt(rn.v, root.v);                                  // Place the left child back in the path becuae the leaf we are seeking is under it
           }
         };
       }
     };
    m.execute();

    //stop(m.print());                                                            // Path starts at root and then goes to leaf 4
    ok(m.print(), """
                   4(4-0)5                     |
      8(2-4)7                   6(6-5)9        |
1,2=8        3,4=7        5,6=6        7,8,9=9 |
""");

    //stop(p);
    p.ok("""
T   At  Wide  Index       Value   Field name
V    0     5                  8   key
T   At  Wide  Index       Value   Field name
B    0     1                  1   found
T   At  Wide  Index       Value   Field name
S    0    25            1048581   path
A    0    20      0           5     array     array
V    0     4                  5       nodeIndex     array.nodeIndex
A    4    20      1           5     array     array
V    4     4                  0       nodeIndex     array.nodeIndex
A    8    20      2           5     array     array
V    8     4                  0       nodeIndex     array.nodeIndex
A   12    20      3           5     array     array
V   12     4                  0       nodeIndex     array.nodeIndex
A   16    20      4           5     array     array
V   16     4                  0       nodeIndex     array.nodeIndex
V   20     5                  1     unary     unary
T   At  Wide  Index       Value   Field name
V    0     4                  9   nodeIndex
T   At  Wide  Index       Value   Field name
V    0     4                  1   leafIndex
""");

// The index position i now shows the position of the last not full branch, either because it actually found one correctly or because we split the root to make one - and - in this case the index was already zero so it now shows the correct result for this outcome as well.

say("AAAA---------------------------------------------------");
    m.reset();
    final NN parent = m.new NN(p.nodeIndex.v.like());                           // Current last not full branch will be the parent
    final NN child  = m.new NN();                                               // Child of parent
    final BI divide = m.branchFindFirstGreaterOrEqual(parent, k, child);        // inDivide index to reach child
    m.execute();
say("AAAA", parent, child, divide);

   }

  void branchMergeTopLeaves(NN NodeIndex)                                       // Merge the top two leaves of a branch if possible. We assume that the children of the parent are leaves without checking that this is true.
   {new Block()
     {void code()
       {final BI size = new BI(branchGetCurrentSize(NodeIndex));                // Size of branch
        returnIfAllZero(size.v);                                                // Cannot merge an empty branch
        final KeyNext tkn = branchGetLast(NodeIndex);                           // Get last key, next of branch
        final NN     left = tkn.next();                                         // Last key, next pair indicates the left child leaf
        final NN    right = branchGetTopNext(NodeIndex);                        // Top node indicates the right child leaf
        new If(leafJoinable(left, right))                                       // Can we join the two leaves?
         {void Then()                                                           // Join the two leaves
           {leafJoin(left, right);                                              // Merge the right leaf into the left leaf
            branchSetTopNext(NodeIndex, left);                                  // Make the left leaf inot the new top next
            branchStuck.pop(tkn.v.asLayout());                                  // Remove the last key, next pair
            free(right);                                                        // free the right leaf as its content is now all merged into the left leaf forming the new top next of the branch
           }
         };
       }
     };
   }

  static void test_branch_merge_top_leaves()
   {final int BitsPerKey = 4, BitsPerData = 4, MaxKeysPerLeaf = 4, size = 4, N = 5;

    final MjafPut m = new MjafPut(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);
    for (int i = 1; i <= N; i++) m.put(i, i);
    m.execute();

    m.leafSetCurrentSize(3, 2);
    //stop(m.print());
    ok(m.print(), """
      2(2-0)3      |
1,2=2        3,4=3 |
""");
    m.reset();
    m.branchMergeTopLeaves(m.new NN(m.root));
    m.execute();
    ok(m.print(), """
0-2          |
   1,2,3,4=2 |
""");
   }

  void branchMergeLeaves(NN NodeIndex, BI Left)                                 // Merge the two leaves of a branch on either side of the indicated key, next pair if possible. We assume that the children of the parent are leaves without checking that this is true.
   {new Block()
     {void code()
       {final BI size = new BI(branchGetCurrentSize(NodeIndex));                // Size of branch
        returnIfAllZero(size.v);                                                // Cannot merge an empty branch
        shiftRightOneByZero(size.v);                                            // Index of last key, next pair
        returnIfAllZero(size.v);                                                // Cannot merge a branch that only has only one key, next pair - use branchMergeTopLeaves() instead
        returnIfGreaterThanOrEqual(Left.v, size.v);                             // Cannot merge beyond end of branch. We are comapring inunary but lessThanOrEqual works correctly on unary as well as binary
        final BI right = new BI(Left.v.like());                                 // Storage for index of right leaf
        shiftLeftOneByOne(right.v);                                             // Index of right leaf
        returnIfGreaterThan(right.v, size.v);                                   // Cannot merge beyond end of branch

        final KeyNext lkn = branchGet(NodeIndex, Left);                         // Left leaf key, next pair
        final KeyNext rkn = branchGet(NodeIndex, right);                        // Right leaf key, next pair
        final NN        l = lkn.next();                                         // Left leaf index
        final NN        r = rkn.next();                                         // Right leaf index
        new If(leafJoinable(l, r))                                              // Can we join the two leaves?
         {void Then()                                                           // Join the two leaves
           {leafJoin(l, r);                                                     // Merge the right leaf into the left leaf
            copy(rkn.next().v, l.v);                                            // Point the right key, net pair at the merged leaf
            branchPut(NodeIndex, right, rkn);                                   // Make the branch refer to the merged leaf
            branchRemove(NodeIndex, Left);                                      // Remove the key, next pair describing the left leaf because the left lef has had the righ leaf merged into it and then been reused as the right leaf
           }
         };
       }
     };
   }

  static void test_branch_merge_leaves()
   {final int BitsPerKey = 4, BitsPerData = 4, MaxKeysPerLeaf = 4, size = 8, N = 7;

    final MjafPut m = new MjafPut(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);
    for (int i = 1; i <= N; i++) m.put(i, i);
    m.execute();

    //stop(m.print());
    ok(m.print(), """
      6(2-0)      5(4-0.1)7        |
1,2=6       3,4=5          5,6,7=7 |
""");
    m.reset();
    m.branchMergeLeaves(m.new NN(m.root), m.new BI(0));
    m.execute();

    //stop(m.print());
    ok(m.print(), """
          6(4-0)7        |
1,2,3,4=6        5,6,7=7 |
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    //test_split_reduction();
    test_branch_merge_leaves();
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
