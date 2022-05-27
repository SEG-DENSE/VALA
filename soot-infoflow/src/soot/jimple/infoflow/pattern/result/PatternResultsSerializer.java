package soot.jimple.infoflow.pattern.result;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.pattern.patterndata.*;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOP;
import soot.jimple.infoflow.pattern.patternresource.LCResourceOPList;
import soot.jimple.infoflow.results.InfoflowPerformanceData;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.infoflow.util.MyOutputer;
import soot.util.MultiMap;

/**
 * Class for serializing FlowDroid results to XML
 *
 * @author Steven Arzt
 *
 */
public class PatternResultsSerializer {

    public static final int FILE_FORMAT_VERSION = 102;

    protected boolean serializeTaintPath = true;
    protected IInfoflowCFG icfg;
    protected InfoflowConfiguration config;
    protected long startTime = 0;

    /**
     * Creates a new instance of the InfoflowResultsSerializer class
     */
    public PatternResultsSerializer() {
        this(null, null);
    }

    /**
     * Creates a new instance of the InfoflowResultsSerializer class
     *
     * @param cfg The control flow graph to be used for obtaining additional
     *            information such as the methods containing source or sink
     *            statements
     */
    public PatternResultsSerializer(IInfoflowCFG cfg, InfoflowConfiguration config) {
        this.icfg = cfg;
        this.config = config;
    }

    /**
     * Serializes the given FlowDroid result object into the given file
     *
     * @param results  The result object to serialize
     * @param fileName The target file name
     * @throws FileNotFoundException Thrown if target file cannot be used
     * @throws XMLStreamException    Thrown if the XML data cannot be written
     */
    public void serialize(LCMethodSummaryResult results, Set<ErrorUsageDetailedReport> reports, String fileName) throws FileNotFoundException, XMLStreamException {
        this.startTime = System.currentTimeMillis();

        OutputStream out = new FileOutputStream(fileName);
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = factory.createXMLStreamWriter(out, "UTF-8");

        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeStartElement(XmlConstants.Tags.root);
        writer.writeAttribute(XmlConstants.Attributes.fileFormatVersion, FILE_FORMAT_VERSION + "");
        writer.writeAttribute(XmlConstants.Attributes.terminationState,
                terminationStateToString(results.getTerminationState()));

        // Write out the data flow results
        if (results != null && !results.isEmpty()) {
            writePatternDatas(results, writer);
            writer.writeStartElement(XmlConstants.Tags.exception);
            if (null != results.getExceptions()) {
                String exceptionStr = "";
                for (String str : results.getExceptions()) {
                    exceptionStr += str + " \r\n";
                }
                writer.writeAttribute(XmlConstants.Tags.exceptionStmt, exceptionStr);
            }
            writer.writeEndElement();
        }

        if (!reports.isEmpty()) {
            writeLCErrorUsages(writer, reports);
        }

        // Write out performance data
        writer.writeStartElement(XmlConstants.Tags.performanceData);
        InfoflowPerformanceData performanceData = results.getPerformanceData();
        if (performanceData != null && !performanceData.isEmpty()) {
            writePerformanceData(performanceData, writer);

        }
        writeLCStatisticData(writer);
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.close();
    }

    private void writePatternDatas(LCMethodSummaryResult results, XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement(XmlConstants.Tags.patterndatas);
        PatternDataHelper helper = PatternDataHelper.v();
        Set<PatternData> datas = helper.getAllPatterns();
        for (PatternData data : datas) {
            writer.writeStartElement(XmlConstants.Tags.patterndata);
            writer.writeAttribute(XmlConstants.Attributes.patterndatatype, data.getPatternTitle());
            if (!data.getInvolvedEntrypoints().isEmpty()) {
                writeInvolvedEntrypointsBySootField(data.getInvolvedEntrypoints(), writer, results);
            }
            data.writeExtraResult(writer);
            writer.writeEndElement();
        }
    }

