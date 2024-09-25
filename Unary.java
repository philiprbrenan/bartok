//------------------------------------------------------------------------------
// Unary arithmetic using boolean arrays.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Design, simulate and layout a binary tree on a silicon chip.

abstract class Unary extends Chip                                               // Unary arithmetic on a chip
 {final String      name;                                                       // Name of the chip
  final Variable notZero;                                                       // Not zero if true
  final Variable   atMax;                                                       // At the maximum value
  final Variable   value;                                                       // The value of the unary number
  final Structure  unary;                                                       // Structure representing a unary number
  final Bit    decrement;                                                       // If true decrement the unary number
  final Bit    increment;                                                       // If true increment the unary number
  final Bits     incBits;                                                       // Result of an increment
  final Bits     decBits;                                                       // Result of a decrement
  final Bits   enIncBits;                                                       // Enable result of an increment
  final Bits   enDecBits;                                                       // Enable result of a decrement

//D1 Construction                                                               // Create a unary number


  Unary(String Name)                                                            // Create a unary number of specified size
   {super(Name);                                                                // Structure definition
    if (max() <= 0) stop("Unary size must be at least one, not", max());        // Size check
    name       = Name;                                                          // Name of the chip
    notZero    = (Variable) layout.getField("unary.notZero");                   // Not zero if true
    atMax      = (Variable) layout.getField("unary.atMax");                     // At the maximum value
    value      = (Variable) layout.getField("unary.value");                     // The value of the unary number
    unary      = (Structure)layout.getField("unary");                           // The value of the unary number
    increment  = bit(name, "increment");                                        // Increment the value of the unary number if true
    decrement  = bit(name, "decrement");                                        // Decrement the value of the unary number if true
    incBits    = bits("incBits",   max());                                      // Result of an increment
    decBits    = bits("decBits",   max());                                      // Result of a decrement
    enIncBits  = bits("enIncBits", max());                                      // Enabled increment
    enDecBits  = bits("enDecBits", max());                                      // Disabled increment

    incBits.shiftLeftOnceByOne  (value.output());                               // Increment
    decBits.shiftRightOnceByZero(value.output());                               // Decrement

    enIncBits.enable(incBits, increment);                                       // Enable increment if requested
    enDecBits.enable(incBits, decrement);                                       // Enable decrement if requested
    value.input().or(enIncBits, enDecBits);                                     // Assemble result
   }

  Layout layout()                                                               // Memory layout of a unary number
   {final Variable  notZero = variable("notZero", 1);                           // Not zero if true
    final Variable  atMax   = variable("atMax",   1);                           // At the maximum value
    final Variable  value   = variable("value", max()) ;                        // The value of the unary number
    final Structure unary   = structure("unary", value, notZero, atMax);        // Structure representing a unary number
    return unary;
   }

  static Unary unary(String name, int max)                                      // Create a unary number of specified size
   {return new Unary(name)
     {int max() {return max;}
     };
   }

  abstract int max();                                                           // The maximum value of the unary number - override this method to set a non zero size

  void ok(int n) {ok(get(), n);}                                                // Check that a unary number has the expected value

  int get()                                                                     // Get the unary number
   {int n = 0;
    for (Bit b : value.output()) if (b.value) ++n;
    return n;
   }

//D1 Arithmetic                                                                 // Arithmetic using unary numbers

  boolean canInc() {return !value.output(). lastElement().value;}               // Unary has at space for at least one more element so we can increment
  boolean canDec() {return  value.output().firstElement().value;}               // Unary is at least one so we can decrement

  void inc()                                                                    // Increment the unary number
   {increment.set(true);
    decrement.set(false);
    simulate();
   }

  void dec()                                                                    // Decrement the unary number
   {increment.set(false);
    decrement.set(true);
    simulate();
   }

//D1 Print                                                                      // Print a unary number

  public String toString()
   {return "Unary(notZero:"+notZero.toInt()+                                    // Print a unary number
                 ", atMax:"+atMax.toInt()+
                 ", value:"+get()+
                   ", max:"+max()+")";
   }

//D0 Tests                                                                      // Test unary numbers

  static void test_unary()
   {Unary u = unary("Unary", 4);
              u.ok(0); ok( u.canInc()); ok(!u.canDec());
    u.inc();  u.ok(1); ok( u.canInc()); ok( u.canDec());
    u.inc();  u.ok(2); ok( u.canInc()); ok( u.canDec());
    u.inc();  u.ok(3); ok( u.canInc()); ok( u.canDec());
    u.inc();  u.ok(4); ok(!u.canInc()); ok( u.canDec());
   }

  static void test_sub_unary()
   {final int N = 4;
    Chip      c = new Chip("Unary")
     {Layout layout()
       {Variable  a = variable ("a", N);
        Unary     u = unary("unary", N);
        Structure s = structure("s", a, u.layout);
        addSubChip(u);
        return s;
       }
     };
    Unary u = (Unary)c.getSubChip("unary");
    u.inc(); u.inc();
    final Variable v = (Variable)c.layout.getField("s.unary.value");
say("AAAA", v.name, v.at, v.width);
say("BBBB", v.toInt());
    c.layout.getField("s.unary.value").ok(2);
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_unary();
    test_sub_unary();
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
