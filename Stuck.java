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
  final Layout           layout;                                                // Layout of the stuck

  public Layout.Field getLayoutField() {return layout.top;}                     // Layout associated with this class
  public Layout       getLayout     () {return layout    ;}                     // Layout associated with this class

  final Layout.Variable  source;                                                // Source index
  final Layout.Variable  target;                                                // Target index
  final Layout.Variable  buffer;                                                // Temporary buffer for moving data in or out of the stuck
  final Layout.Structure   temp;                                                // Temporary data structure
  final Layout          tempLay;                                                // Layout of temporary data

  final int max;                                                                // The maximum number of entries in the stuck.
  final int width;                                                              // The width of each object in the stuck in bits

  BitMachine bitMachine = this;                                                 // The bit machine in which to load instructions

//D1 Construction                                                               // Create a stuck

  Stuck(String Name, int Max, Layout repeat)                                    // Create the stuck with a maximum number of the specified elements
   {name     = Name;                                                            // Name of stuck
    max      = Max;                                                             // Maximum size
    width    = repeat.size();                                                   // Width of element of stuck
    unary    = Unary.unary(max);                                                // Unary number showing which elements in the stack are valid
    unary.bitMachine = this;                                                    // Unary instructions to be placed in this machine
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
   }

  Layout.Field layout() {return layout.top;}                                    // Get the topmost structure
  static Stuck stuck(String Name, int Max, Layout Layout)                       // Create the stuck
   {return new Stuck(Name, Max, Layout);
   }

  void clear() {zero(stuck);}                                                   // Clear a stuck

  public void ok(String expected) {ok(toString(), expected);}                   // Check the stuck

//D1 Characteristics                                                            // Characteristics of the stuck

  void isFull (Layout.Bit result) {unary.canNotInc(result);}                    // Check the stuck is full
  void isEmpty(Layout.Bit result) {unary.canNotDec(result);}                    // Check the stuck is empty

