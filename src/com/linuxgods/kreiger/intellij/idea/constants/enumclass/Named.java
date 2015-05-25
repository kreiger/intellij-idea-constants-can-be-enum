package com.linuxgods.kreiger.intellij.idea.constants.enumclass;

class Named<T> {
    private String name;
    private T value;

    Named(String name, T value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }
    public T getValue() {
        return value;
    }
}
