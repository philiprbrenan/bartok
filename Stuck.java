//------------------------------------------------------------------------------
// A fixed size stack of ordered bit keys in assembler on a bit machine.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Design, simulate and layout  a binary tree on a silicon chip.

import java.util.*;

class Stuck extends BitMachine implements LayoutAble                            // Stuck: a fixed size stack controlled by a unary number. The unary number zero indicates an empty stuck.
 {final String             name;                                                // The name of the stuck. The name does not have to be globally unique
  final Unary             unary;                                                // The layout of the stuck
  final Layout.Field    element;                                                // An element of the stuck
  final Layout.Array      array;                                                // The array holding the elements of the stuck
  final Layout.Structure  stuck;                                                // The stuck

  public Layout.Field asField()  {return layout.top;}                           // Layout associated with this class
  public Layout       asLayout() {return layout;}                               // Layout associated with this class

  final Layout.Variable  source;                                                // Source index
  final Layout.Variable  target;                                                // Target index
  final Layout.Variable  buffer;                                                // Temporary buffer for moving data in or out of the stuck
  final Layout.Structure   temp;                                                // Temporary data structure
  final Layout          tempLay;                                                // Layout of temporary data

  final int max;                                                                // The maximum number of entries in the stuck.
  final int width;                                                              // The width of each object in the stuck in bits

//D1 Construction                                                               // Create a stuck

  Stuck(String Name, int Max, Layout repeat)                                    // Create the stuck with a maximum number of the specified elements
   {super(Name);
    name     = Name;                                                            // Name of stuck
    max      = Max;                                                             // Maximum size
    width    = repeat.size();                                                   // Width of element of stuck
    unary    = Unary.unary(max);                                                // Unary number showing which elements in the stack are valid
    layout   = new Layout();                                                    // An element of the stuck
    array    = layout.array    ("array", repeat.duplicate(), max);              // An array of elements comprising the stuck. Duplicate the input element so that we can manipulate it inmdependently
    stuck    = layout.structure(name, array,  unary);                           // An array of elements comprising the stuck
    layout.layout(stuck);                                                       // Layout the structure of the stuck
    element  = layout.get("array"+"."+repeat.top.name);                         // Element on stuck
    stuck.zero();
    tempLay  = new Layout();                                                    // Temporary storage
    source   = tempLay.variable ("source", max);                                // Source index
    target   = tempLay.variable ("target", max);                                // Target index
    buffer   = tempLay.variable ("buffer", width);                              // Buffer for moving data in and out of the stuck
    temp     = tempLay.structure("structure", source, target, buffer);          // Temporary structure
    tempLay.layout(temp);                                                       // Layout of temporary storage
    temp.zero();                                                                // Clear temporary storage
    bitMachines(unary);
   }

  static Stuck stuck(String Name, int Max, Layout Layout)                       // Create the stuck
   {return new Stuck(Name, Max, Layout);
   }

  Stuck like()        {return new Stuck(name, max, element.duplicate());}       // Make a stuck like this one
  Stuck like(int Max) {return new Stuck(name, Max, element.duplicate());}       // Make a stuck like this one with a specified size

  void clear() {zero(stuck);}                                                   // Clear a stuck

  public void ok(String expected) {ok(toString(), expected);}                   // Check the stuck

  int size() {return unary.value();}                                            // The current number of elements in the stuck as a binary integer

//D1 Indexing                                                                   // Index the stuck

  class Index                                                                   // A variable with which to index the stuck
   {final Layout          layout = new Layout();                                // Layout of index
    final Layout.Variable index;                                                // Index variable
    final Layout.Variable value;                                                // Value of stuck at this index
    final Layout.Bit      valid;                                                // Indicate whether the index is valid or not

    Index(String Name)                                                          // Create the index variable
     {index = layout.variable(Name, max);                                       // Index
      valid = layout.bit     ("valid");                                         // Validity of index
      value = layout.variable("value",  width);                                 // Value associated with index - but you have to request that it be set
      final Layout.Structure s = layout.new Structure("s", index, valid, value);// Structure of index
      layout.layout(s);                                                         // Layout index
     }

    void inc()         {shiftLeftOneByOne(index);}                              // Increment the index
    void dec()         {shiftRightOneByZero(index);}                            // Decrement theindex

    void first()       {zero(index);}                                           // Set the index to index the first element on the stuck
    void past()        {copy(index, unary.value);}                              // Set the index to index one past the last element on the stuck.
    void setValid()    {ones(valid);}                                           // Show that the index is valid
    void setNotValid() {zero(valid);}                                           // Show that the index is not valid

    void get()         {elementAt   (value, index);}                            // Get the value at this index from the stuck
    void set()         {setElementAt(value, index);}                            // Set the value at this index in the stuck

    Layout.Bit isFirst() {return Stuck.this.equals(index, 0);}                  // Return a variable indicating whether we are indexing the first element of the stuck
    Layout.Bit isPast () {return Stuck.this.equals(index, unary.value);}        // Return a variable indicating whether we are indexing one past the last element of the stuck

    void isFirst(Layout.Bit f) {Stuck.this.equals(f, index, 0);}                // Set a variable to indicate whether we are indexing the first element of the stuck
    void isPast (Layout.Bit p) {Stuck.this.equals(p, index, unary.value);}      // Set a variable to indicate whether we are indexing one past the last element of the stuck


    void ok(String expected) {Test.ok(toString(), expected);}                   // Check the index has the expected value
    public String toString() {return layout.toString();}                        // Convert to string
   }

//D1 Characteristics                                                            // Characteristics of the stuck

  void isFull (Layout.Bit result) {unary.canNotInc(result);}                    // Check the stuck is full
  void isEmpty(Layout.Bit result) {unary.canNotDec(result);}                    // Check the stuck is empty

//D1 Actions                                                                    // Place and remove data to/from stuck

  void push(LayoutAble ElementToPush)                                           // Push an element onto the stuck
   {setIndexFromUnary(array, unary.value);                                      // Index stuck
    copy(element, ElementToPush.asField());                                     // Copy data into the stuck
    unary.inc();                                                                // Show next free slot
   }

  void push(int v)                                                              // Push an element set to the specified integer value onto the stuck
   {final Layout p = element.duplicate();
    p.asField().fromInt(v);
    push(p);
   }

  void pop(LayoutAble PoppedElement)                                            // Pop an element from the stuck
   {unary.dec();                                                                // Index of top most element
    setIndexFromUnary(array, unary.value);                                      // Set index of topmost element
    copy(PoppedElement.asField(), element);                                     // Copy data out of the stuck
   }

  void shift(LayoutAble ShiftedElement)                                         // Shift an element from the stuck
   {zero(target);                                                               // Index of the first element
    setIndexFromUnary(array, target);                                           // Index of first element
    copy(ShiftedElement.asField(), element);                                    // Copy shifted element out
    zero(source);                                                               // Index the start of the stuck
    shiftLeftOneByOne(source);                                                  // Index first element of stuck

    for (int i = 1; i < max; i++)                                               // Shift the stuck down one place
     {setIndexFromUnary(array, source);                                         // Source index
      copy(buffer, element);                                                    // Copy element at source
      setIndexFromUnary(array, target);                                         // Taregt index is one less than source index
      copy(element, buffer);                                                    // Copy the copy of the source element into the target position
      shiftLeftOneByOne(source);                                                // Next source
      shiftLeftOneByOne(target);                                                // Next target
     }
    unary.dec();                                                                // New number of elements on stuck after one has been shifted out
   }

  void unshift(LayoutAble ElementToUnShift)                                     // Unshift an element from the stuck by moving all the elements up one place
   {ones(source);                                                               // Start the source at the top
    ones(target);                                                               // Start the target at the top

    shiftRightOneByZero(target);                                                // Last slot
    shiftRightOneByZero(source);                                                // Last slot
    shiftRightOneByZero(source);                                                // Second to last slot

    for (int i = 1; i < max; ++i)                                               // Shift the stuck up one place
     {setIndexFromUnary(array, source);                                         // Index source
      copy(buffer, element);                                                    // Copy source
      setIndexFromUnary(array, target);                                         // Index target
      copy(element, buffer);                                                    // Copy target
      shiftRightOneByZero(source);                                              // Move target down one
      shiftRightOneByZero(target);                                              // Move source down one
     }
    zero(target);                                                               // Index of the first element
    setIndexFromUnary(array, target);                                           // Index the first element
    copy(element, ElementToUnShift.asField());                                  // Copy in the new element
    unary.inc();                                                                // New number of elements on stuck
   }

  void elementAt(LayoutAble elementOut, Layout.Variable index)                  // Return the element at the indicated zero based index
   {setIndexFromUnary(array, index);                                            // Index of required element
    copy(elementOut.asField(), element);                                        // Copy element out
   }

  void setElementAt(LayoutAble elementIn, Layout.Variable index)                // Set the element at the indicated zero based index
   {setIndexFromUnary(array, index);                                            // Index of element to set
    copy(element, elementIn.asField());                                         // Copy element in
   }

  void insertElementAt(LayoutAble elementToInsert, Layout.Variable index)       // Insert an element represented as memory into the stuckstack at the indicated zero based index after moving the elements above up one position
   {ones(target);                                                               // Top of stuck
    ones(source);                                                               // Top of stuck
    shiftRightOneByZero(target);                                                // One step down on target
    shiftRightOneByZero(source);                                                // One step down on source
    new Repeat()
     {void code()
       {returnIfEqual(target, index);                                           // Test for finish of shifting phase
        shiftRightOneByZero(source);                                            // One step down on source
        setIndexFromUnary(array, source);                                       // Index of source
        copy(buffer, element);                                                  // Copy source into buffer
        setIndexFromUnary(array, target);                                       // Index of target
        copy(element, buffer);                                                  // Copy copy of osurce into target slot
        shiftRightOneByZero(target);                                            // One step down on target
       }
     };
    setIndexFromUnary(array, index);                                            // Index of element to set
    copy(element, elementToInsert.asField());                                   // Copy in new element
    unary.inc();                                                                // New number of elements on stuck
   }

  void removeElementAt(Layout.Variable index)                                   // Remove the element at the indicated zero based index
   {copy(target, index);                                                        // Target of removal
    copy(source, target);                                                       // Source of removeal
    shiftLeftOneByOne(source);                                                  // One step down on source
    new Repeat()
     {void code()
       {returnIfAllOnes(source);                                                // Test for finish of shifting phase
        setIndexFromUnary(array, source);                                       // Index of source
        copy(buffer, element);                                                  // Copy source into buffer
        setIndexFromUnary(array, target);                                       // Index of target
        copy(element, buffer);                                                  // Copy copy of osurce into target slot
        shiftLeftOneByOne(target);                                              // One step down on target
        shiftLeftOneByOne(source);                                              // One step down on source
       }
     };
    unary.dec();                                                                // New number of elements on stuck
   }

  void firstElement(LayoutAble FirstElement)                                    // Get the first element
   {zero(source);                                                               // Index of first element
    setIndexFromUnary(array, source);                                           // Set index of first element
    copy(FirstElement.asField(), element);                                      // Copy of first element
   }

  void lastElement(LayoutAble LastElement)                                      // Get the last active element
   {copy(source, unary.value);                                                  // Index top of stuck
    shiftRightOneByZero(source);                                                // Index of top most active element
    setIndexFromUnary(array, source);                                           // Set index of topmost element
    copy(LastElement.asField(), element);                                       // Copy of top most element
   }

//D1 Iteration                                                                  // Iterate over a stuck

  class Up extends Index                                                        // Iterate upwards over a stuck
   {Up()
     {super("index");
      first();
      new Repeat()                                                              // Block of code for each iteration
       {void code()
         {returnIfOne(isPast());                                                // Past all the entries
          get();                                                                // Get current entry
          up();                                                                 // Process current entry
          inc();                                                                // Move up
         }
       };
     }
    void up() {}                                                                // Action to perform on each iteration
   }

  class Down extends Index                                                      // Iterate downwards over a stuck
   {Down()
     {super("index");
      past();
      new Repeat()                                                              // Block of code for each iteration
       {void code()
         {returnIfOne(isFirst());                                               // No more entries
          dec();                                                                // Move down
          get();                                                                // Get the stuck entry at the current index
          down();                                                               // Process the curent entry
         }
       };
     }
    void down() {}                                                              // Action to perform on each iteration
   }

//D1 Search                                                                     // Search a stuck.

  void indexOf                                                                  // Find the index of an element in the stuck and set the found flag to true else if no such element is found the found flag is set to false
   (LayoutAble elementToFind, int length,                                       // Check whether the stuck contains the specified element  comparing the first length bits
    Layout.Bit found,         Layout.Variable index)                            // Whether the element was found and at what index
   {zero(found);                                                                // Assume not found
    zero(index);                                                                // Start at index 0
    Branch[]equal = new Branch[max];
    for (int i = 0; i < max; i++)                                               // Check each element in turn
     {setIndexFromUnary(array, index);
      equals(found, elementToFind.asField(), 0, element, 0, length);            // Test for the element to be found
      equal[i] = branchIfOne(found);
      shiftLeftOneByOne(index);
     }
    for (int i = 0; i < max; i++) comeFrom(equal[i]);
   }

//D1 Print                                                                      // Print a stuck

//D0 Tests                                                                      // Test stuck

  static void test_push()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);

    final Layout l = new Layout();
    final Layout.Bit
      e0 = l.bit("e0"), f0 = l.bit("f0"),
      e1 = l.bit("e1"), f1 = l.bit("f1"),
      e2 = l.bit("e2"), f2 = l.bit("f2"),
      e3 = l.bit("e3"), f3 = l.bit("f3"),
      e4 = l.bit("e4"), f4 = l.bit("f4");
    final Layout.Variable value = l.variable ("value", W);
    final Layout.Structure    S = l.structure("structure", e0, e1, e2, e3, e4,
       value,                                              f0, f1, f2, f3, f4);
    l.layout(S);

    Stuck s = stuck("s", M, keyData);
    s.isEmpty(e0);
    s.isFull(f0);
    s.zero(data); s.ones(key); s.push(keyData); s.isEmpty(e1); s.isFull(f1);
    s.shiftLeftOneByOne(data); s.push(keyData); s.isEmpty(e2); s.isFull(f2);
    s.shiftLeftOneByOne(data); s.push(keyData); s.isEmpty(e3); s.isFull(f3);
    s.shiftLeftOneByOne(data); s.push(keyData); s.isEmpty(e4); s.isFull(f4);
    s.execute();

    //stop(s.layout);
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    44                      s
A    0    40      0                 array     array
S    0    10                 15       s     array.s
V    0     4                 15         key     array.s.key
V    4     6                  0         data     array.s.data
A   10    40      1                 array     array
S   10    10                 31       s     array.s
V   10     4                 15         key     array.s.key
V   14     6                  1         data     array.s.data
A   20    40      2                 array     array
S   20    10                 63       s     array.s
V   20     4                 15         key     array.s.key
V   24     6                  3         data     array.s.data
A   30    40      3                 array     array
S   30    10                127       s     array.s
V   30     4                 15         key     array.s.key
V   34     6                  7         data     array.s.data
V   40     4                 15     unary     unary
""");
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    16              32769   structure
B    0     1                  1     e0     e0
B    1     1                  0     e1     e1
B    2     1                  0     e2     e2
B    3     1                  0     e3     e3
B    4     1                  0     e4     e4
V    5     6                  0     value     value
B   11     1                  0     f0     f0
B   12     1                  0     f1     f1
B   13     1                  0     f2     f2
B   14     1                  0     f3     f3
B   15     1                  1     f4     f4
""");
   }
  static void test_pop()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);

    final Layout
      v1 = keyData.duplicate().asLayout(),
      v2 = keyData.duplicate().asLayout(),
      v3 = keyData.duplicate().asLayout(),
      v4 = keyData.duplicate().asLayout();

    Stuck s = stuck("S", M, keyData);
    s.shiftLeftOneByOne(data); s.push(keyData);
    s.shiftLeftOneByOne(data); s.push(keyData);
    s.shiftLeftOneByOne(data); s.push(keyData);
    s.shiftLeftOneByOne(data); s.push(keyData);
    s.pop(v1);
    s.pop(v2);
    s.pop(v3);
    s.pop(v4);
    s.execute();

    //stop(s.layout);
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    44                      S
A    0    40      0                 array     array
S    0    10                 16       s     array.s
V    0     4                  0         key     array.s.key
V    4     6                  1         data     array.s.data
A   10    40      1                 array     array
S   10    10                 48       s     array.s
V   10     4                  0         key     array.s.key
V   14     6                  3         data     array.s.data
A   20    40      2                 array     array
S   20    10                112       s     array.s
V   20     4                  0         key     array.s.key
V   24     6                  7         data     array.s.data
A   30    40      3                 array     array
S   30    10                240       s     array.s
V   30     4                  0         key     array.s.key
V   34     6                 15         data     array.s.data
V   40     4                  0     unary     unary
""");
    //stop(v1);
    v1.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                240   s
V    0     4                  0     key     key
V    4     6                 15     data     data
""");
    //stop(v2);
    v2.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                112   s
