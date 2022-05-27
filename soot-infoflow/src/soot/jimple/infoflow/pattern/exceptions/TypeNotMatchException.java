package soot.jimple.infoflow.pattern.exceptions;

import soot.Type;

public class TypeNotMatchException extends Exception{
    public TypeNotMatchException(Type typ1, Type typ2) {
        super("type : " + typ1.toString() + ", " + typ2.toString() + " not match!!!!!");
    }
}
