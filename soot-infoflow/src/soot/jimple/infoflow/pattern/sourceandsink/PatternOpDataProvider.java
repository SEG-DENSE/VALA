package soot.jimple.infoflow.pattern.sourceandsink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import soot.Scene;
import soot.SootMethod;
import soot.Value;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOP;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPConstant;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPHelper;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPType;
import soot.jimple.infoflow.pattern.patternresource.definition.LCEditOPDefinition;
import soot.jimple.infoflow.pattern.patternresource.definition.LCMethodDefinition;
import soot.jimple.infoflow.pattern.patternresource.definition.LCResourceOPDefinition;
import soot.jimple.infoflow.pattern.patternresource.enumeration.MergeStrategy;
import soot.jimple.infoflow.resourceleak.ResourceLeakGroup;
import soot.jimple.infoflow.sourcesSinks.definitions.MethodSourceSinkDefinition;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkDefinition;
import soot.jimple.infoflow.sourcesSinks.definitions.SourceSinkType;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static soot.jimple.infoflow.pattern.patternresource.LCResourceOPConstant.xml_category;

public class PatternOpDataProvider {
    private static final int INITIAL_SET_SIZE = 10000;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Set<LCResourceOPDefinition> defs = new HashSet<>();
    public static PatternOpDataProvider fromFile(String fileName) throws IOException {
        PatternOpDataProvider instance = new PatternOpDataProvider();
        instance.readFile(fileName);
        return instance;
    }



    private void readFile(String fileName) throws IOException {
        File f = new File(fileName);
        if (!f.exists()) {
            return;
        }
        FileInputStream inputStream = new FileInputStream(f);
        SAXParserFactory pf = SAXParserFactory.newInstance();
        SAXHandler handler = new SAXHandler();
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

    public Set<LCResourceOPDefinition> getAllDefs() {
        return defs;
    }

    protected class SAXHandler extends DefaultHandler {
        protected Set<LCResourceOPDefinition> opdefs = new HashSet<>();
        protected String patternName = null;
        protected String category = null;
        protected LCResourceOPType opType = null;
        protected Set<LCMethodDefinition> methodDefs = null;
        protected MergeStrategy mergeStrategy = MergeStrategy.NEVER;
        public SAXHandler() {
            super();
            this.opdefs.add(LCEditOPDefinition.getSingletonEditDefinition());
        }
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            String qNameLower = qName.toLowerCase();
            if (qNameLower.startsWith("pattern_")) {
                patternName = qNameLower.replace("pattern_", "");
            } else {
                switch (qNameLower) {
                    case LCResourceOPConstant.xml_operation: {
                        category = attributes.getValue(LCResourceOPConstant.xml_category);
                        if (null == category) category = LCResourceOPConstant.DEFAULT;
                        String optypestr = attributes.getValue(LCResourceOPConstant.xml_optype);
                        opType = (optypestr != null) ? LCResourceOPType.valueOf(optypestr.toUpperCase()) : LCResourceOPType.UNKNOWN;
                        String mergestrategystr = attributes.getValue(LCResourceOPConstant.xml_mergestrategy);
                        mergeStrategy = (mergestrategystr != null) ? MergeStrategy.valueOf(mergestrategystr.toUpperCase()) : MergeStrategy.NEVER;
                        methodDefs = new HashSet<>();
                        break;
                    }
                    case LCResourceOPConstant.xml_method: {
                        String methodsig = attributes.getValue(LCResourceOPConstant.xml_methodsig);
                        if (null == methodsig) {
                            break;
                        }
                        SootMethod method = null;
                        try {
                            method = Scene.v().getMethod(methodsig);
                        } catch (RuntimeException e) {
                            method = null;
                        }
                        if (null == method) {
                            break;
                        }
                        String checkValues = attributes.getValue(LCResourceOPConstant.xml_methodcheckvalue);
                        boolean shouldNotCheck = false;
                        boolean shouldCheckLeft = true;
                        boolean shouldCheckBase = true;
                        int[] shouldCheckParams = null;
                        if (null != checkValues) {
                            ArrayList<Integer> checkParamInts = new ArrayList<>();
                            String[] temp = checkValues.split(",");
                            for (String checkValue : temp) {
                                String value = checkValue.trim();
                                if (value.equals("!l")) {
                                    shouldCheckLeft = false;
                                } else if (value.equals("!b")) {
                                    shouldCheckBase = false;
                                } else if (value.startsWith("p")){
                                    int checkParam = Integer.parseInt(value.substring(1));
                                    checkParamInts.add(checkParam);
                                } else if (value.equals("not")) {
                                    shouldNotCheck = true;
                                }
                            }
                            if (!checkParamInts.isEmpty()) {
                                shouldCheckParams = new int[checkParamInts.size()];
                                for (int i = 0; i < checkParamInts.size(); i++) {
                                    shouldCheckParams[i] = checkParamInts.get(i);
                                }
                            }
                        } else {
                            int methodParamCount = method.getParameterCount();
                            if (methodParamCount > 0) {
                                shouldCheckParams = new int[methodParamCount];
                                for (int i = 0; i < methodParamCount; i++) {
                                    shouldCheckParams[i] = i;
                                }
                            }
                        }
                        LCMethodDefinition methodef = new LCMethodDefinition(methodsig, method, shouldCheckBase, shouldCheckLeft, shouldCheckParams, shouldNotCheck);
                        methodDefs.add(methodef);
                        break;
                    }
                }
            }

        }
        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            String qNameLower = qName.toLowerCase();
            switch (qNameLower) {
                case LCResourceOPConstant.xml_operation: {
                    opdefs.add(new LCResourceOPDefinition(patternName, category, opType, methodDefs, mergeStrategy));
                    break;
                }
            }
        }

        public Set<LCResourceOPDefinition> getopdefs() {
            return opdefs;
        }
    }
}
