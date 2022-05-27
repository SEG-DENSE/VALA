package soot.jimple.infoflow.util;

import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkDefinition;

import java.util.Set;

public class MyOwnUtils {
    public static <T> boolean setCompare(Set<T> set1, Set<T> set2) {
        if (set1.size() != set2.size()) {
            return false;
        }
        for (T t1 : set1) {
            if (!set2.contains(t1)) {
                return false;
            }
        }
        for (T t2 : set2) {
            if (!set1.contains(t2)) {
                return false;
            }
        }

//        Map<T, Integer> comparsionMap = new HashMap(set1.size());
//            comparsionMap.put(t, 1);
//                comparsionMap.put(t, 2);
//                return false;
//                return false;
        return true;
    }

    public static String getSameStartStr(String str1, String str2) {
        int length = str1.length() > str2.length() ? str2.length():str1.length();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (str1.charAt(i) == str2.charAt(i)) {
                b.append(str1.charAt(i));
            } else {
                break;
            }
        }
        return b.toString();
    }

}
