//------------------------------------------------------------------------------
// Bit machine memory laid out as variables, arrays, structures, and unions
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Simulate a silicon chip.
// Add tags to fields so that we can check that we are getting a field of the right sort.
import java.util.*;

//D1 Construct                                                                  // Layout a description of the memory used by a chip

public class Layout extends Test implements LayoutAble                          // A Memory layout for a chip. There might be several such layouts representing parts of the chip.
 {Field top;                                                                    // The top most field in a set of nested fields describing memory.
  Memory                memory = new Memory();                                  // A sample memory that can be freed if not wanted by assigning null to this non final field.
  final Stack<Layout> layouts  = new Stack<>();                                 // All the sub layouts added to this layout so we can unify their memory

  void layout(Field field)                                                      // Create a new Layout loaded from a set of definitions
   {top  = field;                                                               // Record layout
    field.layout(0, 0);                                                         // Locate field positions
    //memory = new Memory();                                                    // Create a matching memory.
    field.indexNames();                                                         // Index the names of the fields
    memory = new Memory();                                                      // New memory encompassing all the unified memories
    unifyMemory(memory);                                                        // Unify the memory of all declared layouts with the memory of this layout
   }

  Field get(String path)                                                        // Locate a field from its full name path which must include the top most field
   {return top.fullNames.get(path);
   }

  LayoutAble copy()                                                             // Copy a layout and share its memory
   {final Layout d = new Layout();                                              // New layout
    if (top == null) return d;                                                  // No fields to duplicate
    d.top = top.duplicate(d);                                                   // Copy each field into this layout
    d.top.indexNames();                                                         // Index the names of the fields
    d.memory = memory;                                                          // Share the existing memory
    return d;
   }

  LayoutAble duplicate()                                                        // Duplicate a layout and create a new memeory
   {final LayoutAble d = copy();                                                // New layout
    d.asLayout().memory = d.asLayout().new Memory();                            // New memory
    return d;                                                                   // Duplicate
   }

  public String toString()                                                      // Print the layout with a header
   {return top.toString();
   }

  public Layout.Field asField()       {return top;}                             // Layout associated with this class
  public Layout       asLayout     () {return this;}                            // Layout associated with this class

  int size() {return top == null ? 0 : top.width;}                              // Size of memory

  Field asFields() {return top;}                                                // Get field definitions associated with memory

  Field field(LayoutAble field)
   {if      (field instanceof Layout l) return l.top;
    else if (field instanceof Field  f) return f;
    stop(field.getClass().getSimpleName(), "cannot be converted to 'Layout'");
    return null;
   }

  void unifyMemory(Memory memory)                                               // Unify the memory of all declared layouts with the memory of this layout
   {for (Layout l : layouts)                                                    // Unify the memory of each layout
     {l.memory = memory;
      if (l != this) l.unifyMemory(memory);
     }
   }

  void clear()                                                                  // Clear memory
   {final int N = top != null ? top.width : 0;
    for (int i = 0; i < N; i++) set(i, false);
   }

  void ok(String expected) {Test.ok(asLayout().top.toString(), expected);}      // Confirm layout is as expected

  static void constants(Field...fields)                                         // Mark the specified fields as constants
   {for (Field f : fields) f.constant = true;
   }

  static int unary(int binary) {return (1<<binary) - 1;}                        // Convert a binary integer to a unary integer of the same value

  Variable dupVariable(String name)                                             // Create a variable like the named one in the layout
   {return get(name).duplicate().asField().toVariable();
   }

  Bit dupBit(String name)                                                       // Create a variable like the named one in the layout
   {return get(name).duplicate().asField().toBit();
   }

//D1 Bit Memory                                                                 // A bit is the element from which memory is constructed.

  void    set(int i, Boolean b) {memory.setElementAt(b, i);}                    // Set a bit in the sample memory. This method should be overridden to drive a more useful memory that captures more information about its bits than just their values.
  Boolean get(int i)            {return memory.elementAt(i);}                   // Get a bit from the sample memory

  class Memory extends Stack<Boolean>                                           // A sample memory that can be freed if not wanted by assigning null to this non final field.
   {private static final long serialVersionUID = 1L;
    static int memories = 0;
    final int memoryNumber = ++memories;                                        // Number the memory to make assist debugging
    Memory()                                                                    // A memory large enough to hold the layout
     {if (top != null) for (int i = 0; i < top.width; i++) push(false);         // Initialize memory to zeros as this simplifies debugging
     }
    public String toString()                                                    // Print memory
     {final StringBuilder s = new StringBuilder();
      final int N = size();
      //s.append("Memory: "+memoryNumber+" has "+N+" bits\n");                  // Title
      int l = 0;
      for (int i = 1; i <= N; i++)
       {final Boolean b = get(i-1);
        s.append(b == null ? 'n' : b ? '1' : '.');
        if (false) {}
        else if (i % 64 == 0) s.append("\n");
        else if (i % 32 == 0) s.append("*");
        else if (i % 16 == 0) s.append("+");
        else if (i %  8 == 0) s.append(" ");
       }
      s.append("\n");
      return s.toString();
     }
   }

//D1 Layouts                                                                    // Field memory of the chip as variables, arrays, structures, unions. Dividing the memory in this manner makes it easier to program the chip symbolically.

  abstract class Field implements LayoutAble                                    // Variable/Array/Structure/Union definition.
   {final String  name;                                                         // Name of field
    final  int  number;                                                         // Number of field
    static int numbers = 0;                                                     // Numbers of fields
    String    fullName;                                                         // Full name of this field
    boolean   constant = false;                                                 // Whether the field can be modified
    int at;                                                                     // Offset of field from start of memory
    int width;                                                                  // Number of bits in a field
    int depth;                                                                  // Depth of field - the number of containing arrays/structures/unions above
    Field up;                                                                   // Upward chain to containing array/structure/union
    final Map<String,Field> fullNames = new TreeMap<>();                        // Fields by name
    final Set<String>  classification = new TreeSet<>();                        // Names that identify the type of the field to aid debugging

    Field(String Name) {name = Name; number = ++numbers;}                       // Create a new named field with a unique number

    int at   () {return at;}                                                    // Position of field in memory
    int width() {return width;}                                                 // Size of the memory in bits occupied by this field

    void fullName(Layout.Field top, StringBuilder s)                            // The full name of a field relative to the indicated top
     {if (top == this) return;
      final Stack<String> names = new Stack<>();                                // Full name path
      for(Layout.Field f = this; f.up != null; f = f.up)                        // Go up structure
       {names.insertElementAt(f.name, 0);
        if (f.up == top) break;
       }
      if (names.size() == 0) return;                                            // Zero length path to element
      final String        t = names.pop();
      s.append("     ");
      for(String n : names) s.append(n+".");
      s.append(t);
     }

    void indexName()                                                            // Index all the full names of a field
     {final Stack<String> names = new Stack<>();                                // Full name path
      for(Layout.Field f = this; f.up != null; f = f.up)                        // Go up structure
       {names.insertElementAt(f.name, 0);
        final StringBuilder s = new StringBuilder();
        final String top = names.pop();
        for(String n : names) s.append(n+".");
        s.append(top); names.push(top);
        f.up.fullNames.put(s.toString(), this);
       }
     }

    Field get(String path) {return fullNames.get(path);}                        // Address a contained field by name
    abstract void indexNames();                                                 // Set the full names of all the sub fields in a field

    public Layout.Field asField () {return this;}                               // Layout associated with this field
    public Layout       asLayout() {return Layout.this;}                        // Layout associated with this field

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

    abstract void layout(int at, int depth);                                    // Layout this field

    void position(int At) {at = At;}                                            // Reposition a field after an index of a containing array has been changed

    String  indent() {return "  ".repeat(depth);}                               // Indentation during printing
    char fieldType() {return getClass().getName().split("\\$")[1].charAt(0);}   // First letter of inner most class name to identify type of field

    StringBuilder header()                                                      // Create a string builder with a header preloaded
     {final StringBuilder s = new StringBuilder();
      //s.append("Memory: "+memory.memoryNumber+"\n");
      s.append(String.format
       ("%1s %4s  %4s  %5s    %8s   %s\n",
        "T", "At", "Wide", "Index", "Value", "Field name"));
      return s;
     }

    String printName(Layout.Field top)                                          // Print the name of a field showing whether it is a constant and its classification
     {final StringBuilder s = new StringBuilder();
      s.append(indent());
      if (constant) s.append("=");                                              // Header requested
      s.append(name);
      if (!classification.isEmpty())                                            // Add any classification
       {s.append("(");
        for(String c: classification) s.append(c+", ");                         // Classification
        s.setLength(s.length()-2);
        s.append(")");
       }
      fullName(top, s);                                                         // Full name for this field
      //s.append(" N="+number);
      //if (up != null) s.append(" U="+up.number);
      return s.toString();
     }

    void print(Layout.Field top, StringBuilder s)                               // Print the field
     {final String  i = printInt();                                             // Format value of field if available
      final String  n = printName(top);                                         // Name using indentation to show depth
      final char    c = fieldType();                                            // First letter of inner most class name to identify type of field

      s.append(String.format("%c %4d  %4d         %10s   %s\n",                 // Variable
                             c,  at,  width,      i,     n));
     }

    public String toString()                                                    // Print the field and its sub structure
     {final StringBuilder s = header();
      print(this, s);
      return s.toString();
     }

    abstract Field duplicate(Layout d);                                         // Duplicate this field and place it in the specified layout so we can modify it safely

    Bit       toBit      () {return (Bit)      this;}                           // Try to convert to a bit
    Variable  toVariable () {return (Variable) this;}                           // Try to convert to a variable
    Array     toArray    () {return (Array)    this;}                           // Try to convert to an array
    Structure toStructure() {return (Structure)this;}                           // Try to convert to a structure
    Union     toUnion    () {return (Union)    this;}                           // Try to convert to a union

    Boolean get(int i)     {return Layout.this.get(at+i);}                      // Get a bit from this layout
    void    set(int i, Boolean b)                                               // Put a bit into this layout as long as it os not a constant
     {Layout.this.set(at+i, b);                                                 // Set this field
     }

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
       {try
         {final Boolean v = get(i);                                             // Value of bit
          if (v == null) return null;                                           // One of the bits is null so the overall value is no longer known
          if (v && i > Integer.SIZE-1) return null;                             // Value is too big to be represented
          n += v ? 1<<i : 0;
         }
        catch(Exception e)
         {err("Unable to get bit", i, "from field", name, "at", at);
          return null;
         }
       }
      return n;                                                                 // Valid representation of bits as an integer
     }

    String printInt()                                                           // Print the value of the field as an integer accounting for nulls
     {final Integer v = asInt();                                                // Value of field
      return v == null ? "" : String.format("%10d", v);                         // Format value of field if available
     }

    void fromString(String value)                                               // Set bits to the little endian value represented by the supplied string
     {final int  N = value.length();
      if (constant) err("cannot modify constant:",name,"with value:",asInt());  // Complain if we try to set a field marked as constant
      else for(int i = 0; i < N; ++i)                                           // Set bits of non constant field
       {final char c = value.charAt(i);                                         // Bit value
        set(i, c == '0' ? false : c == '1' ? true : null);
       }
     }

    void zero() {for(int i = 0; i < width; ++i) set(i, false);}                 // Clear all the bits in the field to zero
    void ones() {for(int i = 0; i < width; ++i) set(i, true); }                 // Set   all the bits in the field to one

    void fromInt(int value)                                                     // Set bits to match those of the supplied integer
     {final int N = min(Integer.SIZE-1, width);                                 // Maximum number of bits we can set
      if (constant) err("cannot modify constant:",name,"with value:",asInt());  // Complain if we try to set a field marked as constant
      else for(int i = 0; i < N; ++i)                                           // Set bits of non constant field
       {final Boolean b = (value & (1<<i)) > 0;                                 // Bit value
        set(i, b);                                                              // Set bit
       }
     }

    void  fromUnary(int i) {fromInt((1<<i)-1);}                                 // Set a field to the unary representation of an integer

    int asUnary()                                                               // Get an integer representing the unary value contained a field by counting the bits that are on
     {int n = 0;
      for (int i = 0; i < width; i++) if (get(i)) ++n;
      return n;
     }

    void ok(int    expected) {Test.ok(asInt(),    expected);}                   // Check value of a field as an integer
    void ok(String expected) {Test.ok(toString(), expected);}                   // Check value of a field as a string including meta data

    Layout copy()                                                               // Copy a layout and share its memory so we can see and modify current values in the copy
     {final Layout d = new Layout();                                            // New layout
      d.top = duplicate(d);                                                     // Copy each field into this layout
      d.top.indexNames();                                                       // Index the names of the fields
      d.memory = memory;                                                        // Share the existing memory
      return d;
     }

    Layout duplicate()                                                          // Duplicate a layout and create a new memory that is entirely separate.
     {final Layout d = copy();                                                  // New layout
      d.top.layout(0, 0);                                                       // Locate field positions relative to new top
      d.memory = d.new Memory();                                                // New memory
      return d;                                                                 // Duplicate
     }

    void addClasses(String...Classes)                                           // Add classes to a field
     {for(String C : Classes)
       {for(String c : C.split("\\s+")) classification.add(c);
       }
     }
    void removeClasses(String...Classes)                                        // Remove classes from a field
     {for(String C : Classes)
       {for(String c : C.split("\\s+")) classification.remove(c);
       }
     }
    boolean checkClasses(String...Classes)                                      // Check thata field has at least one of the specifed classes
     {search: for(String C : Classes)
       {for(String c : C.split("\\s+"))
         {if (!classification.contains(c))
           {err("Class:", c, "not contained in classes for:", name,
              "with classes:", classification);
            break search;
           }
         }
       }
      return true;
     }
   }

  class Variable extends Field                                                  // Layout a variable with no sub structure
   {Variable(String name, int Width)
     {super(name); width = Width;
     }

    void indexNames()                                                           // Index the name of this field
     {indexName();
     }

    void layout(int At, int Depth)                                              // Layout the variable in the structure
     {at = At; depth = Depth;
     }

    Field duplicate(Layout d)                                                   // Duplicate a variable during the duplication of a layout
     {final Variable v = d.new Variable(name, width);
      v.at = at; v.depth = depth;
      return v;
     }

    Variable like() {return duplicate().asField().toVariable();}                // Make a variable like this one
   }

  class Bit extends Variable                                                    // A variable of unit width is a boolean. could have called it a boolean but decied to callit abool instead becuase it was shorter and more like
   {Bit(String name) {super(name, 1);}
    Boolean get()              {return get(0);}                                 // Get bit value as Boolean
    void    set(Boolean value) {       set(0, value);}                          // Set bit value from Boolean

    Field duplicate(Layout d)                                                   // Duplicate a bit so we can modify it safely
     {final Bit b = d.new Bit(name);
      b.at = at; b.depth = depth;
      return b;
     }
   }

  class Array extends Field                                                     // Layout an array definition.
   {int size;                                                                   // Dimension of array
    int index = 0;                                                              // Index of array field to access
    Field element;                                                              // The elements of this array are of this type

    Array(String Name, LayoutAble Element, int Size)                            // Create the array definition
     {super(Name);                                                              // Name of array
      size = Size;                                                              // Size of array
      element = field(Element);                                                 // Field definition associated with this layout
      final Layout l = Element.asLayout();
      if (l != null) layouts.push(l);                                           // Add the element to the list of sub layouts if it is a sub layout
     }

    int at(int i) {return at+i*element.width;}                                  // Offset of this array field in the structure

    void layout(int At, int Depth)                                              // Position this array within the layout
     {depth = Depth;                                                            // Depth of field in the layout
      element.layout(At, Depth+1);                                              // Field sub structure
      at = At;                                                                  // Position on index
      element.up = this;                                                        // Chain up to containing parent field
      width = size * element.width;                                             // The size of the array is the sie of its element times the number of elements in the array
     }

    void indexNames()                                                           // Index the name of this field and its sub fields
     {indexName();
      element.indexNames();                                                     // Full names of sub fields relative to outer most layout
      //element.indexNames(fullNames, null);                                    // Index name in this array
     }

    void position(int At)                                                       // Reposition this array after an index of a containing array has been changed
     {at = At;
      element.position(at + index * element.width);
     }

    void print(Layout.Field top, StringBuilder s)                               // Print the array
     {final String  n = printName(top);                                         // Name using indentation to show depth
      final char    c = fieldType();                                            // First letter of inner most class name to identify type of field

      final int indexSave = index;                                              // Save the array index
      for (int j = 0; j < size; j++)                                            // Each array element
       {setIndex(j);
        final String i = printInt();                                            // Format value of field if available
        final int    w = width;                                                 // Bits occupied bythe array
        final int    p = at + element.width * j;                                // Position of the array element
        s.append(String.format("%c %4d  %4d  %5d  %10s   %s\n",                 // Index of the array
                               c,  p,   w,   j,   i,     n));
        element.print(top, s);                                                  // Print the array element without headers
       }
      index = indexSave;                                                        // Restore the original array index
     }

    void setIndex(int Index)                                                    // Sets the index for the current array field allowing us to set and get this field and all its sub elements.
     {index = Index; position(at);
     }

    Field duplicate(Layout d)                                                   // Duplicate an array so we can modify it safely
     {final Field e = element.duplicate(d);
      final Array a = d.new Array(name, e, size);
      a.width = width; a.at = at; a.depth = depth; a.index = index;
      e.up = a;
      return a;
     }

    Array like() {return duplicate().asField().toArray();}                      // Make an array like this one
   }

  class Structure extends Field                                                 // Layout a structure
   {final Map<String,Field> subMap   = new TreeMap<>();                         // Unique variables contained inside this structure
    final Stack     <Field> subStack = new Stack  <>();                         // Order of fields inside this structure

    Structure(String Name, LayoutAble...Fields)                                 // Fields in the structure
     {super(Name);
      for (int i = 0; i < Fields.length; ++i) addField(Fields[i]);              // Each field supplied
     }

    void addField(LayoutAble layout)                                            // Add additional fields
     {final Field field = layout.asField();                                     // Field associated with this layout
      field.up = this;                                                          // Chain up to containing structure
      if (subMap.containsKey(field.name))
       {stop("Structure:", name, "already contains field with this name:",
             field.name);
       }
      subMap.put   (field.name, field);                                         // Add as a field by name to this structure
      subStack.push(field);                                                     // Add as a field in order to this structure
      final Layout l = layout.asLayout();
      if (l != null) layouts.push(l);                                           // Add the element to the list of sub layouts if it is a sub layout
     }

    void layout(int At, int Depth)                                              // Place the structure in the layout
     {at = At;
      width = 0;
      depth = Depth;
      for(Field v : subStack)                                                   // Field sub structure
       {v.at = at+width;
        v.layout(v.at, Depth+1);
        width += v.width;
       }
     }

    void indexNames()                                                           // Index the name of this structure and its sub fields
     {indexName();
      for (Field f : subStack)                                                  // Each field in the structure
       {f.indexNames();                                                         // Index name in outermost layout
       }
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
        l.up = s;
       }
      return s;
     }

    Structure like() {return duplicate().asField().toStructure();}              // Make a structure like this one

    void print(Layout.Field top, StringBuilder s)                               // Print the structure
     {super.print(top, s);
      for(Field f: subStack) f.print(top, s);                                   // Print each field of structure
     }
   }

  class Union extends Structure                                                 // Union of fields laid out in memory on top of each other - it is up to you to have a way of deciding which fields are valid
   {Union(String Name, LayoutAble...Fields)                                     // Fields in the union
     {super(Name, Fields);
     }

    void layout(int at, int Depth)                                              // Compile this variable so that the size, width and byte fields are correct
     {width = 0;
      depth = Depth;
      for(Field v : subMap.values())                                            // Find largest substructure
       {v.at = at;                                                              // Substructures are laid out on top of each other
        v.layout(v.at, Depth+1);
        width = max(width, v.width);                                            // Space occupied is determined by largest field of union
       }
     }

    void position(int At)                                                       // Reposition this union after an index of a containing array has been changed
     {at = At;
      for(Field v : subMap.values()) v.position(at);
     }
   }

  Bit       bit      (String name)                            {return new Bit      (name);}
  Variable  variable (String name, int width)                 {return new Variable (name, width);}
  Array     array    (String name, LayoutAble   ml, int size) {return new Array    (name, ml, size);}
  Structure structure(String name, LayoutAble...ml)           {return new Structure(name, ml);}
  Union     union    (String name, LayoutAble...ml)           {return new Union    (name, ml);}

  static Layout.Bit createBit(String name)                                      // Create a single bit
   {final Layout          l = new Layout();
    final Layout.Variable v = l.bit(name);                                      // New variable
    l.layout(v);                                                                // Layout the variable
    return l.asField().toBit();                                           // Bit
   }

  static Layout.Variable createVariable(String name, int width)                 // Create a single variable
   {final Layout          l = new Layout();
    final Layout.Variable v = l.variable(name, width);                          // New variable
    l.layout(v);                                                                // Layout the variable
    return l.asField().toVariable();                                      // Variable
   }

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

