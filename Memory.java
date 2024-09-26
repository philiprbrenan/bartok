//------------------------------------------------------------------------------
// Bit memory laid out as variables, arrays, structures, and unions
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Simulate a silicon chip.

import java.util.*;

//D1 Construct                                                                  // Layout a description of the powered memory used by a chip

public class Memory extends Test                                                // Describe a chip and emulate its operation.  A chip consists of a block of memory and some logic that computes the next value of the memory thus changing the state of the chip. A chip is testable.
 {final String  name;                                                           // Name of layout
  Field top;                                                                    // The top most field in a set of nested fields
  final Stack<     Field> fields    = new Stack<>();                            // Fields in the layout in in-order
  final Map<String,Field> fullNames = new TreeMap<>();                          // Fields by name

  Memory(String Name)                                                           // Create a new Layout loaded from a set of definitions
   {name = Name;                                                                // Name of layout
    top  = define();
    if (top != null) top.layout(0, 0, null);                                    // Locate field positions
    indexNames();                                                               // Index the names of the fields
   }

  Field define() {return null;};                                                // Use variables, arrays, structures, union to define the layout and return its upper most field

  Field get(String path) {return fullNames.get(path);}                          // Locate a field from its full name path which must include the outer most field

  Field get3(String path)                                                        // Locate a field from its path which must include the outer most field
   {final String[]names = path.split("\\.");                                    // Split path
    search: for (Field m : fields)                                              // Each field in structure
     {Field p = m;                                                              // Start at this field and try to match the path
      for(int q = names.length; q > 0 && p != null; --q, p = p.up)              // Back track through names
       {if (!p.name.equals(names[q-1])) continue search;                        // Check path matches
       }
      return m;                                                                 // Return this field as its path matches
     }
    return null;                                                                // No matching path
   }

  Memory duplicate()                                                            // Duplicate a layout
   {final Memory d = new Memory(name);
    if (top == null) return d;                                                  // No fields to duplicate
    d.top = top.duplicate(d);                                                   // Copy each field into this layout
    d.top.order();                                                              // Order the fields
    d.indexNames();                                                             // Index the names of the fields
    return d;
   }

  public String toString()                                                      // Walk the field tree in in-order printing the memory field headers
   {final StringBuilder b = new StringBuilder();
    b.append(String.format("%1s %4s  %4s  %4s    %8s   %s\n",
                             "T", "At", "Wide", "Size", "Value", "Field name"));
    for(Field f : fields) b.append(f.toString());                               // Print all the fields in the structure field
    return b.toString();
   }

  void indexNames() {for(Field f : fields) fullNames.put(f.fullName(), f);}     // Index field names

  int size() {return top == null ? 0 : top.width;}                              // Size of memory

  void ok(String expected) {Test.ok(toString(), expected);}                     // Confirm layout is as expected

//D1 Bits                                                                       // A memory is implemented using bits

  Boolean get(int i) {return null;}                                             // Get a bit
  void    set(int i, Boolean value) {}                                          // Set a bit

//D1 Layouts                                                                    // Field memory of the chip as variables, arrays, structures, unions. Dividing the memory in this manner makes it easier to program the chip symbolically.

  abstract class Field                                                          // Variable/Array/Structure/Union definition.
   {final String name;                                                          // Name of field
    int at;                                                                     // Offset of field from start of memory
    int width;                                                                  // Number of bits in a field
    int depth;                                                                  // Depth of field - the number of containing arrays/structures/unions above
    Field up;                                                                   // Upward chain to containing array/structure/union

    Field(String Name) {name = Name;}                                           // Create a new named field

    int at   () {return at;}                                                    // Position of field in memory
    int width() {return width;}                                                 // Size of the memory in bits occupied by this field

    String fullName()                                                               // The full name of a field
     {final StringBuilder b = new StringBuilder();
      final Stack<String> n = new Stack<>();
      for(Field p = this; p != null; p = p.up) n.push(p.name);
      for(;n.size() > 0;) b.append("."+n.pop());
      final String r = b.toString().substring(1);
      return r;
     }

    abstract void layout(int at, int depth, Field superStructure);              // Layout this field
    abstract void order();                                                      // Add this field to the tree of fields

    void position(int At) {at = At;}                                            // Reposition a field after an index of a containing array has been changed

    String indent() {return "  ".repeat(depth);}                                // Indentation during printing

    public String toString()                                                    // Print the field
     {final Integer v = asInt();
      final String  i = v == null ? "" : String.format("%10d", v);              // Format value of field if available
      final String  n = indent()+name;
      final String[]c = getClass().getName().split("\\$");                      // Class name
      return String.format("%c %4d  %4d        %10s   %s\n",
                           c[1].charAt(0),  at, width, i, n);
     }

    abstract Field duplicate(Memory d);                                         // Duplicate an field of this field so we can modify it safely

    Variable  toVariable () {return (Variable) this;}                           // Try to convert to a variable
    Array     toArray    () {return (Array)    this;}                           // Try to convert to an array
    Structure toStructure() {return (Structure)this;}                           // Try to convert to a structure
    Union     toUnion    () {return (Union)    this;}                           // Try to convert to a union

    public String asString()                                                    // Part of memory corresponding to this layout as a string of bits in low endian order
     {final StringBuilder s = new StringBuilder();
      for (int i = 0; i <  width; ++i)                                          // Index each bit
       {final Boolean v = get(at+i);                                            // Value of bit
        s.append(v == null ? '.' : v ? '1' : '0');                              // Represent bit
       }
      return s.reverse().toString();                                            // Prints string with lowest bit rightmost because we work in little endian layout
     }

    Integer asInt()                                                             // Get an integer representing the value of the layout to the extent that is possible.  The integer is held inlittle endian format
     {int n = 0;                                                                // Resulting integer
      for (int i = 0; i < width; ++i)                                           // Each bit
       {final Boolean v = get(at+i);                                            // Value of bit
        if (v == null) return null;                                             // One of the bits is null so the overall value is no longer known
        if (v && i > Integer.SIZE-1) return null;                               // Value is too big to be represented
        n += v ? 1<<i : 0;
       }
      return n;                                                                 // Valid representation of bits as an integer
     }

    void fromString(String value)                                               // Set bits to the little endian value represented by the supplied string
     {final int  N = value.length();
      for(int i = 0; i < N; ++i)
       {final char c = value.charAt(i);                                         // Bit value
        set(at+i, c == '0' ? false : c == '1' ? true : null);
       }
     }

    void fromInt(int value)                                                     // Set bits to match those of the supplied integer
     {final int N = min(Integer.SIZE-1, width);                                 // Maximum number of bits we can set
      for(int i = 0; i < N; ++i)                                                // Set bits
       {final Boolean b = (value & (1<<i)) > 0;                                 // Bit value
        set(at+i, b);                                                           // Set bit
       }
     }
   }

  class Variable extends Field                                                  // Layout a variable with no sub structure
   {Variable(String name, int Width)
     {super(name); width = Width;
     }

    void order() {fields.push(this);}                                           // Order the variable in the layout

    void layout(int At, int Depth, Field superStructure)                        // Layout the variable in the structure
     {at = At; depth = Depth; fields.push(this);
     }

    Field duplicate(Memory d)                                                   // Duplicate a variable so we can modify it safely
     {final Variable v = d.new Variable(name, width);
      v.at = at; v.depth = depth;
      return v;
     }
   }

  class Array extends Field                                                     // Layout an array definition.
   {int size;                                                                   // Dimension of array
    int index = 0;                                                              // Index of array field to access
    Field element;                                                              // The elements of this array are of this type

    Array(String Name, Field Element, int Size)                                 // Create the array definition
     {super(Name);
      size = Size;
      element = Element;
     }

    int at(int i) {return at+i*element.width;}                                  // Offset of this array field in the structure

    void layout(int At, int Depth, Field superStructure)                        // Position this array within the layout
     {depth = Depth;                                                            // Depth of field in the layout
      fields.push(this);                                                        // Relative to super structure
      element.layout(At, Depth+1, superStructure);                              // Field sub structure
      at = At;                                                                  // Position on index
      element.up = this;                                                        // Chain up to containing parent field
      width = size * element.width;                                             // The size of the array is the sie of its element times the number of elements in the array
     }

    void order()                                                                // Order the array in the layout
     {fields.push(this);                                                        // Relative to super structure
      element.order();                                                          // Field sub structure
     }

    void position(int At)                                                       // Reposition this array after an index of a containing array has been changed
     {at = At;
      element.position(at + index * element.width);
     }

    public String toString()                                                    // Print the field
     {final String  i = String.format("%10d", index);                           // Format value of field if available
      final String  n = indent()+name;
      final String[]c = getClass().getName().split("\\$");                      // Class name
      return String.format("%c %4d  %4d   %4d %10s   %s\n",
                            c[1].charAt(0), at, width, size, i, n);
     }

    void setIndex(int Index)                                                    // Sets the index for the current array field allowing us to set and get this field and all its sub elements.
     {if (index != Index)
       {index = Index; position(at);
       }
     }

    Field duplicate(Memory d)                                                   // Duplicate an array so we can modify it safely
     {final Field e = element.duplicate(d);
      final Array a = d.new Array(name, e, size);
      a.width = width; a.at = at; a.depth = depth; a.index = index;
      e.up = a;
      return a;
     }
   }

  class Structure extends Field                                                 // Layout a structure in memory
   {final Map<String,Field> subMap   = new TreeMap<>();                         // Unique variables contained inside this variable
    final Stack     <Field> subStack = new Stack  <>();                         // Order of variables inside this variable

    Structure(String Name, Field...Fields)                                      // Fields in the structure
     {super(Name);
      for (int i = 0; i < Fields.length; ++i) addField(Fields[i]);              // Each field in this structure
     }

    void addField(Field Field)                                                  // Add additional fields
     {Field.up = this;                                                          // Chain up to containing structure
      if (subMap.containsKey(Field.name))
       {stop("Structure already contains field with this name",
             name, Field.name);
       }
      subMap.put (Field.name, Field);                                           // Add as a sub structure by name
      subStack.push(Field);                                                     // Add as a sub structure in order
     }

    void layout(int At, int Depth, Field superStructure)                        // Place the structure in the layout
     {at = At;
      width = 0;
      depth = Depth;
      fields.push(this);
      for(Field v : subStack)                                                   // Field sub structure
       {v.at = at+width;
        v.layout(v.at, Depth+1, superStructure);
        width += v.width;
       }
     }

    void order()                                                                // Order structure in layout
     {fields.push(this);
      for(Field v : subStack) v.order();
     }

    void position(int At)                                                       // Reposition this structure after an index of a containing array has been changed
     {at = At;
      int w = 0;
      for(Field v : subStack)                                                   // Field sub structure
       {v.position(v.at = at+w);                                                // Substructures are laid out sequentially
        w += v.width;
       }
     }

    Field duplicate(Memory d)                                                   // Duplicate a structure so we can modify it safely
     {final Structure s = d.new Structure(name);
      s.width = width; s.at = at; s.depth = depth;
      for(Field L : subStack)
       {final Field l = L.duplicate(d);
        s.subMap.put(l.name, l);
        s.subStack.push(l);
        l.up = this;
       }
      return s;
     }
   }

  class Union extends Field                                                     // Union of structures laid out in memory on top of each other - it is up to you to have a way of deciding which substructure is valid
   {final Map<String,Field> subMap = new TreeMap<>();                           // Unique variables contained inside this variable

    Union(String Name, Field...Fields)                                          // Fields in the union
     {super(Name);
      for (int i = 0; i < Fields.length; ++i) addField(Fields[i]);              // Each field in this union
     }

    void addField(Field Field)                                                  // Add a field to the union
     {final String n = Field.name;
      Field.up = this;                                                          // Chain up to containing structure
      if (subMap.containsKey(n)) stop(name, "already contains", n);
      subMap.put (n, Field);                                                    // Add as a sub structure by name
     }

    void order()                                                                // Order the union in the layout
     {fields.push(this);
      for(Field v : subMap.values()) v.order();                                 // Find largest substructure
     }

    void layout(int at, int Depth, Field superStructure)                        // Compile this variable so that the size, width and byte fields are correct
     {width = 0;
      depth = Depth;
      fields.push(this);
      for(Field v : subMap.values())                                            // Find largest substructure
       {v.at = at;                                                              // Substructures are laid out on top of each other
        v.layout(v.at, Depth+1, superStructure);
        width = max(width, v.width);                                            // Space occupied is determined by largest field of union
       }
     }

    void position(int At)                                                       // Reposition this union after an index of a containing array has been changed
     {at = At;
      for(Field v : subMap.values()) v.position(at);
     }

    Field duplicate(Memory d)                                                   // Duplicate a union so we can modify it safely
     {final Union u = d.new Union(name);
      u.width = width; u.at = at; u.depth = depth;
      for(String s : subMap.keySet())
       {final Field L = subMap.get(s);
        final Field l = L.duplicate(d);
        u.subMap.put(l.name, l);
        l.up = this;
       }
      return u;
     }
   }

  Variable  variable (String name, int width)            {return new Variable (name, width);}
  Array     array    (String name, Field   ml, int size) {return new Array    (name, ml, size);}
  Structure structure(String name, Field...ml)           {return new Structure(name, ml);}
  Union     union    (String name, Field...ml)           {return new Union    (name, ml);}

//D0                                                                            // Tests: I test, therefore I am.  And so do my mentees.  But most other people, apparently, do not, they live in a half world lit by shadows in which they never know if their code works or not.

  static void test_1()
   {Memory l = new Memory("layout")
     {Field define()
       {Variable  a = variable ("a", 2);
        Variable  b = variable ("b", 2);
        Variable  c = variable ("c", 4);
        Structure s = structure("s", a, b, c);
        Array     A = array    ("A", s, 3);
        Variable  d = variable ("d", 4);
        Variable  e = variable ("e", 4);
        return        structure("S", d, A, e);
       }
     };

    l.ok("""
T   At  Wide  Size       Value   Field name
S    0    32                     S
V    0     4                       d
A    4    24      3          0     A
S    4     8                         s
V    4     2                           a
V    6     2                           b
V    8     4                           c
V   28     4                       e
""");

    l.get("S.A").toArray().setIndex(1);
    //stop(l);
    l.ok("""
T   At  Wide  Size       Value   Field name
S    0    32                     S
V    0     4                       d
A    4    24      3          1     A
S   12     8                         s
V   12     2                           a
V   14     2                           b
V   16     4                           c
V   28     4                       e
""");

    Memory m = l.duplicate();
    //stop(l);
    l.ok("""
T   At  Wide  Size       Value   Field name
S    0    32                     S
V    0     4                       d
A    4    24      3          1     A
S   12     8                         s
V   12     2                           a
V   14     2                           b
V   16     4                           c
V   28     4                       e
""");

    //stop(m);
    m.ok("""
T   At  Wide  Size       Value   Field name
S    0    32                     S
V    0     4                       d
A    4    24      3          1     A
S   12     8                         s
V   12     2                           a
V   14     2                           b
V   16     4                           c
V   28     4                       e
""");
    m.get("S.A").toArray().setIndex(2);
    //stop(l);
    l.ok("""
T   At  Wide  Size       Value   Field name
S    0    32                     S
V    0     4                       d
A    4    24      3          1     A
S   12     8                         s
V   12     2                           a
V   14     2                           b
V   16     4                           c
V   28     4                       e
""");

    //stop(m);
    m.ok("""
T   At  Wide  Size       Value   Field name
S    0    32                     S
V    0     4                       d
A    4    24      3          2     A
S   20     8                         s
V   20     2                           a
V   22     2                           b
V   24     4                           c
V   28     4                       e
""");
   }

  static void test_memory()
   {final Stack<Boolean> memory = new Stack<>();
    Memory l = new Memory("layout")
     {Field define()
       {Variable  a = variable ("a", 8);
        Variable  b = variable ("b", 8);
        Variable  c = variable ("c", 8);
        Structure s = structure("s", a, b, c);
        Array     A = array    ("A", s, 3);
        Variable  d = variable ("d", 4);
        Variable  e = variable ("e", 4);
        return        structure("S", d, A, e);
       }
      void    set(int i, Boolean b) {       memory.setElementAt(b, i);}
      Boolean get(int i)            {return memory.   elementAt(   i);}
     };

    for (int i = 0; i < l.size(); i++) memory.push(false);

    final Variable a = l.get("S.A.s.a").toVariable();
    final Variable b = l.get("S.A.s.b").toVariable();
    final Variable c = l.get("S.A.s.c").toVariable();
    final Array    A = l.get("S.A").toArray();
    A.setIndex(0); a.fromInt(11); b.fromInt(12); c.fromInt(14);
    A.setIndex(1); a.fromInt( 1); b.fromInt( 2); c.fromInt( 3);
    A.setIndex(2); a.fromInt(31); b.fromInt(32); c.fromInt(34);
    A.setIndex(1);
    l.ok("""
T   At  Wide  Size       Value   Field name
S    0    80                     S
V    0     4                 0     d
A    4    72      3          1     A
S   28    24            197121       s
V   28     8                 1         a
V   36     8                 2         b
V   44     8                 3         c
V   76     4                 0     e
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_1();
    test_memory();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
    test_memory();
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
