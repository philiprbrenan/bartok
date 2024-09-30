//------------------------------------------------------------------------------
// Emulate assembler code sufficient to manipulate a BTree
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Simulate a silicon chip.

import java.util.*;

//D1 Construct                                                                  // Construct a silicon chip comprised of memory, intermediate bits and logic gates.

public class BitMachine extends Test                                            // A machine whose assembler code is just capable enough to manipulate a b-tree
 {Stack<Instruction> instructions = new Stack<>();                              // Instructions to be executed
  Map<String,Instruction>  labels = new TreeMap<>();                            // Map labels to instructions
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
    abstract void action();                                                     // Action performed by the instruction

    void addInstruction()                                                       // Add the instruction to the instruction stack
     {position = instructions.size();                                           // Position of instruction in stack of instructions
      instructions.push(this);                                                  // Save instruction
     }

    void   addLabels()                                                          // Add the instruction label to the labels map
     {if (labels.containsKey(label)) stop("Label", label, "already defined");   // Duplicate label
      labels.put(label, this);                                                  // Add label to label map
     }
   }

  class ShiftLeftOneByOne extends Instruction                                   // Left shift one place and fill by one
   {Layout.Field field;                                                         // Field to shift
    ShiftLeftOneByOne(Layout.Field Field)                                       // Left shift a field by one place fillng with a one
     {name = "ShiftLeftOneByOne";
      field = Field;
      addInstruction();
     }
    ShiftLeftOneByOne(String Label, Layout.Field Field)                         // Left shift a field by one place fillng with a one
     {this(Field);
      label = Label;
      addLabels();
     }
    void action()                                                               // Perform instruction
     {for (int i = field.width-1; i > 0; i--) field.set(i, field.get(i-1));
      field.set(0, true);
     }
   }

//D0                                                                            // Tests: I test, therefore I am.  And so do my mentees.  But most other people, apparently, do not, they live in a half world lit by shadows in which they never know if their code works or not.

  static void test_shift_left_oneByOne()
   {Layout           l = new Layout();
    Layout .Variable a = l.variable ("a", 4);
    l.layout(a);

    a.fromInt(0b1010);

    BitMachine m = new BitMachine();
    m.new ShiftLeftOneByOne(a);
    m.execute();
    a.ok(0b0101);
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_shift_left_oneByOne();
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
