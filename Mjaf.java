//------------------------------------------------------------------------------
// BTree in bit machine assembler.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Design, simulate and layout  a binary tree on a silicon chip.
// Use derived classes to specialize Layouts used as parameters to supply typing
// Assert Branch or Leaf for all parameters indexing a branch or a leaf
// Use Stuck.Index everywhere possible
// Use put(int, int) where possible
// Remove .v
// add index 0.0, 0.1 etc to branch descriptions in print()
import java.util.*;

class Mjaf extends BitMachine                                                   // BTree algorithm but with data stored only in the leaves to facilitate deletion without complicating search or insertion. The branches (interior nodes) have an odd number of keys to make the size of a branch as close to that of a leaf as possible to simplify memory management.
 {final int bitsPerKey;                                                         // Number of bits in key
  final int bitsPerData;                                                        // Number of bits in data
  final int bitsPerNext;                                                        // Number of bits in next level
  final int maxKeysPerLeaf;                                                     // The maximum number of keys per leaf.  This should be an even number greater than three. The maximum number of keys per branch is one less. The normal BTree algorithm requires an odd number greater than two for both leaves and branches.  The difference arises because we only store data in leaves not in leaves and branches as does the classic BTree algorithm.
  final int maxKeysPerBranch;                                                   // The maximum number of keys per branch.
  final int maxNodes;                                                           // The maximum number of nodes in the tree
  final int leafSplitPoint;                                                     // The point at which to split a leaf
  final int branchSplitPoint;                                                   // The point at which to split a branch
  final int maxPrintLevels = 10;                                                // Maximum number of levels to print in a tree
  final Layout.Bit       hasNode;                                               // Tree has at least one node. Or perhaps use the fact that the nodes free stuck will not be full if there is  anode in the tree
  final Layout.Variable  nodesCreated;                                          // Number of nodes created
  final Layout.Variable  keyDataStored;                                         // Current number of key/data pairs currently stored in tree
  final Layout.Variable  root;                                                  // Root
  final Layout.Variable  nodeFree;                                              // Index of a free node as a positive binary integer
  final Stuck            nodesFree;                                             // Free nodes
  final Stuck            branchStuck;                                           // Branch key, next pairs stuck
  final Stuck            leaf;                                                  // Leaf key, data pairs stuck
  final Layout.Variable  leafKey;                                               // Key in a leaf
  final Layout.Variable  leafData;                                              // Data in a leaf
  final Layout.Variable  branchKey;                                             // Key in a branch
  final Layout.Variable  branchNext;                                            // Next from a branch
  final Layout.Variable  topNext;                                               // Next node if search key is greater than all keys in this node
  final Layout.Structure branch;                                                // Branch node of the tree
  final Layout.Structure leafKeyData;                                           // An entry in a leaf node
  final Layout.Structure branchKeyNext;                                         // An entry in a branch node
  final Layout.Union     branchOrLeaf;                                          // Branch or leaf of the tree
  final Layout.Bit       isBranch;                                              // The node is a branch if true
  final Layout.Bit       isLeaf;                                                // The node is a leaf if true
  final Layout.Structure node;                                                  // Node of the tree
  final Layout.Array     nodes;                                                 // Array of nodes comprising tree
  final Layout.Structure tree;                                                  // Structure of tree
  final Layout           layoutLeafKeyData;                                     // Layout of a leaf key data pair
  final Layout           layoutBranchKeyNext;                                   // Layout of a branch key next pair
//final Layout           layout;                                                // Layout of tree

  final Layout.Variable  leafSplitIdx;                                          // Index of leaf splitting key
  final Layout.Variable  branchSplitIdx;                                        // Index of branch splitting key
  final Layout.Structure workStructure;                                         // Work structure
  final Layout           work;                                                  // Memory work area for temporary, intermediate results

  final static String nbol = "nodes.node.branchOrLeaf.";                        // Search layout

//D1 Construction                                                               // Create a BTree from nodes which can be branches or leaves.  The data associated with the BTree is stored only in the leaves opposite the keys

  Mjaf(int BitsPerKey, int BitsPerData, int MaxKeysPerLeaf, int size)           // Define a BTree with the specified dimensions
   {super("Mjaf");
    final int N      = MaxKeysPerLeaf;                                          // Assign a shorter name
    bitsPerKey       = BitsPerKey;
    bitsPerNext      = logTwo(size);                                            // Wide enough to index the specified size using binary arithmetic
    bitsPerData      = BitsPerData;

    if (N % 2 == 1) stop("# keys per leaf must be even not odd:", N);
    if (N     <= 3) stop("# keys per leaf must be greater than three, not:", N);
    maxKeysPerLeaf   = N;                                                       // Some even number
    maxKeysPerBranch = N-1;                                                     // Ideally should be some number that makes the leaf nodes and the branch nodes the same size
    maxNodes         = size;
    leafSplitPoint   = (N-1) >> 1;                                              // Point at which to split a leaf
    branchSplitPoint = (N-1) >> 1;                                              // Point at which to split a branch

    final Layout W   = work = new Layout();                                     // Layout of working memory
    leafSplitIdx     = W.variable ("leafSplitIdx",   N);                        // Index of leaf splitting key
    branchSplitIdx   = W.variable ("branchSplitIdx", N);                        // Index of branch splitting key
    workStructure    = W.structure("workStructure",                             // An entry in a leaf node
      leafSplitIdx, branchSplitIdx);

    W.layout(workStructure);                                                    // Layout of a leaf key data pair
    leafSplitIdx.fromUnary(leafSplitPoint);                                     // Index of splitting key in leaf in unary
    branchSplitIdx.fromUnary(branchSplitPoint);                                 // Index of splitting key in branch in unary
    Layout.constants(leafSplitIdx, branchSplitIdx);                             // Mark as constants

    final Layout L   = layoutLeafKeyData = new Layout();                        // Layout of a leaf key data pair
    leafKey          = L.variable ("leafKey",  bitsPerKey);                     // Key in a leaf
    leafData         = L.variable ("leafData", bitsPerData);                    // Data in a leaf
    leafKeyData      = L.structure("leafKeyData", leafKey, leafData);           // An entry in a leaf node
    layoutLeafKeyData.layout(leafKeyData);                                      // Layout of a leaf key data pair

    leaf             =   new Stuck("leaf",                                      // Leaf key, data pairs stuck
      maxKeysPerLeaf,layoutLeafKeyData);

    final Layout B   = layoutBranchKeyNext = new Layout();                      // An entry in a branch node
    branchKey        = B.variable ("branchKey",  bitsPerKey);                   // Key in a branch
    branchNext       = B.variable ("branchNext", bitsPerNext);                  // Next from a branch
    branchKeyNext    = B.structure("branchKeyNext", branchKey, branchNext);     // An entry in a branch node
    layoutBranchKeyNext.layout(branchKeyNext);                                  // Layout of a branch key next pair

    branchStuck    = new Stuck("branchStuck",                                   // Branch key, next pairs stuck
      maxKeysPerBranch, layoutBranchKeyNext);

    final Layout T = layout = new Layout();                                     // Tree level layout
    nodesCreated   = T.variable ("nodesCreated",   bitsPerNext);                // Number of nodes created
    keyDataStored  = T.variable ("keyDataStored",  bitsPerNext);                // Field to track number of keys stored in twos complement form hence an extra bit for the sign
    root           = T.variable ("root",           bitsPerNext);                // Root
    nodeFree       = T.variable ("nodeFree",       logTwo(size));               // Index of a free node as a positive binary integer
    nodesFree      = new Stuck  ("nodesFree", size, nodeFree.duplicate());      // Free nodes stuck

    topNext        = T.variable ("topNext",        bitsPerNext);                // Next node if search key is greater than all keys in this node
    branch         = T.structure("branch",         branchStuck, topNext);       // Branch of the tree

    branchOrLeaf   = T.union    ("branchOrLeaf",   branch, leaf);               // Branch or leaf of the tree
    isBranch       = T.bit      ("isBranch");                                   // The node is a branch if true
    isLeaf         = T.bit      ("isLeaf");                                     // The node is a leaf if true
    node           = T.structure("node",  isLeaf,  isBranch, branchOrLeaf);     // Node of the tree
    nodes          = T.array    ("nodes", node,    size);                       // Array of nodes comprising tree
    hasNode        = T.bit      ("hasNode");                                    // Tree has at least one node in it
    tree           = T.structure("tree",                                        // Tree
                       nodesFree, nodesCreated,
                       keyDataStored, root, hasNode, nodes);
    T.layout(tree);                                                             // Layout

    tree.zero();                                                                // Clear all of memory
    for (int i = 0; i < size; i++)                                              // All nodes are originally free
     {nodesFree.array.setIndex(i);                                              // Index free node
      nodesFree.element.fromInt(i);                                             // Place the free node
     }
    nodesFree.unary.value.ones();                                               // The stuck is initially full of free nodes - need immediate execution so cannot use currentSize() which executes when the bit machine is actually run.
    setRootToLeaf();

    bitMachines(nodesFree, branchStuck, leaf);                                  // Place all the instruction that would otherwise be generated in these machines into this machine instead
   }

  static Mjaf mjaf(int Key, int Data, int MaxKeysPerLeaf, int size)             // Define a BTree with a specified maximum number of keys per leaf.
   {return new Mjaf(Key, Data, MaxKeysPerLeaf, size);
   }

  void size     (Layout.Variable size) {copy(size, keyDataStored);}             // Number of entries in the tree
  void emptyTree(Layout.Bit    result) {copy(result, hasNode); not(result);}    // Test for an empty tree

  void allocate(Layout.Variable index)                                          // Allocate a node from the free node stack
   {nodesFree.pop(index);                                                       // Binary index of next free node
   }

  void free(NN index)                                                           // Free the indexed node
   {nodesFree.push(index.v.copy());                                             // Place node on free nodes stuck
    clear(index);
   }

  void clear(NN index)                                                          // Clear a node
   {setIndex(nodes, index);                                                     // Address node just freed
    zero(node);                                                                 // Clear the node
   }

  void setIndex(Layout.Array array, NN index) {setIndex(array, index.v);}       // Set the index of a layout array

  void setRootToLeaf()                                                          // Set the root node of a new tree to be a leaf
   {nodes.setIndex(0); isLeaf.ones(); isBranch.zero();
   }

  void setRootToBranch()                                                        // Set the root node of a new tree to be a branch
   {nodes.setIndex(0); isBranch.ones(); isLeaf.zero();
   }

//D1 Components                                                                 // The components of leaves and branches used to construct a tree

  class NN                                                                      // A node number
   {final Layout.Variable v;
    NN(Layout.Variable V)
     {if (V.width != bitsPerNext) stop("Wrong sized node number", V);
      v = V;
     }

    NN(String name) {this(Layout.createVariable(name, bitsPerNext));}           // Create a node index with the specified name
    NN() {this("nodeIndex");}                                                   // Create a node index with a default name

    NN(int value)                                                               // Create a next item with the specified value
     {final Layout.Variable next = v = Layout.createVariable("next", bitsPerNext);
      copy(next, value);
     }
    NN duplicate() {return new NN(v.duplicate().asField().toVariable());}       // Duplicate a node index so we can safely modify it
    public String toString() {return v.toString();}                             // Print the wrapped layout variable
   }

  class BI                                                                      // An index within a branch
   {final Layout.Variable v;
    BI(Layout.Variable V)                                                       // Index via a variable
     {if (V.width != maxKeysPerBranch) stop("Wrong sized branch index", V);
      v = V;
     }
    BI(int V)                                                                   // Index from a constant
     {this("branchIndex");
      v.fromInt(V);
     }
    BI duplicate() {return new BI(v.duplicate().asField().toVariable());}       // Duplicate a branch index so we can safely modify it
    BI(String name) {this(Layout.createVariable(name, maxKeysPerBranch));}      // Create a branch index with the specified name
    BI() {this("branchIndex");}                                                 // Create a branch index with a default name
    public String toString() {return v.toString();}                             // Print the wrapped layout variable
   }

  class LI                                                                      // An index within a leaf
   {final Layout.Variable v;
    LI(Layout.Variable V)
     {if (V.width != maxKeysPerLeaf) stop("Wrong sized leaf index", V);
      v = V;
     }
    LI duplicate() {return new LI(v.duplicate().asField().toVariable());}       // Duplicate a leaf index so we can safely modify it
    LI(String name) {this(Layout.createVariable(name, maxKeysPerLeaf));}        // Create a leaf index with the specified name
    LI() {this("leafIndex");}                                                   // Create a leaf index with a default name
    public String toString() {return v.toString();}                             // Print the wrapped layout variable
  }

  class Key                                                                     // A as key represented by a layout variable
   {final Layout.Variable v;
    Key(Layout.Variable V)
     {if (V.width != bitsPerKey) stop("Wrong sized key", V);
      v = V;
     }
    Key(int value)                                                              // Create a key with the specified value
     {v = Layout.createVariable("key", bitsPerKey);
      copy(v, value);
     }
    public String toString() {return v.toString();}                             // Print the wrapped layout variable
   }

  class Data                                                                    // A datum as represented by a layout variable
   {final Layout.Variable v;
    Data(Layout.Variable V)
     {if (V.width != bitsPerData) stop("Wrong sized data", V);
      v = V;
     }
    Data()
     {v = Layout.createVariable("data", bitsPerData);
     }
    Data(int value)                                                             // Create a data item with the specified value
     {v = Layout.createVariable("data", bitsPerData);
      copy(v, value);
     }
    void ok(String expected) {Test.ok(v.toString(), expected);}                 // Check data is as expected
    public String toString() {return v.toString();}                             // Print the wrapped layout variable
   }

  class KeyData                                                                 // A key, data pair
   {final LayoutAble v;
    KeyData(LayoutAble V)
     {if (V.asField().width != bitsPerKey+bitsPerData)
       {stop("Wrong sized key, data", V);
       }
      v = V;
     }

    KeyData(Key Key, Data Data)                                                 // Create a key, data pair for insertion into a leaf
     {final LayoutAble kd = v = leafKeyData.duplicate();                        // Key, data pair
      if (Key  != null) copy(kd.asLayout().get("leafKey"),  Key.v);             // Copy key if requested
      if (Data != null) copy(kd.asLayout().get("leafData"), Data.v);            // Copy data if requested
     }

    KeyData()   {this(null, null);}                                             // Create a key, data pair for insertion into a leaf without loading the fields
    KeyData(int key, int data) {this(new Key(key), new Data(data));}            // Create a key, data pair for insertion into a leaf providing integer arguments

    Key  key () {return new Key(v.asLayout().get("leafKey").toVariable());}     // Get the key from a key, data pair
    Data data() {return new Data(v.asLayout().get("leafData").toVariable());}   // Get the data from a key, data pair
    public String toString() {return v.toString();}                             // Print the wrapped layout variable
   }

  class KeyNext                                                                 // A key, next pair
   {final LayoutAble v;
    KeyNext(LayoutAble V)
     {if (V.asField().width != bitsPerKey+bitsPerNext)
       {stop("Wrong sized key, next", V);
       }
      v = V;
     }

    KeyNext(Key Key, NN Next)                                                   // Create a key, next  pair for insertion into a branch
     {final LayoutAble kn = v = branchKeyNext.duplicate();                      // Key, next pair
      if (Key  != null) copy(kn.asLayout().get("branchKey"),  Key.v);           // Copy key if requested
      if (Next != null) copy(kn.asLayout().get("branchNext"), Next.v);          // Copy data if requested
     }

    KeyNext(int Key, int Next)                                                  // Create a key, next  pair for insertion into a branch
     {final LayoutAble kn = v = branchKeyNext.duplicate();                      // Key, next pair
      copy(kn.asLayout().get("branchKey"),  Key);
      copy(kn.asLayout().get("branchNext"), Next);
     }

    KeyNext() {this(null, null);}                                               // Create a key, next  pair for insertion into a branch without loading the fields
    Key key() {return new Key(v.asLayout().get("branchKey").toVariable());}     // Get the key from a key, next pair
    NN next() {return new NN(v.asLayout().get("branchNext").toVariable());}     // Get the next node from a key, next pair
    public String toString() {return v.toString();}                             // Print the wrapped layout variable
   }

//D1 Leaf                                                                       // Process a leaf

  void isLeaf(NN index, Layout.Bit result)                                      // Check whether the specified node is a leaf
   {setIndex(nodes, index.v);                                                   // Index node
    copy(result, isLeaf);                                                       // Get leaf flag
   }

  Layout.Bit isLeaf(NN index)                                                   // Check whether the specified node is a leaf
   {final Layout.Bit result = Layout.createBit("isLeaf");                       // Result bit
    isLeaf(index, result);                                                      // Check whether the specified node is a leaf
    return result;
   }

  boolean isLeaf(int iLeaf)                                                     // Decide if a node is leaf so we can print it
   {Layout.Array nodes = layout.get("nodes").toArray();
    Layout.Bit   index = layout.get("nodes.node.isLeaf").toBit();
    nodes.setIndex(iLeaf);                                                      // Select the leaf to process
    return index.get(0);
   }

  void rootIsLeaf(Layout.Bit result)                                            // Check whether the root is a leaf
   {setIndex(nodes, root);                                                      // Index node
    copy(result, isLeaf);                                                       // Get leaf flag
   }

  Layout.Bit rootIsLeaf()                                                       // Check whether the root is a leaf
   {final Layout.Bit result = Layout.createBit("rootIsLeaf");                   // Result bit
    rootIsLeaf(result);                                                         // Check whether the root is a leaf
    return result;
   }

  void leafMark(NN iLeaf)                                                       // Mark a node as a leaf not as a branch
   {setIndex(nodes, iLeaf);                                                     // Select the leaf to process
    ones(isLeaf);                                                               // Flag as a leaf
    zero(isBranch);                                                             // Flag as not a branch
   }

  void leafMake(NN iLeaf)                                                       // Make a new leaf by taking a node off the free nodes stack and converting it into a leaf
   {allocate(iLeaf.v);                                                          // Allocate a new node
    setIndex(nodes, iLeaf);                                                     // Select the leaf to process
    leaf.unary.zero();                                                          // Clear leaf
    leafMark(iLeaf);
   }

  NN leafMake()                                                                 // Make a new leaf and return its index
   {final NN i = new NN("leaf");
    leafMake(i);
    return i;
   }

  int leafGetKey(int iLeaf, int index)                                          // Get leaf key so we can print it
   {Layout.Array nodes = layout.get("nodes").toArray();
    Layout.Array leaf  = layout.get(nbol+"leaf.array").toArray();
    nodes.setIndex(iLeaf);                                                      // Select the leaf to process
    leaf.setIndex(index);                                                       // Select the key, data pair to process
    return leaf.get("leafKeyData.leafKey").asInt();
   }

  void leafGet(NN iLeaf, LI index, KeyData kd)                                  // Get the specified key, data pair in the specified leaf
   {setIndex(nodes, iLeaf);                                                     // Select the leaf to process
    leaf.elementAt(kd.v, index.v);                                              // Insert the key, data pair at the specified index in the specified leaf
   }

  void leafPut(NN iLeaf, LI index, KeyData kd)                                  // Put the specified key, data pair from the specified leaf
   {setIndex(nodes, iLeaf);                                                     // Select the leaf to process
    leaf.setElementAt(kd.v, index.v);                                           // Insert the key, data pair at the specified index in the specified leaf
   }

  void leafInsert(NN iLeaf, LI index, KeyData kd)                               // Place the specified key, data pair at the specified location in the specified leaf
   {setIndex(nodes, iLeaf);                                                     // Select the leaf to process
    leaf.insertElementAt(kd.v, index.v);                                        // Insert the key, data pair at the specified index in the specified leaf
   }

  void leafRemove(NN iLeaf, LI index)                                           // Remove the key, data pair at the specified location in the specified leaf
   {setIndex(nodes, iLeaf);                                                     // Select the leaf to process
    leaf.removeElementAt(index.v);                                              // Remove the key, data pair at the specified index in the specified leaf
   }

  void leafIsEmpty(NN index, Layout.Bit result)                                 // Leaf is empty
   {setIndex(nodes, index);
    leaf.unary.canNotDec(result);
   }

  void leafIsFull(NN index, Layout.Bit result)                                  // Leaf is full
   {setIndex(nodes, index);
    leaf.unary.canNotInc(result);
   }

  Layout.Bit leafRootIsFull()                                                   // Return whether the root, known to be a leaf, is full
   {final Layout.Bit result = Layout.createBit("leafRootIsFull");
    leafIsFull(new NN(root), result);
    return result;
   }

  Layout.Bit leafIsFull(NN index)                                               // Leaf is full
   {Layout.Bit result = Layout.createBit("leafIsFull");
    leafIsFull(index, result);
    return result;
   }

  void leafIsNotFull(NN index, Layout.Bit result)                               // Leaf is not full
   {setIndex(nodes, index);
    leaf.unary.canInc(result);
   }

  Layout.Bit leafIsNotFull(NN index)                                            // Leaf is not full
   {Layout.Bit result = Layout.createBit("leafIsNotFull");
    leafIsNotFull(index, result);
    return result;
   }

  void leafSetCurrentSize(int iLeaf, int size)                                  // Set current size of leaf
   {nodes.setIndex(iLeaf);
    leaf.unary.value.fromUnary(size);
   }

  void leafSplitKey(NN index, KeyData out)                                      // Splitting key in a leaf
   {setIndex(nodes, index);
    leaf.elementAt(out.v, leafSplitIdx);
   }

  KeyData leafSplitKey(NN index)                                                // Return splitting key in a leaf
   {final KeyData out = new KeyData();
    leafSplitKey(index, out);
    return out;
   }

  void leafPush(NN index, KeyData kd)                                           // Push a key, data pair onto the indicated leaf
   {setIndex(nodes, index);
    leaf.push(kd.v);
   }

  void leafShift(NN index, KeyData kd)                                          // Shift a key, data pair from the indicated leaf
   {setIndex(nodes, index);
    leaf.shift(kd.v);
   }

  void leafFindIndexOf (NN index, Key key, Layout.Bit found, LI result)         // Find index of the specified key, data pair in the specified leaf
   {setIndex(nodes, index);
    leaf.indexOf(key.v, bitsPerKey, found, result.v);
   }

  void leafSplitRoot(NN F1, NN F2)                                              // Split the root when it is a leaf
   {final KeyData lkd = new KeyData(leafKeyData.duplicate());                   // Transferring key, data pairs from the source node to the target node
    final KeyData rkd = new KeyData(leafKeyData.duplicate());                   // Root key, data pair
    final KeyNext rkn = new KeyNext(branchKeyNext.duplicate());                 // Root key, next pair
    final NN      ort = new NN(topNext.like());                                 // Old root top
    final NN        r = new NN(root);                                           // Root

    leafMake(F2);                                                               // New right leaf
    leafMake(F1);                                                               // New left leaf
    for (int i = 0; i <= leafSplitPoint; i++)                                   // Transfer keys, data pairs to new left child
     {leafShift(r,  lkd);                                                       // Current key, data pair from root
      leafPush (F1, lkd);                                                       // Save key, data pair in new left child
     }
    for (int i = 0; i <= leafSplitPoint; i++)                                   // Transfer keys, data pairs to new right child
     {leafShift(r,  rkd);                                                       // Current key, data pair from root
      leafPush (F2, rkd);                                                       // Save key, data pair to new right child
     }

    copy(rkn.key().v, lkd.key().v);                                             // Save key
    copy(rkn.next().v, F1.v);                                                   // First root key refers to left child
    setIndex(nodes, r);                                                         // Index the root
    zero(node);                                                                 // Clear the root
    branchSetTopNext(r, F2);                                                    // Top of root refers to right child
    branchPush(r, rkn);                                                         // Save root key, next pair
    branchMark(r);                                                              // The root is now a branch
   }

  void leafSplitRoot()                                                          // Split the root when it is a leaf
   {final NN F1 = new NN("left");                                               // New left leaf
    final NN F2 = new NN("right");                                              // New right root
    leafSplitRoot(F1, F2);
   }

  void leafSplit(NN target, NN source)                                          // Source leaf, target leaf. After the leaf has been split the upper half will appear in the source and the loweer half in the target
   {final KeyData kd = new KeyData(leafKeyData.duplicate());                    // Work area for transferring key data pairs from the source code to the target node

    leafMake(target);
    for (int i = 0; i <= leafSplitPoint; i++)                                   // Transfer keys, data pairs
     {leafShift(source, kd);                                                    // Current key, data pair
      leafPush (target, kd);                                                    // Save key, data pair
     }
   }

  NN leafSplit(NN source)                                                       // Split the source leaf. After the leaf has been split the upper half will appear in the source and the loweer half in the target
   {final NN target = new NN("target");
    leafSplit(target, source);
    return target;
   }

  NN leafFission(NN parent, NN source)                                          // Split a leaf that is one of children of its parent branch
   {KeyData kd = leafSplitKey(source);                                          // Leaf splitting key
    NN  target = leafSplit(source);                                             // Split leaf, the target leaf is on the left

    Key      k = kd.key();                                                      // Split key
    KeyNext kn = new KeyNext(k, target);                                        // Key, next pair for insertion into branch
    NN       t = branchGetTopNext(parent);                                      // Top next of parent

    new IfElse(equals(t.v, source.v))                                           // Source is top next of parent
     {void Then()
       {branchPush(parent, kn);                                                 // Append split out leaf as last key, next pair.
       }
      void Else()
       {BI i = branchFindFirstGreaterOrEqualIndex(parent, k);                   // Position to insert into branch
        branchInsert(parent, i, kn);
       }
     };
    return target;                                                              // The split out leaf
   }

  void leafJoinable(NN target, NN source, Layout.Bit result)                    // Check that we can join two leaves
   {setIndex(nodes, target); Layout.Variable t = leaf.currentSize();
    setIndex(nodes, source); Layout.Variable s = leaf.currentSize();
    unaryFilled(s, t, result);
   }

  Layout.Bit leafJoinable(NN target, NN source)                                 // Check that we can join the source leaf into the target leaf
   {final Layout.Bit result = Layout.createBit("joinAbleLeaves");               // Whether the leaves can be joined
    leafJoinable(target, source, result);
    return result;
   }

  void leafJoin(NN target, NN source)                                           // Join the specified source leaf onto the end of the target leaf
   {new Repeat()
     {void code()
       {setIndex(nodes, source);                                                // Address source
        returnIfAllZero(leaf.currentSize());                                    // Exit then the source leaf has been emptied
        final Layout kd = leafKeyData.duplicate();                              // Key data pair buffer
        leaf.shift(kd);                                                         // Remove from source
        setIndex(nodes, target);                                                // Address target
        leaf.push(kd);                                                          // Add to target
       }
     };
    free(source);                                                               // Free the leaf that was joined
   }

  void leafInsertPair(NN NodeIndex, Key Key, Data Data)                         // Insert a key and the corresponding data into a leaf at the correct position
   {final LI      leafIndex = new LI();
    final Layout.Bit insert = Layout.createBit("insert");                       // Insertion will be needed to palce the new key, data pair
    final KeyData        kd = new KeyData(Key, Data);                           // Key, data pair to insert

    leafFirstGreaterThanOrEqual(NodeIndex, Key, leafIndex, insert);             // Find key to insert before
    new IfElse (insert)
     {void Then()
       {leafInsert(NodeIndex, leafIndex, kd);                                   // Insert key, data pair into leaf
       }
      void Else()
       {leafPush(NodeIndex, kd);                                                // Append key, data pair to leaf
       }
     };
   }

  void leafFirstGreaterThanOrEqual(NN NodeIndex,                                // Find the index of the first key in a leaf that is greater than or equal to the specified key or set found to be false if such a key cannot be found becuase all the keys in the leaf are less than the search key
    Key Key, LI Leaf, Layout.Bit Result)
   {new Block()
     {void code()
       {final Block outer = this;
        new Block()
         {void code()
           {final Block inner = this;
            final KeyData  kd = new KeyData(leafKeyData.duplicate());           // Work area for transferring key data pairs from the source code to the target node
            setIndex(nodes, NodeIndex);                                         // Index the node to search
            zero(Leaf.v);                                                       // Start with the first key, next pair in the leaf
            ones(Result);                                                       // Assume  success
            for (int i = 0; i < maxKeysPerLeaf; i++)                            // Check each key
             {inner.returnIfEqual(Leaf.v, leaf.currentSize());                  // Passed all the valid keys - serach key is bigger than all keys
              leafGet(NodeIndex, Leaf, kd);                                     // Retrieve key/data pair from leaf
              outer.returnIfLessThan(Key.v, kd.v.asLayout().get("leafKey"));    // Found a key greater than the serach key
              shiftLeftOneByOne(Leaf.v);                                        // Next key
             }
           }
         }; // Inner block in which we search for the key
        zero(Result);                                                           // Show that the search key is greater than all the keys in the leaf
       }
     }; // Outer block to exit when we have found the key
   }

  int leafSize(int nodeIndex) {nodes.setIndex(nodeIndex); return  leaf.size();} // Number of key, data pairs in a leaf

  void leafPrint(Stack<StringBuilder>S, int level, int nodeIndex)               // Print leaf horizontally
   {padStrings(S, level);
    final StringBuilder s = new StringBuilder();                                // String builder
    final int K = leafSize(nodeIndex);
    for  (int i = 0; i < K; i++) s.append(""+leafGetKey(nodeIndex, i)+",");
    if (s.length() > 0) s.setLength(s.length()-1);                              // Remove trailing comma if present
    s.append("="+nodeIndex+" ");
    S.elementAt(level).append(s.toString());
    padStrings(S, level);
   }

//D1 Branch                                                                     // Process a branch

  void branchMark(NN iBranch)                                                   // Mark a node as a branch not a branch
   {setIndex(nodes, iBranch.v);                                                 // Select the branch to process
    zero(isLeaf);                                                               // Flag as not a branch
    ones(isBranch);                                                             // Flag as a branch
   }

  void branchMake(NN iBranch)                                                   // Make a new branch by taking a node off the free nodes stack and converting it into a branch
   {allocate(iBranch.v);                                                        // Allocate a new node
    setIndex(nodes, iBranch.v);                                                 // Select the branch to process
    branchStuck.unary.zero();                                                   // Clear branch
    branchMark(iBranch);
   }

  void isBranch(NN index, Layout.Bit result)                                    // Check whether the specified node is a branch
   {setIndex(nodes, index.v);                                                   // Index node
    copy(result, isBranch);                                                     // Get branch flag
   }

  Layout.Bit isBranch(NN index)                                                 // Return whether the specified node is a branch
   {final Layout.Bit result = Layout.createBit("isBranch");                     // Result bit
    isBranch(index, result);                                                    // Check whether the specified node is a branch
    return result;
   }

  void rootIsBranch(Layout.Bit result)                                          // Check whether the root is a branch
   {setIndex(nodes, root);                                                      // Index node
    copy(result, isBranch);                                                     // Get branch flag
   }

  Layout.Bit rootIsBranch()                                                     // Check whether the root is a branch
   {final Layout.Bit result = Layout.createBit("rootIsBranch");                 // Result bit
    rootIsBranch(result);                                                       // Check whether the root is a branch
    return result;
   }

  NN branchMake()                                                               // Make a new branch and return its index
   {final NN i = new NN("branch");
    branchMake(i);
    return i;
   }

  int branchGetKey(int iBranch, int index)                                      // Get branch key so we can print it
   {Layout.Array nodes  = layout.get("nodes").toArray();
    Layout.Array branch = layout.get(nbol+"branch.branchStuck.array").toArray();
    nodes.setIndex(iBranch);                                                    // Select the leaf to process
    branch.setIndex(index);                                                     // Select the key, data pair to process
    return branch.get("branchKeyNext.branchKey").asInt();
   }

  int branchGetNext(int iBranch, int index)                                     // Get branch next so we can traverse the tree during printing
   {Layout.Array nodes  = layout.get("nodes").toArray();
    Layout.Array branch = layout.get(nbol+"branch.branchStuck.array").toArray();
    nodes.setIndex(iBranch);                                                    // Select the leaf to process
    branch.setIndex(index);                                                     // Select the key, data pair to process
    return branch.get("branchKeyNext.branchNext").asInt();
   }

  int branchGetTopNext(int iBranch)                                             // Get branch top next so we can traverse the tree during printing
   {Layout.Array nodes  = layout.get("nodes").toArray();
    nodes.setIndex(iBranch);                                                    // Select the leaf to process
    return layout.get(nbol+"branch.topNext").asInt();
   }

  void branchSetTopNext(int parent, int child )                                 // Set the branch top next of the specified branch to the specified child branch or leaf
   {nodes.setIndex(parent);                                                     // Select the leaf to process
    layout.get(nbol+"branch.topNext").fromInt(child);
   }

  int branchGetSize(int iBranch)                                                // Get branch size as an integer
   {Layout.Array nodes  = layout.get("nodes").toArray();
    Layout.Array branch = layout.get(nbol+"branch.branchStuck.array").toArray();
    nodes.setIndex(iBranch);                                                    // Select the branch to process
    return branch.get("branchKeyNext.branchNext").asInt();
   }

  Layout.Variable branchGetCurrentSize(NN iBranch)                              // Get branch size as a variable
   {setIndex(nodes, iBranch.v);                                                 // Select the branch to process
    return branchStuck.currentSize();                                           // Number of elements currently in this branch as a variable
   }

  void branchSetCurrentSize(int iBranch, int size)                              // Set branch size
   {nodes.setIndex(iBranch);                                                         // Select the branch to process
    branchStuck.unary.value.fromUnary(size);
   }

  void branchGet(NN iBranch, BI index, KeyNext kn)                              // Get the specified key, next pair in the specified branch
   {setIndex(nodes, iBranch.v);                                                 // Select the branch to process
    branchStuck.elementAt(kn.v, index.v);                                       // Get the key, next pair at the specified index in the specified branch
   }

  KeyNext branchGet(NN iBranch, BI index)                                       // Return the specified key, next pair in the specified branch
   {final KeyNext kn = new KeyNext();
    branchGet(iBranch, index, kn);
    return kn;
   }

  void branchGetFirst(NN iBranch, KeyNext kn)                                   // Return the first key, next pair in the specified branch
   {setIndex(nodes, iBranch.v);                                                 // Select the branch to process
    branchStuck.firstElement(kn.v);                                             // Get the first key, next pair in the specified branch
   }

  KeyNext branchGetFirst(NN iBranch)                                            // Get the first key, next pair in the specified branch
   {final KeyNext kn = new KeyNext();
    branchGetFirst(iBranch, kn);
    return kn;
   }

  void branchGetLast(NN iBranch, KeyNext kn)                                    // Get the last key, next pair in the specified branch
   {setIndex(nodes, iBranch.v);                                                 // Select the branch to process
    branchStuck.lastElement(kn.v);                                              // Get the first key, next pair in the specified branch
   }

  KeyNext branchGetLast(NN iBranch)                                             // Return the last key, next pair in the specified branch
   {final KeyNext kn = new KeyNext();
    branchGetLast(iBranch, kn);
    return kn;
   }

  void branchPut(NN iBranch, BI index, KeyNext kn)                              // Put the specified key, next pair from the specified branch
   {setIndex(nodes, iBranch.v);                                                 // Select the branch to process
    branchStuck.setElementAt(kn.v, index.v);                                    // Insert the key, next pair at the specified index in the specified branch
   }

  void branchInsert                                                             // Place the specified key, next pair at the specified location in the specified branch
   (NN iBranch, BI index, KeyNext kn)
   {setIndex(nodes, iBranch.v);                                                 // Select the branch to process
    branchStuck.insertElementAt(kn.v, index.v);                                 // Insert the key, next pair at the specified index in the specified branch
   }

  void branchRemove(NN iBranch, BI index)                                       // Remove the key, next pair at the specified location in the specified branch
   {setIndex(nodes, iBranch.v);                                                 // Select the branch to process
    branchStuck.removeElementAt(index.v);                                       // Remove the key, next pair at the specified index in the specified branch
   }

  void branchIsEmpty(NN index, Layout.Bit result)                               // Branch is empty
   {setIndex(nodes, index.v);
    branchStuck.unary.canNotDec(result);
   }

  Layout.Bit branchIsEmpty(NN index)                                            // Branch is empty
   {final Layout.Bit result = Layout.createBit("empty");
    branchIsEmpty(index, result);
    return result;
   }

  void branchIsFull(NN index, Layout.Bit result)                                // Branch is full
   {setIndex(nodes, index.v);
    branchStuck.unary.canNotInc(result);
   }

  Layout.Bit branchIsFull(NN index)                                             // Branch is full
   {final Layout.Bit result = Layout.createBit("branchFull");
    branchIsFull(index, result);
    return result;
   }

  Layout.Bit branchRootIsFull()                                                 // Return whether the root, known to be a branch, is full
   {final Layout.Bit result = Layout.createBit("branchRootIsFull");
    branchIsFull(new NN(root), result);
    return result;
   }

  Layout.Bit branchMightContainKey(NN branchIndex, Key Key)                     // Whether the specified key is in the range of keys within the specified branc, i.e. it could be in the branch withoit teh nmeessity of actually confirming that it is.
   {final KeyNext   kn = new KeyNext();                                         // Key, next from branch
    final Layout.Bit r = Layout.createBit("result");                            // Result of test

    zero(r);                                                                    // Assume that the test will fail
    new Block()
     {void code()
       {branchGetFirst(branchIndex, kn);                                        // First key - we are asuming there is one
        returnIfLessThan   (Key.v, kn.key().v);                                 // Less than the first key so return failure
        branchGetLast(branchIndex, kn);                                         // Last key - we are asuming there is one
        returnIfGreaterThan(Key.v, kn.key().v);                                 // Greater than the first key so return failure
        ones(r);                                                                // Passed
       }
     };
    return r;                                                                   // Return result
   }

  void branchSplitKey(NN index, KeyNext out)                                    // Splitting key in a branch
   {setIndex(nodes, index.v);
    branchStuck.elementAt(out.v, branchSplitIdx);
   }

  KeyNext branchSplitKey(NN index)                                              // Return splitting key in a branch
   {final KeyNext out = new KeyNext();
    branchSplitKey(index, out);
    return out;
   }

  void branchPush(NN index, KeyNext kn)                                         // Push a key, next pair onto the indicated branch
   {setIndex(nodes, index.v);
    branchStuck.push(kn.v);
   }

  void branchShift(NN index, KeyNext kn)                                        // Shift a key, next pair from the indicated branch
   {setIndex(nodes, index.v);
    branchStuck.shift(kn.v);
   }

  void branchFindIndexOf(NN index, KeyNext kn, Layout.Bit found, BI result)     // Find index of the specified key, next pair in the specified branch
   {setIndex(nodes, index);
    branchStuck.indexOf(kn.v, bitsPerKey, found, result.v);
   }

  void branchGetTopNext(NN nodeIndex, NN top)                                   // Get the top node for a branch
   {setIndex(nodes, nodeIndex.v);
    copy(top.v, topNext);
   }

  NN branchGetTopNext(NN nodeIndex)                                             // Get the top node for a branch
   {final NN top = new NN("top");
    branchGetTopNext(nodeIndex, top);
    return top;
   }

  void branchSetTopNext(NN nodeIndex, NN newTop)                                // Set the top node for a branch
   {setIndex(nodes, nodeIndex);
    copy(topNext, newTop.v);
   }

  void branchSplit(NN target, NN source)                                        // Source branch, target branch. After the branch has been split the upper half will appear in the source and the lower half in the target
   {final KeyNext kn = new KeyNext(branchKeyNext.duplicate());                  // Work area for transferring key data pairs from the source code to the target node

    branchMake(target);
    for (int i = 0; i < branchSplitPoint; i++)                                  // Transfer keys, data pairs
     {branchShift(source, kn);                                                  // Current key, next pair
      branchPush (target, kn);                                                  // Save key, next pair
     }
    branchShift(source, kn);                                                    // Current key, next pair
    branchSetTopNext(target, kn.next());                                        // Copy in the new top node
   }

  NN branchSplit(NN source)                                                     // Source branch, target branch. After the branch has been split the upper half will appear in the source and the lower half in the target
   {final NN target = new NN();                                                 // Index of split out node
    branchSplit(target, source);                                                // Work area for transferring key data pairs from the source code to the target node
    return target;
   }

  void branchSplitRoot(NN F1, NN F2)                                            // Split the root when it is a branch
   {final KeyNext  kn = new KeyNext(branchKeyNext.duplicate());                 // Transferring key, next pairs from the source node to the target node
    final KeyNext rkn = new KeyNext(branchKeyNext.duplicate());                 // Root key,next pair
    final NN      ort = new NN(topNext.like());                                 // Old root top

    branchMake(F2);                                                             // New right branch
    branchMake(F1);                                                             // New left branch
    branchGetTopNext(new NN(root), ort);                                        // Old root top
    for (int i = 0; i < branchSplitPoint; i++)                                  // Transfer keys, next pairs to new left child
     {branchShift(new NN(root), kn);                                            // Current key, next pair
      branchPush (F1,   kn);                                                    // Save key, next pair in new left child
     }
    branchShift(new NN(root), rkn);                                             // Root key, next pair
    for (int i = 0; i < branchSplitPoint; i++)                                  // Transfer keys, next pairs to new right child
     {branchShift(new NN(root), kn);                                            // Current key, next pair
      branchPush (F2,   kn);                                                    // Save key, next pair in new right child
     }
// f2 top = root old top, f1 top = rkn.next, root top = f2, root left = f1
    branchSetTopNext(F2, ort);                                                  // Set top next references for each branch
    branchSetTopNext(F1, rkn.next());
    branchSetTopNext(new NN(root), F2);
    copy(rkn.next().v, F1.v);                                                   // Left next refers to new left branch
    branchPush (new NN(root), rkn);                                             // Save root key, next pair
   }

  void branchSplitRoot()                                                        // Split the root when it is a branch
   {final NN F1 = new NN("left");                                               // New left branch
    final NN F2 = new NN("right");                                              // New right root
    branchSplitRoot(F1, F2);
   }

  NN branchFission(NN parent, NN source)                                        // Split a branch that is one of the children of its parent branch
   {final KeyNext  kn = branchSplitKey(source);                                 // Branch splitting key
    final NN   target = branchSplit(source);                                    // Split branch, the target branch is on the left
    final Key       k = kn.key();                                               // Split key
    final KeyNext pkn = new KeyNext(k, target);                                 // Key, next pair for insertion into branch
    final NN        t = branchGetTopNext(parent);                               // Top next of parent

    new IfElse(equals(t.v, source.v))                                           // Source is top next of parent
     {void Then()
       {branchPush(parent, pkn);                                                // Append split out branch as last key, next pair.
       }
      void Else()
       {final BI i = branchFindFirstGreaterOrEqualIndex(parent, k);             // Position to insert into branch
        branchInsert(parent, i, pkn);                                           // Insert splitting key into branch
       }
     };
    return target;                                                              // The split out branch
   }

  void branchJoinable(NN target, NN source, Layout.Bit result)                  // Check that we can join two branches
   {setIndex(nodes, target);                                                    // Index the target branch
    final Layout.Variable t = branchStuck.unary.value.like();
    copy(t, branchStuck.currentSize());

    setIndex(nodes, source);
    final Layout.Variable s = branchStuck.unary.value.like();
    copy(s, branchStuck.currentSize());
    unaryFilledMinusOne(s, t, result);
   }

  Layout.Bit branchJoinable(NN target, NN source)                               // Check that we can join two branches
   {final Layout.Bit result = Layout.createBit("result");
    branchJoinable(target, source, result);
    return result;
   }

  void branchJoin(NN target, Key key, NN source)                                // Join the specified branch onto the end of this branch
   {final KeyNext kn = new KeyNext(branchKeyNext.duplicate());                  // Work area for transferring key data pairs from the source code to the target node

    copy(kn. key().v, key.v);    setIndex(nodes, source);
    copy(kn.next().v, topNext);  setIndex(nodes, target);
    branchStuck.push(kn.v);

    for (int i = 0; i < branchSplitPoint; i++)                                  // Transfer source keys and next
     {setIndex(nodes, source); branchStuck.shift(kn.v);
      setIndex(nodes, target); branchStuck.push(kn.v);
     }
    free(source);                                                               // Free the branch that was joined
   }

  void branchMergeLeaves(NN NodeIndex, BI IBranch)                              // Merge the leaves associated with the indexed key, next pair in the specified branch
   {final BI  iBranch1 = IBranch.duplicate();                                   // Make a copy of this parameter so we can adjust it
    shiftLeftOneByOne(iBranch1.v);                                              // Get leaf associated with the next key up
    final Layout.Variable z = branchGetCurrentSize(NodeIndex);                  // Size of branch

    new IfElse(equals(z, iBranch1.v))                                           // Are we merging the leaves associated with the last key, next pair
     {void Then()                                                               // Merging the leaves associated with the last key, next pair
       {final KeyNext    lkn = branchGet(NodeIndex, IBranch);                   // Key next, pair describing left leaf
        final NN    leftLeaf = new NN(lkn.next().v);                            // Address left leaf
        final NN   rightLeaf = branchGetTopNext(NodeIndex);                     // Address right leaf which is located via top next
        new If(leafJoinable(rightLeaf, leftLeaf))                               // Are the leaves joinable?
         {void Then()                                                           // Join the leaves
           {leafJoin(leftLeaf, rightLeaf);                                      // Join right leaf into left leaf to get the order right
            branchRemove(NodeIndex, IBranch);                                   // Remove key, next pair associated with left leaf
            branchSetTopNext(NodeIndex, lkn.next());                            // Set the top next to left leaf
            free(rightLeaf);                                                    // Free the right leaf as it is now empty
           }
         };
       }
      void Else()                                                               // Merging  the leaves associated with a key, next pair other than the last one
       {final KeyNext    lkn = branchGet(NodeIndex, IBranch);                   // Key next, pair describing left leaf
        final NN    leftLeaf = new NN(lkn.next().v);                            // Address left leaf
        final KeyNext    rkn = branchGet(NodeIndex, iBranch1);                  // Key next, pair describing right leaf
        final NN   rightLeaf = new NN(rkn.next().v);                            // Address right leaf
        new If(leafJoinable(rightLeaf, leftLeaf))                               // Are the leaves joinable
         {void Then()                                                           // Join the leaves
           {leafJoin(leftLeaf, rightLeaf);                                      // Join right leaf into left leaf to get the order right
            branchRemove(NodeIndex, iBranch1);                                  // Remove key, next pair associated with right leaf
            copy(lkn.key().v, rkn.key().v);                                     // Update the key of the key, next pair for the left leaf to include the keys merged from the right leaf
            branchPut(NodeIndex, IBranch, lkn);                                 // Update the branch with the updated key, next pair whoing the new key for this key, next pair
            free(rightLeaf);                                                    // Free the right leaf as it is now empty
           }
         };
       }
     };
   }

  void branchMergeLeaves(int NodeIndex, int IBranch)                            // Merge the leaves associated with the indexed key, next pair in the specified branch
   {final NN nodeIndex = new NN(NodeIndex);
    final BI iBranch   = new BI(IBranch);
    branchMergeLeaves(nodeIndex, iBranch);
   }

  void branchMergeBranches(NN NodeIndex, BI IBranch)                            // Merge the branches associated with the indexed key, next pair in the specified branch
   {final BI  iBranch1 = IBranch.duplicate();                                   // Make a copy of this parameter so we can adjust it
    shiftLeftOneByOne(iBranch1.v);                                              // Get leaf associated with the next key up
    final Layout.Variable z = branchGetCurrentSize(NodeIndex);                  // Size of branch

    new IfElse(equals(z, iBranch1.v))                                           // Are we merging the branches associated with the last key, next pair
     {void Then()                                                               // Merging the branches associated with the last key, next pair
       {final KeyNext    lkn = branchGet(NodeIndex, IBranch);                   // Key next, pair describing left branch
        final NN  leftBranch = new NN(lkn.next().v);                            // Address left branch
        final NN rightBranch = branchGetTopNext(NodeIndex);                     // Address right branch which is located via top next
        new If(branchJoinable(rightBranch, leftBranch))                         // Are the branches joinable?
         {void Then()                                                           // Join the branches
           {branchJoin(leftBranch, lkn.key(), rightBranch);                     // Join right branch into left branch to get the order right
            branchRemove(NodeIndex, IBranch);                                   // Remove key, next pair associated with left branch
            branchSetTopNext(NodeIndex, lkn.next());                            // Set the top next to left branch
            free(rightBranch);                                                  // Free the right branch as it is now empty
           }
         };
       }
      void Else()                                                               // Merging  the branches associated with a key, next pair other than the last one
       {final KeyNext    lkn = branchGet(NodeIndex, IBranch);                   // Key next, pair describing left branch
        final NN    leftBranch = new NN(lkn.next().v);                          // Address left branch
        final KeyNext    rkn = branchGet(NodeIndex, iBranch1);                  // Key next, pair describing right branch
        final NN   rightBranch = new NN(rkn.next().v);                          // Address right branch
        new If(branchJoinable(rightBranch, leftBranch))                         // Are the branches joinable
         {void Then()                                                           // Join the branches
           {branchJoin(leftBranch, lkn.key(), rightBranch);                     // Join right branch into left branch to get the order right
            branchRemove(NodeIndex, iBranch1);                                  // Remove key, next pair associated with right branch
            copy(lkn.key().v, rkn.key().v);                                     // Update the key of the key, next pair for the left branch to include the keys merged from the right branch
            branchPut(NodeIndex, IBranch, lkn);                                 // Update the branch with the updated key, next pair whoing the new key for this key, next pair
            free(rightBranch);                                                  // Free the right branch as it is now empty
           }
         };
       }
     };
   }

  void branchFindFirstGreaterOrEqual(NN NodeIndex, Key Key, NN Result)          // Find the 'next' for the first key in a branch that is greater than or equal to the specified key or else return the top node if no such key exists.
   {new Block()
     {void code()
       {final Block outer = this;
        new Block()
         {void code()
           {final Block inner = this;
            final KeyNext  kn = new KeyNext();                                  // Work area for transferring key data pairs from the source code to the target node
            final BI    index = new BI();                                       // Index the key, next pairs in the branch
            zero(index.v);
            setIndex(nodes, NodeIndex);                                         // Index the node to search
            for (int i = 0; i < maxKeysPerBranch; i++)                          // Check each key
             {inner.returnIfEqual(index.v, branchStuck.currentSize());          // Passed all the valid keys
              branchGet(NodeIndex, index, kn);                                  // Retrieve key/next pair
              copy(Result.v, kn.next().v);
              outer.returnIfGreaterThanOrEqual(kn.key().v, Key.v);
              shiftLeftOneByOne(index.v);
             }
           }
         }; // Inner block in which we search for the key
        setIndex(nodes, NodeIndex);                                             // Index the node to search
        copy(Result.v, topNext);                                                // The search key is greater than all the keys in the branch so return the top node
       }
     }; // Outer block to exit when we have found the key

   }

  NN branchFindFirstGreaterOrEqual(NN NodeIndex, Key Key)                       // Return the 'next' for the first key in a branch that is greater than or equal to the specified key or else return the top node if no such key exists.
   {final NN Result = new NN("child");
    branchFindFirstGreaterOrEqual(NodeIndex, Key, Result);
    return Result;
   }

  BI branchFindFirstGreaterOrEqualIndex(NN NodeIndex, Key Key)                  // Assuming the branch is not full, find the index for the first key in a branch that is greater than or equal to the specified key or else return all ones if no such key exists.
   {final BI result = new BI();
    new Block()
     {void code()
       {final Block outer = this;
        new Block()
         {void code()
           {final Block inner = this;
            final KeyNext  kn = new KeyNext();                                  // Work area for transferring key data pairs from the source code to the target node
            final BI    index = new BI();                                       // Index the key, next pairs in the branch

            setIndex(nodes, NodeIndex);                                         // Index the node to search

            for (int i = 0; i < maxKeysPerBranch; i++)                          // Check each key
             {inner.returnIfEqual(index.v, branchStuck.currentSize());          // Passed all the valid keys
              branchGet(NodeIndex, index, kn);                                  // Retrieve key/next pair
              copy(result.v, index.v);
              outer.returnIfGreaterThanOrEqual(kn.key().v, Key.v);
              shiftLeftOneByOne(index.v);
             }
           }
         }; // Inner block in which we search for the key
        ones(result.v);
       }
     }; // Outer block to exit when we have found the key
    return result;
   }

  int branchSize(int nodeIndex)                                                 // Number of key, data pairs in a branch
   {nodes.setIndex(nodeIndex);
    return  branchStuck.size();
   }

  void branchPrintLeafOrBranch(Stack<StringBuilder>S, int level, int nodeIndex) // Print a branch or a leaf
   {nodes.setIndex(nodeIndex);
    if (isLeaf(nodeIndex)) leafPrint  (S, level+1, nodeIndex);
    else                   branchPrint(S, level+1, nodeIndex);
   }

  void branchPrint(Stack<StringBuilder>S, int level, int nodeIndex)             // Print branch horizontally
   {if (level > maxPrintLevels) return;
    padStrings(S, level);
    final int K = branchSize(nodeIndex);
    if (K > 0)                                                                  // Branch has key, next pairs
     {for  (int i = 0; i < K; i++)
       {final int next = branchGetNext(nodeIndex, i);                           // Each key, next pair
        branchPrintLeafOrBranch(S, level, next);                                // Print child identified by next field
        S.elementAt(level).append(""+branchGetNext(nodeIndex, i)+               // Next ( key - nodeIndex . branchIndex )
         "("+branchGetKey(nodeIndex, i)+"-"+nodeIndex+                          // Index of branch
         (i > 0 ?  "."+i : "") +")");                                           // Index of nkey, next pair unless it is the first one whose presence is amrked by the abscence of this information
       }
     }
    else                                                                        // Branch is empty so print just the index of the branch
     {S.elementAt(level).append(""+nodeIndex+"-");
     }
    final int top = branchGetTopNext(nodeIndex);                                // Top next will always be present
    S.elementAt(level).append(top);                                             // Append top next
    if (isLeaf(top)) leafPrint (S, level+1, top);                               // Print child referenced by top next as leaf if it is a leaf
    else             branchPrint(S, level+1, top);                              // Print child referenced by top next as branch if it is a branch
    padStrings(S, level);                                                       // Pad the strings at each level of the tree so we have a vertical face to continue with - a bit like Marc Brunel's tunnelling shield
   }

//D1 Print                                                                      // Print a BTree horizontally

  static void padStrings(Stack<StringBuilder> S, int level)                     // Pad a stack of strings so they all have the same length
   {for (int i = S.size(); i <= level; ++i) S.push(new StringBuilder());        // Make sure we have a full deck of strings
    int m = 0;                                                                  // Maximum length
    for (StringBuilder s : S) m = m < s.length() ? s.length() : m;              // Find maximum length
    for (StringBuilder s : S)                                                   // Pad each string to maximum length
     {if (s.length() < m) s.append(" ".repeat(m - s.length()));                 // Pad string to maximum length
     }
   }

  String print()                                                                // Print a tree horizontally
   {final Stack<StringBuilder> S = new Stack<>();

    nodes.setIndex(0);                                                          // Address root
    if (isLeaf.asInt() == 1) leafPrint(S, 0, 0); else branchPrint(S, 0, 0);     // Tree consists of one leaf node

    final StringBuilder t = new StringBuilder();
    for  (StringBuilder s : S) t.append(s.toString()+"|\n");
    return t.toString();
   }

//D1 Path                                                                       // Find the path from the root to a specified key in a leaf

  class Path                                                                    // Describe the path from the root to a specified key in a leaf
   {final Key          key;                                                     // Key being searched for
    final Layout.Bit found = Layout.createBit("found");                         // Whether the key was found or not
    final NN     nodeIndex = new NN();                                          // Node index of the leaf that should contain the key
    final Stuck       path = new Stuck("path", bitsPerKey, nodeIndex.v.asLayout()); // Guess a stuck that is big enough to hold the path
    final LI     leafIndex = new LI();                                          // Index of key, data pair in the leaf if found

    Path(Key Key)                                                               // Find the path in the BTree from the root to the key
     {key = Key;
      bitMachines(path);                                                        // Generate code in our bit machine, not theirs
      new Repeat()
       {void code()
         {returnIfOne(isLeaf(nodeIndex));                                       // Exit when we reach a leaf
          final NN next = new NN("next");                                       // Next child down
          branchFindFirstGreaterOrEqual(nodeIndex, Key, next);                  // Find next child
          path.push(nodeIndex.v);                                               // Save child index
          copy(nodeIndex.v, next.v);                                            // Child becomes parent
         }
       };

      leafFindIndexOf(nodeIndex, Key, found, leafIndex);                        // Find index of the specified key, data pair in the specified leaf
     }

    Path(int Key) {this(new Key(Key));}                                         // Find the path in the BTree from the root to the indicated key

    Stuck.Index lastNotFull()                                                   // Find the index of the last not full branch in the path
     {final Stuck.Index pi = path.new Index("pathIndex");                       // Path index
      new Block()                                                               // Jump out of this block of we find a non full branch
       {void code()
         {final Block outer = this;                                             // Record outer block in something other than this
          path.new Down()                                                       // Each branch in the path from the leaf to the root
           {void before() {pi.setValid();}                                      // Assume we will find a non full branch
            void down(Repeat r)                                                 // Check each entry in the path starting with the first one
             {copy(pi.index, index);                                            // Save indexof current branch
              outer.returnIfZero(branchIsFull(new NN(value)));                  // Non full branch so we have found the first not full going from the top down
             }
            void after() {pi.setNotValid();};                                   // Every branch is full
           };
         }
       };
      return pi;
     }
    public String toString()                                                    // Print path
     {return ""+key.v+found+path+nodeIndex.v+leafIndex.v;
     }
    void ok(String expected) {Test.ok(toString(), expected);}                   // Check a path is as expected
   }

//D1 Search                                                                     // Find a key, data pair

  void find(Key Key, Layout.Bit Found, Data Data)                               // Find the data associated with a key in a tree
   {final NN nodeIndex = new NN();                                              // Node index variable
    final LI leafIndex = new LI();                                              // Leaf key, data pair index variable

    new Repeat()
     {void code()
       {returnIfOne(isLeaf(nodeIndex));                                         // Exit when we reach a leaf
        final NN next = new NN("next");                                         // Next child down
        branchFindFirstGreaterOrEqual(nodeIndex, Key, next);
        copy(nodeIndex.v, next.v);
       }
     };

    leafFindIndexOf(nodeIndex, Key, Found, leafIndex);                          // Find index of the specified key, data pair in the specified leaf
    new If(Found)
     {void Then()
       {final KeyData kd = new KeyData();                                       // Key, data pair from leaf
        leafGet(nodeIndex, leafIndex, kd);                                      // Get key, data from stuck
        copy(Data.v, kd.v.asLayout().get("leafData"));
       }
     };
   }

  Layout.Bit find(Key Key, Data Data)                                           // Find the data associated with a key in a tree
   {Layout.Bit Found = Layout.createBit("found");                               // Whether we found the key
    find(Key, Found, Data);
    return Found;
   }

  Layout.Bit find(int Key, Data Data)                                           // Find the data associated with a key in a tree
   {Layout.Bit Found = Layout.createBit("found");                               // Whether we found the key
    find(new Key(Key), Found, Data);
    return Found;
   }

  void findAndInsert(Key Key, Data Data, Layout.Bit Inserted)                   // Find the leaf for a key and insert the indicated key, data pair into if possible, returning true if the insertion was possible else false.
   {final NN nodeIndex = new NN();                                              // Node index variable starting at the root
    final LI leafIndex = new LI();                                              // Leaf key, data index variable
    copy(nodeIndex.v, root);                                                    // Start at the root

    new Repeat()                                                                // Step down through branches to a leaf into which it might be possible to insert the key
     {void code()
       {Layout.Bit il = isLeaf(nodeIndex);
        returnIfOne(isLeaf(nodeIndex));                                         // Exit when we reach a leaf
        final NN child = branchFindFirstGreaterOrEqual(nodeIndex, Key);         // Step down to the next node
        copy(nodeIndex.v,  child.v);
       }
     };

    leafFindIndexOf(nodeIndex, Key, Inserted, leafIndex);                       // Find index of the specified key, data pair in the specified leaf
    new IfElse (Inserted)
     {void Then()
       {leafPut(nodeIndex, leafIndex, new KeyData(Key, Data));                  // Key already present in leaf - update data
       }
      void Else()                                                               // Key not present
       {new If (leafIsNotFull(nodeIndex))
         {void Then()                                                           // Leaf is not full so insertion is possible
           {leafInsertPair(nodeIndex, Key, Data);                               // Insert a key and the corresponding data into a leaf at the correct position
            ones(Inserted);                                                     // Show we were able to insert or update the key
           }
         };
       }
     };
   }

  Layout.Bit findAndInsert(Key Key, Data Data)                                  // Find the leaf for a key and insert the indicated key, data pair into if possible, returning true if the insertion was possible else false.
   {final Layout.Bit inserted = Layout.createBit("inserted");                   // Whether theinsert succeeded
    findAndInsert(Key, Data, inserted);                                         // Find the leaf for a key and insert the indicated key, data pair into if possible, returning true if the insertion was possible else false.
    return inserted;
   }

//D1 Insertion                                                                  // Insert key, data pairs into the BTree

  void put(Key Key, Data Data)                                                  // Insert a new key, data pair into the BTree
   {new Block()
     {void code()
       {returnIfOne(findAndInsert(Key, Data));                                  // Return immediately if fast insert succeeded

        new IfElse(rootIsLeaf())                                                // Insert into root as a leaf
         {void Then()
           {new IfElse(leafRootIsFull())                                        // Root is a leaf
             {void Then()                                                       // Insert into root as a leaf which is full
               {leafSplitRoot();                                                // Split the root known to be a leaf
                final NN n = new NN("next");                                    // The index of the node into which to insert the key, data pair
                branchFindFirstGreaterOrEqual(new NN(root), Key, n);            // Choose the leaf in which to insert the key
                leafInsertPair(n, Key, Data);                                   // Insertion is possible because the leaf was just split out of the root and so must have free space
               }
              void Else()                                                       // Still room in the root while it is is a leaf
               {leafInsertPair(new NN(root), Key, Data);                        // Insertion is possible because the leaf is not full
               }
             };
            returnRegardless();
           }
          void Else()                                                           // Root is a branch
           {new If(branchRootIsFull())                                          // Check root is a full branch
             {void Then()                                                       // Root is a full branch so split
               {branchSplitRoot();                                              // Split full branch root
               }
             };
           }
         };

        final NN p = new NN("p");                                               // The root is known to be a branch because if it were a leaf we would have either inserted into the leaf or split it.
        final NN q = new NN("q");                                               // Child beneath parent
        new Repeat()                                                            // Step down through tree to find the required leaf, splitting as we go
         {void code()
           {branchFindFirstGreaterOrEqual(p, Key, q);                           // Step down to next child
            returnIfOne(isLeaf(q));                                             // Stepped to a leaf

            new IfElse (branchIsFull(q))                                        // Split the child branch because it is full and we might need to insert below it requiring a slot in this branch
             {void Then()                                                       // Branch is full
               {branchFission(p, q);                                            // Split full branch
               }
              void Else()                                                       // Branch was not full
               {copy(p.v, q.v);                                                 // Step down
               }
             };
           }
         };

        leafFission(p, q);                                                      // If the leaf did not need splitting findAndInsert() would have suceeded
        branchFindFirstGreaterOrEqual(p, Key, q);                               // Step down to next child which will be a leaf that has just ben split or split out
        leafInsertPair(q, Key, Data);
       } // code
     }; // block
   } // put

  void put(int Key, int Data) {put(new Key(Key), new Data(Data));}              // Insert a new integer key, data pair into the BTree

//D1 Deletion                                                                   // Delete a key from a BTre. If the key is present, return the associated data
/*
  Layout.Bit delete(Key Key, Data Data)                                         // Delete a key from a tree
   {final Layout.Bit Found = createBit("found");                                // Whether the key was found or not
    new Block()
     {void code()
       {returnIfZero(findAndInsert(Key, Data));                                 // Return immediately if the key is not present

    if (new Node().isLeaf())                                                    // Delete from root as a leaf
     {final Leaf r = new Node().leaf();                                         // Root is a leaf
      final int  i = r.findIndexOfKey(keyName);                                 // Only one leaf and the key is known to be in the BTree so it must be in this leaf
      r.removeKey (i);
      r.removeData(i);
      keyDataStored.dec();
      return foundData;
     }

    if (new Node().branch().nKeys() == 1)                                       // If the root is a branch and only has one key so we might be able to merge its children
     {final Branch r = new Node().branch();                                     // Root as a branch
      final Node   A = r.getNext(0);                                            // Step down

      if (A.isLeaf())                                                           // Step down to leaf
       {final Leaf    a = A.leaf(), b = r.getTop().leaf();
        final boolean j = a.joinable(b);                                        // Can we merge the two leaves
        if (j)                                                                  // Merge the two leaves
         {a.join(b);
          a.setRoot();                                                          // New merged root
         }
       }
      else                                                                      // Merge two branches under root
       {final Branch  a = new Branch(A.index), b = r.getTop().branch();
        final boolean j = a.joinable(b);                                        // Can we merge the two branches
        if (j)                                                                  // Merge the two branches
         {final Key k = r.getKey(0);
          a.join(b, k);
          a.setRoot();                                                          // New merged root
         }
       }
     }

    Node P = new Node();                                                        // We now know that the root is a branch

    for    (int i = 0; i < 999; ++i)                                            // Step down through tree to find the required leaf, merging leaves as we go
     {if (P.isLeaf()) break;                                                    // Stepped to a leaf
      final Branch p = P.branch();
      for(int j = 0; j < p.nKeys()-1; ++j)                                      // See if any pair under this node can be merged
       {final Node A = p.getNext(j);
        final Node B = p.getNext(j+1);
        if (A.isLeaf())                                                         // Both nodes are leaves
         {final Leaf a = A.leaf(), b = B.leaf();
          final boolean m = a.joinable(b);                                      // Can we merge the two leaves
          if (m)                                                                // Merge the two leaves
           {a.join(b);
            p.removeKey(j);
            p.removeNext(j+1);
           }
         }
        else                                                                    // Merge two branches
         {final Branch a = A.branch(), b = B.branch();
          final boolean m = a.joinable(b);                                      // Can we merge the two branches
          if (m)                                                                // Merge the two branches
           {final Key k = p.getKey(j); p.removeKey(j);
            a.join(b, k);
            p.removeNext(j+1);
           }
         }
       }

      if (!p.isEmpty())                                                         // Check last pair
       {final Node A = p.lastNext();
        if (A instanceof Leaf)
         {final Leaf a = A.leaf(), b = p.getTop().leaf();
          final boolean j = a.joinable(b);                                      // Can we merge the two leaves
          if (j)                                                                // Merge the two leaves
           {a.join(b);                                                          // Join the two leaves
            p.popKey();                                                         // Remove the last key from parent branch as this is the last pair that is being merged
            p.popNext();                                                        // Remove the last next from parent branch as this is the last pair that is being merged
            p.setTop(a.index);                                                  // The node to goto if the search key is greater than all keys in the branch
           }
         }
        else                                                                    // Merge two branches
         {final Branch a = A.branch(), b = p.getTop().branch();
          final boolean j = a.joinable(b);                                      // Can we merge the last two branches
          if (j)                                                                // Merge the last two branches
           {final Key k = p.popKey();
            a.join(b, k);
            p.popNext();
            p.setTop(a.index);
           }
         }
       }
      P = p.findFirstGreaterOrEqual(keyName);                                   // Find key position in branch
     }
    keyDataStored.dec();                                                        // Remove one entry  - we are on a leaf andf the entry is known to exist

    final Leaf l = P.leaf();                                                    // We know we are at the leaf
    final int  F = l.findIndexOfKey(keyName);                                   // Key is known to be present
    l.removeKey(F);
    l.removeData(F);

    return foundData;
   } // delete

//D1 Print                                                                      // Print a tree

  static void padStrings(java.util.Stack<StringBuilder> S, int level)           // Pad a stack of strings so they all have the same length
   {for (int i = S.size(); i <= level; ++i) S.push(new StringBuilder());
    int m = 0;
    for (StringBuilder s : S) m = m < s.length() ? s.length() : m;
    for (StringBuilder s : S)
      if (s.length() < m) s.append(" ".repeat(m - s.length()));
   }

  static String joinStrings(java.util.Stack<StringBuilder> S)                   // Join lines
   {final StringBuilder a = new StringBuilder();
    for  (StringBuilder s : S) a.append(s.toString()+"|\n");
    return a.toString();
   }

  String printHorizontally()                                                    // Print a tree horizontally
   {final java.util.Stack<StringBuilder> S = new java.util.Stack<>();

    if (emptyTree()) return "";                                                 // Empty tree
    S.push(new StringBuilder());

    if (new Node().isLeaf())                                                    // Root is a leaf
     {final Leaf lr = new Leaf(root.toInt());
      lr.printHorizontally(S, 0, false);
      return S.toString()+"\n";
     }

    final Branch b = new Branch(root.toInt());                                  // Root is a branch
    final Node btm = b.getTop();

    final int    N = b.nKeys();

    for (int i = 0; i < N; i++)                                                 // Nodes below root
     {final Node m = b.getNext(i);
      if (m.isLeaf())
       {m.leaf().printHorizontally(S, 1, false);
        S.firstElement().append(" "+b.getKey(i).toInt());
       }
      else
       {m.branch().printHorizontally(S, 1, false);
        S.firstElement().append(" "+b.getKey(i).toInt());
       }
     }

    if (btm.isLeaf())
     {final Leaf l = b.getTop().leaf();
      l.printHorizontally(S, 1, false);
     }
    else
     {final Branch B = b.getTop().branch();
      B.printHorizontally(S, 1, false);
     }
    return joinStrings(S);
   }
*/

//D1 Tests                                                                      // Tests

/*
  static void test_create_branch()
   {final int N = 2, M = 4;
    Mjaf m = mjaf(N, N, M, N);
    final Branch b = m.new Branch(m.new Node(N+1));

    Layout.Variable key = Layout.variable("key", N);
    key.layout();
    ok( b.index, N-1);
    ok( b.isEmpty());
    ok(!b.isFull ());
    for (int i = 0; i < M-1; i++) b.put(m.key(i), m.new Node(i));
    ok(!b.isEmpty());
    ok( b.isFull ());
    b.ok("Branch_1(0:0, 1:1, 2:2, 3)");

    Branch c = b.split();
    ok(c.nKeys(), 1);
    ok(b.nKeys(), 1);
    ok(b.nKeys(), 1);
    b.ok("Branch_1(2:2, 3)");
    c.ok("Branch_0(0:0, 1)");
   }

  static void test_join_branch()
   {final int N = 4, M = 4;
    Mjaf m = mjaf(N, N, M, N);
    Branch j = m.new Branch(m.new Node(11));
    Branch k = m.new Branch(m.new Node(13));
    Branch l = m.new Branch(m.new Node(15));
    j.put(m.key(1), m.new Node(8)); ok( k.joinable(j));
    k.put(m.key(3), m.new Node(4)); ok( k.joinable(j));

    k.ok("Branch_2(3:4, 13)");
    j.ok("Branch_3(1:8, 11)");
    ok(k.nKeys(), 1);
    ok(j.nKeys(), 1);
    j.join(k, m.key(2));
    j.ok("Branch_3(1:8, 2:11, 3:4, 13)");
    ok(m.nodesFree.stuckSize(), 2);
   }

  static void test_create_leaf()
   {final int N = 2, M = 4;
    Mjaf m = mjaf(N, N, M, N);
    final Leaf l = m.new Leaf();
    ok(l.index, N-1);

    Layout.Variable key = Layout.variable("key", N);
    key.layout();
    ok( l.isEmpty());
    ok(!l.isFull ());
    for (int i = 0; i < M; i++)
     {key.set(i);
      l.put(m. new Key(key.memory()), m.new Data(key.memory()));
     }
    ok(!l.isEmpty());
    ok( l.isFull ());
    l.ok("Leaf_1(0:0, 1:1, 2:2, 3:3)");

    Leaf k = l.split();
    ok(k.nKeys(), 2); ok(k.nData(), 2);
    ok(l.nKeys(), 2); ok(l.nKeys(), 2);
    k.ok("Leaf_0(0:0, 1:1)");
    l.ok("Leaf_1(2:2, 3:3)");
   }

  static void test_join_leaf()
   {final int N = 4, M = 4;
    Mjaf m = mjaf(N, N, M, N);
    Leaf j = m.new Leaf();
    Leaf k = m.new Leaf();
    Leaf l = m.new Leaf();
    j.put(m.key(1), m.data(8)); ok( k.joinable(j));
    j.put(m.key(2), m.data(6)); ok( k.joinable(j));
    k.put(m.key(3), m.data(4)); ok( k.joinable(j));
    k.put(m.key(4), m.data(2)); ok( k.joinable(j));
    l.put(m.key(4), m.data(8)); ok( k.joinable(l));
    l.put(m.key(3), m.data(6)); ok( k.joinable(l));
    l.put(m.key(2), m.data(4)); ok(!k.joinable(l));
    l.put(m.key(1), m.data(2)); ok(!k.joinable(l));

    k.ok("Leaf_2(3:4, 4:2)");
    j.ok("Leaf_3(1:8, 2:6)");
    ok(m.nodesFree.stuckSize(), 1);
    j.join(k);
    j.ok("Leaf_3(1:8, 2:6, 3:4, 4:2)");
    ok(m.nodesFree.stuckSize(), 2);
   }

  static void test_put()
   {final int N = 8, M = 4;
    Mjaf m = mjaf(N, N, M, 4*N);

    m.put(m.key(1), m.data(2));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), "[1]\n");
    ok(m.size(), 1);

    m.put(m.key(2), m.data(4));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), "[1,2]\n");
    ok(m.size(), 2);

    m.put(m.key(3), m.data(6));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), "[1,2,3]\n");
    ok(m.size(), 3);

    m.put(m.key(4), m.data(8));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), "[1,2,3,4]\n");
    ok(m.size(), 4);

    m.put(m.key(5), m.data(10));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
    2     |
1,2  3,4,5|
""");
    ok(m.size(), 5);

    m.put(m.key(6), m.data(12));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
    2       |
1,2  3,4,5,6|
""");
    ok(m.size(), 6);

    m.put(m.key(7), m.data(14));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
    2    4     |
1,2  3,4  5,6,7|
""");
    ok(m.size(), 7);

    m.put(m.key(8), m.data(16));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
    2    4       |
1,2  3,4  5,6,7,8|
""");
    ok(m.size(), 8);

    m.put(m.key(9), m.data(18));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
    2    4    6     |
1,2  3,4  5,6  7,8,9|
""");
    ok(m.size(), 9);

    m.put(m.key(10), m.data(20));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
    2    4    6        |
1,2  3,4  5,6  7,8,9,10|
""");
    ok(m.size(), 10);

    m.put(m.key(11), m.data(22));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
         4                 |
   2         6    8        |
1,2  3,4  5,6  7,8  9,10,11|
""");
    ok(m.size(), 11);

    m.put(m.key(12), m.data(24));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
         4                    |
   2         6    8           |
1,2  3,4  5,6  7,8  9,10,11,12|
""");
    ok(m.size(), 12);

    m.put(m.key(13), m.data(26));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
         4                         |
   2         6    8     10         |
1,2  3,4  5,6  7,8  9,10   11,12,13|
""");
    ok(m.size(), 13);

    m.put(m.key(14), m.data(28));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
         4                            |
   2         6    8     10            |
1,2  3,4  5,6  7,8  9,10   11,12,13,14|
""");
    ok(m.size(), 14);

    m.put(m.key(15), m.data(30));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
         4         8                       |
   2         6          10      12         |
1,2  3,4  5,6  7,8  9,10   11,12   13,14,15|
""");
    ok(m.size(), 15);

    m.put(m.key(16), m.data(32));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
         4         8                          |
   2         6          10      12            |
1,2  3,4  5,6  7,8  9,10   11,12   13,14,15,16|
""");
    ok(m.size(), 16);

    m.put(m.key(17), m.data(34));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
         4         8                               |
   2         6          10      12      14         |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16,17|
""");
    ok(m.size(), 17);

    m.put(m.key(18), m.data(36));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
         4         8                                  |
   2         6          10      12      14            |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16,17,18|
""");
    ok(m.size(), 18);

    m.put(m.key(19), m.data(38));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
                   8                                       |
        4                       12                         |
   2         6          10              14      16         |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18,19|
""");
    ok(m.size(), 19);

    m.put(m.key(20), m.data(40));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
                   8                                          |
        4                       12                            |
   2         6          10              14      16            |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18,19,20|
""");
    ok(m.size(), 20);

    m.put(m.key(21), m.data(42));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
                   8                                               |
        4                       12                                 |
   2         6          10              14      16      18         |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20,21|
""");
    ok(m.size(), 21);

    for (int i = 22; i < 32; i++) m.put(m.key(i), m.data(i<<2));

    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
                   8                             16                                                        |
        4                       12                              20              24                         |
   2         6          10              14              18              22              26      28         |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30,31|
""");
    ok(m.size(), 31);
   }

  static void test_put2()
   {final int N = 8, M = 4;
    Mjaf m = mjaf(N, N, M, 4*N);

    for (int i = 0; i < 64; i++) m.put(m.key(i>>1), m.data(i));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
                   7                           15                                                           |
        3                     11                              19              23                            |
   1         5         9              13              17              21              25      27            |
0,1  2,3  4,5  6,7  8,9  10,11   12,13   14,15   16,17   18,19   20,21   22,23   24,25   26,27   28,29,30,31|
""");
     ok(m.find(m.key( 9)).toInt() == 19);
     ok(m.find(m.key(10)).toInt() == 21);
     ok(m.find(m.key(32))         == null);
        m.put (m.key( 9), m.data(18));
     ok(m.find(m.key( 9)).toInt() == 18);
   }

  static void test_put_reverse()
   {final int N = 8, M = 4;
    Mjaf m = mjaf(N, N, M, 4*N);

    for (int i = 15; i > 0; --i)
     {m.put(m.key(i), m.data(i<<1));
     }
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
                7           11             |
     3    5         9              13      |
1,2,3  4,5  6,7  8,9  10,11   12,13   14,15|
""");
     ok(m.find(m.key( 9)).toInt() == 18);
     ok(m.find(m.key(15)).toInt() == 30);
     ok(m.find(m.key(0))          == null);
   }

  static int[]random_array()                                                    // Random array
   {final int[]r = {27, 442, 545, 317, 511, 578, 391, 993, 858, 586, 472, 906, 658, 704, 882, 246, 261, 501, 354, 903, 854, 279, 526, 686, 987, 403, 401, 989, 650, 576, 436, 560, 806, 554, 422, 298, 425, 912, 503, 611, 135, 447, 344, 338, 39, 804, 976, 186, 234, 106, 667, 494, 690, 480, 288, 151, 773, 769, 260, 809, 438, 237, 516, 29, 376, 72, 946, 103, 961, 55, 358, 232, 229, 90, 155, 657, 681, 43, 907, 564, 377, 615, 612, 157, 922, 272, 490, 679, 830, 839, 437, 826, 577, 937, 884, 13, 96, 273, 1, 188};
    return r;
   }

  static void test_put_random()
   {final int[] r = random_array();
    final Mjaf m = mjaf(12, 12, 4, r.length);
    for (int i = 0; i < r.length; ++i) m.put(m.key(r[i]), m.data(i));
    //stop(m.printHorizontally());
    ok(m.printHorizontally(), """
                                                                                                                                                                                                                                                                   511                                                                                                                                                                                                                                    |
                                                                    186                                                                    317                                                                                                                                                                                                      658                                                                                                                                                   |
                                      103                                                         246                                                                                      403                                      472                                                                        578                                                                704                                              858                           912                                      |
       27      39         72                     135                                   234                   261            279                       344        358            391                   425                442                           501                       545        560                           611            650                           686                           806            830                           903                               961        987        |
1,13,27   29,39   43,55,72   90,96,103    106,135    151,155,157,186    188,229,232,234    237,246    260,261    272,273,279    288,298,317    338,344    354,358    376,377,391    401,403    422,425    436,437,438,442    447,472    480,490,494,501    503,511    516,526,545    554,560    564,576,577,578    586,611    612,615,650    657,658    667,679,681,686    690,704    769,773,804,806    809,826,830    839,854,858    882,884,903    906,907,912    922,937,946,961    976,987    989,993|
""");
    for (int i = 0; i < r.length; ++i) ok(m.find(m.key(r[i])), m.data(i));
    ok(m.find(m.key(r.length+1)) == null);
   }

  static Mjaf test_delete_one(int Print, int count, int delete, String e, String d)
   {final int N = 8, M = 4;
    final boolean print = Print > 0;
    Mjaf m = mjaf(N, N, M, 4*N);

    for (int i = 0; i < count; i++) m.put(m.key(i+1), m.data(2*(i+1)));
    if (print) stop(m.printHorizontally());
    test_delete_two(m, Print, delete, d);
    return m;
   }

  static void test_delete_two(Mjaf m, int Print, int delete, String d)
   {final int N = 8, M = 4;
    final boolean print = Print > 0;

    m.delete(m.key(delete));
    if (print) stop(m.printHorizontally(), d);
                 ok(m.printHorizontally(), d);
   }

  static void test_delete()
   {Mjaf m;
    test_delete_one(0, 1, 1, """
[1]
""", """
[]
""");
    test_delete_one(0, 2, 1, """
[1,2]
""", """
[2]
""");
    test_delete_one(0, 2, 2, """
[1,2]
""", """
[1]
""");
    test_delete_one(0, 3, 1, """
[1,2,3]
""", """
[2,3]
""");
    test_delete_one(0, 3, 2, """
[1,2,3]
""", """
[1,3]
""");
    test_delete_one(0, 3, 3, """
[1,2,3]
""", """
[1,2]
""");
    test_delete_one(0, 5, 1, """
    2     |
1,2  3,4,5|
""", """
  2     |
2  3,4,5|
""");
    m = test_delete_one(0, 5, 1, """
    2     |
1,2  3,4,5|
""", """
  2     |
2  3,4,5|
""");
    test_delete_two(m, 0, 2, """
[3,4,5]
""");
    m = test_delete_one(0, 7, 5, """
    2    4     |
1,2  3,4  5,6,7|
""", """
        4   |
1,2,3,4  6,7|
""");
    test_delete_two(m, 0, 2, """
      4   |
1,3,4  6,7|
""");
    test_delete_two(m, 0, 3, """
    4   |
1,4  6,7|
""");
    test_delete_two(m, 0, 1, """
[4,6,7]
""");

    m = test_delete_one(0, 31, 5, """
                   8                             16                                                        |
        4                       12                              20              24                         |
   2         6          10              14              18              22              26      28         |
1,2  3,4  5,6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30,31|
""", """
                                              16                                                        |
               8                                             20              24                         |
       4  6          10      12      14              18              22              26      28         |
1,2,3,4  6  7,8  9,10   11,12   13,14   15,16   17,18   19,20   21,22   23,24   25,26   27,28   29,30,31|
""");
    test_delete_two(m, 0, 20, """
                                              16                                                   |
               8                                                        24                         |
       4  6          10      12      14                 20      22              26      28         |
1,2,3,4  6  7,8  9,10   11,12   13,14   15,16   17,18,19   21,22   23,24   25,26   27,28   29,30,31|
""");
    test_delete_two(m, 0, 21, """
                8                             16                      24                        |
       4  6          10      12      14                 20   22              26      28         |
1,2,3,4  6  7,8  9,10   11,12   13,14   15,16   17,18,19   22   23,24   25,26   27,28   29,30,31|
""");
    test_delete_two(m, 0, 22, """
                8                             16                 24                        |
       4  6          10      12      14                 22              26      28         |
1,2,3,4  6  7,8  9,10   11,12   13,14   15,16   17,18,19   23,24   25,26   27,28   29,30,31|
""");
    test_delete_two(m, 0, 23, """
                8                             16              24                        |
       4  6          10      12      14                 22           26      28         |
1,2,3,4  6  7,8  9,10   11,12   13,14   15,16   17,18,19   24   25,26   27,28   29,30,31|
""");
    ok(m.size(), 26);

    test_delete_two(m, 0, 24, """
                8                             16            24                        |
       4  6          10      12      14                 22         26      28         |
1,2,3,4  6  7,8  9,10   11,12   13,14   15,16   17,18,19      25,26   27,28   29,30,31|
""");
    ok(m.size(), 25);

    test_delete_two(m, 0, 6, """
               8                             16            24                        |
       4 6          10      12      14                 22         26      28         |
1,2,3,4    7,8  9,10   11,12   13,14   15,16   17,18,19      25,26   27,28   29,30,31|
""");
    ok(m.size(), 24);
   }
*/

//D0 Tests                                                                      // Testing

  static Mjaf create_leaf_tree()                                                // Create a pre loaded tree of leaves
   {final int B = 12, S = 4, N = 3;
    Mjaf m = mjaf(B, B, S, N);
    m.reset();
    int c = 1;
    for (int n = 0; n < N; n++)                                                 // Each node
     {m.setIndexFromInt(m.nodes, n);                                            // Index node
      m.setVariable("nodes.node.isLeaf",                      1);
      m.setVariable("nodes.node.isBranch",                    0);
      m.setVariable(nbol+"leaf.unary",    15);

      for (int s = 0; s < S; s++)                                               // Each key, data pair in leaf
       {final Layout.Array a = m.layout.get(nbol+"leaf.array").toArray();
        m.setIndexFromInt(a, s);
        m.setVariable(nbol+"leaf.array.leafKeyData.leafKey",  c++);
        m.setVariable(nbol+"leaf.array.leafKeyData.leafData", c++);
       }
     }
    m.copy(m.layout.get("nodesFree.unary").toVariable(), Layout.unary(N));      // Tree globals
    m.copy(m.nodesCreated,  N);
    m.copy(m.keyDataStored, N);
    m.copy(m.root,          0);                                                 // Root is the zero node
    m.copy(m.hasNode,       1);

    m.execute();
    Layout.constants(m.root);                                                   // Root is always the zero node
    //stop(m.layout);
    m.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0   322                      tree
S    0     9                484     nodesFree     nodesFree
A    0     6      0          36       array     nodesFree.array
V    0     2                  0         nodeFree     nodesFree.array.nodeFree
A    2     6      1          36       array     nodesFree.array
V    2     2                  1         nodeFree     nodesFree.array.nodeFree
A    4     6      2          36       array     nodesFree.array
V    4     2                  2         nodeFree     nodesFree.array.nodeFree
V    6     3                  7       unary     nodesFree.unary
V    9     2                  3     nodesCreated     nodesCreated
V   11     2                  3     keyDataStored     keyDataStored
V   13     2                  0     =root     root
B   15     1                  1     hasNode     hasNode
A   16   306      0                 nodes     nodes
S   16   102                          node     nodes.node
B   16     1                  1         isLeaf     nodes.node.isLeaf
B   17     1                  0         isBranch     nodes.node.isBranch
U   18   100                            branchOrLeaf     nodes.node.branchOrLeaf
S   18    47                              branch     nodes.node.branchOrLeaf.branch
S   18    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A   18    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   18    14               8193                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   18    12                  1                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   30     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   32    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   32    14               3072                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   32    12               3072                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   44     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   46    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   46    14               1024                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   46    12               1024                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   58     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V   60     3                  0               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V   63     2                  0             topNext     nodes.node.branchOrLeaf.branch.topNext
S   18   100                              leaf     nodes.node.branchOrLeaf.leaf
A   18    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S   18    24               8193               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   18    12                  1                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   30    12                  2                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   42    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S   42    24              16387               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   42    12                  3                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   54    12                  4                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   66    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S   66    24              24581               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   66    12                  5                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   78    12                  6                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   90    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S   90    24              32775               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   90    12                  7                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  102    12                  8                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  114     4                 15             unary     nodes.node.branchOrLeaf.leaf.unary
A  118   306      1                 nodes     nodes
S  118   102                          node     nodes.node
B  118     1                  1         isLeaf     nodes.node.isLeaf
B  119     1                  0         isBranch     nodes.node.isBranch
U  120   100                            branchOrLeaf     nodes.node.branchOrLeaf
S  120    47                              branch     nodes.node.branchOrLeaf.branch
S  120    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  120    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  120    14               8201                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  120    12                  9                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  132     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  134    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  134    14              11266                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  134    12               3074                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  146     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  148    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  148    14               3072                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  148    12               3072                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  160     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  162     3                  0               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V  165     2                  0             topNext     nodes.node.branchOrLeaf.branch.topNext
S  120   100                              leaf     nodes.node.branchOrLeaf.leaf
A  120    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  120    24              40969               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  120    12                  9                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  132    12                 10                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  144    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  144    24              49163               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  144    12                 11                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  156    12                 12                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  168    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  168    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  168    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  180    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  192    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  192    24              65551               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  192    12                 15                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  204    12                 16                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  216     4                 15             unary     nodes.node.branchOrLeaf.leaf.unary
A  220   306      2                 nodes     nodes
S  220   102                          node     nodes.node
B  220     1                  1         isLeaf     nodes.node.isLeaf
B  221     1                  0         isBranch     nodes.node.isBranch
U  222   100                            branchOrLeaf     nodes.node.branchOrLeaf
S  222    47                              branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14               8209                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                 17                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               3076                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12               3076                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14               5121                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12               1025                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  0               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V  267     2                  0             topNext     nodes.node.branchOrLeaf.branch.topNext
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              73745               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                 17                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 18                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              81939               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                 19                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 20                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24              90133               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 21                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 22                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24              98327               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 23                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 24                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                 15             unary     nodes.node.branchOrLeaf.leaf.unary
""");
    m.reset();
    return m;
   }

  static Mjaf create_branch_tree()                                              // Create a pre loaded treee of branches
   {final int B = 12, S = 4, N = 3;
    Mjaf m = mjaf(B, B, S, N);
    m.reset();
    int c = 1;
    for (int n = 0; n < N; n++)                                                 // Each node
     {m.setIndexFromInt(m.nodes, n);                                            // Index node
      m.setVariable("nodes.node.isLeaf",                      0);
      m.setVariable("nodes.node.isBranch",                    1);
      m.setVariable(nbol+"branch.topNext", n);
      m.setVariable(nbol+"branch.branchStuck.unary", Layout.unary(N));
      for (int s = 0; s < m.maxKeysPerBranch; s++)                              // Each key, data pair in leaf
       {final Layout.Array a = m.layout.get(nbol+"branch.branchStuck.array").toArray();
        m.setIndexFromInt(a, s);                                                // Index pair
        m.setVariable(nbol+"branch.branchStuck.array.branchKeyNext.branchKey", c++);
        m.setVariable(nbol+"branch.branchStuck.array.branchKeyNext.branchNext", s);
       }
     }
    m.copy(m.layout.get("nodesFree.unary").toVariable(), Layout.unary(N));      // Tree globals
    m.copy(m.nodesCreated,  N);
    m.copy(m.keyDataStored, N);
    m.copy(m.root,          0);                                                 // The root is node zero
    m.copy(m.hasNode,       1);

    m.execute();
    Layout.constants(m.root);                                                   // The root is always node zero
    //stop(m.layout);
    m.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0   322                      tree
S    0     9                484     nodesFree     nodesFree
A    0     6      0          36       array     nodesFree.array
V    0     2                  0         nodeFree     nodesFree.array.nodeFree
A    2     6      1          36       array     nodesFree.array
V    2     2                  1         nodeFree     nodesFree.array.nodeFree
A    4     6      2          36       array     nodesFree.array
V    4     2                  2         nodeFree     nodesFree.array.nodeFree
V    6     3                  7       unary     nodesFree.unary
V    9     2                  3     nodesCreated     nodesCreated
V   11     2                  3     keyDataStored     keyDataStored
V   13     2                  0     =root     root
B   15     1                  1     hasNode     hasNode
A   16   306      0                 nodes     nodes
S   16   102                          node     nodes.node
B   16     1                  0         isLeaf     nodes.node.isLeaf
B   17     1                  1         isBranch     nodes.node.isBranch
U   18   100                            branchOrLeaf     nodes.node.branchOrLeaf
S   18    47                              branch     nodes.node.branchOrLeaf.branch
S   18    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A   18    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   18    14                  1                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   18    12                  1                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   30     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   32    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   32    14               4098                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   32    12                  2                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   44     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   46    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   46    14               8195                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   46    12                  3                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   58     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V   60     3                  7               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V   63     2                  0             topNext     nodes.node.branchOrLeaf.branch.topNext
S   18   100                              leaf     nodes.node.branchOrLeaf.leaf
A   18    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S   18    24              32769               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   18    12                  1                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   30    12                  8                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   42    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S   42    24            1966132               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   42    12                 52                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   54    12                480                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   66    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S   66    24                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   66    12                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   78    12                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   90    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S   90    24                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   90    12                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  102    12                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  114     4                  0             unary     nodes.node.branchOrLeaf.leaf.unary
A  118   306      1                 nodes     nodes
S  118   102                          node     nodes.node
B  118     1                  0         isLeaf     nodes.node.isLeaf
B  119     1                  1         isBranch     nodes.node.isBranch
U  120   100                            branchOrLeaf     nodes.node.branchOrLeaf
S  120    47                              branch     nodes.node.branchOrLeaf.branch
S  120    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  120    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  120    14                  4                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  120    12                  4                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  132     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  134    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  134    14               4101                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  134    12                  5                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  146     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  148    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  148    14               8198                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  148    12                  6                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  160     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  162     3                  7               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V  165     2                  1             topNext     nodes.node.branchOrLeaf.branch.topNext
S  120   100                              leaf     nodes.node.branchOrLeaf.leaf
A  120    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  120    24              81924               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  120    12                  4                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  132    12                 20                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  144    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  144    24            4063332               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  144    12                100                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  156    12                992                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  168    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  168    24                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  168    12                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  180    12                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  192    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  192    24                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  192    12                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  204    12                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  216     4                  0             unary     nodes.node.branchOrLeaf.leaf.unary
A  220   306      2                 nodes     nodes
S  220   102                          node     nodes.node
B  220     1                  0         isLeaf     nodes.node.isLeaf
B  221     1                  1         isBranch     nodes.node.isBranch
U  222   100                            branchOrLeaf     nodes.node.branchOrLeaf
S  222    47                              branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14                  7                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                  7                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               4104                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                  8                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14               8201                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                  9                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  7               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V  267     2                  2             topNext     nodes.node.branchOrLeaf.branch.topNext
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24             131079               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                  7                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 32                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24            6160532               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                148                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12               1504                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                  0             unary     nodes.node.branchOrLeaf.leaf.unary
""");
    m.reset();
    return m;
   }

  static class TestLeafTree                                                     // Create a test tree
   {final Mjaf mjaf = create_leaf_tree();                                       // Create a sample tree
    final Layout  t = mjaf.layout;                                              // Tree layout
    final String
              node  = "nodes.node.",                                            // Name prefix for a node in the tree
              leaf  = node+"branchOrLeaf.leaf.",                                // Name prefix for leaf stuck
            branch  = node+"branchOrLeaf.branch.",                              // Name prefix for branch
      branchStuck   = branch+"branchStuck.";                                    // Name prefix for branch stuck

    final Layout.Variable
          nodeIndex = t.dupVariable("nodesFree.array.nodeFree"),                // Node index variable
        sourceIndex = nodeIndex.like(),                                         // Source node index variable
        targetIndex = nodeIndex.like(),                                         // Target node index variable
          leafIndex = t.dupVariable(leaf       +"unary"),                       // Index a key/data pair in a leaf
        branchIndex = t.dupVariable(branchStuck+"unary");                       // Index a key/next pair in a branch
    final Layout.Bit
           leafFlag = t.dupBit(node+"isLeaf"),                                  // Is leaf flag
         branchFlag = t.dupBit(node+"isBranch");                                // Is branch flag

    final Layout lef = new Layout();                                            // Empty full leaf layout
    final Layout.Bit
      le = lef.bit("le"),
      lf = lef.bit("lf"),
      lE = lef.bit("lE"),
      lF = lef.bit("lF");
    Layout.Structure lefs = lef.structure("lefs", le, lf, lE, lF);

    final LayoutAble
      kd = t.get(leaf       +"array.leafKeyData")  .duplicate(),                // Key, data pair in a leaf
      kn = t.get(branchStuck+"array.branchKeyNext").duplicate();                // Key, next pair in a branch

    TestLeafTree()                                                              // Layout temporary storage
     {lef.layout(lefs);
     }
   }

  static class TestBranchTree                                                   // Create a test tree
   {final Mjaf mjaf = create_branch_tree();                                     // Create a sample tree
    final Layout  t = mjaf.layout;                                              // Tree layout
    final String
              node  = "nodes.node.",                                            // Name prefix for a node in the tree
              leaf  = node+"branchOrLeaf.leaf.",                                // Name prefix for leaf stuck
            branch  = node+"branchOrLeaf.branch.",                              // Name prefix for branch
      branchStuck   = branch+"branchStuck.";                                    // Name prefix for branch stuck

    final Layout.Variable
          nodeIndex = t.dupVariable("nodesFree.array.nodeFree"),                // Node index variable
        sourceIndex = t.dupVariable("nodesFree.array.nodeFree"),                // Source node index variable
        targetIndex = t.dupVariable("nodesFree.array.nodeFree"),                // Target node index variable
          leafIndex = t.dupVariable(leaf       +"unary"),                       // Index a key/data pair in a leaf
        branchIndex = t.dupVariable(branchStuck+"unary");                       // Index a key/next pair in a branch
    final Layout.Bit
           leafFlag = t.dupBit(node+"isLeaf"),                                  // Is leaf flag
         branchFlag = t.dupBit(node+"isBranch");                                // Is branch flag

    final Layout bef = new Layout();                                            // Empty full branch layout
    final Layout.Bit
      be = bef.bit("be"),
      bf = bef.bit("bf"),
      bE = bef.bit("bE"),
      bF = bef.bit("bF");

    Layout.Structure befs = bef.structure("befs", be, bf, bE, bF);

    final LayoutAble
      kd = t.get(leaf       +"array.leafKeyData")  .duplicate(),                // Key, data pair in a leaf
      kn = t.get(branchStuck+"array.branchKeyNext").duplicate();                // Key, next pair in a branch

    TestBranchTree()                                                            // Layout temporary storage
     {bef.layout(befs);
     }
   }

  static void test_leaf_make()                                                  // Make a new leaf
   {TestLeafTree t = new TestLeafTree();                                        // Allocate a new leaf treetree tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    m.leafMake(m.new NN(t.nodeIndex));                                          // Choose the node
    m.isLeaf  (m.new NN(t.nodeIndex), t.leafFlag);                              // Copy its leaf flag
    m.isBranch(m.new NN(t.nodeIndex), t.branchFlag);                            // Copy its branch flag
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(t.n);
    t.nodeIndex.ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  2   nodeFree
""");
    //stop(t.l);                                                                // Check Leaf
    t.leafFlag.ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  1   isLeaf
""");
    //stop(t.b);                                                                // Check not branch
    t.branchFlag.ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  0   isBranch