//D1 Actions                                                                    // Place and remove data to/from stuck

  void push(Layout ElementToPush)                                               // Push an element onto the stuck
   {setIndex(array, unary.value);                                               // Index stuck
    copy(element, ElementToPush.getLayoutField());                              // Copy data into the stuck
    unary.inc();                                                                // Show next free slot
   }

  void pop(Layout PoppedElement)                                                // Pop an element from the stuck
   {unary.dec();                                                                // Index of top most element
    setIndex(array, unary.value);                                               // Set index of topmost element
    copy(PoppedElement.getLayoutField(), element);                              // Copy data out of the stuck
   }

  void shift(Layout ShiftedElement)                                             // Shift an element from the stuck
   {zero(target);                                                               // Index of the first element
    setIndex(array, target);                                                    // Index of first element
    copy(ShiftedElement.getLayoutField(), element);                             // Copy shifted element out
    zero(source);                                                               // Index the start of the stuck
    shiftLeftOneByOne(source);                                                  // Index first element of stuck

    for (int i = 1; i < max; i++)                                               // Shift the stuck down one place
     {setIndex(array, source);                                                  // Source index
      copy(buffer, element);                                                    // Copy element at source
      setIndex(array, target);                                                  // Taregt index is one less than source index
      copy(element, buffer);                                                    // Copy the copy of the source element into the target position
      shiftLeftOneByOne(source);                                                // Next source
      shiftLeftOneByOne(target);                                                // Next target
     }
    unary.dec();                                                                // New number of elements on stuck after one has been shifted out
   }

  void unshift(Layout ElementToUnShift)                                         // Unshift an element from the stuck by moving all the elements up one place
   {ones(source);                                                               // Start the source at the top
    ones(target);                                                               // Start the target at the top

    shiftRightOneByZero(target);                                                // Last slot
    shiftRightOneByZero(source);                                                // Last slot
    shiftRightOneByZero(source);                                                // Second to last slot

    for (int i = 1; i < max; ++i)                                               // Shift the stuck up one place
     {setIndex(array, source);                                                  // Index source
      copy(buffer, element);                                                    // Copy source
      setIndex(array, target);                                                  // Index target
      copy(element, buffer);                                                    // Copy target
      shiftRightOneByZero(source);                                              // Move target down one
      shiftRightOneByZero(target);                                              // Move source down one
     }
    zero(target);                                                               // Index of the first element
    setIndex(array, target);                                                    // Index the first element
    copy(element, ElementToUnShift.getLayoutField());                           // Copy in the new element
    unary.inc();                                                                // New number of elements on stuck
   }

  void elementAt(Layout elementOut, Layout.Variable index)                      // Return the element at the indicated zero based index
   {setIndex(array, index);                                                     // Index of required element
    copy(elementOut.getLayoutField(), element);                                 // Copy element out
   }

  void setElementAt(Layout elementIn, Layout.Variable index)                    // Set the element at the indicated zero based index
   {setIndex(array, index);                                                     // Index of element to set
    copy(element, elementIn.getLayoutField());                                  // Copy element in
   }

  void insertElementAt(Layout elementToInsert, Layout.Variable index)           // Insert an element represented as memory into the stuckstack at the indicated zero based index after moving the elements above up one position
   {ones(target);                                                               // Top of stuck
    ones(source);                                                               // Top of stuck
    shiftRightOneByZero(target);                                                // One step down on target
    shiftRightOneByZero(source);                                                // One step down on source
      final BranchOnCompare test = branchIfEqual(target, index);                // Test for finish of shifting phase
      shiftRightOneByZero(source);                                              // One step down on source
      setIndex(array, source);                                                  // Index of source
      copy(buffer, element);                                                    // Copy source into buffer
      setIndex(array, target);                                                  // Index of target
      copy(element, buffer);                                                    // Copy copy of osurce into target slot
      shiftRightOneByZero(target);                                              // One step down on target
      goTo(test);                                                               // Restart shifting loop
    comeFromComparison(test);                                                   // Exited shifting phase
    setIndex(array, index);                                                     // Index of element to set
    copy(element, elementToInsert.getLayoutField());                            // Copy in new element
   }

  void removeElementAt(Layout.Variable index)                                   // Remove the element at the indicated zero based index
   {copy(target, index);                                                        // Target of removal
    copy(source, target);                                                       // Source of removeal
    shiftLeftOneByOne(source);                                                  // One step down on source
    final Branch test = branchIfAllOnes(source);                                // Test for finish of shifting phase
      setIndex(array, source);                                                  // Index of source
      copy(buffer, element);                                                    // Copy source into buffer
      setIndex(array, target);                                                  // Index of target
      copy(element, buffer);                                                    // Copy copy of osurce into target slot
      shiftLeftOneByOne(target);                                                // One step down on target
      shiftLeftOneByOne(source);                                                // One step down on source
    goTo(test);                                                                 // Restart shifting loop
    comeFrom(test);                                                             // Finished shift down
    unary.dec();                                                                // New number of elements on stuck
   }

  void firstElement(Layout FirstElement)                                        // Get the first element
   {zero(source);                                                               // Index of first element
    setIndex(array, source);                                                    // Set index of first element
    copy(FirstElement.getLayoutField(), element);                               // Copy of first element
   }

  void lastElement(Layout LastElement)                                          // Get the last active element
   {copy(source, unary.value);                                                  // Index top of stuck
    shiftRightOneByZero(source);                                                // Index of top most active element
    setIndex(array, source);                                                    // Set index of topmost element
    copy(LastElement.getLayoutField(), element);                                // Copy of top most element
   }

//D1 Search                                                                     // Search a stuck.

  void indexOf(Layout elementToFind, Layout.Bit found, Layout.Variable index)   // Find the index of an element in the stuck and set the found flag to true else if no such element is found the found flag is set to false
   {zero(found);
    zero(index);
    Branch[]equal = new Branch[max];
    for (int i = 0; i < max; i++)
     {setIndex(array, index);
      equals(found, elementToFind.getLayoutField(), element);                   // Test for the element to be found
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
A    0    40      0                 array
S    0    10                 15       s
V    0     4                 15         key
V    4     6                  0         data
A   10    40      1                 array
S   10    10                 31       s
V   10     4                 15         key
V   14     6                  1         data
A   20    40      2                 array
S   20    10                 63       s
V   20     4                 15         key
V   24     6                  3         data
A   30    40      3                 array
S   30    10                127       s
V   30     4                 15         key
V   34     6                  7         data
V   40     4                 15     unary
""");
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    16                      structure
B    0     1                  1     e0
B    1     1                  0     e1
B    2     1                  0     e2
B    3     1                  0     e3
B    4     1                  0     e4
V    5     6                        value
B   11     1                  0     f0
B   12     1                  0     f1
B   13     1                  0     f2
B   14     1                  0     f3
B   15     1                  1     f4
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
      v1 = keyData.duplicate(),
      v2 = keyData.duplicate(),
      v3 = keyData.duplicate(),
      v4 = keyData.duplicate();

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
S    0    44                  0   S
A    0    40      0           0     array
S    0    10                 16       s
V    0     4                  0         key
V    4     6                  1         data
A   10    40      1           0     array
S   10    10                 48       s
V   10     4                  0         key
V   14     6                  3         data
A   20    40      2           0     array
S   20    10                112       s
V   20     4                  0         key
V   24     6                  7         data
A   30    40      3           0     array
S   30    10                240       s
V   30     4                  0         key
V   34     6                 15         data
V   40     4                  0     unary
""");
    //stop(v1);
    v1.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                240   s
V    0     4                  0     key
V    4     6                 15     data
""");
    //stop(v2);
    v2.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                112   s
V    0     4                  0     key
V    4     6                  7     data
""");
    //stop(v3);
    v3.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                 48   s
