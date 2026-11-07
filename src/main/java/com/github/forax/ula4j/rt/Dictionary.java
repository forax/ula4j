package com.github.forax.ula4j.rt;

import com.github.forax.ula4j.ast.Stmt;

import java.util.ArrayList;

public final class Dictionary {
  private final ArrayList<Stmt.FunctionDef> functions;

  public Dictionary() {
    this.functions = new ArrayList<>();
    super();
  }

  public int register(Stmt.FunctionDef function) {
    var index = functions.size();
    functions.add(function);
    return index;
  }

  public Stmt.FunctionDef retrieve(int index) {
    var function =  functions.get(index);
    if (function == null) {
      throw new AssertionError("function not found: " + index);
    }
    functions.set(index, null);  // clear for the GC
    return function;
  }
}
