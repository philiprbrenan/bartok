//------------------------------------------------------------------------------
// A fixed size stack of ordered bit keys in assembler on a bit machine.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Design, simulate and layout  a binary tree on a silicon chip.

import java.util.*;

class Stuck extends BitMachine                                                  // Stuck: a fixed size stack controlled by a unary number. The unary number zero indicates an empty stuck.
 {final String             name;                                                // The lname of the stuc: it does not have to be globally unique
  final Unary             unary;                                                // The layout of the stuck
  final Layout.Field    element;                                                // An element of the stuck
  final Layout.Array      array;                                                // The array holding the elements of the stuck
  final Layout.Structure  stuck;                                                // The stuck
  final Layout           layout;                                                // Layout of the stuck

  final Layout.Variable  source;                                                // Source index
  final Layout.Variable  target;                                                // Target index
  final Layout.Variable  buffer;                                                // Temporary buffer for moving data in or out of the stuck
  final Layout.Structure   temp;                                                // Temporary data structure
  final Layout          tempLay;                                                // Layout of temporary data

  final int max;                                                                // The maximum number of entries in the stuck.
  final int width;                                                              // The width of each object in the stuck in bits

  BitMachine bitMachine = this;                                                 // The bit machine in which to load instructions

//D1 Construction                                                               // Create a stuck

  Stuck(String Name, int Max, int Width)                                        // Create the stuck
   {name = Name; width = Width; max = Max;                                      // Parameterize the stuck
    unary    = Unary.unary(max);                                                // Unary number showing which elements in the stack are valid
    unary.bitMachine = this;                                                    // Unary instructions to be placed in this machine
    layout   = new Layout();                                                    // An element of the stuck
    element  = layout.variable ("element", width);                              // An element of the stuck
    array    = layout.array    ("array",   element, max);                       // An array of elements comprising the stuck
    stuck    = layout.structure(name,      array,   unary.layout.top);          // An array of elements comprising the stuck
    layout.layout(stuck);                                                       // Layout the structure of the stuck
    unary.layout.memory = layout.memory;                                        // Make our memory superceded the default memeory created with unary.
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
  static Stuck stuck(String Name, int Max, int Width)                           // Create the stuck
   {return new Stuck(Name, Max, Width);
   }

  void clear() {zero(stuck);}                                                   // Clear a stuck

  public void ok(String expected) {ok(toString(), expected);}                   // Check the stuck

//D1 Characteristics                                                            // Characteristics of the stuck

  void isFull (Layout.Bit result) {unary.canNotInc(result);}                    // Check the stuck is full
  void isEmpty(Layout.Bit result) {unary.canNotDec(result);}                    // Check the stuck is empty

//D1 Actions                                                                    // Place and remove data to/from stuck

  void push(Layout.Field ElementToPush)                                         // Push an element onto the stuck
   {setIndex(array, unary.value);                                               // Index stuck
    copy(element, ElementToPush);                                               // Copy data into the stuck
    unary.inc();                                                                // Show next free slot
   }

  void pop(Layout.Field PoppedElement)                                          // Pop an element from the stuck
   {unary.dec();                                                                // Index of top most element
    setIndex(array, unary.value);                                               // Set index of topmost element
    copy(PoppedElement, element);                                               // Copy data out of the stuck
   }

  void shift(Layout.Field ShiftedElement)                                       // Shift an element from the stuck
   {zero(target);                                                               // Index of the first element
    setIndex(array, target);                                                    // Index of first element
    copy(ShiftedElement, element);                                              // Copy shifted element out
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

  void unshift(Layout.Field ElementToUnShift)                                   // Unshift an element from the stuck by moving all the elements up one place
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
    copy(element, ElementToUnShift);                                            // Copy in the new element
    unary.inc();                                                                // New number of elements on stuck
   }

  void elementAt(Layout.Field elementOut, Layout.Variable index)                // Return the element at the indicated zero based index
   {setIndex(array, index);                                                     // Index of required element
    copy(elementOut, element);                                                  // Copy element out
   }

  void setElementAt(Layout.Field elementIn, Layout.Variable index)              // Set the element at the indicated zero based index
   {setIndex(array, index);                                                     // Index of element to set
    copy(element, elementIn);                                                   // Copy element in
   }

  void insertElementAt(Layout.Field elementToInsert, Layout.Variable index)     // Insert an element represented as memory into the stuckstack at the indicated zero based index after moving the elements above up one position
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
    copy(element, elementToInsert);                                             // Copy in new element
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

  void firstElement(Layout.Field FirstElement)                                  // Get the first element
   {zero(source);                                                               // Index of first element
    setIndex(array, source);                                                    // Set index of first element
    copy(FirstElement, element);                                                // Copy of first element
   }

  void lastElement(Layout.Field LastElement)                                    // Get the last active element
   {copy(source, unary.value);                                                  // Index top of stuck
    shiftRightOneByZero(source);                                                // Index of top most active element
    setIndex(array, source);                                                    // Set index of topmost element
    copy(LastElement, element);                                                 // Copy of top most element
   }

//D1 Search                                                                     // Search a stuck.

  void indexOf                                                                  // Find the index of an element in the stuck and set the found flag to true else if no such element is found the found flag is set to false
   (Layout.Field elementToFind, Layout.Bit found, Layout.Variable index)        // Set found to true and index to the 0 based index of the indicated memory else -1 if the memory is not present in the stuck.
   {zero(found);
    zero(index);
    Branch[]equal = new Branch[max];
    for (int i = 0; i < max; i++)
     {setIndex(array, index);
      equals(found, elementToFind, element);                                    // Test for the element to be found
      equal[i] = branchIfOne(found);
      shiftLeftOneByOne(index);
     }
    for (int i = 0; i < max; i++) comeFrom(equal[i]);
   }

//D1 Print                                                                      // Print a stuck

//D0 Tests                                                                      // Test stuck

  static void test_push()
   {final int W = 6, M = 4;

    final Layout l = new Layout();
    final Layout.Bit
      e0 = l.bit("e0"), f0 = l.bit("f0"),
      e1 = l.bit("e1"), f1 = l.bit("f1"),
      e2 = l.bit("e2"), f2 = l.bit("f2"),
      e3 = l.bit("e3"), f3 = l.bit("f3"),
      e4 = l.bit("e4"), f4 = l.bit("f4");
    final Layout.Variable value = l.variable("value", W);
    final Layout.Structure    S = l.structure("structure", e0, e1, e2, e3, e4,
       value,                                              f0, f1, f2, f3, f4);
    l.layout(S);

    Stuck s = stuck("s", M, W);
    s.isEmpty(e0);
    s.isFull(f0);
    s.zero(value);              s.push(value); s.isEmpty(e1); s.isFull(f1);
    s.shiftLeftOneByOne(value); s.push(value); s.isEmpty(e2); s.isFull(f2);
    s.shiftLeftOneByOne(value); s.push(value); s.isEmpty(e3); s.isFull(f3);
    s.shiftLeftOneByOne(value); s.push(value); s.isEmpty(e4); s.isFull(f4);
    //stop(s);
    s.execute();
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    16              32993   structure
B    0     1                  1     e0
B    1     1                  0     e1
B    2     1                  0     e2
B    3     1                  0     e3
B    4     1                  0     e4
V    5     6                  7     value
B   11     1                  0     f0
B   12     1                  0     f1
B   13     1                  0     f2
B   14     1                  0     f3
B   15     1                  1     f4
""");
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    28          253505600   s
A    0    24      0     1847360     array
V    0     6                  0       element
A    6    24      1     1847360     array
V    6     6                  1       element
A   12    24      2     1847360     array
V   12     6                  3       element
A   18    24      3     1847360     array
V   18     6                  7       element
V   24     4                 15     unary
""");
   }

  static void test_pop()
   {final int W = 6, M = 4;

    final Layout l = new Layout();
    final Layout.Variable
      v1    = l.variable("v1",    W),
      v2    = l.variable("v2",    W),
      v3    = l.variable("v3",    W),
      v4    = l.variable("v4",    W),
      value = l.variable("value", W);
    final Layout.Structure S = l.structure("S", v1, v2, v3, v4, value);
    l.layout(S);
    S.zero();

    Stuck s = stuck("s", M, W);
    s.shiftLeftOneByOne(value); s.push(value);
    s.shiftLeftOneByOne(value); s.push(value);
    s.shiftLeftOneByOne(value); s.push(value);
    s.shiftLeftOneByOne(value); s.push(value);
    s.pop(v1);
    s.pop(v2);
    s.pop(v3);
    s.pop(v4);
    s.execute();
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    28            3961025   s
A    0    24      0     3961025     array
V    0     6                  1       element
A    6    24      1     3961025     array
V    6     6                  3       element
A   12    24      2     3961025     array
V   12     6                  7       element
A   18    24      3     3961025     array
V   18     6                 15       element
V   24     4                  0     unary
""");
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    30          251933135   S
V    0     6                 15     v1
V    6     6                  7     v2
V   12     6                  3     v3
V   18     6                  1     v4
V   24     6                 15     value
""");
   }

  static void test_shift()
   {final int W = 6, M = 4;

    final Layout l = new Layout();
    final Layout.Variable
      v1    = l.variable("v1",    W),
      v2    = l.variable("v2",    W),
      v3    = l.variable("v3",    W),
      v4    = l.variable("v4",    W),
      value = l.variable("value", W);
    final Layout.Structure S = l.structure("S", v1, v2, v3, v4, value);
    l.layout(S);
    S.zero();

    Stuck s = stuck("s", M, W);
    s.shiftLeftOneByOne(value); s.push(value);
    s.shiftLeftOneByOne(value); s.push(value);
    s.shiftLeftOneByOne(value); s.push(value);
    s.shiftLeftOneByOne(value); s.push(value);
    s.shift(v1);
    s.shift(v2);
    s.shift(v3);
    s.shift(v4);
    s.execute();
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    28            3994575   s
A    0    24      0     3994575     array
V    0     6                 15       element
A    6    24      1     3994575     array
V    6     6                 15       element
A   12    24      2     3994575     array
V   12     6                 15       element
A   18    24      3     3994575     array
V   18     6                 15       element
V   24     4                  0     unary
""");

    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    30          255619265   S
V    0     6                  1     v1
V    6     6                  3     v2
V   12     6                  7     v3
V   18     6                 15     v4
V   24     6                 15     value
""");
   }

  static void test_unshift()
   {final int W = 6, M = 4;

    final Layout l = new Layout();
    final Layout.Variable value = l.variable("value", W);
    l.layout(value);

    Stuck s = stuck("s", M, W);
    s.zero(value);
    s.shiftLeftOneByOne(value); s.unshift(value);
    s.shiftLeftOneByOne(value); s.unshift(value);
    s.shiftLeftOneByOne(value); s.unshift(value);
    s.shiftLeftOneByOne(value); s.unshift(value);
    s.execute();

    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    28          251933135   s
A    0    24      0      274895     array
V    0     6                 15       element
A    6    24      1      274895     array
V    6     6                  7       element
A   12    24      2      274895     array
V   12     6                  3       element
A   18    24      3      274895     array
V   18     6                  1       element
V   24     4                 15     unary
""");
   }

  static void test_elementAt()
   {final int W = 6, M = 4;

    final Layout l = new Layout();
    final Layout.Variable value = l.variable ("value", W);
    final Layout.Variable v1    = l.variable ("v1",    W);
    final Layout.Variable v2    = l.variable ("v2",    W);
    final Layout.Variable v3    = l.variable ("v3",    W);
    final Layout.Variable v4    = l.variable ("v4",    W);
    final Layout.Variable index = l.variable ("index", M);
    final Layout.Structure S    = l.structure("S",
      value, v1, v2, v3, v4, index);
    l.layout(S);

    Stuck s = stuck("s", M, W);
    s.zero(value);
    s.shiftLeftOneByOne(value); s.unshift(value);
    s.shiftLeftOneByOne(value); s.unshift(value);
    s.shiftLeftOneByOne(value); s.unshift(value);
    s.shiftLeftOneByOne(value); s.unshift(value);
    s.zero             (index); s.elementAt(v1, index);
    s.shiftLeftOneByOne(index); s.elementAt(v2, index);
    s.shiftLeftOneByOne(index); s.elementAt(v3, index);
    s.shiftLeftOneByOne(index); s.elementAt(v4, index);
    s.execute();

    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    28          251933135   s
A    0    24      0      274895     array
V    0     6                 15       element
A    6    24      1      274895     array
V    6     6                  7       element
A   12    24      2      274895     array
V   12     6                  3       element
A   18    24      3      274895     array
V   18     6                  1       element
V   24     4                 15     unary
""");
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    34                      S
V    0     6                 15     value
V    6     6                 15     v1
V   12     6                  7     v2
V   18     6                  3     v3
V   24     6                  1     v4
V   30     4                  7     index
""");

   }

  static void test_insert_element_at()
   {final int W = 6, M = 4;

    final Layout l = new Layout();
    final Layout.Variable v1 = l.variable ("v1",    W);
    final Layout.Variable v2 = l.variable ("v2",    W);
    final Layout.Variable v3 = l.variable ("v3",    W);
    final Layout.Variable v4 = l.variable ("v4",    W);
    final Layout.Variable i0 = l.variable ("i0",    M);
    final Layout.Variable i1 = l.variable ("i1",    M);
    final Layout.Variable i2 = l.variable ("i2",    M);
    final Layout.Structure S = l.structure("S",
      v1, v2, v3, v4, i0, i1, i2);
    l.layout(S);
    i0.fromInt(0); v1.fromInt(11);
    i1.fromInt(1); v2.fromInt(22);
    i2.fromInt(3); v3.fromInt(33);
                   v4.fromInt(44);

    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    36                      S
V    0     6                 11     v1
V    6     6                 22     v2
V   12     6                 33     v3
V   18     6                 44     v4
V   24     4                  0     i0
V   28     4                  1     i1
V   32     4                  3     i2
""");

    Stuck s = stuck("s", M, W);
    s.insertElementAt(v3, i0);
    s.insertElementAt(v2, i1);
    s.insertElementAt(v1, i2);
    s.insertElementAt(v4, i1);
    s.execute();

    //stop(s.layout);
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    28            2976545   s
A    0    24      0     2976545     array
V    0     6                 33       element
A    6    24      1     2976545     array
V    6     6                 44       element
A   12    24      2     2976545     array
V   12     6                 22       element
A   18    24      3     2976545     array
V   18     6                 11       element
V   24     4                  0     unary
""");
   }

  static void test_remove_element_at()
   {final int W = 6, M = 4;

    final Layout l = new Layout();
    final Layout.Variable i0 = l.variable ("i0",    M);
    final Layout.Variable i1 = l.variable ("i1",    M);
    final Layout.Variable i2 = l.variable ("i2",    M);
    final Layout.Structure S = l.structure("S", i0, i1, i2);
    l .layout (S);
    i0.fromInt(0);
    i1.fromInt(1);
    i2.fromInt(3);

    Stuck s = stuck("s", M, W);

    s.array.zero();
    s.array.setIndex(0); s.element.fromInt(11);
    s.array.setIndex(1); s.element.fromInt(22);
    s.array.setIndex(2); s.element.fromInt(33);
    s.array.setIndex(3); s.element.fromInt(44);
    s.unary.value.fromInt(15);

    //stop(s.layout);
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    28          263329163   s
A    0    24      0    11670923     array
V    0     6                 11       element
A    6    24      1    11670923     array
V    6     6                 22       element
A   12    24      2    11670923     array
V   12     6                 33       element
A   18    24      3    11670923     array
V   18     6                 44       element
V   24     4                 15     unary
""");
    s.removeElementAt(i1);
    s.execute();

    //stop(s.layout);
    s.layout.ok("""
T   At  Wide  Index       Value   Field name
S    0    28          129157195   s
A    0    24      0    11716683     array
V    0     6                 11       element
A    6    24      1    11716683     array
V    6     6                 33       element
A   12    24      2    11716683     array
V   12     6                 44       element
A   18    24      3    11716683     array
V   18     6                 44       element
V   24     4                  7     unary
""");

    Stuck t = stuck("t", M, W);

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
S    0    28           62049035   t
A    0    24      0    11717387     array
V    0     6                 11       element
A    6    24      1    11717387     array
V    6     6                 44       element
A   12    24      2    11717387     array
V   12     6                 44       element
A   18    24      3    11717387     array
V   18     6                 44       element
V   24     4                  3     unary
""");

    Stuck u = stuck("u", M, W);

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
S    0    28           28494636   u
A    0    24      0    11717420     array
V    0     6                 44       element
A    6    24      1    11717420     array
V    6     6                 44       element
A   12    24      2    11717420     array
V   12     6                 44       element
A   18    24      3    11717420     array
V   18     6                 44       element
V   24     4                  1     unary
""");

    Stuck v = stuck("v", M, W);

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
S    0    28           11717420   v
A    0    24      0    11717420     array
V    0     6                 44       element
A    6    24      1    11717420     array
V    6     6                 44       element
A   12    24      2    11717420     array
V   12     6                 44       element
A   18    24      3    11717420     array
V   18     6                 44       element
V   24     4                  0     unary
""");
   }

  static void test_first()
   {final int W = 6, M = 4;

    Stuck s = stuck("s", M, W);

    s.array.zero();
    s.array.setIndex(0); s.element.fromInt(11);
    s.array.setIndex(1); s.element.fromInt(22);
    s.array.setIndex(2); s.element.fromInt(33);
    s.array.setIndex(3); s.element.fromInt(44);
    s.unary.value.fromInt(15);

    final Layout          l = new Layout();
    final Layout.Variable v = l.variable ("value", W);
    l.layout(v);

    s.firstElement(v);
    s.execute();
    //stop(v);
    v.ok(11);
   }

  static void test_last()
   {final int W = 6, M = 4;

    Stuck s = stuck("s", M, W);

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

    Stuck s = stuck("s", M, W);

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

    Stuck s = stuck("s", M, W);

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

    Stuck s = stuck("s", M, W);

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

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_push();
    test_pop();
    test_shift();
    test_unshift();
    test_elementAt();
    test_insert_element_at();
    test_remove_element_at();
    test_first();
    test_last();
    test_index_of();
    test_index_of_notFound();
    test_set_element_at();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
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