    protected void writeInvolvedEntrypointsBySootField(Map<SootClass, PatternEntryData> involvedEntrypoints, XMLStreamWriter writer, LCMethodSummaryResult result) throws XMLStreamException {
        writer.writeStartElement(XmlConstants.Tags.entrypointclasses);
        for (SootClass involvedClass: involvedEntrypoints.keySet()) {
            PatternEntryData entryData = involvedEntrypoints.get(involvedClass);
            writer.writeStartElement(XmlConstants.Tags.entrypointclass);
            writer.writeAttribute(XmlConstants.Attributes.classname, involvedClass.getName());
            List<SootMethod> involvedMethods = new LinkedList<>();
            involvedMethods.addAll(entryData.getAllMethods());
            for (SootField field : involvedClass.getFields()) {
                MultiMap<SootMethod, LCResourceOPList> methodOPmaps = result.getFieldOPSummary(field);
                if (null == methodOPmaps || methodOPmaps.isEmpty()) {continue;};
                writer.writeStartElement(XmlConstants.Tags.targetfield);
                writer.writeAttribute(XmlConstants.Attributes.fieldname, field.getName() + ":" + field.getType().toString());
                if (null != methodOPmaps && !methodOPmaps.isEmpty()) {
                    for (SootMethod m : methodOPmaps.keySet()) {
                        writer.writeStartElement(XmlConstants.Tags.entrypointmethod);
                        writer.writeAttribute(XmlConstants.Attributes.methodsig, m.getSignature());
                        for (LCResourceOPList oplist: methodOPmaps.get(m)) {
                            writerOPList(oplist, writer);
                        }
                        writer.writeEndElement();
                    }
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    protected void writerOPList(LCResourceOPList oplist, XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement(XmlConstants.Tags.operationlist);
        writer.writeAttribute(XmlConstants.Attributes.optype, oplist.getCategory());
        LinkedList<LCResourceOP> ops = oplist.getOpList();
        if (null != ops && !ops.isEmpty()) {
            for (LCResourceOP op : ops) {
                writer.writeStartElement(op.getTitle());
                writer.writeAttribute(XmlConstants.Attributes.stmt, op.getStmtStr());
                writer.writeEndElement();
            }
        } else {
            writer.writeStartElement("EMPTY");
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    private String terminationStateToString(int terminationState) {
        switch (terminationState) {
            case InfoflowResults.TERMINATION_SUCCESS:
                return "Success";
            case InfoflowResults.TERMINATION_DATA_FLOW_TIMEOUT:
                return "DataFlowTimeout";
            case InfoflowResults.TERMINATION_DATA_FLOW_OOM:
                return "DataFlowOutOfMemory";
            case InfoflowResults.TERMINATION_PATH_RECONSTRUCTION_TIMEOUT:
                return "PathReconstructionTimeout";
            case InfoflowResults.TERMINATION_PATH_RECONSTRUCTION_OOM:
                return "PathReconstructionOfMemory";
            default:
                return "Unknown";
        }
    }

    private void writePerformanceData(InfoflowPerformanceData performanceData, XMLStreamWriter writer)
            throws XMLStreamException {
        writePerformanceEntry(XmlConstants.Values.PERF_CALLGRAPH_SECONDS,
                performanceData.getCallgraphConstructionSeconds(), writer);
        writePerformanceEntry(XmlConstants.Values.PERF_TAINT_PROPAGATION_SECONDS,
                performanceData.getTaintPropagationSeconds(), writer);
        writePerformanceEntry(XmlConstants.Values.PERF_PATH_RECONSTRUCTION_SECONDS,
                performanceData.getPathReconstructionSeconds(), writer);
        writePerformanceEntry(XmlConstants.Values.PERF_TOTAL_RUNTIME_SECONDS, performanceData.getTotalRuntimeSeconds(),
                writer);
        writePerformanceEntry(XmlConstants.Values.PERF_MAX_MEMORY_CONSUMPTION,
                performanceData.getMaxMemoryConsumption(), writer);
        writePerformanceEntry(XmlConstants.Values.PERF_SOURCE_COUNT, performanceData.getSourceCount(), writer);
        writePerformanceEntry(XmlConstants.Values.PERF_SINK_COUNT, performanceData.getSinkCount(), writer);
    }

    private void writeLCErrorUsages(XMLStreamWriter writer, Set<ErrorUsageDetailedReport> reports) throws XMLStreamException {
        writer.writeStartElement(XmlConstants.Tags.errorusages);
        for (ErrorUsageDetailedReport report : reports) {
            writer.writeStartElement(XmlConstants.Tags.errorusage);
            writer.writeAttribute(XmlConstants.Attributes.violatedPattern, report.violatedPattern.getPatternTitle());
            writer.writeAttribute(XmlConstants.Attributes.fieldname, report.field.getSignature());
            for (String entrypointStr : report.errorOps.keySet()) {
                writer.writeStartElement(XmlConstants.Tags.entrypoint);
                writer.writeAttribute(XmlConstants.Attributes.methodsig, entrypointStr);
                LCResourceOPList oplist = report.errorOps.get(entrypointStr);
                writerOPList(oplist, writer);
                writer.writeEndElement();
            }
        }
        writer.writeEndElement();
    }

    private void writeLCStatisticData(XMLStreamWriter writer)
            throws XMLStreamException {
        MyOutputer LCOutputer = MyOutputer.getInstance();
        writePerformanceEntry(XmlConstants.Values.PERF_CFG_EDGE_COUNT, LCOutputer.getCurrentCFGEdgeNum(), writer);
        writePerformanceEntry(XmlConstants.Values.PERF_ENTRY_NUM, LCOutputer.getCurrntInvolvedEntryNum(), writer);
        writePerformanceEntry(XmlConstants.Values.PERF_ENTRY_FIELD_NUM, LCOutputer.getCurrentEntryFieldNum(), writer);
        writePerformanceEntry(XmlConstants.Values.PERF_ALL_ENTRYCLASS_NUM, LCOutputer.getAllEntryClassNum(), writer);
        writePerformanceEntry(XmlConstants.Values.PERF_ALL_ENTRYPOINT_NUM, LCOutputer.getAllEntryPointNum(), writer);
        writePerformanceEntry(XmlConstants.Values.PERF_ALL_ENTRYFIELD_NUM, LCOutputer.getAllEntryFieldsNum(), writer);
        writePerformanceEntry(XmlConstants.Values.PERF_REPORT_ENTRYCLASS_NUM, LCOutputer.getReportEntryClassNum(), writer);
        writePerformanceEntry(XmlConstants.Values.PERF_REPORT_ENTRYFIELD_NUM, LCOutputer.getReportEntryFieldNum(), writer);
    }

    private void writePerformanceEntry(String entryName, int entryValue, XMLStreamWriter writer)
            throws XMLStreamException {
        if (entryValue > 0) {
            writer.writeStartElement(XmlConstants.Tags.performanceEntry);
            writer.writeAttribute(XmlConstants.Attributes.name, entryName);
            writer.writeAttribute(XmlConstants.Attributes.value, entryValue + "");
            writer.writeEndElement();
        } else if (entryValue == 0 && entryName.equals(XmlConstants.Values.PERF_TAINT_PROPAGATION_SECONDS)) {
            writer.writeStartElement(XmlConstants.Tags.performanceEntry);
            writer.writeAttribute(XmlConstants.Attributes.name, entryName);
            writer.writeAttribute(XmlConstants.Attributes.value, "<1");
            writer.writeEndElement();
        }
    }





    /**
     * Writes the given data flow results into the given XML stream writer
     *
     * @param results The results to write out
     * @param writer  The stream writer into which to write the results
     * @throws XMLStreamException Thrown if the XML data cannot be written
     */
////			writer.writeStartElement(XmlConstants.Tags.result);
////			writeSinkInfo(sink, writer);
////
////			// Write out the sources
////			writer.writeStartElement(XmlConstants.Tags.sources);
////			for (ResultSourceInfo src : results.getResults().get(sink))
////				writeSourceInfo(src, writer);
////			writer.writeEndElement();
////
////			writer.writeEndElement();
//            writer.writeStartElement(XmlConstants.Tags.result);
//            writeSourceInfo(source, writer);
//
//            // Write out the sources
//            writer.writeStartElement(XmlConstants.Tags.sinks);
//            for (ResultSinkInfo src : results.getReResults().get(source))
//                writeSinkInfoWithPath(src, writer);
//            writer.writeEndElement();
//
//            writer.writeEndElement();


}
