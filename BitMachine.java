//------------------------------------------------------------------------------
// Implement just enough assembler code on a bit machine to manipulate a BTree.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Simulate a silicon chip.

import java.util.*;

//D1 Construct                                                                  // Construct a bit machine capable of manipulating a BTree

public class BitMachine extends Test implements LayoutAble                      // A machine whose assembler code is just capable enough to manipulate a b-tree
 {int maxSteps = 9999;                                                          // Maximum number of steps to be executed
  int maxRepeatSteps = 12;                                                      // Maximum number of times a repeat block can be iterated
  final String bitMachineName;                                                  // The name of the bit machine
  final int bitMachineNumber;                                                   // The number of the bit machine
  final StringBuilder           printer = new StringBuilder();                  // Place test output here for comparison with expected values
  final Stack<BitMachine>      machines = new Stack<>();                        // Machines that will generate instructions for this machine
  final Stack<Instruction> instructions = new Stack<>();                        // Instructions to be executed

  BitMachine       bitMachine = this;                                           // The bit machine in which to load instructions
  Layout               layout;                                                  // Layout of bit memory being manipulated by this bit machine
  int        instructionIndex = 0;                                              // The current instruction
  int                    step = 0;                                              // The number of the currently executing step
  static int BitMachineNumber = 0;                                              // Bit machine enumerator

  int       copySourceAddress = 0;                                              // Source of a long copy
  int       copyTargetAddress = 0;                                              // Target iof a long copy

  public Layout.Field asField () {return layout.top;}                           // Top most field of the layout associated with this bit machine
  public Layout       asLayout() {return layout;}                               // Layout associated with this bit machine

  void                setLayout(Layout Layout) {layout = Layout;}               // Set the layout associated with this bit machine

  BitMachine(String Name)                                                       // Assign a name and number to the bit machine to assist debugging
   {bitMachineName   = Name;                                                    // Name of bit machine
    bitMachineNumber = ++BitMachineNumber;                                      // Number of bit machine
   }

  BitMachine()            {this("BitMachine");}                                 // Default name for bit machine

  String bitMachineName() {return bitMachineName+"_"+bitMachineNumber;}         // Get the name of the bit machine that we are going to place generated code into

  void bitMachines(BitMachine...subMachines)                                    // Save machines this machine is dependent on
   {for (BitMachine b : subMachines) machines.push(b);
    bitMachine(this);                                                           // Mark this bit machine as the one that conatains all the others
   }

  void bitMachine(BitMachine machine)                                           // Have the sub machines put their instructions into this machine
   {for (BitMachine b : machines)
     {b.bitMachine = machine;                                                   // Target this machine
      b.bitMachine(machine);
     }
   }

  void setInstructionIndex(int index) {bitMachine.instructionIndex = index;}    // Set the instruction pointer in the top level bit machine
  void reset() {printer.setLength(0); instructions.clear(); step = 0;}          // Reset the machine
  void trace() {}                                                               // Trace the execution

  void codeOk(String expected) {Test.ok(printCode(), expected);}                // Check the code for this machine is as expected

  Layout.Variable getVariable(String name)                                      // Get a variable by name
   {final Layout.Field l = this.layout.get(name);
    if (l == null) stop("No such field as", name);
    return l.toVariable();
   }

  void setVariable(String name, int value)                                      // Set a variable by name from a specified integer
   {final Layout.Variable v = getVariable(name);                                // Address the variable
    copy(v, value);                                                             // Set the variable
   }

  void ok(String Lines)                                                         // Check that specified lines are present in the memory layout of the bit machine
   {final String  m = layout.toString();                                        // Memory as string
    final String[]L = Lines.split("\\n");                                       // Lines of expected
    int p = 0;
    for(int i = 0; i < L.length; ++i)                                           // Each expected
     {final String l = L[i];
      final int q = m.indexOf(l, p);                                            // Check specified lines are present
      if (q == -1)                                                              // Line missing
       {err("Layout does not contain line:", i+1, "\n"+l+"\n");
        ++Layout.testsFailed;
        return;
       }
      p = q;
     }
    ++Layout.testsPassed;                                                       // Lines found
   }

//D1 Instruction                                                                // Instructions recognized by the bit machine

  void execute()                                                                // Execute the instructions in this machine
   {final int N = instructions.size();
    step = 0;
    for(instructionIndex = 0; instructionIndex < N; ++instructionIndex)         // Instruction sequence
     {final Instruction i = instructions.elementAt(instructionIndex);
say("AAAA", debug);
      if (debug) say("Debug:", step+1, instructionIndex, i.position, i.name);
      i.action();
      trace();
      if (++step > maxSteps) stop("Terminating after", maxSteps, "steps");
     }
   }

  abstract class Instruction                                                    // An instruction to be executed
   {String name;                                                                // Name of the instruction
    String label;                                                               // Label of the instruction
    int    position;                                                            // Position of the instruction in the instruction stack
    final String traceBack;                                                     // The trace back when the instruction was created

    Instruction()                                                               // Set name of instruction
     {traceBack = Test.traceBack();                                             // Trace back at time instruction was executed
      name = getClass().getName().split("\\$")[1];                              // Name of instruction from class name representing the instruction as long as the class has a name
      addInstruction();
     }

    void action() {}                                                            // Action performed by the instruction. Composite  instructuins liek If or For use other instructions to implement their processing as this simplifies the instruction set

    void addInstruction()                                                       // Add the instruction to the instruction stack
     {position = bitMachine.instructions.size();                                // Position of instruction in stack of instructions
      bitMachine.instructions.push(this);                                       // Save instruction
     }
   }

  class Nop extends Instruction                                                 // No operation
   {void action() {}                                                            // Perform instruction
   }
  Nop nop() {return new Nop();}                                                 // No operation

  class Copy extends Instruction                                                // Copy data from the second field to the first field
   {final Layout.Field source, target;                                          // Copy source to target
    final int sOff, tOff, length;                                               // Offsets relative to source and target
    final int sourceInt;                                                        // Copy a constant integer
    Copy(Layout.Field Target, int Source)                                       // Copy source to target
     {source = null; sourceInt = Source; target = Target;
      sOff = 0; tOff = 0; length = 0;
     }
    Copy(Layout.Field Target, Layout.Field Source)                              // Copy source to target
     {Source.sameSize(Target);
      source = Source; target = Target;
      sOff = 0; tOff = 0; length = source.width; sourceInt = 0;
     }
    Copy(Layout.Field Target, int TOff,                                         // Copy some bits from source plus offset to target plus offset
         Layout.Field Source, int SOff, int Length)
     {source = Source; target = Target; sourceInt = 0;
      sOff = SOff; tOff = TOff; length = Length;
     }
    void action()                                                               // Perform instruction
     {if (source != null)                                                       // Copy from source field to target field
       {//if (debug) say("Copy:", source.asInt(), "to", target.name, "at", target.at);
        for(int i = length-1; i >= 0; i--)                                      // Copy each bit assuming no overlap
         {final Boolean b = source.get(sOff+i);
          target.set(tOff+i, b);
         }
       }
      else
       {//if (debug) say("Copy integer:", sourceInt, "to", target.name, "at", target.at, "width", target.width);
        target.fromInt(sourceInt);                                              // Copy from source integer
       }
     }
   }
  Copy copy(Layout.Field target, int source)                                    // Copy integer from source to target
   {return new Copy(target, source);
   }

  Copy copy(Layout.Field target, Layout.Field source)                           // Copy bits from source to target
   {return new Copy(target, source);
   }

  Copy copy(Layout.Field Target, int TOff,                                      // Copy some bits from source plus offset to target plus offset
            Layout.Field Source, int SOff, int Length)
   {return new Copy(Target, TOff, Source, SOff, Length);
   }

  class CopyLong extends Instruction                                            // Copy bits from the source address set by copySetSource to the target address set by copySetTarget
   {final int length;                                                           // Length of copy
    CopyLong(int Length)                                                        // Specify length of copy
     {length = Length;
     }
    void action()                                                               // Perform instruction
     {for(int i = 0; i < length; ++i)                                           // Copy each bit assuming no overlap
       {final int I  = i;
        final Boolean b = layout.memory.get(copySourceAddress+i);
        layout.memory.set(copyTargetAddress+i, b);
       }
     }
   }
  CopyLong copyLong(int Length)                                                 // Copy bits from source location to target location
   {return new CopyLong(Length);
   }

  class CopySetSource extends Instruction                                       // Set the source address for a long copy
   {final Layout.Field source;                                                  // Variable whose location is the source of the long copy
    CopySetSource(Layout.Field Source)                                          // Specify length of copy
     {source = Source;
     }
    void action()                                                               // Perform instruction
     {copySourceAddress = source.at;                                            // Source address
     }
   }
  CopySetSource copySetSource(Layout.Field source)                              // Set the source address for a long copy
   {return new CopySetSource(source);
   }

  class CopySetTarget extends Instruction                                       // Set the target address for a long copy
   {final Layout.Field target;                                                  // Variable whose location is the target of the long copy
    CopySetTarget(Layout.Field Target)                                          // Specify length of copy
     {target = Target;
     }
    void action()                                                               // Perform instruction
     {copyTargetAddress = target.at;                                            // Target address
     }
   }
  CopySetTarget copySetTarget(Layout.Field target)                              // Set the target address for a long copy
   {return new CopySetTarget(target);
   }

//D2 Arithmetic                                                                 // Integer arithmetic

  class Add extends Instruction                                                 // Add two equal sized fields containing positive integers in binary form to get a field of the same size by ignoring any overflow
   {final Layout.Field f1, f2;                                                  // Fields to add
    final Layout.Field result;                                                  // Result of addition
    Add(Layout.Field Result, Layout.Field F1, Layout.Field F2)                  // Check the two fields are the same size
     {if (Result.width != 1) stop("Result field must be one bit, but it is",
        Result.width, "bits");
      F1.sameSize(F2);
      result = Result; f1 = F1; f2 = F2;
     }
    void action()  {result.fromInt(f1.asInt() + f2.asInt());}                   // Perform instruction
   }
  Add add(Layout.Field Result, Layout.Field F1, Layout.Field F2)                // Add two positive integers ignoring overflow
   {return new Add(Result, F1, F2);
   }

  class Inc extends Instruction                                                 // Increment a field containing a positive integer in binary form ignoring any overflow
   {final Layout.Field field;                                                   // Field to increment
    Inc(Layout.Field Field) {field = Field;}                                    // Record field to increment
    void action()           {field.fromInt(field.asInt() + 1);}                 // Perform instruction
   }
  Inc inc(Layout.Field field) {return new Inc(field);}                          // Increment a field containing a positive integer ignoring the result

  class Dec extends Instruction                                                 // Increment a field containing a positive integer in binary form ignoring any overflow
   {final Layout.Field field;                                                   // Field to increment
    Dec(Layout.Field Field) {field = Field;}                                    // Record field to increment
    void action()           {field.fromInt(field.asInt() - 1);}                 // Perform instruction
   }
  Dec dec(Layout.Field field) {return new Dec(field);}                          // Decrement a field containing a positive integer ignoring the result

//D2 Boolean tests                                                              // Test the value of one field against another or a constant to get a boolean result
//D3 Equals                                                                     // Test whether one field is equal to another or to a constant

  class Equals extends Instruction                                              // Check that two fields are equal
   {final Layout.Field f1, f2;                                                  // Fields to compare
    final Layout.Field result;                                                  // Bit field showing result
    final int off1, off2, length;                                               // Offsets relative to first and second field, length of comparison
    final int f2Int;                                                            // Constant integer comparison

    boolean result() {return true;}                                             // Result to return on equals

    Equals(Layout.Bit Result, Layout.Field F1, int F2)                          // Compare with a constant integer
     {f1 = F1; f2 = null; f2Int = F2; result = Result;
      off1 = 0; off2 = 0; length = F1.width;
     }
    Equals(Layout.Bit Result, Layout.Field F1, Layout.Field F2)                 // Check two fields and set result
     {F1.sameSize(F2);
      result = Result; f1 = F1; f2 = F2;
      f2Int  = off1 = off2 = 0;
      length = F1.width;
     }
    Equals(Layout.Bit Result,                                                   // Check offsets within two fields for a specifed length
         Layout.Field F1, int Off1,
         Layout.Field F2, int Off2,
         int          Length)
     {f1 = F1; f2 = F2; f2Int = 0; result = Result; length = Length;
      off1 = Off1; off2 = Off2;
     }
    void action()                                                               // Perform instruction
     {if (f2 != null)
       {for (int i = length-1; i >= 0; i--)                                     // Check each bit
         {if (f1.get(off1+i) != f2.get(off2+i))                                 // Unequal bit
           {result.set(0, !result());                                           // Indicate that the fields are not equal becuase they differ in at least one bit
            return;                                                             // No need to check further
           }
         }
        result.set(0, result());                                                // All bits are equal
       }
      else                                                                      // Compare the field with the constant
       {final int a = f1.asInt();
        result.set(0, a == f2Int ? result() : !result());
       }
     }
   }
  Equals Equals(Layout.Bit Result, Layout.Field F1, int F2)                     // Check a field equals a constant
   {return new Equals(Result, F1, F2);
   }
  Equals Equals(Layout.Bit Result, Layout.Field F1, Layout.Field F2)            // Check two fields are equal
   {return new Equals(Result, F1, F2);
   }
  Equals Equals(Layout.Bit Result,                                              // Check two sets of bits are equal
    Layout.Field F1, int Off1,
    Layout.Field F2, int Off2,
    int          Length)
   {return new Equals(Result, F1, Off1,  F2, Off2, Length);
   }

  Layout.Bit Equals(Layout.Field F1, int F2)                                    // Return a variable which will hold the result of comparing a field to an integer for equals
   {final Layout.Bit result = Layout.createBit("equals");
    new Equals(result, F1, F2);
    return result;
   }

  Layout.Bit Equals(Layout.Field F1, Layout.Field F2)                           // Return a variable which will hold the result of comparing two fields for equals
   {final Layout.Bit result = Layout.createBit("equals");
    new Equals(result, F1, F2);
    return result;
   }

  Layout.Bit Equals                                                             // Return a variable which will hold the result of comparing two fields for equals
   (Layout.Field F1, int Off1,
    Layout.Field F2, int Off2,
    int          Length)
   {final Layout.Bit result = Layout.createBit("equals");
    new Equals(result, F1, Off1, F2, Off2, Length);
    return result;
   }

//D3 Not Equals                                                                 // Test whether one field is not equal to another or to a constant

  class NotEquals extends Equals                                                // Check that two fields are not equal
   {boolean result() {return false;}                                            // Result to return on equals

    NotEquals(Layout.Bit Result, Layout.Field F1, int F2)                       // Compare with a constant integer
     {super(Result, F1, F2);
     }
    NotEquals(Layout.Bit Result, Layout.Field F1, Layout.Field F2)              // Check two fields and set result
     {super(Result, F1, F2);
     }
    NotEquals(Layout.Bit Result,                                                // Check offsets within two fields for a specifed length
              Layout.Field F1, int Off1,
              Layout.Field F2, int Off2,
              int          Length)
     {super(Result, F1, Off1,  F2, Off2, Length);
     }
   }
  NotEquals notEquals(Layout.Bit Result, Layout.Field F1, int F2)               // Check a field notEquals a constant
   {return new NotEquals(Result, F1, F2);
   }
  NotEquals notEquals(Layout.Bit Result, Layout.Field F1, Layout.Field F2)      // Check two fields are equal
   {return new NotEquals(Result, F1, F2);
   }
  NotEquals notEquals(Layout.Bit Result,                                        // Check two sets of bits are equal
    Layout.Field F1, int Off1,
    Layout.Field F2, int Off2,
    int          Length)
   {return new NotEquals(Result, F1, Off1,  F2, Off2, Length);
   }

  Layout.Bit notEquals(Layout.Field F1, int F2)                                 // Return a variable which will hold the result of comparing a field to an integer for not equals
   {final Layout.Bit result = Layout.createBit("notEquals");
    new NotEquals(result, F1, F2);
    return result;
   }

  Layout.Bit notEquals(Layout.Field F1, Layout.Field F2)                        // Return a variable which will hold the result of comparing two fields for not equals
   {final Layout.Bit result = Layout.createBit("notEquals");
    new NotEquals(result, F1, F2);
    return result;
   }

  Layout.Bit notEquals                                                          // Return a variable which will hold the result of comparing two fields for not equals
   (Layout.Field F1, int Off1,
    Layout.Field F2, int Off2,
    int          Length)
   {final Layout.Bit result = Layout.createBit("notEquals");
    new NotEquals(result, F1, Off1, F2, Off2, Length);
    return result;
   }

//D3 Less Than                                                                  // Test whether one field is less than another or a constant

  class LessThan extends Instruction                                            // Check that the first field is less than the second field
   {boolean result() {return true;}                                             // Result to return on less than
    final Layout.Field f1, f2;                                                  // Fields to compare
    final Layout.Field result;                                                  // Bit field showing result
    final int off1, off2, length;                                               // Offsets relative to first and second field, length of comparison
    final int f2Int;                                                            // Constant integer comparison

    LessThan(Layout.Bit Result, Layout.Field F1, int F2)                        // Compare with a constant integer
     {f1 = F1; f2 = null; f2Int = F2; result = Result;
      off1 = 0; off2 = 0; length = F1.width;
     }
    LessThan(Layout.Bit Result, Layout.Field F1, Layout.Field F2)               // Check two fields and set result
     {Result.isBit();
      F1.sameSize(F2);
      result = Result; f1 = F1; f2 = F2; length = F1.width;
      off1 = off2 = f2Int = 0;
     }
    LessThan(Layout.Bit Result,                                                 // Check offsets within two fields for a specifed length
         Layout.Field F1, int Off1,
         Layout.Field F2, int Off2,
         int          Length)
     {f1 = F1; f2 = F2; f2Int = 0; result = Result; length = Length;
      off1 = Off1; off2 = Off2;
     }
    void action()                                                               // Perform instruction
     {if (f2 != null)                                                           // Check each bit
       {for (int i = length-1; i >= 0; i--)                                     // Check each bit
         {if (!f1.get(off1 + i) && f2.get(off2 + i))                            // 0 and 1 meaning the first is definitely less than the second
           {result.set(0, result());                                            // Found two bits that show the first field is less than the second
            return;                                                             // No need to check further
           }
          if (f1.get(off1 + i) && !f2.get(off2 + i))                            // 1 and 0 meaning the first is greater than not less than
           {result.set(0, !result());                                           // Found two bits that show the first field is less than the second
            return;                                                             // No need to check further
           }
         }
        result.set(0, !result());                                               // All bits are equal
       }
      else                                                                      // Compare the field with the constant
       {final int a = f1.asInt();
        result.set(0, a < f2Int ? result() : !result());
       }
     }
   }
  LessThan lessThan(Layout.Bit Result, Layout.Field F1, int F2)                 // Check that the first field is less than a constant integer
   {return new LessThan(Result, F1, F2);
   }
  LessThan lessThan(Layout.Bit Result, Layout.Field F1, Layout.Field F2)        // Check that the first field is less than the second field
   {return new LessThan(Result, F1, F2);
   }
  LessThan lessThan(Layout.Bit Result,                                          // Check that offsets from the first and second fields are less than for the specified length
    Layout.Field F1, int Off1,
    Layout.Field F2, int Off2,
    int          Length)
   {return new LessThan(Result, F1, Off1,  F2, Off2, Length);
   }

  Layout.Bit lessThan(Layout.Field F1, int F2)                                  // Return a variable which will hold the result of comparing a field to an integer for less than
   {final Layout.Bit result = Layout.createBit("LessThan");
    new LessThan(result, F1, F2);
    return result;
   }

  Layout.Bit lessThan(Layout.Field F1, Layout.Field F2)                         // Return a variable which will hold the result of comparing two fields for less than
   {final Layout.Bit result = Layout.createBit("LessThan");
    new LessThan(result, F1, F2);
    return result;
   }

  Layout.Bit lessThan                                                           // Return a variable which will hold the result of comparing two fields for less than
   (Layout.Field F1, int Off1,
    Layout.Field F2, int Off2,
    int          Length)
   {final Layout.Bit result = Layout.createBit("LessThan");
    new LessThan(result, F1, Off1, F2, Off2, Length);
    return result;
   }

//D3 Less Than or Equal                                                         // Test whether one field is less than or equal to another or to a constant

  class LessThanOrEqual extends Instruction                                     // Check that the first field is less than or equal to the second field
   {boolean result() {return true;}                                             // Result to return on less than
    final Layout.Field f1, f2;                                                  // Fields to compare
    final Layout.Field result;                                                  // Bit field showing result
    final int off1, off2, length;                                               // Offsets relative to first and second field, length of comparison
    final int f2Int;                                                            // Constant integer comparison
    LessThanOrEqual(Layout.Bit Result, Layout.Field F1, int F2)                 // Compare with a constant integer
     {f1 = F1; f2 = null; f2Int = F2; result = Result;
      off1 = 0; off2 = 0; length = F1.width;
     }
    LessThanOrEqual(Layout.Bit Result, Layout.Field F1, Layout.Field F2)        // Check two fields and set result
     {Result.isBit();
      F1.sameSize(F2);
      result = Result; f1 = F1; f2 = F2;
      f2Int = off1 = off2 = 0; length = F1.width;
     }
    LessThanOrEqual(Layout.Bit Result,                                          // Check offsets within two fields for a specifed length
         Layout.Field F1, int Off1,
         Layout.Field F2, int Off2,
         int          Length)
     {f1 = F1; f2 = F2; f2Int = 0; result = Result; length = Length;
      off1 = Off1; off2 = Off2;
     }
    void action()                                                               // Perform instruction
     {if (f2 != null)                                                           // Check each bit
       {for (int i = length-1; i >= 0; i--)                                     // Check each bit
         {if (!f1.get(off1+i) && f2.get(off2+i))                                // 0 and 1 meaning the first is definitely less than the second
           {result.set(0, result());                                            // Found two bits that show the first field is less than the second
            return;                                                             // No need to check further
           }
          if (f1.get(off1+i) && !f2.get(off2+i))                                // 1 and 0 meaning the first is greater than not less than
           {result.set(0, !result());                                           // Found two bits that show the first field is less than the second
            return;                                                             // No need to check further
           }
         }
        result.set(0, result());                                                // All bits are equal so less than holds
       }
      else                                                                      // Compare the field with the constant
       {final int a = f1.asInt();
        result.set(0, a <= f2Int ? result() : !result());
       }
     }
   }
  LessThanOrEqual lessThanOrEqual(Layout.Bit Result, Layout.Field F1, int F2)   // Check that the first field is less than or equal to a constant integer
   {return new LessThanOrEqual(Result, F1, F2);
   }
  LessThanOrEqual lessThanOrEqual                                               // Check that the first field is less than or equal to  the second field
   (Layout.Bit Result, Layout.Field F1, Layout.Field F2)
   {return new LessThanOrEqual(Result, F1, F2);
   }
  LessThanOrEqual lessThanOrEqual(Layout.Bit Result,                            // Check that offsets from the first and second fields are less than or equal to for the specified length
    Layout.Field F1, int Off1,
    Layout.Field F2, int Off2,
    int          Length)
   {return new LessThanOrEqual(Result, F1, Off1,  F2, Off2, Length);
   }

  Layout.Bit lessThanOrEqual(Layout.Field F1, int F2)                           // Return a variable which will hold the result of comparing a field to an integer for less than or equal
   {final Layout.Bit result = Layout.createBit("LessThanOrEqual");
    new LessThanOrEqual(result, F1, F2);
    return result;
   }

  Layout.Bit lessThanOrEqual(Layout.Field F1, Layout.Field F2)                  // Return a variable which will hold the result of comparing two fields for less than or equal
   {final Layout.Bit result = Layout.createBit("LessThanOrEqual");
    new LessThanOrEqual(result, F1, F2);
    return result;
   }

  Layout.Bit lessThanOrEqual                                                    // Return a variable which will hold the result of comparing two fields for less than or equal
   (Layout.Field F1, int Off1,
    Layout.Field F2, int Off2,
    int          Length)
   {final Layout.Bit result = Layout.createBit("LessThanOrEqual");
    new LessThanOrEqual(result, F1, Off1, F2, Off2, Length);
    return result;
   }

//D3 Greater than or equal                                                      // Test whether one field is greater than or equal to another or to a constant

  class GreaterThanOrEqual extends LessThan                                     // Check that the first field is greater than or equal the second field
   {boolean result() {return false;}                                            // Result to return on less than

    GreaterThanOrEqual(Layout.Bit Result, Layout.Field F1, int F2)              // Compare with a constant integer
     {super(Result, F1, F2);
     }
    GreaterThanOrEqual(Layout.Bit Result, Layout.Field F1, Layout.Field F2)     // Check two fields and set result
     {super(Result, F1, F2);
     }
    GreaterThanOrEqual(Layout.Bit Result,                                       // Check offsets within two fields for a specifed length
         Layout.Field F1, int Off1,
         Layout.Field F2, int Off2,
         int          Length)
     {super(Result, F1, Off1,  F2, Off2, Length);
     }
   }

  GreaterThanOrEqual greaterThanOrEqual(Layout.Bit Result, Layout.Field F1, int F2)                 // Check that the first field is greater than or equal a constant integer
   {return new GreaterThanOrEqual(Result, F1, F2);
   }
  GreaterThanOrEqual greaterThanOrEqual(Layout.Bit Result, Layout.Field F1, Layout.Field F2)        // Check that the first field is greater than or equal the second field
   {return new GreaterThanOrEqual(Result, F1, F2);
   }
  GreaterThanOrEqual greaterThanOrEqual(Layout.Bit Result,                                          // Check that offsets from the first and second fields are greater than or equal for the specified length
    Layout.Field F1, int Off1,
    Layout.Field F2, int Off2,
    int          Length)
   {return new GreaterThanOrEqual(Result, F1, Off1,  F2, Off2, Length);
   }

  Layout.Bit greaterThanOrEqual(Layout.Field F1, int F2)                        // Return a variable which will hold the result of comparing a field to an integer for greater than or equal
   {final Layout.Bit result = Layout.createBit("GreaterThanOrEqual");
    new GreaterThanOrEqual(result, F1, F2);
    return result;
   }

  Layout.Bit greaterThanOrEqual(Layout.Field F1, Layout.Field F2)               // Return a variable which will hold the result of comparing two fields for greater than or equal
   {final Layout.Bit result = Layout.createBit("GreaterThanOrEqual");
    new GreaterThanOrEqual(result, F1, F2);
    return result;
   }

  Layout.Bit greaterThanOrEqual                                                 // Return a variable which will hold the result of comparing two fields for greater than or equal
   (Layout.Field F1, int Off1,
    Layout.Field F2, int Off2,
    int          Length)
   {final Layout.Bit result = Layout.createBit("GreaterThanOrEqual");
    new GreaterThanOrEqual(result, F1, Off1, F2, Off2, Length);
    return result;
   }

//D3 Greater than                                                               // Test whether one field is greater than another or to a constant

  class GreaterThan extends LessThanOrEqual                                     // Check that the first field is greater than a second field
   {boolean result() {return false;}                                            // Result to return on less than

    GreaterThan(Layout.Bit Result, Layout.Field F1, int F2)                     // Compare with a constant integer
     {super(Result, F1, F2);
     }
    GreaterThan(Layout.Bit Result, Layout.Field F1, Layout.Field F2)            // Check two fields and set result
     {super(Result, F1, F2);
     }
    GreaterThan(Layout.Bit Result,                                              // Check offsets within two fields for a specifed length
         Layout.Field F1, int Off1,
         Layout.Field F2, int Off2,
         int          Length)
     {super(Result, F1, Off1,  F2, Off2, Length);
     }
   }
  GreaterThan greaterThan(Layout.Bit Result, Layout.Field F1, int F2)           // Check that the first field is greater than to a constant integer
   {return new GreaterThan(Result, F1, F2);
   }
  GreaterThan greaterThan                                                       // Check that the first field is greater than to  the second field
   (Layout.Bit Result, Layout.Field F1, Layout.Field F2)
   {return new GreaterThan(Result, F1, F2);
   }
  GreaterThan greaterThan(Layout.Bit Result,                                    // Check that offsets from the first and second fields are greater than to for the specified length
    Layout.Field F1, int Off1,
    Layout.Field F2, int Off2,
    int          Length)
   {return new GreaterThan(Result, F1, Off1,  F2, Off2, Length);
   }

  Layout.Bit greaterThan(Layout.Field F1, int F2)                               // Return a variable which will hold the result of comparing a field to an integer for greater than
   {final Layout.Bit result = Layout.createBit("GreaterThan");
    new GreaterThan(result, F1, F2);
    return result;
   }

  Layout.Bit greaterThan(Layout.Field F1, Layout.Field F2)                      // Return a variable which will hold the result of comparing two fields for greater than
   {final Layout.Bit result = Layout.createBit("GreaterThan");
    new GreaterThan(result, F1, F2);
    return result;
   }

  Layout.Bit greaterThan                                                        // Return a variable which will hold the result of comparing two fields for greater than
   (Layout.Field F1, int Off1,
    Layout.Field F2, int Off2,
    int          Length)
   {final Layout.Bit result = Layout.createBit("GreaterThan");
    new GreaterThan(result, F1, Off1, F2, Off2, Length);
    return result;
   }

//D2 Shift                                                                      // Shift left or right filling with zeros, ones or sign bit

  class ShiftLeftOneByOne extends Instruction                                   // Left shift one place and fill by one
   {Layout.Field field;                                                         // Field to shift
    ShiftLeftOneByOne(Layout.Field Field)                                       // Left shift a field by one place fillng with a one
     {field = Field;
     }
    void action()                                                               // Perform instruction
     {for (int i = field.width-1; i > 0; i--)
       {field.set(i, field.get(i-1));
       }
      field.set(0, true);
     }
   }
  ShiftLeftOneByOne shiftLeftOneByOne(Layout.Field Field)                       // Left shift a field by one place fillng with a one
   {return new ShiftLeftOneByOne(Field);                                        // Left shift a field by one place fillng with a one
   }

  class ShiftRightOneByZero extends Instruction                                 // Shift right one fill with zero
   {final Layout.Field field;                                                   // Field to shift
    ShiftRightOneByZero(Layout.Field Field)                                     // Right shift a field by one place fillng with a zero
     {field = Field;
     }
    void action()                                                               // Perform instruction
     {final int N  = field.width;
      for (int i = 1; i < N; ++i) field.set(i-1, field.get(i));
      field.set(N-1, false);
     }
   }
  ShiftRightOneByZero shiftRightOneByZero(Layout.Field Field)                   // Shift right one fill with zero
   {return new ShiftRightOneByZero(Field);
   }

//D1 Arithmetic                                                                 // Arithemetic instructions

  class Zero extends Instruction                                                // Clear a field to zero
   {final Layout.Field field;                                                   // Field to clear
    Zero(Layout.Field Field) {field = Field;}                                   // Set field to clear
    void action() {field.zero();}                                               // Clear field
   }
  Zero zero(Layout.Field Field) {return new Zero(Field);}                       // Clear a field

  class Ones extends Instruction                                                // Set a field to ones
   {final Layout.Field field;                                                   // Field to set
    Ones(Layout.Field Field) {field = Field;}                                   // Set field to set
    void action() {field.ones();}                                               // Set field
   }
  Ones ones(Layout.Field Field) {return new Ones(Field);}                       // Set a field to ones

  class Not extends Instruction                                                 // Invert a field
   {final Layout.Field field;                                                   // Field to invert
    Not(Layout.Field Field) {field = Field;}                                    // Set field to invert
    void action()                                                               // Invert fields
     {for (int i = 0; i < field.width; i++) field.set(i, !field.get(i));        // Invert the field bit by bit
     }
   }
  Not not(Layout.Field Field) {return new Not(Field);}                          // Invert a field

  class UnaryFilled extends Instruction                                         // Check that two unary fields fill the maximum value allowed
   {final int N;                                                                // Width of fields to test
    final Layout.Field f1, f2;                                                  // Unary fields
    final Layout.Bit   r;                                                       // Result
    UnaryFilled(Layout.Field F1, Layout.Field F2, Layout.Bit R)                 // Check that two unary fields fill the maximum value allowed
     {F1.sameSize(F2);
      f1 = F1; f2 = F2; r = R;
      N = f1.width;
     }
    void action()                                                               // Invert fields
     {int n = 0;
      for (int i = 0; i < N; i++) if (f1.get(i)) ++n;                           // Count bits in the first field
      for (int i = 0; i < N; i++) if (f2.get(i)) ++n;                           // Count bits in the second field
      r.set(n == N);                                                            // Unary is all ones
     }
   }
  UnaryFilled unaryFilled(Layout.Field F1, Layout.Field F2, Layout.Bit R)       // Check that two unary fields fill the maximum value allowed
   {return new UnaryFilled(F1, F2, R);
   }

  class UnaryFilledMinusOne extends Instruction                                 // Check that two unary fields fill the maximum value allowed minus one
   {final int N;                                                                // Width of fields to test
    final Layout.Field f1, f2;                                                  // Unary fields, intermediate result
    final Layout.Bit   r;                                                       // Result
    UnaryFilledMinusOne(Layout.Field F1, Layout.Field F2, Layout.Bit R)         // Check that two unary fields fill the maximum value allowed
     {F1.sameSize(F2);
      f1 = F1; f2 = F2; r = R;
      N = f1.width;
     }
    void action()                                                               // Invert fields
     {int n = 0;
      for (int i = 0; i < N; i++) if (f1.get(i)) ++n;                           // Count bits in the first field
      for (int i = 0; i < N; i++) if (f2.get(i)) ++n;                           // Count bits in the second field
      r.set(n == N-1);                                                          // Unary has just one zero
     }
   }
  UnaryFilledMinusOne unaryFilledMinusOne                                       // Check that two unary fields fill the maximum value allowed
   (Layout.Field F1, Layout.Field F2, Layout.Bit R)
   {return new UnaryFilledMinusOne(F1, F2, R);
   }

  class ConvertUnaryToBinary extends Instruction                                // Convert a unary value to binary
   {final Layout.Variable source, target;                                       // Unary source, binary target
    ConvertUnaryToBinary(Layout.Variable Target, Layout.Variable Source)        // Convert a unary value to binary
     {source = Source; target = Target;
     }
    void action()                                                               // Invert fields
     {int n = 0;
      for (int i = 0; i < source.width; i++) if (source.get(i)) ++n;            // Count bits in unary
      target.fromInt(n);
     }
   }
  ConvertUnaryToBinary convertUnaryToBinary                                     // Check that two unary fields fill the maximum value allowed
   (Layout.Variable target, Layout.Variable source)
   {return new ConvertUnaryToBinary(target, source);
   }

  class ConvertBinaryToUnary extends Instruction                                // Convert a binary value to unary
   {final Layout.Variable source, target;                                       // Unary source, binary target
    ConvertBinaryToUnary(Layout.Variable Target, Layout.Variable Source)        // Convert a unary value to binary
     {source = Source; target = Target;
     }
    void action()                                                               // Invert fields
     {final int N = source.asInt();
      for (int i = 0; i < target.width; i++) target.set(i, i < N);              // Count bits in unary
     }
   }
  ConvertBinaryToUnary convertBinaryToUnary                                     // Convert binary to unary
   (Layout.Variable target, Layout.Variable source)
   {return new ConvertBinaryToUnary(target, source);
   }

//D1 Branch instructions                                                        // Instructions that alter the flow of execution of the code

//D2 Constant comparison                                                        // Compare the value of a field  with a constant value to determine whether to branch or not

  abstract class Branch extends Instruction                                     // A branch instruction. These instructions are primarily to support 'if' statements. Otherwise instructions based on  'block' are preffereable.
   {final Layout.Field bit;                                                     // Result of comparison
    int target;                                                                 // Target of branch

    Branch(Layout.Field Bit) {bit = Bit;}                                       // Forward branch to a come from instruction
    Branch(Layout.Field Bit, Instruction instruction)                           // Backward branch
     {this(Bit);
      target = instruction.position-1;                                          // Record index of instruction before target instruction
     }
   }

  class BranchIfZero extends Branch                                             // Branch if a bit is zero
   {BranchIfZero(Layout.Bit Bit) {super(Bit);}                                  // Forward branch to a come from instruction
    BranchIfZero(Layout.Bit Bit, Instruction Instruction)                       // Backward branch
     {super(Bit, Instruction);
     }
    void action() {if (!bit.get(0)) setInstructionIndex(target);}               // Set instruction pointer to continue execution at the next instruction
   }
  BranchIfZero branchIfZero(Layout.Bit bit)                                     // Jump forward to a come from instruction
   {return new BranchIfZero(bit);
   }
  BranchIfZero branchIfZero(Layout.Bit bit, Instruction instruction)            // Jump back to an existing instruction
   {return new BranchIfZero(bit, instruction);
   }

//  class BranchIfAllZero extends Branch                                        // Branch if all the bits in a field are zero
//   {BranchIfAllZero(Layout.Field Field) {super(Field);}                       // Forward branch to a come from instruction
//    BranchIfAllZero(Layout.Field Field, Instruction Instruction)              // Backward branch
//     {super(Field, Instruction);
//     }
//    void action()                                                             // Set instruction pointer to continue execution at the next instruction
//     {for (int i = 0; i <  bit.width; i++) if (bit.get(i)) return;            // Non zero bit
//      setInstructionIndex(target);                                            // Set instruction pointer to continue execution at the next instruction becuase all biots are zero
//     }
//   }
//  BranchIfAllZero branchIfAllZero(Layout.Field field)                         // Jump forward to a come from instruction
//   {return new BranchIfAllZero(field);
//   }
//  BranchIfAllZero branchIfAllZero(Layout.Field field, Instruction instruction)  // Jump back to an existing instruction
//   {return new BranchIfAllZero(field, instruction);
//   }
//
//  class BranchIfNotAllZero extends Branch                                     // Branch if not all the bits in a field are zero
//   {BranchIfNotAllZero(Layout.Field Field) {super(Field);}                    // Forward branch to a come from instruction
//    BranchIfNotAllZero(Layout.Field Field, Instruction Instruction)           // Backward branch
//     {super(Field, Instruction);
//     }
//    void action()                                                             // Set instruction pointer to continue execution at the next instruction
//     {for (int i = 0; i <  bit.width; i++)                                    // Examine each bit
//       {if (bit.get(i))                                                       // Found a bit that is not zero so branch
//         {setInstructionIndex(target);                                        // Set instruction pointer to continue execution at the next instruction becuase all biots are zero
//          return;
//         }
//       }
//     }
//   }
//  BranchIfNotAllZero branchIfNotAllZero(Layout.Field field)                   // Jump forward to a come from instruction
//   {return new BranchIfNotAllZero(field);
//   }
//  BranchIfNotAllZero branchIfNotAllZero                                       // Jump back to an existing instruction
//   (Layout.Field field, Instruction instruction)
//   {return new BranchIfNotAllZero(field, instruction);
//   }

  class BranchIfOne extends Branch                                              // Branch if a bit is one
   {BranchIfOne(Layout.Bit Bit) {super(Bit);}                                   // Forward branch to a come from instruction
    BranchIfOne(Layout.Bit Bit, Instruction Instruction)                        // Backward branch
     {super(Bit, Instruction);
     }
    void action() {if (bit.get(0)) setInstructionIndex(target);}                // Set instruction pointer to continue execution at the next instruction
   }
  BranchIfOne branchIfOne(Layout.Bit bit)                                       // Jump forward to a come from instruction
   {return new BranchIfOne(bit);
   }
  BranchIfOne branchIfOne(Layout.Bit bit, Instruction instruction)              // Jump back to an existing instruction
   {return new BranchIfOne(bit, instruction);
   }

//  class BranchIfAllOnes extends Branch                                        // Branch if all the bits in a field are one
//   {BranchIfAllOnes(Layout.Field Field) {super(Field);}                       // Forward branch to a come from instruction
//    BranchIfAllOnes(Layout.Field Field, Instruction Instruction)              // Backward branch
//     {super(Field, Instruction);
//     }
//    void action()                                                             // Set instruction pointer to continue execution at the next instruction
//     {for (int i = 0; i <  bit.width; i++) if (!bit.get(i)) return;           // Zero bit
//      setInstructionIndex(target);                                            // Set instruction pointer to continue execution at the next instruction becuase all biots are zero
//     }
//   }
//  BranchIfAllOnes branchIfAllOnes(Layout.Field field)                         // Jump forward to a come from instruction
//   {return new BranchIfAllOnes(field);
//   }
//  BranchIfAllOnes branchIfAllOnes(Layout.Field field, Instruction instruction)  // Jump back to an existing instruction
//   {return new BranchIfAllOnes(field, instruction);
//   }
//
//  class BranchIfNotAllOnes extends Branch                                     // Branch if not all the bits in a field are one
//   {BranchIfNotAllOnes(Layout.Field Field) {super(Field);}                    // Forward branch to a come from instruction
//    BranchIfNotAllOnes(Layout.Field Field, Instruction Instruction)           // Backward branch
//     {super(Field, Instruction);
//     }
//    void action()                                                             // Set instruction pointer to continue execution at the next instruction
//     {for (int i = 0; i <  bit.width; i++)                                    // Examine each bit
//       {if (!bit.get(i))                                                      // Zero bit
//         {setInstructionIndex(target);                                        // Set instruction pointer to continue execution at the next instruction becuase all biots are zero
//          return;
//         }
//       }
//     }
//   }
//  BranchIfNotAllOnes branchIfNotAllOnes(Layout.Field field)                   // Jump forward to a come from instruction
//   {return new BranchIfNotAllOnes(field);
//   }
//  BranchIfNotAllOnes branchIfNotAllOnes                                       // Jump back to an existing instruction
//   (Layout.Field field, Instruction instruction)
//   {return new BranchIfNotAllOnes(field, instruction);
//   }

//D2 Variable comparison                                                        // Branch based on the comparison of the values of two fields

  abstract class BranchOnCompare extends Instruction                            // A branch instruction based on the comparison of two fields
   {final Layout.Field first, second;                                           // The two fields to comapare
    int target;

    BranchOnCompare(Layout.Field First, Layout.Field Second)                    // Forward branch to a come from instruction
     {First.sameSize(Second);
       first = First; second = Second;
     }
    BranchOnCompare(Layout.Field First, Layout.Field Second,                    // Backward branch
                    Instruction instruction)
     {this(First, Second);
      target = instruction.position-1;                                          // Record index of instruction before target instruction
     }
   }

  class BranchIfEqual extends BranchOnCompare                                   // Branch if two fields are equal
   {BranchIfEqual(Layout.Field First, Layout.Field Second)                      // Forward branch to a come from instruction
     {super(First, Second);
     }
    BranchIfEqual                                                               // Backward branch
     (Layout.Field First, Layout.Field Second, Instruction Instruction)
     {super(First, Second, Instruction);
     }
    void action()                                                               // Set instruction pointer to continue execution at the next instruction
     {for (int i = 0; i < first.width; i++)                                     // Check each bit  in the same  sized fields
       {if (first.get(i) != second.get(i)) return;                              // Bits differ at this position so no branch required
       }
      setInstructionIndex(target);                                              // All bits equal, update the instruction pointer to effect the branch
     }
   }
  BranchIfEqual branchIfEqual(Layout.Field first, Layout.Field second)          // Jump forward to a come from instruction
   {return new BranchIfEqual(first, second);
   }
  BranchIfEqual branchIfEqual                                                   // Jump back to an existing instruction
   (Layout.Field first, Layout.Field second, Instruction instruction)
   {return new BranchIfEqual(first, second, instruction);
   }

  class BranchIfNotEqual extends BranchOnCompare                                // Branch if two fields are equal
   {BranchIfNotEqual(Layout.Field First, Layout.Field Second)                   // Forward branch to a come from instruction
     {super(First, Second);
     }
    BranchIfNotEqual                                                            // Backward branch
     (Layout.Field First, Layout.Field Second, Instruction Instruction)
     {super(First, Second, Instruction);
     }
    void action()                                                               // Set instruction pointer to continue execution at the next instruction
     {for (int i = 0; i < first.width; i++)                                     // Check each bit  in the same  sized fields
       {if (first.get(i) != second.get(i))                                      // Bits differ at this position so branch is required
         {setInstructionIndex(target);                                          // At least one bit differs so update the instruction pointer to effect the branch
          return;
         }
       }
     }
   }
  BranchIfNotEqual branchIfNotEqual(Layout.Field first, Layout.Field second)    // Jump forward to a come from instruction
   {return new BranchIfNotEqual(first, second);
   }
  BranchIfNotEqual branchIfNotEqual                                             // Jump back to an existing instruction
   (Layout.Field first, Layout.Field second, Instruction instruction)
   {return new BranchIfNotEqual(first, second, instruction);
   }

//D2 Unconditional                                                              // Unconditiona branch

  class GoTo extends Branch                                                     // Goto the specified instruction
   {GoTo() {super(null);}                                                       // Forward goto
    GoTo(Instruction instruction) {super(null, instruction);}                   // Backward goto
    void action()   {setInstructionIndex(target);}                              // Set instruction pointer to continue execution at the next instruction
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

  class ComeFromComparison extends Instruction                                  // Set the target of branch on comparison instruction
   {ComeFromComparison(BranchOnCompare source)                                  // Forward goto to this instruction
     {source.target = position - 1;                                             // Set goto to jump to the instruction before the target instruction
     }
    void action() {}                                                            // Perform instruction
   }
  ComeFromComparison comeFromComparison(BranchOnCompare source)                 // Set the source instruction to jump to this instruction
   {return new ComeFromComparison(source);
   }


//D1 Structured Programming                                                     // Structured programming constructs

  abstract class If extends Instruction                                         // If condition then block
   {Layout.Bit condition;                                                       // Condition deciding if
    If(Layout.Bit Condition)                                                    // Right shift a field by one place fillng with a zero
     {name = "If";
      condition = Condition;
      final Branch test = branchIfZero(condition);
      Then();
      comeFrom(test);
     }
    abstract void Then();                                                       // Then block is required
   }

  abstract class IfElse extends Instruction                                     // If condition then block else block
   {Layout.Bit condition;                                                       // Deciding condition
    IfElse(Layout.Bit Condition)                                                // Right shift a field by one place fillng with a zero
     {name = "IfElse";
      condition = Condition;
      final Branch test = branchIfZero(condition);
      Then();
      final Branch endThen = goTo();
      comeFrom(test);
      Else();
      comeFrom(endThen);
     }
    abstract void Then();                                                       // Then block is required
    abstract void Else();                                                       // Else block is required
   }

  abstract class Unless extends Instruction                                     // If not condition then block
   {Layout.Bit condition;                                                       // Deciding condition
    Unless(Layout.Bit Condition)                                                // Right shift a field by one place fillng with a zero
     {name = "unless";
      condition = Condition;
      final Branch test = branchIfOne(condition);                               // Jump over then if the condition is true
      Then();
      comeFrom(test);
     }
    abstract void Then();                                                       // Then block is required
   }

  abstract class DownTo extends Instruction                                     // Iterate a unary value from its maximum value  to a specified value
   {Layout.Field counter;                                                       // The variable we are going to decrement
    Layout.Field limit;                                                         // The variable we are going to increment

    DownTo(Layout.Field Counter, Layout.Field Limit)                            // Iterate down from maximum value down to limit
     {name    = "DownTo";
      counter = Counter;                                                        // Field to be used as a counter
      limit   = Limit;                                                          // Field to be used as limit
      ones(counter);                                                            // Start the counter at the maximum
      final BranchOnCompare test = branchIfEqual(counter, limit);               // Exit the loop if we are at the limit
      block();                                                                  // Block of code supplied by caller
      shiftRightOneByZero(counter);                                             // Next counter value
      goTo(test);                                                               // Restart loop
      comeFromComparison(test);                                                 // Exit the loop
     }
    void action() {}
    abstract void block();                                                      // Block of code to execute on each iteration
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
     {name    = "For";
      array   = Array;
      layout  = new Layout();
      counter = layout.variable ("counter", array.size);
      limit   = layout.variable ("limit",   array.size);
      atEnd   = layout.bit      ("atEnd");
      layout.layout("struct", counter, limit, atEnd);
      limit.ones();

      start = lessThan(atEnd, counter, limit);
      finished = branchIfZero(atEnd);

      setIndexFromUnary(array, counter);
      block();

      shiftLeftOneByOne(counter);
      goTo(start);
      comeFrom(finished);
     }
    void action() {counter.zero();}                                             // Initialize for loop
    abstract void block();                                                      // Block of code to execute on each iteration
   }

  abstract class Repeat extends Block                                           // Repeat a block of code until a return is requested
   {int repeats;                                                                // Number of repeats
    Repeat()                                                                    // Iterate over an array
     {super(true);
      name = "Repeat";
      code();
      //goTo(this);
      new Continue(this);
      end = nop();                                                              // End of block
     }
    void action() {repeats = 0;}                                                // Reset repetition count every time we start the repeat block
    class Continue extends GoTo                                                 // Continue a block by going to its start no more than a specified number of times
     {Continue(Instruction instruction) {super(instruction);}                   // Backward goto

     void action()                                                              // Restart the repeat block unless it has been executed too many times
       {++repeats;                                                              // Count the number of repeats
        if (repeats > maxRepeatSteps)                                           // Stop if too many repeats
         {stop("Repeat has executed", maxRepeatSteps, "times\n"+traceBack);
         }
        setInstructionIndex(target+1);                                          // First caller instruction of block.  The repeat instriuction slot itself is used to initialize the repetition counter every time we start a set of repetitions.
       }
     }
   }

  class SetIndex extends Instruction                                            // Set the index of an array from a field interpreted as a binary integer
   {final Layout.Array    array;                                                // Array to index
    final Layout.Variable index;                                                // Index
    SetIndex(Layout.Array Array, Layout.Variable Index)                         // Array, index value
     {array = Array;
      index = Index;
     }
    void action()                                                               // Set index for the indicated array from the specified field interpreting it as a unary number
     {array.setIndex(index.asInt());                                            // Set the array index
     }
   }
  SetIndex setIndex(Layout.Array Array, Layout.Variable Index)                  // Array, index value
   {return new SetIndex(Array, Index);
   }

  class SetIndexFromInt extends Instruction                                     // Set the index of an array from a constant integer
   {final Layout.Array array;                                                   // Array to index
    final int          index;                                                   // Index
    SetIndexFromInt(Layout.Array Array, int Index)                              // Array, index value
     {array = Array;
      index = Index;
     }
    void action() {array.setIndex(index);}                                      // Set index for the indicated array from the specified integer
   }
  SetIndexFromInt setIndexFromInt(Layout.Array Array, int Index)                // Array, index value
   {return new SetIndexFromInt(Array, Index);
   }

  class SetIndexFromUnary extends Instruction                                   // Set the index of an array from a field interpreted as a unary number
   {final Layout.Array    array;                                                // Array to index
    final Layout.Variable index;                                                // Index
    SetIndexFromUnary(Layout.Array Array, Layout.Variable Index)                // Array, index value
     {array = Array;
      index = Index;
     }
    void action()                                                               // Set index for the indicated array from the specified field interpreting it as a unary number
     {final int N = index.width;
      int ones = N;                                                             // Faulte de mieux
      for (int i = 0; i < N; ++i) if (!index.get(i)) {ones = i; break;}         // First zero
      array.setIndex(ones);                                                     // Set the array index
     }
   }
  SetIndexFromUnary setIndexFromUnary(Layout.Array Array, Layout.Variable Index)// Array, index value
   {return new SetIndexFromUnary(Array, Index);
   }

// D2 Block                                                                     // A block of code can be easily exited on a condition making it behave rather like a subroutine with a return

  abstract class Block extends Instruction                                      // A block of code acts like an instruction
   {Instruction end;                                                            // The final instruction at the end of the block that we exit to

    Block(boolean doNothing) {}                                                 // Define the block without generating any code

    Block()                                                                     // Define the block
     {code();
      end = nop();                                                              // End of block
     }

    abstract void code();                                                       // The code of the block

    class ReturnRegardless extends Branch                                       // Return regardless
     {ReturnRegardless() {super(null); name = "ReturnRegardless";}             // Forward branch to a come from instruction
      void action()      {setInstructionIndex(end.position);}                   // Set instruction pointer to continue execution at the next instruction
     }
    ReturnRegardless returnRegardless() {return new ReturnRegardless();}        // Leave the block regardless

//D3 Simplex tests                                                              // Exit a block depending on the result of testing the content of a variable

    class ReturnIfAllZero extends Branch                                        // Branch if all the bits in a field are zero
     {ReturnIfAllZero(Layout.Field Field)                                       // Forward branch to a come from instruction
       {super(Field); name = "ReturnIfAllZero";
       }
      void action()                                                             // Set instruction pointer to continue execution at the next instruction
       {for (int i = 0; i <  bit.width; i++) if (bit.get(i)) return;            // Non zero bit
        setInstructionIndex(end.position);                                      // Set instruction pointer to continue execution at the next instruction becuase all biots are zero
       }
     }
    ReturnIfAllZero returnIfAllZero(Layout.Field field)                         // Jump forward to a come from instruction
     {return new ReturnIfAllZero(field);
     }

    class ReturnIfNotAllZero extends Branch                                     // Branch if not all the bits in a field are zero
     {ReturnIfNotAllZero(Layout.Field Field)                                    // Forward branch to a come from instruction
       {super(Field); name = "ReturnIfNotAllZero";
       }
      void action()                                                             // Set instruction pointer to continue execution at the next instruction
       {for (int i = 0; i <  bit.width; i++)                                    // Examine each bit
         {if (bit.get(i))                                                       // Found a bit that is not zero so branch
           {setInstructionIndex(end.position);                                  // Set instruction pointer to continue execution at the next instruction becuase all biots are zero
            return;
           }
         }
       }
     }
    ReturnIfNotAllZero returnIfNotAllZero(Layout.Field field)                   // Jump forward to a come from instruction
     {return new ReturnIfNotAllZero(field);
     }

    class ReturnIfZero extends Branch                                           // Branch if a bit is zero
     {ReturnIfZero(Layout.Bit Bit)                                              // Forward branch to a come from instruction
       {super(Bit); name = "ReturnIfZero";
       }
      void action() {if (!bit.get(0)) setInstructionIndex(end.position);}       // Set instruction pointer to continue execution at the next instruction
     }
    ReturnIfZero returnIfZero(Layout.Bit bit)                                   // Jump forward to a come from instruction
     {return new ReturnIfZero(bit);
     }

    class ReturnIfOne extends Branch                                            // Branch if a bit is one
     {ReturnIfOne(Layout.Bit Bit)                                               // Forward branch to a come from instruction
       {super(Bit); name = "ReturnIfOne";
       }
      void action() {if (bit.get(0)) setInstructionIndex(end.position);}        // Set instruction pointer to continue execution at the next instruction
     }
    ReturnIfOne returnIfOne(Layout.Bit bit)                                     // Jump forward to a come from instruction
     {return new ReturnIfOne(bit);
     }

    class ReturnIfAllOnes extends Branch                                        // Branch if all the bits in a field are one
     {ReturnIfAllOnes(Layout.Field Field)                                       // Forward branch to a come from instruction
       {super(Field); name = "ReturnIfAllOnes";
       }
      void action()                                                             // Set instruction pointer to continue execution at the next instruction
       {for (int i = 0; i <  bit.width; i++) if (!bit.get(i)) return;           // Zero bit
        setInstructionIndex(end.position);                                      // Set instruction pointer to continue execution at the next instruction becuase all biots are zero
       }
     }
    ReturnIfAllOnes returnIfAllOnes(Layout.Field field)                         // Jump forward to a come from instruction
     {return new ReturnIfAllOnes(field);
     }

    class ReturnIfNotAllOnes extends Branch                                     // Branch if not all the bits in a field are one
     {ReturnIfNotAllOnes(Layout.Field Field)                                    // Forward branch to a come from instruction
       {super(Field); name = "ReturnINotAllAOnes";
       }
      void action()                                                             // Set instruction pointer to continue execution at the next instruction
       {for (int i = 0; i <  bit.width; i++)                                    // Examine each bit
         {if (!bit.get(i))                                                      // Zero bit
           {setInstructionIndex(end.position);                                  // Set instruction pointer to continue execution at the next instruction becuase all biots are zero
            return;                                                             // Zero bit
           }
         }
       }
     }
    ReturnIfNotAllOnes returnIfNotAllOnes(Layout.Field field)                   // Jump forward to a come from instruction
     {return new ReturnIfNotAllOnes(field);
     }

//D3 Duplex tests                                                               // Exit a block depending on the result of testing one variable against another

    class ReturnIfEqual extends BranchOnCompare                                 // Exit the block if two fields are equal
     {ReturnIfEqual(Layout.Field First, Layout.Field Second)
       {super(First, Second);
        name = "ReturnIfEqual";
       }
      void action()                                                             // Set instruction pointer to continue execution at the next instruction
       {for (int i = 0; i < first.width; i++)                                   // Check each bit  in the same  sized fields
         {if (first.get(i) != second.get(i)) return;                            // Bits differ at this position so no branch required
         }
        setInstructionIndex(end.position);                                      // All bits equal, update the instruction pointer to exit the block
       }
     }
    ReturnIfEqual returnIfEqual(Layout.Field first, Layout.Field second)        // Exit if the two field are equal
     {return new ReturnIfEqual(first, second);
     }

    class ReturnIfNotEqual extends BranchOnCompare                              // Exit the block if two fields are equal
     {ReturnIfNotEqual(Layout.Field First, Layout.Field Second)
       {super(First, Second);
        name = "ReturnIfNotEqual";
       }
      void action()                                                             // Set instruction pointer to continue execution at the next instruction
       {for (int i = 0; i < first.width; i++)                                   // Check each bit  in the same  sized fields
         {if (first.get(i) != second.get(i))                                    // Unequal bits so exit
           {setInstructionIndex(end.position);
            return;
           }
         }
       }
     }
    ReturnIfNotEqual returnIfNotEqual(Layout.Field first, Layout.Field second)  // Exit if the two field are equal
     {return new ReturnIfNotEqual(first, second);
     }

    class ReturnIfLessThan extends BranchOnCompare                              // Exit the block if the first field is less than the second field
     {ReturnIfLessThan(Layout.Field First, Layout.Field Second)
       {super(First, Second);
        name = "ReturnIfLessThan";
       }
      void action()                                                             // Set instruction pointer to continue execution at the next instruction
       {for (int i = first.width-1; i >= 0; --i)                                // Check each bit  in the same  sized fields
         {final Boolean f = first.get(i), s = second.get(i);                    // Bits
          if (f && !s) return;                                                  // Greater than so less or equal than is impossible
          if (f == s) continue;                                                 // Equal so less than or equal might be possible
          if (!f && s)                                                          // Less than
           {setInstructionIndex(end.position);
            return;
           }
         }
       }
     }
    ReturnIfLessThan returnIfLessThan(Layout.Field first, Layout.Field second)  // Exit if the two field are equal
     {return new ReturnIfLessThan(first, second);
     }

    class ReturnIfLessThanOrEqual extends BranchOnCompare                       // Exit the block if the first field is less than the second field
     {ReturnIfLessThanOrEqual(Layout.Field First, Layout.Field Second)
       {super(First, Second);
        name = "ReturnIfLessThanOrEqual";
       }
      void action()                                                             // Set instruction pointer to continue execution at the next instruction
       {for (int i = first.width-1; i >= 0; --i)                                // Check each bit  in the same  sized fields
         {final Boolean f = first.get(i), s = second.get(i);                    // Bits
          if (f && !s) return;                                                  // Greater than so less than or equal is impossible
          if (f == s) continue;                                                 // Equal so less than or equal  might be possible
          if (!f && s)                                                          // Less than
           {setInstructionIndex(end.position);
            return;
           }
         }
        setInstructionIndex(end.position);                                      // All equal so exit block
       }
     }
    ReturnIfLessThanOrEqual returnIfLessThanOrEqual                             // Exit if the two field are equal
     (Layout.Field first, Layout.Field second)
     {return new ReturnIfLessThanOrEqual(first, second);
     }

    class ReturnIfGreaterThan extends BranchOnCompare                           // Exit the block if the first field is greater than the second field
     {ReturnIfGreaterThan(Layout.Field First, Layout.Field Second)
       {super(First, Second);
        name = "ReturnIfGreaterThan";
       }
      void action()                                                             // Set instruction pointer to continue execution at the next instruction
       {for (int i = first.width-1; i >= 0; --i)                                // Check each bit  in the same  sized fields
         {final Boolean f = first.get(i), s = second.get(i);                    // Bits
          if (!f &&  s) return;                                                 // Less than so greater than is impossible
          if ( f ==  s) continue;                                               // Equal so greater than might be possible
          if ( f && !s)                                                         // Greater than
           {setInstructionIndex(end.position);
            return;
           }
         }
       }
     }
    ReturnIfGreaterThan returnIfGreaterThan(Layout.Field first, Layout.Field second)  // Exit if the two field are equal
     {return new ReturnIfGreaterThan(first, second);
     }

    class ReturnIfGreaterThanOrEqual extends BranchOnCompare                    // Exit the block if the first field is greater than the second field
     {ReturnIfGreaterThanOrEqual(Layout.Field First, Layout.Field Second)
       {super(First, Second);
        name = "ReturnIfGreaterThanOrEqual";
       }
      void action()                                                             // Set instruction pointer to continue execution at the next instruction
       {for (int i = first.width-1; i >= 0; --i)                                // Check each bit  in the same sized fields
         {final Boolean f = first.get(i), s = second.get(i);                    // Bits
          if (!f &&  s) return;                                                 // Less than so greater than or equal is impossible
          if ( f ==  s) continue;                                               // Equal so greater than or equal might be possible
          if ( f && !s)                                                         // Greater than
           {setInstructionIndex(end.position);
            return;
           }
         }
        setInstructionIndex(end.position);                                      // All equal so exit block
       }
     }
    ReturnIfGreaterThanOrEqual returnIfGreaterThanOrEqual                       // Exit if the two field are equal
     (Layout.Field first, Layout.Field second)
     {return new ReturnIfGreaterThanOrEqual(first, second);
     }
   }

//D1 Debugging                                                                  // Print program

  String printCode()                                                            // Print the program
   {final StringBuilder s = new StringBuilder();                                // Printed results
    s.append(String.format("%4s  %24s %6s\n", "Line", "OpCode", "Target"));     // Titles
    final int N = instructions.size();                                          // Number of instructions
    for (int i = 0; i < N; i++)
     {final Instruction I = instructions.elementAt(i);
      if (I instanceof Branch)
       {final Branch B = (Branch)I;
        s.append(String.format("%4d  %24s %6d\n", i+1, I.name, B.target+2));    // Two becuase we want to be one based and because the loop will increment the instructionPointer again
       }
      else
       {s.append(String.format("%4d  %24s\n", i+1, I.name));
       }
     }
    return s.toString();
   }

//public String toString() {return printProgram();}                             // Alternate name to make it easier to print a program
  public String toString() {return layout.toString();}                          // Alternate name to make it easier to print a program

  static boolean        debug = false;                                          // Debug if true

  class Debug extends Instruction                                               // Enable or  disable debug
   {final boolean on;
    Debug(boolean On) {on = On;}                                                // Save debug state
    void action() {debug = on;}                                                 // Perform instruction
   }
  Debug debug(boolean on) {return new Debug(on);}                               // Debug mode


  class Say extends Instruction                                                 // Say something to help debug a program
   {Say() {name = "Say";}
    void action() {if (debug) debug();}                                         // Say stuff if we are debugging
    void debug () {}                                                            // Debug stuff to say
   }

  void  Say(Object...O) {say(printer, O);}                                      // Captured say

//D0                                                                            // Tests: I test, therefore I am.  And so do my mentees.  But most other people, apparently, do not, they live in a half world lit by shadows in which they never know if their code works or not.

  static void test_zero_and_ones()
   {Layout           l = new Layout();
    Layout.Variable  a = l.variable ("a", 4);
    Layout.Variable  b = l.variable ("b", 4);
    l.layout("s", a, b);

    BitMachine m = new BitMachine();
    m.zero(a);
    m.ones(b);
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     8                240   s
V    0     4                  0     a     a
V    4     4                 15     b     b
""");
   }

  static void test_invert()
   {Layout           l = new Layout();
    Layout.Variable  a = l.variable ("a", 4);
    l.layout(a);
    a.fromString("0101");

    BitMachine m = new BitMachine();
    m.not(a);
    a.ok(10);
   }

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

    a.fromInt(0b1100);

    BitMachine m = new BitMachine();
    m.new ShiftRightOneByZero(a);
    m.execute();
    a.ok(0b0110);
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
    Layout.Bit      a0 = l.bit      ("a0");
    Layout.Bit      a1 = l.bit      ("a1");
    Layout.Bit      b1 = l.bit      ("b1");
    Layout.Bit      b2 = l.bit      ("b2");
    l.layout("s", a, b, c, aa, ab, ac, ba, bb, bc, ca, cb, cc, a0, a1, b1, b2);

    a.fromInt(1);
    b.fromInt(2);
    c.fromInt(1);

    BitMachine m = new BitMachine();
    m.Equals(aa, a, a);
    m.Equals(ab, a, b);
    m.Equals(ac, a, c);
    m.Equals(ba, b, a);
    m.Equals(bb, b, b);
    m.Equals(bc, b, c);
    m.Equals(ca, c, a);
    m.Equals(cb, c, b);
    m.Equals(cc, c, c);
    m.Equals(a0, a, 0);
    m.Equals(a1, a, 1);
    m.Equals(b1, b, 1, a, 0, 1);
    m.Equals(b2, b, 2, a, 0, 1);
    Layout.Bit A0 = m.Equals(a, 0);
    Layout.Bit A1 = m.Equals(a, 1);
    Layout.Bit AA = m.Equals(a, a);
    Layout.Bit AB = m.Equals(a, b);
    Layout.Bit AC = m.Equals(a, c);
    m.execute();
    //stop(A0, A1);
    A0.ok(0);
    A1.ok(1);
    AA.ok(1);
    AB.ok(0);
    AC.ok(1);

    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    25           13979937   s
V    0     4                  1     a     a
V    4     4                  2     b     b
V    8     4                  1     c     c
B   12     1                  1     aa     aa
B   13     1                  0     ab     ab
B   14     1                  1     ac     ac
B   15     1                  0     ba     ba
B   16     1                  1     bb     bb
B   17     1                  0     bc     bc
B   18     1                  1     ca     ca
B   19     1                  0     cb     cb
B   20     1                  1     cc     cc
B   21     1                  0     a0     a0
B   22     1                  1     a1     a1
B   23     1                  1     b1     b1
B   24     1                  0     b2     b2
""");
   }

  static void test_not_equal()
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
    Layout.Bit      a0 = l.bit      ("a0");
    Layout.Bit      a1 = l.bit      ("a1");
    Layout.Bit      b1 = l.bit      ("b1");
    Layout.Bit      b2 = l.bit      ("b2");
    l.layout("s", a, b, c, aa, ab, ac, ba, bb, bc, ca, cb, cc, a0, a1, b1, b2);

    a.fromInt(1);
    b.fromInt(2);
    c.fromInt(1);

    BitMachine m = new BitMachine();
    m.notEquals(aa, a, a);
    m.notEquals(ab, a, b);
    m.notEquals(ac, a, c);
    m.notEquals(ba, b, a);
    m.notEquals(bb, b, b);
    m.notEquals(bc, b, c);
    m.notEquals(ca, c, a);
    m.notEquals(cb, c, b);
    m.notEquals(cc, c, c);
    m.notEquals(a0, a, 0);
    m.notEquals(a1, a, 1);
    m.notEquals(b1, b, 1, a, 0, 1);
    m.notEquals(b2, b, 2, a, 0, 1);
    Layout.Bit A0 = m.notEquals(a, 0);
    Layout.Bit A1 = m.notEquals(a, 1);
    Layout.Bit AA = m.notEquals(a, a);
    Layout.Bit AB = m.notEquals(a, b);
    Layout.Bit AC = m.notEquals(a, c);
    m.execute();
    //stop(A0, A1);
    A0.ok(1);
    A1.ok(0);
    AA.ok(0);
    AB.ok(1);
    AC.ok(0);

    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    25           19570977   s
V    0     4                  1     a     a
V    4     4                  2     b     b
V    8     4                  1     c     c
B   12     1                  0     aa     aa
B   13     1                  1     ab     ab
B   14     1                  0     ac     ac
B   15     1                  1     ba     ba
B   16     1                  0     bb     bb
B   17     1                  1     bc     bc
B   18     1                  0     ca     ca
B   19     1                  1     cb     cb
B   20     1                  0     cc     cc
B   21     1                  1     a0     a0
B   22     1                  0     a1     a1
B   23     1                  0     b1     b1
B   24     1                  1     b2     b2
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
    Layout.Bit      a0 = l.bit      ("a0");
    Layout.Bit      a1 = l.bit      ("a1");
    Layout.Bit      a2 = l.bit      ("a2");
    l.layout("s", a, b, c, aa, ab, ac, ba, bb, bc, ca, cb, cc, a0, a1, a2);

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
    m.lessThan(a0, a, 0);
    m.lessThan(a1, a, 1);
    m.lessThan(a2, a, 2);
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    24            8921377   s
V    0     4                  1     a     a
V    4     4                  2     b     b
V    8     4                  1     c     c
B   12     1                  0     aa     aa
B   13     1                  1     ab     ab
B   14     1                  0     ac     ac
B   15     1                  0     ba     ba
B   16     1                  0     bb     bb
B   17     1                  0     bc     bc
B   18     1                  0     ca     ca
B   19     1                  1     cb     cb
B   20     1                  0     cc     cc
B   21     1                  0     a0     a0
B   22     1                  0     a1     a1
B   23     1                  1     a2     a2
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
    Layout.Bit      a0 = l.bit      ("a0");
    Layout.Bit      a1 = l.bit      ("a1");
    Layout.Bit      a2 = l.bit      ("a2");
    l.layout("s", a, b, c, aa, ab, ac, ba, bb, bc, ca, cb, cc, a0, a1, a2);

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
    m.lessThanOrEqual(a0, a, 0);
    m.lessThanOrEqual(a1, a, 1);
    m.lessThanOrEqual(a2, a, 2);
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    24           14512417   s
V    0     4                  1     a     a
V    4     4                  2     b     b
V    8     4                  1     c     c
B   12     1                  1     aa     aa
B   13     1                  1     ab     ab
B   14     1                  1     ac     ac
B   15     1                  0     ba     ba
B   16     1                  1     bb     bb
B   17     1                  0     bc     bc
B   18     1                  1     ca     ca
B   19     1                  1     cb     cb
B   20     1                  1     cc     cc
B   21     1                  0     a0     a0
B   22     1                  1     a1     a1
B   23     1                  1     a2     a2
""");
   }

  static void test_greater_than_equal()
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
    Layout.Bit      a0 = l.bit      ("a0");
    Layout.Bit      a1 = l.bit      ("a1");
    Layout.Bit      a2 = l.bit      ("a2");
    l.layout("s", a, b, c, aa, ab, ac, ba, bb, bc, ca, cb, cc, a0, a1, a2);

    a.fromInt(1);
    b.fromInt(2);
    c.fromInt(1);

    BitMachine m = new BitMachine();
    m.greaterThanOrEqual(aa, a, a);
    m.greaterThanOrEqual(ab, a, b);
    m.greaterThanOrEqual(ac, a, c);
    m.greaterThanOrEqual(ba, b, a);
    m.greaterThanOrEqual(bb, b, b);
    m.greaterThanOrEqual(bc, b, c);
    m.greaterThanOrEqual(ca, c, a);
    m.greaterThanOrEqual(cb, c, b);
    m.greaterThanOrEqual(cc, c, c);
    m.greaterThanOrEqual(a0, a, 0);
    m.greaterThanOrEqual(a1, a, 1);
    m.greaterThanOrEqual(a2, a, 2);
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    24            7852321   s
V    0     4                  1     a     a
V    4     4                  2     b     b
V    8     4                  1     c     c
B   12     1                  1     aa     aa
B   13     1                  0     ab     ab
B   14     1                  1     ac     ac
B   15     1                  1     ba     ba
B   16     1                  1     bb     bb
B   17     1                  1     bc     bc
B   18     1                  1     ca     ca
B   19     1                  0     cb     cb
B   20     1                  1     cc     cc
B   21     1                  1     a0     a0
B   22     1                  1     a1     a1
B   23     1                  0     a2     a2
""");
   }

  static void test_greater_than()
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
    Layout.Bit      a0 = l.bit      ("a0");
    Layout.Bit      a1 = l.bit      ("a1");
    Layout.Bit      a2 = l.bit      ("a2");
    l.layout("s", a, b, c, aa, ab, ac, ba, bb, bc, ca, cb, cc, a0, a1, a2);

    a.fromInt(1);
    b.fromInt(2);
    c.fromInt(1);

    BitMachine m = new BitMachine();
    m.greaterThan(aa, a, a);
    m.greaterThan(ab, a, b);
    m.greaterThan(ac, a, c);
    m.greaterThan(ba, b, a);
    m.greaterThan(bb, b, b);
    m.greaterThan(bc, b, c);
    m.greaterThan(ca, c, a);
    m.greaterThan(cb, c, b);
    m.greaterThan(cc, c, c);
    m.greaterThan(a0, a, 0);
    m.greaterThan(a1, a, 1);
    m.greaterThan(a2, a, 2);
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    24            2261281   s
V    0     4                  1     a     a
V    4     4                  2     b     b
V    8     4                  1     c     c
B   12     1                  0     aa     aa
B   13     1                  0     ab     ab
B   14     1                  0     ac     ac
B   15     1                  1     ba     ba
B   16     1                  0     bb     bb
B   17     1                  1     bc     bc
B   18     1                  0     ca     ca
B   19     1                  0     cb     cb
B   20     1                  0     cc     cc
B   21     1                  1     a0     a0
B   22     1                  0     a1     a1
B   23     1                  0     a2     a2
""");
   }

  static void test_copy()
   {Layout           l = new Layout();
    Layout.Variable  a = l.variable ("a", 4);
    Layout.Variable  b = l.variable ("b", 4);
    Layout.Variable  c = l.variable ("c", 4);
    l.layout("s", a, b, c);

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
V    0     4                  7     a     a
V    4     4                  7     b     b
V    8     4                  7     c     c
""");
   }

  static void test_copy_off()
   {Layout           l = new Layout();
    Layout.Variable  a = l.variable ("a", 4);
    Layout.Bit       b = l.bit      ("b");
    Layout.Bit       c = l.bit      ("c");
    l.layout("s", a, b, c);

    a.fromInt(7);
    b.ones();
    c.ones();

    BitMachine m = new BitMachine();
    m.copy(b, 0, a, 0, 1);
    m.copy(c, 0, a, 3, 1);
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     6                 23   s
V    0     4                  7     a     a
B    4     1                  1     b     b
B    5     1                  0     c     c
""");
   }

  static void test_if_then()
   {Layout           l = new Layout();
    Layout.Variable  s = l.variable ("s", 4);
    Layout.Variable  t = l.variable ("t", 4);
    Layout.Variable  e = l.variable ("e", 4);
    Layout.Bit       i = l.bit      ("i");
    l.layout("x", i, s, t, e);

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
B    0     1                  1     i     i
V    1     4                  7     s     s
V    5     4                  7     t     t
V    9     4                  0     e     e
""");
   }

  static void test_unless_then()
   {Layout           l = new Layout();
    Layout.Variable  s = l.variable ("s", 4);
    Layout.Variable  t = l.variable ("t", 4);
    Layout.Bit       i = l.bit      ("i");
    l.layout("x", i, s, t);

    i.fromInt(0);
    s.fromInt(7);
    t.fromInt(0);

    BitMachine m = new BitMachine();
    m.new Unless(i)
     {void Then() {m.copy(t, s);}
     };
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     9                238   x
B    0     1                  0     i     i
V    1     4                  7     s     s
V    5     4                  7     t     t
""");
   }

  static void test_if_else()
   {Layout           l = new Layout();
    Layout.Variable  s = l.variable ("s", 4);
    Layout.Variable  t = l.variable ("t", 4);
    Layout.Variable  e = l.variable ("e", 4);
    Layout.Bit       i = l.bit      ("i");
    l.layout("x", i, s, t, e);

    i.fromInt(0);
    s.fromInt(7);
    t.fromInt(0);
    e.fromInt(0);

    BitMachine m = new BitMachine();
    m.new IfElse(i)
     {void Then() {m.copy(t, s);}
      void Else() {m.copy(e, s);}
     };
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    13               3598   x
B    0     1                  0     i     i
V    1     4                  7     s     s
V    5     4                  0     t     t
V    9     4                  7     e     e
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
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
A    0    96      0               A
S    0    24             590849     s     s
V    0     8                  1       a     s.a
V    8     8                  4       b     s.b
V   16     8                  9       c     s.c
A   24    96      1               A
S   24    24             787970     s     s
V   24     8                  2       a     s.a
V   32     8                  6       b     s.b
V   40     8                 12       c     s.c
A   48    96      2               A
S   48    24             985091     s     s
V   48     8                  3       a     s.a
V   56     8                  8       b     s.b
V   64     8                 15       c     s.c
A   72    96      3               A
S   72    24            1182212     s     s
V   72     8                  4       a     s.a
V   80     8                 10       b     s.b
V   88     8                 18       c     s.c
""");
    BitMachine m = new BitMachine();
    m.new For(A)
     {void block()
       {m.copy(b, a);
       };
     };

    m.codeOk("""
Line                    OpCode Target
   1                       For
   2                  LessThan
   3              BranchIfZero      8
   4         SetIndexFromUnary
   5                      Copy
   6         ShiftLeftOneByOne
   7                      GoTo      2
   8                  ComeFrom
""");

    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
