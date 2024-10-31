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

//D1 Find last not full

  void findLastNotFull(Key Key, Layout.Bit Found, NN branchIndex)               // Find the last not full branch in the search path of a specified tree setting found to true if such a branch is found else false assuming that the tree is not a single leaf
   {final NN nodeIndex  = new NN();                                             // Node index variable
    final NN childIndex = new NN("child");                                      // Next child down

    new IfElse(rootIsFull())                                                    // Check the root
     {void Then()                                                               // The root is full
       {zero(Found);                                                            // So far we cannot cannot find such a branch
       }
      void Else()                                                               // The root is a possibility
       {ones(Found);                                                            // Flag as possible
        copy(branchIndex.v, root);                                              // Save index of possibility
       }
     };

    new Repeat()
     {void code()
       {branchFindFirstGreaterOrEqual(nodeIndex, Key, childIndex);              // Step down to child
        returnIfOne(isLeaf(childIndex));                                        // Exit when we reach a leaf
        new Unless(branchIsFull(childIndex))                                    // Is the child full?
         {void Then()                                                           // Theis branch is  better possibility
           {ones(Found);                                                        // Flag as possible
            copy(branchIndex.v, childIndex.v);                                  // Save index of possibility
           }
         };
        copy(nodeIndex.v, childIndex.v);
       }
     };
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

  static void test_find_last_not_full()                                         // Find last noit f ull node in the serach path for a key
   {final int BitsPerKey = 8, BitsPerData = 8, MaxKeysPerLeaf = 4, size = 80,
      N = 86;

    final MjafPut m = new MjafPut(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);
    for (int i = 1; i <= N; i++) m.put(m.new Key(i), m.new Data(2*i));
    final Key k = m.new Key(84);

    final Layout.Bit found = Layout.createBit("Found");
    final NN branchIndex   = m.new NN("branchIndex");
    m.findLastNotFull(k, found, branchIndex);                                   // Find the last not full branch in the search path of a specified tree setting found to true if such a branch is found else false assuming that the tree is not a single leaf
    m.maxSteps = 99999;
    m.execute();
    //stop(m.print(), k, found, branchIndex);
    found.ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  1   Found
""");
    branchIndex.v.ok("""
T   At  Wide  Index       Value   Field name
V    0     7                 66   branchIndex
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_split_reduction();
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    //test_split_reduction();
    test_find_last_not_full();
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
