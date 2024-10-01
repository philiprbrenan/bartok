//------------------------------------------------------------------------------
// Emulate assembler code sufficient to manipulate a BTree
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Simulate a silicon chip.

import java.util.*;

//D1 Construct                                                                  // Construct a silicon chip comprised of memory, intermediate bits and logic gates.

public class BitMachine extends Test                                            // A machine whose assembler code is just capable enough to manipulate a b-tree
 {Stack<Instruction> instructions = new Stack<>();                              // Instructions to be executed
  Layout layout;                                                                // Layout of bit memory being manipulated by this bit machine

  void execute()
   {final int N = instructions.size();
    for(int p = 0; p < N; ++p) instructions.elementAt(p).action();
   }

//D1 Instruction                                                                // Instructions recognized by the bit machine

  abstract class Instruction                                                    // An instruction to be executed
   {String name;                                                                // Name of the instruction
    String label;                                                               // Label of the instruction
    int    position;                                                            // Position of the instruction in the instruction stack

    Instruction(String Name)                                                    // Set name of instruction
     {name  = Name;
      addInstruction();
     }

    abstract void action();                                                     // Action performed by the instruction

    void addInstruction()                                                       // Add the instruction to the instruction stack
     {position = instructions.size();                                           // Position of instruction in stack of instructions
      instructions.push(this);                                                  // Save instruction
     }
   }

  class Copy extends Instruction                                                // Copy data from the second field to the first field
   {Layout.Field source, target;                                                // Copy source to target
    Copy(Layout.Field Target, Layout.Field Source)                              // Copy source to target
     {super("Copy");
      Source.sameSize(Target);
      source = Source; target = Target;
     }
    void action()                                                               // Perform instruction
     {for (int i = source.width-1; i >= 0; i--) target.set(i, source.get(i));   // Copy each bit assuming no overlap
     }
   }
  Copy copy(Layout.Field target, Layout.Field source)                           // Copy bits from source to target
   {return new Copy(target, source);
   }

  class Equals extends Instruction                                              // Check that two fields are equal
   {Layout.Field f1, f2;                                                        // Fields to compare
    Layout.Field result;                                                        // Bit field showing result
    Equals(Layout.Field Result, Layout.Field F1, Layout.Field F2)               // Check two fields and set result
     {super("Equals");
      if (Result.width != 1) stop("Result field must be one bit, but it is",
        Result.width, "bits");
      F1.sameSize(F2);
      result = Result; f1 = F1; f2 = F2;
     }
    void action()                                                               // Perform instruction
     {for (int i = f1.width-1; i >= 0; i--)                                     // Check each bit
       {if (f1.get(i) != f2.get(i))                                             // Unequal bit
         {result.set(0, false);                                                 // Indicate that the fields are not equal becuase they differ in at least one bit
          return;                                                               // No need to check further
         }
       }
      result.set(0, true);                                                      // All bits are equal
     }
   }
  Equals equals(Layout.Field Result, Layout.Field F1, Layout.Field F2)          // Check two fields are equal
   {return new Equals(Result, F1, F2);
   }

  class LessThan extends Instruction                                            // Check that the first field is less than the second field
   {Layout.Field f1, f2;                                                        // Fields to compare
    Layout.Field result;                                                        // Bit field showing result
    LessThan(Layout.Field Result, Layout.Field F1, Layout.Field F2)             // Check two fields and set result
     {super("LessThan");
      Result.isBit();
      F1.sameSize(F2);
      result = Result; f1 = F1; f2 = F2;
     }
    void action()                                                               // Perform instruction
     {for (int i = f1.width-1; i >= 0; i--)                                     // Check each bit
       {if (!f1.get(i) && f2.get(i))                                            // 0 and 1 meaning the first is definitely less than the second
         {result.set(0, true);                                                  // Found two bits that show the first field is less than the second
          return;                                                               // No need to check further
         }
        if (f1.get(i) && !f2.get(i))                                            // 1 and 0 meaning the first is greater than not less than
         {result.set(0, false);                                                 // Found two bits that show the first field is less than the second
          return;                                                               // No need to check further
         }
       }
      result.set(0, false);                                                     // All bits are equal
     }
   }
  LessThan lessThan(Layout.Field Result, Layout.Field F1, Layout.Field F2)      // Check that the first field is less than the second field
   {return new LessThan(Result, F1, F2);
   }

  class LessThanOrEqual extends Instruction                                     // Check that the first field is less than or equal to the second field
   {Layout.Field f1, f2;                                                        // Fields to compare
    Layout.Field result;                                                        // Bit field showing result
    LessThanOrEqual(Layout.Field Result, Layout.Field F1, Layout.Field F2)      // Check two fields and set result
     {super("LessThanOrEqual");
      Result.isBit();
      F1.sameSize(F2);
      result = Result; f1 = F1; f2 = F2;
     }
    void action()                                                               // Perform instruction
     {for (int i = f1.width-1; i >= 0; i--)                                     // Check each bit
       {if (!f1.get(i) && f2.get(i))                                            // 0 and 1 meaning the first is definitely less than the second
         {result.set(0, true);                                                  // Found two bits that show the first field is less than the second
          return;                                                               // No need to check further
         }
        if (f1.get(i) && !f2.get(i))                                            // 1 and 0 meaning the first is greater than not less than
         {result.set(0, false);                                                 // Found two bits that show the first field is less than the second
          return;                                                               // No need to check further
         }
       }
      result.set(0, true);                                                      // All bits are equal so less than holds
     }
   }
  LessThanOrEqual lessThanOrEqual(Layout.Field Result,                          // Check that the first field is less than or equal to the second field
    Layout.Field F1, Layout.Field F2)
   {return new LessThanOrEqual(Result, F1, F2);
   }

  class ShiftLeftOneByOne extends Instruction                                   // Left shift one place and fill by one
   {Layout.Field field;                                                         // Field to shift
    ShiftLeftOneByOne(Layout.Field Field)                                       // Left shift a field by one place fillng with a one
     {super("ShiftLeftOneByOne");
      field = Field;
     }
    void action()                                                               // Perform instruction
     {for (int i = field.width-1; i > 0; i--) field.set(i, field.get(i-1));
      field.set(0, true);
     }
   }
  ShiftLeftOneByOne shiftLeftOneByOne(Layout.Field Field)                       // Left shift a field by one place fillng with a one
   {return new ShiftLeftOneByOne(Field);                                        // Left shift a field by one place fillng with a one
   }

  class ShiftRightOneByZero extends Instruction                                 // Shift right one fill with zero
   {Layout.Field field;                                                         // Field to shift
    ShiftRightOneByZero(Layout.Field Field)                                     // Right shift a field by one place fillng with a zero
     {super("ShiftRightOneByZero");
      field = Field;
     }
    void action()                                                               // Perform instruction
     {for (int i = field.width-1; i > 0; i--) field.set(i, field.get(i-1));
      field.set(0, true);
     }
   }
  ShiftRightOneByZero shiftRLeftOneByOne(Layout.Field Field)                    // Shift right one fill with zero
   {return new ShiftRightOneByZero(Field);
   }

//D0                                                                            // Tests: I test, therefore I am.  And so do my mentees.  But most other people, apparently, do not, they live in a half world lit by shadows in which they never know if their code works or not.

  static void test_shift_left_oneByOne()
   {Layout          l = new Layout();
    Layout.Variable a = l.variable ("a", 4);
    l.layout(a);

    a.fromInt(0b1010);

    BitMachine m = new BitMachine();
    m.new ShiftLeftOneByOne(a);
    m.execute();
    a.ok(0b0101);
   }

  static void test_shift_right_oneByZero()
   {Layout          l = new Layout();
    Layout.Variable a = l.variable ("a", 4);
    l.layout(a);

    a.fromInt(0b1010);

    BitMachine m = new BitMachine();
    m.new ShiftRightOneByZero(a);
    m.execute();
    a.ok(0b0101);
   }

  static void test_equal()
   {Layout           l = new Layout();
    Layout.Variable  a = l.variable ("a", 4);
    Layout.Variable  b = l.variable ("b", 4);
    Layout.Variable  c = l.variable ("c", 4);
    Layout.Bit      aa = l.bit      ("aa");
    Layout.Bit      ab = l.bit      ("ab");
    Layout.Bit      ac = l.bit      ("ac");
    Layout.Bit      ba = l.bit      ("ba");
    Layout.Bit      bb = l.bit      ("bb");
    Layout.Bit      bc = l.bit      ("bc");
    Layout.Bit      ca = l.bit      ("ca");
    Layout.Bit      cb = l.bit      ("cb");
    Layout.Bit      cc = l.bit      ("cc");
    Layout.Structure s = l.structure("s", a, b, c, aa, ab, ac, ba, bb, bc, ca, cb, cc);
    l.layout(s);

    a.fromInt(1);
    b.fromInt(2);
    c.fromInt(1);

    BitMachine m = new BitMachine();
    m.equals(aa, a, a);
    m.equals(ab, a, b);
    m.equals(ac, a, c);
    m.equals(ba, b, a);
    m.equals(bb, b, b);
    m.equals(bc, b, c);
    m.equals(ca, c, a);
    m.equals(cb, c, b);
    m.equals(cc, c, c);
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Size       Value   Field name
S    0    21           1397025   s
V    0     4                 1     a
V    4     4                 2     b
V    8     4                 1     c
B   12     1                 1     aa
B   13     1                 0     ab
B   14     1                 1     ac
B   15     1                 0     ba
B   16     1                 1     bb
B   17     1                 0     bc
B   18     1                 1     ca
B   19     1                 0     cb
B   20     1                 1     cc
""");
   }

  static void test_less_than()
   {Layout           l = new Layout();
    Layout.Variable  a = l.variable ("a", 4);
    Layout.Variable  b = l.variable ("b", 4);
    Layout.Variable  c = l.variable ("c", 4);
    Layout.Bit      aa = l.bit      ("aa");
    Layout.Bit      ab = l.bit      ("ab");
    Layout.Bit      ac = l.bit      ("ac");
    Layout.Bit      ba = l.bit      ("ba");
    Layout.Bit      bb = l.bit      ("bb");
    Layout.Bit      bc = l.bit      ("bc");
    Layout.Bit      ca = l.bit      ("ca");
    Layout.Bit      cb = l.bit      ("cb");
    Layout.Bit      cc = l.bit      ("cc");
    Layout.Structure s = l.structure("s", a, b, c, aa, ab, ac, ba, bb, bc, ca, cb, cc);
    l.layout(s);

    a.fromInt(1);
    b.fromInt(2);
    c.fromInt(1);

    BitMachine m = new BitMachine();
    m.lessThan(aa, a, a);
    m.lessThan(ab, a, b);
    m.lessThan(ac, a, c);
    m.lessThan(ba, b, a);
    m.lessThan(bb, b, b);
    m.lessThan(bc, b, c);
    m.lessThan(ca, c, a);
    m.lessThan(cb, c, b);
    m.lessThan(cc, c, c);
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Size       Value   Field name
S    0    21            532769   s
V    0     4                 1     a
V    4     4                 2     b
V    8     4                 1     c
B   12     1                 0     aa
B   13     1                 1     ab
B   14     1                 0     ac
B   15     1                 0     ba
B   16     1                 0     bb
B   17     1                 0     bc
B   18     1                 0     ca
B   19     1                 1     cb
B   20     1                 0     cc
""");
   }

  static void test_less_than_equal()
   {Layout           l = new Layout();
    Layout.Variable  a = l.variable ("a", 4);
    Layout.Variable  b = l.variable ("b", 4);
    Layout.Variable  c = l.variable ("c", 4);
    Layout.Bit      aa = l.bit      ("aa");
    Layout.Bit      ab = l.bit      ("ab");
    Layout.Bit      ac = l.bit      ("ac");
    Layout.Bit      ba = l.bit      ("ba");
    Layout.Bit      bb = l.bit      ("bb");
    Layout.Bit      bc = l.bit      ("bc");
    Layout.Bit      ca = l.bit      ("ca");
    Layout.Bit      cb = l.bit      ("cb");
    Layout.Bit      cc = l.bit      ("cc");
    Layout.Structure s = l.structure("s", a, b, c, aa, ab, ac, ba, bb, bc, ca, cb, cc);
    l.layout(s);

    a.fromInt(1);
    b.fromInt(2);
    c.fromInt(1);

    BitMachine m = new BitMachine();
    m.lessThanOrEqual(aa, a, a);
    m.lessThanOrEqual(ab, a, b);
    m.lessThanOrEqual(ac, a, c);
    m.lessThanOrEqual(ba, b, a);
    m.lessThanOrEqual(bb, b, b);
    m.lessThanOrEqual(bc, b, c);
    m.lessThanOrEqual(ca, c, a);
    m.lessThanOrEqual(cb, c, b);
    m.lessThanOrEqual(cc, c, c);
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Size       Value   Field name
S    0    21           1929505   s
V    0     4                 1     a
V    4     4                 2     b
V    8     4                 1     c
B   12     1                 1     aa
B   13     1                 1     ab
B   14     1                 1     ac
B   15     1                 0     ba
B   16     1                 1     bb
B   17     1                 0     bc
B   18     1                 1     ca
B   19     1                 1     cb
B   20     1                 1     cc
""");
   }

  static void test_copy()
   {Layout           l = new Layout();
    Layout.Variable  a = l.variable ("a", 4);
    Layout.Variable  b = l.variable ("b", 4);
    Layout.Variable  c = l.variable ("c", 4);
    Layout.Structure s = l.structure("s", a, b, c);
    l.layout(s);

    a.fromInt(7);
    b.fromInt(0);
    c.fromInt(0);

    BitMachine m = new BitMachine();
    m.copy(b, a);
    m.copy(c, b);
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Size       Value   Field name
S    0    12              1911   s
V    0     4                 7     a
V    4     4                 7     b
V    8     4                 7     c
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_copy();
    test_shift_left_oneByOne();
    test_shift_right_oneByZero();
    test_equal();
    test_less_than();
    test_less_than_equal();
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    test_copy();
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
    testExit(0);                                                                // Exit with a return code if we failed any tests to alert github
   }
 }
