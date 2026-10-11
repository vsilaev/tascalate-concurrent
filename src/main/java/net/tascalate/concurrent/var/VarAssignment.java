package net.tascalate.concurrent.var;

final class VarAssignment<T> {
    final ContextVar<T> variable;
    final T value;
    
    VarAssignment(ContextVar<T> variable, T value) {
        this.variable = variable;
        this.value = value;
    }
}
