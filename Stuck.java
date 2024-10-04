//------------------------------------------------------------------------------
// A fixed size stack of ordered bit keys on a bit machine.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Design, simulate and layout  a binary tree on a silicon chip.

import java.util.*;

class Stuck extends BitMachine                                                  // Stuck: a fixed size stack controlled by a unary number. The unary number zero indicates an empty stuck stack.
 {final Unary             unary;                                                // The layout of the stuck stack
  final Layout.Field    element;                                                // An element of the stuck stack
  final Layout.Array      array;                                                // The array holding the elements of the stuck stack
  final Layout.Structure  stuck;                                                // The array holding the elements of the stuck stack
  final Layout           layout;                                                // The array holding the elements of the stuck stack

  final Layout.Variable    zero;                                                // Stuck is full flag
  final Layout.Variable   index;                                                // Stuck is full flag
  final Layout.Variable  source;                                                // Stuck is full flag
  final Layout.Variable  target;                                                // Stuck is full flag
  final Layout.Variable  buffer;                                                // Stuck is full flag
  final Layout.Structure   temp;                                                // Temporary data structure
  final Layout          tempLay;                                                // Layout of temporary data

  final int max;                                                                // The maximum number of entries in the stuck stack.
  final int width;                                                              // The width of each object in the stuck in bits

  BitMachine bitMachine = this;                                                 // The bit machine in which to load instructions

//D1 Construction                                                               // Create a stuck stack

  Stuck(int Max, int Width)                                                     // Create the stuck stack
   {width    = Width; max = Max;
    unary    = Unary.unary(max);                                                // Unary number showing which elements in the stack are valid
    unary.bitMachine = this;                                                    // Unary instructions to be placed in this machine
    layout   = new Layout();                                                    // An element of the stuck stack
    element  = layout.variable ("element",   width);                            // An element of the stuck stack
    array    = layout.array    ("array",     element, max);                     // An array of elements comprising the stuck stack
    stuck    = layout.structure("structure", array,   unary.layout.top);        // An array of elements comprising the stuck stack
    layout.layout(stuck);                                                       // Layout the structure of the stuck stack
    unary.layout.memory = layout.memory;                                        // Make our memory superceded the default memeory created with unary.
    stuck.zero();
    tempLay  = new Layout();                                                    // Temporary storage
    zero     = tempLay.variable ("zero",    max);                               // Stuck is full flag
    index    = tempLay.variable ("index",   max);                               // Stuck is full flag
    source   = tempLay.variable ("source",  max);                               // Stuck is full flag
    target   = tempLay.variable ("target",  max);                               // Stuck is full flag
    buffer   = tempLay.variable ("buffer",  width);                             // Stuck is full flag
    temp     = tempLay.structure("structure", zero, index,                      // An array of elements comprising the stuck stack
      source, target, buffer);
    tempLay.layout(temp);                                                       // Layout the structure of the stuck stack
    temp.zero();                                                                // Clear temporary storage
   }

  static Stuck stuck(int Max, int Width) {return new Stuck(Max, Width);}        // Create the stuck stack

  void clear() {zero(stuck);}                                                   // Clear a stuck stack

  public void ok(String expected) {ok(toString(), expected);}                   // Check the stuck stack

//D1 Characteristics                                                            // Characteristics of the stuck stack

  void isFull (Layout.Bit result) {unary.canNotInc(result);}                    // Check the stuck stack is full
  void isEmpty(Layout.Bit result) {unary.canNotDec(result);}                    // Check the stuck stack is empty

//D1 Actions                                                                    // Place and remove data to/from stuck stack

  void push(Layout.Field ElementToPush)                                         // Push an element as memory onto the stuck stack
   {setIndex(array, unary.value);                                               // Index stuck memory
    copy(element, ElementToPush);                                               // Set memory of stuck stack from supplied memory
    unary.inc();                                                                // Show new slot in use
   }

  void pop(Layout.Field PoppedElement)                                          // Pop an element as memory from the stuck stack
   {unary.dec();                                                                // New number of elements on stuck stack
    setIndex(array, unary.value);                                               // Index stuck memory
    copy(PoppedElement, element);                                               // Set memory of stuck stack from supplied memory
   }

  void shift(Layout.Field ShiftedElement)                                       // Shift an element as memory from the stuck stack
   {zero(target);
    setIndex(array, target);
    copy(ShiftedElement, element);                                              // Copy the slice of memory
    zero(source);
    shiftLeftOneByOne(source);

    for (int i = 1; i < max; i++)                                               // Shift the stuck stack down place
     {setIndex(array, source);
      copy(buffer, element);                                                    // Copy the slice of memory
      setIndex(array, target);
      copy(element, buffer);                                                    // Copy the slice of memory
      shiftLeftOneByOne(source);
      shiftLeftOneByOne(target);
     }
    unary.dec();                                                                // New number of elements on stuck stack
   }

  void unshift(Layout.Field ElementToUnShift)                                   // Unshift an element as memory onto the stuck stack
   {zero(source);
    zero(target);

    for (int i = 0; i < max; i++)                                               // Shift the stuck stack down place
     {shiftLeftOneByOne(source);
      shiftLeftOneByOne(target);
     }
    shiftRightOneByZero(source);

    for (int i = max; i > 1; --i)                                               // Shift the stuck stack down place
     {setIndex(array, source);
      copy(buffer, element);                                                    // Copy the slice of memory
      setIndex(array, target);
      copy(element, buffer);                                                    // Copy the slice of memory
      shiftRightOneByZero(source);
      shiftRightOneByZero(target);
     }
    setIndex(array, zero);
    copy(element, ElementToUnShift);                                            // Copy the slice of memory
    unary.inc();                                                                // New number of elements on stuck stack
   }

  void elementAt(Layout.Field elementOut, int n)                                // Return the element at the indicated constant index
   {zero(index);
    for (int i = 0; i < n; i++) shiftLeftOneByOne(index);                       // Shift the stuck stack down place
    copy(elementOut, element);
   }

  void setElementAt(Layout.Field elementIn, int n)                              // Return the element at the indicated constant index
   {zero(index);
    for (int i = 0; i < n; i++) shiftLeftOneByOne(index);                       // Shift the stuck stack down place
    copy(element, elementIn);
   }

  void insertElementAt(Layout.Field elementToInsert, Layout.Field i)            // Insert an element represented as memory into the stuck stack at the indicated 0-based index after moving the elements above up one position
   {ones(target);                                                               // Top of stuck stack
    ones(source);                                                               // Top of stuck stack
    final BranchOnCompare test = branchIfEqual(target, i);                      // Test for finish of shifting phase
      shiftRightOneByZero(source);                                              // One step down on source
      setIndex(array, source);
      copy(buffer, element);
      setIndex(array, target);
      copy(element, buffer);
      shiftRightOneByZero(target);                                              //
    goTo(test);
    comeFromComparison(test);
   }

  void removeElementAt(Layout.Field i)                                          // Remove the Memory at 0 based index i and shift the Memorys above down one position
   {copy(target, i);                                                            // Target of removal
    copy(source, target);                                                       // Source of removeal
    final Branch test = branchIfAllOnes(target);                                // Test for finish of shifting phase
      shiftLeftOneByOne(source);                                                // One step down on source
      setIndex(array, source);
      copy(buffer, element);
      setIndex(array, target);
      copy(element, buffer);
      shiftLeftOneByOne(target);                                                //
    goTo(test);
    comeFrom(test);
   }

  void firstElement(Layout.Field FirstElement)                                  // Get the first element
   {setIndex(array, zero);
    copy(FirstElement, element);
   }

  void lastElement(Layout.Field LastElement)                                    // Get the last active element
   {setIndex(array, unary.value);
    copy(LastElement, element);
   }

//D1 Search                                                                     // Search a stuck stack.

  void indexOf                                                                  // Find the index of an element in the stuck and set the found flag to true else if no such element is found the found flag is set to false
   (Layout.Field elementToFind, Layout.Bit found, Layout.Variable index)        // Set found to true and index to the 0 based index of the indicated memory else -1 if the memory is not present in the stuck stack.
   {zero(found);
    zero(index);
    Branch equal = null;
    for (int i = 0; i < max; i++)
     {setIndex(array, index);
      equals(found, elementToFind, element);                                    // Test for the element to be found
      equal = branchIfOne(found);
      shiftLeftOneByOne(index);
     }
    comeFrom(equal);
   }

//D1 Print                                                                      // Print a stuck stack

//D0 Tests                                                                      // Test stuck stack

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

    Stuck s = stuck(M, W);
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
S    0    28          253505600   structure
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

    Stuck s = stuck(M, W);
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
S    0    28            3961025   structure
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

    Stuck s = stuck(M, W);
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
S    0    28            3994575   structure
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
/*

  static void test_unshift()
   {final int W = 4, M = 4;
    Stuck s = stuck(M, W);
                  ok(s.stuckSize(), 0);
    s.unshift(1); ok(s.stuckSize(), 1); s.ok("Stuck(1)");
    s.unshift(2); ok(s.stuckSize(), 2); s.ok("Stuck(2, 1)");
    s.unshift(3); ok(s.stuckSize(), 3); s.ok("Stuck(3, 2, 1)");
    s.unshift(9); ok(s.stuckSize(), 4); s.ok("Stuck(9, 3, 2, 1)");
   }

  static void test_element_at()
   {final int W = 4, M = 4;
    Stuck s = stuck(M, W);
    s.push(1); s.push(2); s.push(3); s.push(12);
    s.elementAt(0).ok(1);
    s.elementAt(1).ok(2);
    s.elementAt(2).ok(3);
    s.elementAt(3).ok(12);
   }

  static void test_insert_element_at()
   {final int W = 4, M = 8;
    Stuck s = stuck(M, W);
    s.insertElementAt(3, 0); s.ok("Stuck(3)");
    s.insertElementAt(2, 1); s.ok("Stuck(3, 2)");
    s.insertElementAt(1, 2); s.ok("Stuck(3, 2, 1)");
    s.insertElementAt(4, 1); s.ok("Stuck(3, 4, 2, 1)");
   }

  static void test_remove_element_at()
   {final int W = 4, M = 4;
    Stuck s = stuck(M, W);
    s.push(1); s.push(2); s.push(3); s.push(12);
    s.removeElementAt(1).ok(2);   s.ok("Stuck(1, 3, 12)");
    s.removeElementAt(1).ok(3);   s.ok("Stuck(1, 12)");
    s.removeElementAt(0).ok(1);   s.ok("Stuck(12)");
    s.removeElementAt(0).ok(12);  s.ok("Stuck()");
   }

  static void test_first_last()
   {final int W = 4, M = 4;
    Stuck s = stuck(M, W);
                s.notEmpty.ok(0); s.full.ok(0);
    s.push( 1); s.notEmpty.ok(1); s.full.ok(0);
    s.push( 2); s.notEmpty.ok(1); s.full.ok(0);
    s.push( 3); s.notEmpty.ok(1); s.full.ok(0);
    s.push(12); s.notEmpty.ok(1); s.full.ok(1);
    s.firstElement().ok(1);
    s.lastElement() .ok(12);
   }

  static void test_index_of()
   {final int W = 4, M = 4;
    Stuck s = stuck(M, W);
    s.push(1); s.push(2); s.push(3); s.push(6);
    ok(s.indexOf(1),  0);
    ok(s.indexOf(2),  1);
    ok(s.indexOf(3),  2);
    ok(s.indexOf(6),  3);
    ok(s.indexOf(7), -1);
   }

  static void test_clear()
   {final int W = 4, M = 4;
    Stuck s = stuck(M, W);
    s.full.ok(0); s.notEmpty.ok(0);
    s.push(1); s.push(2); s.push(3); s.push(6);
    s.full.ok(1); s.notEmpty.ok(1); s.clear();
    s.full.ok(0); s.notEmpty.ok(0);
    s.ok("Stuck()");
   }

  static void test_print()
   {final int W = 4, M = 4;
    Stuck s = stuck(M, W);
    s.push(1); s.push(2); s.push(3); s.push(6);
    s.ok("Stuck(1, 2, 3, 6)");
    ok(s.print("[","]"), "[1, 2, 3, 6]");
   }

  static void test_set_element_at()
   {final int W = 4, M = 8;
    Stuck s = stuck(M, W);

    s.setElementAt(1, 0); s.ok("Stuck(1)");
    s.setElementAt(2, 1); s.ok("Stuck(1, 2)");
    s.setElementAt(3, 2); s.ok("Stuck(1, 2, 3)");
    s.setElementAt(4, 0); s.ok("Stuck(4, 2, 3)");
    s.setElementAt(5, 1); s.ok("Stuck(4, 5, 3)");
    s.setElementAt(6, 2); s.ok("Stuck(4, 5, 6)");
    s.setElementAt(7, 0); s.ok("Stuck(7, 5, 6)");
   }
*/
  static void oldTests()                                                        // Tests thought to be in good shape
   {test_push();
    test_pop();
    //test_shift();
    //test_unshift();
    //test_element_at();
    //test_insert_element_at();
    //test_remove_element_at();
    //test_first_last();
    //test_index_of();
    //test_clear();
    //test_print();
    //test_set_element_at();
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    test_shift();
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