//D0                                                                            // Tests.

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
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    32                  0   S
V    0     4                  0     d     d
A    4    24      0           0     A     A
S    4     8                  0       s     A.s
V    4     2                  0         a     A.s.a
V    6     2                  0         b     A.s.b
V    8     4                  0         c     A.s.c
A   12    24      1           0     A     A
S   12     8                  0       s     A.s
V   12     2                  0         a     A.s.a
V   14     2                  0         b     A.s.b
V   16     4                  0         c     A.s.c
A   20    24      2           0     A     A
S   20     8                  0       s     A.s
V   20     2                  0         a     A.s.a
V   22     2                  0         b     A.s.b
V   24     4                  0         c     A.s.c
V   28     4                  0     e     e
""");

    l.get("A").toArray().setIndex(1);
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    32                  0   S
V    0     4                  0     d     d
A    4    24      0           0     A     A
S    4     8                  0       s     A.s
V    4     2                  0         a     A.s.a
V    6     2                  0         b     A.s.b
V    8     4                  0         c     A.s.c
A   12    24      1           0     A     A
S   12     8                  0       s     A.s
V   12     2                  0         a     A.s.a
V   14     2                  0         b     A.s.b
V   16     4                  0         c     A.s.c
A   20    24      2           0     A     A
S   20     8                  0       s     A.s
V   20     2                  0         a     A.s.a
V   22     2                  0         b     A.s.b
V   24     4                  0         c     A.s.c
V   28     4                  0     e     e
""");

    Layout m = l.duplicate().asLayout();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    32                  0   S
