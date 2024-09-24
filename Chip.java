//------------------------------------------------------------------------------
// Simulate a silicon chip with memory, bits and gates.
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Simulate a silicon chip.

import java.util.*;

//D1 Construct                                                                  // Construct a silicon chip comprised of memory, intermediate bits and logic gates.

public class Chip extends Test                                                  // Describe a chip and emulate its operation.  A chip consists of a block of memory and some logic that computes the next value of the memory thus changing the state of the chip. A chip is testable.
 {final String  name;                                                           // Name of chip
  final Bits   input;                                                           // The logic computed input bits used to load the memory of the chip
  final Bits  output;                                                           // The output bits representing the current state of the memory
  final int maxSimulationSteps = 1_0;                                           // Maximum number of simulation steps
  final Map<String,Bit>    bit = new TreeMap<>();                               // Bits on the chip
  final Map<String,Gate>  gate = new TreeMap<>();                               // Gates on the chip
  final Layout          layout;                                                 // Layout of the memory used  by the chip

  int                     time = 0;                                             // Number of  steps in time.  We start at time 0 and  continue to increment through subsequent simulations
  int    changedBitsInLastStep = 0;                                             // Changed bits in last step
  Trace trace;                                                                  // Tracing

  Chip(String Name)                                                             // Create a new L<chip>  with the specified memory layout
   {if (Name == null) Name = currentTestNameSuffix();                           // Use the current test name as the chip name if none has been supplied
    name   = Name;                                                              // Name of chip
    layout = layout();                                                          // Layout of memory used by the chip into variables arranged in arrays, structures and unions
    if (layout != null) layout.layout();                                        // Position each element in memory
    final int width = layout != null ? layout.width : 0;                        // Width of memory in bits required for the memory layout supplied
    input  = bits(Name, "Input" , width);                                       // Input to memory once the chip has stabilized after a sufficient number of steps
    output = bits(Name, "Output", width);                                       // Current output of memory
    for (Bit b : output) b.set(false);                                          // Clear memory to zero
   }

  Chip() {this(null);}                                                          // Create a new L<chip>  named after the current test with the specified memory layout

  static Chip chip()            {return new Chip();}                            // Chip named after the test
  static Chip chip(String name) {return new Chip(name);}                        // Create a new chip while testing.

  Layout layout() {return null;}                                                // Override to provide the layout of the memory used by the chip

  void update()                                                                 // Update the memory associated with the chip once the chips bits have stabilized
   {final int N = input.size();                                                 // Size of memory in bits
    for (int j = 0; j < N; ++j)                                                 // Copy memory input bits into memory and present them on the output bits
     {output.elementAt(j).set(input.elementAt(j).get());
     }
   }

  public String toString()                                                      // Print a description of the state of the chip
   {final StringBuilder s = new StringBuilder();
    s.append("Time: " + time + ", Chip:" + name);                               // Time and chip name form the title

    int i = 0;                                                                  // Number each bit
    for (Bit b : bit.values())                                                  // Each bit
     {final Boolean v = b.get();                                                // Value of bit
      s.append(String.format("%4d %16s %s", ++i,                                // Print bit name and value
        b.name, v == null ? "." : v ? "1" : "0"));
     }
    return s.toString();                                                        // Description of chip at this time
   }

//D1 Layouts                                                                    // Layout memory of the chip as variables, arrays, structures, unions. Dividing the memory in this manner makes it easier to program the chip symbolically.

  abstract class Layout                                                         // Variable/Array/Structure definition.
   {final String name;                                                          // Name of field
    int at;                                                                     // Offset of variable from start of memory
    int width;                                                                  // Number of bits in a field
    int depth;                                                                  // Depth of field - the number of containing arrays/structures/unions above
    Layout up;                                                                  // Upward chain to containing array/structure/union
    Layout superStructure;                                                      // Containing super structure
    final Stack<Layout> fields = new Stack<>();                                 // Fields in the super structure in the order they appear in the memory layout. Only relevant in the outer most layout == the super structure,  where it is used for printing the structure and locating sub structures.

    Layout(String Name) {name = Name;}                                          // Create a new named memory layout

    Layout width   (int Width) {width = Width; return this;}                    // Set width or layout once it is known
    Layout position(int At)    {at    = At;    return this;}                    // Reposition array elements to take account of the index applied to the array

    int at()   {return at;}                                                     // Position of field in memory
    int memorySize() {return width;}                                            // Size of the memory

    Layout layout()                                                             // Layout the structure based on the fields that describe it
     {fields.clear();                                                           // Clear of chain of fields in this layout
      layout(0, 0, this);                                                       // Compute field positions
      for(Layout l : fields) l.superStructure = this;                           // Locate the super structure containing this field
      return this;
     }

    abstract void layout(int at, int depth, Layout superStructure);             // Layout this field within the super structure.

    String indent() {return "  ".repeat(depth);}                                // Indentation

    String printEntry()                                                         // Print the memory layout header
     {return String.format("%4d  %4d        %s  %s", at, width, indent(), name);
     }

    String print()                                                              // Walk the field list printing the memory layout headers
     {if (fields == null) return "";                                            // The structure has not been laid out
      final StringBuilder b = new StringBuilder();
      b.append(String.format("%4s  %4s  %4s    %s",
                             "  At", "Wide", "Size", "Field name\n"));
      for(Layout l : fields) b.append(""+l.printEntry()+"\n");                  // Print all the fields in the structure layout
      return b.toString();
     }

    public String toString()                                                    // Part of memory corresponding to this layout as a string of bits in low endian order
     {final StringBuilder s = new StringBuilder();
      for (int i = 0; i <  width; ++i)                                          // Index each bit
       {final Bit     b = output.elementAt(at+i);                               // Bit definition
        final Boolean v = b.get();                                              // Value of bit
        s.append(v == null ? '.' : v ? '1' : '0');                              // Represent bit
       }
      return s.reverse().toString();                                            // Prints string with lowest bit rightmost because we work in little endian layout
     }

    Integer toInt()                                                             // Get an integer representing the value of the layout
     {int n = 0;
      for (int i = 0; i < width; ++i)
       {final Bit     b = output.elementAt(at+i);
        final Boolean v = b.get();
        if (v == null) return null;                                             // One of the bits is null so the overall value is no longer known
        if (v && i > Integer.SIZE-1) return null;                               // Value is too big to be represented
        n += v ? 1<<i : 0;
       }
      return n;                                                                 // Valid representation of bits as an integer
     }

    Layout getField(String path)                                                // Locate a field from its path which must include the outer most element
     {final String[]names = path.split("\\.");                                  // Split path
      if (fields == null) return null;                                          // Not compiled
      search: for (Layout m : fields)                                           // Each field in structure
       {Layout p = m;                                                           // Start at this field and try to match the path
        for(int q = names.length; q > 0 && p != null; --q, p = p.up)            // Back track through names
         {if (!p.name.equals(names[q-1])) continue search;                      // Check path matches
         }
        return m;                                                               // Return this field as its path matches
       }
      return null;                                                              // No matching path
     }

    Bits input()                                                                // Get the input bits mapped by the layout
     {final Bits b = new Bits();
      for (int i = 0; i < width; i++) b.push( input.elementAt(at + i));
      return b;
     }

    Bits output()                                                               // Get the output bits mapped by the layout
     {final Bits b = new Bits();
      for (int i = 0; i < width; i++) b.push(output.elementAt(at + i));
      return b;
     }

    abstract Layout duplicate(int At);                                          // Duplicate an element of this layout so we can modify it safely

    Layout duplicate()                                                          // Duplicate a set of nested layouts rebasing their start point to zero
     {final Layout l = duplicate(at);                                           // Duplicate the layout
      l.layout();                                                               // Layout the structure - it now has its own memory from layout. Its easier to do this than duplicate all of layout logic during duplication and iyt only costs an unnecessary memory allocatc
      return l;
     }

    void ok(int expected) {Chip.ok(toInt(), expected);}                         // Check the value of a variable
   }

  class Variable extends Layout                                                 // Variable
   {Variable(String name, int Width)
     {super(name); width(Width);
     }
    void layout(int At, int Depth, Layout superStructure)                       // Layout the variable in the structure
     {at = At; depth = Depth; superStructure.fields.push(this);
     }
    Layout duplicate(int At)                                                    // Duplicate a variable so we can modify it safely
     {final Variable v = new Variable(name, width);
      v.at = at - At; v.depth = depth; //v.bits = bits;
      return v;
     }
   }

  class Array extends Layout                                                    // Array definition.
   {int size;                                                                   // Dimension of array
    int index = 0;                                                              // Index of array element to access
    Layout element;                                                             // The elements of this array

    Array(String Name, Layout Element, int Size)
     {super(Name);
      arraySize(Size).element(Element);
     }

    Array arraySize(int Size)     {size    = Size;    return this;}             // Set the size of the array
    Array element(Layout Element) {element = Element; return this;}             // The type of the element in the array
    int at(int i)                 {return at+i*element.width;}                  // Offset of this array element in the structure

    void layout(int At, int Depth, Layout superStructure)                       // Compile this variable so that the size, width and byte fields are correct
     {depth = Depth; superStructure.fields.push(this);                          // Relative to super structure
      element.layout(At, Depth+1, superStructure);                              // Layout sub structure
      position(at);                                                             // Position on index
      element.up = this;                                                        // Chain up to containing parent layout
      width = size * element.width;
     }

    Layout position(int At)                                                     // Reposition an array
     {at = At;
      element.position(at + index * element.width);
      return this;
     }

    String printEntry()                                                         // Print the field
     {return String.format("%4d  %4d  %4d  %s  %s",
                            at, width, size, indent(), name);
     }

    void setIndex(int Index)                                                    // Sets the index for the current array element allowing us to set and get this element and all its sub elements.
     {if (index != Index)
       {index = Index; position(at);
       }
     }

    Layout duplicate(int At)                                                    // Duplicate an array so we can modify it safely
     {final Array a = new Array(name, element.duplicate(), size);
      a.width = width; a.at = at - At; a.depth = depth; a.index = index;
      return a;
     }
   }

  class Structure extends Layout                                                // Structure laid out in memory
   {final Map<String,Layout> subMap   = new TreeMap<>();                        // Unique variables contained inside this variable
    final Stack     <Layout> subStack = new Stack  <>();                        // Order of variables inside this variable

    Structure(String Name, Layout...Fields)                                     // Fields in the structure
     {super(Name);
      for (int i = 0; i < Fields.length; ++i) addField(Fields[i]);              // Each field in this structure
     }

    void addField(Layout Field)                                                 // Add additional fields
     {Field.up = this;                                                          // Chain up to containing structure
      if (subMap.containsKey(Field.name))
       {stop("Structure already contains field with this name",
             name, Field.name);
       }
      subMap.put (Field.name, Field);                                           // Add as a sub structure by name
      subStack.push(Field);                                                     // Add as a sub structure in order
     }

    void layout(int at, int Depth, Layout superStructure)                       // Compile this variable so that the size, width and byte fields are correct
     {width = 0;
      depth = Depth;
      superStructure.fields.push(this);
      for(Layout v : subStack)                                                  // Layout sub structure
       {v.at = at+width;
        v.layout(v.at, Depth+1, superStructure);
        width += v.width;
       }
     }

    Layout position(int At)                                                     // Reposition this structure to allow access to array elements via an index
     {at = At;
      int w = 0;
      for(Layout v : subStack)                                                  // Layout sub structure
       {v.position(v.at = at+w);                                                // Substructures are laid out sequentially
        w += v.width;
       }
      return this;
     }

    Layout duplicate(int At)                                                    // Duplicate a structure so we can modify it safely
     {final Structure s = new Structure(name);
      s.width = width; s.at = at - At; s.depth = depth;
      for(Layout L : subStack)
       {final Layout l = L.duplicate(L.at);
        s.subMap.put(l.name, l);
        s.subStack.push(l);
       }
      return s;
     }
   }

  class Union extends Layout                                                    // Union of structures laid out in memory on top of each other - it is up to you to have a way of deciding which substructure is valid
   {final Map<String,Layout> subMap = new TreeMap<>();                          // Unique variables contained inside this variable

    Union(String Name, Layout...Fields)                                         // Fields in the union
     {super(Name);
      for (int i = 0; i < Fields.length; ++i) addField(Fields[i]);              // Each field in this union
     }

    void addField(Layout Field)                                                 // Add a field to the union
     {final String n = Field.name;
      Field.up = this;                                                          // Chain up to containing structure
      if (subMap.containsKey(n)) stop(name, "already contains", n);
      subMap.put (n, Field);                                                    // Add as a sub structure by name
     }

    void layout(int at, int Depth, Layout superStructure)                       // Compile this variable so that the size, width and byte fields are correct
     {width = 0;
      depth = Depth;
      superStructure.fields.push(this);
      for(Layout v : subMap.values())                                           // Find largest substructure
       {v.at = at;                                                              // Substructures are laid out on top of each other
        v.layout(v.at, Depth+1, superStructure);
        width = max(width, v.width);                                            // Space occupied is determined by largest element of union
       }
     }

    Layout position(int At)                                                     // Position elements of this union to allow arrays to access their elements by an index
     {at = At;
      for(Layout v : subMap.values()) v.position(at);
      return this;
     }

    Layout duplicate(int At)                                                    // Duplicate a union so we can modify it safely
     {final Union u = new Union(name);
      u.width = width; u.at = at - At; u.depth = depth;
      for(String s : subMap.keySet())
       {final Layout L = subMap.get(s);
        final Layout l = L.duplicate(L.at);
        u.subMap.put(l.name, l);
       }
      return u;
     }
   }

  Variable  variable (String name, int width)              {return new Variable (name, width);}
  Array     array    (String name, Layout   ml, int width) {return new Array    (name, ml, width);}
  Structure structure(String name, Layout...ml)            {return new Structure(name, ml);}
  Union     union    (String name, Layout...ml)            {return new Union    (name, ml);}

// Name                                                                         // Elements of the chip have names which are part alpabetic and part numeric.  Components of a name are separated by underscores

  abstract class Name                                                           // Names of elements on the chip
   {final String name;                                                          // Name of the element

    Name(Name name) {this(name.toString());}                                    // The output bit of a gate can be used as the name of the gate

    Name(String Name)                                                           // Single name
     {name = validateName(Name);
      put();
     }
    Name(String Name, int index)                                                // Named and numbered Name
     {name = validateName(Name)+"_"+index;
      put();
     }
    Name(String Name1, String Name2)                                            // Double name
     {name = validateName(Name1)+"_"+validateName(Name2, false);
      put();
     }
    Name(String Name1, String Name2, int index)                                 // Double name with index
     {name = validateName(Name1)+"_"+validateName(Name2, false)+"_"+index;
      put();
     }

    String validateName(String name, boolean addChipName)                       // Confirm that a component name looks like a variable name and has not already been used
     {if (name == null) err("Name cannot be null");
      final String[]words = name.split("_");
      for (int i = 0; i < words.length; i++)
       {final String w = words[i];
        if (!w.matches("\\A([a-zA-Z][a-zA-Z0-9_.:]*|\\d+)\\Z"))
          stop("Invalid gate name:", name, "in word", w);
       }
      return addChipName ? Chip.this.name+"."+name : name;                      // Add chip name if requested
     }

    String validateName(String name) {return validateName(name, true);}         // Confirm that a component name looks like a variable name and has not already been used

    abstract void put();                                                        // Put the element into the map of existing elements if possible
    public String toString()      {return name;}                                // Name  as a string
   }

// Bit                                                                          // Description of a bit. Bits are points to which gates conbnect.  If multiple gates connect to the same bit their values are combined with "or".

  class Bit extends Name                                                        // Bits are connected by gates.  Each bit has a name.
   {final Set<Gate> drivenBy = new TreeSet<>();                                 // Driven by these gates. If a bit is driven by multiple gates we assume that the inputs are combined using "or"
    final Set<Gate> drives   = new TreeSet<>();                                 // Drives these gates. The order of the input pins for a gate matters  so this can only be used to find which gates drive this bit but the actual pin will have to be found by examining the gate.
    Boolean value;                                                              // The currently set value of the bit computed as the or of the values of its driving gates
    Integer changed;                                                            // The last step at which this bit was changed

    Bit(String name)                           {super(name);}                   // Named bit
    Bit(String name,                int index) {super(name, index);}            // Named and numbered bit
    Bit(String name1, String name2)            {super(name1, name2);}           // Named bit within a named bit
    Bit(String name1, String name2, int index) {super(name1, name2, index);}    // Named and numbered bit within a named bit

    void put()                                                                  // Add a bit to the bit map
     {if (bit.containsKey(name)) stop("Bit", name,
       "has already been already defined");
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

    Boolean get()              {return value;}                                  // Current value of a bit

    void set(Boolean Value)                                                     // Set the current value of the bit.
     {if (Value != null && value != null)                                       // Both states are known
       {if (Value != value)                                                     // Changed definitely at this time
         {value   = Value;
          changed = time;
          changedBitsInLastStep++;
         }
       }
      else                                                                      // At least one state is unknown
       {value   = Value;                                                        // What ever the new value is
        changed = null;                                                         // We do not know if it changed or not
        changedBitsInLastStep++;                                                // It might have changed so we should keep going
       }
     }

    void ok(Boolean expected) {Test.ok(value, expected);}                       // Conform a bit has the expected value

    public String toString()                                                    // Print the name and value of a bit
     {return name+(value == null ? "" : value ? "=1": "=0");
     }
   }

  Bit bit(String Name)                 {return new Bit(Name);}                  // Create a new named bit
  Bit bit(String Name, int Index)      {return new Bit(Name, Index);}           // Create a new named, indexed, bit
  Bit bit(String N1, String N2)        {return new Bit(N1, N2);}                // Create a new double named bit
  Bit bit(String N1, String N2, int I) {return new Bit(N1, N2, I);}             // Create a new double named, indexed bit

// Bits                                                                         // Description of a collection of bits

  class Bits extends Stack<Bit>                                                 // Collection of bits in order so we can operate on them enmass
   {private static final long serialVersionUID = 1L;                            // Some weird Java ceremony we would be better off without

    Bits() {}                                                                   // Create an empty collection of bits that behaves like a stack
    Bits(Bit...Bits) {for (int i = 0; i < Bits.length; i++) push(Bits[i]);}     // Create an collection of bits that behaves like a stack

    Bits(String name, int width)                                                // Create an array of bits with an indexed name
     {for (int i = 0; i < width; i++) push(bit(name, i));
     }

    Bits(String name1, String name2, int width)                                 // Create an array of bits with an indexed double name
     {for (int i = 0; i < width; i++) push(bit(name1, name2, i));
     }

    Boolean getValue(int index) {return elementAt(index).get();}                // Get the value of an indexed bit in the collection of bits

    Integer toInt()                                                             // Get an integer representing the value of the bits
     {int i = 0, n = 0;
      final int N = size();
      for (Bit b : this)
       {final Boolean v = b.get();
        if (v == null) return null;                                             // One of the bits is null so the overall value is no longer known
        if (v && i > Integer.SIZE-1) return null;                               // Value is too big to be represented
        n += v ? 1<<(i++) : 0;                                                  // Set the corresponding bit in the result if possible and requested
       }
      return n;                                                                 // Valid representation of bits as an integer
     }

    public String toString()                                                    // Convert the bits represented by an output bus to a string
     {final StringBuilder s = new StringBuilder();
      for (Bit b : this)
       {final Boolean v = b.get();
        s.append(v == null ? '.' : v ? '1' : '0');
       }
      return s.reverse().toString();                                            // Prints string with lowest bit rightmost
     }

    void ok(String expected) {Test.ok(toString(), expected);}                   // Expected value of the bits

    int sameSize(Bits b)                                                        // Check the specified set of bits is the same size as this one
     {final int A = size(), B = b.size();
      if (A != B) stop("This set of bits has width", A,
         "but the set of input bits has width", B);
      return A;
     }

    void ones()  {for(Bit b : this) new One(b);}                                // Drive these bits with some ones
    void zeros() {for(Bit b : this) new Zero(b);}                               // Drive these bits with some zeros

    void shiftLeftOnce(Bits toShift, boolean fill)                              // Attach to these bits the left shift by one filled with the specified replacement bit
     {final int  N = sameSize(toShift);
      final Gate g = fill ? new One(elementAt(0)) : new Zero(elementAt(0));
      for (int i = 1; i < N; i++)
       {new Continue(elementAt(i), toShift.elementAt(i-1));
       }
     }

    void shiftLeftOnceByOne (Bits toShift) {shiftLeftOnce(toShift, true);}      // Attach to these bits the right shift by one filled with one  of the supplied bits
    void shiftLeftOnceByZero(Bits toShift) {shiftLeftOnce(toShift, false);}     // Attach to these bits the right shift by one filled with zero of the supplied bits

    void shiftRightOnce(Bits toShift, boolean fill)                             // Attach to these bits the right shift by one filled with the specified replacement bit
     {final int  N = sameSize(toShift);
      final Gate g = fill ? new One(elementAt(N-1)) : new Zero(elementAt(N-1));
      for (int i = 1; i < N; i++)
       {new Continue(elementAt(i-1), toShift.elementAt(i));
       }
     }

    void shiftRightOnceByOne (Bits toShift) {shiftRightOnce(toShift, true);}    // Attach to these bits the right shift by one filled with one  of the supplied bits
    void shiftRightOnceByZero(Bits toShift) {shiftRightOnce(toShift, false);}   // Attach to these bits the right shift by one filled with zero of the supplied bits

    void shiftRightOnceArithmetic(Bits toShift)                                 // Attach to these bits the right arithmetic shift by one
     {final int N = sameSize(toShift);
      for (int i = 1; i < N; i++)
       {new Continue(elementAt(i-1), toShift.elementAt(i));
       }
      new Continue(elementAt(N-1), toShift.elementAt(N-1));
     }

    void enable(Bits in, Bit enable)                                            // Set these bits to the value of the input bits if the enable flag is true else set them to false.
     {final int N = sameSize(in);
      for (int i = 0; i < N; i++)
       {new And(elementAt(i), in.elementAt(i), enable);
       }
     }

    void and(Bits...in)                                                         // Set these bits to the value obtained by "anding" corresponding input bits horizontallythrough the vertical of the inputs
     {final int L = size(), N = in.length;                                      // Dimensions
      for (int i = 0; i < N; i++) sameSize(in[i]);                              // Check each input has the same size as these bits
      for (int i = 0; i < L; i++)                                               // Combine horizontally
       {final  Bit[]b = new Bit[N];                                             // Bits to combine
        for (int j = 0; j < N; j++) b[j] = in[j].elementAt(i);                  // Combine horizontally
        new And(elementAt(i), b);                                               // Perform combination
       }
     }

    void or(Bits...in)                                                          // Set these bits to the value obtained by "oring" corresponding input bits horizontallythrough the vertical of the inputs
     {final int L = size(), N = in.length;                                      // Dimensions
      for (int i = 0; i < N; i++) sameSize(in[i]);                              // Check each input has the same size as these bits
      for (int i = 0; i < L; i++)                                               // Combine horizontally
       {final  Bit[]b = new Bit[N];                                             // Bits to combine
        for (int j = 0; j < N; j++) b[j] = in[j].elementAt(i);                  // Combine horizontally
        new Or(elementAt(i), b);                                                // Perform combination
       }
     }
   }

  Bits bits(Bit...bits) {return new Bits(bits);}                                // Make a collection of bits from the supplied individual bits

  Bits bits(String name, int width) {return new Bits(name, width);}             // Make an array of named indexed bits

  Bits bits(String name1, String name2, int width)                              // Make an array of doubke named  indexed bits
   {return new Bits(name1, name2, width);
   }

// Gate                                                                         // Gates connect and combine bits with well known logical operations

  class Gate extends Name implements Comparable<Gate>                           // Gates combine input bits to produce an out value which is used to drive a bit
   {final Bit  output;                                                          // A gate drives one output bit
    final Bits inputs;                                                          // A gate is driven by zero or more input bits whose order matters
    Boolean value;                                                              // The next value of the gate which drives the output bit
    Gate(Bit Output, Bit...Inputs)                                              // A gate has one output and several input bits
     {super(Output);                                                            // Use the name of the output bit as the name of the gate as well
      output = Output;
      inputs = new Bits(Inputs);
      output.drivenBy.add(this);
      for (Bit i : inputs) i.drives.add(this);
     }

    void action() {}                                                            // Override this method to specify how the gate works

    void put()                                                                  // Add to gate map
     {if (gate.containsKey(name)) stop("Gate", name,
        "has already been already defined");
      gate.put(name, this);
     }

    void set(Boolean Value) {value = Value;}                                    // Set the next value of the gate
    public int compareTo(Gate a)  {return toString().compareTo(a.toString());}  // Gates can be added to sets

    public String toString()                                                    // Print a gate
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
    void action() {set(inputs.firstElement().get());}                           // Set the value of the gate, not the target bit. The bit is responsible for examining its inputs and deciding what its actual value will be
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
      set(false);                                                               // No true or null inputs means it was false
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

// Simulate                                                                     // Simulate the elements comprising the chip

  void simulate()                                                               // Simulate the elements comprising the chip
   {for (int i = 0; i < maxSimulationSteps; ++time, i++)                        // Each step in time
     {for (Gate g: gate.values()) g.action();                                   // Each gate computes its result
      changedBitsInLastStep = 0;                                                // Number of changes to bits
      for (Bit  b: bit.values())  b.update();                                   // Each bit updates itself from the gates it is driven by
      if (trace != null) trace.add();                                           // Trace this step if requested
      if (changedBitsInLastStep == 0)                                           // If nothing has changed then the chip has stabilized and the simulation has come to an end
       {//say("Finished at step", time, "after no activity");
        update();                                                               // Update the memory associated with the chip with the values computed by the gates of the chip
        return;
       }
      //print();
     }
    err("Finished at maximum simulation step", maxSimulationSteps);
   }

//D1 Tracing                                                                    // Trace simulation

  abstract class Trace                                                          // Trace simulation
   {final Stack<String> trace = new Stack<>();                                  // Execution trace

    void add() {trace.push(trace());}                                           // Add a trace record

    abstract String title();                                                    // Title of trace
    abstract String trace();                                                    // Trace at each step

    String print()                                                              // Print execution trace
     {final StringBuilder b = new StringBuilder();
      b.append("Step  "+title()); b.append('\n');
      b.append(String.format("%4d  %s\n", 1, trace.firstElement()));
      for(int i = 1; i < trace.size(); ++i)
       {final String s = trace.elementAt(i-1);
        final String t = trace.elementAt(i);
        b.append(String.format("%4d  %s\n", i+1, trace.elementAt(i)));
       }
      return b.toString();
     }

    void ok(String expected) {Test.ok(print(), expected);}                      // Confirm expected trace
   }

  String printTrace  () {return trace.print();}                                 // Print execution trace

//D0                                                                            // Tests: I test, therefore I am.  And so do my mentees.  But most other people, apparently, do not, they live in a half world lit by shadows in which they never know if their code works or not.

  static void test_bit()
   {Chip c = chip();
    Bit  a = c.bit("a", 1);
    Bit  b = c.bit("a", "b", 1);
    ok(a, "bit.a_1");
    ok(b, "bit.a_b_1");
   }

  static void test_bits()
   {Chip c = chip();
    Bit  a = c.bit("a", 1);
    Bit  b = c.bit("a", "b", 1);
    Bits B = c.bits(a, b);
    ok(B.size(), 2);
   }

  static void test_enable_bits()
   {final int N = 2;
    Chip c = chip();
    Bits a = c.bits("i", N); a.ones();
    Bits b = c.bits("j", N);
    Bit  e = c.bit("enable");
         b.enable(a, e);

    e.set(false);
    c.simulate();
    b.ok("00");

    e.set(true);
    c.simulate();
    b.ok("11");
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
    Chip       c = new Chip() {Layout layout() {return variable("memory", N);}};

    c.input.shiftLeftOnceByOne(c.output);
    c.trace = c.new Trace()
     {String title() {return "Input            Output";}
      String trace() {return String.format("%s %s", c.input, c.output);}
     };
    for (int i = 0; i < N; i++) c.simulate();
    //say(c.printTrace());
    c.trace.ok("""
Step  Input            Output
   1  0000000000000001 0000000000000000
   2  0000000000000001 0000000000000000
   3  0000000000000011 0000000000000001
   4  0000000000000011 0000000000000001
   5  0000000000000111 0000000000000011
   6  0000000000000111 0000000000000011
   7  0000000000001111 0000000000000111
   8  0000000000001111 0000000000000111
   9  0000000000011111 0000000000001111
  10  0000000000011111 0000000000001111
  11  0000000000111111 0000000000011111
  12  0000000000111111 0000000000011111
  13  0000000001111111 0000000000111111
  14  0000000001111111 0000000000111111
  15  0000000011111111 0000000001111111
  16  0000000011111111 0000000001111111
  17  0000000111111111 0000000011111111
  18  0000000111111111 0000000011111111
  19  0000001111111111 0000000111111111
  20  0000001111111111 0000000111111111
  21  0000011111111111 0000001111111111
  22  0000011111111111 0000001111111111
  23  0000111111111111 0000011111111111
  24  0000111111111111 0000011111111111
  25  0001111111111111 0000111111111111
  26  0001111111111111 0000111111111111
  27  0011111111111111 0001111111111111
  28  0011111111111111 0001111111111111
  29  0111111111111111 0011111111111111
  30  0111111111111111 0011111111111111
  31  1111111111111111 0111111111111111
  32  1111111111111111 0111111111111111
""");
   }

  static void test_shift()
   {final int  N = 4;
    Chip       c = new Chip() {Layout layout() {return variable("memory", N);}};
    Bits      b1 = c.bits("b1", N); b1.shiftLeftOnceByOne (c.output);
    Bits      b2 = c.bits("b2", N); b2.shiftLeftOnceByZero(b1);
    Bits      b3 = c.bits("b3", N); b3.shiftLeftOnceByOne (b2);
    Bits      b4 = c.bits("b4", N); b4.shiftLeftOnceByZero(b3);
    c.input.shiftRightOnceArithmetic(b4);
    c.trace = c.new Trace()
     {String title() {return "Out  b1   b2   b3   b4   In";}
      String trace() {return String.format("%s %s %s %s %s %s", c.output, b1, b2, b3, b4, c.input);}
     };
    for (int i = 0; i < N; i++) c.simulate();
    ok(c.layout, "1101");
    ok(c.output, "1101");
    //say(c.printTrace());
    c.trace.ok("""
Step  Out  b1   b2   b3   b4   In
   1  0000 0001 ...0 ...1 ...0 ....
   2  0000 0001 0010 ..01 ..10 ....
   3  0000 0001 0010 0101 .010 ...1
   4  0000 0001 0010 0101 1010 ..01
   5  0000 0001 0010 0101 1010 1101
   6  0000 0001 0010 0101 1010 1101
   7  1101 1011 0010 0101 1010 1101
   8  1101 1011 0110 0101 1010 1101
   9  1101 1011 0110 1101 1010 1101
  10  1101 1011 0110 1101 1010 1101
  11  1101 1011 0110 1101 1010 1101
  12  1101 1011 0110 1101 1010 1101
""");
   }

  static void test_double_shift()
   {final int  N = 4;
    Chip       c = new Chip()
     {Layout layout()
       {final Variable  a = variable ("a", N);
        final Variable  b = variable ("b", N);
        return              structure("c", a, b);
       }
     };
    ok(c.layout.print(), """
  At  Wide  Size    Field name
   0     8          c
   0     4            a
   4     4            b
""");
    Bits ai = c.layout.getField("a").input ();
    Bits ao = c.layout.getField("a").output();
    Bits bi = c.layout.getField("b").input ();
    Bits bo = c.layout.getField("b").output();
    Bits a  = c.bits("a", N);
    Bits b  = c.bits("b", N);
    a.shiftLeftOnceByOne (ao); ai.shiftLeftOnceByZero(a);
    b.shiftLeftOnceByZero(bo); bi.shiftLeftOnceByOne (b);
    c.trace = c.new Trace()
     {String title() {return "ao   bo   a    b    ai   bi";}
      String trace() {return String.format("%s %s %s %s %s %s", ao, bo, a, b, ai, bi);}
     };
    for (int i = 0; i < N; i++) c.simulate();
    ao.ok("1010");
    a .ok("0101");
    ai.ok("1010");
    bo.ok("0101");
    b .ok("1010");
    bi.ok("0101");
    ok(c.layout, "01011010");
    ok(c.output, "01011010");
    //c.printTrace(); stop();
    c.trace.ok("""
Step  ao   bo   a    b    ai   bi
   1  0000 0000 0001 0000 ...0 ...1
   2  0000 0000 0001 0000 0010 0001
   3  0000 0000 0001 0000 0010 0001
   4  0010 0001 0101 0010 0010 0001
   5  0010 0001 0101 0010 1010 0101
   6  0010 0001 0101 0010 1010 0101
   7  1010 0101 0101 1010 1010 0101
   8  1010 0101 0101 1010 1010 0101
   9  1010 0101 0101 1010 1010 0101
""");
   }

  static void test_and_bits()
   {final int N = 4;
    Chip c = chip();
    Bits a = c.bits("a", N); a.ones();
    Bits b = c.bits("b", N); b.shiftLeftOnceByZero(a);
    Bits d = c.bits("d", N); d.shiftLeftOnceByZero(b);
    Bits e = c.bits("e", N);
         e.and(a, d);
    c.simulate();
    e.ok("1100");
   }

  static void test_or_bits()
   {final int N = 4;
    Chip c = chip();
    Bits a = c.bits("a", N); a.ones();
    Bits b = c.bits("b", N); b.shiftLeftOnceByZero(a);
    Bits d = c.bits("d", N); d.shiftLeftOnceByZero(b);
    Bits e = c.bits("e", N);
         e.or(a, d);
    c.simulate();
    e.ok("1111");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_bit();
    test_bits();
    test_simulate();
    test_register();
    test_shift();
    test_double_shift();
    test_enable_bits();
    test_and_bits();
    test_or_bits();
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
