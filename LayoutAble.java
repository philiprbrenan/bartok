//------------------------------------------------------------------------------
// Unify layouts and sub layouts so that either may be used as method parameters
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Design, simulate and layout digital a binary tree on a silicon chip.

interface LayoutAble                                                            // There is a memory layout associated with this object
 {Layout.Field asLayoutField();                                                 // Get the layout field describing the memory use by the class on the bit machine
  Layout       asLayout();                                                      // Get the layout describing the memory use by the class on the bit machine
 }