V    0     4                  0     key
V    4     6                  3     data
""");
    //stop(v4);
    v4.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                 16   s
V    0     4                  0     key
V    4     6                  1     data
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
      v1 = keyData.duplicate(),
      v2 = keyData.duplicate(),
      v3 = keyData.duplicate(),
      v4 = keyData.duplicate();

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
S    0    44                  0   s
A    0    40      0           0     array
S    0    10                240       s
V    0     4                  0         key
V    4     6                 15         data
A   10    40      1           0     array
S   10    10                240       s
V   10     4                  0         key
V   14     6                 15         data
A   20    40      2           0     array
S   20    10                240       s
V   20     4                  0         key
V   24     6                 15         data
A   30    40      3           0     array
S   30    10                240       s
V   30     4                  0         key
V   34     6                 15         data
V   40     4                  0     unary
""");

    //stop(v1);
    v1.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                 16   s
V    0     4                  0     key
V    4     6                  1     data
""");
    //stop(v2);
    v2.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                 48   s
V    0     4                  0     key
V    4     6                  3     data
""");
    //stop(v3);
    v3.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                112   s
V    0     4                  0     key
V    4     6                  7     data
""");

    //stop(v4);
    v4.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                240   s
V    0     4                  0     key
V    4     6                 15     data
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
S    0    44                  0   s
A    0    40      0           0     array
S    0    10                240       s
V    0     4                  0         key
V    4     6                 15         data
A   10    40      1           0     array
S   10    10                112       s
V   10     4                  0         key
V   14     6                  7         data
A   20    40      2           0     array
S   20    10                 48       s
V   20     4                  0         key
V   24     6                  3         data
A   30    40      3           0     array
S   30    10                 16       s
V   30     4                  0         key
V   34     6                  1         data
V   40     4                 15     unary
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
      v1 = keyData.duplicate(),
      v2 = keyData.duplicate(),
      v3 = keyData.duplicate(),
      v4 = keyData.duplicate();

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
S    0    44                  0   s
A    0    40      0           0     array
S    0    10                240       s
V    0     4                  0         key
V    4     6                 15         data
A   10    40      1           0     array
S   10    10                112       s
V   10     4                  0         key
V   14     6                  7         data
A   20    40      2           0     array
S   20    10                 48       s
V   20     4                  0         key
V   24     6                  3         data
A   30    40      3           0     array
S   30    10                 16       s
V   30     4                  0         key
V   34     6                  1         data
V   40     4                 15     unary
""");
    //stop(v4);
    v4.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                 16   s
V    0     4                  0     key
V    4     6                  1     data
""");
    //stop(v3);
    v3.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                 48   s
V    0     4                  0     key
V    4     6                  3     data
""");
    //stop(v2);
    v2.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                112   s
V    0     4                  0     key
V    4     6                  7     data
""");

    //stop(v1);
    v1.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                240   s
V    0     4                  0     key
V    4     6                 15     data
""");
   }

  static void test_insert_element_at()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);

    final Layout v1 = keyData.duplicate();
    final Layout v2 = keyData.duplicate();
    final Layout v3 = keyData.duplicate();
    final Layout v4 = keyData.duplicate();

    final Layout l = new Layout();
    final Layout.Variable i0 = l.variable ("i0", M);
    final Layout.Variable i1 = l.variable ("i1", M);
    final Layout.Variable i2 = l.variable ("i2", M);
    final Layout.Structure S = l.structure("S", i0, i1, i2);
    l.layout(S);
    i0.fromInt(0); v1.get("s.data").fromInt(11);
    i1.fromInt(1); v2.get("s.data").fromInt(22);
    i2.fromInt(3); v3.get("s.data").fromInt(33);
                   v4.get("s.data").fromInt(44);

    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    12                784   S
