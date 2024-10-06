//------------------------------------------------------------------------------
// Get the layout of memory being used by a class to Test on a bit machine
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Design, simulate and layout digital a binary tree on a silicon chip.

interface LayoutAble                                                            // There is a memory layout associated with this object
 {Layout.Field getLayoutField();                                                // Get the layout field describing the memory use by the class on the bit machine
  Layout       getLayout();                                                     // Get the layout describing the memory use by the class on the bit machine
 }