V    0     4                  0     key     key
V    4     6                  7     data     data
""");
    //stop(v3);
    v3.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                 48   s
V    0     4                  0     key     key
V    4     6                  3     data     data
""");
    //stop(v4);
    v4.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                 16   s
V    0     4                  0     key     key
V    4     6                  1     data     data
""");
   }

  static void test_shift()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);

    final Layout
      v1 = keyData.duplicate().asLayout(),
      v2 = keyData.duplicate().asLayout(),
      v3 = keyData.duplicate().asLayout(),
      v4 = keyData.duplicate().asLayout();

    Stuck s = stuck("s", M, keyData);
    s.shiftLeftOneByOne(data); s.push(keyData);
    s.shiftLeftOneByOne(data); s.push(keyData);
    s.shiftLeftOneByOne(data); s.push(keyData);
    s.shiftLeftOneByOne(data); s.push(keyData);
    s.shift(v1);
    s.shift(v2);
    s.shift(v3);
    s.shift(v4);
    s.execute();

    //stop(s.layout);
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    44                      s
A    0    40      0                 array     array
S    0    10                240       s     array.s
V    0     4                  0         key     array.s.key
V    4     6                 15         data     array.s.data
A   10    40      1                 array     array
S   10    10                240       s     array.s
V   10     4                  0         key     array.s.key
V   14     6                 15         data     array.s.data
A   20    40      2                 array     array
S   20    10                240       s     array.s
V   20     4                  0         key     array.s.key
V   24     6                 15         data     array.s.data
A   30    40      3                 array     array
S   30    10                240       s     array.s
V   30     4                  0         key     array.s.key
V   34     6                 15         data     array.s.data
V   40     4                  0     unary     unary
""");

    //stop(v1);
    v1.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                 16   s
