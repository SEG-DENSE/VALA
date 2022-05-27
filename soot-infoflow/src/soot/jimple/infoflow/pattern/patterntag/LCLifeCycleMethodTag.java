package soot.jimple.infoflow.pattern.patterntag;

import soot.tagkit.AttributeValueException;
import soot.tagkit.Tag;

public class LCLifeCycleMethodTag implements Tag {
    public static String TAG_NAME = "lc_lc_method";
    private String currentName = null;
    private static LCLifeCycleMethodTag notRelatedInstance = new LCLifeCycleMethodTag("notRelated");
    public static LCLifeCycleMethodTag getNotRelatedInstance(){return notRelatedInstance;}

    public LCLifeCycleMethodTag(String newtagname) {
        currentName = TAG_NAME + "_" + newtagname;
    }

    @Override
    public String getName() {
        return currentName;
    }

    @Override
    public byte[] getValue() throws AttributeValueException {
        return null;
    }
}
