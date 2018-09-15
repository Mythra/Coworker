package io.kungfury.endtoend;

import com.jsoniter.any.Any;

import java.io.Serializable;

public class JavaFunctions implements Serializable {
    public Void empty(Any[] v) {
        System.out.println("Empty java");
        return null;
    }

    public Void nonEmpty(Any[] args) {
        int num = args[0].toInt();
        System.out.println("Num is: " + num);
        return null;
    }
}