""");

    m.reset();                                                                  // Next leaf from a leaf
    m.leafMake(m.new NN(t.nodeIndex));                                          // Choose the node
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(t.n);
    t.nodeIndex.ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  1   nodeFree
""");

    m.reset();                                                                  // Next leaf from a branch
    m.leafMake(m.new NN(t.nodeIndex));                                          // Choose the node
    m.copy(t.leafFlag, m.isLeaf);                                               // Copy its leaf flag
    m.copy(t.branchFlag, m.isBranch);                                           // Copy its branch flag
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(n);
    t.nodeIndex.ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  0   nodeFree
""");
    //stop(l);                                                                  // Check Leaf
    t.leafFlag.ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  1   isLeaf
""");
    //stop(b);                                                                  // Check not branch
    t.branchFlag.ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  0   isBranch
""");
    m.reset();
   }

  static void test_branch_make()                                                // Allocate a new branch
   {TestBranchTree t = new TestBranchTree();                                    // Create a test tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    m.branchMake(m.new NN(t.nodeIndex));                                        // Allocate a new Choose the node
    m.isLeaf    (m.new NN(t.nodeIndex), t.leafFlag);                            // Copy its leaf flag
    m.isBranch  (m.new NN(t.nodeIndex), t.branchFlag);                          // Copy its branch flag
    m.execute();                                                                // Execute the code to copy out the splitting key

    //stop(m.new NN(t.nodeIndex));
    t.nodeIndex.ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  2   nodeFree
""");
    //stop(t.leafFlag);
    t.leafFlag.ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  0   isLeaf
