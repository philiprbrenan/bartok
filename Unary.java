//------------------------------------------------------------------------------
// Unary arithmetic using boolean arrays on a bit machine.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Design, simulate and layout a binary tree on a silicon chip.

class Unary extends BitMachine implements LayoutAble                            // Unary arithmetic on a bit machine
 {final Layout          layout;                                                 // The layout of the unary number
  final Layout.Variable value;                                                  // The value of the unary number

  public Layout.Field asField () {return layout.top;}                           // Layout field associated with this class
  public Layout       asLayout() {return layout;    }                           // Layout associated with this class

//D1 Construction                                                               // Create a unary number

  Unary(int Max)                                                                // Create a unary number of specified size
   {super("Unary");
    if (Max <= 0) stop("Unary size must be at least one, not", Max);            // Size check
    layout = new Layout();
    value  = layout.variable("unary", Max);                                     // The value of the unary number
    layout.layout(value);                                                       // Layout memory
   }
  static Unary unary(int Max) {return new Unary(Max);}                          // Create a unary number

  int max() {return value.width;}                                               // The maximum value of the unary number - override this method to set a non zero size

  void ok(int n) {ok(value.asInt(), n);}                                        // Check that a unary number has the expected value

  Layout.Variable get() {return value;}                                         // Get the unary number
  Layout.Field layout() {return layout.top;}                                    // Get the topmost structure

//D1 Arithmetic                                                                 // Arithmetic using unary numbers

  void zero() {zero(value);}                                                    // Clear unary number to all zeros
  void ones() {ones(value);}                                                    // Set unary number to all ones

  void canInc   (Layout.Bit result) {copy(result, 0, value, value.width-1, 1); not(result);} // Not full
  void canNotInc(Layout.Bit result) {copy(result, 0, value, value.width-1, 1);}              // Full
  void canDec   (Layout.Bit result) {copy(result, 0, value, 0,             1);}              // Not empty
  void canNotDec(Layout.Bit result) {copy(result, 0, value, 0,             1); not(result);} // Empty

  void inc() {shiftLeftOneByOne  (value);}                                      // Increment the unary number
  void dec() {shiftRightOneByZero(value);}                                       // Decrement the unary number

//D1 Print                                                                      // Print a unary number

  public String toString() {return value.asString();}                           // Return the string

//D0 Tests                                                                      // Test unary numbers

  static void test_unary()
   {Layout           l = new Layout();
    Layout.Bit       a = l.bit      ("a");
    Layout.Bit       b = l.bit      ("b");
    Layout.Bit       c = l.bit      ("c");
    Layout.Bit       d = l.bit      ("d");
    Layout.Structure s = l.structure("s", a, b, c, d);
    l.layout(s);

    Unary u = unary(4);
    u.zero();
    u.canDec(a);
    u.canInc(b);
    u.inc();
    u.inc();
    u.inc();
    u.inc();
    u.canDec(c);
    u.canInc(d);
    u.execute();
    u.ok("1111");
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     4                  6   s     s
B    0     1                  0     a     s.a
B    1     1                  1     b     s.b
B    2     1                  1     c     s.c
B    3     1                  0     d     s.d
""");

    //stop(u.layout);
    u.layout.ok("""
T   At  Wide  Index       Value   Field name
V    0     4                 15   unary     unary
""");

    Unary u1 = unary(4);
    u1.layout.memory = u.layout.memory;
    u1.dec();
    u1.dec();
    u1.execute();
    //stop(u1.layout);
    u1.layout.ok("""
T   At  Wide  Index       Value   Field name
V    0     4                  3   unary     unary
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_unary();
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
    System.exit(testsFailed);
   }
 }