V    0     4                  0     d     d
A    4    24      0           0     A     A
S    4     8                  0       s     A.s
V    4     2                  0         a     A.s.a
V    6     2                  0         b     A.s.b
V    8     4                  0         c     A.s.c
A   12    24      1           0     A     A
S   12     8                  0       s     A.s
V   12     2                  0         a     A.s.a
V   14     2                  0         b     A.s.b
V   16     4                  0         c     A.s.c
A   20    24      2           0     A     A
S   20     8                  0       s     A.s
V   20     2                  0         a     A.s.a
V   22     2                  0         b     A.s.b
V   24     4                  0         c     A.s.c
V   28     4                  0     e     e
""");

    //stop(m);
    m.ok("""
T   At  Wide  Index       Value   Field name
S    0    32                  0   S
V    0     4                  0     d     d
A    4    24      0           0     A     A
S    4     8                  0       s     A.s
V    4     2                  0         a     A.s.a
V    6     2                  0         b     A.s.b
V    8     4                  0         c     A.s.c
A   12    24      1           0     A     A
S   12     8                  0       s     A.s
V   12     2                  0         a     A.s.a
V   14     2                  0         b     A.s.b
V   16     4                  0         c     A.s.c
A   20    24      2           0     A     A
S   20     8                  0       s     A.s
V   20     2                  0         a     A.s.a
V   22     2                  0         b     A.s.b
V   24     4                  0         c     A.s.c
V   28     4                  0     e     e
""");
    m.get("A").toArray().setIndex(2);
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    32                  0   S
V    0     4                  0     d     d
A    4    24      0           0     A     A
S    4     8                  0       s     A.s
V    4     2                  0         a     A.s.a
V    6     2                  0         b     A.s.b
V    8     4                  0         c     A.s.c
A   12    24      1           0     A     A
S   12     8                  0       s     A.s
V   12     2                  0         a     A.s.a
V   14     2                  0         b     A.s.b
V   16     4                  0         c     A.s.c
A   20    24      2           0     A     A
S   20     8                  0       s     A.s
V   20     2                  0         a     A.s.a
V   22     2                  0         b     A.s.b
V   24     4                  0         c     A.s.c
V   28     4                  0     e     e
""");

    //stop(m);
    m.ok("""
T   At  Wide  Index       Value   Field name
S    0    32                  0   S
V    0     4                  0     d     d
A    4    24      0           0     A     A
S    4     8                  0       s     A.s
V    4     2                  0         a     A.s.a
V    6     2                  0         b     A.s.b
V    8     4                  0         c     A.s.c
A   12    24      1           0     A     A
S   12     8                  0       s     A.s
V   12     2                  0         a     A.s.a
V   14     2                  0         b     A.s.b
V   16     4                  0         c     A.s.c
A   20    24      2           0     A     A
S   20     8                  0       s     A.s
V   20     2                  0         a     A.s.a
V   22     2                  0         b     A.s.b
V   24     4                  0         c     A.s.c
V   28     4                  0     e     e
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
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    80                      S
V    0     4                  0     d     d
A    4    72      0                 A     A
S    4    24             920587       s     A.s
V    4     8                 11         a     A.s.a
V   12     8                 12         b     A.s.b
V   20     8                 14         c     A.s.c
A   28    72      1                 A     A
S   28    24             197121       s     A.s
V   28     8                  1         a     A.s.a
V   36     8                  2         b     A.s.b
V   44     8                  3         c     A.s.c
A   52    72      2                 A     A
S   52    24            2236447       s     A.s
V   52     8                 31         a     A.s.a
V   60     8                 32         b     A.s.b
V   68     8                 34         c     A.s.c
V   76     4                  0     e     e
""");
    l.clear();
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    80                  0   S
V    0     4                  0     d     d
A    4    72      0           0     A     A
S    4    24                  0       s     A.s
V    4     8                  0         a     A.s.a
V   12     8                  0         b     A.s.b
V   20     8                  0         c     A.s.c
A   28    72      1           0     A     A
S   28    24                  0       s     A.s
V   28     8                  0         a     A.s.a
V   36     8                  0         b     A.s.b
V   44     8                  0         c     A.s.c
A   52    72      2           0     A     A
S   52    24                  0       s     A.s
V   52     8                  0         a     A.s.a
V   60     8                  0         b     A.s.b
V   68     8                  0         c     A.s.c
V   76     4                  0     e     e
""");
   }

  static void test_bit()
   {Layout l = new Layout();
    var a = l.bit      ("a");
    var b = l.variable ("b", 7);
    var s = l.structure("s", a, b);
    l.layout(s);

    l.asField().toStructure().fromInt(3);
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     8                  3   s
B    0     1                  1     a     a
V    1     7                  1     b     b
""");
    s.get("a").toBit()     .ok(1);
    s.get("b").toVariable().ok(1);
    ok( a.get());
    a.set(false);
    ok(!a.get());
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

    l.asField().toStructure().fromInt(27);
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0     6                 27   s
B    0     1                  1     a     a
V    1     2                  1     b     b
B    3     1                  1     c     c
V    4     2                  1     d     d
""");
    Bits A = l.bits(); A.push(a);       A.push(c);    A.ok(3);
    Bits B = l.bits(); B.push(b, 1, 1); B.push(c, 0); B.ok(2);
    ok(a.sameSize(c) == a.width);
   }

  static void test_sub_layout()
   {Layout l = new Layout();
    var a = l.bit      ("a");
    var b = l.variable ("b", 7);
    var s = l.structure("s", a, b);
    l.layout(s);

    Layout L = new Layout();
    var A = L.variable ("A", 64);
    var B = L.variable ("B", 24);
    var S = L.structure("S", A, B);
    L.layout(S);

    Layout M = new Layout();
    var C = M.variable ("C", 64);
    var D = M.variable ("D", 24);
    var E = M.structure("E", C, l, L, D);
    M.layout(E);

    a.fromInt(1);

    //stop(M);
    M.ok("""
