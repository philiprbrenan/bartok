//------------------------------------------------------------------------------
// Unary arithmetic using boolean arrays in BitMachine assembler.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Design, simulate and layout a binary tree on a silicon chip.

class UnaryBM extends BitMachine                                                // Unary arithmetic on a chip
 {final Layout          layout;                                                 // The layout of the unary number
  final Layout.Variable value;                                                  // The value of the unary number

//D1 Construction                                                               // Create a unary number

  UnaryBM(int Max)                                                              // Create a unary number of specified size
   {if (Max <= 0) stop("Unary size must be at least one, not", Max);            // Size check
    layout = new Layout();
    value  = layout.variable("value", Max);                                     // The value of the unary number
    layout.layout(value);                                                       // Layout memory
   }
  static UnaryBM unaryBM(int Max) {return new UnaryBM(Max);}                    // Create a unary number

  int max() {return value.width;}                                               // The maximum value of the unary number - override this method to set a non zero size

  void ok(int n) {ok(value.asInt(), n);}                                        // Check that a unary number has the expected value

  Layout.Variable get() {return value;}                                         // Get the unary number

//D1 Arithmetic                                                                 // Arithmetic using unary numbers

  void zero() {zero(value);}                                                    // Clear unary number to all zeros
  void ones() {ones(value);}                                                    // Set unary number to all ones

  void canInc(Layout.Bit result) {copy(result, 0, value, 0, 1); not(result);}   // Unary contains at least one one
  void canDec(Layout.Bit result) {copy(result, 0, value, 0, 1);}                // Unary contains at least one zero

  void inc() {shiftLeftOneByOne  (value);}                                      // Increment the unary number
  void dec() {shiftRightOneByZero(value);}                                      // Decrement the unary number

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

    UnaryBM   u = unaryBM(4);
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
S    0     4                  6   s
B    0     1                  0     a
B    1     1                  1     b
B    2     1                  1     c
B    3     1                  0     d
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
   }
 }