""");
    //stop(t.branchFlag);
    t.branchFlag.ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  1   isBranch
""");
   }

  static void test_leaf_is_full_empty()                                         // Test whether a leaf is full or emoty
   {TestLeafTree t = new TestLeafTree();                                        // Create a tree of leaves
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    Layout           l = new Layout();
    Layout.Bit       e = l.bit("empty");
    Layout.Bit       E = l.bit("Empty");
    Layout.Bit       f = l.bit("full");
    Layout.Bit       F = l.bit("Full");
    Layout.Structure s = l.structure("s", e, f, E, F);
    l.layout(s);

    m.copy(t.nodeIndex, 2);
    m.setIndex(m.nodes, t.nodeIndex);
    m.leaf.isEmpty(e);
    m.leaf.isFull (f);
    m.leafMake(m.new NN(t.nodeIndex));                                          // Choose the node
    m.leaf.isEmpty(E);
    m.leaf.isFull (F);
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     4                  6   s
B    0     1                  0     empty     empty
B    1     1                  1     full     full
B    2     1                  1     Empty     Empty
B    3     1                  0     Full     Full
""");
   }

  static void test_branch_is_full_empty()                                       // Test whether a branch is full or emoty
   {TestBranchTree t = new TestBranchTree();                                    // Create a tree of branches
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    Layout           l = new Layout();
    Layout.Bit       e = l.bit("empty");
    Layout.Bit       E = l.bit("Empty");
    Layout.Bit       f = l.bit("full");
    Layout.Bit       F = l.bit("Full");
    Layout.Structure s = l.structure("s", e, f, E, F);
    l.layout(s);

    m.copy(t.nodeIndex, 0);
    m.setIndex(m.nodes, t.nodeIndex);
    m.branchStuck.isEmpty(e);
    m.branchStuck.isFull (f);
    m.branchMake(m.new NN(t.nodeIndex));                                        // Choose the node
    m.branchStuck.isEmpty(E);
    m.branchStuck.isFull (F);
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     4                  6   s
B    0     1                  0     empty     empty
B    1     1                  1     full     full
B    2     1                  1     Empty     Empty
B    3     1                  0     Full     Full
""");
   }

  static void test_leaf_get_put()                                               // Copy a key, data pair from one location in a leaf to another
   {TestLeafTree t = new TestLeafTree();                                        // Create a test tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    m.leafMake(m.new NN(t.nodeIndex));                                          // Allocate a leaf
    m.copy(t.leafIndex, 1);                                                     // Choose the key, data pair
    m.leafGet(m.new NN(t.nodeIndex), m.new LI(t.leafIndex),m.new KeyData(t.kd));// Copy the indexed key, data pair
    m.execute();

    //stop(m.layout);                                                           // State before changes
    m.ok("""
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              73745               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                 17                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 18                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              81939               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                 19                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 20                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24              90133               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 21                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 22                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24              98327               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 23                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 24                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                  0             unary     nodes.node.branchOrLeaf.leaf.unary
""");

    m.reset();
    m.copy(t.leafIndex, 0);                                                     // Update key,data pair at this index
    m.leafPut(m.new NN(t.nodeIndex), m.new LI(t.leafIndex),m.new KeyData(t.kd));// Put the key, data pair
    m.execute();
    //stop(m.layout);
    m.ok("""
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              81939               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                 19                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 20                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              81939               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                 19                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 20                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24              90133               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 21                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 22                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24              98327               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 23                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 24                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                  0             unary     nodes.node.branchOrLeaf.leaf.unary
""");
   }

  static void test_branch_get_put()                                             // Copy a key, data pair from one location in a leaf to another
   {TestBranchTree t = new TestBranchTree();                                    // Create a test tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    m.branchMake(m.new NN(t.nodeIndex));                                        // Allocate a branch
    m.copy                  (t.branchIndex, 1);                                 // Choose the key, next pair
    m.branchGet(m.new NN(t.nodeIndex), m.new BI(t.branchIndex), m.new KeyNext(t.kn));                              // Copy the indexed key, data pair
    m.execute();

    //stop(m.new KeyNext(m.new KeyNext(t.kn)));
    t.kn.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    14               4104   branchKeyNext
V    0    12                  8     branchKey     branchKey
V   12     2                  1     branchNext     branchNext
""");

    //stop(m.layout);
    m.ok("""
S  222    47                              branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14                  7                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                  7                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               4104                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                  8                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14               8201                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                  9                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
""");

    m.reset();
    m.copy(t.branchIndex, 0);                                                   // Update key,next pair at this index
    m.branchPut(m.new NN(t.nodeIndex), m.new BI(t.branchIndex), m.new KeyNext(t.kn));                              // Put the key, next pair
    m.execute();
    //stop(m.layout);
    m.ok("""
S  222    47                              branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14               4104                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                  8                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               4104                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                  8                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14               8201                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                  9                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
""");
   }

  static void test_leaf_insert_remove()                                         // Insert some key, data pairs into a leaf and them remove them
   {TestLeafTree t = new TestLeafTree();                                        // Create a test tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    m.leafMake(m.new NN(t.targetIndex));                                        // Allocate a leaf
    m.leafMake(m.new NN(t.sourceIndex));                                        // Allocate a leaf
    m.copy                     (t.leafIndex, Layout.unary(2));                  // Choose the key, data pair
    m.leafGet   (m.new NN(t.sourceIndex), m.new LI(t.leafIndex), m.new KeyData(t.kd));                             // Copy the indexed key, data pair
    m.copy                     (t.leafIndex, Layout.unary(0));                  // Insert key, data pair at start
    m.leafInsert(m.new NN(t.targetIndex), m.new LI(t.leafIndex), m.new KeyData(t.kd));                             // Insert key, data pair
    m.execute();

    //stop(m.new KeyNext(m.new KeyNext(t.kn)));
    t.kd.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    24              57357   leafKeyData
V    0    12                 13     leafKey     leafKey
V   12    12                 14     leafData     leafData
""");

    //stop(m.layout);
    m.ok("""
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              73745               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                 17                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 18                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24              81939               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 19                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 20                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24              90133               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 21                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 22                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
""");

    m.reset();
    m.copy                     (t.leafIndex, Layout.unary(1));                  // Choose the key, data pair
    m.leafGet   (m.new NN(t.sourceIndex), m.new LI(t.leafIndex), m.new KeyData(t.kd));  // Copy the indexed key, data pair
    m.copy                     (t.leafIndex, Layout.unary(0));                  // Insert key, data pair at start
    m.leafInsert(m.new NN(t.targetIndex), m.new LI(t.leafIndex), m.new KeyData(t.kd));  // Insert key, data pair
    m.execute();

    //stop(m.layout);
    m.ok("""
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              49163               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                 11                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 12                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24              73745               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 17                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 18                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24              81939               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 19                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 20                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                  3             unary     nodes.node.branchOrLeaf.leaf.unary
""");

    m.reset();
    m.copy                     (t.leafIndex, Layout.unary(0));                  // Choose the key, data pair
    m.leafGet   (m.new NN(t.sourceIndex), m.new LI(t.leafIndex), m.new KeyData(t.kd)); // Copy the indexed key, data pair
    m.copy                     (t.leafIndex, Layout.unary(0));                  // Insert key, data pair at start
    m.leafInsert(m.new NN(t.targetIndex), m.new LI(t.leafIndex), m.new KeyData(t.kd)); // Insert key, data pair
    m.execute();

    //stop(m.layout);
    m.ok("""
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              40969               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                  9                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 10                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              49163               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                 11                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 12                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24              73745               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 17                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 18                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                  7             unary     nodes.node.branchOrLeaf.leaf.unary
""");

    m.reset();
    m.copy                     (t.leafIndex, Layout.unary(3));                  // Choose the key, data pair
    m.leafGet   (m.new NN(t.sourceIndex), m.new LI(t.leafIndex), m.new KeyData(t.kd)); // Copy the indexed key, data pair
    m.copy                     (t.leafIndex, Layout.unary(2));                  // Insert key, data pair at start
    m.leafInsert(m.new NN(t.targetIndex), m.new LI(t.leafIndex), m.new KeyData(t.kd)); // Insert key, data pair
    m.execute();

    //stop(m.layout);
    m.ok("""
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              40969               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                  9                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 10                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              49163               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                 11                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 12                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24              65551               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 15                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 16                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                 15             unary     nodes.node.branchOrLeaf.leaf.unary
""");

    m.reset();
    m.copy                     (t.leafIndex, Layout.unary(1));                  // Choose the key, data pair
    m.leafRemove(m.new NN(t.targetIndex),m.new LI(t.leafIndex));                // Remove key, data pair
    m.execute();

    //stop(m.layout);
    m.ok("""
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              40969               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                  9                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 10                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              65551               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                 15                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 16                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                  7             unary     nodes.node.branchOrLeaf.leaf.unary
""");

    m.reset();
    m.copy                     (t.leafIndex, Layout.unary(0));                  // Choose the key, data pair
    m.leafRemove(m.new NN(t.targetIndex),m.new LI(t.leafIndex));                // Remove key, data pair
    m.execute();

    //stop(m.layout);
    m.ok("""
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              65551               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                 15                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 16                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                  3             unary     nodes.node.branchOrLeaf.leaf.unary
""");
   }

  static void test_branch_insert_remove()                                       // Insert some key, next pairs into a branch and them remove them
   {TestBranchTree t = new TestBranchTree();                                    // Create a test tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    m.branchMake(m.new NN(t.targetIndex));                                      // Allocate a branch
    m.branchStuck.unary.ones();                                                 // Fill branch - branchMake will have set an otherwise full branch to empty

    m.branchMake(m.new NN(t.sourceIndex));                                      // Allocate a branch

    m.copy                       (t.branchIndex, Layout.unary(1));              // Choose the key, next pair
    m.branchGet   (m.new NN(t.sourceIndex), m.new BI(t.branchIndex), m.new KeyNext(t.kn));                         // Copy the indexed key, next pair
    m.copy                       (t.branchIndex, Layout.unary(0));              // Insert key, next pair at start
    m.branchInsert(m.new NN(t.targetIndex), m.new BI(t.branchIndex), m.new KeyNext(t.kn));                         // Insert key, next pair
    m.execute();
    //stop(m.new KeyNext(m.new KeyNext(t.kn)));
    t.kn.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    14               4101   branchKeyNext
V    0    12                  5     branchKey     branchKey
V   12     2                  1     branchNext     branchNext
""");

    //stop(m.layout);
    m.ok("""
S  222    47                              branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14               4101                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                  5                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14                  7                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                  7                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14               4104                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                  8                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  7               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
""");

    m.reset();
    m.copy                     (t.branchIndex, Layout.unary(2));                // Choose the key, next pair
    m.branchGet (m.new NN(t.sourceIndex), m.new BI(t.branchIndex), m.new KeyNext(t.kn));                           // Copy the indexed key, next pair
    m.copy                     (t.branchIndex, Layout.unary(0));                // Insert key, next pair at start
    m.branchInsert(m.new NN(t.targetIndex), m.new BI(t.branchIndex), m.new KeyNext(t.kn));                         // Insert key, next pair
    m.execute();

    //stop(m.layout);
    m.ok("""
S  222    47                              branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0  1946247174               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14               8198                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                  6                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1  1946247174               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               4101                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                  5                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2  1946247174               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14                  7                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                  7                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  7               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
""");

    m.reset();
    m.copy                     (t.branchIndex, Layout.unary(0));                // Choose the key, next pair
    m.branchGet   (m.new NN(t.sourceIndex), m.new BI(t.branchIndex), m.new KeyNext(t.kn));                         // Copy the indexed key, next pair
    m.copy                     (t.branchIndex, Layout.unary(0));                // Insert key, next pair at start
    m.branchInsert(m.new NN(t.targetIndex), m.new BI(t.branchIndex), m.new KeyNext(t.kn));                         // Insert key, next pair
    m.execute();

    //stop(m.layout);
    m.ok("""
S  222    47                              branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14                  4                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                  4                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               8198                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                  6                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14               4101                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                  5                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  7               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
""");

    m.reset();
    m.copy                       (t.branchIndex, Layout.unary(1));              // Choose the key, next pair
    m.branchRemove(m.new NN(t.targetIndex), m.new BI(t.branchIndex));           // Remove key, next pair
    m.execute();

    //stop(m.layout);
    m.ok("""
S  222    47                              branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14                  4                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                  4                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               4101                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                  5                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14               4101                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                  5                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  3               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
""");

    m.reset();
    m.copy                     (t.branchIndex, Layout.unary(0));                // Choose the key, next pair
    m.branchRemove(m.new NN(t.targetIndex), m.new BI(t.branchIndex));           // Remove key, next pair
    m.execute();

    //stop(m.layout);
    m.ok("""
S  222    47                              branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14               4101                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                  5                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               4101                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                  5                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14               4101                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                  5                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  1               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
""");
   }

  static void test_leaf_push_leaf()                                             // Push some key, data pairs into a leaf and then shift them
   {TestLeafTree t = new TestLeafTree();                                        // Create a test tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    m.leafMake(m.new NN(t.targetIndex));                                        // Allocate a leaf
    m.leafMake(m.new NN(t.sourceIndex));                                        // Allocate a leaf
    for (int i = 0; i < 4; i++)
     {m.copy                   (t.leafIndex, Layout.unary(i));                  // Choose the key, data pair
      m.leafGet (m.new NN(t.sourceIndex), m.new LI(t.leafIndex), m.new KeyData(t.kd));                             // Copy the indexed key, data pair
      m.leafPush(m.new NN(t.targetIndex), m.new KeyData(t.kd));                 // Push the key, data pair
     }
    m.execute();

    //stop(m.layout);                                                           // Source layout
    m.ok("""
S  120   100                              leaf     nodes.node.branchOrLeaf.leaf
A  120    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  120    24              40969               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  120    12                  9                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  132    12                 10                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  144    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  144    24              49163               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  144    12                 11                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  156    12                 12                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  168    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  168    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  168    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  180    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  192    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  192    24              65551               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  192    12                 15                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  204    12                 16                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
""");
                                                                                // Target layout
    m.ok("""
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              40969               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                  9                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 10                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              49163               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                 11                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 12                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24              65551               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 15                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 16                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                 15             unary     nodes.node.branchOrLeaf.leaf.unary
""");

    m.reset();
    m.leafShift(m.new NN(t.targetIndex), m.new KeyData(t.kd));                  // Shift the key, data pair
    m.execute();

    //stop(m.new KeyData(m.new KeyData(t.kd)));
    t.kd.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    24              40969   leafKeyData
V    0    12                  9     leafKey     leafKey
V   12    12                 10     leafData     leafData
""");

    m.reset();
    m.leafShift(m.new NN(t.targetIndex), m.new KeyData(t.kd));                  // Shift the key, data pair
    m.execute();

    //stop(m.new KeyData(m.new KeyData(t.kd)));
    t.kd.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    24              49163   leafKeyData