T   At  Wide  Index       Value   Field name
S    0   184                      E
V    0    64                  0     C     C
S   64     8                  1     s     s
B   64     1                  1       a     s.a
V   65     7                  0       b     s.b
S   72    88                  0     S     S
V   72    64                  0       A     S.A
V  136    24                  0       B     S.B
V  160    24                  0     D     D
""");

    M.get("s.a").toBit().fromInt(1);

    M.get("s.a").ok(1);

    M.get("s.a").ok("""
T   At  Wide  Index       Value   Field name
B   64     1                  1       a
""");

    M.get("s.a").asLayout().ok("""
T   At  Wide  Index       Value   Field name
S   64     8                  1     s
B   64     1                  1       a     a
V   65     7                  0       b     b
""");
   }

  static void test_union()
   {Layout l = new Layout();
    var a = l.bit      ("a");
    var b = l.variable ("b", 7);
    var s = l.structure("s", a, b);

    var A = l.variable ("A", 4);
    var B = l.variable ("B", 4);
    var S = l.structure("S", A, B);

    var u = l.union    ("u", S, s);


    var C = l.variable ("C", 4);
    var D = l.variable ("D", 4);
    var E = l.structure("E", C, D, u);

    l.layout(E);

    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    16                  0   E
V    0     4                  0     C     C
V    4     4                  0     D     D
U    8     8                  0     u     u
S    8     8                  0       S     u.S
V    8     4                  0         A     u.S.A
V   12     4                  0         B     u.S.B
S    8     8                  0       s     u.s
B    8     1                  0         a     u.s.a
V    9     7                  0         b     u.s.b
""");
   }

  static void test_unary()
   {Layout l = new Layout();
    var a = l.variable ("a", 4);
    var b = l.variable ("b", 4);
    var c = l.variable ("c", 4);
    var d = l.variable ("d", 4);
    var e = l.variable ("e", 4);
    var s = l.structure("s", a, b, c, d, e);
    l.layout(s);

    a.fromUnary(0);
    b.fromUnary(1);
    c.fromUnary(2);
    d.fromUnary(3);
    e.fromUnary(4);

    ok(a.asUnary(), 0);
    ok(b.asUnary(), 1);
    ok(c.asUnary(), 2);
    ok(d.asUnary(), 3);
    ok(e.asUnary(), 4);

    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    20            1012496   s
V    0     4                  0     a     a
V    4     4                  1     b     b
V    8     4                  3     c     c
V   12     4                  7     d     d
V   16     4                 15     e     e
""");
   }

  static void test_duplicate()
   {Layout l = new Layout();
    var a = l.variable ("a", 4);
    var b = l.variable ("b", 4);
    var c = l.variable ("c", 4);
    var d = l.variable ("d", 4);
    var e = l.variable ("e", 4);
    var s = l.structure("s", a, b, c, d, e);
    l.layout(s);

    Layout D = l.copy().asLayout();

    a.fromUnary(0);
    b.fromUnary(1);
    c.fromUnary(2);
    d.fromUnary(3);
    e.fromUnary(4);

    D.get("a").ok(0);
    D.get("b").ok(1);
    D.get("c").ok(3);
    D.get("d").ok(7);
    D.get("e").ok(15);

    //stop(D.top);
    D.ok("""
T   At  Wide  Index       Value   Field name
S    0    20            1012496   s
V    0     4                  0     a     a
V    4     4                  1     b     b
V    8     4                  3     c     c
V   12     4                  7     d     d
V   16     4                 15     e     e
""");
   }

  static void test_duplicate_sub_layout()
   {Layout l = new Layout();
    var a = l.variable ("a", 4);
    var b = l.variable ("b", 4);
    var s = l.structure("s", a, b);
    var c = l.variable ("c", 4);
    var d = l.variable ("d", 4);
    var t = l.structure("t", c, d);
    var e = l.variable ("e", 4);
    var S = l.structure("S", e, s, t);
    l.layout(S);
    a.fromInt(1);
    b.fromInt(2);
    c.fromInt(3);
    d.fromInt(4);
    e.fromInt(5);

    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    20             274965   S
V    0     4                  5     e     e
S    4     8                 33     s     s
V    4     4                  1       a     s.a
V    8     4                  2       b     s.b
S   12     8                 67     t     t
V   12     4                  3       c     t.c
V   16     4                  4       d     t.d
""");

    final Layout j = t.duplicate();
    j.get("c").ok(0);
    j.get("d").ok(0);
    //stop(j);
    j.ok("""
T   At  Wide  Index       Value   Field name
S    0     8                  0   t
V    0     4                  0     c     c
V    4     4                  0     d     d
""");

    final Layout k = t.copy();
    k.get("c").ok(3);
    k.get("d").ok(4);
    //stop(k);
    k.ok("""
T   At  Wide  Index       Value   Field name
S   12     8                 67     t
V   12     4                  3       c     c
V   16     4                  4       d     d
""");

    k.get("c").fromInt(6);
    //stop(l);
    l.ok("""
T   At  Wide  Index       Value   Field name
S    0    20             287253   S
V    0     4                  5     e     e
S    4     8                 33     s     s
V    4     4                  1       a     s.a
V    8     4                  2       b     s.b
S   12     8                 70     t     t
V   12     4                  6       c     t.c
V   16     4                  4       d     t.d
""");
   }

  static void test_single_bit()
   {Layout.Bit b = createBit("b");
               b.fromInt(1);
    //stop(b);
    b.ok( """
T   At  Wide  Index       Value   Field name
B    0     1                  1   b
""");
   }

  static void test_single_variable()
   {Layout.Variable a = createVariable("a", 4);
    a.fromInt(3);
    //stop(a);
    a.ok( """
T   At  Wide  Index       Value   Field name
V    0     4                  3   a
""");
   }

  static void test_constant()
   {LayoutAble  A = createVariable("a", 4);
    Layout.Variable a = A.asField().toVariable();
    a.fromInt(3);
    //stop(a);
    a.ok("""
T   At  Wide  Index       Value   Field name
V    0     4                  3   a
""");
    Layout.constants(a);
    a.ok("""
T   At  Wide  Index       Value   Field name
V    0     4                  3   =a
""");
    sayThisOrStop("cannot modify constant: a with value: 3 ");
    a.fromInt(4);
   }

  static void test_classes()
   {Layout.Variable a = createVariable("a", 4);
    a.addClasses("index node", "string");
    ok( a.checkClasses("node", "index"));
    sayThisOrStop("Class: a not contained in classes for: a with classes: [index, node, string]");
    a.checkClasses("a b");
   }

  static void test_like()
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
    var al = a.like();
    var Al = A.like();
    var sl = s.like();
    al.ok("""
T   At  Wide  Index       Value   Field name
V    0     2                  0   a
""");
    Al.ok("""
T   At  Wide  Index       Value   Field name
A    0    24      0           0   A
S    0     8                  0     s     s
V    0     2                  0       a     s.a
V    2     2                  0       b     s.b
V    4     4                  0       c     s.c
A    8    24      1           0   A
S    8     8                  0     s     s
V    8     2                  0       a     s.a
V   10     2                  0       b     s.b
V   12     4                  0       c     s.c
A   16    24      2           0   A
S   16     8                  0     s     s
V   16     2                  0       a     s.a
V   18     2                  0       b     s.b
V   20     4                  0       c     s.c
""");
    sl.ok("""
T   At  Wide  Index       Value   Field name
S    0     8                  0   s
V    0     2                  0     a     a
V    2     2                  0     b     b
V    4     4                  0     c     c
""");
   }

  static void test_binary_to_unary()
   {ok(unary(0),  0);
    ok(unary(1),  1);
    ok(unary(2),  3);
    ok(unary(3),  7);
    ok(unary(4), 15);
    ok(unary(5), 31);
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_1();
    test_memory();
    test_bit();
    test_bits();
    test_sub_layout();
    test_union();
    test_unary();
    test_duplicate();
    test_duplicate_sub_layout();
    test_single_variable();
    test_single_bit();
    test_constant();
    test_classes();
    test_binary_to_unary();
    test_like();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
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
