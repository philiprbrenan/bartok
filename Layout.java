//------------------------------------------------------------------------------
// Layout memory as a sequence of variables, arrays, structures, and unions
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Simulate a silicon chip.

import java.util.*;

//D1 Construct                                                                  // Layout a description of the powered memory used by a chip

public class Layout extends Test                                                // Describe a chip and emulate its operation.  A chip consists of a block of memory and some logic that computes the next value of the memory thus changing the state of the chip. A chip is testable.
 {final String  name;                                                           // Name of layout
  Field top;                                                                    // The top most field in a set of nested fields
  final Stack<Field> fields = new Stack<>();                                   // Fields in the layout in in-order

  Layout(String Name)                                                           // Create a new Layout loaded from a set of definitions
   {name = Name;                                                                // Name of layout
    top  = define();
    if (top != null) top.layout(0, 0, null);                                    // Locate field positions
   }

  Field define() {return null;};                                                // Use variables, arrays, structures, union to define the layout and return its upper most field

  Field get(String path)                                                        // Locate a field from its path which must include the outer most field
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

  Layout duplicate()                                                            // Duplicate a layout
   {final Layout d = new Layout(name);
    if (top == null) return d;                                                  // No fields to duplicate
    d.top = top.duplicate(d);                                                   // Copy each field into this layout
    d.top.order();                                                              // Order the fields
    return d;
   }

  public String toString()                                                      // Walk the field tree in in-order printing the memory field headers
   {final StringBuilder b = new StringBuilder();
    b.append(String.format("%4s  %4s  %4s    %s\n",
                             "  At", "Wide", "Size", "Field name"));
    for(Field f : fields) b.append(f.toString());                               // Print all the fields in the structure field
    return b.toString();
   }

  void ok(String expected) {Test.ok(toString(), expected);}                     // Confirm layout is as expected

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

    abstract void layout(int at, int depth, Field superStructure);              // Layout this field
    abstract void order();                                                      // Add this field to the tree of fields

    void position(int At) {at = At;}                                            // Reposition a field after an index of a containing array has been changed

    String indent() {return "  ".repeat(depth);}                                // Indentation during printing

    public String toString()                                                    // Print the field
     {return String.format("%4d  %4d        %s  %s\n",
                            at, width, indent(), name);
     }

    abstract Field duplicate(Layout d);                                         // Duplicate an field of this field so we can modify it safely

    Variable  toVariable () {return (Variable) this;}                           // Try to convert to a variable
    Array     toArray    () {return (Array)    this;}                           // Try to convert to an array
    Structure toStructure() {return (Structure)this;}                           // Try to convert to a structure
    Union     toUnion    () {return (Union)    this;}                           // Try to convert to a union
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
     {return String.format("%4d  %4d  %4d  %s  %s\n",
                            at, width, size, indent(), name);
     }

    void setIndex(int Index)                                                    // Sets the index for the current array field allowing us to set and get this field and all its sub elements.
     {if (index != Index)
       {index = Index; position(at);
       }
     }

    Field duplicate(Layout d)                                                   // Duplicate an array so we can modify it safely
     {final Array a = d.new Array(name, element.duplicate(d), size);
      a.width = width; a.at = at; a.depth = depth; a.index = index;
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

    Field duplicate(Layout d)                                                   // Duplicate a union so we can modify it safely
     {final Union u = d.new Union(name);
      u.width = width; u.at = at; u.depth = depth;
      for(String s : subMap.keySet())
       {final Field L = subMap.get(s);
        final Field l = L.duplicate(d);
        u.subMap.put(l.name, l);
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
   {Layout l = new Layout("layout")
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
  At  Wide  Size    Field name
   0    32          S
   0     4            d
   4    24     3      A
   4     8              s
   4     2                a
   6     2                b
   8     4                c
  28     4            e
""");

    l.get("S.A").toArray().setIndex(1);
    //stop(l);
    l.ok("""
  At  Wide  Size    Field name
   0    32          S
   0     4            d
   4    24     3      A
  12     8              s
  12     2                a
  14     2                b
  16     4                c
  28     4            e
""");

    Layout m = l.duplicate();
    //say(l);
    //stop(m);
    l.ok("""
  At  Wide  Size    Field name
   0    32          S
   0     4            d
   4    24     3      A
  12     8              s
  12     2                a
  14     2                b
  16     4                c
  28     4            e
""");
    m.ok("""
  At  Wide  Size    Field name
   0    32          S
   0     4            d
   4    24     3      A
  12     8              s
  12     2                a
  14     2                b
  16     4                c
  28     4            e
""");
    m.get("S.A").toArray().setIndex(2);
    //stop(m);
    l.ok("""
  At  Wide  Size    Field name
   0    32          S
   0     4            d
   4    24     3      A
  12     8              s
  12     2                a
  14     2                b
  16     4                c
  28     4            e
""");

    m.ok("""
  At  Wide  Size    Field name
   0    32          S
   0     4            d
   4    24     3      A
  20     8              s
  20     2                a
  22     2                b
  24     4                c
  28     4            e
""");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_1();
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    test_1();
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