V    0    12                 11     leafKey     leafKey
V   12    12                 12     leafData     leafData
""");
   //stop(m.layout);
   m.ok("""
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              65551               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                 15                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 16                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24              65551               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 15                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 16                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24              65551               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 15                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 16                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                  3             unary     nodes.node.branchOrLeaf.leaf.unary
""");
  }

  static void test_branch_push_shift()                                          // Push some key, next pairs into a branch and then shift them
   {TestBranchTree t = new TestBranchTree();                                    // Create a test tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    m.branchMake(m.new NN(t.targetIndex));                                      // Allocate a branch
    m.branchMake(m.new NN(t.sourceIndex));                                      // Allocate a branch

    for (int i = 0; i < 3; i++)
     {m.copy                     (t.branchIndex, Layout.unary(i));              // Choose the key, next pair
      m.branchGet (m.new NN(t.sourceIndex), m.new BI(t.branchIndex), m.new KeyNext(t.kn));                         // Copy the indexed key, next pair
      m.branchPush(m.new NN(t.targetIndex), m.new KeyNext(t.kn));               // Push the key, next pair
     }
    m.execute();

    //stop(m.layout);                                                           // Source layout
    m.ok("""
S  222    47                              branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14                  4                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                  4                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               4101                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                  5                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14               8198                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                  6                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  7               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
""");

    m.reset();
    m.branchShift(m.new NN(t.targetIndex), m.new KeyNext(t.kn));                // Shift the key, data pair
    m.execute();

    //stop(m.new KeyNext(t.kn));
    t.kn.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    14                  4   branchKeyNext
V    0    12                  4     branchKey     branchKey
V   12     2                  0     branchNext     branchNext
""");

    m.reset();
    m.branchShift(m.new NN(t.targetIndex), m.new KeyNext(t.kn));                // Shift the key, data pair
    m.execute();

    //stop(m.new KeyNext(m.new KeyNext(t.kn)));
    t.kn.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    14               4101   branchKeyNext
