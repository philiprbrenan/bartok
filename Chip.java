//------------------------------------------------------------------------------
// Design, simulate and layout a binary tree on a silicon chip.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Design, simulate and layout digital a binary tree on a silicon chip.

import java.util.*;

//D1 Construct                                                                  // Construct a silicon chip using standard logic gates combined via buses.

public class Chip extends Test                                                  // Describe a chip and emulate its operation.
 {final String   name;                                                          // Name of chip
  final int        maxSimulationSteps = 1_0;                                    // Maximum number of simulation steps
  final Map<String,Bit>           bit = new TreeMap<>();                        // Bits on the chip
  final Map<String,Gate>         gate = new TreeMap<>();                        // Gates on the chip
  final Map<String,Pulse>       pulse = new TreeMap<>();                        // Pulse used to control the chip
  final Map<String,Register> register = new TreeMap<>();                        // Registers on the chip
  final Pulse  clear;                                                           // The pulse used to clear the chip to zero
  final Pulse update;                                                           // The next execution of the chip
  int           time = 0;                                                       // Number of  steps in time.  We start at time 0
  int changedBitsInLastStep;                                                    // Changed bits in last step
  Trace trace;                                                                  // Tracing

  Chip(String Name)                                                             // Create a new L<chip>.
   {name   = Name;
    clear  = new Pulse(name, "clear");                                          // Clear the chip for action
    update = new Pulse(name, "update");                                         // Next execution of the chip
   }

  Chip() {this(currentTestNameSuffix());}                                       // Create a new chip while testing.

  static Chip chip()            {return new Chip();}                            // Create a new chip while testing.
  static Chip chip(String name) {return new Chip(name);}                        // Create a new chip while testing.

  public String toString()                                                      // Convert chip to string
   {final StringBuilder b = new StringBuilder();
    b.append("Chip: "                         + name);
    return b.toString();                                                        // String describing chip
   }

// Name                                                                         // Elements of the chip have names which are part akphabertic and part numeric.  Components of a name are separated by underscores

  abstract class Name                                                           // Names of elements on the chip
   {final String name;                                                          // Name of the element

    Name(Name name) {this(name.toString());}                                    // The output bit of a gate can be used as the name of the gate

    Name(String Name)                                                           // Named Name
     {name = validateName(Name);
      put();
     }
    Name(String Name, int index)                                                // Named and numbered Name
     {name = validateName(Name)+"_"+index;
      put();
     }
    Name(String Name1, String Name2)                                            // Named Name within a named Name
     {name = validateName(Name1)+"_"+validateName(Name2);
      put();
     }
    Name(String Name1, String Name2, int index)                                 // Named amd numbered Name within a named Name
     {name = validateName(Name1)+"_"+validateName(Name2)+"_"+index;
      put();
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

    abstract void put();                                                        // Put the element into the map of existing elements if possible
    public String toString()      {return name;}                                // Name  as a string
   }

// Bit                                                                          // Description of a bit

  class Bit extends Name                                                        // Bits are connected by gates.  Each bit has a name.
   {final Set<Gate> drivenBy = new TreeSet<>();                                 // Driven by these gates. If a bit is driven by multiple gates we assume that the inputs are combined using "or"
    final Set<Gate> drives   = new TreeSet<>();                                 // Drives these gates. The order of the input pins for a gate matters  so this can only be used to find which gates drive this bit but the actual pin will have to be found by examining the gate.
    Boolean value;                                                              // The currently set value of the bit by its driving gates
    Integer changed;                                                            // The last step at which this bit was changed

    Bit(String name)                           {super(name);}                   // Named bit
    Bit(String name,                int index) {super(name, index);}            // Named and numbered bit
    Bit(String name1, String name2)            {super(name1, name2);}           // Named bit within a named bit
    Bit(String name1, String name2, int index) {super(name1, name2, index);}    // Named and numbered bit within a named bit

    void put()                                                                  // Add to bit map
     {if (bit.containsKey(name)) stop("Bit", name, "has already been already defined");
       bit.put(name, this);
     }

    void update()                                                               // Update the value of this bit via an "or" of all driving gates
     {if (drivenBy.size() == 0) return;                                         // The gate is not being driven by any gate
      for (Gate g : drivenBy)                                                   // Each driving gate looking for a value that is true which will thereby determine a true result
       {Boolean v = g.value;
        if (v != null && v == true) {set(v); return;}                           // Found one value that is true which is enough to determine the outcome
       }
      for (Gate g : drivenBy) if (g.value == null) {set(null); return;}         // Each driving gate looking for a value which is null which will determine a null result
      set(false);                                                               // The only other possibility is that the outcome is false
     }

    Boolean get() {return value;}                                               // Current value o bit

    void set(Boolean Value)                                                     // Set the current value of the bit.
     {if (Value != null && value != null)                                       // Both states are known
       {if (Value != value)                                                     // Changed definitely at this time
         {value   = Value;
          changed = time;
          changedBitsInLastStep++;
         }
       }
      else                                                                      // At least one state is unknown
       {value   = Value;                                                        // What ever thenew value is
        changed = null;                                                         // We do not know if it changed or not
        changedBitsInLastStep++;                                                // It might have changed so we ought to keep going
       }
     }

    void ok(Boolean expected)
     {
     }
    public String toString()                                                    // Print a bit
     {return name+(value == null ? "" : value ? "=1": "=0");
     }
   }

  Bit bit(String Name)            {return new Bit(Name);}                       // Create a new bit
  Bit bit(String Name, int Index) {return new Bit(Name, Index);}                // Create a new bit
  Bit bit(String Name1, String Name2, int Index)                                // Create a new bit
   {return new Bit(Name1, Name2, Index);
   }

// Bits                                                                         // Description of a collection of bits

  class Bits extends Stack<Bit>                                                 // Collection of bits in order so we can convert them to integers
   {private static final long serialVersionUID = 1L;

    Bits(Bit...Bits) {for (int i = 0; i < Bits.length; i++) push(Bits[i]);}     // Add bits to a collection of bits

    Bits(String name, int width)                                                // Create an array of bits
     {for (int i = 0; i < width; i++) push(bit(name, i));
     }

    Bits(String name1, String name2, int width)                                 // Create an array of bits
     {for (int i = 0; i < width; i++) push(bit(name1, name2, i));
     }

    void ones()                                                                 // Drive the bits with some ones
     {for(Bit b : this) new One(b);                                             // Drive this bit with a one
     }

    public String toString()                                                    // Convert the bits represented by an output bus to a string
     {final StringBuilder s = new StringBuilder();
      for (Bit b : this)
       {final Boolean v = b.get();
        s.append(v == null ? '.' : v ? '1' : '0');
       }
      return s.reverse().toString();                                            // Prints string with lowest bit rightmost
     }

    int sameSize(Bits b)                                                        // Check the specified set of bits is the same size as this one
     {final int A = size(), B = b.size();
      if (A != B) stop("This set of bits has width", A,
         "but the set of input bits has width", B);
      return A;
     }

    void shiftLeftOnce(Bits toShift, boolean fill)                             // Attach to these bits the left shift by one filled with the specified replacement bit
     {final int  N = sameSize(toShift);
      final Gate g = fill ? new One(elementAt(0)) : new Zero(elementAt(0));
      for (int i = 1; i < N; i++)
       {new Continue(elementAt(i), toShift.elementAt(i-1));
       }
     }

    void shiftLeftOnceByOne (Bits toShift) {shiftLeftOnce(toShift, true);}      // Attach to these bits the left shift by one filled with one  of the supplied bits
    void shiftLeftOnceByZero(Bits toShift) {shiftLeftOnce(toShift, false);}     // Attach to these bits the left shift by one filled with zero of the supplied bits
   }

  Bits bits(Bit...bits) {return new Bits(bits);}                                // Get a named bit or define such a bit if it does not already exist

  Bits bits(String name, int width) {return new Bits(name, width);}             // Make an array of bits

  Bits bits(String name1, String name2, int width)                              // Make an array of bits
   {return new Bits(name1, name2, width);
   }

  Bits bits(String name)                                                        // Make some bits from a string of names separted by spaces
   {final String[]s = name.split("\\s+");
    final Bit   []b = new Bit[s.length];
    for (int i = 0; i < s.length; ++i) b[i] = bit(s[i]);                        // Make a bit from each name
    return new Bits(b);                                                         // Create bits
   }

// Gate                                                                         // Gates connect and combine bits

  class Gate extends Name implements Comparable<Gate>                           // Gates connect and combine bits
   {final Bit  output;                                                          // A gate has one output bit
    final Bits inputs;                                                          // A gate has zero or more input bits whose order matters
    Boolean value;                                                              // The next value of the gate
    Gate(Bit Output, Bit...Inputs)                                              // A gate has one output and several input bits
     {super(Output);                                                            // Use the name of the output bit as the name of the gate as well
      output = Output;
      inputs = new Bits(Inputs);
      output.drivenBy.add(this);
      for (Bit i : inputs) i.drives.add(this);
     }

    void action() {}                                                            // Override this method to specify how the gate works

    void put()                                                                  // Add to bit map
     {if (gate.containsKey(name)) stop("Gate", name, "has already been already defined");
      gate.put(name, this);
     }

    void set(Boolean Value) {value = Value;}                                    // Set the next value of the gate
    public int compareTo(Gate a)  {return toString().compareTo(a.toString());}  // Gates can be added to sets

    public String toString()
     {final StringBuilder s = new StringBuilder();
      s.append("Gate("+output.name+"=");
      for (Bit b : inputs) s.append(b.name+", ");
      if (inputs.size() > 0) s.setLength(s.length()-2);
      return s.toString();
     }
   }

  class Zero extends Gate                                                       // Zero gate
   {Zero(Bit Output) {super(Output);}
    void action()    {set(false);}                                              // Set to false as a surrogate for zero
   }

  class One extends Gate                                                        // One gate
   {One(Bit Output) {super(Output);}
    void action()   {set(true);}                                                // Set to true as a surrogate for one
   }

  class Continue extends Gate                                                   // Continue gate
   {Continue(Bit Output, Bit Input) {super(Output, Input);}
    void action() {set(inputs.firstElement().get());}                             // Set the value of the gate, not the target bit. The bit is responsible for examining its inputs and deciding what its actual value will be
   }

  class Not extends Gate                                                        // Not gate
   {Not(Bit Output, Bit Input) {super(Output, Input);}
    void action()
     {final Boolean v = inputs.firstElement().get();
      output.set(v == null ? v : !v);                                           // If we do not know the value before we do not know it after
     }
   }

  class Or extends Gate                                                         // Or gate
   {Or(Bit Output, Bit...Inputs) {super(Output, Inputs);}
    void action()
     {if (inputs.size() == 0) {set(null); return;}                              // No inputs so the result is unknown
      for (Bit b: inputs)                                                       // Any true input is enough to set to true
       {final Boolean v = b.get();
        if (v != null && v == true) {set(v); return;}
       }
      for (Bit b: inputs)                                                       // Otherwise any null input is enough  to set do not know
       {final Boolean v = b.get();
        if (v == null) {set(v); return;}
       }
      set(false);                                                               // No true or null inputs means it was flase
     }
   }

  class And extends Gate                                                        // And gate
   {And(Bit Output, Bit...Inputs) {super(Output, Inputs);}
    void action()
     {if (inputs.size() == 0) {set(null); return;}                              // No inputs so the result is unknown
      int c = 0;
      for (Bit b: inputs)                                                       // Any false input is enough to set to false
       {final Boolean v = b.get();
        if (v != null && v == false) {set(v); return;}
       }
      for (Bit b: inputs)                                                       // Otherwise any null input is enough  to set do not know
       {final Boolean v = b.get();
        if (v == null) {set(v); return;}
       }
      set(true);                                                                // No false or null inputs means it was true
     }
   }

  class Xor extends Gate                                                        // Xor gate.
   {Xor(Bit Output, Bit...Inputs) {super(Output, Inputs);}
    void action()
     {if (inputs.size() == 0) {set(null); return;}                              // No inputs so the result is unknown
      for (Bit b: inputs)                                                       // Any null input is enough to result in null
       {final Boolean v = b.get();
        if (v == null) {set(v); return;}
       }

      boolean r = inputs.firstElement().get();                                  // Known to exist and not to be null
      for (int i = 1; i < inputs.size(); i++) r ^= inputs.elementAt(i).get();   // Xor is associative so this sequence is as good as any other
      set(r);
     }
   }

// Pulse                                                                        // Pulses rise and fall at a specified time

  class Pulse extends Name implements Comparable<Pulse>                         // Pulses rise and fall at a specified time
   {Pulse after;                                                                // If set, this pulse is relative to the after pulse. Changes to the after pulse will affect this puilse.
    int step;                                                                   // The step at which the pulse is in effect

    Pulse(String Name)                 {super(Name);}                           // Create a pulse
    Pulse(String Name1, String Name2)  {super(Name1, Name2);}                   // The step on which this pulse is active

    void setAfter(Pulse After) {after = After;}                                 // After pulse
    void setStep (int    Step) {step  = Step;}                                  // Step
    void setNow  ()            {step  = time;}                                  // The pulse will update on the next step

    int at() {return after == null ? step : after.at() + step;}                 // Time step at which this pulse will be active                                      // Current time step at which this pulse will be active

    void put()                                                                  // Add to bit map
     {if (pulse.containsKey(name)) stop("Pulse", pulse,
        "has already been already defined");
      pulse.put(name, this);
     }

    public int compareTo(Pulse a)  {return toString().compareTo(a.toString());} // Pulses can be added to sets
    public String toString() {return "Pulse("+name+"@"+step+")";}
   }

// Register                                                                     // A register takes a snapshot of a set of bits on a receipt of a pulse. A register can also be cleared via another pulse.

  class Register extends Name implements Comparable<Register>                   // A register takes a snapshot of a set of bits on a receipt of a pulse. A register can also be cleared via another pulse.
   {final Pulse  clear;                                                         // The pulse used to clear the register to zero
    final Pulse update;                                                         // The pulse used to load the register from the input bits
    final Bits   input;                                                         // The input bits used to load the register
    final Bits  output;                                                         // The output bits driven by the register
    Register(String Name, int width, int steps)                                 // A register of specified name and width takes a snapshot of its input bits and displays them on its output bits on a receipt of a pulse. A register can also be cleared via another pulse.
     {super(Name);
      clear  = new Pulse(Name, "Clear");  clear .setAfter(Chip.this.clear);     // Pulse used to clear register when chip is cleared
      update = new Pulse(Name, "Update"); update.setAfter(Chip.this.update);    // Pulse used to update register
      update.setStep(steps);                                                    // The number of steps to execute before updating the registers
      input  = bits     (Name, "Input" , width);
      output = bits     (Name, "Output", width);
     }

    public int compareTo(Register a)                                            // Pulses can be added to sets
     {return toString().compareTo(a.toString());
     }

    void put()                                                                  // Add to bit map
     {if (pulse.containsKey(name)) stop("Register", register,
       "has already been already defined");
      register.put(name, this);
     }

    void update()                                                               // Change output depending on pulses
     {if (clear.at() == time)                                                   // Clear has priority
       {for (Bit b : output) b.set(false);
        return;
       }
      if (update.at() == time)                                                  // Load requested
       {final int N = input.size();
        for (int j = 0; j < N; ++j)                                             // Copy input to register to output of register
         {output.elementAt(j).set(input .elementAt(j).get());
         }
       }
     }
   }

// Simulate                                                                     // Simulate the elements comprising the chip

  void simulate()                                                               // Simulate the elements comprising the chip
   {for (int i = 0; i < maxSimulationSteps; ++time, i++)                        // Each step in time
     {for (Register r: register.values()) r.update();                           // Update each register
      for (Gate g: gate.values()) g.action();                                   // Each gate computes its result
      changedBitsInLastStep = 0;                                                // Number of changes to bits
      for (Bit  b: bit.values())  b.update();                                   // Each bit updates itself
      if (trace != null) trace.add();                                           // Trace this step if requested
      if (changedBitsInLastStep == 0 && time >= lastPulse())                    // If nothing has changed and thre are no later pulses due we assume that the simulation has come to an end
       {//say("Finished at step", time, "after no activity");
        return;
       }
      //print();
     }
    err("Finished at maximum simulation step", maxSimulationSteps);
   }

  void trace() {}                                                               // Trace execution, called every step

  int  lastPulse()                                                              // Find the latest outstanding pulse
   {int latest = 0;
    for (Pulse p : pulse.values())  latest = max(latest, p.at());
    return latest;
   }

//D1 Tracing                                                                    // Trace simulation

  void print()                                                                  // Print all the bits in the chip
   {say("Time:", time);
    int i = 0;
    for (Bit b : bit.values())
     {final Boolean v = b.get();
      say(String.format("%4d %16s %s", ++i, b.name, v == null ? "." : v ? "1" : "0"));
     }
   }

  abstract class Trace                                                          // Trace simulation
   {final Stack<String> trace = new Stack<>();                                  // Execution trace

    void add() {trace.push(trace());}                                           // Add a trace record

    abstract String title();                                                    // Trace required at each step
    abstract String trace();                                                    // Trace required at each step

    String print(boolean summary)                                               // Print execution trace
     {final StringBuilder b = new StringBuilder();
      b.append("Step  "+title()); b.append('\n');
      b.append(String.format("%4d  %s\n", 1, trace.firstElement()));
      for(int i = 1; i < trace.size(); ++i)
       {final String s = trace.elementAt(i-1);
        final String t = trace.elementAt(i);
        if (!summary || !s.equals(t))                                           // Remove duplicate entries if summaryion requested
          b.append(String.format("%4d  %s\n", i+1, trace.elementAt(i)));
       }
      return b.toString();
     }

    void ok(String expected)                                                    // Confirm expected trace
     {final String s = print(true), t = print(false);
      if      (s.equals(expected)) Test.ok(s, expected);
      else                         Test.ok(t, expected);
     }
   }

  void printTrace  () {say(trace.print(false));}                                // Print execution trace
  void printSummary() {say(trace.print(true));}                                 // Print execution trace summary

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

  static void test_simulate()
   {Chip c = chip();
    Bit  a = c.bit("a"); c.new Zero(a);
    Bit  b = c.bit("b"); c.new One (b);
    Bit  O = c.bit("O");
    Bit  A = c.bit("A");
    c.new And(A, a, b);
    c.new Or (O, a, b);
    c.simulate();
    A.ok(false);
    O.ok(true);
   }

  static void test_register()
   {final int  N = 16;
    Chip       c = chip();
    Register reg = c.new Register("reg", N, 1);
    reg.input.shiftLeftOnceByOne(reg.output);
    c.trace = c.new Trace()
     {String title() {return "Input            Output";}
      String trace() {return String.format("%s %s", reg.input, reg.output);}
     };
    for (int i = 0; i < N; i++)
     {c.simulate();
      c.update.setNow();
     }
    //c.printTrace();
    c.trace.ok("""
Step  Input            Output
   1  0000000000000001 0000000000000000
   2  0000000000000011 0000000000000001
   3  0000000000000011 0000000000000001
   4  0000000000000011 0000000000000001
   5  0000000000000111 0000000000000011
   6  0000000000000111 0000000000000011
   7  0000000000000111 0000000000000011
   8  0000000000001111 0000000000000111
   9  0000000000001111 0000000000000111
  10  0000000000001111 0000000000000111
  11  0000000000011111 0000000000001111
  12  0000000000011111 0000000000001111
  13  0000000000011111 0000000000001111
  14  0000000000111111 0000000000011111
  15  0000000000111111 0000000000011111
  16  0000000000111111 0000000000011111
  17  0000000001111111 0000000000111111
  18  0000000001111111 0000000000111111
  19  0000000001111111 0000000000111111
  20  0000000011111111 0000000001111111
  21  0000000011111111 0000000001111111
  22  0000000011111111 0000000001111111
  23  0000000111111111 0000000011111111
  24  0000000111111111 0000000011111111
  25  0000000111111111 0000000011111111
  26  0000001111111111 0000000111111111
  27  0000001111111111 0000000111111111
  28  0000001111111111 0000000111111111
  29  0000011111111111 0000001111111111
  30  0000011111111111 0000001111111111
  31  0000011111111111 0000001111111111
  32  0000111111111111 0000011111111111
  33  0000111111111111 0000011111111111
  34  0000111111111111 0000011111111111
  35  0001111111111111 0000111111111111
  36  0001111111111111 0000111111111111
  37  0001111111111111 0000111111111111
  38  0011111111111111 0001111111111111
  39  0011111111111111 0001111111111111
  40  0011111111111111 0001111111111111
  41  0111111111111111 0011111111111111
  42  0111111111111111 0011111111111111
  43  0111111111111111 0011111111111111
  44  1111111111111111 0111111111111111
  45  1111111111111111 0111111111111111
  46  1111111111111111 0111111111111111
  47  1111111111111111 1111111111111111
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_bit();
    test_bits();
    test_simulate();
    test_register();
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    test_register();
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