V    0     4                  0     i0
V    4     4                  1     i1
V    8     4                  3     i2
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
V    0     4                  0     key
V    4     6                 11     data
""");

    //stop(v2);
    v2.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                352   s
V    0     4                  0     key
V    4     6                 22     data
""");

    //stop(v3);
    v3.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                528   s
V    0     4                  0     key
V    4     6                 33     data
""");

    //stop(v4);
    v4.ok("""
T   At  Wide  Index       Value   Field name
S    0    10                704   s
V    0     4                  0     key
V    4     6                 44     data
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
S    0    44                  0   s
A    0    40      0           0     array
S    0    10                 11       s
V    0     4                 11         key
V    4     6                  0         data
A   10    40      1           0     array
S   10    10                 22       s
V   10     4                  6         key
V   14     6                  1         data
A   20    40      2           0     array
S   20    10                 33       s
V   20     4                  1         key
V   24     6                  2         data
A   30    40      3           0     array
S   30    10                 44       s
V   30     4                 12         key
V   34     6                  2         data
V   40     4                 15     unary
""");
    s.removeElementAt(i1);
    s.execute();

    //stop(s.layout);
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    44                  0   s
A    0    40      0           0     array
S    0    10                 11       s
V    0     4                 11         key
V    4     6                  0         data
A   10    40      1           0     array
S   10    10                 33       s
V   10     4                  1         key
V   14     6                  2         data
A   20    40      2           0     array
S   20    10                 44       s
V   20     4                 12         key
V   24     6                  2         data
A   30    40      3           0     array
S   30    10                 44       s
V   30     4                 12         key
V   34     6                  2         data
V   40     4                  7     unary
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
S    0    44                  0   t
A    0    40      0           0     array
S    0    10                 11       s
V    0     4                 11         key
V    4     6                  0         data
A   10    40      1           0     array
S   10    10                 44       s
V   10     4                 12         key
V   14     6                  2         data
A   20    40      2           0     array
S   20    10                 44       s
V   20     4                 12         key
V   24     6                  2         data
A   30    40      3           0     array
S   30    10                 44       s
V   30     4                 12         key
V   34     6                  2         data
V   40     4                  3     unary
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
S    0    44                  0   u
A    0    40      0           0     array
S    0    10                 44       s
V    0     4                 12         key
V    4     6                  2         data
A   10    40      1           0     array
S   10    10                 44       s
V   10     4                 12         key
V   14     6                  2         data
A   20    40      2           0     array
S   20    10                 44       s
V   20     4                 12         key
V   24     6                  2         data
A   30    40      3           0     array
S   30    10                 44       s
V   30     4                 12         key
V   34     6                  2         data
V   40     4                  1     unary
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
S    0    44                  0   v
A    0    40      0           0     array
S    0    10                 44       s
V    0     4                 12         key
V    4     6                  2         data
A   10    40      1           0     array
S   10    10                 44       s
V   10     4                 12         key
V   14     6                  2         data
A   20    40      2           0     array
S   20    10                 44       s
V   20     4                 12         key
V   24     6                  2         data
A   30    40      3           0     array
S   30    10                 44       s
V   30     4                 12         key
V   34     6                  2         data
V   40     4                  0     unary
""");
   }

  static void test_first()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);

    final Layout f = keyData.copy(), l = keyData.copy();

    Stuck s = stuck("s", M, keyData);

    s.array.zero();
    s.array.setIndex(0); s.element.fromInt(11);
    s.array.setIndex(1); s.element.fromInt(22);
    s.array.setIndex(2); s.element.fromInt(33);
    s.array.setIndex(3); s.element.fromInt(44);
    s.unary.value.fromInt(15);

    s.firstElement(f);
    //s.lastElement(l);
    s.execute();
    //stop(key);
    //f.ok(11);
say(s.layout);
say("AAAA", f);
//say("BBBB", l);
    //l.ok(44);
   }