V    0    12                  5     branchKey     branchKey
V   12     2                  1     branchNext     branchNext
""");

   //stop(m.layout);
   m.ok("""
S  222    47                              branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14               8198                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                  6                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               8198                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                  6                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14               8198                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                  6                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  1               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
""");
  }

  static void test_leaf_indexOf()                                               // Find index of a key in a leaf
   {TestLeafTree t = new TestLeafTree();                                        // Create a test tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    Layout               l = new Layout();
    Layout.Bit       found = l.bit     ("found");
    Layout.Variable    key = l.variable("key",    m.bitsPerKey);
    Layout.Variable result = l.variable("result", m.maxKeysPerLeaf);
    Layout.Structure     s = l.structure("s", found, key, result);
    l.layout(s);

    m.copy(t.sourceIndex, 2);                                                   // Leaf to search
    m.copy(key, 23);                                                            // Key to locate
    m.leafFindIndexOf(m.new NN(t.sourceIndex), m.new Key(key), found, m.new LI(result));                       // Locate key
    m.execute();

    //stop(l);                                                                  // Unary "111" printed as "7" is binary "3"
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    17              57391   s
B    0     1                  1     found     found
V    1    12                 23     key     key
V   13     4                  7     result     result
""");

    m.reset();
    m.copy(key, 25);                                                            // Key will not be found
    m.leafFindIndexOf(m.new NN(t.sourceIndex), m.new Key(key), found, m.new LI(result));
    m.execute();

    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    17              57394   s
