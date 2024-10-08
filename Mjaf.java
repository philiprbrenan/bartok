//------------------------------------------------------------------------------
// Btree in bit machine assembler.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Design, simulate and layout  a binary tree on a silicon chip.

class Mjaf extends BitMachine                                                   // Btree algorithm but with data stored only in the leaves to facilitate deletion without complicating search or insertion. The branches (interior nodes) have an odd number of keys to make the size of a branch as close to that of a leaf as possible to simplify memory management.
 {final int bitsPerKey;                                                         // Number of bits in key
  final int bitsPerData;                                                        // Number of bits in data
  final int bitsPerNext;                                                        // Number of bits in next level
  final int maxKeysPerLeaf;                                                     // The maximum number of keys per leaf.  This should be an even number greater than three. The maximum number of keys per branch is one less. The normal Btree algorithm requires an odd number greater than two for both leaves and branches.  The difference arises because we only store data in leaves not in leaves and branches as does the classic Btree algorithm.
  final int maxKeysPerBranch;                                                   // The maximum number of keys per branch.
  final int maxNodes;                                                           // The maximum number of nodes in the tree

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
  final Layout.Variable  topNode;                                               // Next node if search key is greater than all keys in this node
  final Layout.Structure branch;                                                // Branch node of the tree
  final Layout.Structure leafKeyData;                                           // An entry in a leaf node
  final Layout.Structure branchKeyNext;                                         // An entry in a branch node
  final Layout.Union     branchOrLeaf;                                          // Branch or leaf of the tree
  final Layout.Variable  isBranch;                                              // The node is a branch if true
  final Layout.Variable  isLeaf;                                                // The node is a leaf if true
  final Layout.Structure node;                                                  // Node of the tree
  final Layout.Array     nodes;                                                 // Array of nodes comprising tree
  final Layout.Structure tree;                                                  // Structure of tree
  final Layout           layoutLeafKeyData;                                     // Layout of a leaf key data pair
  final Layout           layoutBranchKeyNext;                                   // Layout of a branch key next pair
  final Layout           layout;                                                // Layout of tree

  final Layout.Variable  leafSplitIdx;                                          // Index of splitting key
  final Layout.Structure workStructure;                                         // Work structure
  final Layout           work;                                                  // Memory work area for temporary, intermediate results

//D1 Construction                                                               // Create a Btree from nodes which can be branches or leaves.  The data associated with the Btree is stored only in the leaves opposite the keys

  Mjaf(int BitsPerKey, int BitsPerData, int MaxKeysPerLeaf, int size)           // Define a Btree with a specified maximum number of keys per leaf.
   {super();
    final int N = MaxKeysPerLeaf;                                               // Assign a shorter name
    bitsPerKey  = BitsPerKey;
    bitsPerNext = BitsPerKey + 1;
    bitsPerData = BitsPerData;

    if (N % 2 == 1) stop("# keys per leaf must be even not odd:", N);
    if (N     <= 3) stop("# keys per leaf must be greater than three, not:", N);
    maxKeysPerLeaf   = N;                                                       // Some even number
    maxKeysPerBranch = N-1;                                                     // Ideally should be some number that makes the leaf nodes and the branch nodes the same size
    maxNodes         = size;

    final Layout W   = work = new Layout();                                     // Layout of working memory
    leafSplitIdx     = W.new Variable ("leafSplitIdx",  N);                     // Index of leaf splitting key
    workStructure    = W.new Structure("workStructure", leafSplitIdx);          // An entry in a leaf node
    W.layout(workStructure);                                                    // Layout of a leaf key data pair
    leafSplitIdx.fromUnary(maxKeysPerBranch >> 1);                              // Index of splitting key in leaf

    final Layout L   = layoutLeafKeyData = new Layout();                        // Layout of a leaf key data pair
    leafKey          = L.new Variable ("leafKey",  bitsPerKey);                 // Key in a leaf
    leafData         = L.new Variable ("leafData", bitsPerData);                // Data in a leaf
    leafKeyData      = L.new Structure("leafKeyData", leafKey, leafData);       // An entry in a leaf node
    layoutLeafKeyData.layout(leafKeyData);                                      // Layout of a leaf key data pair

    leaf             =   new Stuck("leaf",                                      // Leaf key, data pairs stuck
      maxKeysPerLeaf,layoutLeafKeyData);

    final Layout B   = layoutBranchKeyNext = new Layout();                      // An entry in a branch node
    branchKey        = B.new Variable ("branchKey",  bitsPerKey);               // Key in a branch
    branchNext       = B.new Variable ("branchNext", bitsPerData);              // Next from a branch
    branchKeyNext    = B.new Structure("branchKeyNext", branchKey, branchNext); // An entry in a branch node
    layoutBranchKeyNext.layout(branchKeyNext);                                  // Layout of a branch key next pair

    branchStuck    = new Stuck("branchStuck",                                   // Branch key, next pairs stuck
      maxKeysPerBranch, layoutBranchKeyNext);


    final Layout T = layout = new Layout();                                     // Tree level layout
    nodesCreated   = T.variable ("nodesCreated",   bitsPerNext);                // Number of nodes created
    keyDataStored  = T.variable ("keyDataStored",  bitsPerNext);                // Field to track number of keys stored in twos complement form hence an extra bit for the sign
    root           = T.variable ("root",           bitsPerNext);                // Root
    nodeFree       = T.variable ("nodeFree",       bitsPerNext);                // Index of a free node as a positive binary integer
    nodesFree      = new Stuck  ("free", size,     nodeFree.duplicate());       // Free nodes stuck

    topNode        = T.variable ("topNode",        bitsPerNext);                // Next node if search key is greater than all keys in this node
    branch         = T.structure("branch",         branchStuck, topNode);       // Branch of the tree

    branchOrLeaf   = T.union    ("branchOrLeaf",   branch, leaf);               // Branch or leaf of the tree
    isBranch       = T.variable ("isBranch",       1);                          // The node is a branch if true
    isLeaf         = T.variable ("isLeaf",         1);                          // The node is a leaf if true
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

    bitMachine(nodesFree, branchStuck, leaf); bitMachines(this);                // Place all the instruction that woukld oether be generated in these anchines into this machine instead
   }

  static Mjaf mjaf(int Key, int Data, int MaxKeysPerLeaf, int size)             // Define a Btree with a specified maximum number of keys per leaf.
   {return new Mjaf(Key, Data, MaxKeysPerLeaf, size);
   }

  void size     (Layout.Variable size) {copy(size, keyDataStored);}             // Number of entries in the tree
  void emptyTree(Layout.Bit    result) {copy(result, hasNode); not(result);}    // Test for an empty tree

//D1 Leaf                                                                       // Process a leaf

  void leafInsert(Layout.Variable iLeaf, Layout.Variable index, Layout kd)      // Place the specified key, data pair at the specified location in the specified leaf
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

  void leafSplitKey(Layout.Variable index, Layout out)                          // Splitting key in a leaf
   {setIndex(nodes, index);
    leaf.elementAt(out, leafSplitIdx);
   }

//D1 Node                                                                       // A branch or a leaf
/*
  class Node                                                                    // A node contains a leaf or a branch
   {final int   index;                                                          // Index of node
    Node()                         {index = root.toInt();}                      // Root node
    Node(int    Index)             {index = Index;}                             // Node from an index - useful in testing
    Node(Branch branch)            {index = branch.index;}                      // Node from a branch
    Node(Leaf   leaf)              {index = leaf.index;}                        // Node from a leaf
    Node(Memory memory)            {index = Layout.toInt();}                    // Node from memory
    Memory node() {return memoryFromInt(bitsPerNext, index);}                   // Create memory representing a node index

    boolean isLeaf  () {nodes.setIndex(index); return isLeaf  .get(0);}         // Whether the node represents a leaf
    boolean isBranch() {nodes.setIndex(index); return isBranch.get(0);}         // Whether the node represents a branch

    void setRoot()  {root.set(index);}                                          // Make this node the root node
    Branch branch() {return new Branch(index);}                                 // Locate branch described by this node
    Leaf   leaf()   {return new Leaf  (index);}                                 // Locate leaf described by this node

    void printHorizontally(java.util.Stack<StringBuilder>S, int level,          // Print leaf horizontally
                            boolean debug)
     {nodes.setIndex(index);
      padStrings(S, level);
      S.elementAt(level).append(debug ? toString() : shortString());
      padStrings(S, level);
     }

    void free()                                                                 // Free a branch or leaf
     {nodes.setIndex(index);                                                    // Address node
      clear();                                                                  // Clear the node
      nodesFree.push(index);                                                    // Put node on free chain
     }

    void clear() {nodes.setIndex(index); node.zero();}                          // Clear a branch or leaf

    public String shortString() {return "";}                                    // Print a leaf or branch compactly

    void inc() {nodes.setIndex(index); keyDataStored.inc();}                    // Increment insertion count
    void dec() {nodes.setIndex(index); keyDataStored.dec();}                    // Decrement insertion count

    void ok(String expected)                                                    // Check leaf or branch is as expected
     {nodes.setIndex(index);
      Mjaf.ok(toString(), expected);
     }
   }

//D1 Branch                                                                     // Methods applicable to a branch

  class Branch extends Node                                                     // Describe a branch
   {Branch(int Index) {super(Index);}                                           // Address the branch at the specified index in the memory layout

    Branch(Node top)                                                            // Create a branch with a specified top node
     {this(nodesFree.pop().toInt());                                            // Index of next free node
      clear();                                                                  // Clear the branch
      nodes.setIndex(index); isBranch.set(1);                                   // Mark as a branch
      topNode.set(top.node());                                                  // Set the top node for this branch
     }

    boolean isEmpty() {nodes.setIndex(index);return branchKeyNames.isEmpty();}  // Branch is empty
    boolean isFull()  {nodes.setIndex(index);return branchKeyNames.isFull();}   // Branch is full

    int nKeys() {nodes.setIndex(index); return branchKeyNames.stuckSize();}     // Number of keys in a branch

    Key splitKey()                                                              // Splitting key for branch
     {nodes.setIndex(index);
      return new Key(getKey(splitIdx).duplicate());
     }

    void pushKey(Key memory)                                                    // Push a key into a branch
     {nodes.setIndex(index); branchKeyNames.push(memory);
     }

    void pushNext(Node node)                                                    // Push a next level into a branch
     {nodes.setIndex(index);
      nextLevel.push(node.node());
     }

    Key shiftKey()                                                              // Shift a key into a branch
     {nodes.setIndex(index);
      return new Key(branchKeyNames.shift());
     }

    Node shiftNext()                                                            // Shift index of next node
     {nodes.setIndex(index);
      return new Node(nextLevel.shift());
     }

    Key popKey()                                                                // Pop a key into a branch
     {nodes.setIndex(index);
      return new Key(branchKeyNames.pop());
     }

    Node popNext()                                                              // Pop index of next node
     {nodes.setIndex(index);
      return new Node(nextLevel.pop());
     }

    void insertKey(Key key, int i)                                              // Insert a key into a branch
     {nodes.setIndex(index);
      branchKeyNames.insertElementAt(key, i);
     }

    void insertNext(Node node, int i)                                           // Insert index of next level node for this branch
     {nodes.setIndex(index);
      nextLevel.insertElementAt(node.node(), i);
     }

    Key getKey(int i)                                                           // Get the indexed key in a branch
     {nodes.setIndex(index);
      return new Key(branchKeyNames.elementAt(i).duplicate());
     }

    Node getNext(int i)                                                         // Get the indexed index of the next level from this branch
     {nodes.setIndex(index);
      return new Node(nextLevel.elementAt(i).toInt());
     }

    int findIndexOfKey(Key key)                                                 // Find zero based index of a key in a branch
     {nodes.setIndex(index);
      return branchKeyNames.indexOf(key);
     }

    void removeKey(int i)                                                       // Remove the indicated key from the branch
     {nodes.setIndex(index);
      branchKeyNames.removeElementAt(i);
     }

    void removeNext(int i)                                                      // Remove the indicated next level from the branch
     {nodes.setIndex(index);
      nextLevel.removeElementAt(i);
     }

    Node lastNext()                                                             // Last next element in a branch, but not the top node
     {nodes.setIndex(index);
      return new Node(nextLevel.lastElement());
     }

    Node getTop()                                                               // Get the top node in a branch
     {nodes.setIndex(index);
      return new Node(topNode);
     }

    void setTop(int TopNode)                                                    // Set the top node in a branch
     {nodes.setIndex(index);
      topNode.set(TopNode);
     }

    void put(Key keyName, Node node)                                            // Put a key / next node index value pair into a branch
     {final int K = nKeys();                                                    // Number of keys currently in node
      for (int i = 0; i < K; i++)                                               // Search existing keys for a greater key
       {if (keyName.lessThanOrEqual(getKey(i)))                                                                  // Insert new key in order
         {insertKey (keyName, i);                                               // Insert key
          insertNext(node,    i);                                               // Insert data
          return;
         }
       }
      pushKey (keyName);                                                        // Either the branch is empty or the new key is greater than every existing key
      pushNext(node);
     }

    void splitRoot()                                                            // Split the root when it is a branch
     {if (isFull())
       {final Key    k = getKey(splitIdx);
        final Branch l = split();
        final Branch b = new Branch(new Node(index));                           // Make a new branch with its top node set to this node.
        b.put(k, l);
        b.setRoot();
       }
     }

    Branch split()                                                              // Split a branch into two branches at the indicated key
     {final int K = nKeys(), f = splitIdx;                                      // Number of keys currently in node
      if (f < K-1) {} else stop("Split", f, "too big for branch of size:", K);
      if (f <   1)         stop("First", f, "too small");
      final Node   t = getNext(f);                                              // Top node
      final Branch b = new Branch(t);                                           // Make a new branch with the top node set to the noed refenced by the splitting key

      for (int i = 0; i < f; i++)                                               // Remove first keys from old node to new node
       {final Key  k = shiftKey();
        final Node n = shiftNext();
        b.put(k, n);
       }
      shiftKey();                                                               // Remove central key which is no longer required
      shiftNext();
      return b;
     }

    boolean joinable(Branch a)                                                  // Check that we can join two branches
     {return nKeys() + a.nKeys() + 1 <= maxKeysPerBranch;
     }

    void join(Branch Join, Key joinKeyName)                                     // Append the second branch to the first one adding the specified key
     {final int K = nKeys(), J = Join.nKeys();                                  // Number of keys currently in branch
      if (K + 1 + J > maxKeysPerLeaf) stop("Join of branch has too many keys",
          K,"+1+",J, "greater than", maxKeysPerBranch);

      final Node t = Join.getTop();                                             // TopNode from branch being joined

      pushKey(joinKeyName);                                                     // Key to separate joined branches
      pushNext(getTop());                                                       // Push current top node
      topNode.set(t.index);                                                     // New top node is the one from teh branch being joined

      for (int i = 0; i < J; i++)                                               // Add right hand branch
       {final Key  k = Join.getKey (i);
        final Node n = Join.getNext(i);
        pushKey(k);                                                             // Push memory associated with key
        pushNext(n);
       }
      Join.free();
     }

    Node findFirstGreaterOrEqual(Key keyName)                                   // Find node associated with a key
     {final int N = nKeys();                                                    // Number of keys currently in node
      for (int i = 0; i < N; i++)                                               // Check each key
       {final Key     k = getKey(i);                                            // Key
        final boolean l = keyName.compareTo(k) <= 0;                            // Compare current key with search key
        if (l) return getNext(i);                                               // Current key is greater than or equal to the search key
       }
      return getTop();                                                          // Key is greater than all the keys in the branch
     }

    public String toString()                                                    // Print branch
     {final StringBuilder s = new StringBuilder();
      s.append("Branch_"+index+"(");
      final int K = nKeys();
      for  (int i = 0; i < K; i++)
       {s.append(""+getKey(i).toInt()+":"+getNext(i).index+", ");
       }
      if (K > 0) s.setLength(s.length()-2);
      s.append((K > 0 ? ", " : "")+topNode.toInt()+")");
      return s.toString();
     }

    public String shortString()                                                 // Print a branch compactly
     {final StringBuilder s = new StringBuilder();
      final int K = nKeys();
      for  (int i = 0; i < K; i++) s.append(""+getKey(i).toInt()+",");
      if (K > 0) s.setLength(s.length()-1);                                     // Remove trailing comma
      return s.toString();
     }

    void printHorizontally(java.util.Stack<StringBuilder>S, int level,          // Print branch horizontally
                            boolean debug)
     {final int N = nKeys();
      for (int i = 0; i < N; i++)
       {final Node n = getNext(i);
        if (n.isBranch())
         {final Branch b = n.branch();
          b.printHorizontally(S, level+1, debug);
          padStrings(S, level);
          S.elementAt(level).append(""+getKey(i).toInt()+" ");
         }
        else
         {n.leaf().printHorizontally(S, level+1, debug);
          padStrings(S, level);
          S.elementAt(level).append(getKey(i).toInt()+" ");
         }
       }
      if (getTop().isBranch())
       {getTop().branch().printHorizontally(S, level+1, debug);
       }
      else
       {getTop().leaf().printHorizontally(S, level+1, debug);
       }
     }
   }

//D1 Leaf                                                                       // Methods applicable to a leaf

  class Leaf extends Node                                                       // Describe a leaf
   {Leaf(int Index) {super(Index);}                                             // Address a leaf by index

    Leaf()                                                                      // Create a leaf
     {this(nodesFree.pop().toInt());                                            // Index of next free node
      clear();                                                                  // Clear the leaf
      nodes.setIndex(index); isLeaf.set(1);                                     // Mark as a leaf
     }

    boolean isEmpty() {nodes.setIndex(index); return leafKeyNames.isEmpty();}   // Leaf is empty
    boolean isFull()  {nodes.setIndex(index); return leafKeyNames.isFull();}    // Leaf is full

    int nKeys() {nodes.setIndex(index); return leafKeyNames.stuckSize();}       // Number of keys in a leaf
    int nData() {nodes.setIndex(index); return dataValues  .stuckSize();}       // Number of data values in a leaf

    Key splitKey()                                                              // Splitting key in a leaf
     {nodes.setIndex(index);
      return new Key(leafKeyNames.elementAt(splitIdx).duplicate());
     }

    void pushKey(Key memory)                                                    // Push a key into a leaf
     {nodes.setIndex(index);
      leafKeyNames.push(memory);
     }

    void pushData(Memory memory)                                                // Push a data value into a leaf
     {nodes.setIndex(index);
      dataValues.push(memory);
     }

    Key shiftKey()                                                              // Push a key into a leaf
     {nodes.setIndex(index);
      return new Key(leafKeyNames.shift());
     }

    Data shiftData()                                                            // Push a data value into a leaf
     {nodes.setIndex(index);
      return new Data(dataValues.shift());
     }

    void insertKey(Key key, int i)                                              // Push a key into a leaf
     {nodes.setIndex(index);
      leafKeyNames.insertElementAt(key, i);
     }

    void insertData(Data data, int i)                                           // Push a data value into a leaf
     {nodes.setIndex(index);
      dataValues  .insertElementAt(data, i);
     }

    Key getKey(int i)                                                           // Get the indexed key in a leaf
     {nodes.setIndex(index);
      return new Key(leafKeyNames.elementAt(i));
     }

    Data getData(int i)                                                         // Get the indexed data value in a leaf
     {nodes.setIndex(index);
      return new Data(dataValues.elementAt(i));
     }

    void setData(Data data, int i)                                              // Set the index element to the specified data
     {nodes.setIndex(index);
      dataValues.setElementAt(data, i);
     }

    int findIndexOfKey(Key key)                                                 // Get the 0 based index of a key on the leaf
     {nodes.setIndex(index);
      return leafKeyNames.indexOf(key);
     }

    void removeKey(int i)                                                       // Remove the indicated key from the leaf
     {nodes.setIndex(index);
      leafKeyNames.removeElementAt(i);
     }

    void removeData(int i)                                                      // Remove the indicated data from the leaf
     {nodes.setIndex(index);
      dataValues.removeElementAt(i);
     }

    void put(Key keyName, Data dataValue)                                       // Put a key / data value pair into a leaf
     {final int K = nKeys();                                                    // Number of keys currently in node
      if (K >= maxKeysPerLeaf) stop("Too many keys in leaf");

      for (int i = 0; i < K; i++)                                               // Search existing keys for a greater key
       {final Memory k = leafKeyNames.elementAt(i);                             // Current key
        if (keyName.lessThanOrEqual(k))                                         // Insert new key in order
         {insertKey (keyName,   i);                                             // Insert key
          insertData(dataValue, i);                                             // Insert data
          inc();                                                                // Created a new entry in the leaf
          return;
         }
       }
      pushKey (keyName);                                                        // Either the leaf is empty or the new key is greater than every existing key
      pushData(dataValue);
      inc();                                                                    // Created a new entry in the leaf
     }

    Leaf split()                                                                // Split the leaf into two leafs - the new leaf consists of the indicated first elements, the old leaf retains the rest
     {final int K = nKeys(), f = maxKeysPerLeaf/2;                              // Number of keys currently in node
      if (f < K) {} else stop("Split", f, "too big for leaf of size:", K);
      if (f < 1)         stop("First", f, "too small");
      final Leaf l = new Leaf();                                                // New leaf
      for (int i = 0; i < f; i++)                                               // Transfer keys and data
       {final Key  k = shiftKey ();                                             // Current key as memory
        final Data d = shiftData();                                             // Current data as memory
        l.pushKey(k);                                                           // Transfer keys
        l.pushData(d);                                                          // Transfer data
       }
      return l;                                                                 // Split out leaf
     }

    boolean joinable(Leaf a)                                                    // Check that we can join two leaves
     {return nKeys() + a.nKeys() <= maxKeysPerLeaf;
     }

    void join(Leaf Join)                                                        // Join the specified leaf onto the end of this leaf
     {final int K = nKeys(), J = Join.nKeys();                                  // Number of keys currently in node
      if (!joinable(Join)) stop("Join of leaf has too many keys", K,
        "+", J, "greater than", maxKeysPerLeaf);

      for (int i = 0; i < J; i++)                                               // Add each key/data from the leaf being joined
       {final Key  k = Join.getKey(i);
        final Data d = Join.getData(i);
        pushKey(k);
        pushData(d);
       }
      Join.free();                                                              // Free the leaf that was joined
     }

    public String toString()                                                    // Print leaf
     {final StringBuilder s = new StringBuilder();
      s.append("Leaf_"+index+"(");
      final int K = leafKeyNames.stuckSize();
      for  (int i = 0; i < K; i++)
       {s.append(""+getKey(i).toInt()+":"+getData(i).toInt()+", ");
       }
      if (K > 0) s.setLength(s.length()-2);
      s.append(")");
      return s.toString();
     }

    public String shortString()                                                 // Print a leaf compactly
     {final StringBuilder s = new StringBuilder();
      final int K = nKeys();
      for  (int i = 0; i < K; i++) s.append(""+getKey(i).toInt()+",");
      if (K > 0) s.setLength(s.length()-1);
      return s.toString();
     }
   }

//D1 Search                                                                     // Find a key, data pair

  Data find(Key keyName)                                                        // Find a the data associated with a key
   {if (emptyTree()) return null;                                               // Empty tree
    Node q = new Node();                                                        // Root as a node

    for(int i = 0; i < 999 ; ++i)                                               // Step down through tree up to some reasonable limit
     {if (q.isLeaf()) break;                                                    // Stepped to a leaf
      q = q.branch().findFirstGreaterOrEqual(keyName);                          // Position of key
     }

    final Leaf l = q.leaf();                                                    // Reached a leaf
    final int g = l.findIndexOfKey(keyName);                                    // We have arrived at a leaf
    return g == -1 ? null : l.getData(g);                                       // Key is not or is present in leaf
   }

  boolean findAndInsert(Key keyName, Data dataValue)                            // Find the leaf for a key and insert the indicated key, data pair into if possible, returning true if the insertion was possible else false.
   {if (emptyTree())                                                            // Empty tree so we can insert directly
     {final Leaf l = new Leaf();                                                // Create the root as a leaf
      l.setRoot();                                                              // Set the roof as a leaf
      l.put(keyName, dataValue);                                                // Insert key, data pair in the leaf
      return true;                                                              // Successfully inserted
     }

    Node q = new Node();                                                        // Start at the root
    for(int i = 0; i < 999 ; ++i)                                               // Step down through tree up to some reasonable limit
     {if (q.isLeaf()) break;                                                    // Stepped to a leaf
      q = q.branch().findFirstGreaterOrEqual(keyName);                          // Stepped through to a branch
     }

    final Leaf l = q.leaf();                                                    // Reached a leaf
    final int g = l.findIndexOfKey(keyName);                                    // We have arrived at a leaf
    if (g != -1) l.setData(dataValue, g);                                       // Key already present in leaf
    else if (l.isFull()) return false;                                          // There's no room in the leaf so return false
    else l.put(keyName, dataValue);                                             // On a leaf that is not full so we can insert directly
    return true;                                                                // Inserted directly
   }

//D1 Insertion                                                                  // Insert keys and data into the Btree

  void put(Key keyName, Data dataValue)                                         // Insert a new key, data pair into the Btree
   {if (findAndInsert(keyName, dataValue)) return;                              // Do a fast insert if possible, this is increasingly likely in trees with large leaves

    if (new Node().isLeaf())                                                    // Insert into root as a leaf
     {final Leaf r = new Node().leaf();                                         // Root is a leaf
      if (!r.isFull()) r.put(keyName, dataValue);                               // Still room in the root while it is is a leaf
      else                                                                      // Insert into root as a leaf which is full
       {final Leaf   l = r.split();                                             // New left hand side of root
        final Key    k = l.splitKey();                                          // Splitting key
        final Branch b = new Branch(new Node(r.index));                         // New root with old root to right
        b.put(k, new Node(l));                                                  // Insert left hand node all of whose elements are less than the first element of what was the root
        final Leaf f = keyName.lessThanOrEqual(k) ? l : r;                      // Choose leaf
        f.put(keyName, dataValue);                                              // Place in leaf
        b.setRoot();                                                            // The root now has just one entry in it - the splitting eky
       }
      return;
     }
    else new Node().branch().splitRoot();                                       // Split full root which is a branch not a leaf

    Branch p = new Node().branch();                                             // The root has already been split so the parent child relationship will be established
    Node   q = p;                                                               // Child of parent

    for(int i = 0; i < 999; ++i)                                                // Step down through tree to find the required leaf, splitting as we go
     {if (q.isLeaf()) break;                                                    // Stepped to a leaf
      final Branch b = q.branch();

      if (b.isFull())                                                           // Split the branch because it is full and we might need to insert below it requiring a slot in this node
       {final Key    k = b.splitKey();                                          // Splitting key
        final Branch l = b.split();                                             // New lower node
        p.put(k, l);                                                            // Place splitting key in parent
        final Branch r = new Node().branch();
        r.splitRoot();                                                          // Root might need to be split to re-establish the invariants at start of loop
        if (keyName.lessThanOrEqual(k)) q = l;                                  // Position on lower node if search key is less than splitting key
       }

      p = q.branch();                                                           // Step parent down
      q = p.findFirstGreaterOrEqual(keyName);                                   // Step through to next child
     }

    final Leaf l = q.leaf();                                                    // Address leaf
    final int g = l.findIndexOfKey(keyName);                                    // Locate index of key
    if (g != -1) l.setData(dataValue, g);                                       // Key already present in leaf
    else if (l.isFull())                                                        // Split the leaf because it is full
     {final Key  k = l.splitKey();                                              // Splitting key
      final Leaf e = l.split();                                                 // Split left key
      p.put(k, e);                                                              // Put splitting key in parent

      if (keyName.lessThanOrEqual(k)) e.put(keyName, dataValue);                // Insert key in the appropriate split leaf
      else                            l.put(keyName, dataValue);
     }
    else l.put(keyName, dataValue);                                             // On a leaf that is not full so we can insert directly
   } // put


//D1 Deletion                                                                   // Delete a key from a Btree

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

  static void test_create_empty_tree()
   {final int N = 2, M = 4;
    Mjaf m = mjaf(N, N, M, N);
    //stop(m.layout);
    m.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    62                  0   tree
S    0     8                200     free
A    0     6      0           8       array
V    0     3                  0         nodeFree
A    3     6      1           8       array
V    3     3                  1         nodeFree
V    6     2                  3       unary
V    8     3                  0     nodesCreated
V   11     3                  0     keyDataStored
V   14     3                  0     root
B   17     1                  0     hasNode
A   18    44      0           0     nodes
S   18    22                  0       node
V   18     1                  0         isLeaf
V   19     1                  0         isBranch
U   20    20                  0         branchOrLeaf
S   20    18                  0           branch
S   20    15                  0             branchStuck
A   20    12      0           0               array
S   20     4                  0                 branchKeyNext
V   20     2                  0                   branchKey
V   22     2                  0                   branchNext
A   24    12      1           0               array
S   24     4                  0                 branchKeyNext
V   24     2                  0                   branchKey
V   26     2                  0                   branchNext
A   28    12      2           0               array
S   28     4                  0                 branchKeyNext
V   28     2                  0                   branchKey
V   30     2                  0                   branchNext
V   32     3                  0               unary
V   35     3                  0             topNode
S   20    20                  0           leafStuck
A   20    16      0           0             array
S   20     4                  0               leafKeyData
V   20     2                  0                 leafKey
V   22     2                  0                 leafData
A   24    16      1           0             array
S   24     4                  0               leafKeyData
V   24     2                  0                 leafKey
V   26     2                  0                 leafData
A   28    16      2           0             array
S   28     4                  0               leafKeyData
V   28     2                  0                 leafKey
V   30     2                  0                 leafData
A   32    16      3           0             array
S   32     4                  0               leafKeyData
V   32     2                  0                 leafKey
V   34     2                  0                 leafData
V   36     4                  0             unary
A   40    44      1           0     nodes
S   40    22                  0       node
V   40     1                  0         isLeaf
V   41     1                  0         isBranch
U   42    20                  0         branchOrLeaf
S   42    18                  0           branch
S   42    15                  0             branchStuck
A   42    12      0           0               array
S   42     4                  0                 branchKeyNext
V   42     2                  0                   branchKey
V   44     2                  0                   branchNext
A   46    12      1           0               array
S   46     4                  0                 branchKeyNext
V   46     2                  0                   branchKey
V   48     2                  0                   branchNext
A   50    12      2           0               array
S   50     4                  0                 branchKeyNext
V   50     2                  0                   branchKey
V   52     2                  0                   branchNext
V   54     3                  0               unary
V   57     3                  0             topNode
S   42    20                  0           leafStuck
A   42    16      0           0             array
S   42     4                  0               leafKeyData
V   42     2                  0                 leafKey
V   44     2                  0                 leafData
A   46    16      1           0             array
S   46     4                  0               leafKeyData
V   46     2                  0                 leafKey
V   48     2                  0                 leafData
A   50    16      2           0             array
S   50     4                  0               leafKeyData
V   50     2                  0                 leafKey
V   52     2                  0                 leafData
A   54    16      3           0             array
S   54     4                  0               leafKeyData
V   54     2                  0                 leafKey
V   56     2                  0                 leafData
V   58     4                  0             unary
""");
   }

  static void test_create_leaf()
   {final int W = 8, M = 4, N = 2;
    final String leaf = "tree.nodes.node.branchOrLeaf.leaf";

    Mjaf m = mjaf(W, W, M, N);

    Layout k0 = m.leafKeyData.getLayoutField().duplicate();
    Layout k1 = m.leafKeyData.getLayoutField().duplicate();
    Layout k2 = m.leafKeyData.getLayoutField().duplicate();
    Layout k3 = m.leafKeyData.getLayoutField().duplicate();
    Layout k  = m.leafKeyData.getLayoutField().duplicate();

    String lk = "leafKeyData.leafKey";
    String ld = "leafKeyData.leafData";
    k0.get(lk).fromInt(1); k0.get(ld).fromInt(11);
    k1.get(lk).fromInt(2); k1.get(ld).fromInt(22);
    k2.get(lk).fromInt(3); k2.get(ld).fromInt(33);
    k3.get(lk).fromInt(4); k3.get(ld).fromInt(44);

    Layout           t     = new Layout();
    Layout.Variable  i0    = t.variable ("i0",  M), n0 = t.variable ("n0",  N); Layout.Bit e0 = t.bit("e0"), f0 = t.bit("f0");
    Layout.Variable  i1    = t.variable ("i1",  M), n1 = t.variable ("n1",  N); Layout.Bit e1 = t.bit("e1"), f1 = t.bit("f1");
    Layout.Variable  i2    = t.variable ("i2",  M), n2 = t.variable ("n2",  N); Layout.Bit e2 = t.bit("e2"), f2 = t.bit("f2");
    Layout.Variable  i3    = t.variable ("i3",  M), n3 = t.variable ("n3",  N); Layout.Bit e3 = t.bit("e3"), f3 = t.bit("f3");
                                                                                Layout.Bit e4 = t.bit("e4"), f4 = t.bit("f4");
    Layout.Structure temp  = t.structure("struct", i0, i1, i2, i3, n0, n1, n2, n3, e0, e1, e2, e3, e4, f0, f1, f2, f3, f4);
    t.layout(temp);

    i0.fromUnary(0);  n0.fromInt(0);
    i1.fromUnary(1);  n1.fromInt(1);
    i2.fromUnary(2);  n2.fromInt(2);
    i3.fromUnary(3);  n3.fromInt(3);

                                                        m.leafIsEmpty(n0, e0); m.leafIsFull(n0, f0);
    m.leafInsert(n0, i0, k0); m.leafInsert(n1, i0, k0); m.leafIsEmpty(n0, e1); m.leafIsFull(n0, f1);
    m.leafInsert(n0, i1, k1); m.leafInsert(n1, i0, k1); m.leafIsEmpty(n0, e2); m.leafIsFull(n0, f2);
    m.leafInsert(n0, i2, k2); m.leafInsert(n1, i0, k2); m.leafIsEmpty(n0, e3); m.leafIsFull(n0, f3);
    m.leafInsert(n0, i3, k3); m.leafInsert(n1, i0, k3); m.leafIsEmpty(n0, e4); m.leafIsFull(n0, f4);
    m.leafSplitKey(n0, k);
    m.execute();

    m.nodes.setIndex(0);
    //stop(m.layout.get("tree.nodes.node.branchOrLeaf.leaf"));
    m.layout.get("tree.nodes.node.branchOrLeaf.leaf").copy().ok("""
T   At  Wide  Index       Value   Field name
S   50    68                              leaf
A   50    64      0                         array
S   50    16               2817               leafKeyData
V   50     8                  1                 leafKey
V   58     8                 11                 leafData
A   66    64      1                         array
S   66    16               5634               leafKeyData
V   66     8                  2                 leafKey
V   74     8                 22                 leafData
A   82    64      2                         array
S   82    16               8451               leafKeyData
V   82     8                  3                 leafKey
V   90     8                 33                 leafData
A   98    64      3                         array
S   98    16              11268               leafKeyData
V   98     8                  4                 leafKey
V  106     8                 44                 leafData
V  114     4                 15             unary
""");
    m.nodes.setIndex(1);
    //stop(m.layout.get(leaf));
    m.layout.get(leaf).copy().ok("""
T   At  Wide  Index       Value   Field name
S  120    68                              leaf
A  120    64      0                         array
S  120    16              11268               leafKeyData
V  120     8                  4                 leafKey
V  128     8                 44                 leafData
A  136    64      1                         array
S  136    16               8451               leafKeyData
V  136     8                  3                 leafKey
V  144     8                 33                 leafData
A  152    64      2                         array
S  152    16               5634               leafKeyData
V  152     8                  2                 leafKey
V  160     8                 22                 leafData
A  168    64      3                         array
S  168    16               2817               leafKeyData
V  168     8                  1                 leafKey
V  176     8                 11                 leafData
V  184     4                 15             unary
""");

    //stop(t);
    t.ok("""
T   At  Wide  Index       Value   Field name
S    0    34                      struct
V    0     4                  0     i0
V    4     4                  1     i1
V    8     4                  3     i2
V   12     4                  7     i3
V   16     2                  0     n0
V   18     2                  1     n1
V   20     2                  2     n2
V   22     2                  3     n3
B   24     1                  1     e0
B   25     1                  0     e1
B   26     1                  0     e2
B   27     1                  0     e3
B   28     1                  0     e4
B   29     1                  0     f0
B   30     1                  0     f1
B   31     1                  0     f2
B   32     1                  0     f3
B   33     1                  1     f4
""");

    k.ok("""
T   At  Wide  Index       Value   Field name
S    0    16               5634   leafKeyData
V    0     8                  2     leafKey
V    8     8                 22     leafData
""");

    m.instructions.clear();
    m.leafRemove(n1, i0);
    m.execute();

    m.nodes.setIndex(1);
    m.layout.get(leaf).copy().ok("""
T   At  Wide  Index       Value   Field name
S  120    68                              leaf
A  120    64      0                         array
S  120    16               8451               leafKeyData
V  120     8                  3                 leafKey
V  128     8                 33                 leafData
A  136    64      1                         array
S  136    16               5634               leafKeyData
V  136     8                  2                 leafKey
V  144     8                 22                 leafData
A  152    64      2                         array
S  152    16               2817               leafKeyData
V  152     8                  1                 leafKey
V  160     8                 11                 leafData
A  168    64      3                         array
S  168    16               2817               leafKeyData
V  168     8                  1                 leafKey
V  176     8                 11                 leafData
V  184     4                  7             unary
""");

    m.instructions.clear();
    m.leafRemove(n1, i1);
    m.execute();

    m.nodes.setIndex(1);
    m.layout.get(leaf).copy().ok("""
T   At  Wide  Index       Value   Field name
S  120    68                              leaf
A  120    64      0                         array
S  120    16               8451               leafKeyData
V  120     8                  3                 leafKey
V  128     8                 33                 leafData
A  136    64      1                         array
S  136    16               2817               leafKeyData
V  136     8                  1                 leafKey
V  144     8                 11                 leafData
A  152    64      2                         array
S  152    16               2817               leafKeyData
V  152     8                  1                 leafKey
V  160     8                 11                 leafData
A  168    64      3                         array
S  168    16               2817               leafKeyData
V  168     8                  1                 leafKey
V  176     8                 11                 leafData
V  184     4                  3             unary
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_create_empty_tree();
    test_create_leaf();
    //test_create_branch();
    //test_join_branch();
    //test_create_leaf();
    //test_join_leaf();
    //test_put();
    //test_put2();
    //test_put_reverse();
    //test_put_random();
    //test_delete();
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    test_create_leaf();
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