V    0     4                  0     key     key
V    4     6                  1     data     data
""");
    //stop(v2);
    v2.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                 48   s
V    0     4                  0     key     key
V    4     6                  3     data     data
""");
    //stop(v3);
    v3.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                112   s
V    0     4                  0     key     key
V    4     6                  7     data     data
""");

    //stop(v4);
    v4.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                240   s
V    0     4                  0     key     key
V    4     6                 15     data     data
""");
   }

  static void test_unshift()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);

    Stuck s = stuck("s", M, keyData);
    s.zero(data);
    s.shiftLeftOneByOne(data); s.unshift(keyData);
    s.shiftLeftOneByOne(data); s.unshift(keyData);
    s.shiftLeftOneByOne(data); s.unshift(keyData);
    s.shiftLeftOneByOne(data); s.unshift(keyData);
    s.execute();

    //stop(s.layout);
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    44                      s
A    0    40      0                 array     array
S    0    10                240       s     array.s
V    0     4                  0         key     array.s.key
V    4     6                 15         data     array.s.data
A   10    40      1                 array     array
S   10    10                112       s     array.s
V   10     4                  0         key     array.s.key
V   14     6                  7         data     array.s.data
A   20    40      2                 array     array
S   20    10                 48       s     array.s
V   20     4                  0         key     array.s.key
V   24     6                  3         data     array.s.data
A   30    40      3                 array     array
S   30    10                 16       s     array.s
V   30     4                  0         key     array.s.key
V   34     6                  1         data     array.s.data
V   40     4                 15     unary     unary
""");
   }

  static void test_elementAt()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);

    final Layout           Index   = new Layout();
    final Layout.Variable  index   = Index.variable("index",  M);
    Index.layout(index);

    final Layout
      v1 = keyData.duplicate().asLayout(),
      v2 = keyData.duplicate().asLayout(),
      v3 = keyData.duplicate().asLayout(),
      v4 = keyData.duplicate().asLayout();

    Stuck s = stuck("s", M, keyData);
    s.zero(index);
    s.shiftLeftOneByOne(index); s.shiftLeftOneByOne(data); s.unshift(keyData);
    s.shiftLeftOneByOne(index); s.shiftLeftOneByOne(data); s.unshift(keyData);
    s.shiftLeftOneByOne(index); s.shiftLeftOneByOne(data); s.unshift(keyData);
    s.shiftLeftOneByOne(index); s.shiftLeftOneByOne(data); s.unshift(keyData);
    s.zero             (index); s.elementAt(v1, index);
    s.shiftLeftOneByOne(index); s.elementAt(v2, index);
    s.shiftLeftOneByOne(index); s.elementAt(v3, index);
    s.shiftLeftOneByOne(index); s.elementAt(v4, index);
    s.execute();

    //stop(s.layout);
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    44                      s
A    0    40      0                 array     array
S    0    10                240       s     array.s
V    0     4                  0         key     array.s.key
V    4     6                 15         data     array.s.data
A   10    40      1                 array     array
S   10    10                112       s     array.s
V   10     4                  0         key     array.s.key
V   14     6                  7         data     array.s.data
A   20    40      2                 array     array
S   20    10                 48       s     array.s
V   20     4                  0         key     array.s.key
V   24     6                  3         data     array.s.data
A   30    40      3                 array     array
S   30    10                 16       s     array.s
V   30     4                  0         key     array.s.key
V   34     6                  1         data     array.s.data
V   40     4                 15     unary     unary
""");
    //stop(v4);
    v4.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                 16   s