/*

  static void test_last()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);

    Stuck s = stuck("s", M, keyData);

    s.array.zero();
    s.array.setIndex(0); s.element.fromInt(11);
    s.array.setIndex(1); s.element.fromInt(22);
    s.array.setIndex(2); s.element.fromInt(33);
    s.array.setIndex(3); s.element.fromInt(44);
    s.unary.value.fromInt(15);

    final Layout          l = new Layout();
    final Layout.Variable v = l.variable ("value", W);
    l.layout(v);

    s.lastElement(v);
    s.execute();
    //stop(v);
    v.ok(44);
   }

  static void test_index_of()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);

    Stuck s = stuck("s", M, keyData);

    s.array.zero();
    s.array.setIndex(0); s.element.fromInt(11);
    s.array.setIndex(1); s.element.fromInt(22);
    s.array.setIndex(2); s.element.fromInt(33);
    s.array.setIndex(3); s.element.fromInt(44);
    s.unary.value.fromInt(15);

    final Layout           l = new Layout();
    final Layout.Variable  i = l.variable ("i", M);
    final Layout.Bit       f = l.bit      ("f");
    final Layout.Variable  v = l.variable ("v", W);
    final Layout.Structure S = l.structure("s", i, f, v);
    l.layout(S);

    v.fromInt(22);
    s.indexOf(v, f, i);
    s.execute();
    //stop(i);
    i.ok(1);
    f.ok(1);
   }

  static void test_index_of_notFound()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);

    Stuck s = stuck("s", M, keyData);

    s.array.zero();
    s.array.setIndex(0); s.element.fromInt(11);
    s.array.setIndex(1); s.element.fromInt(22);
    s.array.setIndex(2); s.element.fromInt(33);
    s.array.setIndex(3); s.element.fromInt(44);
    s.unary.value.fromInt(15);

    final Layout           l = new Layout();
    final Layout.Variable  i = l.variable ("i", M);
    final Layout.Bit       f = l.bit      ("f");
    final Layout.Variable  v = l.variable ("v", W);
    final Layout.Structure S = l.structure("s", i, f, v);
    l.layout(S);

    v.fromInt(21);
    s.indexOf(v, f, i);
    s.execute();
    //stop(f);
    f.ok(0);
   }

  static void test_set_element_at()
   {final int W = 6, M = 4;

    final Layout           keyData = new Layout();
    final Layout.Variable  key     = keyData.variable("key",  M);
    final Layout.Variable  data    = keyData.variable("data", W);
    final Layout.Structure KeyData = keyData.structure("s", key, data);
    keyData.layout(KeyData);

    Stuck s = stuck("s", M, keyData);

    s.array.zero();
    s.array.setIndex(0); s.element.fromInt(11);
    s.array.setIndex(1); s.element.fromInt(22);
    s.array.setIndex(2); s.element.fromInt(33);
    s.array.setIndex(3); s.element.fromInt(44);
    s.unary.value.fromInt(15);

    final Layout           l = new Layout();
    final Layout.Variable  i = l.variable ("i", M);
    final Layout.Variable  j = l.variable ("j", W);
    final Layout.Structure S = l.structure("s", i, j);
    l.layout(S);

    i.fromInt(3);
    j.fromInt(55);

    s.setElementAt(j, i);
    s.execute();
    //stop(s.layout);
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    28          263419275   s
A    0    24      0    11761035     array
V    0     6                 11       element
A    6    24      1    11761035     array
V    6     6                 22       element
A   12    24      2    11761035     array
V   12     6                 55       element
A   18    24      3    11761035     array
V   18     6                 44       element
V   24     4                 15     unary
""");
   }
*/
  static void oldTests()                                                        // Tests thought to be in good shape
   {test_push();
    test_pop();
    test_shift();
    test_unshift();
    test_elementAt();
    test_insert_element_at();
    test_remove_element_at();
    //test_first();
    //test_last();
    //test_index_of();
    //test_index_of_notFound(); October 4th.
    //test_set_element_at();
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    test_first();
   }

  public static void main(String[] args)                                        // Test if called as a program
   {if (args.length > 0 && args[0].equals("compile")) System.exit(0);           // Do a syntax check
    try                                                                         // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_actions) oldTests(); else newTests();                          // Tests to run
      testSummary();                                                            // Summarize test results
     }
    catch(Exception e)                                                          // Get a traceback in a format clickable in Geany
     {System.err.println(e);
      System.err.println(fullTraceBack(e));
     }
   }
 }
