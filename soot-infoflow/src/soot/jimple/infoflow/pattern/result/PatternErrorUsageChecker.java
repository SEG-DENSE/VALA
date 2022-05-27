package soot.jimple.infoflow.pattern.result;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.infoflow.pattern.patterndata.PatternData;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPConstant;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPList;
import soot.jimple.infoflow.pattern.patternresource.definition.LCMethodDefinition;
import soot.jimple.infoflow.results.rules.ExceptionRule;
import soot.jimple.infoflow.results.rules.NoActivityAssignmentRule;
import soot.util.MultiMap;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class PatternErrorUsageChecker {
    protected LCMethodSummaryResult results = null;
    protected Set<PatternData> patterns = null;
    protected Collection<ErrorUsageDefinition> usageDefs = null;
    protected List<ExceptionRule> rules = null;

    public PatternErrorUsageChecker(Set<PatternData> patterns, String usageFile) throws IOException {
        this.patterns = patterns;
        this.rules = new ArrayList<>();
        this.rules.add(new NoActivityAssignmentRule());
        readFile(usageFile);
    }

    public Set<ErrorUsageDetailedReport> check(LCMethodSummaryResult results) {
        this.results = results;
        Set<ErrorUsageDetailedReport> reports = new HashSet<ErrorUsageDetailedReport>();

        Map<SootField, MultiMap<String, LCResourceOPList>> opLists = results.getAllResultsSortedByFieldsAndEntrypoints();
        for (SootField field : opLists.keySet()) {
            MultiMap<String, LCResourceOPList> currentOpLists = opLists.get(field);
            for (ErrorUsageDefinition def : usageDefs) {
                ErrorUsageDetailedReport report = def.check(currentOpLists);
                if (null != report) {
                    report.field = field;
                    long num = this.rules.stream().filter(rule->rule.shouldBeExcluded(report)).count();
                    if (num == 0)
                        reports.add(report);
                }
            }
        }
        return reports;
    }

    private void readFile(String fileName) throws IOException {
        File f = new File(fileName);
        if (!f.exists()) {
            return;
        }
        FileInputStream inputStream = new FileInputStream(f);
        SAXParserFactory pf = SAXParserFactory.newInstance();
        SAXHandler handler = new SAXHandler(this.patterns);
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
        usageDefs = handler.getUsageDefs();
    }

    protected class SAXHandler extends DefaultHandler {
        protected Set<PatternData> allPatternDatas = null;
        protected PatternData currentPatternData = null;
        protected Map<String, ErrorUsageDefinition> usageDefs = new HashMap<>();
        protected ErrorUsageDefinition currentDef = null;
        protected String currentId = null;

        public SAXHandler(Set<PatternData> allPatternData) {
            super();
            this.allPatternDatas = allPatternData;
        }

        public Collection<ErrorUsageDefinition> getUsageDefs() {
            return this.usageDefs.values();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            String qNameLower = qName.toLowerCase();
            switch (qNameLower) {
                case ErrorUsageConstant.xml_pattern: {
                    currentPatternData = null;
                    String patternDataTitle = attributes.getValue(ErrorUsageConstant.xml_title);
                    if (null != patternDataTitle) {
                        for (PatternData data : allPatternDatas) {
                            if (data.getPatternTitle().equalsIgnoreCase(patternDataTitle)) {
                                currentPatternData = data;
                                break;
                            }
                        }
                    }
                    break;
                }
                case ErrorUsageConstant.xml_rule:{
                    if (null == currentPatternData) {
                        break;
                    }
                    String tempId = attributes.getValue(ErrorUsageConstant.xml_id);
                    currentId = currentPatternData.getPatternTitle() + "_" + tempId;
                    currentDef = new ErrorUsageDefinition(tempId, currentPatternData);
                    usageDefs.put(currentId, currentDef);
                    break;
                }
                case ErrorUsageConstant.xml_condition:{
                    if (null == currentPatternData) {
                        break;
                    }
                    String value = attributes.getValue(ErrorUsageConstant.xml_value);
                    String entrypoint = attributes.getValue(ErrorUsageConstant.xml_entrypoint);
                    currentDef.addCondition(entrypoint, value);
                    break;
                }
                default:{
                    break;
                }
            }
        }

//        @Override
//            String qNameLower = qName.toLowerCase();
//                        usageDefs.put(currentId, currentDef);
//                    break;

    }
}
