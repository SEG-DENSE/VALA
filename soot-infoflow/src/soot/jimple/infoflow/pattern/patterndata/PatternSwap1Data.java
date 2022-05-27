package soot.jimple.infoflow.pattern.patterndata;

import heros.solver.Pair;
import soot.*;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.pattern.result.XmlConstants;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.solver.cfg.InfoflowCFG;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PatternSwap1Data extends PatternData {
    private int targetSdk = -1;
    private int minSdk = -1;
    private boolean shouldCheck = false;


    public PatternSwap1Data(PatternDataHelper helper, String title, int targetSdk, int minSdk) {
        super(helper, title);
        this.targetSdk = targetSdk;
        this.minSdk = minSdk;
        if (targetSdk >= 28 && minSdk < 28) {
            shouldCheck = true;
        }
        this.isForDataLoss = true;
    }


    @Override
    protected Map<SootClass, PatternEntryData> getInitialEntryClasses(Set<SootClass> allEntrypoints, IInfoflowCFG icfg) {
        Map<SootClass, PatternEntryData> newEntrypoints = new HashMap<>();
        for (SootClass cClass : allEntrypoints) {
            SootMethod onStopMethod = cClass.getMethodUnsafe(PatternDataConstant.ACTIVITY_ONSTOP);
            if (null == onStopMethod) {continue;}
            SootMethod onSaveMethod = cClass.getMethodUnsafe(PatternDataConstant.ACTIVITY_ONSAVEINSTANCESTATE);
            if (null == onSaveMethod) {continue;}
            PatternEntryData cData = new PatternEntryData(cClass);
            newEntrypoints.put(cClass, cData);
            if (!updateEntryDataWithLCMethods(cClass, cData)) {
                continue;
            }
            updateInvolvedFieldsInEntryDatas(icfg, cClass, cData);
            newEntrypoints.put(cClass, cData);
        }
        return newEntrypoints;
    }


    protected boolean updateEntryDataWithLCMethods(SootClass cClass, PatternEntryData cData) {
        String[] methodsigs = new String[] {PatternDataConstant.ACTIVITY_ONSTOP, PatternDataConstant.ACTIVITY_ONSAVEINSTANCESTATE};
        boolean result = updateEntryDataWithLCMethods_Common(cClass, methodsigs, cData);
        return result;
    }


    public int getMinSdk() {
        return this.minSdk;
    }

    public int getTargetSdk() {
        return this.targetSdk;
    }

    public void clear() {
        super.clear();
        targetSdk = -1;
        minSdk = -1;
        shouldCheck = false;
    }

    public void writeExtraResult(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement(XmlConstants.Tags.shouldCheck);
//			writer.writeAttribute(XmlConstants.Attributes.shouldCheck, data.shouldCheck() + "");
        writer.writeAttribute(XmlConstants.Attributes.minSdk, getMinSdk() + "");
        writer.writeAttribute(XmlConstants.Attributes.targetSdk, getTargetSdk() + "");
    }

}
