package soot.jimple.infoflow.pattern.mappingmethods;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import soot.Scene;
import soot.SootMethod;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPConstant;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPType;
import soot.jimple.infoflow.pattern.patternresource.definition.LCEditOPDefinition;
import soot.jimple.infoflow.pattern.patternresource.definition.LCMethodDefinition;
import soot.jimple.infoflow.pattern.patternresource.definition.LCResourceOPDefinition;
import soot.jimple.infoflow.pattern.patternresource.enumeration.MergeStrategy;
import soot.jimple.infoflow.pattern.solver.PatternInfoflowManager;
import soot.jimple.infoflow.pattern.sourceandsink.PatternOpDataProvider;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class MappingMethodProvider {
    private static final int INITIAL_SET_SIZE = 10000;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Map<SootMethod, MappingMethodDefinition> defs = new HashMap<>();
    private static MappingMethodProvider instance = null;
    private InfoflowConfiguration config = null;
    public static MappingMethodProvider fromFile(InfoflowConfiguration config, String fileName) throws IOException {
        MappingMethodProvider instance = new MappingMethodProvider(config);
        instance.readFile(fileName);
        return instance;
    }
    private MappingMethodProvider(InfoflowConfiguration config) {
        this.config = config;
    }

    private void readFile(String fileName) throws IOException {
        File f = new File(fileName);
        if (!f.exists()) {
            return;
        }
        FileInputStream inputStream = new FileInputStream(f);
        SAXParserFactory pf = SAXParserFactory.newInstance();
        MappingMethodProvider.SAXHandler handler = new MappingMethodProvider.SAXHandler();
        try {
            SAXParser parser = pf.newSAXParser();
            parser.parse(inputStream, handler);
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        defs = handler.getopdefs();
    }

    public Map<SootMethod, MappingMethodDefinition> getAllDefs() {
        return defs;
    }

    protected class SAXHandler extends DefaultHandler {
        protected Map<SootMethod, MappingMethodDefinition> opdefs;
        protected SootMethod method = null;
        protected boolean checkBase = false;
        protected int[] checkArgs = null;
        protected boolean checkLeft = false;
        protected boolean taintLeft = false;
        protected boolean taintBase = false;
        protected boolean shouldAddop = false;
        protected int[] taintArgs = null;

        public SAXHandler() {
            super();
            this.opdefs = new HashMap<>();
            if (config.shouldTaintSubfieldsAndChildren()) {
                boolean tempTaintLeft = true;
                boolean tempCheckBase = true;
                Set<String> allsigs = new HashSet<>();
                allsigs.addAll(Arrays.asList(mapMethods));
                allsigs.addAll(Arrays.asList(setMethods));
                allsigs.addAll(Arrays.asList(iteratorMethods));
                allsigs.addAll(Arrays.asList(listMethods));

                for (String sig : allsigs) {
                    try {
                        SootMethod m = Scene.v().getMethod(sig);
                        if (null != m) {
                            this.opdefs.put(m, new MappingMethodDefinition(m, tempCheckBase, null, false, false, null, tempTaintLeft, false));
                        }
                    } catch (RuntimeException e) {

                    }
                }
            }


        }
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            String qNameLower = qName.toLowerCase();
            switch (qNameLower) {
                case LCResourceOPConstant.xml_method: {
                    String methodsig = attributes.getValue(LCResourceOPConstant.xml_methodsig);
                    if (null == methodsig) {
                        break;
                    }
                    try {
                        method = Scene.v().getMethod(methodsig);
                    } catch (RuntimeException e) {
                        method = null;
                        break;
                    }

                    String checkValues = attributes.getValue(LCResourceOPConstant.xml_methodcheckvalue);
                    if (null != checkValues) {
                        ArrayList<Integer> checkParamInts = new ArrayList<>();
                        String[] temp = checkValues.split(",");
                        for (String checkValue : temp) {
                            String value = checkValue.trim();
                            if (value.equals("l")) {
                                checkLeft = true;
                            } else if (value.equals("b")) {
                                checkBase = true;
                            } else if (value.startsWith("p")) {
                                int checkParam = Integer.parseInt(value.substring(1));
                                checkParamInts.add(checkParam);
                            }
                        }
                        if (!checkParamInts.isEmpty()) {
                            checkArgs = new int[checkParamInts.size()];
                            for (int i = 0; i < checkParamInts.size(); i++) {
                                checkArgs[i] = checkParamInts.get(i);
                            }
                        }
                    }


                    String taintValues = attributes.getValue(LCResourceOPConstant.xml_methodtaintvalue);
                    if (null != taintValues) {
                        ArrayList<Integer> taintParamInts = new ArrayList<>();
                        String[] temp = taintValues.split(",");
                        for (String taintValue : temp) {
                            String value = taintValue.trim();
                            if (value.equals("l")) {
                                taintLeft = true;
                            } else if (value.equals("b")) {
                                taintBase = true;
                            } else if (value.startsWith("p")) {
                                int taintParam = Integer.parseInt(value.substring(1));
                                taintParamInts.add(taintParam);
                            } else if (value.equals("op")) {
                                shouldAddop = true;
                            }
                        }
                        if (!taintParamInts.isEmpty()) {
                            taintArgs = new int[taintParamInts.size()];
                            for (int i = 0; i < taintParamInts.size(); i++) {
                                taintArgs[i] = taintParamInts.get(i);
                            }
                        }
                    }
                }
            }
        }
        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            String qNameLower = qName.toLowerCase();
            switch (qNameLower) {
                case LCResourceOPConstant.xml_method: {
                    if (null != method) {
                        opdefs.put(method, new MappingMethodDefinition(method, checkBase, checkArgs, checkLeft, taintBase, taintArgs, taintLeft, shouldAddop));

                    }
                    method = null;
                    checkBase = false;
                    checkArgs = null;
                    checkLeft = false;
                    taintLeft = false;
                    taintBase = false;
                    shouldAddop = false;
                    taintArgs = null;
                    break;
                }
            }
        }

        public Map<SootMethod, MappingMethodDefinition> getopdefs() {
            return opdefs;
        }
    }
    private static final String[] mapMethods = new String[]{"<java.util.Map: java.util.Set keySet()>", "<java.util.Map: java.util.Collection values()>",
                            "<java.util.Map: java.util.Set entrySet()>", "<java.util.Map: java.lang.Object get(java.lang.Object)>",
                            "<java.util.Map: java.lang.Object getOrDefault(java.lang.Object,java.lang.Object)>", "<java.util.Map$Entry: java.lang.Object getValue()>",
                            "<java.util.Map$Entry: java.lang.Object getKey()>"};
    private static final String[] listMethods = new String[]{"<java.util.List: java.lang.Object[] toArray()>", "<java.util.List: java.lang.Object[] toArray(java.lang.Object[])>"};
    private static final String[] setMethods = new String[]{"<java.util.Set: java.lang.Object[] toArray()>", "<java.util.Set: java.lang.Object[] toArray(java.lang.Object[])>"};
    private static final String[] iteratorMethods = new String[]{"<java.util.Iterator: java.lang.Object next()>", "<java.util.Set: java.util.Iterator iterator()>", "<java.util.List: java.util.Iterator iterator()>"};
}
