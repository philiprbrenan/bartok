//------------------------------------------------------------------------------
// Bit memory laid out as variables, arrays, structures, and unions
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Simulate a silicon chip.

import java.util.*;

//D1 Construct                                                                  // Layout a description of the memory used by a chip

public class Layout extends Test                                                // A Memory layout for a chip. There might be several such layouts representing parts of the chip.
 {Field top;                                                                    // The top most field in a set of nested fields describing memory.
  final Stack<     Field> fields    = new Stack<>();                            // Fields in in-order
  final Map<String,Field> fullNames = new TreeMap<>();                          // Fields by full name
  Stack<Boolean>             memory = new Stack<>();                            // A sample memory that can be freed if not wanted by assigning null to this non final field.

  void layout(Field field)                                                      // Create a new Layout loaded from a set of definitions
   {top  = field;                                                               // Record layout
    field.layout(0, 0, null);                                                   // Locate field positions
    for (int i = 0; i < field.width; i++) memory.push(null);                    // Create a matching memory.
    indexNames();                                                               // Index the names of the fields
   }

  Field get(String path) {return fullNames.get(path);}                          // Locate a field from its full name path which must include the top most field

  Layout duplicate()                                                            // Duplicate a layout
   {final Layout d = new Layout();                                              // New layout
    if (top == null) return d;                                                  // No fields to duplicate
    d.top = top.duplicate(d);                                                   // Copy each field into this layout
    d.top.order();                                                              // Order the fields
    d.indexNames();                                                             // Index the names of the fields
    d.memory = memory;
    return d;
   }

  public String printHeader()                                                   // Header for  print of layout
   {return String.format("%1s %4s  %4s  %5s    %8s   %s\n",
                         "T", "At", "Wide", "Index", "Value", "Field name");
   }

  public String toString()                                                      // Print the layout with a header
   {return top.toString();
   }

  void indexNames() {for(Field f : fields) f.fullName();}                       // Index field names in each containing structure so fields can be accessed by a unique structured name

  int size() {return top == null ? 0 : top.width;}                              // Size of memory

  Field asFields() {return top;}                                                // Get field definitions associated with memory

  void clear()                                                                  // Clear memory
   {final int N = top != null ? top.width : 0;
    for (int i = 0; i < N; i++) set(i, false);
   }

  void ok(String expected) {Test.ok(toString(), expected);}                     // Confirm layout is as expected

//D1 Bit                                                                        // A bit is the element from which memory is constructed.

  void    set(int i, Boolean b) {       memory.setElementAt(b, i);}             // Set a bit in the sample memory. This method should be overridden to drive a more useful memory that captures more information about its bits than just their values.
  Boolean get(int i)            {return memory.elementAt(i);}                   // Get a bit from the sample memory

//D1 Bits                                                                       // A collection of bits abstracted from memory layouts

  class Bits extends Stack<Integer>                                             // Some bits of interest
   {private static final long serialVersionUID = 1L;

    void push(Field field)                                                      // Add the bits associated with a field
     {for (int i = 0; i < field.width; i++) push(Integer.valueOf(field.at+i));  // Add index of the indicated bit in the field
     }

    void push(Field field, int offset) {push(Integer.valueOf(field.at+offset));}// Add the bit at the specified offset int the field

    void push(Field field, int start, int length)                               // Add a substring of the bits associated with a field
     {for (int i = 0; i < length; i++) push(Integer.valueOf(field.at+i+start)); // Add index of each referenced bit in the field
     }

    public String asString()                                                    // Part of memory corresponding to this layout as a string of bits in low endian order
     {final StringBuilder s = new StringBuilder();
      final int N = size();
      for (int i = 0; i < N; ++i)                                               // Index each bit
       {final Boolean v = Layout.this.get(elementAt(i));                        // Value of bit
        s.append(v == null ? '.' : v ? '1' : '0');                              // Represent bit
       }
      return s.reverse().toString();                                            // Prints string with lowest bit rightmost because we work in little endian layout
     }

    Integer asInt()                                                             // Get an integer representing the value of the layout to the extent that is possible.  The integer is held inlittle endian format
     {int n = 0, N = size();                                                    // Resulting integer
      for (int i = 0; i < N; ++i)                                               // Each bit
       {final Boolean v = Layout.this.get(elementAt(i));                        // Value of bit
        if (v == null) return null;                                             // One of the bits is null so the overall value is no longer known
        if (v && i > Integer.SIZE-1) return null;                               // Value is too big to be represented
        n += v ? 1<<i : 0;
       }
      return n;                                                                 // Valid representation of bits as an integer
     }

    void fromString(String value)                                               // Set bits to the little endian value represented by the supplied string
     {final int N = value.length();
      for(int i = 0; i < N; ++i)
       {final char c = value.charAt(i);                                         // Bit value to be set
        final Boolean b = c == '0' ? false : c == '1' ? true : null;            // Value
        final int     j = elementAt(i);                                         // Index
        Layout.this.set(j, b);                                                  // Set bit
       }
     }

    void fromInt(int value)                                                     // Set bits to match those of the supplied integer
     {final int N = min(Integer.SIZE-1, size());                                // Maximum number of bits we can set
      for(int i = 0; i < N; ++i)                                                // Set bits
       {final Boolean b = (value & (1<<i)) > 0;                                 // Value
        final int     j = elementAt(i);                                         // Index
        Layout.this.set(j, b);                                                  // Set bit
       }
     }
    void ok(int    expected) {Test.ok(asInt(),    expected);}                   // Check value of bits as an integer
    void ok(String expected) {Test.ok(asString(), expected);}                   // Check value of a bits as a string
   }

  Bits bits() {return new Bits();}                                              // Create a set of bits

//D1 Layouts                                                                    // Field memory of the chip as variables, arrays, structures, unions. Dividing the memory in this manner makes it easier to program the chip symbolically.

  abstract class Field                                                          // Variable/Array/Structure/Union definition.
   {final String name;                                                          // Name of field
    int at;                                                                     // Offset of field from start of memory
    int width;                                                                  // Number of bits in a field
    int depth;                                                                  // Depth of field - the number of containing arrays/structures/unions above
    Field up;                                                                   // Upward chain to containing array/structure/union
    final Map<String,Field> fullNames = new TreeMap<>();                        // Fields by name

    Field(String Name) {name = Name;}                                           // Create a new named field

    int at   () {return at;}                                                    // Position of field in memory
    int width() {return width;}                                                 // Size of the memory in bits occupied by this field

    void fullName()                                                             // The full name of a field
     {String n = name;
      for(Field p = up; p != null; p = p.up)                                    // Up through containing fields
       {p.fullNames.put(n, this);                                               // Full name from this field
        n = p.name + "."+n;
       }
      Layout.this.fullNames.put(n, this);                                       // Full name from outer most to inner most
     }

    Field get(String path) {return fullNames.get(path);}                        // Address a contained field by name

    int sameSize(Field b)                                                       // Check the specified field is the same size as this field
     {final int A = width, B = b.width;
      if (A != B) stop("Fields must have the same width, but field", name,
        "has width", A, "while field", b.name, "has size", B);
      return A;
     }

    void isBit()                                                                // Check the specified field is a bit field
     {if (width != 1) stop("Field must be one bit wide, but it is",
        width, "bits wide");
     }

    abstract void layout(int at, int depth, Field superStructure);              // Layout this field
    abstract void order();                                                      // Add this field to the tree of fields

    void position(int At) {at = At;}                                            // Reposition a field after an index of a containing array has been changed

    String  indent() {return "  ".repeat(depth);}                               // Indentation during printing
    char fieldType() {return getClass().getName().split("\\$")[1].charAt(0);}   // First letter of inner most class name to identify type of field

    StringBuilder header(boolean printHeader)                                   // Create a strin builder with a header laoded if necessary
     {final StringBuilder s = new StringBuilder();
      if (printHeader) s.append(printHeader());                                 // Header requested
      return s;
     }

    StringBuilder print(boolean printHeader)                                    // Print the field
     {final String  i = printInt();                                             // Format value of field if available
      final String  n = indent()+name;                                          // Name using indentation to show depth
      final char    c = fieldType();                                            // First letter of inner most class name to identify type of field

      final StringBuilder s = header(printHeader);                              // Print header if requested
      s.append(String.format("%c %4d  %4d         %10s   %s\n",                 // Variable
                             c,  at,  width,      i,     n));
      return s;                                                                 // Printed field
     }

    public   String toString() {return toString(true);}                         // Print the field with a header
    abstract String toString(boolean printHeader);                              // Print the field with or without a header

    abstract Field duplicate(Layout d);                                         // Duplicate an field of this field so we can modify it safely

    Bit       toBit      () {return (Bit)      this;}                           // Try to convert to a bit
    Variable  toVariable () {return (Variable) this;}                           // Try to convert to a variable
    Array     toArray    () {return (Array)    this;}                           // Try to convert to an array
    Structure toStructure() {return (Structure)this;}                           // Try to convert to a structure
    Union     toUnion    () {return (Union)    this;}                           // Try to convert to a union

    Boolean get(int i)     {return Layout.this.get(at+i);}                      // Get a bit from this layout
    void    set(int i, Boolean b) {Layout.this.set(at+i, b);}                   // Put a bit into this layout

    public String asString()                                                    // Part of memory corresponding to this layout as a string of bits in low endian order
     {final StringBuilder s = new StringBuilder();
      for (int i = 0; i <  width; ++i)                                          // Index each bit
       {final Boolean v = get(i);                                               // Value of bit
        s.append(v == null ? '.' : v ? '1' : '0');                              // Represent bit
       }
      return s.reverse().toString();                                            // Prints string with lowest bit rightmost because we work in little endian layout
     }

    Integer asInt()                                                             // Get an integer representing the value of the layout to the extent that is possible.  The integer is held inlittle endian format
     {int n = 0;                                                                // Resulting integer
      for (int i = 0; i < width; ++i)                                           // Each bit
       {final Boolean v = get(i);                                               // Value of bit
        if (v == null) return null;                                             // One of the bits is null so the overall value is no longer known
        if (v && i > Integer.SIZE-1) return null;                               // Value is too big to be represented
        n += v ? 1<<i : 0;
       }
      return n;                                                                 // Valid representation of bits as an integer
     }

    String printInt()                                                           // Print the value of the field as an integer accounting for nulls
     {final Integer v = asInt();                                                // Value of field
      return v == null ? "" : String.format("%10d", v);                         // Format value of field if available
     }

    void fromString(String value)                                               // Set bits to the little endian value represented by the supplied string
     {final int  N = value.length();
      for(int i = 0; i < N; ++i)
       {final char c = value.charAt(i);                                         // Bit value
        set(i, c == '0' ? false : c == '1' ? true : null);
       }
     }

    void zero() {for(int i = 0; i < width; ++i) set(i, false);}                 // Clear all the bits in the field to zero
    void ones() {for(int i = 0; i < width; ++i) set(i, true); }                 // Set   all the bits in the field to one

    void fromInt(int value)                                                     // Set bits to match those of the supplied integer
     {final int N = min(Integer.SIZE-1, width);                                 // Maximum number of bits we can set
      for(int i = 0; i < N; ++i)                                                // Set bits
       {final Boolean b = (value & (1<<i)) > 0;                                 // Bit value
        set(i, b);                                                              // Set bit
       }
     }
    void ok(int    expected) {Test.ok(asInt(),    expected);}                   // Check value of a field as an integer
    void ok(String expected) {Test.ok(asString(), expected);}                   // Check value of a field as a string
   }

  class Variable extends Field                                                  // Layout a variable with no sub structure
   {Variable(String name, int Width)
     {super(name); width = Width;
     }

    void order() {fields.push(this);}                                           // Order the variable in the layout

    void layout(int At, int Depth, Field superStructure)                        // Layout the variable in the structure
     {at = At; depth = Depth; fields.push(this);
     }

    Field duplicate(Layout d)                                                   // Duplicate a variable so we can modify it safely
     {final Variable v = d.new Variable(name, width);
      v.at = at; v.depth = depth;
      return v;
     }

    public String toString(boolean printHeader)                                 // Print the variable
     {return print(printHeader).toString();
     }
   }

  class Bit extends Variable {Bit(String name) {super(name, 1);}}               // A variable of unit width is a boolean. could have called it a boolean but decied to callit abool instead becuase it was shorter and more like

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

    public String toString(boolean printHeader)                                 // Print the array
     {final String  n = indent()+name;                                          // Name using indentation to show depth
      final char    c = fieldType();                                            // First letter of inner most class name to identify type of field
      final StringBuilder s = header(printHeader);                              // Print header if requested

      final int indexSave = index;                                              // Save the array index
      for (int j = 0; j < size; j++)                                            // Each array element
       {setIndex(j);
        final String i = printInt();                                            // Format value of field if available
        final int    w = width;                                                 // Bits occupied bythe array
        final int    p = at + element.width * j;                                // Position of the array element
        s.append(String.format("%c %4d  %4d  %5d  %10s   %s\n",                 // Index of the array
                               c,  p,   w,   j,   i,     n));
        s.append(element.toString(false));                                      // Print the array element without headers
       }
      index = indexSave;                                                        // Restore the original array index

      return s.toString();                                                      // Array as a string
     }

    void setIndex(int Index)                                                    // Sets the index for the current array field allowing us to set and get this field and all its sub elements.
     {if (index != Index)
       {index = Index; position(at);
       }
     }

    Field duplicate(Layout d)                                                   // Duplicate an array so we can modify it safely
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

    Field duplicate(Layout d)                                                   // Duplicate a structure so we can modify it safely
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

    public String toString(boolean printHeader)                                 // Print the structure
     {final StringBuilder s = print(printHeader);                               // Print line describing structure
      for(Field f: subStack) s.append(f.toString(false));                       // Print each field of structure
      return s.toString();                                                      // Structure converted to string
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

    Field duplicate(Layout d)                                                   // Duplicate a union so we can modify it safely
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

    public String toString(boolean printHeader)                                 // Print the union
     {final StringBuilder s = print(printHeader);                               // Print line describing structure
      for(Field f: subMap.values()) s.append(f.toString(false));                // Print each field of structure
      return s.toString();                                                      // Structure converted to string
     }
   }

  Bit       bit      (String name)                       {return new Bit      (name);}
  Variable  variable (String name, int width)            {return new Variable (name, width);}
  Array     array    (String name, Field   ml, int size) {return new Array    (name, ml, size);}
  Structure structure(String name, Field...ml)           {return new Structure(name, ml);}
  Union     union    (String name, Field...ml)           {return new Union    (name, ml);}

//D0                                                                            // Tests: I test, therefore I am.  And so do my mentees.  But most other people, apparently, do not, they live in a half world lit by shadows in which they never know if their code actually works or not.

  static void test_1()
   {Layout    l = new Layout();
    Variable  a = l.variable ("a", 2);
    Variable  b = l.variable ("b", 2);
    Variable  c = l.variable ("c", 4);
    Structure s = l.structure("s", a, b, c);
    Array     A = l.array    ("A", s, 3);
    Variable  d = l.variable ("d", 4);
    Variable  e = l.variable ("e", 4);
    Structure S = l.structure("S", d, A, e);
    l.layout(S);

    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    32                      S
V    0     4                        d
A    4    24      0                 A
S    4     8                          s
V    4     2                            a
V    6     2                            b
V    8     4                            c
A   12    24      1                 A
S   12     8                          s
V   12     2                            a
V   14     2                            b
V   16     4                            c
A   20    24      2                 A
S   20     8                          s
V   20     2                            a
V   22     2                            b
V   24     4                            c
V   28     4                        e
""");

    l.get("S.A").toArray().setIndex(1);
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    32                      S
V    0     4                        d
A    4    24      0                 A
S    4     8                          s
V    4     2                            a
V    6     2                            b
V    8     4                            c
A   12    24      1                 A
S   12     8                          s
V   12     2                            a
V   14     2                            b
V   16     4                            c
A   20    24      2                 A
S   20     8                          s
V   20     2                            a
V   22     2                            b
V   24     4                            c
V   28     4                        e
""");

    Layout m = l.duplicate();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    32                      S
V    0     4                        d
A    4    24      0                 A
S    4     8                          s
V    4     2                            a
V    6     2                            b
V    8     4                            c
A   12    24      1                 A
S   12     8                          s
V   12     2                            a
V   14     2                            b
V   16     4                            c
A   20    24      2                 A
S   20     8                          s
V   20     2                            a
V   22     2                            b
V   24     4                            c
V   28     4                        e
""");

    //stop(m);
    m.ok("""
T   At  Wide  Index       Value   Field name
S    0    32                      S
V    0     4                        d
A    4    24      0                 A
S    4     8                          s
V    4     2                            a
V    6     2                            b
V    8     4                            c
A   12    24      1                 A
S   12     8                          s
V   12     2                            a
V   14     2                            b
V   16     4                            c
A   20    24      2                 A
S   20     8                          s
V   20     2                            a
V   22     2                            b
V   24     4                            c
V   28     4                        e
""");
    m.get("S.A").toArray().setIndex(2);
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    32                      S
V    0     4                        d
A    4    24      0                 A
S    4     8                          s
V    4     2                            a
V    6     2                            b
V    8     4                            c
A   12    24      1                 A
S   12     8                          s
V   12     2                            a
V   14     2                            b
V   16     4                            c
A   20    24      2                 A
S   20     8                          s
V   20     2                            a
V   22     2                            b
V   24     4                            c
V   28     4                        e
""");

    //stop(m);
    m.ok("""
T   At  Wide  Index       Value   Field name
S    0    32                      S
V    0     4                        d
A    4    24      0                 A
S    4     8                          s
V    4     2                            a
V    6     2                            b
V    8     4                            c
A   12    24      1                 A
S   12     8                          s
V   12     2                            a
V   14     2                            b
V   16     4                            c
A   20    24      2                 A
S   20     8                          s
V   20     2                            a
V   22     2                            b
V   24     4                            c
V   28     4                        e
""");
   }

  static void test_memory()
   {Layout l = new Layout();
    Variable  a = l.variable ("a", 8);
    Variable  b = l.variable ("b", 8);
    Variable  c = l.variable ("c", 8);
    Structure s = l.structure("s", a, b, c);
    Array     A = l.array    ("A", s, 3);
    Variable  d = l.variable ("d", 4);
    Variable  e = l.variable ("e", 4);
    l.layout(l.structure("S", d, A, e));

    A.setIndex(0); a.fromInt(11); b.fromInt(12); c.fromInt(14);
    A.setIndex(1); a.fromInt( 1); b.fromInt( 2); c.fromInt( 3);
    A.setIndex(2); a.fromInt(31); b.fromInt(32); c.fromInt(34);
    A.setIndex(1);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    80                      S
V    0     4                        d
A    4    72      0                 A
S    4    24             920587       s
V    4     8                 11         a
V   12     8                 12         b
V   20     8                 14         c
A   28    72      1                 A
S   28    24             197121       s
V   28     8                  1         a
V   36     8                  2         b
V   44     8                  3         c
A   52    72      2                 A
S   52    24            2236447       s
V   52     8                 31         a
V   60     8                 32         b
V   68     8                 34         c
V   76     4                        e
""");
    l.clear();
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    80                  0   S
V    0     4                  0     d
A    4    72      0           0     A
S    4    24                  0       s
V    4     8                  0         a
V   12     8                  0         b
V   20     8                  0         c
A   28    72      1           0     A
S   28    24                  0       s
V   28     8                  0         a
V   36     8                  0         b
V   44     8                  0         c
A   52    72      2           0     A
S   52    24                  0       s
V   52     8                  0         a
V   60     8                  0         b
V   68     8                  0         c
V   76     4                  0     e
""");
   }

  static void test_bit()
   {Layout l = new Layout();
    var a = l.bit      ("a");
    var b = l.variable ("b", 7);
    var s = l.structure("s", a, b);
    l.layout(s);

    l.get("s").toStructure().fromInt(3);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     8                  3   s
B    0     1                  1     a
V    1     7                  1     b
""");
    s.get("a").toBit()     .ok(1);
    s.get("b").toVariable().ok(1);
   }

  static void test_bits()
   {final Stack<Boolean> memory = new Stack<>();

    Layout l = new Layout();
    var a = l.bit      ("a");
    var b = l.variable ("b", 2);
    var c = l.bit      ("c");
    var d = l.variable ("d", 2);
    var s = l.structure("s", a, b, c, d);
    l.layout(s);

    l.get("s").toStructure().fromInt(27);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     6                 27   s
B    0     1                  1     a
V    1     2                  1     b
B    3     1                  1     c
V    4     2                  1     d
""");
    Bits A = l.bits(); A.push(a);       A.push(c);    A.ok(3);
    Bits B = l.bits(); B.push(b, 1, 1); B.push(c, 0); B.ok(2);
    ok(a.sameSize(c) == a.width);
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_1();
    test_memory();
    test_bit();
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
    testExit(0);                                                                // Exit with a return code if we failed any tests to alert github
   }
 }
