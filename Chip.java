//------------------------------------------------------------------------------
// Design, simulate and layout a binary tree on a silicon chip.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Design, simulate and layout digital a binary tree on a silicon chip.

import java.util.*;

//D1 Construct                                                                  // Construct a silicon chip using standard logic gates combined via buses.

public class Chip extends Test                                                  // Describe a chip and emulate its operation.
 {final String   name;                                                          // Name of chip

  Chip(String Name) {name = Name;}                                              // Create a new L<chip>.
  Chip() {this(currentTestNameSuffix());}                                       // Create a new chip while testing.

  static Chip chip()            {return new Chip();}                            // Create a new chip while testing.
  static Chip chip(String name) {return new Chip(name);}                        // Create a new chip while testing.

  public String toString()                                                      // Convert chip to string
   {final StringBuilder b = new StringBuilder();
    b.append("Chip: "                         + name);
    return b.toString();                                                        // String describing chip
   }

// Bits                                                                         // Description of bits

  final Map<String,Bit> bit = new TreeMap<>();                                  // Bits have unique names

  class Bit                                                                     // Bits are connected by gates
   {final String name;                                                          // Name of the bit.
    final Set<Gate> drivenBy = new TreeSet<>();                                 // Driven by these gates. If a bit is driven by multiple gates we assume that the inputs are combined using "or"
    final Set<Gate> drives   = new TreeSet<>();                                 // Drives these gates. The order of the input pins for a gate matters  so this can only be used to find which gates drive this bit but the actual pin will have to be found by examining the gate.

    Bit(String Name)                                                            // Named bit
     {name = validateName(Name);
      put(name);
     }
    Bit(String Name, int index)                                                 // Named and numbered bit
     {name = validateName(Name)+"_"+index;
      put(name);
     }
    Bit(String Name1, String Name2)                                             // Named bit within a named bit
     {name = validateName(Name1)+"_"+validateName(Name2);
      put(name);
     }
    Bit(String Name1, String Name2, int index)                                  // Named amd numbered bit within a named bit
     {name = validateName(Name1)+"_"+validateName(Name2)+"_"+index;
      put(name);
     }

    void put(String name)
     {if (bit.containsKey(name)) stop("Bit", name, "already defined");
      bit.put(name, this);
     }

    String validateName(String name)                                            // Confirm that a component name looks like a variable name and has not already been used
     {if (name == null) err("Name cannot be null");
      final String[]words = name.split("_");
      for (int i = 0; i < words.length; i++)
       {final String w = words[i];
        if (!w.matches("\\A([a-zA-Z][a-zA-Z0-9_.:]*|\\d+)\\Z"))
          stop("Invalid gate name:", name, "in word", w);
       }
      return name;
     }

    public String toString()      {return name;}                                // Name of bit
   }

  Bit bit(String Name)            {return new Bit(Name);}                       // Create a new bit
  Bit bit(String Name, int Index) {return new Bit(Name, Index);}                // Create a new bit
  Bit bit(String Name1, String Name2, int Index)                                // Create a new bit
   {return new Bit(Name1, Name2, Index);
   }

  class Bits extends Stack<Bit>                                                 // Collection of bits in order so we can convert them to integers
   {private static final long serialVersionUID = 1L;
    Bits(Bit...Bits) {for (int i = 0; i < Bits.length; i++) push(Bits[i]);}     // Add bits to a collection of bits
   }

  Bits bits(Bit...bits) {return new Bits(bits);}                                // Get a named bit or define such a bit if it does not already exist

// Gates                                                                        // Gates connect and combine bits

  final Map<String,Gate> gate = new TreeMap<>();                                // Gates have unique names

  class Gate implements Comparable<Gate>                                        // Gates connect and combine bits
   {final Bit  output;                                                          // A gate has one output bit
    final Bits inputs;                                                          // A gate has zero or more input bits whose order matters
    Gate(Bit Output, Bit...bits)                                                // A gate has one output and several input bits
     {output = Output;
      inputs = new Bits(bits);
      output.drivenBy.add(this);
      for (Bit i : inputs) i.drives.add(this);
     }

    public int compareTo(Gate a)  {return toString().compareTo(a.toString());}  // Gates can be added to sets

    public String toString()
     {final StringBuilder s = new StringBuilder();
      s.append("Gate("+output.name+"=");
      for (Bit b : inputs) s.append(b.name+", ");
      if (inputs.size() > 0) s.setLength(s.length()-2);
      return s.toString();
     }
   }

// Pulse                                                                        // Pulses rise and fall at a specified time

  final Map<String,Pulse> pulse = new TreeMap<>();                              // Pulses have unique names

  class Pulse implements Comparable<Pulse>                                      // Pulses rise and fall at a specified time
   {final String name;                                                          // The name of the pulse
    final int    step;                                                          // The step at which the pulse is in effect

    Pulse(String Name, int Step)                                                // The step on which this pulse rise and falls to initiate action
     {name = Name;
      step = Step;
      pulse.put(name, this);
     }

    public int compareTo(Pulse a)  {return toString().compareTo(a.toString());} // Pulses can be added to sets

    public String toString() {return "Pulse("+name+"@"+step+")";}
   }

//D0                                                                            // Tests

  static void test_bit()
   {Chip c = chip();
    Bit  a = c.bit("i", 1);
    Bit  b = c.bit("i", "j", 1);
    ok(a, "i_1");
    ok(b, "i_j_1");
   }

  static void test_bits()
   {Chip c = chip();
    Bit  a = c.bit("i", 1);
    Bit  b = c.bit("i", "j", 1);
    Bits B = c.bits(a, b);
    ok(B.size(), 2);
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_bit();
    test_bits();
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
    testExit(0);                                                                 // Exit with a return code if we failed any tests
   }
 }