V    0     4                  0     key     key
V    4     6                  1     data     data
""");
    //stop(v3);
    v3.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                 48   s
V    0     4                  0     key     key
V    4     6                  3     data     data
""");
    //stop(v2);
    v2.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                112   s
V    0     4                  0     key     key
V    4     6                  7     data     data
""");

    //stop(v1);
    v1.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                240   s
V    0     4                  0     key     key
V    4     6                 15     data     data
""");
   }

  static void test_insert_element_at()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);

    final Layout v1 = keyData.duplicate().asLayout();
    final Layout v2 = keyData.duplicate().asLayout();
    final Layout v3 = keyData.duplicate().asLayout();
    final Layout v4 = keyData.duplicate().asLayout();

    final Layout l = new Layout();
    final Layout.Variable i0 = l.variable ("i0", M);
    final Layout.Variable i1 = l.variable ("i1", M);
    final Layout.Variable i2 = l.variable ("i2", M);
    final Layout.Structure S = l.structure("S", i0, i1, i2);
    l.layout(S);
    i0.fromInt(0); v1.get("data").fromInt(11);
    i1.fromInt(1); v2.get("data").fromInt(22);
    i2.fromInt(3); v3.get("data").fromInt(33);
                   v4.get("data").fromInt(44);

    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    12                784   S