B    0     1                  0     found     found
V    1    12                 25     key     key
V   13     4                  7     result     result
""");
  }

  static void test_branch_indexOf()                                             // Push some key, next pairs into a branch and then shift them
   {TestBranchTree t = new TestBranchTree();                                    // Create a test tree
    Mjaf           m = t.mjaf;                                                  // Bit machine to process the tree

    Layout               l = new Layout();
    Layout.Bit       found = l.bit     ("found");
    Layout.Variable result = l.variable("result", m.maxKeysPerBranch);
    Layout.Structure     s = l.structure("s", found, result);
    l.layout(s);

    m.copy(t.sourceIndex, 2);                                                   // Key, data pair
    m.copy(t.branchIndex,   Layout.unary(2));                                   // Address the last node
    m.branchGet(m.new NN(t.sourceIndex), m.new BI(t.branchIndex), m.new KeyNext(t.kn));                            // Copy the indexed key, data pair
    m.branchFindIndexOf(m.new NN(t.sourceIndex), m.new KeyNext(t.kn), found, m.new BI(result));
    m.execute();

    //stop(l);                                                                  // Unary "11" printed as "3" is binary "2"
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     4                  7   s
B    0     1                  1     found     found
V    1     3                  3     result     result
""");

    m.reset();
    final KeyNext key = m.new KeyNext(25, 0);
    m.branchFindIndexOf(m.new NN(t.sourceIndex), key, found, m.new BI(result));
    m.execute();

    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     4                  6   s
B    0     1                  0     found     found
V    1     3                  3     result     result
""");
  }

  static void test_leaf_split_join()                                            // Split a leaf
   {TestLeafTree t = new TestLeafTree();                                        // Create a test tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    m.leafMake (m.new NN(t.sourceIndex));                                       // Allocate a leaf
    m.leaf.unary.ones();                                                        // Fill leaf - leafMake will have set an otherwise full leaf to empty
    m.leafSplit(m.new NN(t.targetIndex), m.new NN(t.sourceIndex));              // Split a leaf
    m.execute();
    //stop(m.layout);                                                           // Source layout
    m.ok("""
S  120   100                              leaf     nodes.node.branchOrLeaf.leaf
A  120    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  120    24              73745               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  120    12                 17                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  132    12                 18                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  144    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  144    24              81939               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  144    12                 19                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  156    12                 20                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  168    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  168    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  168    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  180    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  192    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  192    24              65551               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  192    12                 15                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  204    12                 16                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  216     4                  3             unary     nodes.node.branchOrLeaf.leaf.unary
""");
    m.ok("""
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              90133               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                 21                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 22                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              98327               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                 23                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 24                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24              98327               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 23                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 24                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24              98327               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 23                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 24                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                  3             unary     nodes.node.branchOrLeaf.leaf.unary
""");

    m.reset();
    m.leafJoinable(m.new NN(t.targetIndex), m.new NN(t.sourceIndex), t.le);     // Can the split leaves be joined
    m.leafJoin    (m.new NN(t.targetIndex), m.new NN(t.sourceIndex));           // Join leaves
    m.leafJoinable(m.new NN(t.targetIndex), m.new NN(t.sourceIndex), t.lf);     // Can the joined leaves be joined
    m.execute();
    //stop(m.layout);
    m.ok("""
S  120   100                              leaf     nodes.node.branchOrLeaf.leaf
A  120    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  120    24              73745               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  120    12                 17                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  132    12                 18                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  144    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  144    24              81939               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  144    12                 19                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  156    12                 20                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  168    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  168    24              90133               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  168    12                 21                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  180    12                 22                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  192    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  192    24              98327               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  192    12                 23                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  204    12                 24                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  216     4                 15             unary     nodes.node.branchOrLeaf.leaf.unary
""");
    m.ok("""
S  222   100                  0           leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0           0             array     nodes.node.branchOrLeaf.leaf.array
S  222    24                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1           0             array     nodes.node.branchOrLeaf.leaf.array
S  246    24                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2           0             array     nodes.node.branchOrLeaf.leaf.array
S  270    24                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3           0             array     nodes.node.branchOrLeaf.leaf.array
S  294    24                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                  0             unary     nodes.node.branchOrLeaf.leaf.unary
""");
    //stop(t.lef);
    t.lef.ok("""
T   At  Wide  Index       Value   Field name
S    0     4                  3   lefs
B    0     1                  1     le     le
B    1     1                  1     lf     lf
B    2     1                  0     lE     lE
B    3     1                  0     lF     lF
""");
  }

  static void test_branch_split_join()                                          // Split a branch
   {TestBranchTree t = new TestBranchTree();                                    // Create a test tree
    Mjaf           m = t.mjaf;                                                  // Bit machine to process the tree
    final Key key = m.new Key(m.branchKey.like());                              // Join key

    m.branchMake (m.new NN(t.sourceIndex));                                     // Allocate a  branch

    m.branchSplitKey(m.new NN(t.sourceIndex), m.new KeyNext(t.kn));             // Get the splitting key
    m.copy(key.v,                                                               // Save splitting key
      t.kn.asLayout().get("branchKey").toVariable());

    m.branchStuck.unary.ones();                                                 // Fill branch - branchMake will have set an otherwise full branch to empty
    m.branchSplit(m.new NN(t.targetIndex), m.new NN(t.sourceIndex));            // Split a branch
    m.execute();

    //stop(key);
    key.v.ok("""
T   At  Wide  Index       Value   Field name
V    0    12                  8   branchKey
""");

    //stop(m.layout);                                                           // Source layout
    m.ok("""
S  120    47                              branch     nodes.node.branchOrLeaf.branch
S  120    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  120    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  120    14                  7                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  120    12                  7                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  132     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  134    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  134    14               4101                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  134    12                  5                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  146     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  148    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  148    14               8198                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  148    12                  6                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  160     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  162     3                  1               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V  165     2                  1             topNext     nodes.node.branchOrLeaf.branch.topNext
""");

  m.ok("""
S  222    47                              branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14               8201                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                  9                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               8201                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                  9                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14               8201                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                  9                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  1               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V  267     2                  2             topNext     nodes.node.branchOrLeaf.branch.topNext
""");


    m.reset();
    m.branchJoinable(m.new NN(t.targetIndex),      m.new NN(t.sourceIndex), t.be); // Can the split branches be joined
    m.branchJoin    (m.new NN(t.targetIndex), key, m.new NN(t.sourceIndex));       // Join branches
    m.branchJoinable(m.new NN(t.targetIndex),      m.new NN(t.sourceIndex), t.bf); // Can the joined branches be joined
    m.execute();
    //stop(m.layout);
    m.ok("""
S  120    47                              branch     nodes.node.branchOrLeaf.branch
S  120    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  120    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  120    14                  7                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  120    12                  7                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  132     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  134    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  134    14               8200                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  134    12                  8                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  146     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  148    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  148    14               8201                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  148    12                  9                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  160     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  162     3                  7               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V  165     2                  1             topNext     nodes.node.branchOrLeaf.branch.topNext
""");
    m.ok("""
A  220   306      2                 nodes     nodes
S  220   102                  0       node     nodes.node
B  220     1                  0         isLeaf     nodes.node.isLeaf
B  221     1                  0         isBranch     nodes.node.isBranch
U  222   100                  0         branchOrLeaf     nodes.node.branchOrLeaf
S  222    47                  0           branch     nodes.node.branchOrLeaf.branch
S  222    45                  0             branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0           0               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14                  0                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                  0                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1           0               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14                  0                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                  0                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2           0               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14                  0                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                  0                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  0               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V  267     2                  0             topNext     nodes.node.branchOrLeaf.branch.topNext
""");
    //stop(t.bef);
    t.bef.ok("""
T   At  Wide  Index       Value   Field name
S    0     4                  1   befs
B    0     1                  1     be     be
B    1     1                  0     bf     bf
B    2     1                  0     bE     bE
B    3     1                  0     bF     bF
""");
   }

  static void test_leaf_split_key()                                             // Split key for a leaf
   {TestLeafTree t = new TestLeafTree();                                        // Create a test tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    m.copy(t.nodeIndex, 0);                                                     // Choose the node
    m.leafSplitKey(m.new NN(t.nodeIndex), m.new KeyData(t.kd));                 // Get the splitting key for node 0
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(m.new KeyData(m.new KeyData(t.kd)));
    t.kd.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    24              16387   leafKeyData
V    0    12                  3     leafKey     leafKey
V   12    12                  4     leafData     leafData
""");

    m.copy(t.nodeIndex, 1);                                                     // Choose the node
    m.leafSplitKey(m.new NN(t.nodeIndex), m.new KeyData(t.kd));                 // Get the splitting key for node 0
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(m.new KeyData(m.new KeyData(t.kd)));
    t.kd.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    24              49163   leafKeyData
V    0    12                 11     leafKey     leafKey
V   12    12                 12     leafData     leafData
""");

    m.reset();
    m.copy(t.nodeIndex, 2);                                                     // Choose the node
    m.leafSplitKey(m.new NN(t.nodeIndex), m.new KeyData(t.kd));                 // Get the splitting key for node 0
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(m.new KeyData(m.new KeyData(t.kd)));
    t.kd.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    24              81939   leafKeyData
V    0    12                 19     leafKey     leafKey
V   12    12                 20     leafData     leafData
""");
   }

  static void test_branch_split_key()                                           // Split key for a branch
   {TestBranchTree t = new TestBranchTree();                                    // Create a test tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    m.copy(t.nodeIndex, 0);                                                     // Choose the node
    m.branchSplitKey(m.new NN(t.nodeIndex), m.new KeyNext(t.kn));               // Get the splitting key
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(t.kn.asLayout());
    t.kn.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    14               4098   branchKeyNext
V    0    12                  2     branchKey     branchKey
V   12     2                  1     branchNext     branchNext
""");

    m.copy(t.nodeIndex, 1);                                                     // Choose the node
    m.branchSplitKey(m.new NN(t.nodeIndex), m.new KeyNext(t.kn));               // Get the splitting key
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(m.new KeyNext(m.new KeyNext(t.kn)));
    t.kn.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    14               4101   branchKeyNext
V    0    12                  5     branchKey     branchKey
V   12     2                  1     branchNext     branchNext
""");

    m.reset();
    m.copy(t.nodeIndex, 2);                                                     // Choose the node
    m.branchSplitKey(m.new NN(t.nodeIndex), m.new KeyNext(t.kn));               // Get the splitting key
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(m.new KeyNext(m.new KeyNext(t.kn)));
    t.kn.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    14               4104   branchKeyNext
V    0    12                  8     branchKey     branchKey
V   12     2                  1     branchNext     branchNext
""");
   }

  static void test_leaf_emptyFull()                                             // Leaf empty or full
   {TestLeafTree t = new TestLeafTree();                                        // Create a test tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    m.leafMake   (m.new NN(t.nodeIndex));
    m.leafIsEmpty(m.new NN(t.nodeIndex), t.le);
    m.leafIsFull (m.new NN(t.nodeIndex), t.lf);
    m.leaf.unary.ones();
    m.leafIsEmpty(m.new NN(t.nodeIndex), t.lE);
    m.leafIsFull (m.new NN(t.nodeIndex), t.lF);
    m.execute();
    //stop(t.lef);
    t.lef.ok("""
T   At  Wide  Index       Value   Field name
S    0     4                  9   lefs
B    0     1                  1     le     le
B    1     1                  0     lf     lf
B    2     1                  0     lE     lE
B    3     1                  1     lF     lF
""");
   }

  static void test_branch_emptyFull()                                           // Branch empty or full
   {TestBranchTree t = new TestBranchTree();                                    // Create a test tree
    Mjaf           m = t.mjaf;                                                  // Bit machine to process the tree

    m.branchMake   (m.new NN(t.nodeIndex));
    m.branchIsEmpty(m.new NN(t.nodeIndex), t.be);
    m.branchIsFull (m.new NN(t.nodeIndex), t.bf);
    m.branchStuck.unary.ones();
    m.branchIsEmpty(m.new NN(t.nodeIndex), t.bE);
    m.branchIsFull (m.new NN(t.nodeIndex), t.bF);
    m.execute();
    //stop(t.bef);
    t.bef.ok("""
T   At  Wide  Index       Value   Field name
S    0     4                  9   befs
B    0     1                  1     be     be
B    1     1                  0     bf     bf
B    2     1                  0     bE     bE
B    3     1                  1     bF     bF
""");
   }

  static void test_leaf_greater_than_or_equal()                                 // Find index of first key in aa leaf greater than or equal to a test key
   {TestLeafTree    t = new TestLeafTree();                                     // Create a test tree
    Mjaf            m = t.mjaf;                                                 // Bit machine to process the tree
    Layout.Variable k = m.leafKey.like();                                       // Key to serach for
    Layout.Variable i = m.leaf.unary.value.like();                              // Index of key sought
    Layout.Bit      f = Layout.createBit("found");                              // Whether such a key was found or not

    m.copy(t.nodeIndex, 0);
    m.copy(k,           2);
    m.leafFirstGreaterThanOrEqual(m.new NN(t.nodeIndex), m.new Key(k), m.new LI(i), f);
    m.execute();
    //stop(m.layout);
    m.ok("""
A   18    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S   18    24               8193               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   18    12                  1                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   30    12                  2                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   42    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S   42    24              16387               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   42    12                  3                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   54    12                  4                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
""");

    //stop(k, i, f);
    k.ok("""
T   At  Wide  Index       Value   Field name
V    0    12                  2   leafKey
""");
    i.ok("""
T   At  Wide  Index       Value   Field name
V    0     4                  1   unary
""");
    f.ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  1   found
""");
   }

  static void test_branch_greater_than_or_equal()                               // Find next node associated with  the first key greater than or equal to the search key
   {TestBranchTree  t = new TestBranchTree();                                   // Create a test tree
    Mjaf            m = t.mjaf;                                                 // Bit machine to process the tree
    Key             k = m.new Key(8);                                          // Key to locate
    NN              n = m.new NN (0);                                           // Located next node index

    m.copy(t.nodeIndex, 2);
    m.branchFindFirstGreaterOrEqual(m.new NN(t.nodeIndex), k, n);
    m.execute();
    //stop(k, n);
    n.v.ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  1   next
""");
   }

  static void test_from_keyDataNext()                                           // Get the key and data from a key, data pair or the key and next from a key, next pair
   {TestLeafTree t = new TestLeafTree();                                        // Allocate a new leaf treetree tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    KeyData kd = m.new KeyData(6, 7);
    KeyNext kn = m.new KeyNext(6, 3);
    Key     Kd = kd.key();
    Data    kD = kd.data();
    Key     Kn = kn.key();
    NN      kN = kn.next();
    m.execute();

    //stop(Kd, kD, Kn, kN);
    Kd.v.copy().ok("""
T   At  Wide  Index       Value   Field name
V    0    12                  6     leafKey
""");
    kD.v.copy().ok("""
T   At  Wide  Index       Value   Field name
V   12    12                  7     leafData
""");
    Kn.v.copy().ok("""
T   At  Wide  Index       Value   Field name
V    0    12                  6     branchKey
""");
    kN.v.copy().ok("""
T   At  Wide  Index       Value   Field name
V   12     2                  3     branchNext
""");
   }

  static void test_branch_greater_than_or_equal_index()                         // Find index of first key greater than or equal to the search key
   {TestBranchTree  t = new TestBranchTree();                                   // Create a test tree
    Mjaf            m = t.mjaf;                                                 // Bit machine to process the tree

    //stop(m.layout);
    m.ok("""
S  222    47                              branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14                  7                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                  7                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               4104                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                  8                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14               8201                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                  9                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  7               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
""");

    m.copy(t.nodeIndex, 2);
    Key k = m.new Key (6);                                                       // Key to locate
    BI  i = m.branchFindFirstGreaterOrEqualIndex(m.new NN(t.nodeIndex), k);     // Find index of key
    m.execute();
    //stop(i);
    i.v.ok("""
T   At  Wide  Index       Value   Field name
V    0     3                  0   branchIndex
""");

    m.reset();
    m.copy(k.v, 7);
    i = m.branchFindFirstGreaterOrEqualIndex(m.new NN(t.nodeIndex), k);         // Find index of key
    m.execute();
    //stop(i);
    i.v.ok("""
T   At  Wide  Index       Value   Field name
V    0     3                  0   branchIndex
""");

    m.reset();
    m.copy(k.v, 8);
    i = m.branchFindFirstGreaterOrEqualIndex(m.new NN(t.nodeIndex), k);         // Find index of key
    m.execute();
    //stop(i);
    i.v.ok("""
T   At  Wide  Index       Value   Field name
V    0     3                  1   branchIndex
""");
   }

  static void test_branch_top_next()                                            // Get and set the top next field in a branch
   {TestBranchTree  t = new TestBranchTree();                                   // Create a test tree
    Mjaf            m = t.mjaf;                                                 // Bit machine to process the tree
    NN              n = m.new NN(0);                                              // Located next node index

    m.copy(t.nodeIndex, 2);
    m.branchGetTopNext(m.new NN(t.nodeIndex), n);
    m.execute();
    //stop(n);
    n.v.ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  2   next
""");

    m.reset();
    m.copy(n.v, 1);
    m.branchSetTopNext(m.new NN(t.nodeIndex), n);
    m.execute();
    //stop(m.layout);
    m.ok("""
V  267     2                  1             topNext     nodes.node.branchOrLeaf.branch.topNext
""");
   }

  static void test_leaf_split_root()                                            // Split the root when it is a leaf
   {TestLeafTree  t = new TestLeafTree();                                       // Create a test tree
    Mjaf          m = t.mjaf;                                                   // Bit machine to process the tree

    //stop(m.layout);
    m.ok("""
S   18   100                              leaf     nodes.node.branchOrLeaf.leaf
A   18    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S   18    24               8193               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   18    12                  1                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   30    12                  2                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   42    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S   42    24              16387               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   42    12                  3                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   54    12                  4                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   66    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S   66    24              24581               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   66    12                  5                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   78    12                  6                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   90    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S   90    24              32775               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   90    12                  7                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  102    12                  8                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  114     4                 15             unary     nodes.node.branchOrLeaf.leaf.unary
""");

    m.leafSplitRoot(m.new NN(t.sourceIndex), m.new NN(t.targetIndex));
    m.execute();

    t.sourceIndex.ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  1   nodeFree
""");
    t.targetIndex.ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  2   nodeFree
""");

    //stop(m.layout);
    m.ok("""
A   16   306      0                 nodes     nodes
S   16   102                          node     nodes.node
B   16     1                  0         isLeaf     nodes.node.isLeaf
B   17     1                  1         isBranch     nodes.node.isBranch
U   18   100                            branchOrLeaf     nodes.node.branchOrLeaf
S   18    47                              branch     nodes.node.branchOrLeaf.branch
S   18    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A   18    42      0        4099               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   18    14               4099                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   18    12                  3                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   30     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   32    42      1        4099               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   32    14                  0                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   32    12                  0                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   44     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   46    42      2        4099               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   46    14                  0                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   46    12                  0                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   58     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V   60     3                  1               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V   63     2                  2             topNext     nodes.node.branchOrLeaf.branch.topNext
""");
    m.ok("""
S  120   100                              leaf     nodes.node.branchOrLeaf.leaf
A  120    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  120    24               8193               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  120    12                  1                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  132    12                  2                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  144    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  144    24              16387               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  144    12                  3                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  156    12                  4                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  168    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  168    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  168    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  180    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  192    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  192    24              65551               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  192    12                 15                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  204    12                 16                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  216     4                  3             unary     nodes.node.branchOrLeaf.leaf.unary
""");
    m.ok("""
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              24581               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                  5                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                  6                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              32775               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                  7                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                  8                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24              90133               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 21                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 22                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24              98327               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 23                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 24                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                  3             unary     nodes.node.branchOrLeaf.leaf.unary
""");
   }

  static void test_branch_split_root()                                          // Split the root when it is a branch
   {TestBranchTree  t = new TestBranchTree();                                   // Create a test tree
    Mjaf            m = t.mjaf;                                                 // Bit machine to process the tree

    //stop(m.layout);
    m.ok("""
S   16   102                          node     nodes.node
B   16     1                  0         isLeaf     nodes.node.isLeaf
B   17     1                  1         isBranch     nodes.node.isBranch
U   18   100                            branchOrLeaf     nodes.node.branchOrLeaf
S   18    47                              branch     nodes.node.branchOrLeaf.branch
S   18    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A   18    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   18    14                  1                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   18    12                  1                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   30     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   32    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   32    14               4098                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   32    12                  2                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   44     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   46    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   46    14               8195                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   46    12                  3                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   58     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V   60     3                  7               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V   63     2                  0             topNext     nodes.node.branchOrLeaf.branch.topNext
""");

    m.branchSplitRoot(m.new NN(t.sourceIndex), m.new NN(t.targetIndex));
    m.execute();

    t.sourceIndex.ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  1   nodeFree
""");
    t.targetIndex.ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  2   nodeFree
""");
    //stop(m.layout);
    m.ok("""
S   18    47                              branch     nodes.node.branchOrLeaf.branch
S   18    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A   18    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   18    14               4098                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   18    12                  2                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   30     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   32    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   32    14               8195                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   32    12                  3                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   44     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   46    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   46    14               8195                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   46    12                  3                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   58     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V   60     3                  1               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V   63     2                  2             topNext     nodes.node.branchOrLeaf.branch.topNext
""");
    m.ok("""
S  120    47                              branch     nodes.node.branchOrLeaf.branch
S  120    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  120    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  120    14                  1                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  120    12                  1                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  132     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  134    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  134    14               4101                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  134    12                  5                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  146     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  148    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  148    14               8198                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  148    12                  6                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  160     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  162     3                  1               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V  165     2                  1             topNext     nodes.node.branchOrLeaf.branch.topNext
""");
    m.ok("""
A  220   306      2                 nodes     nodes
S  220   102                          node     nodes.node
B  220     1                  0         isLeaf     nodes.node.isLeaf
B  221     1                  1         isBranch     nodes.node.isBranch
U  222   100                            branchOrLeaf     nodes.node.branchOrLeaf
S  222    47                              branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14               8195                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                  3                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               4104                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                  8                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14               8201                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                  9                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  1               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V  267     2                  0             topNext     nodes.node.branchOrLeaf.branch.topNext
""");
   }

  static void test_find()                                                       // Test find
   {TestLeafTree t = new TestLeafTree();                                        // Create a test tree
    Mjaf         m = t.mjaf;                                                    // Bit machine to process the tree

    Layout               l = new Layout();
    Layout.Bit       found = l.bit     ("found");
    Layout.Variable    key = l.variable("key",  m.bitsPerKey);
    Layout.Variable   data = l.variable("data", m.bitsPerData);
    Layout.Structure     s = l.structure("s", found, key, data);
    l.layout(s);


    m.ok("""
V   42    12                  3                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   54    12                  4                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
""");

    m.copy(key, 2);                                                             // Key to locate
    m.find(m.new Key(key), found, m.new Data(data));
    m.execute();
//  stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    25                  4   s
B    0     1                  0     found     found
V    1    12                  2     key     key
V   13    12                  0     data     data
""");

    m.reset();
    m.copy(key, 3);                                                             // Key to locate
    m.find(m.new Key(key), found, m.new Data(data));
    m.execute();
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    25              32775   s
B    0     1                  1     found     found
V    1    12                  3     key     key
V   13    12                  4     data     data
""");

    m.reset();
    m.leafSplitRoot(m.new NN(t.sourceIndex), m.new NN(t.targetIndex));          // Create a branch and two leaves
    m.execute();

    m.reset();
    m.copy(key, 1);                                                             // Key to locate in left leaf
    m.find(m.new Key(key), found, m.new Data(data));
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    25              16387   s
B    0     1                  1     found     found
V    1    12                  1     key     key
V   13    12                  2     data     data
""");

//    m.reset();
//    m.copy(key, 7);                                                           // Key to locate in right leaf
//    m.find(m.new Key(key), found, m.new Data(data));
//    m.execute();
//   //stop(l);
//    l.ok("""
//T   At  Wide  Index       Value   Field name
//S    0    25              65551   s
//B    0     1                  1     found     found
//V    1    12                  7     key     key
//V   13    12                  8     data     data
//""");
   }

  static void test_find_and_insert()                                            // Test find
   {TestLeafTree t = new TestLeafTree();                                        // Create a test tree
    Mjaf         m = t.mjaf;                                                    // Bit machine to process the tree

    Layout               l = new Layout();
    Layout.Bit       found = l.bit      ("found");
    Layout.Variable    key = l.variable ("key",  m.bitsPerKey);
    Layout.Variable   data = l.variable ("data", m.bitsPerData);
    Layout.Structure     s = l.structure("s", found, key, data);
    l.layout(s);

    m.reset();
    m.leafSplitRoot(m.new NN(t.sourceIndex), m.new NN(t.targetIndex));          // Create a branch and two leaves
    m.execute();

    m.reset();
    m.copy(key,   2);                                                           // Key to add
    m.copy(data, 22);                                                           // Data to add
    m.findAndInsert(m.new Key(key), m.new Data(data), found);
    m.execute();
    //stop(m.layout);
    m.ok("""
S  120   100                              leaf     nodes.node.branchOrLeaf.leaf
A  120    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  120    24               8193               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  120    12                  1                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  132    12                  2                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  144    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  144    24              90114               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  144    12                  2                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  156    12                 22                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  168    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  168    24              16387               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  168    12                  3                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  180    12                  4                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  192    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  192    24              57357               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  192    12                 13                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  204    12                 14                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  216     4                  7             unary     nodes.node.branchOrLeaf.leaf.unary
""");
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    25             180229   s
B    0     1                  1     found     found
V    1    12                  2     key     key
V   13    12                 22     data     data
""");
   }

  static void test_root_is_leaf_or_branch()                                     // Test root is leaf
   {TestLeafTree     t = new TestLeafTree();                                    // Create a test tree
    Mjaf             m = t.mjaf;                                                // Bit machine to process the tree

    Layout           l = new Layout();
    Layout.Bit    leaf = l.bit("leaf");
    Layout.Bit  branch = l.bit("branch");
    Layout.Structure s = l.structure("s", leaf, branch);
    l.layout(s);

    m.rootIsLeaf  (leaf);                                                       // Check whether the root is a leaf
    m.rootIsBranch(branch);                                                     // Check whether the root is a branch
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     2                  1   s
B    0     1                  1     leaf     leaf
B    1     1                  0     branch     branch
""");

    //stop(m.layout);
    m.ok("""
A   16   306      0                 nodes     nodes
S   16   102                          node     nodes.node
B   16     1                  1         isLeaf     nodes.node.isLeaf
B   17     1                  0         isBranch     nodes.node.isBranch
""");

    m.reset();
    m.leafSplitRoot(m.new NN(t.sourceIndex), m.new NN(t.targetIndex));
    m.rootIsLeaf  (leaf);                                                       // Check whether the root is a leaf
    m.rootIsBranch(branch);                                                     // Check whether the root is a branch
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     2                  2   s
B    0     1                  0     leaf     leaf
B    1     1                  1     branch     branch
""");

    //stop(m.layout);
    m.ok("""
