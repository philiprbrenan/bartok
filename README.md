<div>
    <p><a href="https://github.com/philiprbrenan/bartok"><img src="https://github.com/philiprbrenan/bartok/workflows/Test/badge.svg"></a>
</div>

# [B-Tree](https://en.wikipedia.org/wiki/B-tree) on a [Silicon](https://en.wikipedia.org/wiki/Silicon) [chip](https://en.wikipedia.org/wiki/Integrated_circuit) 
A [B-Tree](https://en.wikipedia.org/wiki/B-tree) on a [Silicon](https://en.wikipedia.org/wiki/Silicon) [chip](https://en.wikipedia.org/wiki/Integrated_circuit) .

Reasons why you might want to join this project:

http://prb.appaapps.com/zesal/pitchdeck/pitchDeck.html

# Files

```
BitMachine - A bit machine to run the btree algorithm once written in assembler
Chip       - Describe a chip capable of running the btree algorithm at the register transfer level
Layout     - Layout memory for a bit machine
Stuck      - A fixed size stack written in bit assembler
Test       - Methods used for testing and debugging
Unary      - Unary arithmetic in bit assembler
```

# Sample Output

Here is a [B-Tree](https://en.wikipedia.org/wiki/B-tree), printed horizontally, showing the **(keys)** in each level and the
number of each **-branch** and **=leaf** in the [tree](https://en.wikipedia.org/wiki/Tree_(data_structure)): 
```
         6(317)              5(511)7-0              |
27,317=6       391,442,511=5          545,578,993=7 |
```

The [B-Tree](https://en.wikipedia.org/wiki/B-tree) was created by running [Java](https://en.wikipedia.org/wiki/Java_(programming_language)) generated machine [code](https://en.wikipedia.org/wiki/Computer_program) for a custom [instruction set architecture](https://en.wikipedia.org/wiki/Instruction_set_architecture) focussed on manipulating a [B-Tree](https://en.wikipedia.org/wiki/B-tree) .  The machine [code](https://en.wikipedia.org/wiki/Computer_program) is executed on a [bit](https://en.wikipedia.org/wiki/Bit) machine emulated in [Java](https://en.wikipedia.org/wiki/Java_(programming_language)) as well.
