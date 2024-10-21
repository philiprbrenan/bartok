//------------------------------------------------------------------------------
// Btree in bit machine assembler.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Design, simulate and layout  a binary tree on a silicon chip.
// Use derived classes to specialize Layouts used as parameters to supply typing
// Assert Branch or Leaf for all parameters indexing a branch or a leaf
class Mjaf extends BitMachine                                                   // Btree algorithm but with data stored only in the leaves to facilitate deletion without complicating search or insertion. The branches (interior nodes) have an odd number of keys to make the size of a branch as close to that of a leaf as possible to simplify memory management.
 {final int bitsPerKey;                                                         // Number of bits in key
  final int bitsPerData;                                                        // Number of bits in data
  final int bitsPerNext;                                                        // Number of bits in next level
  final int maxKeysPerLeaf;                                                     // The maximum number of keys per leaf.  This should be an even number greater than three. The maximum number of keys per branch is one less. The normal Btree algorithm requires an odd number greater than two for both leaves and branches.  The difference arises because we only store data in leaves not in leaves and branches as does the classic Btree algorithm.
  final int maxKeysPerBranch;                                                   // The maximum number of keys per branch.
  final int maxNodes;                                                           // The maximum number of nodes in the tree
  final int leafSplitPoint;                                                     // The point at which to split a leaf
  final int branchSplitPoint;                                                   // The point at which to split a branch

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

//D1 Construction                                                               // Create a Btree from nodes which can be branches or leaves.  The data associated with the Btree is stored only in the leaves opposite the keys

  Mjaf(int BitsPerKey, int BitsPerData, int MaxKeysPerLeaf, int size)           // Define a Btree with a specified maximum number of keys per leaf.
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
    leafSplitIdx     = W.new Variable ("leafSplitIdx",   N);                    // Index of leaf splitting key
    branchSplitIdx   = W.new Variable ("branchSplitIdx", N);                    // Index of branch splitting key
    workStructure    = W.new Structure("workStructure",                         // An entry in a leaf node
      leafSplitIdx, branchSplitIdx);

    W.layout(workStructure);                                                    // Layout of a leaf key data pair
    leafSplitIdx.fromUnary(leafSplitPoint);                                     // Index of splitting key in leaf in unary
    branchSplitIdx.fromUnary(branchSplitPoint);                                 // Index of splitting key in branch in unary
    Layout.constants(leafSplitIdx, branchSplitIdx);                             // Mark as constants

    final Layout L   = layoutLeafKeyData = new Layout();                        // Layout of a leaf key data pair
    leafKey          = L.new Variable ("leafKey",  bitsPerKey);                 // Key in a leaf
    leafData         = L.new Variable ("leafData", bitsPerData);                // Data in a leaf
    leafKeyData      = L.new Structure("leafKeyData", leafKey, leafData);       // An entry in a leaf node
    layoutLeafKeyData.layout(leafKeyData);                                      // Layout of a leaf key data pair

    leaf             =   new Stuck("leaf",                                      // Leaf key, data pairs stuck
      maxKeysPerLeaf,layoutLeafKeyData);

    final Layout B   = layoutBranchKeyNext = new Layout();                      // An entry in a branch node
    branchKey        = B.new Variable ("branchKey",  bitsPerKey);               // Key in a branch
    branchNext       = B.new Variable ("branchNext", bitsPerNext);              // Next from a branch
    branchKeyNext    = B.new Structure("branchKeyNext", branchKey, branchNext); // An entry in a branch node
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
    nodesFree.unary.value.ones();                                               // The stuck is initially full of free nodes

    bitMachine(nodesFree, branchStuck, leaf); bitMachines(this);                // Place all the instruction that would otherwise be generated in these machines into this machine instead
   }

  static Mjaf mjaf(int Key, int Data, int MaxKeysPerLeaf, int size)             // Define a Btree with a specified maximum number of keys per leaf.
   {return new Mjaf(Key, Data, MaxKeysPerLeaf, size);
   }

  void size     (Layout.Variable size) {copy(size, keyDataStored);}             // Number of entries in the tree
  void emptyTree(Layout.Bit    result) {copy(result, hasNode); not(result);}    // Test for an empty tree

  void allocate(Layout.Variable index)                                          // Allocate a node from the free node stack
   {nodesFree.pop(index);                                                       // Binary index of next free node
   }

  void free(Layout.Variable index)                                              // Free the indexed node
   {nodesFree.push(index.copy());                                               // Place node on free nodes stuck
    clear(index);
   }

  void clear(Layout.Variable index)                                             // Clear a node
   {setIndex(nodes, index);                                                     // Address node just freed
    zero(node);                                                                 // Clear the node
   }

  void isLeaf(Layout.Variable index, Layout.Bit result)                         // Check whether the specified node is a leaf
   {setIndex(nodes, index);                                                     // Index node
    copy(result, isLeaf);                                                       // Get leaf flag
   }

  Layout.Bit isLeaf(Layout.Variable index)                                      // Check whether the specified node is a leaf
   {final Layout.Bit result = Layout.createBit("isLeaf");                       // Result bit
    isLeaf(index, result);                                                       // Check whether the specified node is a leaf
    return result;
   }

  void isBranch(Layout.Variable index, Layout.Bit result)                       // Check whether the specified node is a branch
   {setIndex(nodes, index);                                                     // Index node
    copy(result, isBranch);                                                     // Get branch flag
   }

  Layout.Bit isBranch(Layout.Variable index)                                    // Check whether the specified node is a branch
   {final Layout.Bit result = Layout.createBit("isBranch");                     // Result bit
    isBranch(index, result);                                                    // Check whether the specified node is a branch
    return result;
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

  void rootIsBranch(Layout.Bit result)                                          // Check whether the root is a branch
   {setIndex(nodes, root);                                                      // Index node
    copy(result, isBranch);                                                     // Get branch flag
   }

  Layout.Bit rootIsBranch()                                                     // Check whether the root is a branch
   {final Layout.Bit result = Layout.createBit("rootIsBranch");                 // Result bit
    rootIsBranch(result);                                                       // Check whether the root is a branch
    return result;
   }

  Layout.Variable nodeIndex(String name)                                        // Create a node index with the specified name
   {return Layout.createVariable(name, bitsPerNext);
   }

  Layout.Variable nodeIndex() {return nodeIndex("nodeIndex");}                  // Create a node index with a default name

  Layout.Variable leafIndex(String name)                                        // Create a leaf index with the specified name
   {return Layout.createVariable(name, maxKeysPerLeaf);
   }
  Layout.Variable leafIndex() {return leafIndex("leafIndex");}                  // Create a leaf index with a default name

  Layout.Variable branchIndex(String name)                                      // Create a branch index with the specified name
   {return Layout.createVariable(name, maxKeysPerBranch);
   }
  Layout.Variable branchIndex() {return branchIndex("branchIndex");}            // Create a branch index with a default name

  Layout.Variable makeKey(int value)                                            // Create a key with the specified value
   {final Layout.Variable key = Layout.createVariable("key", bitsPerKey);
    copy(key, value);
    return key;
   }

  Layout.Variable makeData(int value)                                           // Create a data item with the specified value
   {final Layout.Variable data = Layout.createVariable("data", bitsPerData);
    copy(data, value);
    return data;
   }

  LayoutAble makeKeyData(Layout.Variable Key, Layout.Variable Data)             // Create a key, data pair for insertion into a leaf
   {final LayoutAble kd = leafKeyData.duplicate();                              // Key, data pair
    if (Key  != null) copy(kd.asLayout().get("leafKey"),  Key);                 // Copy key if requested
    if (Data != null) copy(kd.asLayout().get("leafData"), Data);                // Copy data if requested
    return kd;
   }

  LayoutAble makeKeyData() {return makeKeyData(null, null);}                    // Create a key, data pair for insertion into a leaf without loading the fields

  Layout.Variable keyFromKeyData(LayoutAble kd)                                 // Get the key from a key, data pair
   {return kd.asLayout().get("leafKey").toVariable();
   }

  Layout.Variable dataFromKeyData(LayoutAble kd)                                // Get the data from a key, data pair
   {return kd.asLayout().get("leafData").toVariable();
   }


  Layout.Variable makeNext(int value)                                           // Create a next item with the specified value
   {final Layout.Variable next = Layout.createVariable("next", bitsPerNext);
    copy(next, value);
    return next;
   }

  LayoutAble makeKeyNext(Layout.Variable Key, Layout.Variable Next)             // Create a key, next  pair for insertion into a branch
   {final LayoutAble kn = branchKeyNext.duplicate();                            // Key, next pair
    if (Key  != null) copy(kn.asLayout().get("branchKey"),  Key);               // Copy key if requested
    if (Next != null) copy(kn.asLayout().get("branchNext"), Next);              // Copy data if requested
    return kn;
   }

  LayoutAble makeKeyNext() {return makeKeyNext(null, null);}                    // Create a key, next  pair for insertion into a branch without loading the fields


  Layout.Variable keyFromKeyNext(LayoutAble kn)                                 // Get the key from a key, next pair
   {return kn.asLayout().get("branchKey").toVariable();
   }

  Layout.Variable nextFromKeyNext(LayoutAble kn)                                // Get the next node from a key, next pair
   {return kn.asLayout().get("branchNext").toVariable();
   }

//D1 Leaf                                                                       // Process a leaf

  void leafMark(Layout.Variable iLeaf)                                          // Mark a node as a leaf not as a branch
   {setIndex(nodes, iLeaf);                                                     // Select the leaf to process
    ones(isLeaf);                                                               // Flag as a leaf
    zero(isBranch);                                                             // Flag as not a branch
   }

  void leafMake(Layout.Variable iLeaf)                                          // Make a new leaf by taking a node off the free nodes stack and converting it into a leaf
   {allocate(iLeaf);                                                            // Allocate a new node
    setIndex(nodes, iLeaf);                                                     // Select the leaf to process
    leaf.unary.zero();                                                          // Clear leaf
    leafMark(iLeaf);
   }

  Layout.Variable leafMake()                                                    // Make a new leaf and return its index
   {Layout.Variable i = nodeIndex("leaf");
    leafMake(i);
    return i;
   }

  void leafGet(Layout.Variable iLeaf, Layout.Variable index, LayoutAble kd)     // Get the specified key, data pair in the specified leaf
   {setIndex(nodes, iLeaf);                                                     // Select the leaf to process
    leaf.elementAt(kd, index);                                                  // Insert the key, data pair at the specified index in the specified leaf
   }

  void leafPut(Layout.Variable iLeaf, Layout.Variable index, LayoutAble kd)     // Put the specified key, data pair from the specified leaf
   {setIndex(nodes, iLeaf);                                                     // Select the leaf to process
    leaf.setElementAt(kd, index);                                               // Insert the key, data pair at the specified index in the specified leaf
   }

  void leafInsert(Layout.Variable iLeaf, Layout.Variable index, LayoutAble kd)  // Place the specified key, data pair at the specified location in the specified leaf
   {setIndex(nodes, iLeaf);                                                     // Select the leaf to process
    leaf.insertElementAt(kd, index);                                            // Insert the key, data pair at the specified index in the specified leaf
   }

  void leafRemove(Layout.Variable iLeaf, Layout.Variable index)                 // Remove the key, data pair at the specified location in the specified leaf
   {setIndex(nodes, iLeaf);                                                     // Select the leaf to process
    leaf.removeElementAt(index);                                                // Remove the key, data pair at the specified index in the specified leaf
   }

  void leafIsEmpty(Layout.Variable index, Layout.Bit result)                    // Leaf is empty
   {setIndex(nodes, index);
    leaf.unary.canNotDec(result);
   }

  void leafIsFull(Layout.Variable index, Layout.Bit result)                     // Leaf is full
   {setIndex(nodes, index);
    leaf.unary.canNotInc(result);
   }

  Layout.Bit leafIsFull(Layout.Variable index)                                  // Leaf is full
   {Layout.Bit result = Layout.createBit("leafIsFull");
    leafIsFull(index, result);
    return result;
   }

  void leafIsNotFull(Layout.Variable index, Layout.Bit result)                  // Leaf is not full
   {setIndex(nodes, index);
    leaf.unary.canInc(result);
   }

  Layout.Bit leafIsNotFull(Layout.Variable index)                               // Leaf is not full
   {Layout.Bit result = Layout.createBit("leafIsNotFull");
    leafIsNotFull(index, result);
    return result;
   }

  void leafSplitKey(Layout.Variable index, LayoutAble out)                      // Splitting key in a leaf
   {setIndex(nodes, index);
    leaf.elementAt(out, leafSplitIdx);
   }

  LayoutAble leafSplitKey(Layout.Variable index)                                // Return splitting key in a leaf
   {final LayoutAble out = makeKeyData();
    leafSplitKey(index, out);
    return out;
   }

  void leafPush(Layout.Variable index, LayoutAble kd)                           // Push a key, data pair onto the indicated leaf
   {setIndex(nodes, index);
    leaf.push(kd);
   }

  void leafShift(Layout.Variable index, LayoutAble kd)                          // Shift a key, data pair from the indicated leaf
   {setIndex(nodes, index);
    leaf.shift(kd);
   }

  void leafFindIndexOf                                                          // Find index of the specified key, data pair in the specified leaf
   (Layout.Variable index, Layout.Variable key, Layout.Bit found,
    Layout.Variable result)
   {setIndex(nodes, index);
    leaf.indexOf(key, bitsPerKey, found, result);
   }

  void leafSplitRoot(Layout.Variable F1, Layout.Variable F2)                    // Split the root when it is a leaf
   {final LayoutAble  kd = leafKeyData.duplicate();                             // Transferring key, data pairs from the source node to the target node
    final LayoutAble rkd = leafKeyData.duplicate();                             // Root key, data pair
    final LayoutAble rkn = branchKeyNext.duplicate();                           // Root key, next pair
    final Layout.Variable ort = topNext.like();                                 // Old root top

    leafMake(F2);                                                               // New right leaf
    leafMake(F1);                                                               // New left leaf
    for (int i = 0; i <= leafSplitPoint; i++)                                   // Transfer keys, data pairs to new left child
     {leafShift(root, kd);                                                      // Current key, data pair from root
      leafPush (F1,   kd);                                                      // Save key, data pair in new left child
     }
    //leafGet(root, root, rkd);                                                 // Root key, data pair. Root is used as zero here to indicate the first leaf element remaining in the root
    copy(rkd.asField(), kd.asField());                                          // Last key of left leaf is key of branch
    for (int i = 0; i <= leafSplitPoint; i++)                                   // Transfer keys, data pairs to new right child
     {leafShift(root, kd);                                                      // Current key, data pair from root
      leafPush (F2,   kd);                                                      // Save key, data pair to new right child
     }

    branchSetTopNext(root, F2);                                                 // Top of root refers to right child
    copy( keyFromKeyNext(rkn), keyFromKeyData(rkd));                            // Save key
    copy(nextFromKeyNext(rkn), F1);                                             // First root key refers to left child
    branchPush (root,  rkn);                                                    // Save root key, next pair
    branchMark(root);                                                           // The root is now a branch
   }

  void leafSplitRoot()                                                          // Split the root when it is a leaf
   {final Layout.Variable F1 = leafIndex("left");                               // New left leaf
    final Layout.Variable F2 = leafIndex("right");                              // New right root
    leafSplitRoot(F1, F2);
   }

  void leafSplit(Layout.Variable target, Layout.Variable source)                // Source leaf, target leaf. After the leaf has been split the upper half will appear in the source and the loweer half in the target
   {final LayoutAble kd = leafKeyData.duplicate();                              // Work area for transferring key data pairs from the source code to the target node

    leafMake(target);
    for (int i = 0; i <= leafSplitPoint; i++)                                   // Transfer keys, data pairs
     {leafShift(source, kd);                                                    // Current key, data pair
      leafPush (target, kd);                                                    // Save key, data pair
     }
   }

  Layout.Variable leafSplit(Layout.Variable source)                             // Split the source leaf. After the leaf has been split the upper half will appear in the source and the loweer half in the target
   {Layout.Variable target = nodeIndex("target");
    leafSplit(target, source);
    return target;
   }

  void leafFission(Layout.Variable parent, Layout.Variable source)              // Split a leaf that is one of children of its parent branch
   {LayoutAble          kd = leafSplitKey(source);                              // Leaf splitting key
    Layout.Variable target = leafSplit(source);                                 // Split leaf, the target leaf is on the left
    Layout.Variable      k = keyFromKeyData(kd);                                // Split key
    LayoutAble          kn = makeKeyNext(k, target);                            // Key, next pair for insertion into branch
    Layout.Variable      t = branchGetTopNext(parent);                          // Top next of parent

    new IfElse(equals(t, source))                                               // Source is top next of parent
     {void Then()
       {branchPush(parent, kn);                                                 // Append split out leaf as last key, next pair.
       }
      void Else()
       {Layout.Variable      i = branchFindFirstGreaterOrEqualIndex(parent, k); // Position to insert into branch
        branchInsert(parent, i, kn);                                            // Insert splitting key into branch
       }
     };
   }

  void leafJoinable                                                             // Check that we can join two leaves
   (Layout.Variable target, Layout.Variable source, Layout.Bit result)
   {setIndex(nodes, target); Layout.Variable t = leaf.unary.value;
    setIndex(nodes, source); Layout.Variable s = leaf.unary.value;
    unaryFilled(s, t, result);
   }

  void leafJoin(Layout.Variable target, Layout.Variable source)                 // Join the specified leaf onto the end of this leaf
   {new Repeat()
     {void code()
       {setIndex(nodes, source);
        returnIfAllZero(leaf.unary.value);                                      // Exit then the source leaf has been emptied
        final Layout kd = leafKeyData.duplicate();                              // Key data pair buffer
        leaf.shift(kd);
        setIndex(nodes, target);
        leaf.push(kd);
       }
     };
    free(source);                                                               // Free the leaf that was joined
   }

  void leafInsertPair(Layout.Variable NodeIndex,                                // Insert a key and the corresponding data into a leaf at the correct position
    Layout.Variable Key, Layout.Variable Data)
   {final Layout.Variable leafIndex = leafIndex();
    final Layout.Bit         insert = Layout.createBit("insert");
    final LayoutAble             kd = makeKeyData(Key, Data);                   // Key, data pair to insert

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

  void leafFirstGreaterThanOrEqual(Layout.Variable NodeIndex,                   // Find the index of the first key in a leaf that is greater than or equal to the specified key or set found to be false if such a key cannot be found becuase all the keys in the leaf are less than the search key
    Layout.Variable Key, Layout.Variable Leaf, Layout.Bit Result)
   {new Block()
     {void code()
       {final Block outer = this;
        new Block()
         {void code()
           {final Block inner = this;
            final LayoutAble kd = leafKeyData.duplicate();                      // Work area for transferring key data pairs from the source code to the target node
            setIndex(nodes, NodeIndex);                                         // Index the node to search
            zero(Leaf);                                                         // Start with the first key, next pair in the leaf
            ones(Result);                                                       // assume  success
            for (int i = 0; i < maxKeysPerLeaf; i++)                            // Check each key
             {inner.returnIfEqual(Leaf, leaf.unary.value);                      // Passed all the valid keys - serach key is bigger than all keys
              leafGet(NodeIndex, Leaf, kd);                                     // Retrieve key/data pair from leaf
              outer.returnIfLessThan(Key, kd.asLayout().get("leafKey"));        // Found a key greater than the serach key
              shiftLeftOneByOne(Leaf);                                          // Next key
             }
           }
         }; // Inner block in which we search for the key
        zero(Result);                                                           // Show that the search key is greater than all the keys in the leaf
       }
     }; // Outer block to exit when we have found the key
   }

//D1 Branch                                                                     // Process a branch

  void branchMark(Layout.Variable iBranch)                                      // Mark a node as a branch not a branch
   {setIndex(nodes, iBranch);                                                   // Select the branch to process
    zero(isLeaf);                                                               // Flag as not a branch
    ones(isBranch);                                                             // Flag as a branch
   }

  void branchMake(Layout.Variable iBranch)                                      // Make a new branch by taking a node off the free nodes stack and converting it into a branch
   {allocate(iBranch);                                                          // Allocate a new node
    setIndex(nodes, iBranch);                                                   // Select the branch to process
    branchStuck.unary.zero();                                                   // Clear branch
    branchMark(iBranch);
   }

  Layout.Variable branchMake()                                                  // Make a new branch and return its index
   {Layout.Variable i = nodeIndex("branch");
    branchMake(i);
    return i;
   }

  void branchGet(Layout.Variable iBranch, Layout.Variable index, LayoutAble kd) // Get the specified key, next pair in the specified branch
   {setIndex(nodes, iBranch);                                                   // Select the branch to process
    branchStuck.elementAt(kd, index);                                           // Insert the key, next pair at the specified index in the specified branch
   }

  void branchPut(Layout.Variable iBranch, Layout.Variable index, LayoutAble kd) // Put the specified key, next pair from the specified branch
   {setIndex(nodes, iBranch);                                                   // Select the branch to process
    branchStuck.setElementAt(kd, index);                                        // Insert the key, next pair at the specified index in the specified branch
   }

  void branchInsert                                                             // Place the specified key, next pair at the specified location in the specified branch
   (Layout.Variable iBranch, Layout.Variable index, LayoutAble kd)
   {setIndex(nodes, iBranch);                                                   // Select the branch to process
    branchStuck.insertElementAt(kd, index);                                     // Insert the key, next pair at the specified index in the specified branch
   }

  void branchRemove(Layout.Variable iBranch, Layout.Variable index)             // Remove the key, next pair at the specified location in the specified branch
   {setIndex(nodes, iBranch);                                                   // Select the branch to process
    branchStuck.removeElementAt(index);                                         // Remove the key, next pair at the specified index in the specified branch
   }

  void branchIsEmpty(Layout.Variable index, Layout.Bit result)                  // Branch is empty
   {setIndex(nodes, index);
    branchStuck.unary.canNotDec(result);
   }

  void branchIsFull(Layout.Variable index, Layout.Bit result)                   // Branch is full
   {setIndex(nodes, index);
    branchStuck.unary.canNotInc(result);
   }

  Layout.Bit branchIsFull(Layout.Variable index)                                // Branch is full
   {final Layout.Bit result = Layout.createBit("branchFull");
    branchIsFull(index, result);
    return result;
   }

  void branchSplitKey(Layout.Variable index, LayoutAble out)                    // Splitting key in a branch
   {setIndex(nodes, index);
    branchStuck.elementAt(out, branchSplitIdx);
   }

  LayoutAble branchSplitKey(Layout.Variable index)                              // Return splitting key in a branch
   {final LayoutAble out = makeKeyNext();
    branchSplitKey(index, out);
    return out;
   }

  void branchPush(Layout.Variable index, LayoutAble kd)                         // Push a key, next pair onto the indicated branch
   {setIndex(nodes, index);
    branchStuck.push(kd);
   }

  void branchShift(Layout.Variable index, LayoutAble kd)                        // Shift a key, next pair from the indicated branch
   {setIndex(nodes, index);
    branchStuck.shift(kd);
   }

  void branchFindIndexOf                                                        // Find index of the specified key, next pair in the specified branch
   (Layout.Variable index, LayoutAble kd,
    Layout.Bit found, Layout.Variable result)
   {setIndex(nodes, index);
    branchStuck.indexOf(kd, bitsPerKey, found, result);
   }

  void branchGetTopNext(Layout.Variable nodeIndex, Layout.Variable top)         // Get the top node for a branch
   {setIndex(nodes, nodeIndex);
    copy(top, topNext);
   }

  Layout.Variable branchGetTopNext(Layout.Variable nodeIndex)                   // Get the top node for a branch
   {final Layout.Variable top = nodeIndex("top");
    branchGetTopNext(nodeIndex, top);
    return top;
   }

  void branchSetTopNext(Layout.Variable nodeIndex, Layout.Variable newTop)      // Set the top node for a branch
   {setIndex(nodes, nodeIndex);
    copy(topNext, newTop);
   }

  void branchSplit(Layout.Variable target, Layout.Variable source)              // Source branch, target branch. After the branch has been split the upper half will appear in the source and the lower half in the target
   {final LayoutAble kn = branchKeyNext.duplicate();                            // Work area for transferring key data pairs from the source code to the target node

    branchMake(target);
    for (int i = 0; i < branchSplitPoint; i++)                                  // Transfer keys, data pairs
     {branchShift(source, kn);                                                  // Current key, next pair
      branchPush (target, kn);                                                  // Save key, next pair
     }
    branchShift(source, kn);                                                    // Current key, next pair
    branchSetTopNext(target, nextFromKeyNext(kn));                              // Copy in the new top node
   }

  Layout.Variable branchSplit(Layout.Variable source)                           // Source branch, target branch. After the branch has been split the upper half will appear in the source and the lower half in the target
   {final Layout.Variable target = nodeIndex();                                 // Index of split out node
    branchSplit(target, source);                                                // Work area for transferring key data pairs from the source code to the target node
    return target;
   }

  void branchSplitRoot(Layout.Variable F1, Layout.Variable F2)                  // Split the root when it is a branch
   {final LayoutAble  kn = branchKeyNext.duplicate();                           // Transferring key, next pairs from the source node to the target node
    final LayoutAble rkn = branchKeyNext.duplicate();                           // Root key,next pair
    final Layout.Variable ort = topNext.like();                                 // Old root top

    branchMake(F2);                                                             // New right branch
    branchMake(F1);                                                             // New left branch
    branchGetTopNext(root, ort);                                                // Old root top
    for (int i = 0; i < branchSplitPoint; i++)                                  // Transfer keys, next pairs to new left child
     {branchShift(root, kn);                                                    // Current key, next pair
      branchPush (F1,   kn);                                                    // Save key, next pair in new left child
     }
    branchShift(root, rkn);                                                     // Root key, next pair
    for (int i = 0; i < branchSplitPoint; i++)                                  // Transfer keys, next pairs to new right child
     {branchShift(root, kn);                                                    // Current key, next pair
      branchPush (F2,   kn);                                                    // Save key, next pair in new right child
     }
// f2 top = root old top, f1 top = rkn.next, root top = f2, root left = f1
    branchSetTopNext(F2, ort);                                                  // Set top next references for each branch
    branchSetTopNext(F1, nextFromKeyNext(rkn));
    branchSetTopNext(root, F2);
    copy(nextFromKeyNext(rkn), F1);                                             // Left next refers to new left branch
    branchPush (root, rkn);                                                     // Save root key, next pair
   }

  void branchSplitRoot()                                                        // Split the root when it is a branch
   {final Layout.Variable F1 = leafIndex("left");                               // New left branch
    final Layout.Variable F2 = leafIndex("right");                              // New right root
    branchSplitRoot(F1, F2);
   }

  void branchFission(Layout.Variable parent, Layout.Variable source)            // Split a branch that is one of the children of its parent branch
   {final LayoutAble          kn = branchSplitKey(source);                      // Branch splitting key
    final Layout.Variable target = branchSplit(source);                         // Split branch, the target branch is on the left
    final Layout.Variable      k = keyFromKeyNext(kn);                          // Split key
    final LayoutAble         pkn = makeKeyNext(k, target);                      // Key, next pair for insertion into branch
    final Layout.Variable      t = branchGetTopNext(parent);                    // Top next of parent

    new IfElse(equals(t, source))                                               // Source is top next of parent
     {void Then()
       {branchPush(parent, pkn);                                                // Append split out branch as last key, next pair.
       }
      void Else()
       {final Layout.Variable i = branchFindFirstGreaterOrEqualIndex(parent, k);// Position to insert into branch
        branchInsert(parent, i, pkn);                                           // Insert splitting key into branch
       }
     };
   }

  void branchJoinable                                                           // Check that we can join two branches
   (Layout.Variable target, Layout.Variable source, Layout.Bit result)
   {setIndex(nodes, target);                                                    // Index the target branch
    final Layout.Variable t = branchStuck.unary.value.like();
    copy(t, branchStuck.unary.value);

    setIndex(nodes, source);
    final Layout.Variable s = branchStuck.unary.value.like();
    copy(s, branchStuck.unary.value);
    unaryFilledMinusOne(s, t, result);
   }

  void branchJoin
   (Layout.Variable target, Layout.Variable key, Layout.Variable source)        // Join the specified branch onto the end of this branch
   {final LayoutAble kn = branchKeyNext.duplicate();                            // Work area for transferring key data pairs from the source code to the target node

    copy( keyFromKeyNext(kn), key);      setIndex(nodes, source);
    copy(nextFromKeyNext(kn), topNext);  setIndex(nodes, target);
    branchStuck.push(kn);

    for (int i = 0; i < branchSplitPoint; i++)                                  // Transfer source keys and next
     {setIndex(nodes, source); branchStuck.shift(kn);
      setIndex(nodes, target); branchStuck.push(kn);
     }
    free(source);                                                               // Free the branch that was joined
   }

  void branchFindFirstGreaterOrEqual                                            // Find the 'next' for the first key in a branch that is greater than or equal to the specified key or else return the top node if no such key exists.
   (Layout.Variable nodeIndex, Layout.Variable key, Layout.Variable result)     // Find node associated with a key
   {new Block()
     {void code()
       {final Block outer = this;
        new Block()
         {void code()
           {final Block inner = this;
            final LayoutAble kn = makeKeyNext();                                // Work area for transferring key data pairs from the source code to the target node
            final Layout.Variable index = branchIndex();                        // Index the key, next pairs in the branch

            setIndex(nodes, nodeIndex);                                         // Index the node to search

            for (int i = 0; i < maxKeysPerBranch; i++)                          // Check each key
             {inner.returnIfEqual(index, branchStuck.unary.value);              // Passed all the valid keys
              branchGet(nodeIndex, index, kn);                                  // Retrieve key/next pair
              copy(result, nextFromKeyNext(kn));
              outer.returnIfLessThan(key, keyFromKeyNext(kn));
              shiftLeftOneByOne(index);
             }
           }
         }; // Inner block in which we search for the key
        copy(result, topNext);                                                  // The search key is greater than all the keys in the branch so return the top node
       }
     }; // Outer block to exit when we have found the key
   }

  Layout.Variable branchFindFirstGreaterOrEqualIndex                            // Assuming the branch is not full, find the index for the first key in a branch that is greater than or equal to the specified key or else return all ones if no such key exists.
   (Layout.Variable NodeIndex, Layout.Variable Key)                             // Branch and key
   {final Layout.Variable result = branchIndex();
     new Block()
     {void code()
       {final Block outer = this;
        new Block()
         {void code()
           {final Block inner = this;
            final LayoutAble kn = makeKeyNext();                                // Work area for transferring key data pairs from the source code to the target node
            final Layout.Variable index = branchIndex();                        // Index the key, next pairs in the branch

            setIndex(nodes, NodeIndex);                                         // Index the node to search

            for (int i = 0; i < maxKeysPerBranch; i++)                          // Check each key
             {inner.returnIfEqual(index, branchStuck.unary.value);              // Passed all the valid keys
              branchGet(NodeIndex, index, kn);                                  // Retrieve key/next pair
              copy(result, index);
              outer.returnIfGreaterThanOrEqual(keyFromKeyNext(kn), Key);
              shiftLeftOneByOne(index);
             }
           }
         }; // Inner block in which we search for the key
        ones(result);
       }
     }; // Outer block to exit when we have found the key
    return result;
   }

//D1 Search                                                                     // Find a key, data pair

  void find(Layout.Variable Key, Layout.Bit Found, Layout.Variable Result)      // Find the data associated with a key in a tree
   {final Layout.Variable nodeIndex = nodeIndex();                              // Node index variable
    final Layout.Variable leafIndex = leafIndex();                              // Leaf key, data pair index variable

    new Repeat()
     {void code()
       {returnIfOne(isLeaf(nodeIndex));                                         // Exit when we reach a leaf
        branchFindFirstGreaterOrEqual(nodeIndex, Key, nodeIndex);
       }
     };

    leafFindIndexOf(nodeIndex, Key, Found, leafIndex);                          // Find index of the specified key, data pair in the specified leaf
    new If(Found)
     {void Then()
       {final LayoutAble kd = makeKeyData();                                    // Key, data pair from leaf
        leafGet(nodeIndex, leafIndex, kd);                                      // Get key, data from stuck
        copy(Result, kd.asLayout().get("leafData"));
       }
     };
   }

  void findAndInsert                                                            // Find the leaf for a key and insert the indicated key, data pair into if possible, returning true if the insertion was possible else false.
   (Layout.Variable Key, Layout.Variable Data, Layout.Bit Inserted)
   {final Layout.Variable nodeIndex = nodeIndex();                              // Node index variable
    final Layout.Variable leafIndex = leafIndex();                              // Leaf key, data index variable

    new Repeat()                                                                // Step down through branches to a leaf into which it might be possible to insert the key
     {void code()
       {returnIfOne(isLeaf(nodeIndex));                                         // Exit when we reach a leaf
        branchFindFirstGreaterOrEqual(nodeIndex, Key, nodeIndex);               // Step down to the next node
       }
     };

    leafFindIndexOf(nodeIndex, Key, Inserted, leafIndex);                       // Find index of the specified key, data pair in the specified leaf
    new IfElse (Inserted)
     {void Then()
       {leafPut(nodeIndex, leafIndex, makeKeyData(Key, Data));                  // Key already present in leaf - update data
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

  Layout.Bit findAndInsert(Layout.Variable Key, Layout.Variable Data)           // Find the leaf for a key and insert the indicated key, data pair into if possible, returning true if the insertion was possible else false.
   {final Layout.Bit inserted = Layout.createBit("inserted");                   // Whether theinsert succeeded
    findAndInsert(Key, Data, inserted);                                         // Find the leaf for a key and insert the indicated key, data pair into if possible, returning true if the insertion was possible else false.
    return inserted;
   }

//D1 Insertion                                                                  // Insert key, data pairs into the Btree

  void put(Layout.Variable Key, Layout.Variable Data)                           // Insert a new key, data pair into the Btree
   {new Block()
     {void code()
       {returnIfOne(findAndInsert(Key, Data));                                  // Return immediately if fast insert succeeded

        final Layout.Bit isLeaf = Layout.createBit("isLeaf");                   // Whether the root is a leaf
        rootIsLeaf(isLeaf);
        new IfElse (isLeaf)                                                     // Insert into root as a leaf
         {void Then()
           {final Layout.Bit isFull = Layout.createBit("isFull");               // Whether the root is full
            leafIsFull(root, isFull);                                           // Check whether the root is full
            new IfElse(isFull)                                                  // Root is a leaf
             {void Then()                                                       // Insert into root as a leaf which is full
               {leafSplitRoot();                                                // Split the root known to be a leaf
                final Layout.Variable n = nodeIndex("next");                    // The index of the node into which to insert the key, data pair
                branchFindFirstGreaterOrEqual(root, Key, n);                    // Choose the leaf in which to insert the key
                leafInsertPair(n, Key, Data);                                   // Insertion is possible because the leaf was just split out of the root and so must have free space
               }
              void Else()                                                       // Still room in the root while it is is a leaf
               {leafInsertPair(root, Key, Data);                                // Insertion is possible because the leaf is not full
               }
             };
            returnRegardless();
           }
          void Else()
           {branchSplitRoot();                                                  // Split full root which is a branch not a leaf
           }
         };

        final Layout.Variable p = nodeIndex("p");                               // The root is known to be a branch because if it were a leaf we would have either inserted into the leaf or split it.
        final Layout.Variable q = nodeIndex("q");                               // Child beneath parent

        new Repeat()                                                            // Step down through tree to find the required leaf, splitting as we go
         {void code()
           {branchFindFirstGreaterOrEqual(p, Key, q);                           // Step down to next child
            returnIfOne(isLeaf(q));                                             // Stepped to a leaf

            new IfElse (branchIsFull(q))                                        // Split the child branch because it is full and we might need to insert below it requiring a slot in this branch
             {void Then()                                                       // Branch is full
               {branchFission(p, q);                                            // Split full branch
               }
              void Else()                                                       // Branch was not full
               {copy(p, q);                                                     // Step down
               }
             };
           }
         };

        leafFission(p, q);                                                      // If the leaf did not need splitting findAndInsert() would have suceeded
       } // code
     }; // block
   } // put

//D1 Deletion                                                                   // Delete a key from a Btree
/*

  Data delete(Key keyName)                                                      // Delete a key from a tree
   {if (emptyTree()) return null;                                               // The tree is empty
    final Data foundData = find(keyName);                                       // Find the data associated with the key
    if (foundData == null) return null;                                         // The key is not present so cannot be deleted

    if (new Node().isLeaf())                                                    // Delete from root as a leaf
     {final Leaf r = new Node().leaf();                                         // Root is a leaf
      final int  i = r.findIndexOfKey(keyName);                                 // Only one leaf and the key is known to be in the Btree so it must be in this leaf
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

  static Mjaf create_leaf_tree()                                                // Create a pre loaded tree of leaves
   {final int B = 12, S = 4, N = 3;
    Mjaf m = mjaf(B, B, S, N);
    m.reset();
    int c = 1;
    for (int n = 0; n < N; n++)                                                 // Each node
     {m.setIndexFromInt(m.nodes, n);                                            // Index node
      m.setVariable("nodes.node.isLeaf",                      1);
      m.setVariable("nodes.node.isBranch",                    0);
      m.setVariable("nodes.node.branchOrLeaf.leaf.unary",    15);

      for (int s = 0; s < S; s++)                                               // Each key, data pair in leaf
       {final Layout.Array a = m.layout.get("nodes.node.branchOrLeaf.leaf.array").toArray();
        m.setIndexFromInt(a, s);
        m.setVariable("nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey",  c++);
        m.setVariable("nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData", c++);
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
S   16   102                  1       node     nodes.node
B   16     1                  1         isLeaf     nodes.node.isLeaf
B   17     1                  0         isBranch     nodes.node.isBranch
U   18   100                  0         branchOrLeaf     nodes.node.branchOrLeaf
S   18    47                  0           branch     nodes.node.branchOrLeaf.branch
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
S  118   102                  1       node     nodes.node
B  118     1                  1         isLeaf     nodes.node.isLeaf
B  119     1                  0         isBranch     nodes.node.isBranch
U  120   100                  0         branchOrLeaf     nodes.node.branchOrLeaf
S  120    47                  0           branch     nodes.node.branchOrLeaf.branch
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
S  220   102                  1       node     nodes.node
B  220     1                  1         isLeaf     nodes.node.isLeaf
B  221     1                  0         isBranch     nodes.node.isBranch
U  222   100                  0         branchOrLeaf     nodes.node.branchOrLeaf
S  222    47                  0           branch     nodes.node.branchOrLeaf.branch
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
      m.setVariable("nodes.node.branchOrLeaf.branch.topNext", n);
      m.setVariable("nodes.node.branchOrLeaf.branch.branchStuck.unary", Layout.unary(N));
      for (int s = 0; s < m.maxKeysPerBranch; s++)                              // Each key, data pair in leaf
       {final Layout.Array a = m.layout.get("nodes.node.branchOrLeaf.branch.branchStuck.array").toArray();
        m.setIndexFromInt(a, s);                                                // Index pair
        m.setVariable("nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey", c++);
        m.setVariable("nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext", s);
       }
     }
    m.copy(m.layout.get("nodesFree.unary").toVariable(), Layout.unary(N)); // Tree globals
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
S   16   102                  2       node     nodes.node
B   16     1                  0         isLeaf     nodes.node.isLeaf
B   17     1                  1         isBranch     nodes.node.isBranch
U   18   100                  0         branchOrLeaf     nodes.node.branchOrLeaf
S   18    47                  0           branch     nodes.node.branchOrLeaf.branch
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
S  144    24            1966180               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  144    12                100                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  156    12                480                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
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
S  246    24            1966228               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                148                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                480                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
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

    m.leafMake(t.nodeIndex);                                                    // Choose the node
    m.isLeaf  (t.nodeIndex, t.leafFlag);                                        // Copy its leaf flag
    m.isBranch(t.nodeIndex, t.branchFlag);                                      // Copy its branch flag
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
    m.leafMake(t.nodeIndex);                                                    // Choose the node
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(t.n);
    t.nodeIndex.ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  1   nodeFree
""");

    m.reset();                                                                  // Next leaf from a branch
    m.leafMake(t.nodeIndex);                                                    // Choose the node
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

    m.branchMake(t.nodeIndex);                                                  // Allocate a new Choose the node
    m.isLeaf  (t.nodeIndex, t.leafFlag);                                        // Copy its leaf flag
    m.isBranch(t.nodeIndex, t.branchFlag);                                      // Copy its branch flag
    m.execute();                                                                // Execute the code to copy out the splitting key

    //stop(t.nodeIndex);
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
    m.leafMake(t.nodeIndex);                                                    // Choose the node
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
    m.branchMake(t.nodeIndex);                                                  // Choose the node
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

    m.leafMake(t.nodeIndex);                                                    // Allocate a leaf
    m.copy(t.leafIndex, 1);                                                     // Choose the key, data pair
    m.leafGet(t.nodeIndex, t.leafIndex, t.kd);                                  // Copy the indexed key, data pair
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
    m.leafPut(t.nodeIndex, t.leafIndex, t.kd);                                  // Put the key, data pair
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

    m.branchMake(t.nodeIndex);                                                  // Allocate a branch
    m.copy                  (t.branchIndex, 1);                                 // Choose the key, next pair
    m.branchGet(t.nodeIndex, t.branchIndex, t.kn);                              // Copy the indexed key, data pair
    m.execute();

    //stop(t.kn);
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
    m.branchPut(t.nodeIndex, t.branchIndex, t.kn);                              // Put the key, next pair
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

    m.leafMake(t.targetIndex);                                                  // Allocate a leaf
    m.leafMake(t.sourceIndex);                                                  // Allocate a leaf
    m.copy                     (t.leafIndex, Layout.unary(2));                  // Choose the key, data pair
    m.leafGet   (t.sourceIndex, t.leafIndex, t.kd);                             // Copy the indexed key, data pair
    m.copy                     (t.leafIndex, Layout.unary(0));                  // Insert key, data pair at start
    m.leafInsert(t.targetIndex, t.leafIndex, t.kd);                             // Insert key, data pair
    m.execute();

    //stop(t.kn);
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
    m.leafGet   (t.sourceIndex, t.leafIndex, t.kd);                             // Copy the indexed key, data pair
    m.copy                     (t.leafIndex, Layout.unary(0));                  // Insert key, data pair at start
    m.leafInsert(t.targetIndex, t.leafIndex, t.kd);                             // Insert key, data pair
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
    m.leafGet   (t.sourceIndex, t.leafIndex, t.kd);                             // Copy the indexed key, data pair
    m.copy                     (t.leafIndex, Layout.unary(0));                  // Insert key, data pair at start
    m.leafInsert(t.targetIndex, t.leafIndex, t.kd);                             // Insert key, data pair
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
    m.leafGet   (t.sourceIndex, t.leafIndex, t.kd);                             // Copy the indexed key, data pair
    m.copy                     (t.leafIndex, Layout.unary(2));                  // Insert key, data pair at start
    m.leafInsert(t.targetIndex, t.leafIndex, t.kd);                             // Insert key, data pair
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
    m.leafRemove(t.targetIndex, t.leafIndex);                                   // Remove key, data pair
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
    m.leafRemove(t.targetIndex, t.leafIndex);                                   // Remove key, data pair
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

    m.branchMake(t.targetIndex);                                                // Allocate a branch
    m.branchStuck.unary.ones();                                                 // Fill branch - branchMake will have set an otherwise full branch to empty

    m.branchMake(t.sourceIndex);                                                // Allocate a branch

    m.copy                       (t.branchIndex, Layout.unary(1));              // Choose the key, next pair
    m.branchGet   (t.sourceIndex, t.branchIndex, t.kn);                         // Copy the indexed key, next pair
    m.copy                       (t.branchIndex, Layout.unary(0));              // Insert key, next pair at start
    m.branchInsert(t.targetIndex, t.branchIndex, t.kn);                         // Insert key, next pair
    m.execute();
    //stop(t.kn);
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
    m.branchGet (t.sourceIndex, t.branchIndex, t.kn);                           // Copy the indexed key, next pair
    m.copy                     (t.branchIndex, Layout.unary(0));                // Insert key, next pair at start
    m.branchInsert(t.targetIndex, t.branchIndex, t.kn);                         // Insert key, next pair
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
    m.branchGet   (t.sourceIndex, t.branchIndex, t.kn);                         // Copy the indexed key, next pair
    m.copy                     (t.branchIndex, Layout.unary(0));                // Insert key, next pair at start
    m.branchInsert(t.targetIndex, t.branchIndex, t.kn);                         // Insert key, next pair
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
    m.branchRemove(t.targetIndex, t.branchIndex);                               // Remove key, next pair
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
    m.branchRemove(t.targetIndex, t.branchIndex);                               // Remove key, next pair
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

    m.leafMake(t.targetIndex);                                                  // Allocate a leaf
    m.leafMake(t.sourceIndex);                                                  // Allocate a leaf
    for (int i = 0; i < 4; i++)
     {m.copy                   (t.leafIndex, Layout.unary(i));                  // Choose the key, data pair
      m.leafGet (t.sourceIndex, t.leafIndex, t.kd);                             // Copy the indexed key, data pair
      m.leafPush(t.targetIndex, t.kd);                                          // Push the key, data pair
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
    m.leafShift(t.targetIndex, t.kd);                                           // Shift the key, data pair
    m.execute();

    //stop(t.kd);
    t.kd.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    24              40969   leafKeyData
V    0    12                  9     leafKey     leafKey
V   12    12                 10     leafData     leafData
""");

    m.reset();
    m.leafShift(t.targetIndex, t.kd);                                           // Shift the key, data pair
    m.execute();

    //stop(t.kd);
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

    m.branchMake(t.targetIndex);                                                // Allocate a branch
    m.branchMake(t.sourceIndex);                                                // Allocate a branch

    for (int i = 0; i < 3; i++)
     {m.copy                     (t.branchIndex, Layout.unary(i));              // Choose the key, next pair
      m.branchGet (t.sourceIndex, t.branchIndex, t.kn);                         // Copy the indexed key, next pair
      m.branchPush(t.targetIndex, t.kn);                                        // Push the key, next pair
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
    m.branchShift(t.targetIndex, t.kn);                                         // Shift the key, data pair
    m.execute();

    //stop(t.kn);
    t.kn.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    14                  4   branchKeyNext
V    0    12                  4     branchKey     branchKey
V   12     2                  0     branchNext     branchNext
""");

    m.reset();
    m.branchShift(t.targetIndex, t.kn);                                         // Shift the key, data pair
    m.execute();

    //stop(t.kn);
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
    m.leafFindIndexOf(t.sourceIndex, key, found, result);                       // Locate key
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
    m.leafFindIndexOf(t.sourceIndex, key, found, result);
    m.execute();

    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    17             122930   s
B    0     1                  0     found     found
V    1    12                 25     key     key
V   13     4                 15     result     result
""");
  }

  static void test_branch_indexOf()                                             // Push some key, next pairs into a branch and then shift them
   {TestBranchTree t = new TestBranchTree();                                    // Create a test tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    Layout               l = new Layout();
    Layout.Bit       found = l.bit     ("found");
    Layout.Variable result = l.variable("result", m.maxKeysPerLeaf);
    Layout.Structure     s = l.structure("s", found, result);
    l.layout(s);

    m.copy(t.sourceIndex, 2);                                                   // Key, data pair
    m.copy(t.branchIndex,   Layout.unary(2));                                   // Address the last node
    m.branchGet(t.sourceIndex, t.branchIndex, t.kn);                            // Copy the indexed key, data pair
    m.branchFindIndexOf(t.sourceIndex, t.kn, found, result);
    m.execute();

    //stop(l);                                                                  // Unary "11" printed as "3" is binary "2"
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     5                  7   s
B    0     1                  1     found     found
V    1     4                  3     result     result
""");

    m.reset();
    final Layout.Variable key = m.branchKey.like();
    m.copy(key, 25);                                                            // Key will not be found
    m.leafFindIndexOf(t.sourceIndex, key, found, result);
    m.execute();

    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     5                 30   s
B    0     1                  0     found     found
V    1     4                 15     result     result
""");
  }

  static void test_leaf_split_join()                                            // Split a leaf
   {TestLeafTree t = new TestLeafTree();                                        // Create a test tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    m.leafMake (t.sourceIndex);                                                 // Allocate a leaf
    m.leaf.unary.ones();                                                        // Fill leaf - leafMake will have set an otherwise full leaf to empty
    m.leafSplit(t.targetIndex, t.sourceIndex);                                  // Split a leaf
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
    m.leafJoinable(t.targetIndex, t.sourceIndex, t.le);                         // Can the split leaves be joined
    m.leafJoin    (t.targetIndex, t.sourceIndex);                               // Join leaves
    m.leafJoinable(t.targetIndex, t.sourceIndex, t.lf);                         // Can the joined leaves be joined
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
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24              98327               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                 23                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                 24                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
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
V  318     4                  0             unary     nodes.node.branchOrLeaf.leaf.unary
""");
    //stop(t.lef);
    t.lef.ok("""
T   At  Wide  Index       Value   Field name
S    0     4                  1   lefs
B    0     1                  1     le     le
B    1     1                  0     lf     lf
B    2     1                  0     lE     lE
B    3     1                  0     lF     lF
""");
  }

  static void test_branch_split_join()                                          // Split a branch
   {TestBranchTree t = new TestBranchTree();                                    // Create a test tree
    Mjaf           m = t.mjaf;                                                  // Bit machine to process the tree
    final Layout.Variable key = m.branchKey.like();                             // Join key

    m.branchMake (t.sourceIndex);                                               // Allocate a  branch

    m.branchSplitKey(t.sourceIndex, t.kn);                                      // Get the splitting key
    m.copy(key,                                                                 // Save splitting key
      t.kn.asLayout().get("branchKey").toVariable());

    m.branchStuck.unary.ones();                                                 // Fill branch - branchMake will have set an otherwise full branch to empty
    m.branchSplit(t.targetIndex, t.sourceIndex);                                // Split a branch
    m.execute();

    //stop(key);
    key.ok("""
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
    m.branchJoinable(t.targetIndex, t.sourceIndex, t.be);                       // Can the split branches be joined
    m.branchJoin    (t.targetIndex, key, t.sourceIndex);                        // Join branches
    m.branchJoinable(t.targetIndex, t.sourceIndex, t.bf);                       // Can the joined branches be joined
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
    m.leafSplitKey(t.nodeIndex, t.kd);                                          // Get the splitting key for node 0
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(t.kd);
    t.kd.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    24              16387   leafKeyData
V    0    12                  3     leafKey     leafKey
V   12    12                  4     leafData     leafData
""");

    m.copy(t.nodeIndex, 1);                                                     // Choose the node
    m.leafSplitKey(t.nodeIndex, t.kd);                                          // Get the splitting key for node 0
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(t.kd);
    t.kd.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    24              49163   leafKeyData
V    0    12                 11     leafKey     leafKey
V   12    12                 12     leafData     leafData
""");

    m.reset();
    m.copy(t.nodeIndex, 2);                                                     // Choose the node
    m.leafSplitKey(t.nodeIndex, t.kd);                                          // Get the splitting key for node 0
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(t.kd);
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
    m.branchSplitKey(t.nodeIndex, t.kn);                                        // Get the splitting key
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(t.kn.asLayout());
    t.kn.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    14               4098   branchKeyNext
V    0    12                  2     branchKey     branchKey
V   12     2                  1     branchNext     branchNext
""");

    m.copy(t.nodeIndex, 1);                                                     // Choose the node
    m.branchSplitKey(t.nodeIndex, t.kn);                                        // Get the splitting key
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(t.kn);
    t.kn.asLayout().ok("""
T   At  Wide  Index       Value   Field name
S    0    14               4101   branchKeyNext
V    0    12                  5     branchKey     branchKey
V   12     2                  1     branchNext     branchNext
""");

    m.reset();
    m.copy(t.nodeIndex, 2);                                                     // Choose the node
    m.branchSplitKey(t.nodeIndex, t.kn);                                        // Get the splitting key
    m.execute();                                                                // Execute the code to copy out the splitting key
    //stop(t.kn);
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

    m.leafMake   (t.nodeIndex);
    m.leafIsEmpty(t.nodeIndex, t.le);
    m.leafIsFull (t.nodeIndex, t.lf);
    m.leaf.unary.ones();
    m.leafIsEmpty(t.nodeIndex, t.lE);
    m.leafIsFull (t.nodeIndex, t.lF);
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

    m.branchMake   (t.nodeIndex);
    m.branchIsEmpty(t.nodeIndex, t.be);
    m.branchIsFull (t.nodeIndex, t.bf);
    m.branchStuck.unary.ones();
    m.branchIsEmpty(t.nodeIndex, t.bE);
    m.branchIsFull (t.nodeIndex, t.bF);
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
    m.leafFirstGreaterThanOrEqual(t.nodeIndex, k, i, f);
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
    Layout.Variable k = m.makeKey (8);                                          // Key to locate
    Layout.Variable n = m.makeNext(0);                                          // Located next node index

    m.copy(t.nodeIndex, 2);
    m.branchFindFirstGreaterOrEqual(t.nodeIndex, k, n);
    m.execute();
    //stop(k, n);
    n.ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  2   next
""");
   }

  static void test_from_keyDataNext()                                           // Get the key and data from a key, data pair or the key and next from a key, next pair
   {TestLeafTree t = new TestLeafTree();                                        // Allocate a new leaf treetree tree
    Mjaf     m = t.mjaf;                                                        // Bit machine to process the tree

    Layout.Variable key  = m.makeKey (6);
    Layout.Variable data = m.makeData(7);
    Layout.Variable next = m.makeNext(3);
    LayoutAble        kd = m.makeKeyData(key, data);
    LayoutAble        kn = m.makeKeyNext(key, next);
    Layout.Variable   Kd = m.keyFromKeyData(kd);
    Layout.Variable   kD = m.dataFromKeyData(kd);
    Layout.Variable   Kn = m.keyFromKeyNext(kn);
    Layout.Variable   kN = m.nextFromKeyNext(kn);
    m.execute();

    //stop(Kd, kD, Kn, kN);
    Kd.copy().ok("""
T   At  Wide  Index       Value   Field name
V    0    12                  6     leafKey
""");
    kD.copy().ok("""
T   At  Wide  Index       Value   Field name
V   12    12                  7     leafData
""");
    Kn.copy().ok("""
T   At  Wide  Index       Value   Field name
V    0    12                  6     branchKey
""");
    kN.copy().ok("""
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
    Layout.Variable k = m.makeKey(6);                                           // Key to locate
    Layout.Variable i = m.branchFindFirstGreaterOrEqualIndex(t.nodeIndex, k);   // Find index of key
    m.execute();
    //stop(i);
    i.ok("""
T   At  Wide  Index       Value   Field name
V    0     3                  0   branchIndex
""");

    m.reset();
    m.copy(k, 7);
    i = m.branchFindFirstGreaterOrEqualIndex(t.nodeIndex, k);                   // Find index of key
    m.execute();
    //stop(i);
    i.ok("""
T   At  Wide  Index       Value   Field name
V    0     3                  0   branchIndex
""");

    m.reset();
    m.copy(k, 8);
    i = m.branchFindFirstGreaterOrEqualIndex(t.nodeIndex, k);                   // Find index of key
    m.execute();
    //stop(i);
    i.ok("""
T   At  Wide  Index       Value   Field name
V    0     3                  1   branchIndex
""");
   }

  static void test_branch_top_next()                                            // Get and set the top next field in a branch
   {TestBranchTree  t = new TestBranchTree();                                   // Create a test tree
    Mjaf            m = t.mjaf;                                                 // Bit machine to process the tree
    Layout.Variable n = m.makeNext(0);                                          // Located next node index

    m.copy(t.nodeIndex, 2);
    m.branchGetTopNext(t.nodeIndex, n);
    m.execute();
    //stop(n);
    n.ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  2   next
""");

    m.reset();
    m.copy(n, 1);
    m.branchSetTopNext(t.nodeIndex, n);
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

    m.leafSplitRoot(t.sourceIndex, t.targetIndex);
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
B   16     1                  0         isLeaf     nodes.node.isLeaf
B   17     1                  1         isBranch     nodes.node.isBranch
U   18   100                            branchOrLeaf     nodes.node.branchOrLeaf
S   18    47                              branch     nodes.node.branchOrLeaf.branch
S   18    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A   18    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   18    14               4099                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   18    12                  3                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   30     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   32    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   32    14               7170                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   32    12               3074                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   44     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   46    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   46    14               2048                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   46    12               2048                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
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
S   18    47                  0           branch     nodes.node.branchOrLeaf.branch
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

    m.branchSplitRoot(t.sourceIndex, t.targetIndex);
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
S  222    47                  0           branch     nodes.node.branchOrLeaf.branch
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
    m.find(key, found, data);
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
    m.find(key, found, data);
    m.execute();
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    25              32775   s
B    0     1                  1     found     found
V    1    12                  3     key     key
V   13    12                  4     data     data
""");

    m.reset();
    m.leafSplitRoot(t.sourceIndex, t.targetIndex);                              // Create a branch and two leaves
    m.execute();

    m.reset();
    m.copy(key, 1);                                                             // Key to locate in left leaf
    m.find(key, found, data);
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    25              16387   s
B    0     1                  1     found     found
V    1    12                  1     key     key
V   13    12                  2     data     data
""");

    m.reset();
    m.copy(key, 7);                                                             // Key to locate in right leaf
    m.find(key, found, data);
    m.execute();
   //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    25              65551   s
B    0     1                  1     found     found
V    1    12                  7     key     key
V   13    12                  8     data     data
""");
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
    m.leafSplitRoot(t.sourceIndex, t.targetIndex);                              // Create a branch and two leaves
    m.execute();

    m.reset();
    m.copy(key,   2);                                                           // Key to add
    m.copy(data, 22);                                                           // Data to add
    m.findAndInsert(key, data, found);
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
   {TestLeafTree     t = new TestLeafTree();                                     // Create a test tree
    Mjaf             m = t.mjaf;                                                 // Bit machine to process the tree

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
S   16   102                  1       node     nodes.node
B   16     1                  1         isLeaf     nodes.node.isLeaf
B   17     1                  0         isBranch     nodes.node.isBranch
""");

    m.reset();
    m.leafSplitRoot(t.sourceIndex, t.targetIndex);
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

    Layout.Variable key  = m.makeKey(6);
    Layout.Variable data = m.makeData(6);
    m.leafSplitRoot(t.sourceIndex, t.targetIndex);
    m.leafInsertPair(t.targetIndex, key, data);
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

  static void test_leaf_fission()                                               // Split a leaf and insert the split results into the parent branch
   {TestLeafTree     t = new TestLeafTree();                                    // Create a test tree
    Mjaf             m = t.mjaf;                                                // Bit machine to process the tree

    Layout.Variable l = m.leafMake();
    Layout.Variable b = m.branchMake();

    m.leafPush(l, m.makeKeyData(m.makeKey(10), m.makeData(11)));
    m.leafPush(l, m.makeKeyData(m.makeKey(20), m.makeData(22)));
    m.leafPush(l, m.makeKeyData(m.makeKey(30), m.makeData(33)));
    m.leafPush(l, m.makeKeyData(m.makeKey(40), m.makeData(44)));

    m.branchPush(b, m.makeKeyNext(m.makeKey(25), m.makeNext(0)));
    m.branchPush(b, m.makeKeyNext(m.makeKey(45), l));
    m.execute();
    //stop(b, l);

    b.copy().ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  1   branch
""");

    l.copy().ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  2   leaf
""");

    //stop(m.layout);

    m.ok("""
A  118   306      1                 nodes     nodes
S  118   102                  2       node     nodes.node
B  118     1                  0         isLeaf     nodes.node.isLeaf
B  119     1                  1         isBranch     nodes.node.isBranch
U  120   100                  0         branchOrLeaf     nodes.node.branchOrLeaf
S  120    47                  0           branch     nodes.node.branchOrLeaf.branch
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
T   At  Wide  Index       Value   Field name
S    0   322                      tree
S    0     9                 36     nodesFree     nodesFree
A    0     6      0          36       array     nodesFree.array
V    0     2                  0         nodeFree     nodesFree.array.nodeFree
A    2     6      1          36       array     nodesFree.array
V    2     2                  1         nodeFree     nodesFree.array.nodeFree
A    4     6      2          36       array     nodesFree.array
V    4     2                  2         nodeFree     nodesFree.array.nodeFree
V    6     3                  0       unary     nodesFree.unary
V    9     2                  3     nodesCreated     nodesCreated
V   11     2                  3     keyDataStored     keyDataStored
V   13     2                  0     =root     root
B   15     1                  1     hasNode     hasNode
A   16   306      0                 nodes     nodes
S   16   102                  1       node     nodes.node
B   16     1                  1         isLeaf     nodes.node.isLeaf
B   17     1                  0         isBranch     nodes.node.isBranch
U   18   100                  0         branchOrLeaf     nodes.node.branchOrLeaf
S   18    47                  0           branch     nodes.node.branchOrLeaf.branch
S   18    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A   18    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   18    14              12298                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   18    12                 10                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   30     2                  3                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   32    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   32    14               4098                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   32    12                  2                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   44     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   46    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   46    14               5633                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   46    12               1537                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   58     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V   60     3                  0               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V   63     2                  0             topNext     nodes.node.branchOrLeaf.branch.topNext
S   18   100                              leaf     nodes.node.branchOrLeaf.leaf
A   18    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S   18    24              45066               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   18    12                 10                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   30    12                 11                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   42    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S   42    24              90132               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   42    12                 20                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   54    12                 22                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   66    96      2                         array     nodes.node.branchOrLeaf.leaf.array
S   66    24              24581               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   66    12                  5                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   78    12                  6                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   90    96      3                         array     nodes.node.branchOrLeaf.leaf.array
S   90    24              32775               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   90    12                  7                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  102    12                  8                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
V  114     4                  3             unary     nodes.node.branchOrLeaf.leaf.unary
A  118   306      1                 nodes     nodes
S  118   102                  2       node     nodes.node
B  118     1                  0         isLeaf     nodes.node.isLeaf
B  119     1                  1         isBranch     nodes.node.isBranch
U  120   100                  0         branchOrLeaf     nodes.node.branchOrLeaf
S  120    47                  0           branch     nodes.node.branchOrLeaf.branch
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
S  120   100                              leaf     nodes.node.branchOrLeaf.leaf
A  120    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  120    24             409620               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  120    12                 20                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  132    12                100                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  144    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  144    24            1966800               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  144    12                720                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  156    12                480                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
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
S  220   102                  1       node     nodes.node
B  220     1                  1         isLeaf     nodes.node.isLeaf
B  221     1                  0         isBranch     nodes.node.isBranch
U  222   100                  0         branchOrLeaf     nodes.node.branchOrLeaf
S  222    47                  0           branch     nodes.node.branchOrLeaf.branch
S  222    45                                branchStuck     nodes.node.branchOrLeaf.branch.branchStuck
A  222    42      0                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  222    14               4126                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  222    12                 30                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  234     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  236    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  236    14               8200                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  236    12                  8                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  248     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A  250    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S  250    14              11266                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V  250    12               3074                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V  262     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V  264     3                  0               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V  267     2                  0             topNext     nodes.node.branchOrLeaf.branch.topNext
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

    Layout.Variable b = m.branchMake();
    Layout.Variable B = m.branchMake();

    m.branchPush(b, m.makeKeyNext(m.makeKey(10), m.makeNext(0)));
    m.branchPush(b, m.makeKeyNext(m.makeKey(20), m.makeNext(1)));
    m.branchPush(b, m.makeKeyNext(m.makeKey(30), m.makeNext(2)));

    m.branchPush(B, m.makeKeyNext(m.makeKey(15), m.makeNext(0)));
    m.branchPush(B, m.makeKeyNext(m.makeKey(35), b));
    m.execute();
    //stop(b, B);

    b.copy().ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  2   branch
""");

    B.copy().ok("""
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

    //stop(m.layout);
    m.ok("""
T   At  Wide  Index       Value   Field name
S    0   322                      tree
S    0     9                 36     nodesFree     nodesFree
A    0     6      0          36       array     nodesFree.array
V    0     2                  0         nodeFree     nodesFree.array.nodeFree
A    2     6      1          36       array     nodesFree.array
V    2     2                  1         nodeFree     nodesFree.array.nodeFree
A    4     6      2          36       array     nodesFree.array
V    4     2                  2         nodeFree     nodesFree.array.nodeFree
V    6     3                  0       unary     nodesFree.unary
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
S   18    14                 10                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   18    12                 10                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   30     2                  0                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   32    42      1                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   32    14               4098                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   32    12                  2                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   44     2                  1                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
A   46    42      2                           array     nodes.node.branchOrLeaf.branch.branchStuck.array
S   46    14               8195                 branchKeyNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext
V   46    12                  3                   branchKey     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchKey
V   58     2                  2                   branchNext     nodes.node.branchOrLeaf.branch.branchStuck.array.branchKeyNext.branchNext
V   60     3                  1               unary     nodes.node.branchOrLeaf.branch.branchStuck.unary
V   63     2                  1             topNext     nodes.node.branchOrLeaf.branch.topNext
S   18   100                              leaf     nodes.node.branchOrLeaf.leaf
A   18    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S   18    24              32778               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   18    12                 10                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   30    12                  8                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A   42    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S   42    24             393268               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V   42    12                 52                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V   54    12                 96                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
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
S  120   100                              leaf     nodes.node.branchOrLeaf.leaf
A  120    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  120    24             327695               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  120    12                 15                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  132    12                 80                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  144    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  144    24            1966640               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  144    12                560                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  156    12                480                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
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
V  267     2                  2             topNext     nodes.node.branchOrLeaf.branch.topNext
S  222   100                              leaf     nodes.node.branchOrLeaf.leaf
A  222    96      0                         array     nodes.node.branchOrLeaf.leaf.array
S  222    24             499742               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  222    12                 30                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  234    12                122                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
A  246    96      1                         array     nodes.node.branchOrLeaf.leaf.array
S  246    24             393704               leafKeyData     nodes.node.branchOrLeaf.leaf.array.leafKeyData
V  246    12                488                 leafKey     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafKey
V  258    12                 96                 leafData     nodes.node.branchOrLeaf.leaf.array.leafKeyData.leafData
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
   }

  static void test_put()                                                        // Load a Btree
   {final int BitsPerKey = 8, BitsPerData = 8, MaxKeysPerLeaf = 4, size = 4;    // Dimensions of Btree
    final Mjaf m = mjaf(BitsPerKey, BitsPerData, MaxKeysPerLeaf, size);         // Crete Btree

    m.put(m.makeKey(1), m.makeData(11));
    m.execute();

    //stop(m.layout);

    m.layout.ok("""
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

    test_root_is_leaf_or_branch();
    test_find();
    test_find_and_insert();
    test_leaf_insert_pair();
    test_from_keyDataNext();
    test_branch_greater_than_or_equal_index();

    test_leaf_fission();                test_branch_fission();

    //test_put();
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    //test_put();
   }

  public static void main(String[] args)                                        // Test if called as a program
   {try                                                                         // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_actions) oldTests(); else newTests();                          // Tests to run
      testSummary();                                                            // Summarize test results
     }
    catch(Exception e)                                                          // Get a traceback in a format clickable in Geany
     {System.err.println(e);
      System.err.println(fullTraceBack(e));
     }
   }
 }