A   16   306      0                 nodes     nodes
S   16   102                          node     nodes.node
B   16     1                  0         isLeaf     nodes.node.isLeaf
B   17     1                  1         isBranch     nodes.node.isBranch
""");
   }

  static void test_leaf_insert_pair()                                           // Insert a key and the corresponding data into a leaf at the correct position
   {TestLeafTree     t = new TestLeafTree();                                    // Create a test tree
    Mjaf             m = t.mjaf;                                                // Bit machine to process the tree

    //stop(m.layout);
    m.ok("""
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              73745               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                 17                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 18                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              81939               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                 19                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 20                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24              90133               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 21                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 22                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24              98327               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 23                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 24                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                 15             unary     nodes.node.branchOrLeaf.leaf.unary
""");

    Key  key  = m.new Key (6);
    Data data = m.new Data(6);
    m.leafSplitRoot (m.new NN(t.sourceIndex), m.new NN(t.targetIndex));
    m.leafInsertPair(m.new NN(t.targetIndex), key, data);
    m.execute();

    //stop(m.layout);
    m.ok("""
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              24581               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                  5                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                  6                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              24582               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                  6                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                  6                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24              32775               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                  7                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                  8                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24              90133               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 21                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 22                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                  7             unary     nodes.node.branchOrLeaf.leaf.unary
""");
   }

  static void test_leaf_root_is_full()                                          // Test whether the root as a leaf is full
   {TestLeafTree     t = new TestLeafTree();                                    // Create a test tree
    Mjaf             m = t.mjaf;                                                // Bit machine to process the tree

    Layout.Bit f = m.leafRootIsFull();
    m.branchSplitRoot();
    Layout.Bit F = m.branchRootIsFull();
    m.execute();
    //stop(f, F);
    f.copy().ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  1   leafRootIsFull
""");
    F.copy().ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  0   branchRootIsFull
""");
   }

  static void test_branch_root_is_full()                                        // Test whether the root as a branch is full
   {TestBranchTree   t = new TestBranchTree();                                  // Create a test tree
    Mjaf             m = t.mjaf;                                                // Bit machine to process the tree

    Layout.Bit f = m.branchRootIsFull();
    m.branchSplitRoot();
    Layout.Bit F = m.branchRootIsFull();
    m.execute();
    //stop(f, F);
    f.copy().ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  1   branchRootIsFull
""");
    F.copy().ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  0   branchRootIsFull
""");
   }

  static void test_leaf_fission()                                               // Split a leaf and insert the split results into the parent branch
   {TestLeafTree     t = new TestLeafTree();                                    // Create a test tree
    Mjaf             m = t.mjaf;                                                // Bit machine to process the tree

    NN l = m.leafMake();
    NN b = m.branchMake();

    m.leafPush(l, m.new KeyData(10, 11));
    m.leafPush(l, m.new KeyData(20, 22));
    m.leafPush(l, m.new KeyData(30, 33));
    m.leafPush(l, m.new KeyData(40, 44));

    m.branchPush(b, m.new KeyNext(25, 0));
    m.branchPush(b, m.new KeyNext(m.new Key (45), l));
    m.execute();
    //stop(b, l);

    b.v.copy().ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  1   branch
""");

    l.v.copy().ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  2   leaf
""");

    //stop(m.layout);

    m.ok("""
A  118   306      1                 nodes     nodes
S  118   102                          node     nodes.node
B  118     1                  0         isLeaf     nodes.node.isLeaf
B  119     1                  1         isBranch     nodes.node.isBranch
U  120   100                            branchOrLeaf     nodes.node.branchOrLeaf
S  120    47                              branch     nodes.node.branchOrLeaf.branch
S  120    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  120    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  120    14                 25                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  120    12                 25                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  132     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  134    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  134    14               8237                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  134    12                 45                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  146     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  148    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  148    14               3072                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  148    12               3072                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  160     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  162     3                  3               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V  165     2                  0             topNext     nodes.node.branchOrLeaf.branch.topNext
""");

    m.ok("""
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              45066               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                 10                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 11                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24              90132               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                 20                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 22                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24             135198               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 30                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 33                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24             180264               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 40                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 44                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                 15             unary     nodes.node.branchOrLeaf.leaf.unary
""");

    m.reset();
    m.leafFission(b, l);
    m.execute();

    //stop(m.layout);
    m.ok("""
A  118   306      1                 nodes     nodes
S  118   102                          node     nodes.node
B  118     1                  0         isLeaf     nodes.node.isLeaf
B  119     1                  1         isBranch     nodes.node.isBranch
U  120   100                            branchOrLeaf     nodes.node.branchOrLeaf
S  120    47                              branch     nodes.node.branchOrLeaf.branch
S  120    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  120    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  120    14                 20                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  120    12                 20                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  132     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  134    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  134    14                 25                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  134    12                 25                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  146     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  148    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  148    14               8237                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  148    12                 45                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  160     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  162     3                  7               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V  165     2                  0             topNext     nodes.node.branchOrLeaf.branch.topNext
A  220   306      2                 nodes     nodes
S  220   102                          node     nodes.node
B  220     1                  1         isLeaf     nodes.node.isLeaf
B  221     1                  0         isBranch     nodes.node.isBranch
U  222   100                            branchOrLeaf     nodes.node.branchOrLeaf
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24             135198               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                 30                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 33                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24             180264               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                 40                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 44                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  270    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S  270    24             180264               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  270    12                 40                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  282    12                 44                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  294    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S  294    24             180264               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  294    12                 40                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  306    12                 44                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  318     4                  3             unary     nodes.node.branchOrLeaf.leaf.unary
""");
   }

  static void test_branch_fission()                                             // Split a branch and insert the split results into the parent branch
   {TestBranchTree  t = new TestBranchTree();                                   // Create a test tree
    Mjaf            m = t.mjaf;                                                 // Bit machine to process the tree

    NN b = m.branchMake();
    NN B = m.branchMake();

    m.branchPush(b, m.new KeyNext(10, 0));
    m.branchPush(b, m.new KeyNext(20, 1));
    m.branchPush(b, m.new KeyNext(30, 2));

    m.branchPush(B, m.new KeyNext(15, 0));
    m.branchPush(B, m.new KeyNext(m.new Key(35), b));
    m.execute();
    //stop(b, B);

    b.v.copy().ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  2   branch
""");

    B.v.copy().ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  1   branch
""");

    //stop(m.layout);

    m.ok("""
S  120    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  120    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  120    14                 15                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  120    12                 15                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  132     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  134    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  134    14               8227                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  134    12                 35                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  146     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  148    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  148    14               8198                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  148    12                  6                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  160     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  162     3                  3               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
""");

    m.ok("""
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14                 10                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                 10                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               4116                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                 20                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14               8222                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                 30                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  7               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
""");

    m.reset();
    m.branchFission(B, b);
    m.execute();

   // stop(m.layout);
    m.ok("""
T   At  Wide  Index       Value   Field name
A  118   306      1                 nodes     nodes
S  118   102                          node     nodes.node
B  118     1                  0         isLeaf     nodes.node.isLeaf
B  119     1                  1         isBranch     nodes.node.isBranch
U  120   100                            branchOrLeaf     nodes.node.branchOrLeaf
S  120    47                              branch     nodes.node.branchOrLeaf.branch
S  120    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  120    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  120    14                 15                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  120    12                 15                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  132     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  134    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  134    14                 20                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  134    12                 20                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  146     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  148    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  148    14               8227                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  148    12                 35                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  160     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  162     3                  7               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V  165     2                  1             topNext     nodes.node.branchOrLeaf.branch.topNext
A  220   306      2                 nodes     nodes
S  220   102                          node     nodes.node
B  220     1                  0         isLeaf     nodes.node.isLeaf
B  221     1                  1         isBranch     nodes.node.isBranch
U  222   100                            branchOrLeaf     nodes.node.branchOrLeaf
S  222    47                              branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14               8222                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                 30                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               8222                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                 30                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14               8222                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12                 30                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  1               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
""");
   }

  static void test_put()                                                        // Load a BTree
   {final int BitsPerKey = 8, BitsPerData = 8, MaxKeysPerLeaf = 4, size = 4;    // Dimensions of BTree
    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);         // Create BTree

    m.put(m.new Key (1), m.new Data(11));
    m.put(m.new Key (2), m.new Data(22));
    m.put(m.new Key (3), m.new Data(33));
    m.put(m.new Key (4), m.new Data(44));
    m.execute();

    //stop(m.layout);

    m.ok("""
T   At  Wide  Index       Value   Field name
S   21    68                              leaf     nodes.node.branchOrLeaf.leaf
A   21    64      0                         array     nodes.node.branchOrLeaf.leaf.array
S   21    16               2817               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   21     8                  1                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   29     8                 11                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   37    64      1                         array     nodes.node.branchOrLeaf.leaf.array
S   37    16               5634               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   37     8                  2                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   45     8                 22                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   53    64      2                         array     nodes.node.branchOrLeaf.leaf.array
S   53    16               8451               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   53     8                  3                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   61     8                 33                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   69    64      3                         array     nodes.node.branchOrLeaf.leaf.array
S   69    16              11268               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   69     8                  4                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   77     8                 44                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V   85     4                 15             unary     nodes.node.branchOrLeaf.leaf.unary
""");

    m.reset();
    m.put(m.new Key (5), m.new Data(55));                                        // Split leaf root
    m.execute();

    //stop(m.layout);

    m.ok("""
T   At  Wide  Index       Value   Field name
A   19   280      0                 nodes     nodes
S   19    70                          node     nodes.node
B   19     1                  0         isLeaf     nodes.node.isLeaf
B   20     1                  1         isBranch     nodes.node.isBranch
U   21    68                            branchOrLeaf     nodes.node.branchOrLeaf
S   21    35                              branch     nodes.node.branchOrLeaf.branch
S   21    33         1073742338             branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A   21    30      0         514               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   21    10                514                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   21     8                  2                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   29     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   31    30      1         514               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   31    10                  0                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   31     8                  0                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   39     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   41    30      2         514               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   41    10                  0                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   41     8                  0                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   49     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V   51     3                  1               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V   54     2                  3             topNext     nodes.node.branchOrLeaf.branch.topNext
A   89   280      1                 nodes     nodes
S   89    70                  0       node     nodes.node
B   89     1                  0         isLeaf     nodes.node.isLeaf
B   90     1                  0         isBranch     nodes.node.isBranch
U   91    68                  0         branchOrLeaf     nodes.node.branchOrLeaf
S   91    35                  0           branch     nodes.node.branchOrLeaf.branch
S   91    33                  0             branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A   91    30      0           0               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   91    10                  0                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   91     8                  0                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   99     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  101    30      1           0               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  101    10                  0                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  101     8                  0                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  109     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  111    30      2           0               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  111    10                  0                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  111     8                  0                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  119     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  121     3                  0               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V  124     2                  0             topNext     nodes.node.branchOrLeaf.branch.topNext
S   91    68                  0           leaf     nodes.node.branchOrLeaf.leaf
A   91    64      0           0             array     nodes.node.branchOrLeaf.leaf.array
S   91    16                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   91     8                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   99     8                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  107    64      1           0             array     nodes.node.branchOrLeaf.leaf.array
S  107    16                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  107     8                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  115     8                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  123    64      2           0             array     nodes.node.branchOrLeaf.leaf.array
S  123    16                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  123     8                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  131     8                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  139    64      3           0             array     nodes.node.branchOrLeaf.leaf.array
S  139    16                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  139     8                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  147     8                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  155     4                  0             unary     nodes.node.branchOrLeaf.leaf.unary
A  159   280      2                 nodes     nodes
S  159    70                          node     nodes.node
B  159     1                  1         isLeaf     nodes.node.isLeaf
B  160     1                  0         isBranch     nodes.node.isBranch
U  161    68                            branchOrLeaf     nodes.node.branchOrLeaf
S  161    68                              leaf     nodes.node.branchOrLeaf.leaf
A  161    64      0   369232641             array     nodes.node.branchOrLeaf.leaf.array
S  161    16               2817               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  161     8                  1                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  169     8                 11                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  177    64      1   369232641             array     nodes.node.branchOrLeaf.leaf.array
S  177    16               5634               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  177     8                  2                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  185     8                 22                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  193    64      2   369232641             array     nodes.node.branchOrLeaf.leaf.array
S  193    16                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  193     8                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  201     8                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  209    64      3   369232641             array     nodes.node.branchOrLeaf.leaf.array
S  209    16                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  209     8                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  217     8                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  225     4                  3             unary     nodes.node.branchOrLeaf.leaf.unary
A  229   280      3                 nodes     nodes
S  229    70                          node     nodes.node
B  229     1                  1         isLeaf     nodes.node.isLeaf
B  230     1                  0         isBranch     nodes.node.isBranch
U  231    68                            branchOrLeaf     nodes.node.branchOrLeaf
S  231    68                              leaf     nodes.node.branchOrLeaf.leaf
A  231    64      0                         array     nodes.node.branchOrLeaf.leaf.array
S  231    16               8451               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  231     8                  3                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  239     8                 33                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  247    64      1                         array     nodes.node.branchOrLeaf.leaf.array
S  247    16              11268               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  247     8                  4                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  255     8                 44                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  263    64      2                         array     nodes.node.branchOrLeaf.leaf.array
S  263    16              14085               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  263     8                  5                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  271     8                 55                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  279    64      3                         array     nodes.node.branchOrLeaf.leaf.array
S  279    16                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  279     8                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  287     8                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  295     4                  7             unary     nodes.node.branchOrLeaf.leaf.unary
""");

    m.reset();
    m.put(m.new Key (6), m.new Data(66));
    m.execute();

    //stop(m.layout);

    m.ok("""
T   At  Wide  Index       Value   Field name
S  231    68                              leaf     nodes.node.branchOrLeaf.leaf
A  231    64      0                         array     nodes.node.branchOrLeaf.leaf.array
S  231    16               8451               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  231     8                  3                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  239     8                 33                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  247    64      1                         array     nodes.node.branchOrLeaf.leaf.array
S  247    16              11268               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  247     8                  4                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  255     8                 44                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  263    64      2                         array     nodes.node.branchOrLeaf.leaf.array
S  263    16              14085               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  263     8                  5                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  271     8                 55                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  279    64      3                         array     nodes.node.branchOrLeaf.leaf.array
S  279    16              16902               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  279     8                  6                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  287     8                 66                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  295     4                 15             unary     nodes.node.branchOrLeaf.leaf.unary
""");

    m.reset();
    m.put(m.new Key (7), m.new Data(77));
    m.put(m.new Key (8), m.new Data(88));
    m.execute();

    //stop(m.layout);
    m.ok("""
T   At  Wide  Index       Value   Field name
A   19   280      0                 nodes     nodes
S   19    70                          node     nodes.node
B   19     1                  0         isLeaf     nodes.node.isLeaf
B   20     1                  1         isBranch     nodes.node.isBranch
U   21    68                            branchOrLeaf     nodes.node.branchOrLeaf
S   21    35                              branch     nodes.node.branchOrLeaf.branch
S   21    33         -1073475070             branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A   21    30      0      266754               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   21    10                514                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   21     8                  2                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   29     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   31    30      1      266754               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   31    10                260                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   31     8                  4                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   39     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   41    30      2      266754               array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   41    10                  0                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   41     8                  0                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   49     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V   51     3                  3               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V   54     2                  3             topNext     nodes.node.branchOrLeaf.branch.topNext
A   89   280      1                 nodes     nodes
S   89    70                          node     nodes.node
B   89     1                  1         isLeaf     nodes.node.isLeaf
B   90     1                  0         isBranch     nodes.node.isBranch
U   91    68                            branchOrLeaf     nodes.node.branchOrLeaf
S   91    68                              leaf     nodes.node.branchOrLeaf.leaf
A   91    64      0   738468099             array     nodes.node.branchOrLeaf.leaf.array
S   91    16               8451               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   91     8                  3                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   99     8                 33                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  107    64      1   738468099             array     nodes.node.branchOrLeaf.leaf.array
S  107    16              11268               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  107     8                  4                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  115     8                 44                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  123    64      2   738468099             array     nodes.node.branchOrLeaf.leaf.array
S  123    16                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  123     8                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  131     8                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  139    64      3   738468099             array     nodes.node.branchOrLeaf.leaf.array
S  139    16                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  139     8                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  147     8                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  155     4                  3             unary     nodes.node.branchOrLeaf.leaf.unary
A  159   280      2                 nodes     nodes
S  159    70                          node     nodes.node
B  159     1                  1         isLeaf     nodes.node.isLeaf
B  160     1                  0         isBranch     nodes.node.isBranch
U  161    68                            branchOrLeaf     nodes.node.branchOrLeaf
S  161    68                              leaf     nodes.node.branchOrLeaf.leaf
A  161    64      0   369232641             array     nodes.node.branchOrLeaf.leaf.array
S  161    16               2817               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  161     8                  1                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  169     8                 11                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  177    64      1   369232641             array     nodes.node.branchOrLeaf.leaf.array
S  177    16               5634               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  177     8                  2                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  185     8                 22                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  193    64      2   369232641             array     nodes.node.branchOrLeaf.leaf.array
S  193    16                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  193     8                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  201     8                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  209    64      3   369232641             array     nodes.node.branchOrLeaf.leaf.array
S  209    16                  0               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  209     8                  0                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  217     8                  0                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  225     4                  3             unary     nodes.node.branchOrLeaf.leaf.unary
A  229   280      3                 nodes     nodes
S  229    70                          node     nodes.node
B  229     1                  1         isLeaf     nodes.node.isLeaf
B  230     1                  0         isBranch     nodes.node.isBranch
U  231    68                            branchOrLeaf     nodes.node.branchOrLeaf
S  231    68                              leaf     nodes.node.branchOrLeaf.leaf
A  231    64      0                         array     nodes.node.branchOrLeaf.leaf.array
S  231    16              14085               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  231     8                  5                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  239     8                 55                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  247    64      1                         array     nodes.node.branchOrLeaf.leaf.array
S  247    16              16902               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  247     8                  6                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  255     8                 66                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  263    64      2                         array     nodes.node.branchOrLeaf.leaf.array
S  263    16              19719               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  263     8                  7                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  271     8                 77                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  279    64      3                         array     nodes.node.branchOrLeaf.leaf.array
S  279    16              22536               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  279     8                  8                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  287     8                 88                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  295     4                 15             unary     nodes.node.branchOrLeaf.leaf.unary
""");

    //stop(m.print());
    ok(m.print(), """
      2(2-0)      1(4-0.1)3          |