A    0    96      0               A
S    0    24             590081     s     s
V    0     8                  1       a     s.a
V    8     8                  1       b     s.b
V   16     8                  9       c     s.c
A   24    96      1               A
S   24    24             786946     s     s
V   24     8                  2       a     s.a
V   32     8                  2       b     s.b
V   40     8                 12       c     s.c
A   48    96      2               A
S   48    24             983811     s     s
V   48     8                  3       a     s.a
V   56     8                  3       b     s.b
V   64     8                 15       c     s.c
A   72    96      3               A
S   72    24            1180676     s     s
V   72     8                  4       a     s.a
V   80     8                  4       b     s.b
V   88     8                 18       c     s.c
""");
   }

  static void test_branchIfEqual()
   {Layout           l = new Layout();
    Layout.Variable  a = l.variable ("a", 4);
    Layout.Variable  b = l.variable ("b", 4);
    Layout.Variable  c = l.variable ("c", 4);
    l.layout("s", a, b, c);

    a.fromInt(3);
    b.fromInt(3);
    c.fromInt(0);

    BitMachine      m = new BitMachine();
    BranchOnCompare e = m.branchIfEqual(a, b);
    m.copy(c, a);
    m.comeFromComparison(e);
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    12                 51   s
V    0     4                  3     a     a
V    4     4                  3     b     b
V    8     4                  0     c     c
""");
   }

  static void test_block_comparisons()
   {Layout           l = new Layout();
    Layout.Variable  a = l.variable ("a", 4);
    Layout.Variable  b = l.variable ("b", 4);
    Layout.Variable  c = l.variable ("c", 4);
    Layout.Variable  eq01 = l.variable ("eq01", 4), eq11 = l.variable ("eq11", 4), eq10 = l.variable ("eq10", 4);
    Layout.Variable  ne01 = l.variable ("ne01", 4), ne11 = l.variable ("ne11", 4), ne10 = l.variable ("ne10", 4);
    Layout.Variable  lt01 = l.variable ("lt01", 4), lt11 = l.variable ("lt11", 4), lt10 = l.variable ("lt10", 4);
    Layout.Variable  le01 = l.variable ("le01", 4), le11 = l.variable ("le11", 4), le10 = l.variable ("le10", 4);
    Layout.Variable  gt01 = l.variable ("gt01", 4), gt11 = l.variable ("gt11", 4), gt10 = l.variable ("gt10", 4);
    Layout.Variable  ge01 = l.variable ("ge01", 4), ge11 = l.variable ("ge11", 4), ge10 = l.variable ("ge10", 4);
    Layout.Structure s     = l.structure("s", a, b, c,
      eq01, eq11, eq10, ne01, ne11, ne10,
      lt01, lt11, lt10, le01, le11, le10,
      gt01, gt11, gt10, ge01, ge11, ge10);
    l.layout(s); s.ones();

    a.fromInt(0);
    b.fromInt(1);
    c.fromInt(0);

    BitMachine m = new BitMachine();
    m.new Block() {void code() {returnIfEqual             (a, b); m.copy(eq01, c);}};
    m.new Block() {void code() {returnIfNotEqual          (a, b); m.copy(ne01, c);}};
    m.new Block() {void code() {returnIfLessThan          (a, b); m.copy(lt01, c);}};
    m.new Block() {void code() {returnIfLessThanOrEqual   (a, b); m.copy(le01, c);}};
    m.new Block() {void code() {returnIfGreaterThan       (a, b); m.copy(gt01, c);}};
    m.new Block() {void code() {returnIfGreaterThanOrEqual(a, b); m.copy(ge01, c);}};

    m.new Block() {void code() {returnIfEqual             (b, b); m.copy(eq11, c);}};
    m.new Block() {void code() {returnIfNotEqual          (b, b); m.copy(ne11, c);}};
    m.new Block() {void code() {returnIfLessThan          (b, b); m.copy(lt11, c);}};
    m.new Block() {void code() {returnIfLessThanOrEqual   (b, b); m.copy(le11, c);}};
    m.new Block() {void code() {returnIfGreaterThan       (b, b); m.copy(gt11, c);}};
    m.new Block() {void code() {returnIfGreaterThanOrEqual(b, b); m.copy(ge11, c);}};

    m.new Block() {void code() {returnIfEqual             (b, a); m.copy(eq10, c);}};
    m.new Block() {void code() {returnIfNotEqual          (b, a); m.copy(ne10, c);}};
    m.new Block() {void code() {returnIfLessThan          (b, a); m.copy(lt10, c);}};
    m.new Block() {void code() {returnIfLessThanOrEqual   (b, a); m.copy(le10, c);}};
    m.new Block() {void code() {returnIfGreaterThan       (b, a); m.copy(gt10, c);}};
    m.new Block() {void code() {returnIfGreaterThanOrEqual(b, a); m.copy(ge10, c);}};
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    84                      s
V    0     4                  0     a     a
V    4     4                  1     b     b
V    8     4                  0     c     c
V   12     4                  0     eq01     eq01
V   16     4                 15     eq11     eq11
V   20     4                  0     eq10     eq10
V   24     4                 15     ne01     ne01
V   28     4                  0     ne11     ne11
V   32     4                 15     ne10     ne10
V   36     4                 15     lt01     lt01
V   40     4                  0     lt11     lt11
V   44     4                  0     lt10     lt10
V   48     4                 15     le01     le01
V   52     4                 15     le11     le11
V   56     4                  0     le10     le10
V   60     4                  0     gt01     gt01
V   64     4                  0     gt11     gt11
V   68     4                 15     gt10     gt10
V   72     4                  0     ge01     ge01
V   76     4                 15     ge11     ge11
V   80     4                 15     ge10     ge10
""");
   }

  static void test_block_bits()
   {Layout           l   = new Layout();
    Layout.Variable  a   = l.variable ("a",  1);
    Layout.Variable  f00 = l.variable("f00", 2);
    Layout.Variable  f01 = l.variable("f01", 2);
    Layout.Variable  f11 = l.variable("f11", 2);

    Layout.Variable AllZero____f00 = l.variable("AllZero____f00", 1);
    Layout.Variable NotAllZero_f00 = l.variable("NotAllZero_f00", 1);
    Layout.Variable AllOnes____f00 = l.variable("AllOnes____f00", 1);
    Layout.Variable NotAllOnes_f00 = l.variable("NotAllOnes_f00", 1);

    Layout.Variable AllZero____f01 = l.variable("AllZero____f01", 1);
    Layout.Variable NotAllZero_f01 = l.variable("NotAllZero_f01", 1);
    Layout.Variable AllOnes____f01 = l.variable("AllOnes____f01", 1);
    Layout.Variable NotAllOnes_f01 = l.variable("NotAllOnes_f01", 1);

    Layout.Variable AllZero____f11 = l.variable("AllZero____f11", 1);
    Layout.Variable NotAllZero_f11 = l.variable("NotAllZero_f11", 1);
    Layout.Variable AllOnes____f11 = l.variable("AllOnes____f11", 1);
    Layout.Variable NotAllOnes_f11 = l.variable("NotAllOnes_f11", 1);

    Layout.Structure s   = l.structure("s", a, f00, f01, f11,
     AllZero____f00, NotAllZero_f00, AllOnes____f00, NotAllOnes_f00,
     AllZero____f01, NotAllZero_f01, AllOnes____f01, NotAllOnes_f01,
     AllZero____f11, NotAllZero_f11, AllOnes____f11, NotAllOnes_f11);

    l.layout(s); l.asField().ones();

    a  .zero();
    f00.fromInt(0);
    f01.fromInt(1);
    f11.fromInt(3);

    BitMachine m = new BitMachine();
    m.new Block() {void code() {returnIfAllZero   (f00); m.copy(AllZero____f00, a);}};
    m.new Block() {void code() {returnIfNotAllZero(f00); m.copy(NotAllZero_f00, a);}};
    m.new Block() {void code() {returnIfAllOnes   (f00); m.copy(AllOnes____f00, a);}};
    m.new Block() {void code() {returnIfNotAllOnes(f00); m.copy(NotAllOnes_f00, a);}};

    m.new Block() {void code() {returnIfAllZero   (f01); m.copy(AllZero____f01, a);}};
    m.new Block() {void code() {returnIfNotAllZero(f01); m.copy(NotAllZero_f01, a);}};
    m.new Block() {void code() {returnIfAllOnes   (f01); m.copy(AllOnes____f01, a);}};
    m.new Block() {void code() {returnIfNotAllOnes(f01); m.copy(NotAllOnes_f01, a);}};

    m.new Block() {void code() {returnIfAllZero   (f11); m.copy(AllZero____f11, a);}};
    m.new Block() {void code() {returnIfNotAllZero(f11); m.copy(NotAllZero_f11, a);}};
    m.new Block() {void code() {returnIfAllOnes   (f11); m.copy(AllOnes____f11, a);}};
    m.new Block() {void code() {returnIfNotAllOnes(f11); m.copy(NotAllOnes_f11, a);}};

    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    19             218344   s
V    0     1                  0     a     a
V    1     2                  0     f00     f00
V    3     2                  1     f01     f01
V    5     2                  3     f11     f11
V    7     1                  1     AllZero____f00     AllZero____f00
V    8     1                  0     NotAllZero_f00     NotAllZero_f00
V    9     1                  0     AllOnes____f00     AllOnes____f00
V   10     1                  1     NotAllOnes_f00     NotAllOnes_f00
V   11     1                  0     AllZero____f01     AllZero____f01
V   12     1                  1     NotAllZero_f01     NotAllZero_f01
V   13     1                  0     AllOnes____f01     AllOnes____f01
V   14     1                  1     NotAllOnes_f01     NotAllOnes_f01
V   15     1                  0     AllZero____f11     AllZero____f11
V   16     1                  1     NotAllZero_f11     NotAllZero_f11
V   17     1                  1     AllOnes____f11     AllOnes____f11
V   18     1                  0     NotAllOnes_f11     NotAllOnes_f11
""");
   }

  static void test_inc_dec()
   {Layout           l = new Layout();
    Layout.Variable  a = l.variable("a", 4);
    Layout.Variable  b = l.variable("b", 4);
    l.layout("s", a, b);
    a.fromInt(2); b.fromInt(2);

    BitMachine m = new BitMachine();
    m.inc(a);
    m.dec(b);

    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     8                 19   s
V    0     4                  3     a     a
V    4     4                  1     b     b
""");
   }

  static void test_branchIfNotEqual()
   {Layout           l = new Layout();
    Layout.Variable  a = l.variable ("a", 4);
    Layout.Variable  b = l.variable ("b", 4);
    Layout.Variable  c = l.variable ("c", 4);
    l.layout("s", a, b, c);

    a.fromInt(3);
    b.fromInt(3);
    c.fromInt(0);

    BitMachine      m = new BitMachine();
    BranchOnCompare e = m.branchIfNotEqual(a, b);
    m.copy(c, a);
    m.comeFromComparison(e);
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    12                819   s
V    0     4                  3     a     a
V    4     4                  3     b     b
V    8     4                  3     c     c
""");
   }

  static void test_repeat()
   {Layout           l = new Layout();
    Layout.Variable  a = l.variable ("a", 4);
    Layout.Variable  b = l.variable ("b", 4);
    Layout.Variable  c = l.variable ("c", 4);
    l.layout("s", a, b, c);

    b.fromUnary(4);

    BitMachine       m = new BitMachine();
    m.new Repeat()
     {void code()
       {m.shiftLeftOneByOne(a);
        m.new Say() {void action() {m.Say("AAAA", a.asUnary());}};
        returnIfEqual(a, b);
        m.new Say() {void action() {m.Say("BBBB", b.asUnary());}};
        m.shiftLeftOneByOne(c);
       };
     };
    m.execute();
    ok(m.printer, """
AAAA 1
BBBB 4
AAAA 2
BBBB 4
AAAA 3
BBBB 4
AAAA 4
""");
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    12               2047   s
V    0     4                 15     a     a
V    4     4                 15     b     b
V    8     4                  7     c     c
""");
   }

  static void test_unary_filled()
   {Layout           l = new Layout();
    Layout.Variable  a = l.variable("a", 4);
    Layout.Variable  b = l.variable("b", 4);
    Layout.Variable  c = l.variable("c", 4);
    Layout.Bit      r1 = l.bit("r1");
    Layout.Bit      r2 = l.bit("r2");
    l.layout("s", a, b, c, r1, r2);

    a.fromUnary(1); b.fromUnary(1); c.fromUnary(3);

    BitMachine m = new BitMachine();
    m.unaryFilled(a, b, r1);
    m.unaryFilled(a, c, r2);
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    14              10001   s
V    0     4                  1     a     a
V    4     4                  1     b     b
V    8     4                  7     c     c
B   12     1                  0     r1     r1
B   13     1                  1     r2     r2
""");
   }

  static void test_set_index()
   {final int N = 4;
    Layout           l = new Layout();
    Layout.Variable  a = l.variable ("a", N);
    Layout.Variable  b = l.variable ("b", N);
    Layout.Variable  c = l.variable ("c", N);
    Layout.Structure s = l.structure("s", a, b, c);
    Layout.Array     A = l.array    ("A", s, N);
    Layout.Variable  d = l.variable ("d", N);
    l.layout("S", A, d);

    BitMachine m = new BitMachine();
    for(int i = 0; i < N; ++i)
     {m.setIndexFromInt(A, i);
      m.copy(a, i+1);
      m.copy(b, i+2);
      m.copy(c, i+3);
     }
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    52                      S
A    0    48      0                 A     A
S    0    12                801       s     A.s
V    0     4                  1         a     A.s.a
V    4     4                  2         b     A.s.b
V    8     4                  3         c     A.s.c
A   12    48      1                 A     A
S   12    12               1074       s     A.s
V   12     4                  2         a     A.s.a
V   16     4                  3         b     A.s.b
V   20     4                  4         c     A.s.c
A   24    48      2                 A     A
S   24    12               1347       s     A.s
V   24     4                  3         a     A.s.a
V   28     4                  4         b     A.s.b
V   32     4                  5         c     A.s.c
A   36    48      3                 A     A
S   36    12               1620       s     A.s
V   36     4                  4         a     A.s.a
V   40     4                  5         b     A.s.b
V   44     4                  6         c     A.s.c
V   48     4                  0     d     d
""");

    m.reset();  m.copy(d, 1); m.setIndex(A, d); m.copy(d, a); m.execute(); d.ok(2);
    m.reset();  m.copy(d, 1); m.setIndex(A, d); m.copy(d, b); m.execute(); d.ok(3);
    m.reset();  m.copy(d, 3); m.setIndex(A, d); m.copy(d, a); m.execute(); d.ok(4);
    m.reset();  m.copy(d, 3); m.setIndex(A, d); m.copy(d, c); m.execute(); d.ok(6);
   }

  static void test_return_regardless()
   {Layout           l = new Layout();
    Layout.Bit       a = l.bit      ("a");
    Layout.Bit       b = l.bit      ("b");
    Layout.Bit       c = l.bit      ("c");
    l.layout("S", a, b, c);

    BitMachine m = new BitMachine();
    m.new Block()
     {void code()
       {m.copy(a, 1);
        returnRegardless();
        m.copy(b, 2);
       }
     };
    m.copy(c, 3);
    m.execute();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     3                  5   S
B    0     1                  1     a     a
B    1     1                  0     b     b
B    2     1                  1     c     c
""");
   }

  static void test_copy_long()
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
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
A    0    96      0               A
S    0    24             590849     s     s
V    0     8                  1       a     s.a
V    8     8                  4       b     s.b
V   16     8                  9       c     s.c
A   24    96      1               A
S   24    24             787970     s     s
V   24     8                  2       a     s.a
V   32     8                  6       b     s.b
V   40     8                 12       c     s.c
A   48    96      2               A
S   48    24             985091     s     s
V   48     8                  3       a     s.a
V   56     8                  8       b     s.b
V   64     8                 15       c     s.c
A   72    96      3               A
S   72    24            1182212     s     s
V   72     8                  4       a     s.a
V   80     8                 10       b     s.b
V   88     8                 18       c     s.c
""");

    final Layout.Variable i = Layout.createVariable("index", 4);

    BitMachine m = new BitMachine();
    m.setLayout(l);                                                             // Memory to be manipulated by copyLong
    m.copy(i, 1); m.setIndex(A, i); m.copySetSource(c);
    m.copy(i, 2); m.setIndex(A, i); m.copySetTarget(b);
    m.copyLong(c.width);
    m.execute();

    A.setIndex(2); b.ok(12);
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
A    0    96      0               A
S    0    24             590849     s     s
V    0     8                  1       a     s.a
V    8     8                  4       b     s.b
V   16     8                  9       c     s.c
A   24    96      1               A
S   24    24             787970     s     s
V   24     8                  2       a     s.a
V   32     8                  6       b     s.b
V   40     8                 12       c     s.c
A   48    96      2               A
S   48    24             986115     s     s
V   48     8                  3       a     s.a
V   56     8                 12       b     s.b
V   64     8                 15       c     s.c
A   72    96      3               A
S   72    24            1182212     s     s
V   72     8                  4       a     s.a
V   80     8                 10       b     s.b
V   88     8                 18       c     s.c
""");
  }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_zero_and_ones();
    test_invert();
    test_copy();
    test_copy_off();
    test_shift_left_oneByOne();
    test_shift_right_oneByZero();
    test_equal();
    test_not_equal();
    test_less_than();
    test_less_than_equal();
    test_greater_than();
    test_greater_than_equal();
    test_if_then();
    test_if_else();
    test_unless_then();
    test_for();
    test_branchIfEqual();
    test_branchIfNotEqual();
    test_block_comparisons();
    test_block_bits();
    test_repeat();
    test_inc_dec();
    test_unary_filled();
    test_set_index();
    test_return_regardless();
    test_copy_long();
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    test_repeat();
   }

  public static void main(String[] args)                                        // Test if called as a program
   {try                                                                         // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
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