V    0     4                  0     i0     i0
V    4     4                  1     i1     i1
V    8     4                  3     i2     i2
""");

    Stuck s = stuck("s", M, keyData);
    s.insertElementAt(v3, i0);
    s.insertElementAt(v2, i1);
    s.insertElementAt(v1, i2);
    s.insertElementAt(v4, i1);
    s.execute();

    //stop(v1);
    v1.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                176   s
V    0     4                  0     key     key
V    4     6                 11     data     data
""");

    //stop(v2);
    v2.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                352   s
V    0     4                  0     key     key
V    4     6                 22     data     data
""");

    //stop(v3);
    v3.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                528   s
V    0     4                  0     key     key
V    4     6                 33     data     data
""");

    //stop(v4);
    v4.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                704   s
V    0     4                  0     key     key
V    4     6                 44     data     data
""");
   }

  static void test_remove_element_at()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);

    final Layout l = new Layout();
    final Layout.Variable i0 = l.variable ("i0", M);
    final Layout.Variable i1 = l.variable ("i1", M);
    final Layout.Variable i2 = l.variable ("i2", M);
    final Layout.Structure S = l.structure("S", i0, i1, i2);
    l .layout (S);
    i0.fromInt(0);
    i1.fromInt(1);
    i2.fromInt(3);

    Stuck s = stuck("s", M, keyData);

    s.array.zero();
    s.array.setIndex(0); s.element.fromInt(11);
    s.array.setIndex(1); s.element.fromInt(22);
    s.array.setIndex(2); s.element.fromInt(33);
    s.array.setIndex(3); s.element.fromInt(44);
    s.unary.value.fromInt(15);

    //stop(s.layout);
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    44                      s
A    0    40      0                 array     array
S    0    10                 11       s     array.s
V    0     4                 11         key     array.s.key
V    4     6                  0         data     array.s.data
A   10    40      1                 array     array
S   10    10                 22       s     array.s
V   10     4                  6         key     array.s.key
V   14     6                  1         data     array.s.data
A   20    40      2                 array     array
S   20    10                 33       s     array.s
V   20     4                  1         key     array.s.key
V   24     6                  2         data     array.s.data
A   30    40      3                 array     array
S   30    10                 44       s     array.s
V   30     4                 12         key     array.s.key
V   34     6                  2         data     array.s.data
V   40     4                 15     unary     unary
""");
    s.removeElementAt(i1);
    s.execute();

    //stop(s.layout);
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    44                      s
A    0    40      0                 array     array
S    0    10                 11       s     array.s
V    0     4                 11         key     array.s.key
V    4     6                  0         data     array.s.data
A   10    40      1                 array     array
S   10    10                 33       s     array.s
V   10     4                  1         key     array.s.key
V   14     6                  2         data     array.s.data
A   20    40      2                 array     array
S   20    10                 44       s     array.s
V   20     4                 12         key     array.s.key
V   24     6                  2         data     array.s.data
A   30    40      3                 array     array
S   30    10                 44       s     array.s
V   30     4                 12         key     array.s.key
V   34     6                  2         data     array.s.data
V   40     4                  7     unary     unary
""");

    Stuck t = stuck("t", M, keyData);

    t.array.zero();
    t.array.setIndex(0); t.element.fromInt(11);
    t.array.setIndex(1); t.element.fromInt(22);
    t.array.setIndex(2); t.element.fromInt(33);
    t.array.setIndex(3); t.element.fromInt(44);
    t.unary.value.fromInt(15);

    t.removeElementAt(i1);
    t.removeElementAt(i1);
    t.execute();

    //stop(t.layout);
    t.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    44                      t
A    0    40      0                 array     array
S    0    10                 11       s     array.s
V    0     4                 11         key     array.s.key
V    4     6                  0         data     array.s.data
A   10    40      1                 array     array
S   10    10                 44       s     array.s
V   10     4                 12         key     array.s.key
V   14     6                  2         data     array.s.data
A   20    40      2                 array     array
S   20    10                 44       s     array.s
V   20     4                 12         key     array.s.key
V   24     6                  2         data     array.s.data
A   30    40      3                 array     array
S   30    10                 44       s     array.s
V   30     4                 12         key     array.s.key
V   34     6                  2         data     array.s.data
V   40     4                  3     unary     unary
""");

    Stuck u = stuck("u", M, keyData);

    u.array.zero();
    u.array.setIndex(0); u.element.fromInt(11);
    u.array.setIndex(1); u.element.fromInt(22);
    u.array.setIndex(2); u.element.fromInt(33);
    u.array.setIndex(3); u.element.fromInt(44);
    u.unary.value.fromInt(15);

    u.removeElementAt(i1);
    u.removeElementAt(i1);
    u.removeElementAt(i0);
    u.execute();

    //stop(u.layout);
    u.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    44                      u