1,2=2       3,4=1          5,6,7,8=3 |
""");
   }

  static void test_put_ascending()                                              // Load a BTree from an ascending sequence
   {final int BitsPerKey = 8, BitsPerData = 8, MaxKeysPerLeaf = 4, size = 32;   // Dimensions of BTree
    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);         // Create BTree
    final int N = 32;
    for (int i = 1; i <= N; i++) m.put(m.new Key (i), m.new Data(2*i));
    m.execute();
    //stop(m.print());
    ok(m.print(), """
                                                          17(8-0)                                                                    10(16-0.1)18                                                                                                                                 |
                        26(4-17)23                                                           20(12-10)15                                                                      12(20-18)                            8(24-18.1)27                                                   |
       30(2-26)29                        28(6-23)25                      24(10-20)22                             21(14-15)19                              16(18-12)14                           13(22-8)11                             9(26-27)        7(28-27.1)31               |
1,2=30           3,4=29           5,6=28           7,8=25        9,10=24            11,12=22            13,14=21            15,16=19             17,18=16            19,20=14          21,22=13           23,24=11             25,26=9         27,28=7             29,30,31,32=31 |
""");
   }

  static void test_put_descending()                                             // Load a BTree from a descending sequence
   {final int BitsPerKey = 8, BitsPerData = 8, MaxKeysPerLeaf = 4, size = 32;   // Dimensions of BTree
    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);         // Create BTree
    final int N = 32;
    for (int i = N; i > 0; i--) m.put(m.new Key (i), m.new Data(2*i));
    m.execute();
    //stop(m.print());
    ok(m.print(), """
                                                                                                                    10(16-0)                                                                     17(24-0.1)18                                                                     |
                                       8(8-10)                            12(12-10.1)15                                                                  20(20-17)23                                                                      26(28-18)27                             |
          7(4-8)      9(6-8.1)11                      13(10-12)14                               16(14-15)19                          21(18-20)22                             24(22-23)25                              28(26-26)29                             30(30-27)31         |
1,2,3,4=7       5,6=9           7,8=11        9,10=13            11,12=14              13,14=16            15,16=19         17,18=21            19,20=22            21,22=24            23,24=25             25,26=28            27,28=29            29,30=30            31,32=31 |
""");

    if (true)                                                                   // Find the data associated with a key
     {m.reset();
      final Data data = m.new Data();
      final Layout.Bit f = m.find(11, data);
      m.execute();
      f.copy().ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  1   found
""");
      data.ok("""
T   At  Wide  Index       Value   Field name
V    0     8                 22   data
""");
     }

    if (true)                                                                   // Missing key
     {m.reset();
      final Data data = m.new Data();
      final Layout.Bit f = m.find(33, data);
      m.execute();
      f.copy().ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  0   found
""");
     }
   }

  static int[]random_array()                                                    // Random array
   {final int[]r = {27, 442, 545, 317, 511, 578, 391, 993, 858, 586, 472, 906, 658, 704, 882, 246, 261, 501, 354, 903, 854, 279, 526, 686, 987, 403, 401, 989, 650, 576, 436, 560, 806, 554, 422, 298, 425, 912, 503, 611, 135, 447, 344, 338, 39, 804, 976, 186, 234, 106, 667, 494, 690, 480, 288, 151, 773, 769, 260, 809, 438, 237, 516, 29, 376, 72, 946, 103, 961, 55, 358, 232, 229, 90, 155, 657, 681, 43, 907, 564, 377, 615, 612, 157, 922, 272, 490, 679, 830, 839, 437, 826, 577, 937, 884, 13, 96, 273, 1, 188};
    return r;
   }

  static void test_put_random()                                                 // Load a BTree from random data
   {final int BitsPerKey = 10, BitsPerData = 10, MaxKeysPerLeaf = 4, size = 256;// Dimensions of BTree
    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);         // Create BTree

    m.maxSteps = 26_000;

    final int[]r = random_array();
    for (int i = 0; i < r.length; i++) m.put(m.new Key(r[i]), m.new Data(2*r[i]));
    m.execute();
    //stop(m.step);
    //stop(m.print());
    ok(m.print(), """
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               208(511-0)209                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
                                                                                                                                                       201(186-208)                                                                                                                                       226(317-208.1)240                                                                                                                                                                                                                                                                                                                                                                                                                                  211(658-209)241                                                                                                                                                                                                                                                                                                   |
                                                                                         216(103-201)229                                                                                                          203(246-226)243                                                                                                                                                                                         234(403-240)                                                                         225(472-240.1)250                                                                                                                                         214(578-211)245                                                                                                                                   205(704-241)                                                                                 236(858-241.1)                                               207(912-241.2)251                                                                         |
            200(27-216)          232(39-216.1)             215(72-216.2)220                                         222(135-229)231                                                    219(234-203)246                                       202(261-243)                228(279-243.1)254                                             223(344-234)            212(358-234.1)                242(391-234.2)237                                    224(425-225)                    248(442-225.1)230                                                 233(501-250)253                                         239(545-214)            213(560-214.1)252                                               218(611-245)                210(650-245.1)247                                               217(686-205)238                                            227(806-236)                204(830-236.1)249                                              244(903-207)221                                                     206(961-251)            235(987-251.1)255            |
1,13,27=200            29,39=232              43,55,72=215                 90,96,103=220                106,135=222                151,155,157,186=231             188,229,232,234=219                237,246=246                260,261=202             272,273,279=228                  288,298,317=254                  338,344=223             354,358=212               376,377,391=242                  401,403=237             422,425=224             436,437,438,442=248                  447,472=230                  480,490,494,501=233                503,511=253              516,526,545=239             554,560=213                  564,576,577,578=252                586,611=218             612,615,650=210                  657,658=247                667,679,681,686=217                690,704=238             769,773,804,806=227             809,826,830=204                  839,854,858=249               882,884,903=244                906,907,912=221                  922,937,946,961=206             976,987=235                  989,993=255 |
""");
   //say("Number of steps ", m.step);
   }

  static void test_put_random_small()                                           // Load a BTree from a small amount of random data so it easy to see the full tree
   {final int BitsPerKey = 10, BitsPerData = 10, MaxKeysPerLeaf = 4, size = 8;  // Dimensions of BTree
    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);         // Create BTree

    final int[]r = random_array();
    for (int i = 0; i < size; i++) m.put(m.new Key(r[i]), m.new Data(2*r[i]));
    m.execute();
    ok(m.print(), """
         6(317)              5(511)7-0              |
27,317=6       391,442,511=5          545,578,993=7 |
""");
   //say("Number of steps ", m.step);
   }

  static void test_path()                                                       // Locate the path from the root to the leaf which should contain the key
   {final int BitsPerKey = 5, BitsPerData = 6, MaxKeysPerLeaf = 4, size = 16;   // Dimensions of BTree
    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);         // Create BTree
    for (int i = 1; i <= size; i++) m.put(m.new Key(i), m.new Data(2*i));
    m.execute();
    //stop(m.print());
    ok(m.print(), """
                        10(4-0)                     7(8-0.1)11                                                  |
       14(2-10)13                     12(6-7)9                       8(10-11)        6(12-11.1)15               |
1,2=14           3,4=13        5,6=12         7,8=9           9,10=8         11,12=6             13,14,15,16=15 |
""");

    if (true)                                                                   // Path to key 1
     {m.reset();
      Path p = m.new Path(m.new Key(1));
      m.execute();

      p.key.v.asLayout().ok("""
T   At  Wide  Index       Value   Field name
V    0     5                  1   key
""");

      p.found.asLayout().ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  1   found
""");

      p.path.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    25            3145888   path
A    0    20      0         160     array     array
V    0     4                  0       nodeIndex     array.nodeIndex
A    4    20      1         160     array     array
V    4     4                 10       nodeIndex     array.nodeIndex
A    8    20      2         160     array     array
V    8     4                  0       nodeIndex     array.nodeIndex
A   12    20      3         160     array     array
V   12     4                  0       nodeIndex     array.nodeIndex
A   16    20      4         160     array     array
V   16     4                  0       nodeIndex     array.nodeIndex
V   20     5                  3     unary     unary
""");

      p.nodeIndex.v.asLayout().ok("""
T   At  Wide  Index       Value   Field name
V    0     4                 14   nodeIndex
""");

      p.leafIndex.v.asLayout().ok("""
T   At  Wide  Index       Value   Field name
V    0     4                  0   leafIndex
""");
     }

    if (true)                                                                   // Path to key 2
     {m.reset();
      Path p = m.new Path(m.new Key(2));
      m.execute();

      p.key.v.asLayout().ok("""
T   At  Wide  Index       Value   Field name
V    0     5                  2   key
""");

      p.found.asLayout().ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  1   found
""");

      p.path.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    25            3145888   path
A    0    20      0         160     array     array
V    0     4                  0       nodeIndex     array.nodeIndex
A    4    20      1         160     array     array
V    4     4                 10       nodeIndex     array.nodeIndex
A    8    20      2         160     array     array
V    8     4                  0       nodeIndex     array.nodeIndex
A   12    20      3         160     array     array
V   12     4                  0       nodeIndex     array.nodeIndex
A   16    20      4         160     array     array
V   16     4                  0       nodeIndex     array.nodeIndex
V   20     5                  3     unary     unary
""");

      p.nodeIndex.v.asLayout().ok("""
T   At  Wide  Index       Value   Field name
V    0     4                 14   nodeIndex
""");

      p.leafIndex.v.asLayout().ok("""
T   At  Wide  Index       Value   Field name
V    0     4                  1   leafIndex
""");
     }

    if (true)                                                                   // Path to key 16
     {m.reset();
      Path p = m.new Path(m.new Key(16));
      m.execute();

      p.key.v.asLayout().ok("""
T   At  Wide  Index       Value   Field name
V    0     5                 16   key
""");

      p.found.asLayout().ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  1   found
""");

      p.path.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    25            3145904   path
A    0    20      0         176     array     array
V    0     4                  0       nodeIndex     array.nodeIndex
A    4    20      1         176     array     array
V    4     4                 11       nodeIndex     array.nodeIndex
A    8    20      2         176     array     array
V    8     4                  0       nodeIndex     array.nodeIndex
A   12    20      3         176     array     array
V   12     4                  0       nodeIndex     array.nodeIndex
A   16    20      4         176     array     array
V   16     4                  0       nodeIndex     array.nodeIndex
V   20     5                  3     unary     unary
""");

      p.nodeIndex.v.asLayout().ok("""
T   At  Wide  Index       Value   Field name
V    0     4                 15   nodeIndex
""");

      p.leafIndex.v.asLayout().ok("""
T   At  Wide  Index       Value   Field name
V    0     4                  7   leafIndex
""");
     }

    if (true)                                                                   // Path to missing key
     {m.reset();
      Path p = m.new Path(m.new Key(17));
      m.execute();

      p.key.v.asLayout().ok("""
T   At  Wide  Index       Value   Field name
V    0     5                 17   key
""");

      p.found.asLayout().ok("""
T   At  Wide  Index       Value   Field name
B    0     1                  0   found
""");

      p.path.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    25            3145904   path
A    0    20      0         176     array     array
V    0     4                  0       nodeIndex     array.nodeIndex
A    4    20      1         176     array     array
V    4     4                 11       nodeIndex     array.nodeIndex
A    8    20      2         176     array     array
V    8     4                  0       nodeIndex     array.nodeIndex
A   12    20      3         176     array     array
V   12     4                  0       nodeIndex     array.nodeIndex
A   16    20      4         176     array     array
V   16     4                  0       nodeIndex     array.nodeIndex
V   20     5                  3     unary     unary
""");

      p.nodeIndex.v.ok("""
T   At  Wide  Index       Value   Field name
V    0     4                 15   nodeIndex
""");

      p.leafIndex.v.ok("""
T   At  Wide  Index       Value   Field name
V    0     4                  7   leafIndex
""");
     }
   }

  static void test_path_index()                                                 // Locate the path from the root to the leaf which should contain the key
   {final int BitsPerKey = 5, BitsPerData = 6, MaxKeysPerLeaf = 4, size = 16;   // Dimensions of BTree
    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);         // Create BTree
    for (int i = 1; i <= size; i++) m.put(m.new Key(i), m.new Data(2*i));
    m.execute();
    //stop(m.print());
    ok(m.print(), """
                        10(4-0)                     7(8-0.1)11                                                  |
       14(2-10)13                     12(6-7)9                       8(10-11)        6(12-11.1)15               |
1,2=14           3,4=13        5,6=12         7,8=9           9,10=8         11,12=6             13,14,15,16=15 |
""");

    m.reset();
    final Path p = m.new Path(16);
    m.execute();

    //stop(p);
    p.ok("""
T   At  Wide  Index       Value   Field name
V    0     5                 16   key
T   At  Wide  Index       Value   Field name
B    0     1                  1   found
T   At  Wide  Index       Value   Field name
S    0    25            3145904   path
A    0    20      0         176     array     array
V    0     4                  0       nodeIndex     array.nodeIndex
A    4    20      1         176     array     array
V    4     4                 11       nodeIndex     array.nodeIndex
A    8    20      2         176     array     array
V    8     4                  0       nodeIndex     array.nodeIndex
A   12    20      3         176     array     array
V   12     4                  0       nodeIndex     array.nodeIndex
A   16    20      4         176     array     array
V   16     4                  0       nodeIndex     array.nodeIndex
V   20     5                  3     unary     unary
T   At  Wide  Index       Value   Field name
V    0     4                 15   nodeIndex
T   At  Wide  Index       Value   Field name
V    0     4                  7   leafIndex
""");

    m.reset();
    final Stuck.Index i = p.lastNotFull();
    m.execute();
    //stop(i);
    i.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                 33   s
V    0     5                  1     pathIndex     pathIndex
B    5     1                  1     valid     valid
V    6     4                  0     value     value
""");
   }

  static void test_put9()                                                       // Insert the same data twice to get the same result
   {final int BitsPerKey = 5, BitsPerData = 6, MaxKeysPerLeaf = 4, size = 9;

    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);

    for (int i = 1; i <= size; i++) m.put(i, 2*i);
    m.execute();
    m.execute();

    m.reset();
    final Path        p = m.new Path(8);
    final Stuck.Index i = p.lastNotFull();
    m.execute();

    //stop(m.print());
    ok(m.print(), """
      7(2-0)      6(4-0.1)      5(6-0.2)8        |
1,2=7       3,4=6         5,6=5          7,8,9=8 |
""");
   }

  static void test_branch_get_first_last()                                      // Get the first and last key, next pairs in a branch
   {final int BitsPerKey = 5, BitsPerData = 6, MaxKeysPerLeaf = 4, size = 9;

    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);
    for (int i = 1; i <= size; i++) m.put(i, 2*i);

    final NN      r = m.new NN(m.root);
    final KeyNext f = m.branchGetFirst(r);
    final KeyNext l = m.branchGetLast (r);
    m.execute();

    //stop(f, l);
    f.v.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0     9                226   branchKeyNext
V    0     5                  2     branchKey     branchKey
V    5     4                  7     branchNext     branchNext
""");
    l.v.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0     9                166   branchKeyNext
V    0     5                  6     branchKey     branchKey
V    5     4                  5     branchNext     branchNext
""");
   }

  static void test_branch_might_contain_key()                                   // Branch might contain a key
   {final int BitsPerKey = 5, BitsPerData = 6, MaxKeysPerLeaf = 4, size = 5,
      N = 9;

    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);
    for (int i = 1; i <= N; i++) m.put(m.new Key(i), m.new Data(2*i));
    Layout.Bit t1 = m.branchMightContainKey(m.new NN(0), m.new Key(1));
    Layout.Bit t2 = m.branchMightContainKey(m.new NN(0), m.new Key(2));
    Layout.Bit t3 = m.branchMightContainKey(m.new NN(0), m.new Key(3));
    Layout.Bit t4 = m.branchMightContainKey(m.new NN(0), m.new Key(4));
    Layout.Bit t5 = m.branchMightContainKey(m.new NN(0), m.new Key(5));
    Layout.Bit t6 = m.branchMightContainKey(m.new NN(0), m.new Key(6));
    Layout.Bit t7 = m.branchMightContainKey(m.new NN(0), m.new Key(7));
    m.execute();

    //stop(m.print(), t1, t2, t3, t4, t5, t6, t7);
    ok(m.print(), """
      3(2-0)      2(4-0.1)      1(6-0.2)4        |
1,2=3       3,4=2         5,6=1          7,8,9=4 |
""");
    ok(t1, """
T   At  Wide  Index       Value   Field name
B    0     1                  0   result
""");
    ok(t2, """
T   At  Wide  Index       Value   Field name
B    0     1                  1   result
""");
    ok(t3, """
T   At  Wide  Index       Value   Field name
B    0     1                  1   result
""");
    ok(t4, """
T   At  Wide  Index       Value   Field name
B    0     1                  1   result
""");
    ok(t5, """
T   At  Wide  Index       Value   Field name
B    0     1                  1   result
""");
    ok(t6, """
T   At  Wide  Index       Value   Field name
B    0     1                  1   result
""");
    ok(t7, """
T   At  Wide  Index       Value   Field name
B    0     1                  0   result
""");
   }

  static void test_unary()                                                      // unary.value versus currentSize() for a stuck
   {final int BitsPerKey = 5, BitsPerData = 6, MaxKeysPerLeaf = 4, size = 9;

    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);
    m.execute();
   }

  static void test_branch_merge_leaves()                                        // Merge the two leaves under a key at the specified index in branch
   {final int BitsPerKey = 5, BitsPerData = 6, MaxKeysPerLeaf = 4, size = 10,
      N = 8;

    final Mjaf m = new Mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);
    for (int i = 1; i <= N; i++) m.put(m.new Key(i), m.new Data(2*i));
    m.execute();

    //stop(m.print());
    ok(m.print(), """
      8(2-0)      7(4-0.1)9          |
1,2=8       3,4=7          5,6,7,8=9 |
""");

    m.reset();
    m.branchMergeLeaves(m.new NN(m.root), m.new BI(0));
    m.execute();
    //stop(m.print());
    ok(m.print(), """
          8(4-0)9          |
1,2,3,4=8        5,6,7,8=9 |
""");

    m.reset();
    for (int i = 1; i <= N; i++) m.put(9, 9);                                   // Make it possible to join the last two leaves
    m.setIndex(m.nodes, m.new NN(9));
    m.copy(m.leaf.unary.value, 3);                                              // Set leaf length
    m.execute();

    //stop(m.print());
    ok(m.print(), """
          8(4-0)      7(6-0.1)9      |
1,2,3,4=8       5,6=7          7,8=9 |
""");

    m.branchMergeLeaves(m.new NN(m.root), m.new BI(1));
    m.execute();

    //stop(m.print());
    ok(m.print(), """
          8(4-0)7          |
1,2,3,4=8        5,6,7,8=7 |
""");
   }

  static void test_branch_merge_branches()                                      // Merge the two branches under a key at the specified index in branch
   {final int BitsPerKey = 5, BitsPerData = 6, MaxKeysPerLeaf = 4, size = 32,
      N = 32;

    final Mjaf m = new Mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);
    for (int i = 1; i <= N; i++) m.put(m.new Key(i), m.new Data(2*i));
    m.execute();

    //stop(m.print());
    ok(m.print(), """
                                                            17(8-0)                                                                    10(16-0.1)18                                                                                                                              |
                          26(4-17)23                                                           20(12-10)15                                                                      12(20-18)                            8(24-18.1)27                                                |
         30(2-26)29                        28(6-23)25                      24(10-20)22                             21(14-15)19                              16(18-12)14                           13(22-8)11                             9(26-27)        7(28-27.1)31            |
0,1,2=30           3,4=29           5,6=28           7,8=25        9,10=24            11,12=22            13,14=21            15,16=19             17,18=16            19,20=14          21,22=13           23,24=11             25,26=9         27,28=7             29,30,31=31 |
""");

    m.branchSetCurrentSize(27, 1);                                              // Make a pair of branches we can join
    m.branchSetTopNext(27, 7);
    //stop(m.layout);
    //stop(m.print());
    ok(m.print(), """
                                                            17(8-0)                                                                    10(16-0.1)18                                                                                                       |
                          26(4-17)23                                                           20(12-10)15                                                                      12(20-18)                            8(24-18.1)27                         |
         30(2-26)29                        28(6-23)25                      24(10-20)22                             21(14-15)19                              16(18-12)14                           13(22-8)11                             9(26-27)7        |
0,1,2=30           3,4=29           5,6=28           7,8=25        9,10=24            11,12=22            13,14=21            15,16=19             17,18=16            19,20=14          21,22=13           23,24=11             25,26=9          27,28=7 |
""");

    m.reset();
    m.branchMergeBranches(m.new NN(18), m.new BI(1));                           // Merge branches
    m.execute();

    //stop(m.print());
    ok(m.print(), """
                                                            17(8-0)                                                                    10(16-0.1)18                                                                                                     |
                          26(4-17)23                                                           20(12-10)15                                                                      12(20-18)8                                                              |
         30(2-26)29                        28(6-23)25                      24(10-20)22                             21(14-15)19                              16(18-12)14                            13(22-8)        7(24-8.1)        9(26-8.2)11         |
0,1,2=30           3,4=29           5,6=28           7,8=25        9,10=24            11,12=22            13,14=21            15,16=19             17,18=16            19,20=14           21,22=13         27,28=7          25,26=9            23,24=11 |
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {create_leaf_tree();                 create_branch_tree();
    test_leaf_make();                   test_branch_make();
    test_leaf_get_put();                test_branch_get_put();
    test_leaf_insert_remove();          test_branch_insert_remove();
    test_leaf_split_key();              test_branch_split_key();
    test_leaf_is_full_empty();          test_branch_is_full_empty();
    test_leaf_push_leaf();              test_branch_push_shift();
    test_leaf_indexOf();                test_branch_indexOf();
    test_leaf_split_join();             test_branch_split_join();
    test_leaf_emptyFull();              test_branch_emptyFull();
    test_branch_top_next();
    test_leaf_greater_than_or_equal();  test_branch_greater_than_or_equal();
    test_leaf_split_root();             test_branch_split_root();
                                        test_branch_get_first_last();

    test_root_is_leaf_or_branch();
    test_find();
    test_find_and_insert();
    test_leaf_insert_pair();
    test_from_keyDataNext();
    test_branch_greater_than_or_equal_index();
    test_leaf_root_is_full();           test_branch_root_is_full();

    test_leaf_fission();                test_branch_fission();

    test_put();  test_put_ascending();  test_put_descending();
    test_put_random();
    test_path();
    test_path_index();
    test_put9();
    test_branch_might_contain_key();
    test_unary();
    test_branch_merge_leaves(); test_branch_merge_branches();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
    test_branch_merge_branches();
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
