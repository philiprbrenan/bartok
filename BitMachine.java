//------------------------------------------------------------------------------
// Emulate assembler code sufficient to manipulate a BTree
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Simulate a silicon chip.

import java.util.*;

//D1 Construct                                                                  // Construct a silicon chip comprised of memory, intermediate bits and logic gates.

public class BitMachine extends Test                                            // A machine whose assembler code is just capable enough to manipulate a b-tree
 {final int maxSteps = 50;                                                      // Maximum number of steps to be executed
  Stack<Instruction> instructions = new Stack<>();                              // Instructions to be executed
  Layout layout;                                                                // Layout of bit memory being manipulated by this bit machine
  int instructionIndex;                                                         // The current instruction
  int step = 0;                                                                 // The number of the currently executing step

  void execute()
   {final int N = instructions.size();
    step = 0;
    for(instructionIndex = 0; instructionIndex < N; ++instructionIndex)
     {final Instruction i = instructions.elementAt(instructionIndex);
      i.action();
      trace();
      if (++step > maxSteps) stop("Terminating after", maxSteps, "steps");
     }
   }

  void trace() {}                                                               // Trace the execution

//D1 Instruction                                                                // Instructions recognized by the bit machine

  abstract class Instruction                                                    // An instruction to be executed
   {String name;                                                                // Name of the instruction
    String label;                                                               // Label of the instruction
    int    position;                                                            // Position of the instruction in the instruction stack

    Instruction()                                                               // Set name of instruction
     {name  = getClass().getName().split("\\$")[1];                             // Name of instruction from class name representing the instruction
      addInstruction();
     }

    abstract void action();                                                     // Action performed by the instruction

    void addInstruction()                                                       // Add the instruction to the instruction stack
     {position = instructions.size();                                           // Position of instruction in stack of instructions
      instructions.push(this);                                                  // Save instruction
     }
   }

  class Nop extends Instruction                                                 // No operation
   {void action() {}                                                            // Perform instruction
   }
  Nop nop() {return new Nop();}                                                 // No operation

  class Copy extends Instruction                                                // Copy data from the second field to the first field
   {Layout.Field source, target;                                                // Copy source to target
    Copy(Layout.Field Target, Layout.Field Source)                              // Copy source to target
     {Source.sameSize(Target);
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
     {if (Result.width != 1) stop("Result field must be one bit, but it is",
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
     {Result.isBit();
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
     {Result.isBit();
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
     {field = Field;
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
     {field = Field;
     }
    void action()                                                               // Perform instruction
     {for (int i = field.width-1; i > 0; i--) field.set(i, field.get(i-1));
      field.set(0, true);
     }
   }
  ShiftRightOneByZero shiftRLeftOneByOne(Layout.Field Field)                    // Shift right one fill with zero
   {return new ShiftRightOneByZero(Field);
   }

//D1 Branch instructions                                                        // Instructions that alter the flow of execution of the code

  abstract class Branch extends Instruction                                     // A branch instruction
   {final Layout.Bit bit;
    int target;

    Branch(Layout.Bit Bit) {bit = Bit;}                                         // Forward branch to a come from instruction
    Branch(Layout.Bit Bit, Instruction instruction)                             // Backward branch
     {this(Bit);
      target = instruction.position-1;                                          // Record index of instruction before target instruction
     }
   }

  class BranchIfZero extends Branch                                             // Branch if a bit is zero
   {BranchIfZero(Layout.Bit Bit) {super(Bit);}                                   // Forward branch to a come from instruction
    BranchIfZero(Layout.Bit Bit, Instruction Instruction)                       // Backward branch
     {super(Bit, Instruction);
     }
    void action() {if (!bit.get(0)) instructionIndex = target;}                 // Set instruction pointer to continue execution at the next instruction
   }
  BranchIfZero branchIfZero(Layout.Bit bit)                                     // Jump forward to a come from instruction
   {return new BranchIfZero(bit);
   }
  BranchIfZero branchIfZero(Layout.Bit bit, Instruction instruction)            // Jump back to an existing instruction
   {return new BranchIfZero(bit, instruction);
   }

  class BranchIfOne extends Branch                                              // Branch if a bit is one
   {BranchIfOne(Layout.Bit Bit) {super(Bit);}                                   // Forward branch to a come from instruction
    BranchIfOne(Layout.Bit Bit, Instruction Instruction)                        // Backward branch
     {super(Bit, Instruction);
     }
    void action() {if (bit.get(0)) instructionIndex = target;}                  // Set instruction pointer to continue execution at the next instruction
   }
  BranchIfOne branchIfOne(Layout.Bit bit)                                       // Jump forward to a come from instruction
   {return new BranchIfOne(bit);
   }
  BranchIfOne branchIfOne(Layout.Bit bit, Instruction instruction)              // Jump back to an existing instruction
   {return new BranchIfOne(bit, instruction);
   }

  class GoTo extends Branch                                                     // Goto the specified instruction
   {GoTo() {super(null);}                                                       // Forward goto
    GoTo(Instruction instruction) {super(null, instruction);}                   // Backward goto
    void action()   {instructionIndex = target;}                                // Set instruction pointer to continue execution at the next instruction
   }
  GoTo goTo()                        {return new GoTo();}                       // Jump forward to a comeFrom instruction
  GoTo goTo(Instruction instruction) {return new GoTo(instruction);}            // Jump back to an existing instruction

  class ComeFrom extends Instruction                                            // Set the target of the referenced goto instruction
   {ComeFrom(Branch source)                                                     // Forward goto to this instruction
     {source.target = position - 1;                                             // Set goto to jump to the instruction before the target instruction
     }
    void action() {}                                                            // Perform instruction
   }
  ComeFrom comeFrom(Branch source) {return new ComeFrom(source);}               // Set the source instruction to jump to this instruction


//D1 Structured Programming                                                     // Structured programming constructs

  abstract class If extends Instruction                                         // If condition then block else block
   {Layout.Field condition;                                                     // Condition deciding if
    If(Layout.Field Condition)                                                  // Right shift a field by one place fillng with a zero
     {condition = Condition;
      final GoTo else_inst = goTo();      Then();
      final GoTo end_inst = goTo();
      comeFrom(else_inst);
      Else();
      comeFrom(end_inst);
     }
    void action()                                                               // Perform instruction
     {if (condition.get(0)) instructionIndex++;
     }
    abstract void Then();                                                       // Then block is required
             void Else() {}                                                     // Else block is optional
   }

  abstract class For extends Instruction                                        // Iterate over an array
   {Layout.Array     array;                                                     // Array being iterated
    Layout           layout;
    Layout.Variable  counter;
    Layout.Variable  limit;
    Layout.Bit       atEnd;
    Layout.Structure struct;
    Instruction      start;
    Branch           finished;

    For(Layout.Array Array)                                                     // Iterate over an array
     {array   = Array;
      layout  = new Layout();
      counter = layout.variable ("counter", array.size);
      limit   = layout.variable ("limit",   array.size);
      atEnd   = layout.bit      ("atEnd");
      struct  = layout.structure("struct",  counter, limit, atEnd);
      layout.layout(struct);
      limit.ones();

      start = lessThan(atEnd, counter, limit);
      finished = branchIfZero(atEnd);

      setIndex(array, counter);
      block();

      shiftLeftOneByOne(counter);
      goTo(start);
      comeFrom(finished);
     }
    void action() {counter.zero();}                                             // Initialize for loop
    abstract void block();                                                      // Block of code to execute on each iteration
   }

  class SetIndex extends Instruction                                            // Set the index of an array from a field
   {final Layout.Array    array;                                                // Array to index
    final Layout.Variable index;                                                // Index
    SetIndex(Layout.Array Array, Layout.Variable Index)                         // Array, index value
     {array = Array;
      index = Index;
     }
    void action()                                                               // Set index for inducated array from the specified field
     {final int N = index.width;
      int ones = N;                                                             // Faulte de mieux
      for (int i = 0; i < N; ++i) if (!index.get(i)) {ones = i; break;}         // First zero
      array.setIndex(ones);                                                     // Set the array index
     }
   }
  SetIndex setIndex(Layout.Array Array, Layout.Variable Index)                  // Array, index value
   {return new SetIndex(Array, Index);
   }

//D1                                                                            // Print program

  String print()                                                                // Print the program
   {final StringBuilder s = new StringBuilder();                                //                                                                                //
    final int N = instructions.size();                                          // Number of instrictions
    for (int i = 0; i < N; i++)
     {final Instruction I = instructions.elementAt(i);
      s.append(String.format("%4d  %16s\n", i, I.name));
     }
    return s.toString();
   }

//D0                                                                            // Tests: I test, therefore I am.  And so do my mentees.  But most other people, apparently, do not, they live in a half world lit by shadows in which they never know if their code works or not.

  static void test_shift_left_oneByOne()
   {Layout          l = new Layout();
    Layout.Variable a = l.variable ("a", 4);
    l.layout(a);

    a.fromInt(0b1010);

    BitMachine m = new BitMachine();
    m.shiftLeftOneByOne(a);
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
T   At  Wide  Index       Value   Field name
S    0    21            1397025   s
V    0     4                  1     a
V    4     4                  2     b
V    8     4                  1     c
B   12     1                  1     aa
B   13     1                  0     ab
B   14     1                  1     ac
B   15     1                  0     ba
B   16     1                  1     bb
B   17     1                  0     bc
B   18     1                  1     ca
B   19     1                  0     cb
B   20     1                  1     cc
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
T   At  Wide  Index       Value   Field name
S    0    21             532769   s
V    0     4                  1     a
V    4     4                  2     b
V    8     4                  1     c
B   12     1                  0     aa
B   13     1                  1     ab
B   14     1                  0     ac
B   15     1                  0     ba
B   16     1                  0     bb
B   17     1                  0     bc
B   18     1                  0     ca
B   19     1                  1     cb
B   20     1                  0     cc
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
T   At  Wide  Index       Value   Field name
S    0    21            1929505   s
V    0     4                  1     a
V    4     4                  2     b
V    8     4                  1     c
B   12     1                  1     aa
B   13     1                  1     ab
B   14     1                  1     ac
B   15     1                  0     ba
B   16     1                  1     bb
B   17     1                  0     bc
B   18     1                  1     ca
B   19     1                  1     cb
B   20     1                  1     cc
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
    m.nop();
    m.copy(b, a);
    m.nop();
    m.copy(c, b);
    m.nop();
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    12               1911   s
V    0     4                  7     a
V    4     4                  7     b
V    8     4                  7     c
""");
   }

  static void test_if_then()
   {Layout           l = new Layout();
    Layout.Variable  s = l.variable ("s", 4);
    Layout.Variable  t = l.variable ("t", 4);
    Layout.Variable  e = l.variable ("e", 4);
    Layout.Variable  i = l.bit      ("i");
    Layout.Structure x = l.structure("x", i, s, t, e);
    l.layout(x);

    i.fromInt(1);
    s.fromInt(7);
    t.fromInt(0);
    e.fromInt(0);

    BitMachine m = new BitMachine();
    m.new If(i)
     {void Then() {m.copy(t, s);}
      void Else() {m.copy(e, s);}
     };
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    13                239   x
B    0     1                  1     i
V    1     4                  7     s
V    5     4                  7     t
V    9     4                  0     e
""");
   }

  static void test_if_else()
   {Layout           l = new Layout();
    Layout.Variable  s = l.variable ("s", 4);
    Layout.Variable  t = l.variable ("t", 4);
    Layout.Variable  e = l.variable ("e", 4);
    Layout.Variable  i = l.bit      ("i");
    Layout.Structure x = l.structure("x", i, s, t, e);
    l.layout(x);

    i.fromInt(0);
    s.fromInt(7);
    t.fromInt(0);
    e.fromInt(0);

    BitMachine m = new BitMachine();
    m.new If(i)
     {void Then() {m.copy(t, s);}
      void Else() {m.copy(e, s);}
     };
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    13               3598   x
B    0     1                  0     i
V    1     4                  7     s
V    5     4                  0     t
V    9     4                  7     e
""");
   }

  static void test_for()
   {final int N = 4;
    Layout           l = new Layout();
    Layout.Variable  a = l.variable ("a", 8);
    Layout.Variable  b = l.variable ("b", 8);
    Layout.Variable  c = l.variable ("c", 8);
    Layout.Structure s = l.structure("s", a, b, c);
    Layout.Array     A = l.array    ("A", s, N);
    l.layout(A);

    for (int i = 0; i < N; i++)
     {A.setIndex(i);
      a.fromInt(1*(i+1));
      b.fromInt(2*(i+2));
      c.fromInt(3*(i+3));
     }
    l.ok("""
T   At  Wide  Index       Value   Field name
A    0    96      0               A
S    0    24             590849     s
V    0     8                  1       a
V    8     8                  4       b
V   16     8                  9       c
A   24    96      1               A
S   24    24             787970     s
V   24     8                  2       a
V   32     8                  6       b
V   40     8                 12       c
A   48    96      2               A
S   48    24             985091     s
V   48     8                  3       a
V   56     8                  8       b
V   64     8                 15       c
A   72    96      3               A
S   72    24            1182212     s
V   72     8                  4       a
V   80     8                 10       b
V   88     8                 18       c
""");
    BitMachine m = new BitMachine();
    m.new For(A)
     {void block()
       {m.copy(b, a);
       };
     };

    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
A    0    96      0               A
S    0    24             590081     s
V    0     8                  1       a
V    8     8                  1       b
V   16     8                  9       c
A   24    96      1               A
S   24    24             786946     s
V   24     8                  2       a
V   32     8                  2       b
V   40     8                 12       c
A   48    96      2               A
S   48    24             983811     s
V   48     8                  3       a
V   56     8                  3       b
V   64     8                 15       c
A   72    96      3               A
S   72    24            1180676     s
V   72     8                  4       a
V   80     8                  4       b
V   88     8                 18       c
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_copy();
    test_shift_left_oneByOne();
    test_shift_right_oneByZero();
    test_equal();
    test_less_than();
    test_less_than_equal();
    test_if_then();
    test_if_else();
    test_for();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
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