A    0    40      0                 array     array
S    0    10                 44       s     array.s
V    0     4                 12         key     array.s.key
V    4     6                  2         data     array.s.data
A   10    40      1                 array     array
S   10    10                 44       s     array.s
V   10     4                 12         key     array.s.key
V   14     6                  2         data     array.s.data
A   20    40      2                 array     array
S   20    10                 44       s     array.s
V   20     4                 12         key     array.s.key
V   24     6                  2         data     array.s.data
A   30    40      3                 array     array
S   30    10                 44       s     array.s
V   30     4                 12         key     array.s.key
V   34     6                  2         data     array.s.data
V   40     4                  1     unary     unary
""");

    Stuck v = stuck("v", M, keyData);

    v.array.zero();
    v.array.setIndex(0); v.element.fromInt(11);
    v.array.setIndex(1); v.element.fromInt(22);
    v.array.setIndex(2); v.element.fromInt(33);
    v.array.setIndex(3); v.element.fromInt(44);
    v.unary.value.fromInt(15);

    v.removeElementAt(i1);
    v.removeElementAt(i1);
    v.removeElementAt(i0);
    v.removeElementAt(i0);
    v.execute();

    //stop(v.layout);
    v.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    44                      v
A    0    40      0                 array     array
S    0    10                 44       s     array.s
V    0     4                 12         key     array.s.key
V    4     6                  2         data     array.s.data
A   10    40      1                 array     array
S   10    10                 44       s     array.s
V   10     4                 12         key     array.s.key
V   14     6                  2         data     array.s.data
A   20    40      2                 array     array
S   20    10                 44       s     array.s
V   20     4                 12         key     array.s.key
V   24     6                  2         data     array.s.data
A   30    40      3                 array     array
S   30    10                 44       s     array.s
V   30     4                 12         key     array.s.key
V   34     6                  2         data     array.s.data
V   40     4                  0     unary     unary
""");
   }

  static void test_first_last()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);

    final Layout f = keyData.duplicate().asLayout(), l = keyData.duplicate().asLayout();

    Stuck s = stuck("s", M, keyData);

    s.array.zero();
    s.array.setIndex(0); s.element.fromInt(11);
    s.array.setIndex(1); s.element.fromInt(22);
    s.array.setIndex(2); s.element.fromInt(33);
    s.array.setIndex(3); s.element.fromInt(44);
    s.unary.value.fromInt(15);

    s.firstElement(f);
    s.lastElement(l);
    s.execute();

    f.asField().ok(11);
    l.asField().ok(44);
   }

  static void test_index_of()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);
    final Layout
      ka = keyData.duplicate().asLayout(),
      k1 = keyData.duplicate().asLayout(),
      kb = keyData.duplicate().asLayout(),
      k2 = keyData.duplicate().asLayout(),
      kc = keyData.duplicate().asLayout(),
      k3 = keyData.duplicate().asLayout(),
      kd = keyData.duplicate().asLayout(),
      k4 = keyData.duplicate().asLayout(),
      ke = keyData.duplicate().asLayout();
    ka.top.fromInt(10);
    k1.top.fromInt(11);
    kb.top.fromInt(21);
    k2.top.fromInt(22);
    kc.top.fromInt(32);
    k3.top.fromInt(33);
    kd.top.fromInt(43);
    k4.top.fromInt(44);
    ke.top.fromInt(45);

    Stuck s = stuck("s", M, keyData);

    s.array.zero();
    s.array.setIndex(0); s.element.fromInt(11);
    s.array.setIndex(1); s.element.fromInt(22);
    s.array.setIndex(2); s.element.fromInt(33);
    s.array.setIndex(3); s.element.fromInt(44);
    s.unary.value.fromInt(15);

    final Layout           l  = new Layout();
    final Layout.Variable  ia = l.variable ("ia", M);
    final Layout.Variable  i1 = l.variable ("i1", M);
    final Layout.Variable  ib = l.variable ("ib", M);
    final Layout.Variable  i2 = l.variable ("i2", M);
    final Layout.Variable  ic = l.variable ("ic", M);
    final Layout.Variable  i3 = l.variable ("i3", M);
    final Layout.Variable  id = l.variable ("id", M);
    final Layout.Variable  i4 = l.variable ("i4", M);
    final Layout.Variable  ie = l.variable ("ie", M);
    final Layout.Bit       fa = l.bit      ("fa");
    final Layout.Bit       f1 = l.bit      ("f1");
    final Layout.Bit       fb = l.bit      ("fb");
    final Layout.Bit       f2 = l.bit      ("f2");
    final Layout.Bit       fc = l.bit      ("fc");
    final Layout.Bit       f3 = l.bit      ("f3");
    final Layout.Bit       fd = l.bit      ("fd");
    final Layout.Bit       f4 = l.bit      ("f4");
    final Layout.Bit       fe = l.bit      ("fe");
    final Layout.Structure S  = l.structure("s",
     fa, f1, fb, f2, fc, f3, fd, f4, fe,
     ia, i1, ib, i2, ic, i3, id, i4, ie);

    l.layout(S);

    s.indexOf(ka, W, fa, ia);                                                   // The key is only M wide which is not quite enough to compare all of the element value, however W is so that is what is being used
    s.indexOf(k1, W, f1, i1);
    s.indexOf(kb, W, fb, ib);
    s.indexOf(k2, W, f2, i2);
    s.indexOf(kc, W, fc, ic);
    s.indexOf(k3, W, f3, i3);
    s.indexOf(kd, W, fd, id);
    s.indexOf(k4, W, f4, i4);
    s.indexOf(ke, W, fe, ie);
    s.execute();

    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    45                      s
B    0     1                  0     fa     fa
B    1     1                  1     f1     f1
B    2     1                  0     fb     fb
B    3     1                  1     f2     f2
B    4     1                  0     fc     fc
B    5     1                  1     f3     f3
B    6     1                  0     fd     fd
B    7     1                  1     f4     f4
B    8     1                  0     fe     fe
V    9     4                 15     ia     ia
V   13     4                  0     i1     i1
V   17     4                 15     ib     ib
V   21     4                  1     i2     i2
V   25     4                 15     ic     ic
V   29     4                  3     i3     i3
V   33     4                 15     id     id
V   37     4                  7     i4     i4
V   41     4                 15     ie     ie
""");
   }

  static void test_set_element_at()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);

    final Layout
      k1 = keyData.duplicate().asLayout(),
      k2 = keyData.duplicate().asLayout(),
      k3 = keyData.duplicate().asLayout(),
      k4 = keyData.duplicate().asLayout();

    k1.top.fromInt(0); k2.top.fromInt(1); k3.top.fromInt(2); k4.top.fromInt(3);

    Stuck s = stuck("s", M, keyData);

    s.array.zero();
    s.array.setIndex(0); s.element.fromInt(11);
    s.array.setIndex(1); s.element.fromInt(22);
    s.array.setIndex(2); s.element.fromInt(33);
    s.array.setIndex(3); s.element.fromInt(44);
    s.unary.value.fromInt(15);

    //stop(s.layout);
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    44                      s
A    0    40      0                 array     array
S    0    10                 11       s     array.s
V    0     4                 11         key     array.s.key
V    4     6                  0         data     array.s.data
A   10    40      1                 array     array
S   10    10                 22       s     array.s
V   10     4                  6         key     array.s.key
V   14     6                  1         data     array.s.data
A   20    40      2                 array     array
S   20    10                 33       s     array.s
V   20     4                  1         key     array.s.key
V   24     6                  2         data     array.s.data
A   30    40      3                 array     array
S   30    10                 44       s     array.s
V   30     4                 12         key     array.s.key
V   34     6                  2         data     array.s.data
V   40     4                 15     unary     unary
""");

    final Layout           l  = new Layout();
    final Layout.Variable  i1 = l.variable ("i1", M);
    final Layout.Variable  i2 = l.variable ("i2", M);
    final Layout.Variable  i3 = l.variable ("i3", M);
    final Layout.Variable  i4 = l.variable ("i4", M);
    final Layout.Structure S  = l.structure("s", i1, i2, i3, i4);
    l.layout(S);
    i1.fromUnary(0); i2.fromUnary(1); i3.fromUnary(2); i4.fromUnary(3);

    s.setElementAt(k1, i1);
    s.setElementAt(k2, i2);
    s.setElementAt(k3, i3);
    s.setElementAt(k4, i4);
    s.execute();

    //stop(s.layout);
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    44                      s
A    0    40      0  -1071643648     array     array
S    0    10                  0       s     array.s
V    0     4                  0         key     array.s.key
V    4     6                  0         data     array.s.data
A   10    40      1  -1071643648     array     array
S   10    10                  1       s     array.s
V   10     4                  1         key     array.s.key
V   14     6                  0         data     array.s.data
A   20    40      2  -1071643648     array     array
S   20    10                  2       s     array.s
V   20     4                  2         key     array.s.key
V   24     6                  0         data     array.s.data
A   30    40      3  -1071643648     array     array
S   30    10                  3       s     array.s
V   30     4                  3         key     array.s.key
V   34     6                  0         data     array.s.data
V   40     4                 15     unary     unary
""");
   }

  static void test_like()
   {final int W = 6, M = 4;

    final Layout           l = new Layout();
    final Layout.Variable  k = l.variable("k",  M);
    l.layout(k);

    Stuck s = stuck("s", M, l);

    s.push(1);
    s.push(2);
    s.execute();
    //stop(s.layout);
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    20             196641   s
A    0    16      0          33     array     array
V    0     4                  1       k     array.k
A    4    16      1          33     array     array
V    4     4                  2       k     array.k
A    8    16      2          33     array     array
V    8     4                  0       k     array.k
A   12    16      3          33     array     array
V   12     4                  0       k     array.k
V   16     4                  3     unary     unary
""");
   }

  static void test_index()
   {final int M = 5;

    final Layout           l = new Layout();
    final Layout.Variable  k = l.variable("k",  M);
    l.layout(k);

    final Layout           r = new Layout();
    final Layout.Bit
      f0 = r.bit("f0"),   l0 = r.bit("l0"),
      f1 = r.bit("f1"),   l1 = r.bit("l1"),
      f2 = r.bit("f2"),   l2 = r.bit("l2"),
      f3 = r.bit("f3"),  l3 = r.bit("l3");
    r.layout(r.structure("s", f0, l0, f1, l1, f2, l2, f3, l3));

    Stuck s = stuck("s", M, l);

    s.push(11);
    s.push(22);

    Index i = s.new Index("path");
    i.past();
    s.execute();

    ok(s.size(), 2);

    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    30          100664011   s
A    0    25      0         715     array     array
V    0     5                 11       k     array.k
A    5    25      1         715     array     array
V    5     5                 22       k     array.k
A   10    25      2         715     array     array
V   10     5                  0       k     array.k
A   15    25      3         715     array     array
V   15     5                  0       k     array.k
A   20    25      4         715     array     array
V   20     5                  0       k     array.k
V   25     5                  3     unary     unary
""");

    //stop(i);
    i.ok("""
T   At  Wide  Index       Value   Field name
S    0    11                  3   s
V    0     5                  3     path     path
B    5     1                  0     valid     valid
V    6     5                  0     value     value
""");

    s.reset();
    i.dec();
    s.execute();
    ok(s.size(), 2);
    i.ok("""
T   At  Wide  Index       Value   Field name
S    0    11                  1   s
V    0     5                  1     path     path
B    5     1                  0     valid     valid
V    6     5                  0     value     value
""");

    s.reset();
    i.first();
    i.isFirst(f0);
    i.isPast (l0);

    i.inc();
    i.isFirst(f1);
    i.isPast (l1);

    i.inc();
    i.isFirst(f2);
    i.isPast (l2);

    i.inc();
    i.isFirst(f3);
    i.isPast (l3);

    s.execute();
    //stop(i, r);
    i.ok("""
T   At  Wide  Index       Value   Field name
S    0    11                  7   s
V    0     5                  7     path     path
B    5     1                  0     valid     valid
V    6     5                  0     value     value
""");

    r.ok("""
T   At  Wide  Index       Value   Field name
S    0     8                 33   s
B    0     1                  1     f0     f0
B    1     1                  0     l0     l0
B    2     1                  0     f1     f1
B    3     1                  0     l1     l1
B    4     1                  0     f2     f2
B    5     1                  1     l2     l2
B    6     1                  0     f3     f3
B    7     1                  0     l3     l3
""");
   }

  static void test_index_up()                                                   // Read through the stuck upwards
   {final int M = 5;

    final Layout           l = new Layout();
    final Layout.Variable  k = l.variable("k",  M);
    l.layout(k);

    Stuck s = stuck("s", M, l), t = s.like();
    s.bitMachines(t);                                                           // generate code in this biot machoine, not the birmachine

    s.push(11);
    s.push(22);

    s.new Up()
     {void up()
       {t.push(value);
       }
     };
    s.execute();
    t.ok("""
T   At  Wide  Index       Value   Field name
S    0    30          100664011   s
A    0    25      0         715     array     array
V    0     5                 11       k     array.k
A    5    25      1         715     array     array
V    5     5                 22       k     array.k
A   10    25      2         715     array     array
V   10     5                  0       k     array.k
A   15    25      3         715     array     array
V   15     5                  0       k     array.k
A   20    25      4         715     array     array
V   20     5                  0       k     array.k
V   25     5                  3     unary     unary
""");
   }

  static void test_index_down()                                                 // Read through the stuck downwards
   {final int M = 5;

    final Layout           l = new Layout();
    final Layout.Variable  k = l.variable("k",  M);
    l.layout(k);

    Stuck s = stuck("s", M, l), t = s.like();
    s.bitMachines(t);

    s.push(11);
    s.push(22);

    i.past();
    s.new Down()
     {void down()
       {t.push(i.value);
       }
     };
    s.execute();
    t.ok("""
T   At  Wide  Index       Value   Field name
S    0    30          100663670   s
A    0    25      0         374     array     array
V    0     5                 22       k     array.k
A    5    25      1         374     array     array
V    5     5                 11       k     array.k
A   10    25      2         374     array     array
V   10     5                  0       k     array.k
A   15    25      3         374     array     array
V   15     5                  0       k     array.k
A   20    25      4         374     array     array
V   20     5                  0       k     array.k
V   25     5                  3     unary     unary
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_push();
    test_pop();
    test_shift();
    test_unshift();
    test_elementAt();
    test_insert_element_at();
    test_remove_element_at();
    test_first_last();
    test_index_of();
    test_set_element_at();
    test_like();
    test_index();
    test_index_up();
    test_index_down();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
   }

  public static void main(String[] args)                                        // Test if called as a program
   {if (args.length > 0 && args[0].equals("compile")) System.exit(0);           // Do a syntax check
    try                                                                         // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
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
